"""First IDEA startup must seed only after Auth is ready, retaining existing users."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import dev


class IdeaPreparationTest(unittest.TestCase):
    def test_waits_for_auth_then_creates_credentials_once(self):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory)
            queries = []

            def query(sql):
                queries.append(sql)
                return '0' if sql.startswith('SELECT COUNT') else ''

            with patch.object(dev, 'LOCAL', local), patch.object(dev, 'STATE', {'phase': 'prepared'}), \
                    patch.object(dev, 'healthy', side_effect=[False, True]) as ready, \
                    patch.object(dev, 'mysql', side_effect=query), patch.object(dev, 'emit'):
                keys = {'adminUsername': 'idea_admin', 'adminPassword': 'synthetic-fixture-password'}
                self.assertFalse(dev.seed_idea_admin(keys))
                self.assertFalse((local / 'login.txt').exists())
                self.assertEqual(queries, [])
                self.assertTrue(dev.seed_idea_admin(keys))
                self.assertTrue((local / 'login.txt').exists())
                self.assertIn('Username: idea_admin', (local / 'login.txt').read_text())
                before = (local / 'login.txt').read_bytes()
                self.assertFalse(dev.seed_idea_admin(keys))
                self.assertEqual((local / 'login.txt').read_bytes(), before)
                self.assertEqual(len(queries), 2)
                self.assertEqual(ready.call_count, 2)

    def test_preserves_existing_account_and_login_file(self):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory)
            existing = local / 'login.txt'
            existing.write_text('retained existing credentials', encoding='utf-8')
            with patch.object(dev, 'LOCAL', local), patch.object(dev, 'STATE', {'phase': 'prepared'}), \
                    patch.object(dev, 'healthy', return_value=True), \
                    patch.object(dev, 'mysql', return_value='1') as query, patch.object(dev, 'emit'):
                self.assertTrue(dev.seed_idea_admin({'adminUsername': 'idea_admin'}))
                self.assertEqual(existing.read_text(), 'retained existing credentials')
                self.assertEqual(query.call_count, 1)
                self.assertTrue(query.call_args.args[0].startswith('SELECT COUNT'))

    def test_does_not_seed_in_the_full_launcher_or_mark_a_failed_seed_ready(self):
        with patch.object(dev, 'STATE', {'phase': 'ready'}), patch.object(dev, 'healthy') as ready:
            self.assertFalse(dev.seed_idea_admin({}))
            ready.assert_not_called()
        with patch.object(dev, 'STATE', {'phase': 'prepared'}), \
                patch.object(dev, 'healthy', return_value=True), \
                patch.object(dev, 'seed_admin', side_effect=RuntimeError('synthetic database unavailable')):
            with self.assertRaises(RuntimeError):
                dev.seed_idea_admin({})
            self.assertNotIn('adminReady', dev.STATE)


if __name__ == '__main__':
    unittest.main()
