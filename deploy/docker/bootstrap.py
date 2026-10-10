"""Local Compose key/config bootstrap and five-minute service identity rotation.

No Docker socket, no published ports, no secret values in logs.
"""
import json
import os
from pathlib import Path
import sys
import tempfile
import time

import pymysql
from pymysql.constants import CLIENT

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'local'))
import dev

STATE = Path('/state')
GROUP = 'CIVICFLOW_DOCKER'


def connect(service):
    prefix = 'CIVICFLOW_' + service.upper() + '_DB_MIGRATION_'
    return pymysql.connect(host='mysql', user=os.environ[prefix+'USERNAME'],
                           password=os.environ[prefix+'PASSWORD'], database='civicflow_'+service,
                           charset='utf8mb4', client_flag=CLIENT.MULTI_STATEMENTS, autocommit=True)


def query(sql):
    if 'information_schema.tables' in sql:
        # Check each schema under its own account; no root or cross-schema grants.
        count = 0
        for service in ('auth', 'resource', 'appointment', 'queue'):
            with connect(service) as connection, connection.cursor() as cursor:
                cursor.execute('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s',
                               ('civicflow_'+service,))
                count += cursor.fetchone()[0]
        return str(count)
    # Account fixture writes are restricted to auth. No business transitions here.
    with connect('auth') as connection:
        rows = []
        with connection.cursor() as cursor:
            cursor.execute(sql)
            while True:
                if cursor.description:
                    rows.extend(cursor.fetchall())
                if not cursor.nextset():
                    break
        return '\n'.join('\t'.join(str(value) for value in row) for row in rows)


def main(mode):
    values = dict(os.environ)
    STATE.mkdir(parents=True, exist_ok=True)
    if mode == 'init':
        keys = dev.material(directory=STATE, query=query,
                            admin_username=values.get('CIVICFLOW_LOCAL_ADMIN_USERNAME', 'admin'),
                            admin_password=values.get('CIVICFLOW_LOCAL_ADMIN_PASSWORD', '1234'))
        with tempfile.TemporaryDirectory() as folder:
            dev.configure(values, keys, directory=Path(folder), container=True)
            for service in dev.SERVICES:
                directory = Path('/output') / service
                target = directory / 'application.properties'
                temporary = directory / 'application.tmp'
                temporary.write_bytes((Path(folder) / (service + '.properties')).read_bytes())
                os.chown(temporary, 10001, 10001)
                os.chmod(temporary, 0o640)
                temporary.replace(target)
        print('Persistent keys retained; isolated service configurations ready')
    elif mode == 'seed':
        keys = json.loads((STATE / 'keys.json').read_text())
        dev.seed_admin(keys, query=query, directory=STATE)
        print('Local administrator ready; existing accounts unchanged')
    elif mode == 'rotate':
        keys = json.loads((STATE / 'keys.json').read_text())
        while True:
            try:
                dev.rotate_tokens(values, keys, base='http://nacos:8848/nacos', group=GROUP)
                Path('/tmp/rotation-ready').write_text(str(time.time()))
                print('Five-minute service identities refreshed')
                time.sleep(60)
            except Exception as error:
                print('Service identity refresh failed: ' + type(error).__name__, flush=True)
                time.sleep(10)
    else:
        raise ValueError('Unknown bootstrap mode')


if __name__ == '__main__':
    try:
        main(sys.argv[1])
    except Exception as error:
        # Exceptions from drivers may contain connection/configuration secrets.
        print('Bootstrap failed: ' + type(error).__name__ + '; check credentials, health and persistent keys',
              file=sys.stderr, flush=True)
        sys.exit(1)
