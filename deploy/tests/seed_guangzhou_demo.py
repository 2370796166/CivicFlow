"""Add Guangzhou demonstration fixtures to the local Compose installation.

Business records use authenticated APIs. Only staff-window fixture scopes use SQL,
because the application has no public scope-management endpoint. Existing data is
retained. Checkpoints contain IDs, never credentials, tokens or QR payloads.
"""
import argparse
import json
import os
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'target/diagnostics/guangzhou-demo.json'
BASE = os.environ.get('CIVICFLOW_DEMO_URL', 'http://127.0.0.1:5173')
parsed = urlsplit(BASE)
if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password or parsed.path:
    raise RuntimeError('Only a local demonstration installation is supported')
ZONE = timezone(timedelta(hours=8))
PASSWORD = os.environ.get('CIVICFLOW_DEMO_PASSWORD', 'CivicFlow@2026')


class Seeder:
    def __init__(self):
        self.state = json.loads(OUTPUT.read_text(encoding='utf-8')) if OUTPUT.exists() else {
            'label': '广东广州演示数据（虚构地址和人物）', 'run': uuid.uuid4().hex[:12],
            'accounts': {}, 'outlets': {}, 'items': {}, 'windows': {}, 'slots': {},
            'appointments': {}, 'tickets': {}, 'sessions': {}, 'reports': {},
        }
        self.auth = {}

    def save(self):
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        temp = OUTPUT.with_suffix('.tmp')
        temp.write_text(json.dumps(self.state, ensure_ascii=False, indent=2), encoding='utf-8')
        temp.replace(OUTPUT)

    def api(self, method, path, actor='admin', payload=None, operation=None):
        headers = {'Content-Type': 'application/json'}
        if actor: headers['Authorization'] = 'Bearer ' + self.auth[actor]['accessToken']
        if method not in ('GET', 'HEAD'):
            headers['Idempotency-Key'] = 'gz-demo-' + self.state['run'] + '-' + (operation or uuid.uuid4().hex)
        request = Request(BASE + '/api/v1' + path, method=method,
                          data=None if payload is None else json.dumps(payload, ensure_ascii=False).encode(), headers=headers)
        try:
            with urlopen(request, timeout=30) as response: body = json.load(response)
        except HTTPError as error:
            try: code = json.load(error).get('code')
            except ValueError: code = 'invalid response'
            raise RuntimeError(f'{method} {path}: HTTP {error.code}, {code}') from None
        if body.get('code') != 'OK': raise RuntimeError(f'{method} {path}: {body.get("code")}')
        return body['data']

    def login(self, actor, username, password):
        self.auth[actor] = self.api('POST', '/auth/login', None, {'loginName': username, 'password': password})

    def logout(self):
        for actor, value in list(self.auth.items()):
            try: self.api('POST', '/auth/logout', actor, {'refreshToken': value['refreshToken']})
            except RuntimeError: pass

    def account(self, actor, username, display_name, role):
        page = self.api('GET', '/admin/users?' + urlencode({'keyword': username, 'size': 100}))
        current = next((x for x in page['items'] if x['username'] == username), None)
        if current is None:
            current = self.api('POST', '/admin/users', payload={
                'username': username, 'password': PASSWORD, 'displayName': display_name,
                'roles': [role]}, operation='account-' + username)
        if current['roles'] != [role]: raise RuntimeError('Demo account has an unexpected role: ' + username)
        self.state['accounts'][actor] = {'id': current['id'], 'username': username, 'role': role}
        self.save()
        if actor != 'disabled': self.login(actor, username, PASSWORD)
        return current

    def resource(self, kind, code, payload):
        current = self.state[kind].get(code)
        if current: return self.api('GET', f'/admin/{kind}/{current["id"]}')
        page = self.api('GET', f'/admin/{kind}?' + urlencode({'keyword': code, 'size': 100}))
        current = next((x for x in page['items'] if x['code'] == code), None)
        if not current:
            current = self.api('POST', '/admin/' + kind, payload={'code': code, **payload}, operation=code)
        self.state[kind][code] = current
        self.save()
        return current

    def slot(self, key, outlet, item, day, start, end, quota=12, status='OPEN', live=False):
        current = self.state['slots'].get(key)
        if current:
            current = self.api('GET', '/admin/slots/' + current['id'])
            if current['status'] == status:
                self.state['slots'][key] = current
                self.save()
                return current
        now = datetime.now(ZONE)
        start_dt = datetime.combine(day, start, ZONE)
        end_dt = datetime.combine(day, end, ZONE)
        release = now + timedelta(seconds=3) if status in ('OPEN', 'SUSPENDED') else now + timedelta(days=1)
        if status == 'SCHEDULED': release = start_dt - timedelta(hours=2)
        release = min(release,start_dt-timedelta(minutes=1))
        check_start = now - timedelta(minutes=10) if live else start_dt - timedelta(minutes=30)
        instant = lambda x: x.astimezone(timezone.utc).isoformat()
        if not current:
            current = self.api('POST', '/admin/slots', payload={
                'outletId': outlet['id'], 'itemId': item['id'], 'serviceDate': day.isoformat(),
                'startTime': start.isoformat(), 'endTime': end.isoformat(), 'totalQuota': quota,
                'releaseAt': instant(release), 'checkInStart': instant(check_start),
                'checkInEnd': instant(end_dt), 'status': 'SCHEDULED' if status == 'SCHEDULED' else 'DRAFT',
            }, operation='slot-' + key + '-' + item['id'])
        release = datetime.fromisoformat(current['releaseAt'].replace('Z','+00:00'))
        self.state['slots'][key] = current
        self.save()
        # The stock invariant requires initialization before release, including demos.
        if status in ('OPEN', 'SUSPENDED', 'SCHEDULED'):
            self.api('POST', '/admin/stock/slots/' + current['id'] + '/preheat', operation='preheat-before-' + key)
        if status in ('OPEN', 'SUSPENDED', 'CLOSED'):
            if status in ('OPEN', 'SUSPENDED'):
                time.sleep(max(0,(release-datetime.now(ZONE)).total_seconds()) + 0.1)
            transition = 'CLOSED' if status == 'CLOSED' else 'OPEN'
            if current['status'] == 'DRAFT':
                current = self.api('PATCH', '/admin/slots/' + current['id'] + '/status', payload={
                    'version': current['version'], 'status': transition}, operation='open-' + key + '-' + current['id'])
                self.state['slots'][key] = current
                self.save()
            if status != 'CLOSED':
                self.api('POST', '/admin/stock/slots/' + current['id'] + '/preheat', operation='preheat-open-' + key)
            if status == 'SUSPENDED':
                current = self.api('PATCH', '/admin/slots/' + current['id'] + '/status', payload={
                    'version': current['version'], 'status': 'SUSPENDED'}, operation='suspend-' + key + '-' + current['id'])
        if status in ('OPEN', 'SUSPENDED'):
            self.api('POST', '/admin/stock/slots/' + current['id'] + '/preheat', operation='preheat-' + key)
        self.state['slots'][key] = current
        self.save()
        return current

    def book(self, key, actor, slot):
        current = self.state['appointments'].get(key)
        if current: return self.api('GET', '/user/appointments/' + current['appointmentId'], actor)
        reservation = self.api('POST', '/user/appointments/reservations', actor,
                               {'slotId': slot['id']}, 'reserve-' + key)
        for _ in range(90):
            projection = self.api('GET', '/user/appointments/reservations/' + reservation['reservationId'], actor)
            if projection.get('appointment'):
                current = projection['appointment']
                self.state['appointments'][key] = {**current, 'actor': actor}
                self.save()
                return current
            if projection['status'] == 'FAILED': raise RuntimeError('Reservation failed: ' + str(projection.get('failureCode')))
            time.sleep(0.5)
        raise RuntimeError('Reservation did not become an order')

    def confirm(self, key, actor, slot):
        current = self.book(key, actor, slot)
        if current['status'] == 'PENDING_CONFIRM':
            current = self.api('POST', '/user/appointments/' + current['appointmentId'] + '/confirm', actor,
                               {'version': current['version']}, 'confirm-' + key)
        self.state['appointments'][key].update(current)
        self.save()
        return current

    def session(self, window, key):
        result = self.api('GET', '/staff/work-sessions/current?windowId=' + window['id'], 'staff')
        if result: session = result['session']
        else: session = self.api('POST', '/staff/work-sessions', 'staff', {'windowId': window['id']}, 'session-' + key)
        self.state['sessions'][key] = session
        self.save()
        return session

    def queue(self, key, actor, slot, session=None, target='WAITING'):
        appointment = self.confirm(key, actor, slot)
        if key not in self.state['tickets']:
            qr = self.api('POST', '/user/appointments/' + appointment['appointmentId'] + '/check-in-token', actor,
                          {'outletId': slot['outletId']})
            ticket = self.api('POST', '/user/check-ins', actor,
                              {'outletId': slot['outletId'], 'checkInToken': qr['token']}, 'check-in-' + key)
            self.state['tickets'][key] = ticket
            self.save()
        ticket = self.api('GET', '/user/queue-tickets/' + self.state['tickets'][key]['id'], actor)['ticket']
        if target != 'WAITING' and ticket['status'] == 'WAITING':
            ticket = self.api('POST', '/staff/work-sessions/' + session['id'] + '/call-next', 'staff',
                              {'itemIds': [slot['itemId']]}, 'call-' + key)
            if ticket is None or ticket['appointmentId'] != appointment['appointmentId']:
                raise RuntimeError('Unexpected queue ticket; preserve other queued records')
        if target in ('SERVING', 'COMPLETED') and ticket['status'] == 'CALLED':
            ticket = self.api('POST', '/staff/queue-tickets/' + ticket['id'] + '/start', 'staff',
                              {'sessionId': session['id'], 'version': ticket['version']}, 'start-' + key)
        if target == 'COMPLETED' and ticket['status'] == 'SERVING':
            ticket = self.api('POST', '/staff/queue-tickets/' + ticket['id'] + '/complete', 'staff',
                              {'sessionId': session['id'], 'version': ticket['version'], 'resultCode': 'DEMO_COMPLETED'}, 'complete-' + key)
        if target == 'MISSED' and ticket['status'] == 'CALLED':
            ticket = self.api('POST', '/staff/queue-tickets/' + ticket['id'] + '/miss', 'staff',
                              {'sessionId': session['id'], 'version': ticket['version']}, 'miss-' + key)
        if ticket['status'] != target: raise RuntimeError('Unexpected queue status: ' + ticket['status'])
        self.state['tickets'][key] = ticket
        self.save()

    def initialize(self, prepare_only=False):
        self.account('demo', 'demo_user', '陈嘉明', 'USER')
        self.account('user2', 'gz_demo_user2', '林晓彤', 'USER')
        self.account('user3', 'gz_demo_user3', '张志远', 'USER')
        disabled = self.account('disabled', 'gz_demo_disabled', '刘思怡', 'USER')
        if disabled['status'] == 'ENABLED':
            self.api('PATCH', '/admin/users/' + disabled['id'] + '/status', payload={
                'version': disabled['version'], 'status': 'DISABLED'}, operation='disable-sample')

        outlets = [self.resource('outlets', 'GZDEMO_' + code, {
            'name': '广州' + district + '综合服务站（演示）',
            'address': '广东省广州市' + district + '区演示路' + number + '号（虚构地址）',
        }) for code, district, number in [('TH','天河','100'),('YX','越秀','200'),('HZ','海珠','300')]]
        items = [self.resource('items', 'GZDEMO_' + code, {
            'name': name + '（演示）', 'description': description + '。仅用于广州样例演示，不代表实际办事政策。',
            'defaultDurationMinutes': duration,
        }) for code, name, description, duration in [
            ('SB','社保业务咨询','演示参保信息与业务咨询流程',20),
            ('GJJ','公积金业务咨询','演示住房公积金咨询预约流程',20),
            ('QY','企业登记咨询','演示企业登记材料咨询流程',30),
            ('JZ','居住登记咨询','演示居住登记咨询流程',15),
        ]]
        windows = []
        for outlet in outlets:
            for index in range(2):
                code = outlet['code'] + '_W' + str(index+1)
                window = self.resource('windows', code, {'outletId': outlet['id'], 'name': f'{index+1:02}号综合窗口（演示）'})
                if not self.state.get('bindings', {}).get(code):
                    self.api('PUT', '/admin/windows/' + window['id'] + '/items', payload={
                        'version': window['version'], 'itemIds': [x['id'] for x in items[index*2:index*2+2]]}, operation='bind-' + code)
                    self.state.setdefault('bindings', {})[code] = True
                    self.save()
                windows.append(window)

        if 'day' not in self.state:
            now = datetime.now(ZONE).replace(second=0, microsecond=0)
            if now.hour >= 23: raise RuntimeError('Run the live queue fixture before 23:00 local time')
            start = now + timedelta(minutes=20)
            end = min(start + timedelta(hours=2), now.replace(hour=23,minute=59))
            self.state.update(day=now.date().isoformat(), liveStart=start.time().isoformat(), liveEnd=end.time().isoformat())
            self.save()
        day = datetime.fromisoformat(self.state['day']).date()
        start = datetime.fromisoformat(self.state['day'] + 'T' + self.state['liveStart']).time()
        end = datetime.fromisoformat(self.state['day'] + 'T' + self.state['liveEnd']).time()
        convenience = self.resource('items', 'GZDEMO_BM', {
            'name':'便民综合咨询（演示）','description':'广州综合窗口的便民咨询演示流程，不代表实际办事政策。',
            'defaultDurationMinutes':15})
        original = self.state['slots'].get('live-0')
        if original and original['itemId'] == items[0]['id']:
            # Preserve the unused first fixture that was released without preheating.
            original = self.api('GET','/admin/slots/'+original['id'])
            if original['status'] != 'CLOSED':
                original = self.api('PATCH','/admin/slots/'+original['id']+'/status',payload={
                    'version':original['version'],'status':'CLOSED'},operation='close-initial-unused')
            self.state['slots']['closed-initial-unused'] = original
            del self.state['slots']['live-0']
            self.save()
        first_window = self.api('GET','/admin/windows/'+windows[0]['id'])
        if not self.state.get('convenienceBound'):
            self.api('PUT','/admin/windows/'+first_window['id']+'/items',payload={
                'version':first_window['version'],'itemIds':[items[0]['id'],items[1]['id'],convenience['id']]},operation='bind-convenience')
            self.state['convenienceBound']=True
            self.save()
        live_items = [convenience,*items[1:]]
        live = [self.slot('live-'+str(i), outlets[0], item, day, start, end, live=True) for i,item in enumerate(live_items)]
        # Start a genuine five-minute expiry scenario while the other fixtures are prepared.
        tomorrow = day + timedelta(days=1)
        expired_slot = self.slot('expiry',outlets[0],items[1],tomorrow,
                                 datetime.strptime('14:00','%H:%M').time(), datetime.strptime('16:00','%H:%M').time())
        self.book('expired', 'demo', expired_slot)
        self.state['expiryActor'] = 'demo'
        for offset in range(1,4):
            for oi,outlet in enumerate(outlets):
                for ii,item in enumerate(items):
                    status = 'SCHEDULED' if offset == 3 else 'OPEN'
                    self.slot(f'future-{offset}-{oi}-{ii}', outlet, item, day+timedelta(days=offset),
                              datetime.strptime('09:00','%H:%M').time(), datetime.strptime('12:00','%H:%M').time(), status=status)
            print(f'Guangzhou demo slots prepared for day +{offset}.',flush=True)
        self.slot('draft',outlets[2],items[3],day+timedelta(days=4),datetime.strptime('14:00','%H:%M').time(),datetime.strptime('16:00','%H:%M').time(),status='DRAFT')
        self.slot('suspended',outlets[1],items[0],tomorrow,datetime.strptime('14:00','%H:%M').time(),datetime.strptime('16:00','%H:%M').time(),status='SUSPENDED')
        self.slot('closed',outlets[2],items[0],tomorrow,datetime.strptime('14:00','%H:%M').time(),datetime.strptime('16:00','%H:%M').time(),status='CLOSED')
        self.confirm('future-confirmed','demo',self.state['slots']['future-1-0-2'])
        self.confirm('other-confirmed','user2',self.state['slots']['future-1-1-1'])
        cancellation = self.confirm('cancelled','demo',self.state['slots']['future-2-2-3'])
        if cancellation['status'] == 'CONFIRMED':
            self.api('POST', '/user/appointments/' + cancellation['appointmentId'] + '/cancel','demo',
                     {'version':cancellation['version'],'reason':'广州演示：行程调整'},'cancel-example')
        for index in range(3):
            slot = self.state['slots'][f'future-1-{index}-0']
            report = self.api('POST','/admin/reconciliations',payload={'slotId':slot['id'],'repair':False},operation='clean-report-'+str(index))
            self.state['reports']['clean-'+str(index)] = report
        self.state['resourcesReady'] = True
        self.save()
        if prepare_only:
            if not self.state.get('initialized'):
                self.queue('completed','demo',live[0])
                self.queue('missed','user2',live[0])
                self.queue('serving','user3',live[0])
                self.queue('waiting','demo',live[1])
                self.queue('called','user2',live[2])
                self.confirm('today-confirmed','user3',live[3])
            notices=self.api('GET','/user/notifications?size=100','demo')
            unread=[x for x in notices['items'] if x['status'] != 'READ']
            if unread: self.api('POST','/user/notifications/'+unread[-1]['id']+'/read','demo',operation='read-sample')
            for value in self.state['appointments'].values():
                value.update(self.api('GET','/user/appointments/'+value['appointmentId'],value['actor']))
            self.state['partialVerification']={
                'outlets':len(self.state['outlets']),'items':len(self.state['items']),
                'windows':len(self.state['windows']),'slots':len(self.state['slots']),
                'accounts':len(self.state['accounts']),'appointments':len(self.state['appointments']),
                'selfCheckInTickets':len(self.state['tickets']),'manualReports':len(self.state['reports']),
                'demoUserNotifications':notices['total'],
                'verifiedAt':datetime.now(ZONE).isoformat(),
            }
            self.save()
            print('Guangzhou demo resources, user appointments and reports prepared; no staff scopes changed.',flush=True)
            print(json.dumps(self.state['partialVerification'],ensure_ascii=False),flush=True)
            return
        self.prepare_staff(outlets[0], windows[:2])
        session1 = self.session(windows[0], 'first')
        session2 = self.session(windows[1], 'second')
        self.queue('completed','demo',live[0],session1,'COMPLETED')
        self.queue('missed','user2',live[0],session1,'MISSED')
        self.queue('serving','user3',live[0],session1,'SERVING')
        self.queue('waiting','demo',live[1])
        self.queue('called','user2',live[2],session2,'CALLED')
        self.confirm('today-confirmed','user3',live[3])
        self.confirm('future-confirmed','demo',self.state['slots']['future-1-0-2'])
        self.confirm('other-confirmed','user2',self.state['slots']['future-1-1-1'])
        cancellation = self.confirm('cancelled','demo',self.state['slots']['future-2-2-3'])
        if cancellation['status'] == 'CONFIRMED':
            self.api('POST', '/user/appointments/' + cancellation['appointmentId'] + '/cancel','demo',
                     {'version':cancellation['version'],'reason':'广州演示：行程调整'},'cancel-example')
        for index in range(3):
            slot = self.state['slots'][f'future-1-{index}-0']
            report = self.api('POST','/admin/reconciliations',payload={'slotId':slot['id'],'repair':False},operation='clean-report-'+str(index))
            self.state['reports']['clean-'+str(index)] = report
        self.state['initialized'] = True
        self.save()
        print('Guangzhou demo resources and business flows initialized.',flush=True)

    def prepare_staff(self, outlet, windows):
        # Requires explicit approval for this account and these two demonstration windows.
        self.account('staff', 'gz_demo_staff', '广州演示窗口工作人员', 'STAFF')
        if not self.state.get('scopesReady'):
            ids = [self.state['accounts']['staff']['id'], outlet['id'], windows[0]['id'], windows[1]['id']]
            if not all(str(x).isdigit() and 0 < int(x) < 2**63 for x in ids): raise RuntimeError('Invalid scope IDs')
            staff, outlet_id, window1, window2 = map(int, ids)
            scopes = []
            for index, window in enumerate((window1, window2)):
                scope_id = int(time.time()*1000)*1000 + index
                scopes.append(f'INSERT INTO civicflow_resource.staff_window_scope(id,staff_user_id,outlet_id,window_id,deleted) SELECT {scope_id},{staff},{outlet_id},{window},0 WHERE NOT EXISTS (SELECT 1 FROM civicflow_resource.staff_window_scope WHERE staff_user_id={staff} AND outlet_id={outlet_id} AND window_id={window} AND deleted=0);')
            result = subprocess.run(['docker','exec','-i','civicflow-mysql','sh','-lc',
                                     'mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" -N'],
                                    input='\n'.join(scopes), text=True, capture_output=True)
            if result.returncode: raise RuntimeError('Demo staff scope initialization failed')
            self.state['scopesReady'] = True
            self.save()
        scopes = self.api('GET', '/staff/scopes', 'staff')
        if len(scopes) < 2: raise RuntimeError('Demo staff windows are unavailable')

    def finish(self):
        if not self.state.get('initialized'): raise RuntimeError('Run --stage initial first')
        for actor,value in self.state['accounts'].items():
            if actor != 'disabled': self.login(actor,value['username'],PASSWORD)
        expiry = self.state['appointments']['expired']
        deadline = datetime.fromisoformat(expiry['confirmDeadline'].replace('Z','+00:00'))
        remaining = max(0,(deadline-datetime.now(timezone.utc)).total_seconds())
        if remaining > 0:
            print(f'Expiry fixture needs another {int(remaining)+1}s; all other samples are ready.',flush=True)
            return False
        for _ in range(30):
            current = self.api('GET','/user/appointments/'+expiry['appointmentId'],'demo')
            if current['status'] == 'EXPIRED': break
            time.sleep(1)
        else: raise RuntimeError('Automatic expiry has not settled')
        for key in ('live-0','live-1','live-2','live-3','expiry'):
            slot = self.state['slots'][key]
            report = self.api('POST','/admin/reconciliations',payload={'slotId':slot['id'],'repair':False},operation='settled-report-'+key)
            self.state['reports'][key] = report
        notices = self.api('GET','/user/notifications?size=100','demo')
        unread = [x for x in notices['items'] if x['status'] != 'READ']
        if unread:
            self.api('POST','/user/notifications/'+unread[-1]['id']+'/read','demo',operation='read-sample')
        # Fresh pending record demonstrates the confirmation action; it expires normally.
        self.book('pending','demo',self.state['slots']['future-2-0-0'])
        appointments=[]
        for key,value in self.state['appointments'].items():
            current=self.api('GET','/user/appointments/'+value['appointmentId'],value['actor'])
            self.state['appointments'][key].update(current)
            appointments.append(current)
        required={'COMPLETED','NO_SHOW','SERVING','CHECKED_IN','CONFIRMED','CANCELLED','EXPIRED','PENDING_CONFIRM'}
        if not required <= {x['status'] for x in appointments}: raise RuntimeError('Demo appointment states incomplete')
        inbox=self.api('GET','/user/notifications?size=100','demo')
        self.state['verification']={
            'outlets':len(self.state['outlets']),'items':len(self.state['items']),
            'windows':len(self.state['windows']),'slots':len(self.state['slots']),
            'accounts':len(self.state['accounts']),'appointments':len(appointments),
            'appointmentStates':sorted({x['status'] for x in appointments}),
            'tickets':len(self.state['tickets']),'queueStates':sorted({x['status'] for x in self.state['tickets'].values()}),
            'manualReports':len(self.state['reports']),'demoUserNotifications':inbox['total'],
            'verifiedAt':datetime.now(ZONE).isoformat(),
        }
        for source in ('appointment','queue','resource','auth','stock'):
            page=self.api('GET','/admin/operation-logs/'+source+'?size=1')
            if page['total'] == 0: raise RuntimeError('Missing operation log source: '+source)
        self.save()
        print(json.dumps(self.state['verification'],ensure_ascii=False),flush=True)
        return True

    def verify_resources(self):
        if not self.state.get('resourcesReady'): raise RuntimeError('Run --stage resources first')
        for actor,value in self.state['accounts'].items():
            if actor != 'disabled': self.login(actor,value['username'],PASSWORD)
        self.book('pending','demo',self.state['slots']['future-2-0-0'])
        for value in self.state['appointments'].values():
            value.update(self.api('GET','/user/appointments/'+value['appointmentId'],value['actor']))
        for key,value in self.state['tickets'].items():
            actor=self.state['appointments'][key]['actor']
            self.state['tickets'][key]=self.api('GET','/user/queue-tickets/'+value['id'],actor)['ticket']
        available=self.api('GET','/user/outlets?keyword='+urlencode({'q':'广州'}).split('=',1)[1]+'&size=100','demo')
        if len(available['items']) != 3: raise RuntimeError('Guangzhou demo outlets are unavailable to ordinary users')
        for outlet in self.state['outlets'].values():
            items=self.api('GET','/user/outlets/'+outlet['id']+'/items?size=100','demo')
            if len(items['items']) < 4: raise RuntimeError('Demo outlet lacks available services')
        inbox=self.api('GET','/user/notifications?size=100','demo')
        statuses=sorted({x['status'] for x in self.state['appointments'].values()})
        self.state['partialVerification'].update(appointments=len(self.state['appointments']),appointmentStates=statuses,demoUserNotifications=inbox['total'],
                                                verifiedAt=datetime.now(ZONE).isoformat())
        for source in ('appointment','queue','resource','auth','stock'):
            page=self.api('GET','/admin/operation-logs/'+source+'?size=1')
            if not page['total']: raise RuntimeError('Empty operation log: '+source)
        self.save()
        print(json.dumps(self.state['partialVerification'],ensure_ascii=False),flush=True)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--stage',choices=['resources','verify','initial','finish'],required=True)
    args=parser.parse_args()
    seeder=Seeder()
    seeder.login('admin',os.environ.get('CIVICFLOW_DEMO_ADMIN','admin'),os.environ.get('CIVICFLOW_DEMO_ADMIN_PASSWORD','1234'))
    try:
        if args.stage=='resources': seeder.initialize(prepare_only=True)
        elif args.stage=='verify': seeder.verify_resources()
        elif args.stage=='initial': seeder.initialize()
        else: seeder.finish()
    finally: seeder.logout()


if __name__=='__main__': main()
