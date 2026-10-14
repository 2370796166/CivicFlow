"""Real Nginx -> current gateway JAR login-rate regression in disposable containers.

Requires local civicflow/web:local and civicflow/gateway:local runtime images,
and a freshly packaged gateway JAR. Never reads retained credentials or volumes.
"""
import json
from pathlib import Path
import re
import secrets
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
RUN = 'cf-proxy-' + secrets.token_hex(4)
LOCAL = ROOT / '.local' / RUN
SUBNET = '172.31.251.0/29'
PROXY_IP = '172.31.251.2'


def docker(*args, check=True):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, timeout=90)
    if check and result.returncode:
        raise RuntimeError(result.stderr[-1000:])
    return result


def main():
    LOCAL.mkdir(parents=True)
    jar = ROOT / 'civicflow-gateway/target/civicflow-gateway-0.1.0-SNAPSHOT.jar'
    if not jar.exists():
        raise RuntimeError('Package gateway first')
    fake = LOCAL / 'fake.conf'
    fake.write_text('server { listen 8080; location / { default_type application/json; return 200 \'{"code":"OK","message":"synthetic","data":null,"requestId":"proxy-test"}\'; } }\n', encoding='utf-8')
    # Test the actual shipped Nginx configuration, including its header overwrite.
    nginx = LOCAL / 'nginx.conf'
    nginx.write_bytes((ROOT / 'deploy/docker/nginx.conf').read_bytes().replace(b'\r\n', b'\n'))
    names = []
    gateway = RUN + '-gateway'
    network_created = False
    try:
        docker('network', 'create', '--subnet', SUBNET, RUN)
        network_created = True
        def start(suffix, image, *args, ip=None, entrypoint=None):
            name = RUN + '-' + suffix
            names.append(name)
            docker('run', '-d', '--name', name, '--network', RUN,
                   *(['--ip', ip] if ip else []),
                   *(['--entrypoint', entrypoint] if entrypoint else []), *args, image,
                   *(['-g', 'daemon off;'] if entrypoint == 'nginx' else []))
            return name
        start('fake', 'civicflow/web:local', '--network-alias', 'fake',
              '--mount', f'type=bind,source={fake},target=/etc/nginx/conf.d/default.conf,readonly', ip='172.31.251.6', entrypoint='nginx')
        names.append(gateway)
        docker('run', '-d', '--name', gateway, '--network', RUN, '--ip', '172.31.251.3', '--network-alias', 'gateway',
               '--mount', f'type=bind,source={jar},target=/app/server.jar,readonly',
               '--entrypoint', 'java', 'civicflow/gateway:local', '-jar', '/app/server.jar',
               '--spring.profiles.active=test', '--server.forward-headers-strategy=none',
               '--civicflow.gateway.trusted-proxies=' + PROXY_IP + '/32',
               '--spring.cloud.gateway.server.webflux.routes[0].id=proxy-regression',
               '--spring.cloud.gateway.server.webflux.routes[0].uri=http://fake:8080',
               '--spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/v1/auth/**',
               '--civicflow.gateway.rate-limit.login-permits=2',
               '--civicflow.gateway.rate-limit.login-window=PT60S')
        proxy = start('nginx', 'civicflow/web:local', '--network-alias', 'ingress',
                      '--mount', f'type=bind,source={nginx},target=/etc/nginx/conf.d/default.conf,readonly',
                      ip=PROXY_IP, entrypoint='nginx')
        for _ in range(90):
            if docker('inspect', '-f', '{{.State.Running}}', gateway).stdout.strip() != 'true':
                raise RuntimeError('Gateway exited; inspect this run gateway.log')
            health = docker('exec', proxy, 'wget', '-T', '2', '-qO-', 'http://gateway:8080/actuator/health', check=False)
            if health.returncode == 0: break
            time.sleep(1)
        else: raise RuntimeError('Gateway startup timed out')
        clients = []
        for number in (4, 5):
            name = RUN + '-client-' + str(number)
            names.append(name)
            docker('run', '-d', '--name', name, '--network', RUN, '--ip', '172.31.251.' + str(number),
                   '--entrypoint', 'sleep', 'civicflow/web:local', '600')
            clients.append(name)
        def request(client, host='ingress'):
            result = docker('exec', client, 'wget', '-S', '-O', '/dev/null', '--post-data', '{}',
                            '--header', 'X-Forwarded-For: 203.0.113.99',
                            f'http://{host}:8080/api/v1/auth/login', check=False)
            codes = re.findall(r'HTTP/\S+ (\d{3})', result.stderr)
            if not codes: raise RuntimeError('No HTTP status: ' + result.stderr[-500:])
            return int(codes[-1])
        results = {client.rsplit('-', 1)[-1]: [request(client) for _ in range(3)] for client in clients}
        assert all(value == [200, 200, 429] for value in results.values()), results
        # Direct clients cannot escape their exhausted bucket with a forged forwarding header.
        direct = request(clients[0], 'gateway')
        assert direct == 429, direct
        (LOCAL / 'result.json').write_text(json.dumps({'clients': results, 'untrustedDirect': direct}), encoding='utf-8')
        print('REAL NGINX LOGIN RATE ISOLATION PASSED')
    finally:
        if gateway in names:
            (LOCAL / 'gateway.log').write_text(docker('logs', gateway, check=False).stdout, encoding='utf-8')
        for name in reversed(names):
            assert name.startswith(RUN + '-')
            docker('rm', '-f', name, check=False)
        if network_created:
            docker('network', 'rm', RUN, check=False)


if __name__ == '__main__':
    main()
