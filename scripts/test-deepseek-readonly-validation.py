"""Offline accounting and pre-send rejection tests; no provider calls."""
from concurrent.futures import ThreadPoolExecutor
import importlib.util
from http.server import HTTPServer
import json
from pathlib import Path
import sqlite3
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('readonly_live', Path(__file__).with_name('deepseek-readonly-validation.py'))
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)


class WalletTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name) / 'grant'

    def test_unknown_send_survives_restart_and_receipt_does_not_refund(self):
        wallet = live.Wallet(self.directory)
        attempt = wallet.reserve('/responses', b'fixed synthetic body')
        resumed = live.Wallet(self.directory)
        self.assertEqual(live.RESERVATION, resumed.audit()['reservedMicroCny'])
        self.assertIsNone(resumed.audit()['attempts'][0][4])
        resumed.receipt(attempt, 400, b'{"usage":{"input_tokens":1,"bad":"secret"}}')
        self.assertEqual(live.RESERVATION, resumed.audit()['reservedMicroCny'])
        self.assertEqual({'input_tokens': 1}, json.loads(resumed.audit()['attempts'][0][-1]))
        self.assertNotIn('synthetic body', json.dumps(resumed.audit()))
        self.assertNotIn('secret', json.dumps(resumed.audit()))

    def test_concurrent_reservations_share_one_durable_limit(self):
        wallet = live.Wallet(self.directory)
        def reserve(_):
            try:
                return wallet.reserve('/responses', b'{}')
            except ValueError:
                return None
        with ThreadPoolExecutor(max_workers=8) as pool:
            attempts = list(pool.map(reserve, range(32)))
        self.assertEqual(live.MAX_ATTEMPTS, sum(a is not None for a in attempts))
        resumed = live.Wallet(self.directory)
        self.assertEqual(live.RESERVATION * live.MAX_ATTEMPTS, resumed.audit()['reservedMicroCny'])
        self.assertLessEqual(resumed.audit()['reservedMicroCny'], 2_000_000)
        with self.assertRaises(ValueError):
            resumed.reserve('/responses', b'{}')

    def test_missing_existing_wallet_cannot_restart_budget(self):
        self.directory.mkdir()
        with self.assertRaises(ValueError):
            live.Wallet(self.directory)

    def test_corrupt_or_wrong_grant_fails_closed(self):
        wallet = live.Wallet(self.directory)
        with wallet.connect() as db:
            db.execute("UPDATE grant_info SET cap=99999999")
        with self.assertRaises(ValueError):
            live.Wallet(self.directory)
        wallet.path.write_bytes(b'partial database')
        with self.assertRaises(sqlite3.DatabaseError):
            live.Wallet(self.directory)

    def test_failed_transaction_never_grants_a_network_send(self):
        wallet = live.Wallet(self.directory)
        with patch.object(wallet, 'connect', side_effect=OSError('disk full')):
            with self.assertRaises(OSError):
                wallet.reserve('/responses', b'{}')
        self.assertEqual(0, wallet.audit()['reservedMicroCny'])

    def test_relay_commit_failure_sends_nothing_and_unknown_send_retains_cost(self):
        wallet = live.Wallet(self.directory)
        server = HTTPServer(('127.0.0.1', 0), live.handler(wallet, 'synthetic-provider-secret', 'local-test-token'))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            body = json.dumps(RequestTest().request()).encode()
            client = urllib.request.build_opener()
            def send():
                request = urllib.request.Request('http://127.0.0.1:' + str(server.server_port)
                                                 + '/chat/completions', body,
                                                 {'Authorization': 'Bearer local-test-token'})
                with self.assertRaises(urllib.error.HTTPError) as failure:
                    client.open(request, timeout=3)
                self.assertEqual(502, failure.exception.code)
                failure.exception.close()
            with patch.object(live.urllib.request, 'build_opener', side_effect=AssertionError('unexpected send')) as opener:
                with patch.object(wallet, 'reserve', side_effect=OSError('disk full')):
                    send()
                opener.assert_not_called()
            self.assertEqual(0, wallet.audit()['reservedMicroCny'])
            with patch.object(live.urllib.request, 'build_opener') as opener:
                opener.return_value.open.side_effect = TimeoutError('response lost')
                send()
                opener.return_value.open.assert_called_once()
            self.assertEqual(live.RESERVATION, live.Wallet(self.directory).audit()['reservedMicroCny'])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


class RequestTest(unittest.TestCase):
    def request(self, path='/chat/completions'):
        value = dict(model='deepseek-flash', stream=False, messages=[],
                     max_tokens=1024, thinking={'type': 'disabled'}, tools=[])
        if path == '/responses':
            value.pop('thinking')
            value.pop('messages')
            value['input'] = []
            value['reasoning'] = {'effort': 'none'}
            value['max_output_tokens'] = value.pop('max_tokens')
        return value

    def test_three_protocols_pass_only_fixed_bounds(self):
        for path in live.PATHS:
            self.assertEqual('deepseek-flash', live.checked_request(path, json.dumps(self.request(path)).encode())['model'])

    def test_other_model_streaming_output_expansion_and_paid_tools_rejected(self):
        for change in [dict(model='deepseek-v4-pro'), dict(stream=True), dict(max_tokens=1025),
                       dict(n=2), dict(n=True), dict(thinking={'type': 'enabled'}),
                       dict(messages=[{}]*33), dict(tools=[dict(type='web_search')]),
                       dict(tools=[dict(type='function', function=dict(name='execute_command'))])]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                live.checked_request('/chat/completions', json.dumps(self.request() | change).encode())

    def test_oversize_paths_and_redirects_rejected(self):
        for path, body in [('/responses?x=1', b'{}'), ('https://attacker.test', b'{}'),
                           ('/responses', b' ' * 32769)]:
            with self.assertRaises(ValueError):
                live.checked_request(path, body)
        with self.assertRaises(ValueError):
            live.NoRedirect().redirect_request(None)


if __name__ == '__main__':
    unittest.main()
