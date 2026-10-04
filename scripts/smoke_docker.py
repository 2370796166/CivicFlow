"""Read-only Compose smoke, except for creating a normal authenticated login session."""
import json
import subprocess

import dev


def main():
    result = subprocess.run(['docker', 'compose', 'ps', '-a', '--format', 'json'],
                            capture_output=True, text=True, encoding='utf-8', check=True)
    rows = json.loads(result.stdout) if result.stdout.lstrip().startswith('[') else [
        json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    services = {row['Service']: row for row in rows}
    for name in ('mysql', 'redis', 'rabbitmq', 'nacos', 'service-identity', *dev.SERVICES, 'web'):
        row = services[name]
        assert row['State'] == 'running' and row['Health'] == 'healthy', (name, row['State'], row['Health'])
        print(name + ': healthy')
    for name in ('nacos-init', 'bootstrap', 'admin-init'):
        row = services[name]
        assert row['State'] == 'exited' and row['ExitCode'] == 0, (name, row['State'], row['ExitCode'])
    keys = json.loads((dev.LOCAL/'keys.json').read_text())
    base = 'http://127.0.0.1:5173'
    def request(method, path, status=200, **kwargs):
        response = dev.HTTP.request(method, base+path, timeout=20, **kwargs)
        print(method+' '+path+': '+str(response.status_code))
        assert response.status_code == status
        return response
    assert 'text/html' in request('GET', '/admin/outlets').headers['Content-Type']
    request('GET', '/api/v1/admin/outlets', 401)
    data = request('POST', '/api/v1/auth/login', json={
        'loginName': keys.get('adminUsername', 'local_admin'), 'password': keys['adminPassword']}).json()['data']
    headers = {'Authorization': 'Bearer '+data['accessToken']}
    for path in ('/api/v1/user/me', '/api/v1/admin/users', '/api/v1/admin/outlets'):
        request('GET', path, headers=headers)
    request('GET', '/api/v1/user/appointments', 403, headers=headers)
    request('GET', '/internal/v1/resource/slots/1/snapshot', 403, headers=headers)
    request('GET', '/actuator/health', 403)
    # Run a protected internal request from the identity container, without exposing tokens in argv/logs.
    probe = '''import os,datetime,dev
base='http://nacos:8848/nacos'
login=dev.HTTP.post(base+'/v1/auth/login',data={'username':os.environ['NACOS_USERNAME'],'password':os.environ['NACOS_PASSWORD']},timeout=10)
login.raise_for_status()
config=dev.HTTP.get(base+'/v1/cs/configs',params={'accessToken':login.json()['accessToken'],'tenant':os.environ.get('NACOS_NAMESPACE','dev'),'group':'CIVICFLOW_DOCKER','dataId':'civicflow-appointment.yaml'},timeout=10)
config.raise_for_status()
token=config.text.split(': ',1)[1].strip()
response=dev.HTTP.get('http://resource:8082/internal/v1/resource/slots/reconciliation-candidates',params={'at':datetime.datetime.now(datetime.timezone.utc).isoformat()},headers={'Authorization':'Bearer '+token},timeout=15)
assert response.status_code==200,response.status_code
print('Rotated service identity through Docker network: 200')
'''
    result = subprocess.run(['docker', 'compose', 'exec', '-T', 'service-identity', 'python', '-'],
                            input=probe, capture_output=True, text=True, encoding='utf-8', timeout=40)
    assert result.returncode == 0, 'Protected Docker service-identity probe failed'
    print(result.stdout.strip())
    print('PASS: complete Compose stack, Nginx routing, existing admin, RBAC, internal service identity')


if __name__ == '__main__':
    main()
