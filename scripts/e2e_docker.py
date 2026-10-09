"""Full seven-step flow against the running Compose stack; unique synthetic data is retained.

Business configuration uses APIs; booking, window authorization and check-in use the UI.
Requires the optional host test tools; the application itself runs entirely in Docker.
"""
from datetime import datetime, timedelta, timezone
import json
import subprocess
import time
import uuid

import e2e_v1 as e


def verify_facts(slot_id, ticket_id):
    """Read each service's persisted facts separately; also usable after a stack restart."""
    assert str(slot_id).isdigit() and str(ticket_id).isdigit()
    slot = e.mysql(f"SELECT item_id,DATE_FORMAT(service_date,'%Y%m%d'),total_quota,config_version "
                   f"FROM civicflow_resource.resource_slot WHERE id={slot_id};").split('\t')
    stock = 'cf:v1:dev:stock:{'+slot[0]+':'+slot[1]+'}:slot:'+str(slot_id)
    assert e.redis('HGET', stock, 'total') == slot[2] == '3'
    assert e.redis('HGET', stock, 'configVersion') == slot[3]
    assert e.redis('HGET', stock, 'remaining') == '1'
    orders = e.mysql(f"SELECT id,status FROM civicflow_appointment.appointment_order "
                     f"WHERE slot_id={slot_id} ORDER BY id;").splitlines()
    assert [row.split('\t')[1] for row in orders] == ['COMPLETED', 'EXPIRED', 'CONFIRMED']
    first_id = orders[0].split('\t')[0]
    ticket = e.mysql(f"SELECT appointment_id,status FROM civicflow_queue.queue_ticket WHERE id={ticket_id};")
    assert ticket == first_id+'\tCOMPLETED'
    appointment_ops = e.mysql(f"SELECT operation FROM civicflow_appointment.appointment_operation_log "
                              f"WHERE appointment_id={first_id} ORDER BY id;").splitlines()
    queue_ops = e.mysql(f"SELECT operation FROM civicflow_queue.queue_operation_log "
                        f"WHERE ticket_id={ticket_id} ORDER BY id;").splitlines()
    assert {'CREATE', 'CONFIRM', 'CHECK_IN', 'START_SERVING', 'COMPLETE'} <= set(appointment_ops)
    assert queue_ops == ['CHECK_IN', 'CALL', 'START_SERVING', 'COMPLETE']
    return {'orderStatuses': [row.split('\t')[1] for row in orders],
            'ticketStatus': 'COMPLETED', 'appointmentOperations': appointment_ops,
            'queueOperations': queue_ops, 'stockRemaining': 1, 'configVersion': slot[3]}


def main():
    e.BASE = 'http://127.0.0.1:5173'
    e.SESSION.trust_env = False
    e.MYSQL_CONTAINER = 'civicflow-mysql'
    e.REDIS_CONTAINER = 'civicflow-redis'
    keys = json.loads((e.ROOT / '.local/dev/keys.json').read_text())
    admin = e.api('POST', '/api/v1/auth/login', body={
        'loginName': keys.get('adminUsername', 'local_admin'), 'password': keys['adminPassword']})['accessToken']
    prefix = 'DOCKER_' + e.RUN
    password = uuid.uuid4().hex
    names = {}
    for role in ('ADMIN', 'USER', 'STAFF'):
        name = (prefix + '_' + role).lower()
        e.api('POST', '/api/v1/admin/users', admin, {
            'username': name, 'password': password, 'displayName': prefix, 'roles': [role]}, prefix + role)
        names[role] = name
    user_auth = e.api('POST', '/api/v1/auth/login', body={'loginName': names['USER'], 'password': password})
    staff_auth = e.api('POST', '/api/v1/auth/login', body={'loginName': names['STAFF'], 'password': password})
    user = user_auth['accessToken']
    e.denied('GET', '/api/v1/admin/outlets', 401)
    e.denied('GET', '/api/v1/admin/outlets', 403, user)
    e.denied('GET', '/internal/v1/resource/slots/1/snapshot', 403, user)
    outlet = e.api('POST', '/api/v1/admin/outlets', admin,
                   {'code': prefix, 'name': prefix, 'address': 'Synthetic Docker E2E'}, prefix+'outlet')
    item = e.api('POST', '/api/v1/admin/items', admin,
                 {'code': prefix, 'name': prefix, 'defaultDurationMinutes': 30}, prefix+'item')
    window = e.api('POST', '/api/v1/admin/windows', admin,
                   {'code': prefix, 'name': prefix, 'outletId': outlet['id']}, prefix+'window')
    e.api('PUT', f"/api/v1/admin/windows/{window['id']}/items", admin,
          {'version': window['version'], 'itemIds': [item['id']]}, prefix+'bind')
    now = datetime.now(e.SHANGHAI).replace(microsecond=0)
    start = now + timedelta(minutes=15)
    instant = lambda value: value.astimezone(timezone.utc).isoformat()
    slot = e.api('POST', '/api/v1/admin/slots', admin, {
        'outletId': outlet['id'], 'itemId': item['id'], 'serviceDate': start.date().isoformat(),
        'startTime': start.time().isoformat(), 'endTime': (start+timedelta(minutes=30)).time().isoformat(),
        'totalQuota': 3, 'releaseAt': instant(now+timedelta(seconds=15)),
        'checkInStart': instant(now-timedelta(minutes=1)), 'checkInEnd': instant(start+timedelta(minutes=20)),
        'status': 'SCHEDULED'}, prefix+'slot')
    preheat = e.api('POST', f"/api/v1/admin/stock/slots/{slot['id']}/preheat", admin)
    assert preheat['remaining'] == 3
    time.sleep(max(0, (now+timedelta(seconds=16)-datetime.now(e.SHANGHAI)).total_seconds()))
    e.api('PATCH', f"/api/v1/admin/slots/{slot['id']}/status", admin,
          {'version': slot['version'], 'status': 'OPEN'}, prefix+'open')
    e.api('POST', f"/api/v1/admin/stock/slots/{slot['id']}/preheat", admin)
    screenshots = e.ROOT/'target/e2e'/('ui-'+e.RUN)
    screenshots.mkdir(parents=True, exist_ok=True)
    browser = subprocess.run(['node', 'e2e/live-flow.mjs'], cwd=e.ROOT/'civicflow-web',
        input=json.dumps({'admin': names['ADMIN'], 'user': names['USER'], 'staff': names['STAFF'],
                          'password': password, 'itemId': item['id'], 'itemName': item['name'],
                          'serviceDate': slot['serviceDate'], 'windowId': window['id'], 'windowName': prefix,
                          'outletId': outlet['id'], 'outletName': prefix, 'screenshotDir': str(screenshots)}),
        capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=180)
    if browser.returncode:
        raise RuntimeError('Browser flow: '+e.safe_error(browser.stderr[-2500:]))
    browser_result = json.loads(browser.stdout.strip().splitlines()[-1])
    print('Docker Nginx three-role browser flow PASS', flush=True)
    stock = 'cf:v1:dev:stock:{'+item['id']+':'+start.strftime('%Y%m%d')+'}:slot:'+slot['id']
    assert e.redis('HGET', stock, 'remaining') == '2'
    second = e.api('POST', '/api/v1/user/appointments/reservations', user,
                   {'slotId': slot['id']}, prefix+'reserve2', expected=(202,))
    e.poll_reservation(user, second['reservationId'], 'PENDING_CONFIRM')
    print('Waiting for real five-minute timeout and service-identity rotation', flush=True)
    began = time.monotonic()
    while time.monotonic()-began < 345:
        if e.mysql("SELECT status FROM civicflow_appointment.appointment_order WHERE reservation_id='"+
                   second['reservationId']+"';") == 'EXPIRED':
            break
        time.sleep(2)
    else:
        raise TimeoutError('RabbitMQ timeout did not persist EXPIRED')
    elapsed = round(time.monotonic()-began, 2)
    assert e.redis('HGET', stock, 'remaining') == '2'
    third = e.api('POST', '/api/v1/user/appointments/reservations', user,
                  {'slotId': slot['id']}, prefix+'reserve3', expected=(202,))
    third_order = e.poll_reservation(user, third['reservationId'], 'PENDING_CONFIRM')['appointment']
    e.api('POST', f"/api/v1/user/appointments/{third_order['appointmentId']}/confirm", user,
          {'version': third_order['version']}, prefix+'confirm3')
    print('Waiting for reconciliation grace period', flush=True)
    time.sleep(125)
    reconciliation = e.api('POST', '/api/v1/admin/reconciliations', admin,
                           {'slotId': slot['id'], 'repair': False}, prefix+'reconcile')
    assert reconciliation['expected'] == reconciliation['actual'] == 1
    assert reconciliation['classification'] == 'CONSISTENT'
    release = e.mysql("SELECT COUNT(*) FROM civicflow_appointment.stock_release_record WHERE reservation_id='"+
                      second['reservationId']+"' AND status='SUCCEEDED';")
    assert release == '1'
    result = {'run': e.RUN, 'deployment': 'Docker Compose / Nginx', 'slotId': slot['id'],
              'browser': browser_result, 'timeoutSeconds': elapsed, 'successfulReleases': 1,
              'reconciliation': reconciliation,
              'facts': verify_facts(slot['id'], browser_result['ticketId'])}
    output = e.ROOT/'target/e2e'/('docker-'+e.RUN+'.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('DOCKER E2E PASS '+json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
