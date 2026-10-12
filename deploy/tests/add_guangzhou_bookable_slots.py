"""Add unused local Guangzhou slots for hands-on user reservation testing."""
import json
import time
import uuid
from datetime import datetime, timedelta, time as day_time, timezone
from pathlib import Path
from urllib.parse import urlencode
from seed_guangzhou_demo import Seeder, ROOT, ZONE

OUTPUT = ROOT / 'target/diagnostics/guangzhou-bookable.json'

class BookableSeeder(Seeder):
    def __init__(self):
        super().__init__()
        base = self.state
        self.state = json.loads(OUTPUT.read_text(encoding='utf-8')) if OUTPUT.exists() else {
            'label': '广州新增未预约号源（虚构演示数据）', 'run': uuid.uuid4().hex[:12],
            'outlets': base['outlets'], 'items': base['items'], 'accounts': base['accounts'],
            'windows': {}, 'slots': {}, 'appointments': {}, 'tickets': {}, 'sessions': {}, 'reports': {},
        }

    def save(self):
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        temp = OUTPUT.with_suffix('.tmp')
        temp.write_text(json.dumps(self.state, ensure_ascii=False, indent=2), encoding='utf-8')
        temp.replace(OUTPUT)

    def prepare(self, key, outlet, item, date, start, end, live=False):
        current = self.state['slots'].get(key)
        if current:
            current = self.api('GET', '/admin/slots/' + current['id'])
        else:
            # Initialize stock BEFORE release; opening all rows is a separate pass.
            now = datetime.now(ZONE)
            begin = datetime.combine(date, start, ZONE)
            finish = datetime.combine(date, end, ZONE)
            release = now + timedelta(seconds=45)
            if release >= begin: raise RuntimeError('The selected test period has already begun')
            instant = lambda value: value.astimezone(timezone.utc).isoformat()
            current = self.api('POST', '/admin/slots', payload={
                'outletId': outlet['id'], 'itemId': item['id'], 'serviceDate': date.isoformat(),
                'startTime': start.isoformat(), 'endTime': end.isoformat(), 'totalQuota': 30,
                'releaseAt': instant(release), 'checkInStart': instant(now - timedelta(minutes=5) if live else begin - timedelta(minutes=30)),
                'checkInEnd': instant(finish), 'status': 'DRAFT'}, operation='create-' + key)
            self.state['slots'][key] = current
            self.save()
        if current['status'] == 'DRAFT':
            self.api('POST', '/admin/stock/slots/' + current['id'] + '/preheat', operation='initialize-' + current['id'])
        self.state['slots'][key] = current
        self.save()

    def run(self):
        self.login('admin', 'admin', '1234')
        self.login('demo', 'demo_user', 'CivicFlow@2026')
        outlets = list(self.state['outlets'].values())
        items = list(self.state['items'].values())
        for outlet in outlets:
            code = 'GZTRY_' + outlet['code'].split('_')[-1] + '_W3'
            window = self.resource('windows', code, {'outletId':outlet['id'], 'name':'03号预约体验窗口（演示）'})
            if not self.state.get('bound',{}).get(code):
                self.api('PUT', '/admin/windows/' + window['id'] + '/items', payload={
                    'version':window['version'],'itemIds':[item['id'] for item in items]}, operation='bind-' + code)
                self.state.setdefault('bound',{})[code] = True
                self.save()
        today = datetime.now(ZONE).date()
        periods = [(day_time(16),day_time(17)),(day_time(17),day_time(18))]
        for day_index in range(1,8):
            date = today + timedelta(days=day_index)
            for outlet in outlets:
                for item in items:
                    for start,end in periods:
                        key = f'{date}-{outlet["code"]}-{item["code"]}-{start.hour}'
                        self.prepare(key,outlet,item,date,start,end)
            print('Prepared future date',date,'rows',len(self.state['slots']),flush=True)
        if datetime.now(ZONE).time() < day_time(23,15):
            for outlet in outlets:
                for item in items:
                    key = f'{today}-{outlet["code"]}-{item["code"]}-23'
                    self.prepare(key,outlet,item,today,day_time(23,20),day_time(23,55),True)
        for index,(key,current) in enumerate(list(self.state['slots'].items()),1):
            current = self.api('GET','/admin/slots/'+current['id'])
            if current['status'] == 'DRAFT':
                release = datetime.fromisoformat(current['releaseAt'].replace('Z','+00:00'))
                time.sleep(max(0,(release-datetime.now(timezone.utc)).total_seconds()) + 0.02)
                current = self.api('PATCH','/admin/slots/'+current['id']+'/status',payload={
                    'status':'OPEN','version':current['version']},operation='open-'+current['id'])
            if current['status'] != 'OPEN': raise RuntimeError('A test slot is unexpectedly unavailable: '+current['id'])
            self.api('POST','/admin/stock/slots/'+current['id']+'/preheat',operation='ready-'+current['id'])
            self.state['slots'][key] = current
            self.save()
            if index % 30 == 0: print('Opened',index,'/',len(self.state['slots']),flush=True)
        checks = []
        for day_index in range(1,8):
            date = today + timedelta(days=day_index)
            for outlet in outlets:
                for item in items:
                    result = self.api('GET','/user/slots?'+urlencode({'outletId':outlet['id'],'itemId':item['id'],'serviceDate':date.isoformat(),'size':100}),'demo')
                    expected = {row['id'] for row in self.state['slots'].values() if row['outletId']==outlet['id'] and row['itemId']==item['id'] and row['serviceDate']==date.isoformat()}
                    visible = {row['id'] for row in result['items'] if row['reservable']}
                    if not expected.issubset(visible): raise RuntimeError('A new slot is not visible to USER')
                    checks.append({'outletId':outlet['id'],'itemId':item['id'],'date':date.isoformat(),'newAvailable':len(expected)})
            print('User query verified',date,flush=True)
        self.state['verification']={'addedSlots':len(self.state['slots']),'newWindows':len(self.state['windows']),
            'quotaPerSlot':30,'firstDate':(today+timedelta(days=1)).isoformat(),'lastDate':(today+timedelta(days=7)).isoformat(),
            'futureOfferingDateQueries':len(checks),'verifiedAt':datetime.now(ZONE).isoformat()}
        self.save()
        print(json.dumps(self.state['verification']),flush=True)

if __name__ == '__main__':
    seed = BookableSeeder()
    try: seed.run()
    finally: seed.logout()
