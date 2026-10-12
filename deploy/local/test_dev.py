"""Regression checks for persistent local setup and process ownership decisions."""
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

import dev


class LocalSetupTest(unittest.TestCase):
    def test_container_configuration_uses_dns_and_separates_private_keys(self):
        keys = {name: 'synthetic-' + name for name in (
            'userPublic', 'userPrivate', 'servicePublic', 'mobileEncryption',
            'mobileHmac', 'contactEncryption', 'idempotencyHmac', 'checkin')}
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            dev.configure({'MYSQL_PORT': '3307', 'REDIS_PORT': '16379', 'RABBITMQ_PORT': '15673'},
                          keys, directory=folder, container=True)
            for service in dev.SERVICES:
                text = (folder / (service + '.properties')).read_text()
                self.assertIn('NACOS_SERVER_ADDR=nacos:8848', text)
                self.assertNotIn('spring.cloud.nacos.discovery.ip=', text)
                self.assertIn('REDIS_PORT=6379', text)
                self.assertIn('RABBITMQ_PORT=5672', text)
                if service != 'gateway':
                    self.assertIn('jdbc:mysql://mysql:3306/', text)
                if service != 'auth':
                    self.assertNotIn(keys['userPrivate'], text)
                if service != 'resource':
                    self.assertNotIn(keys['contactEncryption'], text)
            dev.configure({'MYSQL_PORT': '3307'}, keys, directory=folder)
            self.assertIn('jdbc:mysql://127.0.0.1:3307/', (folder / 'auth.properties').read_text())

    def test_windows_status_rename_retries_transient_reader_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'state.json'
            replace = Path.replace
            attempts = []
            def temporarily_locked(source, destination):
                attempts.append(1)
                if len(attempts) < 3:
                    raise PermissionError('temporary Windows reader lock')
                return replace(source, destination)
            with patch.object(Path, 'replace', temporarily_locked), patch.object(dev.time, 'sleep'):
                dev.write_json(path, {'phase': 'ready'})
            self.assertEqual(json.loads(path.read_text()), {'phase': 'ready'})
            self.assertEqual(len(attempts), 3)

    def test_process_ownership_uses_lock_even_with_no_heartbeat(self):
        import msvcrt
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            with patch.object(dev, 'LOCAL', folder):
                self.assertFalse(dev.supervisor_running())
                with (folder / 'supervisor.lock').open('a+b') as owner:
                    msvcrt.locking(owner.fileno(), msvcrt.LK_NBLCK, 1)
                    self.assertTrue(dev.supervisor_running())
                self.assertFalse(dev.supervisor_running())

    def test_existing_keys_are_reused_without_database_access(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / 'keys.json').write_text(json.dumps({'stable': 'existing-key'}))
            with patch.object(dev, 'LOCAL', folder), patch.object(dev, 'mysql') as mysql:
                self.assertEqual(dev.material(), {'stable': 'existing-key'})
                mysql.assert_not_called()

    def test_missing_keys_never_silently_rotate_existing_database(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(dev, 'LOCAL', Path(directory)), patch.object(dev, 'mysql', return_value='12'):
                with self.assertRaisesRegex(RuntimeError, 'Restore'):
                    dev.material()
                self.assertFalse((Path(directory) / 'keys.json').exists())

    def test_local_properties_escape_password_metacharacters(self):
        value = dev.property_text({'password': 'a\\b\nc\r#=:'})
        self.assertEqual(value, 'password=a\\\\b\\nc\\r#=:\n')

    def test_env_values_keep_equals_and_support_quotes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            path.write_text('#comment\nSECRET="abc=="\nEMPTY=\n')
            self.assertEqual(dev.read_env(path), {'SECRET': 'abc==', 'EMPTY': ''})

    def test_stale_or_failed_supervisor_is_not_reused(self):
        self.assertFalse(dev.alive({'phase': 'ready', 'heartbeat': time.time() - 60}))
        self.assertFalse(dev.alive({'phase': 'failed', 'heartbeat': time.time()}))
        self.assertTrue(dev.alive({'phase': 'prepared', 'heartbeat': time.time()}))


if __name__ == '__main__':
    unittest.main()
