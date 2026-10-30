"""Offline checks: separate grants, no refunds/retries, real facts required."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('fresh', Path(__file__).with_name('deepseek-fresh-code-validation.py'))
fresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fresh)


class FreshTest(unittest.TestCase):
    def test_new_grant_does_not_reuse_old_identity_or_caps(self):
        spec = importlib.util.spec_from_file_location('old', fresh.ROOT/'scripts/deepseek-readonly-validation.py')
        old = importlib.util.module_from_spec(spec); spec.loader.exec_module(old)
        self.assertEqual(2_000_000, old.LIMIT_MICRO_CNY)
        self.assertEqual(12, old.MAX_ATTEMPTS)
        self.assertNotEqual(old.GRANT, fresh.base.GRANT)
        with tempfile.TemporaryDirectory() as temp:
            wallet = fresh.base.Wallet(Path(temp)/'grant')
            for _ in range(12):
                wallet.reserve('/chat/completions', b'bounded request')
            with self.assertRaisesRegex(ValueError, 'exhausted'):
                wallet.reserve('/chat/completions', b'no thirteenth request')
            self.assertEqual(12*fresh.base.RESERVATION, wallet.audit()['reservedMicroCny'])
            self.assertLess(wallet.audit()['reservedMicroCny'], 5_000_000)

    def test_false_completion_or_wrong_fixture_cannot_supply_code(self):
        facts = [dict(type='tool.completed', payload=dict(tool='read_file', status='SUCCESS', sideEffectLevel='READ_ONLY',
                    value=dict(path='requirements.txt', version=fresh.digest(fresh.REQUIREMENTS)))),
                 dict(type='turn.ended', payload=dict(status='COMPLETED')),
                 dict(type='assistant.message', payload=dict(content='package example; public class Calculator {}'))]
        self.assertTrue(fresh.extract(facts, fresh.REQUIREMENTS).startswith(b'package example;'))
        facts[2]['payload']['content'] = '```java\npackage example; public class Calculator {}\n```\nUnverified until isolated execution.'
        self.assertTrue(fresh.extract(facts, fresh.REQUIREMENTS).startswith(b'package example;'))
        facts[2]['payload']['content'] *= 2
        with self.assertRaisesRegex(ValueError, 'Ambiguous'): fresh.extract(facts, fresh.REQUIREMENTS)
        facts[0]['payload']['value']['version'] = 'wrong'
        with self.assertRaises(ValueError): fresh.extract(facts, fresh.REQUIREMENTS)
        with self.assertRaises(ValueError): fresh.extract(facts[1:], fresh.REQUIREMENTS)

    def test_host_execution_and_remote_tools_are_not_available(self):
        import json
        request = dict(model='deepseek-flash', max_tokens=2048, stream=False,
                       thinking={'type':'disabled'}, messages=[], tools=[dict(type='function', function={'name':'execute_command'})])
        with self.assertRaises(ValueError): fresh.base.checked_request('/chat/completions', json.dumps(request).encode())
        request['tools'][0]['function']['name'] = 'read_file'
        fresh.base.checked_request('/chat/completions', json.dumps(request).encode())
        request['max_tokens'] = 2049
        with self.assertRaises(ValueError): fresh.base.checked_request('/chat/completions', json.dumps(request).encode())


if __name__ == '__main__': unittest.main()
