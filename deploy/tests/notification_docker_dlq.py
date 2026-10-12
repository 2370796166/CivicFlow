"""Exercise notification DLQ and operator replay against the running Compose stack.

Creates one synthetic USER through the public API. It never consumes an existing DLQ
message: the test requires an empty notification DLQ before publishing its fixture.
"""

from datetime import datetime, timezone
import json
from pathlib import Path
import secrets
import time
from urllib.parse import quote
import uuid

import requests

import e2e_v1 as e


ROOT = Path(__file__).resolve().parents[2]
RUN = uuid.uuid4().hex[:8].upper()
SESSION = requests.Session()
SESSION.trust_env = False


def compose_env():
    values = {}
    for line in (ROOT / 'deploy/compose/.env').read_text(encoding='utf-8-sig').splitlines():
        if line and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('"\'')
    return values


def broker(method, path, values, body=None):
    port = values.get('RABBITMQ_MANAGEMENT_PORT', '15672')
    response = SESSION.request(
        method, f'http://127.0.0.1:{port}/api/{path}',
        auth=(values['RABBITMQ_USERNAME'], values['RABBITMQ_PASSWORD']),
        json=body, timeout=15)
    response.raise_for_status()
    return response.json()


def wait_for(check, description, seconds=35):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        result = check()
        if result:
            return result
        time.sleep(0.5)
    raise TimeoutError(description)


def main():
    e.BASE = 'http://127.0.0.1:5173'
    e.SESSION.trust_env = False
    values = compose_env()
    vhost = quote(values.get('RABBITMQ_VHOST', 'civicflow'), safe='')
    dlq_path = f'queues/{vhost}/cf.notification.in-app.q.dlq'
    exchange_path = f'exchanges/{vhost}/cf.appointment.x/publish'
    before = broker('GET', dlq_path, values)['messages_ready']
    if before != 0:
        raise RuntimeError('Notification DLQ is not empty; preserve existing messages for operator review')

    keys = json.loads((ROOT / '.local/dev/keys.json').read_text(encoding='utf-8'))
    admin = e.api('POST', '/api/v1/auth/login', body={
        'loginName': keys.get('adminUsername', 'local_admin'),
        'password': keys['adminPassword']}, quiet=True)['accessToken']
    username = f'notification_e2e_{RUN.lower()}'
    password = secrets.token_urlsafe(24)
    e.api('POST', '/api/v1/admin/users', admin, {
        'username': username, 'password': password,
        'displayName': f'Notification E2E {RUN}', 'roles': ['USER']},
        f'notification-{RUN}', quiet=True)
    auth = e.api('POST', '/api/v1/auth/login', body={
        'loginName': username, 'password': password}, quiet=True)
    user_token = auth['accessToken']
    user_id = auth['user']['id']

    event_id = str(uuid.uuid4())
    event = {
        'schemaVersion': 1, 'eventId': event_id,
        'eventType': 'appointment.notification.created', 'eventVersion': 1,
        'occurredAt': datetime.now(timezone.utc).isoformat(),
        'producer': 'civicflow-appointment', 'traceId': str(uuid.uuid4()),
        'correlationId': user_id, 'causationId': str(uuid.uuid4()),
        'payload': {
            'userId': user_id, 'aggregateId': user_id,
            'templateCode': 'APPOINTMENT_CREATED', 'templateVersion': 1,
            'variables': {'appointmentId': '<script>'}, 'required': True}}

    def publish(payload):
        result = broker('POST', exchange_path, values, {
            'properties': {'delivery_mode': 2, 'content_type': 'application/json'},
            'routing_key': 'appointment.notification.created.v1',
            'payload': json.dumps(payload, ensure_ascii=False),
            'payload_encoding': 'string'})
        if not result.get('routed'):
            raise RuntimeError('Notification event was not routed')

    publish(event)
    wait_for(lambda: broker('GET', dlq_path, values)['messages_ready'] == 1,
             'Invalid event did not reach notification DLQ')
    messages = broker('POST', dlq_path + '/get', values, {
        'count': 1, 'ackmode': 'ack_requeue_false', 'encoding': 'auto'})
    if len(messages) != 1:
        raise AssertionError('Expected one notification dead letter')
    dead = messages[0]
    dead_event = json.loads(dead['payload'])
    if dead_event['eventId'] != event_id:
        raise AssertionError('DLQ eventId differs from the published fixture')
    deaths = dead['properties'].get('headers', {}).get('x-death', [])
    if not deaths or deaths[0].get('reason') != 'rejected':
        raise AssertionError('Notification was not rejected through the configured DLX')

    dead_event['payload']['variables']['appointmentId'] = user_id
    publish(dead_event)

    def delivered():
        page = e.api('GET', '/api/v1/user/notifications?page=1&size=20', user_token,
                     quiet=True)
        matches = [item for item in page['items'] if item['eventId'] == event_id]
        return matches[0] if len(matches) == 1 and matches[0]['status'] == 'SENT' else None

    record = wait_for(delivered, 'Corrected DLQ replay did not create a SENT record')
    publish(dead_event)
    time.sleep(2)
    page = e.api('GET', '/api/v1/user/notifications?page=1&size=20', user_token, quiet=True)
    if sum(item['eventId'] == event_id for item in page['items']) != 1:
        raise AssertionError('Duplicate delivery created another in-app record')
    wait_for(lambda: broker('GET', dlq_path, values)['messages_ready'] == 0,
             'Notification DLQ still contains an unexpected message', seconds=10)

    result = {'run': RUN, 'deployment': 'Docker Compose', 'eventId': event_id,
              'recordId': record['id'], 'dlqReason': deaths[0]['reason'],
              'replayStatus': record['status'], 'duplicateRecords': 1}
    output = ROOT / 'target/e2e' / f'notification-dlq-{RUN}.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('NOTIFICATION DLQ REPLAY PASS ' + json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
