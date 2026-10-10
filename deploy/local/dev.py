"""Persistent loopback-only developer launcher. Never deletes Docker volumes.

up: build and start infra + five services + Vite; prepare: infra/config for IDEA.
The supervisor owns only its child processes. stop never kills arbitrary PIDs.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
import uuid

import bcrypt
import jwt
import requests
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local' / 'dev'
ENV_FILE = ROOT / 'deploy/compose/.env'
SERVICES = {'auth': 8081, 'resource': 8082, 'appointment': 8083, 'queue': 8084, 'gateway': 8080}
GROUP = 'CIVICFLOW_LOCAL_LAUNCHER'
HTTP = requests.Session()
HTTP.trust_env = False
STOP = threading.Event()
CHILDREN = []
STATE = {'phase': 'starting', 'services': {}}
STATE_LOCK = threading.Lock()


def emit(message):
    print(message, flush=True)


def write_json(path, value):
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(value, indent=2), encoding='utf-8')
    # Windows readers (including IDEA/indexers) can briefly deny rename/delete sharing.
    for attempt in range(20):
        try:
            tmp.replace(path)
            return
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(0.05)


def command(args, *, data=None, timeout=180, log=None, env=None):
    result = subprocess.run(args, cwd=ROOT, input=data, capture_output=True,
                            timeout=timeout, env=env)
    if log:
        log.write_bytes(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed ({result.returncode}); '
                           f'check {log.relative_to(ROOT) if log else "local infrastructure credentials/configuration"}')
    return result.stdout.decode('utf-8', errors='replace')


def read_env(path=ENV_FILE):
    values = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, sep, value = line.partition('=')
        if not sep or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise RuntimeError('Invalid local .env entry')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def environment():
    if not ENV_FILE.exists():
        sample = ROOT / 'deploy/compose/.env.example'
        values = read_env(sample)
        for key in values:
            if 'PASSWORD' in key or key == 'NACOS_AUTH_IDENTITY_VALUE':
                values[key] = secrets.token_urlsafe(24)
        values['NACOS_AUTH_TOKEN'] = base64.b64encode(secrets.token_bytes(48)).decode()
        with ENV_FILE.open('x', encoding='utf-8') as handle:
            handle.write(''.join(f'{k}={v}\n' for k, v in values.items()))
    values = read_env()
    if int(values.get('NACOS_GRPC_PORT', 9848)) != int(values.get('NACOS_PORT', 8848)) + 1000:
        raise RuntimeError('NACOS_GRPC_PORT must equal NACOS_PORT + 1000')
    return values


def compose_project():
    # Existing installations used project "compose". Retain their ownership and volumes.
    projects = set()
    for name in ('mysql', 'redis', 'rabbitmq', 'nacos'):
        result = subprocess.run(['docker', 'inspect', 'civicflow-' + name], capture_output=True)
        if result.returncode == 0:
            labels = json.loads(result.stdout)[0]['Config'].get('Labels') or {}
            project = labels.get('com.docker.compose.project')
            if not project:
                raise RuntimeError('Existing civicflow container has no Compose owner')
            projects.add(project)
    if len(projects) > 1:
        raise RuntimeError('Existing CivicFlow containers belong to different Compose projects')
    return next(iter(projects), 'civicflow-local')


def docker_ready():
    check = subprocess.run(['docker', 'info'], capture_output=True)
    if check.returncode == 0:
        return
    if os.name == 'nt':
        paths = [Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'Docker/Docker/Docker Desktop.exe',
                 Path('D:/dockerDesktop/app/Docker Desktop.exe')]
        desktop = next((p for p in paths if p.exists()), None)
        if desktop:
            subprocess.Popen(['powershell.exe', '-NoProfile', '-Command',
                              'Start-Process -FilePath $env:CIVICFLOW_DOCKER_DESKTOP -WindowStyle Hidden'],
                             env={**os.environ, 'CIVICFLOW_DOCKER_DESKTOP': str(desktop)},
                             creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(120):
        if STOP.wait(1):
            raise RuntimeError('Startup stopped')
        if subprocess.run(['docker', 'info'], capture_output=True).returncode == 0:
            return
    raise RuntimeError('Docker Engine is unavailable; start Docker Desktop with Linux containers')


def mysql(sql):
    return command(['docker', 'exec', '-i', 'civicflow-mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot -N'],
                   data=sql.encode(), timeout=30)


def prepare_infra(values):
    docker_ready()
    project = compose_project()
    STATE['composeProject'] = project
    cmd = ['docker', 'compose', '-p', project, '--env-file', str(ENV_FILE),
           '-f', str(ROOT / 'deploy/compose/compose.yaml')]
    command(cmd + ['config', '--quiet'])
    command(cmd + ['up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis', 'rabbitmq', 'nacos'],
            timeout=300, log=LOCAL / 'compose.log')
    command(cmd + ['--profile', 'init', 'run', '--rm', 'nacos-init'],
            timeout=120, log=LOCAL / 'nacos-init.log')
    mysql('SELECT 1;')  # Actual authentication; mysqladmin ping can succeed on access denied.
    emit('Docker infrastructure and Nacos namespace ready')


def material(*, directory=None, query=None, admin_username=None, admin_password=None):
    path = (directory or LOCAL) / 'keys.json'
    query = query or mysql
    if path.exists():
        return json.loads(path.read_text())
    # Never silently rotate encryption keys for a previously populated database.
    count = query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN "
                  "('civicflow_auth','civicflow_resource','civicflow_appointment','civicflow_queue');")
    if int(count.strip()) > 0:
        raise RuntimeError('Existing database has tables but .local/dev/keys.json is missing. '
                           'Restore the previous development keys; existing data was not changed.')
    values = {}
    for prefix in ('user', 'service'):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        values[prefix + 'Private'] = base64.b64encode(key.private_bytes(
            serialization.Encoding.DER, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption())).decode()
        values[prefix + 'Public'] = base64.b64encode(key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
    for name in ('mobileEncryption', 'mobileHmac', 'contactEncryption', 'idempotencyHmac', 'checkin'):
        values[name] = base64.b64encode(secrets.token_bytes(32)).decode()
    values['adminPassword'] = admin_password or secrets.token_urlsafe(18)
    if admin_username:
        values['adminUsername'] = admin_username
    with path.open('x', encoding='utf-8') as handle:
        json.dump(values, handle, indent=2)
    return values


def property_text(values):
    def escape(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    return ''.join(f'{key}={escape(value)}\n' for key, value in values.items())


def configure(values, keys, *, directory=None, container=False):
    common = {k: v for k, v in values.items() if k.startswith(('NACOS_', 'REDIS_', 'RABBITMQ_'))}
    for k in ('NACOS_AUTH_TOKEN', 'NACOS_AUTH_IDENTITY_KEY', 'NACOS_AUTH_IDENTITY_VALUE'):
        common.pop(k, None)
    common.update({'NACOS_SERVER_ADDR': '127.0.0.1:' + values.get('NACOS_PORT', '8848'),
                   'NACOS_GROUP': GROUP, 'REDIS_HOST': '127.0.0.1', 'RABBITMQ_HOST': '127.0.0.1',
                   'spring.profiles.active': 'dev', 'server.address': '127.0.0.1',
                   'spring.cloud.nacos.discovery.ip': '127.0.0.1',
                   'CIVICFLOW_JWKS_URI': 'http://127.0.0.1:8081/.well-known/jwks.json',
                   'CIVICFLOW_GATEWAY_JWK_SET_URI': 'http://127.0.0.1:8081/.well-known/jwks.json',
                   'CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS': 'http://localhost:5173,http://127.0.0.1:5173'})
    if container:
        common.update({'NACOS_SERVER_ADDR': 'nacos:8848', 'NACOS_GROUP': 'CIVICFLOW_DOCKER',
                       'REDIS_HOST': 'redis', 'REDIS_PORT': '6379',
                       'RABBITMQ_HOST': 'rabbitmq', 'RABBITMQ_PORT': '5672',
                       'server.address': '0.0.0.0',
                       'CIVICFLOW_JWKS_URI': 'http://auth:8081/.well-known/jwks.json',
                       'CIVICFLOW_GATEWAY_JWK_SET_URI': 'http://auth:8081/.well-known/jwks.json',
                       'spring.cloud.sentinel.transport.dashboard': ''})
        common.pop('spring.cloud.nacos.discovery.ip', None)
    for name in SERVICES:
        config = dict(common)
        if name != 'gateway':
            config.update({k: v for k, v in values.items() if k.startswith('CIVICFLOW_' + name.upper() + '_DB_')})
            config['CIVICFLOW_' + name.upper() + '_DB_URL'] = (
                f'jdbc:mysql://{"mysql:3306" if container else "127.0.0.1:" + values.get("MYSQL_PORT", "3306")}/civicflow_{name}?'
                'useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true')
        if name == 'auth':
            config.update({'CIVICFLOW_AUTH_JWT_ACTIVE_KID': 'local-user-v1',
                'civicflow.auth.jwt.keys[0].kid': 'local-user-v1',
                'civicflow.auth.jwt.keys[0].public-key': keys['userPublic'],
                'civicflow.auth.jwt.keys[0].private-key': keys['userPrivate'],
                'civicflow.auth.jwt.keys[1].kid': 'local-service-v1',
                'civicflow.auth.jwt.keys[1].public-key': keys['servicePublic'],
                'CIVICFLOW_AUTH_MOBILE_ENCRYPTION_KEY': keys['mobileEncryption'],
                'CIVICFLOW_AUTH_MOBILE_HMAC_KEY': keys['mobileHmac']})
        if name == 'resource':
            config.update({'CIVICFLOW_CONTACT_ENCRYPTION_KEY': keys['contactEncryption'],
                           'CIVICFLOW_RESOURCE_IDEMPOTENCY_HMAC_KEY': keys['idempotencyHmac']})
        if name == 'appointment':
            config['CIVICFLOW_CHECKIN_SIGNING_KEY'] = keys['checkin']
        ((directory or LOCAL) / (name + '.properties')).write_text(property_text(config), encoding='utf-8')


def rotate_tokens(values, keys, *, base=None, group=None):
    base = base or 'http://127.0.0.1:' + values.get('NACOS_PORT', '8848') + '/nacos'
    login = HTTP.post(base + '/v1/auth/login', data={
        'username': values['NACOS_USERNAME'], 'password': values['NACOS_PASSWORD']}, timeout=10)
    if login.status_code != 200 or 'accessToken' not in login.json():
        raise RuntimeError('Nacos authentication failed during service-token rotation')
    access = login.json()['accessToken']
    key = serialization.load_der_private_key(base64.b64decode(keys['servicePrivate']), password=None)
    def token(name, scope):
        now = int(time.time())
        return jwt.encode({'iss': 'https://auth.civicflow.local', 'sub': 'civicflow-' + name,
            'aud': ['civicflow-api', 'civicflow-resource'], 'iat': now, 'exp': now + 300,
            'jti': str(uuid.uuid4()), 'serviceName': 'civicflow-' + name, 'scope': scope},
            key, algorithm='RS256', headers={'kid': 'local-service-v1'})
    configs = {'appointment': {'civicflow.appointment.resource-client.service-token':
        token('appointment', 'resource.slots.read')}, 'queue': {
        'civicflow.queue.resource-service-token': token('queue', 'resource.windows.read'),
        'civicflow.queue.appointment-service-token': token('queue', 'appointments.checkin appointments.queue-state')}}
    for name, content in configs.items():
        response = HTTP.post(base + '/v1/cs/configs', data={'accessToken': access,
            'tenant': values['NACOS_NAMESPACE'], 'group': group or GROUP, 'dataId': 'civicflow-' + name + '.yaml',
            'type': 'yaml', 'content': '\n'.join(k + ': ' + v for k, v in content.items())}, timeout=10)
        if response.status_code != 200 or response.text.strip() != 'true':
            raise RuntimeError('Nacos service-token publication failed')
    STATE['tokenRotatedAt'] = time.time()


def available(port):
    with socket.socket() as probe:
        probe.settimeout(0.3)
        return probe.connect_ex(('127.0.0.1', port)) != 0


def spawn(name, args, cwd=ROOT):
    handle = (LOCAL / (name + '.log')).open('w', encoding='utf-8')
    env = {k: v for k, v in os.environ.items() if not k.startswith(('SPRING_', 'CIVICFLOW_', 'NACOS_', 'REDIS_', 'RABBITMQ_'))}
    env['VITE_PROXY_TARGET'] = 'http://127.0.0.1:8080'
    process = subprocess.Popen(args, cwd=cwd, env=env, stdout=handle, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    CHILDREN.append((name, process, handle))
    return process


def healthy(port):
    try:
        path = '/' if port == 5173 else '/actuator/health'
        return HTTP.get(f'http://127.0.0.1:{port}' + path, timeout=2).status_code == 200
    except requests.RequestException:
        return False


def start_apps(keys):
    for port in [*SERVICES.values(), 5173]:
        if not available(port):
            raise RuntimeError(f'Port {port} is occupied. Stop its IDEA run before starting all; no process was killed.')
    for name, port in SERVICES.items():
        STATE['phase'] = 'starting ' + name
        jars = list((ROOT / f'civicflow-{name}/target').glob('civicflow-*.jar'))
        if len(jars) != 1:
            raise RuntimeError('Build the project first: missing/ambiguous JAR for ' + name)
        runtime = LOCAL / ('runtime-' + name + '.jar')
        shutil.copy2(jars[0], runtime)
        proc = spawn(name, ['java', '-Xms64m', '-Xmx384m', '-jar', str(runtime),
                          '--spring.config.additional-location=' + (LOCAL / (name + '.properties')).as_uri()])
        for _ in range(120):
            if proc.poll() is not None:
                raise RuntimeError(name + ' exited; check .local/dev/' + name + '.log')
            if healthy(port):
                break
            if STOP.wait(1):
                raise RuntimeError('Startup stopped')
        else:
            raise RuntimeError(name + ' health timeout; check .local/dev/' + name + '.log')
        STATE['services'][name] = 'UP'
        emit(name + ' ready')
        if name == 'auth':
            seed_admin(keys)
    proc = spawn('web', ['node', 'node_modules/vite/bin/vite.js', '--host', '127.0.0.1',
                        '--port', '5173', '--strictPort'], ROOT / 'civicflow-web')
    for _ in range(45):
        if proc.poll() is not None:
            raise RuntimeError('Vite exited; check .local/dev/web.log')
        if healthy(5173):
            STATE['services']['web'] = 'UP'
            STATE['phase'] = 'ready'
            return
        if STOP.wait(1):
            raise RuntimeError('Startup stopped')
    raise RuntimeError('Vite health timeout')


def seed_admin(keys, *, query=None, directory=None):
    query = query or mysql
    # Bootstrap only a synthetic local admin; never reset existing users/passwords.
    username = keys.get('adminUsername', 'local_admin')
    if not re.fullmatch(r'[a-z][a-z0-9_]{2,63}', username):
        raise RuntimeError('Invalid local bootstrap admin username')
    existing = query(f"SELECT COUNT(*) FROM civicflow_auth.sys_user WHERE username='{username}' AND deleted=0;")
    if int(existing.strip()):
        return
    ident = int(time.time() * 1000) * 1000
    digest = bcrypt.hashpw(keys['adminPassword'].encode(), bcrypt.gensalt()).decode()
    query(f"START TRANSACTION; INSERT INTO civicflow_auth.sys_user(id,username,password_hash,display_name,status) "
          f"VALUES ({ident},'{username}','{digest}','Local developer','ENABLED'); "
          f"INSERT INTO civicflow_auth.sys_user_role(id,user_id,role_id) SELECT {ident},{ident},id "
          "FROM civicflow_auth.sys_role WHERE role_code='ADMIN' AND status='ENABLED'; COMMIT;")
    ((directory or LOCAL) / 'login.txt').write_text('Local development only\nUsername: ' + username + '\nPassword: ' +
                                   keys['adminPassword'] + '\n', encoding='utf-8')


def read_state():
    try:
        return json.loads((LOCAL / 'state.json').read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def alive(state):
    return time.time() - state.get('heartbeat', 0) < 15 and state.get('phase') not in ('stopped', 'failed')


def supervisor_running():
    import msvcrt
    with (LOCAL / 'supervisor.lock').open('a+b') as lock:
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            return True
        msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
        return False


def supervise(mode):
    import msvcrt
    with (LOCAL / 'supervisor.lock').open('a+b') as lock:
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            return  # Another supervisor already owns this workspace.
        (LOCAL / 'stop.request').unlink(missing_ok=True)
        (LOCAL / 'start.request').unlink(missing_ok=True)
        STATE['pid'] = os.getpid()
        def heartbeat():
            while not STOP.wait(1):
                STATE['heartbeat'] = time.time()
                try:
                    with STATE_LOCK:
                        write_json(LOCAL / 'state.json', STATE)
                except OSError:
                    # A locked status file must not kill supervision or stop handling.
                    emit('Status file temporarily unavailable; retrying heartbeat')
                if (LOCAL / 'stop.request').exists():
                    STOP.set()
        threading.Thread(target=heartbeat, daemon=True).start()
        try:
            values = environment()
            prepare_infra(values)
            keys = material()
            configure(values, keys)
            rotate_tokens(values, keys)
            def rotate():
                while not STOP.wait(60):
                    try:
                        rotate_tokens(values, keys)
                        STATE.pop('rotationError', None)
                    except Exception:
                        STATE['rotationError'] = 'Service-token refresh failed; check Nacos credentials/health'
            threading.Thread(target=rotate, daemon=True).start()
            STATE['phase'] = 'prepared'
            if mode == 'up':
                start_apps(keys)
            while not STOP.wait(1):
                if (LOCAL / 'stop.request').exists():
                    STOP.set()
                    break
                if STATE['phase'] == 'prepared' and (LOCAL / 'start.request').exists():
                    (LOCAL / 'start.request').unlink()
                    start_apps(keys)
                for name, proc, _ in CHILDREN:
                    if proc.poll() is not None:
                        raise RuntimeError(name + ' stopped unexpectedly; check its local log')
        except Exception as exc:
            # Only our sanitized errors; never echo HTTP bodies, command lines or keys.
            STATE['error'] = str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__
            STATE['phase'] = 'failed'
            emit(STATE['error'])
        finally:
            STOP.set()
            for _, proc, handle in reversed(CHILDREN):
                if proc.poll() is None:
                    proc.terminate()
                    try:
                        proc.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        proc.kill()
                        proc.wait()
                handle.close()
            if STATE['phase'] != 'failed':
                STATE['phase'] = 'stopped'
            STATE['heartbeat'] = time.time()
            with STATE_LOCK:
                write_json(LOCAL / 'state.json', STATE)


def build():
    emit('Building Java services (see .local/dev/build.log)...')
    mvn = shutil.which('mvn.cmd') or shutil.which('mvn')
    if not mvn:
        raise RuntimeError('Maven is not on PATH')
    command([mvn, '-q', '-DskipTests', 'package'], timeout=600, log=LOCAL / 'build.log')
    if not (ROOT / 'civicflow-web/node_modules/vite/bin/vite.js').exists():
        npm = shutil.which('npm.cmd') or shutil.which('npm')
        if not npm:
            raise RuntimeError('Node/npm is not on PATH')
        command([npm, '--prefix', str(ROOT / 'civicflow-web'), 'ci'], timeout=300, log=LOCAL / 'npm.log')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['up', 'prepare', 'stop', 'status', '_serve'])
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--mode', default='prepare', choices=['up', 'prepare'])
    args = parser.parse_args()
    LOCAL.mkdir(parents=True, exist_ok=True)
    if args.action == '_serve':
        supervise(args.mode)
        return
    client_lock = None
    if args.action in ('up', 'prepare'):
        import msvcrt
        client_lock = (LOCAL / 'client.lock').open('a+b')
        client_lock.seek(0)
        try:
            msvcrt.locking(client_lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            raise RuntimeError('Another startup is in progress; use status and wait for it to finish')
    state = read_state()
    if args.action == 'status':
        emit(json.dumps({**state, 'supervisorAlive': supervisor_running(),
                         'heartbeatFresh': alive(state)}, indent=2))
        return
    if args.action == 'stop':
        if supervisor_running():
            (LOCAL / 'stop.request').touch()
            for _ in range(70):
                if not supervisor_running():
                    break
                time.sleep(1)
            else:
                raise RuntimeError('Stop still pending; no unrelated process was killed')
        emit('Launcher services stopped. Docker volumes and IDEA-owned processes retained.')
        return
    if supervisor_running() and not alive(state):
        raise RuntimeError('Supervisor heartbeat is stale; use stop, inspect launcher.log, then retry up')
    if alive(state) and state.get('phase') == 'ready':
        emit('Already running: http://localhost:5173')
        return
    if args.action == 'up' and not args.skip_build:
        build()
    # A Maven build may outlive the heartbeat window; re-read the live supervisor.
    state = read_state()
    if supervisor_running():
        if args.action == 'up':
            (LOCAL / 'start.request').touch()
    else:
        write_json(LOCAL / 'state.json', {'phase': 'starting', 'heartbeat': time.time()})
        log = (LOCAL / 'launcher.log').open('w')
        subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '_serve', '--mode', args.action],
                         cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                         creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        log.close()
    target = 'ready' if args.action == 'up' else 'prepared'
    last = None
    for _ in range(480):
        time.sleep(1)
        state = read_state()
        phase = state.get('phase')
        if phase != last:
            emit('Local launcher: ' + str(phase))
            last = phase
        if phase in (target, 'ready') and alive(state):
            emit('Ready. Web: http://localhost:5173' if phase == 'ready' else 'Ready for IDEA Local run configurations.')
            emit('Local credentials: .local/dev/login.txt (created after first auth startup).')
            return
        if phase == 'failed' and time.time() - state.get('heartbeat', 0) < 15:
            raise RuntimeError(state.get('error', 'Startup failed'))
    raise RuntimeError('Startup timed out; inspect .local/dev/launcher.log and state.json')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        emit('ERROR: ' + (str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        sys.exit(1)
