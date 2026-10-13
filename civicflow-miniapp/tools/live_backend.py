"""Disposable synthetic integration environment, isolated from retained installs.

Build JARs first: mvn -B -DskipTests package
Run: python civicflow-miniapp/tools/live_backend.py
Uses only a uniquely named Compose project, loopback ports and new volumes.
No retained .env is read or changed. Cleanup removes only this run's resources.
"""
import base64
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import threading
import time
from datetime import datetime, timedelta, timezone

import bcrypt
import requests
import yaml
import sys
if hasattr(sys.stdout, 'reconfigure'): sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parents[2]
MINI = ROOT / 'civicflow-miniapp'
RUN = 'cf-mini-' + secrets.token_hex(4)
LOCAL = ROOT / '.local' / RUN
LOCAL.mkdir(parents=True)
spec = importlib.util.spec_from_file_location('local_dev', ROOT / 'deploy/local/dev.py')
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)
HTTP = requests.Session()
HTTP.trust_env = False
BASE = 'http://127.0.0.1:18080/api/v1'
dev.SERVICES = {name: port + 10000 for name, port in dev.SERVICES.items()}


def command(args, **kwargs):
    result = subprocess.run(args, capture_output=True, **kwargs)
    if result.returncode:
        (LOCAL / 'command-failure.log').write_bytes(result.stdout + result.stderr)
        # Commands can contain generated credentials; never include their argv.
        raise RuntimeError('Integration subprocess failed; see local log files')
    return result.stdout.decode(errors='replace').strip()


def mysql(sql):
    return command(['docker', 'exec', '-i', RUN + '-mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot --default-character-set=utf8mb4 -N'], input=sql.encode())


def api(method, path, token='', body=None, key=None):
    response = HTTP.request(method, BASE + path, json=body, headers={
        **({'Authorization': 'Bearer ' + token} if token else {}),
        **({'Idempotency-Key': key} if key else {})}, timeout=15)
    value = response.json()
    if response.status_code >= 300 or value.get('code') != 'OK':
        raise RuntimeError(f'{method} {path}: {response.status_code} {value.get("code")}')
    return value['data']


def main():
    ports = {'MYSQL_PORT': '33306', 'REDIS_PORT': '16379', 'RABBITMQ_PORT': '15674',
             'RABBITMQ_MANAGEMENT_PORT': '25674', 'NACOS_PORT': '18848', 'NACOS_GRPC_PORT': '19848'}
    for port in [15173, *dev.SERVICES.values(), *map(int, ports.values())]:
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} occupied; no existing process changed')
    values = dev.read_env(ROOT / 'deploy/compose/.env.example')
    for key in values:
        if 'PASSWORD' in key or key == 'NACOS_AUTH_IDENTITY_VALUE': values[key] = secrets.token_urlsafe(24)
    values.update(ports)
    values['CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS'] = 'http://127.0.0.1:15173'
    values.update(NACOS_NAMESPACE=RUN, NACOS_GROUP=dev.GROUP, NACOS_AUTH_TOKEN=base64.b64encode(secrets.token_bytes(48)).decode())
    env_file = LOCAL / '.env'
    env_file.write_text(''.join(f'{k}={v}\n' for k, v in values.items()), encoding='utf-8')
    source = ROOT / 'deploy/compose/compose.yaml'
    compose = yaml.safe_load(source.read_text(encoding='utf-8'))
    keep = {'mysql', 'redis', 'rabbitmq', 'nacos', 'nacos-init', 'notification-db-init'}
    compose['services'] = {k: v for k, v in compose['services'].items() if k in keep}
    compose['name'] = RUN
    compose['networks'] = {'civicflow-local': {'name': RUN + '-network', 'driver': 'bridge'}}
    compose['volumes'] = {name: {'name': RUN + '-' + name} for name in ('mysql-data', 'redis-data', 'rabbitmq-data', 'nacos-data')}
    # Windows checkouts can contain CRLF; container shell scripts must use LF.
    normalized = LOCAL / 'init'
    normalized.mkdir()
    for script in (source.parent / 'mysql/init').iterdir():
        (normalized / script.name).write_bytes(script.read_bytes().replace(b'\r\n', b'\n'))
    nacos_script = LOCAL / 'init-nacos.sh'
    nacos_script.write_bytes((source.parent / 'nacos/init-nacos.sh').read_bytes().replace(b'\r\n', b'\n'))
    for name, service in compose['services'].items():
        service['container_name'] = RUN + '-' + name
        service['restart'] = 'no'
        for i, volume in enumerate(service.get('volumes', [])):
            if volume.startswith('./'):
                path, target = volume.split(':', 1)
                original = (source.parent / path).resolve()
                if name == 'mysql': original = normalized / '01-create-service-databases.sh'
                elif name == 'notification-db-init': original = normalized / '02-ensure-notification-database.sh'
                elif name == 'nacos-init': original = nacos_script
                service['volumes'][i] = original.as_posix() + ':' + target
    config = LOCAL / 'compose.yaml'
    config.write_text(yaml.safe_dump(compose), encoding='utf-8')
    assert all(volume['name'].startswith(RUN + '-') for volume in compose['volumes'].values())
    assert compose['networks']['civicflow-local']['name'] == RUN + '-network'
    compose_cmd = ['docker', 'compose', '-p', RUN, '--env-file', str(env_file), '-f', str(config)]
    children = []; logs = []; stop = threading.Event()
    try:
        print('Starting disposable infrastructure ' + RUN, flush=True)
        command(compose_cmd + ['up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis', 'rabbitmq', 'nacos'], timeout=300)
        command(compose_cmd + ['run', '--rm', 'nacos-init'], timeout=120)
        command(compose_cmd + ['run', '--rm', 'notification-db-init'], timeout=120)
        keys = dev.material(directory=LOCAL, query=mysql)
        dev.configure(values, keys, directory=LOCAL)
        for name, port in dev.SERVICES.items():
            path = LOCAL / (name + '.properties')
            content = path.read_text(encoding='utf-8').replace('127.0.0.1:8081/', '127.0.0.1:18081/')
            content += f'\nserver.port={port}\n'
            path.write_text(content, encoding='utf-8')
        dev.rotate_tokens(values, keys)
        def rotate():
            while not stop.wait(100):
                try: dev.rotate_tokens(values, keys)
                except Exception: print('Service identity rotation failed', flush=True)
        threading.Thread(target=rotate, daemon=True).start()
        environment = {k: v for k, v in os.environ.items() if not k.startswith(('CIVICFLOW_', 'SPRING_', 'NACOS_', 'REDIS_', 'RABBITMQ_'))}
        pending_health = []
        def wait_app(name, child):
            port = dev.SERVICES[name]
            for _ in range(120):
                if child.poll() is not None: raise RuntimeError(name + ' exited; see local log')
                try:
                    if HTTP.get(f'http://127.0.0.1:{port}/actuator/health', timeout=2).status_code == 200: break
                except requests.RequestException: pass
                time.sleep(1)
            else: raise RuntimeError(name + ' health timeout')
            print(name + ' healthy', flush=True)
        for name in ['auth', 'resource', 'appointment', 'queue', 'notification', 'gateway']:
            jar = ROOT / f'civicflow-{name}/target/civicflow-{name}-0.1.0-SNAPSHOT.jar'
            runtime = LOCAL / jar.name; shutil.copy2(jar, runtime)
            log = (LOCAL / (name + '.log')).open('w', encoding='utf-8'); logs.append(log)
            args = ['java', '-Xmx256m', '-jar', str(runtime), '--spring.config.additional-location=file:' + (LOCAL / (name + '.properties')).as_posix()]
            child = subprocess.Popen(args, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT); children.append(child)
            if name == 'auth': wait_app(name, child)
            else: pending_health.append((name, child))
        for name, child in pending_health: wait_app(name, child)
        password = secrets.token_urlsafe(18)
        digest = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
        users = {}
        base = int(time.time() * 1000) * 1000
        for offset, (role, role_id) in enumerate([('admin', 3), ('user', 1), ('staff', 2)], 1):
            uid = base + offset; username = RUN + '-' + role
            mysql(f"INSERT INTO civicflow_auth.sys_user(id,username,password_hash,display_name,status,token_version,version,deleted) VALUES ({uid},'{username}','{digest}','小程序合成测试','ENABLED',0,0,0); INSERT INTO civicflow_auth.sys_user_role(id,user_id,role_id,deleted) VALUES ({uid},{uid},{role_id},0);")
            users[role] = {'name': username, 'id': str(uid)}
        admin = api('POST', '/auth/login', body={'loginName': users['admin']['name'], 'password': password})['accessToken']
        outlet = api('POST', '/admin/outlets', admin, {'code': RUN, 'name': '小程序联调服务中心', 'address': '隔离环境合成地址'}, RUN + '-outlet')
        item = api('POST', '/admin/items', admin, {'code': RUN, 'name': '合成测试办理事项', 'description': '仅用于本次自动化联调', 'defaultDurationMinutes': 15}, RUN + '-item')
        window = api('POST', '/admin/windows', admin, {'outletId': outlet['id'], 'code': RUN, 'name': '测试窗口'}, RUN + '-window')
        api('PUT', f"/admin/windows/{window['id']}/items", admin, {'itemIds': [item['id']], 'version': window['version']}, RUN + '-bind')
        mysql(f"INSERT INTO civicflow_resource.staff_window_scope(id,staff_user_id,outlet_id,window_id,deleted) VALUES ({base+4},{users['staff']['id']},{outlet['id']},{window['id']},0);")
        now = datetime.now(timezone(timedelta(hours=8))).replace(microsecond=0)
        start = now + timedelta(minutes=15); end = start + timedelta(minutes=30)
        utc = lambda d: d.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')
        release = now + timedelta(seconds=15)
        slot = api('POST', '/admin/slots', admin, {'outletId': outlet['id'], 'itemId': item['id'], 'serviceDate': start.date().isoformat(), 'startTime': start.time().isoformat(), 'endTime': end.time().isoformat(), 'totalQuota': 5, 'releaseAt': utc(release), 'checkInStart': utc(now - timedelta(minutes=1)), 'checkInEnd': utc(end), 'status': 'SCHEDULED'}, RUN + '-slot')
        api('POST', f"/admin/stock/slots/{slot['id']}/preheat", admin)
        time.sleep(max(0, (release - datetime.now(timezone(timedelta(hours=8)))).total_seconds()) + 1)
        slot = api('PATCH', f"/admin/slots/{slot['id']}/status", admin, {'status': 'OPEN', 'version': slot['version']}, RUN + '-slot-open')
        api('POST', f"/admin/stock/slots/{slot['id']}/preheat", admin)
        fixture = LOCAL / 'fixture.json'
        fixture.write_text(json.dumps({'base': BASE, 'password': password, 'users': users, 'outlet': outlet, 'item': item, 'window': window, 'slot': slot, 'webBase': 'http://127.0.0.1:15173'}), encoding='utf-8')
        web_log = (LOCAL / 'web.log').open('w', encoding='utf-8'); logs.append(web_log)
        web = subprocess.Popen(['node', 'node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '15173', '--strictPort'], cwd=ROOT / 'civicflow-web', env={**environment, 'VITE_PROXY_TARGET': 'http://127.0.0.1:18080'}, stdout=web_log, stderr=subprocess.STDOUT); children.append(web)
        for _ in range(45):
            if web.poll() is not None: raise RuntimeError('Web exited; run npm ci in civicflow-web first')
            try:
                if HTTP.get('http://127.0.0.1:15173', timeout=2).status_code == 200: break
            except requests.RequestException: pass
            time.sleep(1)
        else: raise RuntimeError('Web startup timed out')
        print('Synthetic data ready; running real miniapp request layer', flush=True)
        result = subprocess.run(['npm.cmd' if os.name == 'nt' else 'npm', 'test', '--', 'tests/live.integration.test.ts'], cwd=MINI,
                                env={**environment, 'CIVICFLOW_MINI_FIXTURE': str(fixture)}, capture_output=True, text=True, encoding='utf-8', errors='replace')
        (LOCAL / 'client-test.log').write_text(result.stdout + result.stderr, encoding='utf-8')
        print(result.stdout, flush=True)
        if result.returncode: raise RuntimeError('Live client test failed; see client-test.log')
        print('LIVE API CLOSED LOOP PASSED', flush=True)
    finally:
        stop.set()
        for name in ('mysql', 'redis', 'rabbitmq', 'nacos'):
            result = subprocess.run(['docker', 'logs', RUN + '-' + name], capture_output=True)
            (LOCAL / (name + '-container.log')).write_bytes(result.stdout + result.stderr)
        for child in reversed(children):
            child.terminate()
            try: child.wait(timeout=10)
            except subprocess.TimeoutExpired: child.kill(); child.wait()
        for log in logs: log.close()
        # Exact generated project only; no retained volumes or containers are targeted.
        subprocess.run(compose_cmd + ['down', '--volumes', '--remove-orphans'], capture_output=True)
        for name in ['fixture.json', 'keys.json', '.env', *[n + '.properties' for n in dev.SERVICES]]:
            (LOCAL / name).unlink(missing_ok=True)
        print('Disposable services and credentials cleaned; logs: ' + str(LOCAL), flush=True)


if __name__ == '__main__':
    main()
