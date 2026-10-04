"""Local, synthetic gateway E2E. Run after `mvn verify` with Docker available.

Requires Python packages requests, bcrypt, cryptography and PyJWT. No secret or
token is printed or written to the repository. Existing service data is retained.
"""

import base64
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import uuid
import zipfile
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import bcrypt
import jwt
import requests
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa


ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8080"
SHANGHAI = ZoneInfo("Asia/Shanghai")
RUN = secrets.token_hex(4).upper()
MYSQL_CONTAINER = "civicflow-e2e-mysql-" + RUN.lower()
NACOS_CONTAINER = "civicflow-e2e-nacos-" + RUN.lower()
REDIS_CONTAINER = "civicflow-e2e-redis-" + RUN.lower()
RABBIT_CONTAINER = "civicflow-e2e-rabbit-" + RUN.lower()
MYSQL_PORT = 33306
SESSION = requests.Session()


def safe_error(value):
    return re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED_TOKEN]", str(value))


def denied(method, path, status, token=None, body=None):
    headers = {"X-User-Id": "1", "X-User-Roles": "ADMIN"}
    if token:
        headers["Authorization"] = "Bearer " + token
    response = SESSION.request(method, BASE + path, headers=headers, json=body, timeout=15)
    assert response.status_code == status, (path, response.status_code)
    print(f"SECURITY {method} {path} -> {status}", flush=True)


def local_env():
    values = os.environ.copy()
    # No dependency on retained Compose secrets, volumes or application overrides.
    for key in list(values):
        if key.startswith(("CIVICFLOW_", "SPRING_", "NACOS_", "REDIS_", "RABBITMQ_")):
            del values[key]
    values.update(REDIS_HOST="127.0.0.1", REDIS_PORT="16379", REDIS_PASSWORD=secrets.token_urlsafe(24),
                  VITE_PROXY_TARGET="http://127.0.0.1:8080",
                  RABBITMQ_HOST="127.0.0.1", RABBITMQ_PORT="15673", RABBITMQ_USERNAME="e2e",
                  RABBITMQ_PASSWORD=secrets.token_urlsafe(24), RABBITMQ_VHOST="e2e",
                  NACOS_USERNAME="nacos", NACOS_PASSWORD=secrets.token_urlsafe(24),
                  NACOS_NAMESPACE="e2e", NACOS_AUTH_TOKEN=base64.b64encode(secrets.token_bytes(48)).decode(),
                  NACOS_AUTH_IDENTITY_KEY="e2e", NACOS_AUTH_IDENTITY_VALUE=secrets.token_urlsafe(24))
    for service in ("AUTH", "RESOURCE", "APPOINTMENT", "QUEUE"):
        for kind in ("APP", "MIGRATION"):
            values[f"CIVICFLOW_{service}_DB_{kind}_USERNAME"] = f"cf_{service.lower()}_{kind.lower()}"
    return values


def mysql(sql):
    command = ["docker", "exec", "-i", MYSQL_CONTAINER, "sh", "-lc",
               'mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" -N']
    result = subprocess.run(command, input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("MySQL command failed: " + result.stderr.splitlines()[-1][:250])
    return result.stdout.strip()


def redis(*arguments):
    result = subprocess.run(["docker", "exec", REDIS_CONTAINER, "sh", "-c",
        'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw "$@"', "redis-cli", *arguments],
        capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Redis evidence read failed")
    return result.stdout.strip()


def wait_health(port, process, seconds=100):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if process.poll() is not None:
            raise RuntimeError(f"service port {port} exited with code {process.returncode}")
        try:
            if requests.get(f"http://127.0.0.1:{port}/actuator/health", timeout=2).status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(1)
    raise TimeoutError(f"service port {port} not healthy")


def api(method, path, token=None, body=None, key=None, expected=(200,), quiet=False):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    response = SESSION.request(method, BASE + path, headers=headers, json=body, timeout=15)
    try:
        payload = response.json()
    except ValueError:
        payload = {}
    if response.status_code not in expected or payload.get("code") != "OK":
        raise RuntimeError(f"{method} {path}: HTTP {response.status_code}, code={payload.get('code')}, message={payload.get('message')}")
    if not quiet:
        print(f"{method} {path} -> {response.status_code} {payload['code']}", flush=True)
    return payload["data"]


def poll_reservation(token, reservation_id, expected, seconds=35):
    end = time.monotonic() + seconds
    last = None
    while time.monotonic() < end:
        result = api("GET", f"/api/v1/user/appointments/reservations/{reservation_id}", token,
                     quiet=True)
        if result["status"] != last:
            print(f"reservation {reservation_id} -> {result['status']}", flush=True)
            last = result["status"]
        if result["status"] == expected:
            return result
        if result["status"] == "FAILED":
            raise RuntimeError(f"reservation failed: {result.get('failureCode')}")
        time.sleep(1)
    raise TimeoutError(f"reservation {reservation_id} did not become {expected}")


def service_jwt(private_key, kid, service_name, scope):
    now = int(time.time())
    claims = {"iss": "https://auth.civicflow.local", "sub": service_name,
              "aud": ["civicflow-api", "civicflow-resource"], "iat": now,
              "exp": now + 300, "jti": str(uuid.uuid4()), "serviceName": service_name, "scope": scope}
    return jwt.encode(claims, private_key, algorithm="RS256", headers={"kid": kid})


def ensure_nacos(environment):
    base = "http://127.0.0.1:18848/nacos"
    credentials = {"username": environment["NACOS_USERNAME"],
                   "password": environment["NACOS_PASSWORD"]}
    login = requests.post(base + "/v1/auth/login", data=credentials, timeout=8)
    if login.status_code != 200:
        initialized = requests.post(base + "/v1/auth/users/admin",
                      data={"password": credentials["password"]}, timeout=8)
        login = requests.post(base + "/v1/auth/login", data=credentials, timeout=8)
        if login.status_code != 200:
            raise RuntimeError(f"Nacos init HTTP {initialized.status_code}, login HTTP {login.status_code}")
    login.raise_for_status()
    token = login.json()["accessToken"]
    namespace = environment.get("NACOS_NAMESPACE", "dev")
    listing = requests.get(base + "/v2/console/namespace/list",
                           params={"accessToken": token}, timeout=8)
    listing.raise_for_status()
    if namespace not in listing.text:
        created = requests.post(base + "/v2/console/namespace", data={
            "accessToken": token, "namespaceId": namespace,
            "namespaceName": namespace, "namespaceDesc": "CivicFlow E2E local"}, timeout=8)
        created.raise_for_status()
    print("Nacos login and namespace ready", flush=True)
    return token


def publish_service_tokens(environment, private_key, kid, nacos_token):
    configs = {
        "appointment": {"civicflow.appointment.resource-client.service-token":
            service_jwt(private_key, kid, "civicflow-appointment", "resource.slots.read")},
        "queue": {
            "civicflow.queue.resource-service-token": service_jwt(private_key, kid, "civicflow-queue", "resource.windows.read"),
            "civicflow.queue.appointment-service-token": service_jwt(private_key, kid, "civicflow-queue", "appointments.checkin appointments.queue-state")},
    }
    for name, config in configs.items():
        response = requests.post("http://127.0.0.1:18848/nacos/v1/cs/configs", data={
            "accessToken": nacos_token, "tenant": environment["NACOS_NAMESPACE"],
            "dataId": f"civicflow-{name}.yaml", "group": "CIVICFLOW_GROUP", "type": "yaml",
            "content": "\n".join(f"{key}: {value}" for key, value in config.items())}, timeout=10)
        if response.status_code != 200 or response.text.strip() != "true":
            raise RuntimeError("Nacos service-token configuration publish failed")


def browser_flow(users, password, order, outlet, processes, temporary, environment):
    log = open(Path(temporary) / "web.log", "w", encoding="utf-8")
    process = subprocess.Popen(["node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", "5173", "--strictPort"],
        cwd=ROOT / "civicflow-web", env=environment, stdout=log, stderr=subprocess.STDOUT)
    processes.append((process, log))
    for _ in range(45):
        try:
            if requests.get("http://127.0.0.1:5173", timeout=2).status_code == 200:
                break
        except requests.RequestException:
            pass
        time.sleep(1)
    else:
        raise TimeoutError("Vite not ready")
    seed = {"admin": users[0][1], "user": users[1][1], "staff": users[2][1],
            "password": password, "appointmentId": order["appointmentId"], "outletId": outlet["id"]}
    result = subprocess.run(["node", "e2e/live-flow.mjs"], input=json.dumps(seed),
        cwd=ROOT / "civicflow-web", text=True, capture_output=True, timeout=180)
    if result.returncode:
        raise RuntimeError("Browser E2E failed: " + result.stderr[-3500:])
    result = json.loads(result.stdout.strip().splitlines()[-1])
    print("BROWSER " + json.dumps(result), flush=True)
    return result


def main(load_only=False):
    for port in (8080, 8081, 8082, 8083, 8084, 5173, 33306, 16379, 15673, 18848, 19848):
        with socket.socket() as probe:
            probe.settimeout(1)
            if probe.connect_ex(("127.0.0.1", port)) == 0:
                raise RuntimeError(f"E2E requires unused localhost port {port}; existing processes were not changed")
    print(f"START {RUN} {datetime.now(timezone.utc).isoformat()}", flush=True)
    environment = local_env()
    environment["NACOS_SERVER_ADDR"] = "127.0.0.1:18848"
    environment["MYSQL_ROOT_PASSWORD"] = secrets.token_urlsafe(24)
    for service in ("AUTH", "RESOURCE", "APPOINTMENT", "QUEUE"):
        for kind in ("APP", "MIGRATION"):
            environment[f"CIVICFLOW_{service}_DB_{kind}_PASSWORD"] = secrets.token_urlsafe(24)
        environment[f"CIVICFLOW_{service}_DB_URL"] = (
            f"jdbc:mysql://127.0.0.1:{MYSQL_PORT}/civicflow_{service.lower()}?"
            "useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true")
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    service_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    kid = "e2e-" + RUN.lower()
    service_kid = "e2e-service-" + RUN.lower()
    environment.update({
        "CIVICFLOW_AUTH_JWT_ACTIVE_KID": kid,
        "CIVICFLOW_AUTH_JWT_PUBLIC_KEY": base64.b64encode(private_key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode(),
        "CIVICFLOW_AUTH_JWT_PRIVATE_KEY": base64.b64encode(private_key.private_bytes(
            serialization.Encoding.DER, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption())).decode(),
        "CIVICFLOW_AUTH_MOBILE_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "CIVICFLOW_AUTH_MOBILE_HMAC_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "CIVICFLOW_CONTACT_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "CIVICFLOW_RESOURCE_IDEMPOTENCY_HMAC_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "CIVICFLOW_CHECKIN_SIGNING_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "CIVICFLOW_JWKS_URI": "http://127.0.0.1:8081/.well-known/jwks.json",
        "CIVICFLOW_GATEWAY_JWK_SET_URI": "http://127.0.0.1:8081/.well-known/jwks.json",
    })
    environment["SPRING_APPLICATION_JSON"] = json.dumps({"civicflow.auth.jwt.keys": [
        {"kid": kid, "public-key": environment["CIVICFLOW_AUTH_JWT_PUBLIC_KEY"],
         "private-key": environment["CIVICFLOW_AUTH_JWT_PRIVATE_KEY"]},
        {"kid": service_kid, "public-key": base64.b64encode(service_key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()},
    ]})
    names = ["auth", "resource", "appointment", "queue", "gateway"]
    processes = []
    stop_rotation = threading.Event()
    rotation_errors = []
    with tempfile.TemporaryDirectory(prefix="civicflow-e2e-") as temporary:
        try:
            subprocess.run(["docker", "run", "-d", "--rm", "--name", NACOS_CONTAINER,
                "-p", "127.0.0.1:18848:8848", "-p", "127.0.0.1:19848:9848",
                "-e", "MODE=standalone", "-e", "NACOS_AUTH_ENABLE=true",
                "-e", "NACOS_AUTH_SYSTEM_TYPE=nacos",
                "-e", "NACOS_AUTH_TOKEN=" + environment["NACOS_AUTH_TOKEN"],
                "-e", "NACOS_AUTH_IDENTITY_KEY=" + environment["NACOS_AUTH_IDENTITY_KEY"],
                "-e", "NACOS_AUTH_IDENTITY_VALUE=" + environment["NACOS_AUTH_IDENTITY_VALUE"],
                "-e", "JVM_XMS=256m", "-e", "JVM_XMX=512m", "-e", "JVM_XMN=128m",
                "nacos/nacos-server:v2.5.4"], check=True, capture_output=True, text=True)
            for _ in range(90):
                try:
                    if requests.get("http://127.0.0.1:18848/nacos/v1/console/health/readiness",
                                    timeout=2).status_code == 200:
                        break
                except requests.RequestException:
                    pass
                time.sleep(1)
            else:
                raise TimeoutError("isolated Nacos did not become ready")
            init_script = (ROOT / "deploy/compose/nacos/init-nacos.sh").read_text(encoding="utf-8").replace(
                "http://nacos:8848", "http://127.0.0.1:8848")
            for _ in range(2):
                initialized = subprocess.run(["docker", "exec", "-i", "-e", "NACOS_USERNAME=nacos",
                    "-e", "NACOS_PASSWORD=" + environment["NACOS_PASSWORD"], "-e", "NACOS_NAMESPACE=e2e",
                    NACOS_CONTAINER, "sh"], input=init_script.encode("utf-8"), capture_output=True)
                if initialized.returncode:
                    raise RuntimeError("Compose Nacos initialization script failed: " + initialized.stderr.decode(errors="replace")[-200:])
            print("Compose Nacos init script passed twice", flush=True)
            nacos_token = ensure_nacos(environment)
            publish_service_tokens(environment, service_key, service_kid, nacos_token)
            def rotate():
                while not stop_rotation.wait(120):
                    try:
                        publish_service_tokens(environment, service_key, service_kid, nacos_token)
                        print("Five-minute service JWTs rotated through Nacos", flush=True)
                    except Exception as error:
                        rotation_errors.append(type(error).__name__)
            threading.Thread(target=rotate, daemon=True).start()
            subprocess.run(["docker", "run", "-d", "--rm", "--name", REDIS_CONTAINER,
                "-p", "127.0.0.1:16379:6379", "-e", "REDIS_PASSWORD=" + environment["REDIS_PASSWORD"],
                "redis:7.4.11-alpine", "sh", "-c", 'exec redis-server --requirepass "$REDIS_PASSWORD"'],
                check=True, capture_output=True)
            subprocess.run(["docker", "run", "-d", "--rm", "--name", RABBIT_CONTAINER,
                "-p", "127.0.0.1:15673:5672", "-e", "RABBITMQ_DEFAULT_USER=e2e",
                "-e", "RABBITMQ_DEFAULT_PASS=" + environment["RABBITMQ_PASSWORD"],
                "-e", "RABBITMQ_DEFAULT_VHOST=e2e", "rabbitmq:4.1.8-management-alpine"],
                check=True, capture_output=True)
            subprocess.run(["docker", "run", "-d", "--rm", "--name", MYSQL_CONTAINER,
                            "-p", f"127.0.0.1:{MYSQL_PORT}:3306", "-e",
                            "MYSQL_ROOT_PASSWORD=" + environment["MYSQL_ROOT_PASSWORD"],
                            "mysql:8.4.11", "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_0900_ai_ci",
                            "--default-time-zone=+00:00"], check=True, capture_output=True, text=True)
            for _ in range(60):
                ready = subprocess.run(["docker", "exec", MYSQL_CONTAINER, "sh", "-lc",
                    'mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT 1"'],
                    capture_output=True)
                if ready.returncode == 0:
                    break
                time.sleep(1)
            else:
                raise TimeoutError("isolated MySQL did not become ready")
            account_env = []
            for key, value in environment.items():
                if key.startswith("CIVICFLOW_") and "_DB_" in key and not key.endswith("_URL"):
                    account_env.extend(["-e", key + "=" + value])
            init_mysql = (ROOT / "deploy/compose/mysql/init/01-create-service-databases.sh").read_text(encoding="utf-8")
            subprocess.run(["docker", "exec", "-i", *account_env, MYSQL_CONTAINER, "bash"],
                input=init_mysql.encode("utf-8"), capture_output=True, check=True)
            print("isolated MySQL schemas and accounts ready", flush=True)
            for index, name in enumerate(names):
                jar = ROOT / f"civicflow-{name}/target/civicflow-{name}-0.1.0-SNAPSHOT.jar"
                if not jar.exists():
                    matches = list(jar.parent.glob("*.jar"))
                    jar = next((m for m in matches if not m.name.endswith(".original")), jar)
                # Windows locks running JARs; keep Maven's target artifact rebuildable.
                runtime_jar = Path(temporary) / jar.name
                shutil.copy2(jar, runtime_jar)
                with zipfile.ZipFile(runtime_jar) as archive:
                    manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8')
                    if 'Start-Class:' not in manifest or 'Main-Class:' not in manifest:
                        raise RuntimeError(f'{name} JAR is not executable; finish Maven package before E2E')
                log = open(Path(temporary) / f"{name}.log", "w", encoding="utf-8")
                process = subprocess.Popen(["java", "-jar", str(runtime_jar)], cwd=ROOT,
                                           env=environment, stdout=log, stderr=subprocess.STDOUT)
                processes.append((process, log))
                wait_health(8081 + index if name != "gateway" else 8080, process)
                print(f"{name} healthy", flush=True)
                if name == "auth":
                    base_id = int(time.time() * 1000) * 1000 + secrets.randbelow(100)
                    password = secrets.token_urlsafe(18)
                    digest = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
                    users = [(base_id + 1, f"e2e_admin_{RUN.lower()}", 3),
                             (base_id + 2, f"e2e_user_{RUN.lower()}", 1),
                             (base_id + 3, f"e2e_staff_{RUN.lower()}", 2)]
                    statements = []
                    for user_id, username, role in users:
                        statements.append(f"INSERT INTO civicflow_auth.sys_user(id,username,password_hash,display_name,status,token_version,version,deleted) VALUES ({user_id},'{username}','{digest}','E2E synthetic','ENABLED',0,0,0);")
                        statements.append(f"INSERT INTO civicflow_auth.sys_user_role(id,user_id,role_id,deleted) VALUES ({user_id},{user_id},{role},0);")
                    mysql("\n".join(statements))
            admin = api("POST", "/api/v1/auth/login", body={"loginName": users[0][1], "password": password})["accessToken"]
            user = api("POST", "/api/v1/auth/login", body={"loginName": users[1][1], "password": password})["accessToken"]
            staff = api("POST", "/api/v1/auth/login", body={"loginName": users[2][1], "password": password})["accessToken"]
            if load_only:
                from live_load import run
                result = run(sys.modules[__name__], admin, environment, service_key, service_kid)
                output = ROOT / "target/e2e"
                output.mkdir(parents=True, exist_ok=True)
                (output / f"load-{RUN}.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
                return
            denied("GET", "/api/v1/admin/outlets", 401)
            denied("GET", "/api/v1/admin/outlets", 403, user)
            denied("POST", "/api/v1/staff/check-ins", 403, user, {})
            denied("GET", "/internal/v1/resource/slots/1", 403, user)
            outlet = api("POST", "/api/v1/admin/outlets", admin,
                         {"code": "E2E_OUT_" + RUN, "name": "E2E synthetic outlet", "address": "E2E test address"}, RUN + "-outlet")
            item = api("POST", "/api/v1/admin/items", admin,
                       {"code": "E2E_ITEM_" + RUN, "name": "E2E synthetic item", "defaultDurationMinutes": 30}, RUN + "-item")
            window = api("POST", "/api/v1/admin/windows", admin,
                         {"outletId": outlet["id"], "code": "E2E_WIN_" + RUN, "name": "E2E window"}, RUN + "-window")
            api("PUT", f"/api/v1/admin/windows/{window['id']}/items", admin,
                {"itemIds": [item["id"]], "version": window["version"]}, RUN + "-binding")
            mysql(f"INSERT INTO civicflow_resource.staff_window_scope(id,staff_user_id,outlet_id,window_id,deleted) VALUES ({base_id + 4},{users[2][0]},{outlet['id']},{window['id']},0);")
            now = datetime.now(SHANGHAI).replace(microsecond=0)
            start = now + timedelta(minutes=12)
            end = start + timedelta(minutes=30)
            instant = lambda value: value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
            release_at = now + timedelta(minutes=2)
            slot = api("POST", "/api/v1/admin/slots", admin, {
                "outletId": outlet["id"], "itemId": item["id"],
                "serviceDate": start.date().isoformat(), "startTime": start.time().isoformat(),
                "endTime": end.time().isoformat(), "totalQuota": 3,
                "releaseAt": instant(release_at),
                "checkInStart": instant(now - timedelta(minutes=1)),
                "checkInEnd": instant(start + timedelta(minutes=20)), "status": "SCHEDULED"}, RUN + "-slot")
            preheat = api("POST", f"/api/v1/admin/stock/slots/{slot['id']}/preheat", admin)
            assert preheat["remaining"] == 3 and preheat["configVersion"] == slot["configVersion"]
            print("PREHEAT " + json.dumps(preheat, ensure_ascii=False), flush=True)
            tag = "{" + item["id"] + ":" + start.strftime("%Y%m%d") + "}"
            stock_key = "cf:v1:dev:stock:" + tag + ":slot:" + slot["id"]
            assert redis("HMGET", stock_key, "total", "remaining", "configVersion").splitlines() == ["3", "3", "1"]
            time.sleep(max(0, (release_at - datetime.now(SHANGHAI)).total_seconds()) + 2)
            slot = api("PATCH", f"/api/v1/admin/slots/{slot['id']}/status", admin,
                       {"status": "OPEN", "version": slot["version"]}, RUN + "-open")
            api("POST", f"/api/v1/admin/stock/slots/{slot['id']}/preheat", admin)
            first = api("POST", "/api/v1/user/appointments/reservations", user,
                        {"slotId": slot["id"]}, RUN + "-reserve1", expected=(202,))
            order = poll_reservation(user, first["reservationId"], "PENDING_CONFIRM")["appointment"]
            browser = browser_flow(users, password, order, outlet, processes, temporary, environment)
            ticket = {"id": browser["ticketId"]}
            assert redis("HGET", stock_key, "remaining") == "2", "completion returned consumed stock"
            assert redis("GET", "cf:v1:dev:active:" + tag + ":user:" + str(users[1][0])) == ""
            second = api("POST", "/api/v1/user/appointments/reservations", user,
                         {"slotId": slot["id"]}, RUN + "-reserve2", expected=(202,))
            poll_reservation(user, second["reservationId"], "PENDING_CONFIRM")
            print("Waiting for actual five-minute confirm timeout", flush=True)
            timeout_started = time.monotonic()
            while time.monotonic() - timeout_started < 340:
                status = mysql("SELECT status FROM civicflow_appointment.appointment_order WHERE reservation_id='" + second["reservationId"] + "';")
                if status == "EXPIRED":
                    break
                time.sleep(2)
            else:
                raise TimeoutError("Background timeout did not persist EXPIRED")
            elapsed = round(time.monotonic() - timeout_started, 2)
            print(f"Background timeout persisted EXPIRED after {elapsed}s; no expiry-triggering GET used", flush=True)
            assert redis("HGET", stock_key, "remaining") == "2", "timeout did not release stock once"
            poll_reservation(user, second["reservationId"], "EXPIRED")
            third = api("POST", "/api/v1/user/appointments/reservations", user,
                        {"slotId": slot["id"]}, RUN + "-reserve3", expected=(202,))
            third_order = poll_reservation(user, third["reservationId"], "PENDING_CONFIRM")["appointment"]
            api("POST", f"/api/v1/user/appointments/{third_order['appointmentId']}/confirm", user,
                {"version": third_order["version"]}, RUN + "-confirm3")
            print("Waiting for reconciliation grace period", flush=True)
            time.sleep(125)
            reconciliation = api("POST", "/api/v1/admin/reconciliations", admin,
                                 {"slotId": slot["id"], "repair": False}, RUN + "-reconcile")
            assert reconciliation["expected"] == reconciliation["actual"] == 1
            assert reconciliation["classification"] == "CONSISTENT"
            db = mysql(
                "SELECT status FROM civicflow_appointment.appointment_order WHERE reservation_id='" +
                second["reservationId"] + "'; "
                "SELECT status FROM civicflow_appointment.appointment_order WHERE id=" +
                order["appointmentId"] + "; "
                "SELECT COUNT(*) FROM civicflow_appointment.stock_release_record WHERE reservation_id='" +
                second["reservationId"] + "' AND status='SUCCEEDED'; "
                "SELECT COUNT(*) FROM civicflow_appointment.appointment_operation_log WHERE appointment_id=" +
                order["appointmentId"] + "; "
                "SELECT status FROM civicflow_queue.queue_ticket WHERE id=" + ticket["id"] + "; "
                "SELECT COUNT(*) FROM civicflow_queue.queue_operation_log WHERE ticket_id=" + ticket["id"] + "; "
                "SELECT COUNT(*) FROM civicflow_resource.resource_slot WHERE id=" + slot["id"] +
                " AND total_quota=3;")
            facts = db.splitlines()
            assert len(facts) == 7 and facts[0:3] == ["EXPIRED", "COMPLETED", "1"]
            assert int(facts[3]) >= 4 and facts[4] == "COMPLETED"
            assert int(facts[5]) >= 3 and facts[6] == "1", facts
            integrity = mysql(
                "SELECT COUNT(*) FROM civicflow_queue.queue_state_sync WHERE status='DONE'; "
                "SELECT COUNT(*) FROM civicflow_appointment.message_consume_record WHERE status='PROCESSED'; "
                "SELECT COUNT(*) FROM civicflow_appointment.outbox_event WHERE status!='PUBLISHED'; "
                "SELECT TIMESTAMPDIFF(MICROSECOND,created_at,confirm_deadline),TIMESTAMPDIFF(MICROSECOND,confirm_deadline,expired_at) FROM civicflow_appointment.appointment_order WHERE reservation_id='" + second["reservationId"] + "'; "
                "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM civicflow_auth.flyway_schema_history WHERE success=1; "
                "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM civicflow_resource.flyway_schema_history WHERE success=1; "
                "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM civicflow_appointment.flyway_schema_history WHERE success=1; "
                "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM civicflow_queue.flyway_schema_history WHERE success=1;").splitlines()
            assert integrity[:3] == ["2", "3", "0"], integrity
            deadline_us, expired_after_us = map(int, integrity[3].split())
            assert deadline_us == 300_000_000 and 0 <= expired_after_us < 40_000_000, integrity
            assert integrity[4:] == ["1,2,3", "1,2,3,4", "1,2,3,4,5", "1,2,3"], integrity
            queues = subprocess.run(["docker", "exec", RABBIT_CONTAINER, "rabbitmqctl", "list_queues", "-p", "e2e",
                "name", "messages", "arguments", "--formatter=json"], capture_output=True, text=True, check=True)
            topology = json.loads(queues.stdout)
            delay = next(row for row in topology if row["name"] == "cf.appointment.confirm.delay.5m.q")
            assert "300000" in json.dumps(delay["arguments"]) and "cf.timeout.x" in json.dumps(delay["arguments"])
            assert all(row["messages"] == 0 for row in topology if row["name"].endswith(".dlq"))
            expiry_trace = mysql("SELECT request_id FROM civicflow_appointment.appointment_operation_log WHERE reservation_id='" + second["reservationId"] + "' AND operation='EXPIRE';")
            expiry_source = "timeout scanner" if expiry_trace.startswith("timeout-scan-") else "RabbitMQ timeout consumer"
            assert not rotation_errors, rotation_errors
            result = {"run": RUN, "slotId": slot["id"],
                "appointmentId": order["appointmentId"], "ticketId": ticket["id"],
                "expiredReservationId": second["reservationId"],
                "rebookedReservationId": third["reservationId"],
                "databaseFacts": facts, "integrity": integrity, "timeoutSeconds": elapsed,
                "expirySource": expiry_source, "rabbitTopology": topology, "browser": browser, "reconciliation": reconciliation}
            output = ROOT / "target/e2e"
            output.mkdir(parents=True, exist_ok=True)
            (output / f"{RUN}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print("RESULT " + json.dumps(result, ensure_ascii=False), flush=True)
        except Exception as error:
            for name in names[:len(processes)]:
                lines = (Path(temporary) / f"{name}.log").read_text(
                    encoding="utf-8", errors="replace").splitlines()
                clues = [line for line in lines if any(mark in line for mark in
                         ("APPLICATION FAILED", "Caused by:", "Description:", "Exception:", "Error creating bean", "no main manifest"))]
                print(f"{name} error clues:\n" + safe_error("\n".join(clues[-25:])), flush=True)
            if isinstance(error, subprocess.CalledProcessError):
                raise RuntimeError(f"Subprocess failed with exit {error.returncode}; command hidden to protect generated credentials") from None
            raise RuntimeError(safe_error(error)) from None
        finally:
            stop_rotation.set()
            for process, log in reversed(processes):
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                log.close()
            subprocess.run(["docker", "stop", MYSQL_CONTAINER], capture_output=True, text=True)
            for container in (NACOS_CONTAINER, REDIS_CONTAINER, RABBIT_CONTAINER):
                subprocess.run(["docker", "stop", container], capture_output=True, text=True)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--load', action='store_true', help='Run bounded load/security probes in isolated services')
    main(load_only=parser.parse_args().load)
