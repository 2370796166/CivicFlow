"""Read-only startup smoke (apart from login sessions); never prints credentials."""
from datetime import datetime, timezone
import json

import dev


def check(method, base, path, expected=200, **kwargs):
    response = dev.HTTP.request(method, base + path, timeout=15, **kwargs)
    print(f'{method} {base}{path}: {response.status_code}', flush=True)
    assert response.status_code == expected
    return response


def main():
    keys = json.loads((dev.LOCAL / 'keys.json').read_text())
    for port in dev.SERVICES.values():
        result = check('GET', f'http://127.0.0.1:{port}', '/actuator/health')
        assert result.json()['status'] == 'UP'
    check('GET', 'http://127.0.0.1:5173', '/')
    base = 'http://127.0.0.1:5173'
    check('GET', base, '/api/v1/admin/outlets', expected=401)
    result = check('POST', base, '/api/v1/auth/login', json={
        'loginName': keys.get('adminUsername', 'local_admin'), 'password': keys['adminPassword']})
    token = result.json()['data']['accessToken']
    headers = {'Authorization': 'Bearer ' + token}
    me = check('GET', base, '/api/v1/user/me', headers=headers).json()['data']
    assert 'ADMIN' in me['roles']
    for path in ('/api/v1/admin/outlets', '/api/v1/admin/users'):
        assert check('GET', base, path, headers=headers).json()['code'] == 'OK'
    check('GET', base, '/api/v1/admin/queues/overview', headers=headers,
          params={'outletId': '1', 'date': datetime.now().strftime('%Y-%m-%d')})
    check('GET', base, '/api/v1/user/appointments', headers=headers, expected=403)
    check('GET', 'http://127.0.0.1:8080', '/internal/v1/resource/slots/1/snapshot',
          headers=headers, expected=403)

    values = dev.read_env()
    nacos = 'http://127.0.0.1:' + values.get('NACOS_PORT', '8848') + '/nacos'
    access = check('POST', nacos, '/v1/auth/login', data={
        'username': values['NACOS_USERNAME'], 'password': values['NACOS_PASSWORD']}).json()['accessToken']
    content = check('GET', nacos, '/v1/cs/configs', params={
        'accessToken': access, 'tenant': values['NACOS_NAMESPACE'],
        'group': dev.GROUP, 'dataId': 'civicflow-appointment.yaml'}).text
    service_token = content.split(': ', 1)[1].strip()
    result = check('GET', 'http://127.0.0.1:8082', '/internal/v1/resource/slots/reconciliation-candidates',
        headers={'Authorization': 'Bearer ' + service_token},
        params={'at': datetime.now(timezone.utc).isoformat()})
    assert result.json()['code'] == 'OK'
    print('PASS: services, frontend proxy, ADMIN login, RBAC and rotated service JWT')


if __name__ == '__main__':
    main()
