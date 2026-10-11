"""Gateway browser origins must match the Docker web port or an explicit override."""
from pathlib import Path
import tempfile
import unittest

import dev


class GatewayOriginConfigurationTest(unittest.TestCase):
    def gateway(self, values, *, container):
        keys = {name: 'synthetic-' + name for name in (
            'userPublic', 'userPrivate', 'servicePublic', 'mobileEncryption',
            'mobileHmac', 'contactEncryption', 'idempotencyHmac', 'checkin')}
        with tempfile.TemporaryDirectory() as folder:
            dev.configure(values, keys, directory=Path(folder), container=container)
            return (Path(folder) / 'gateway.properties').read_text(encoding='utf-8')

    def test_container_uses_configured_web_port(self):
        text = self.gateway({'WEB_PORT': '15173'}, container=True)
        self.assertIn('CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:15173,http://127.0.0.1:15173\n', text)

    def test_default_port_is_retained(self):
        for container in (False, True):
            with self.subTest(container=container):
                self.assertIn('CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173\n',
                              self.gateway({}, container=container))

    def test_explicit_origins_are_retained_in_both_modes(self):
        for container in (False, True):
            with self.subTest(container=container):
                self.assertIn('CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS=https://demo.example.test\n',
                              self.gateway({'WEB_PORT': '15173',
                                            'CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS': 'https://demo.example.test'},
                                           container=container))

    def test_local_launcher_keeps_its_vite_port(self):
        self.assertIn('CIVICFLOW_GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173\n',
                      self.gateway({'WEB_PORT': '15173'}, container=False))


if __name__ == '__main__':
    unittest.main()
