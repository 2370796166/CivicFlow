"""Bounded real-service load and service-token rejection probes on isolated E2E data."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import subprocess
import tempfile
import threading
import time
import uuid
from pathlib import Path

import jwt
import requests


def run(e, admin, environment, service_key, service_kid):
    prefix = 'LOAD_' + e.RUN
    outlet = e.api('POST', '/api/v1/admin/outlets', admin,
        {'code': prefix, 'name': 'Isolated load outlet', 'address': 'Synthetic'}, prefix+'out')
    item = e.api('POST', '/api/v1/admin/items', admin,
        {'code': prefix, 'name': 'Isolated load item', 'defaultDurationMinutes': 30}, prefix+'item')
    window = e.api('POST', '/api/v1/admin/windows', admin,
        {'outletId':outlet['id'],'code':prefix,'name':'Isolated load window'},prefix+'window')
    e.api('PUT',f"/api/v1/admin/windows/{window['id']}/items",admin,
        {'itemIds':[item['id']],'version':window['version']},prefix+'binding')
    password = uuid.uuid4().hex
    def create_user(index):
        username = 'load_' + e.RUN.lower() + '_' + str(index)
        # Each worker has its own HTTP connection; normal ADMIN create policy remains enabled.
        session = requests.Session()
        session.trust_env = False
        response = session.post(e.BASE+'/api/v1/admin/users', json={
            'username': username, 'password': password, 'displayName': 'Synthetic load', 'roles':['USER']},
            headers={'Authorization':'Bearer '+admin, 'Idempotency-Key':prefix+'user'+str(index)}, timeout=60)
        assert response.status_code == 200, ('create user', response.status_code)
        # Setup authentication directly at auth; gateway login limiting is not the measured workload.
        login = session.post(f'http://127.0.0.1:{e.APP_PORT + 1}/api/v1/auth/login',
            json={'loginName':username,'password':password}, timeout=60)
        assert login.status_code == 200, ('login', login.status_code)
        data = login.json()['data']
        session.close()
        return {'id':data['user']['id'], 'token':data['accessToken'], 'key':prefix+'reserve'+str(index)}
    with ThreadPoolExecutor(max_workers=8) as pool:
        users = list(pool.map(create_user, range(200)))
    print('LOAD setup: 200 distinct authenticated users', flush=True)
    now = datetime.now(e.SHANGHAI).replace(microsecond=0)
    start = now + timedelta(minutes=40)
    instant = lambda value: value.astimezone(timezone.utc).isoformat()
    slot = e.api('POST','/api/v1/admin/slots',admin,{
        'outletId':outlet['id'],'itemId':item['id'],'serviceDate':start.date().isoformat(),
        'startTime':start.time().isoformat(),'endTime':(start+timedelta(minutes=30)).time().isoformat(),
        'totalQuota':50,'releaseAt':instant(now+timedelta(seconds=15)),
        'checkInStart':instant(now-timedelta(minutes=1)),'checkInEnd':instant(start+timedelta(minutes=20)),
        'status':'SCHEDULED'},prefix+'slot')
    e.api('POST',f"/api/v1/admin/stock/slots/{slot['id']}/preheat",admin)
    time.sleep(max(0,(now+timedelta(seconds=16)-datetime.now(e.SHANGHAI)).total_seconds()))
    slot = e.api('PATCH',f"/api/v1/admin/slots/{slot['id']}/status",admin,
        {'status':'OPEN','version':slot['version']},prefix+'open')
    e.api('POST',f"/api/v1/admin/stock/slots/{slot['id']}/preheat",admin)
    tag = '{'+item['id']+':'+start.strftime('%Y%m%d')+'}'
    stock_key = 'cf:v1:dev:stock:'+tag+':slot:'+slot['id']
    samples = []
    monitor_stop = threading.Event()
    def monitor():
        while not monitor_stop.is_set():
            samples.append(int(e.redis('HGET',stock_key,'remaining')))
            monitor_stop.wait(0.1)
    with tempfile.TemporaryDirectory(prefix='civicflow-load-') as folder:
        path = Path(folder)
        (path/'fixture.json').write_text(json.dumps({'slotId':slot['id'],'quota':50,'users':users}))
        (path/'load.js').write_text((e.ROOT/'scripts/load_gateway.js').read_text())
        cmd = ['docker','run','--rm','--name','civicflow-load-'+e.RUN.lower(),
            '-v',str(path)+':/test','-e','FIXTURE=/test/fixture.json',
            '-e',f'BASE_URL=http://host.docker.internal:{e.APP_PORT}','grafana/k6:0.52.0',
            'run','--summary-export=/test/summary.json','/test/load.js']
        observer = threading.Thread(target=monitor, daemon=True)
        observer.start()
        try:
            result = subprocess.run(cmd, capture_output=True, text=True,
                                    encoding='utf-8', errors='replace', timeout=180)
        finally:
            monitor_stop.set()
            observer.join(timeout=10)
        summary = json.loads((path/'summary.json').read_text()) if (path/'summary.json').exists() else {}
        if result.returncode:
            print('k6 failure metrics '+json.dumps(summary.get('metrics',{})),flush=True)
            raise RuntimeError('k6 load failed: '+e.safe_error(result.stderr[-1200:]))
    deadline = time.monotonic()+45
    while time.monotonic()<deadline:
        count = int(e.mysql(f"SELECT COUNT(*) FROM civicflow_appointment.appointment_order WHERE slot_id={slot['id']};"))
        if count == 50:
            break
        time.sleep(1)
    assert count == 50, ('orders',count)
    assert samples and min(samples)>=0, samples
    assert e.redis('HGET',stock_key,'remaining')=='0'
    facts = e.mysql(f"SELECT COUNT(*),COUNT(DISTINCT user_id),MIN(status),MAX(status) FROM civicflow_appointment.appointment_order WHERE slot_id={slot['id']};").split()
    assert facts == ['50','50','PENDING_CONFIRM','PENDING_CONFIRM'],facts
    duplicate = e.mysql(f"SELECT COUNT(*) FROM (SELECT user_id,COUNT(*) n FROM civicflow_appointment.appointment_order WHERE slot_id={slot['id']} GROUP BY user_id HAVING n>1) t;")
    assert duplicate=='0'
    assert e.mysql(f"SELECT COUNT(*) FROM civicflow_appointment.active_booking_guard WHERE item_id={item['id']};")=='50'
    selected = e.mysql(f"SELECT user_id,reservation_id FROM civicflow_appointment.appointment_order WHERE slot_id={slot['id']} ORDER BY id LIMIT 1;").split()
    winner = next(user for user in users if user['id']==selected[0])
    time.sleep(2)
    def replay(_):
        response = requests.post(e.BASE+'/api/v1/user/appointments/reservations',
            json={'slotId':slot['id']}, headers={'Authorization':'Bearer '+winner['token'],
            'Idempotency-Key':winner['key']},timeout=30)
        assert response.status_code in (200,202,429), response.status_code
        if response.status_code != 429:
            assert response.json()['data']['reservationId']==selected[1]
        return response.status_code
    with ThreadPoolExecutor(max_workers=20) as pool:
        replays = list(pool.map(replay,range(100)))
    assert e.redis('HGET',stock_key,'remaining')=='0'
    assert e.mysql(f"SELECT COUNT(*) FROM civicflow_appointment.appointment_order WHERE slot_id={slot['id']};")=='50'
    # Actual service-identity failures, distinct from production secret transport assurance.
    now_seconds=int(time.time())
    claims={'iss':'https://auth.civicflow.local','sub':'civicflow-appointment',
        'aud':['civicflow-resource'],'iat':now_seconds,'exp':now_seconds+300,'jti':str(uuid.uuid4()),
        'serviceName':'civicflow-appointment','scope':'resource.slots.read'}
    cases=[('valid',{},service_kid,200),('expired',{'exp':now_seconds-60},service_kid,401),
        ('wrong-audience',{'aud':['other-service']},service_kid,401),
        ('wrong-scope',{'scope':'resource.windows.read'},service_kid,403),
        ('wrong-service',{'serviceName':'civicflow-queue'},service_kid,403),
        ('unknown-kid',{},'unpublished-key',401)]
    security=[]
    jwks=requests.get(f'http://127.0.0.1:{e.APP_PORT + 1}/.well-known/jwks.json',timeout=15).json()
    assert all(not {'d','p','q','dp','dq','qi'}.intersection(key) for key in jwks['keys'])
    for name, changes, kid, expected in cases:
        token=jwt.encode({**claims,**changes},service_key,algorithm='RS256',headers={'kid':kid})
        response=requests.get(f'http://127.0.0.1:{e.APP_PORT + 2}/internal/v1/resource/slots/reconciliation-candidates',
            params={'at':datetime.now(timezone.utc).isoformat()},headers={'Authorization':'Bearer '+token},timeout=15)
        assert response.status_code==expected,(name,response.status_code)
        security.append({'case':name,'status':response.status_code})
    metrics=summary.get('metrics',{})
    result={'run':e.RUN,'users':200,'vus':50,'quota':50,'orders':50,'uniqueUsers':50,'remaining':0,
        'stockSamples':len(samples),'minimumObservedStock':min(samples),'activeGuards':50,
        'slotId':slot['id'],'replayRequests':100,
        'replayStatusCounts':{str(code):replays.count(code) for code in set(replays)},
        'metrics':{name:metrics[name] for name in ('accepted','stock_empty','rate_limited','unexpected','http_req_duration','http_reqs','checks') if name in metrics},
        'serviceIdentity':security,'scope':'single-host bounded verification; not production capacity'}
    print('LOAD PASS '+json.dumps(result),flush=True)
    return result
