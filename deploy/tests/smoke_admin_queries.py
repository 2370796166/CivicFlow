"""Read-only local administrator query smoke test; credentials stay in memory."""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get('CIVICFLOW_SMOKE_URL', 'http://localhost:5173')
if not BASE.startswith(('http://localhost:', 'http://127.0.0.1:')):
    raise RuntimeError('This smoke test is restricted to local installations')

def call(path, token=None, payload=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + '/api/v1' + path, data=None if payload is None else json.dumps(payload).encode(), headers=headers)
    try:
        with urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)

status, login = call('/auth/login', payload={
    'loginName': os.environ.get('CIVICFLOW_SMOKE_LOGIN', 'admin'),
    'password': os.environ.get('CIVICFLOW_SMOKE_PASSWORD', '1234'),
})
assert status == 200, 'Local administrator login failed'
token = login['data']['accessToken']
results = []
try:
    paths = ['/admin/appointments', '/admin/reconciliations'] + ['/admin/operation-logs/' + source for source in ['appointment', 'queue', 'resource', 'auth', 'stock']]
    for path in paths:
        status, page = call(path + '?page=1&size=1', token)
        assert status == 200 and page['code'] == 'OK', (path, status, page.get('code'))
        assert len(page['data']['items']) <= 1
        status, _ = call(path)
        assert status == 401, (path, 'anonymous request was not rejected')
        status, _ = call(path + '?size=101', token)
        assert status == 400, (path, 'unbounded query was not rejected')
        results.append({'path': path, 'total': page['data']['total'], 'status': 'passed'})
        if path == '/admin/appointments' and page['data']['items']:
            record = page['data']['items'][0]
            identifier = record['appointment']['appointmentId']
            status, detail = call(path + '/' + identifier, token)
            assert status == 200 and detail['data']['appointment']['appointmentId'] == identifier
            assert not any(key in detail['data']['appointment'] for key in ['qrNonceHash', 'qrKeyId', 'cancelReason'])
            status, filtered = call(path + '?keyword=' + identifier, token)
            assert status == 200 and filtered['data']['total'] == 1
        if path == '/admin/reconciliations' and page['data']['items']:
            identifier = page['data']['items'][0]['runId']
            status, detail = call(path + '/' + identifier, token)
            assert status == 200
            status, again = call(path + '/' + identifier, token)
            assert status == 200 and detail['data'] == again['data']
            assert not any(key in detail['data'] for key in ['reportJson', 'idempotencyKeyHash', 'requestHash'])
        if '/operation-logs/' in path:
            for row in page['data']['items']:
                assert not any(key in row for key in ['detailJson', 'beforeJson', 'afterJson'])
    output = Path('target/diagnostics/admin-smoke.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'results': results}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(results, ensure_ascii=False))
finally:
    call('/auth/logout', token, {'refreshToken': login['data']['refreshToken']})
