"""Offline SSE transport/accounting regressions. No provider request or real wallet."""
import email.message
import http.client
from http.server import HTTPServer
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

PATH = Path(__file__).with_name('deepseek-sse-validation.py')
spec = importlib.util.spec_from_file_location('sse_validation', PATH)
sse = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sse)
CHAT = b'data: {"choices":[{"delta":{"content":"42"}}]}\n\ndata: [DONE]\n\n'


class Response(io.BytesIO):
    code = 200
    def __init__(self, body=CHAT):
        super().__init__(body)
        self.headers = email.message.Message()
        self.headers['Content-Type'] = 'text/event-stream; charset=utf-8'


class SseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.wallet = sse.base.Wallet(Path(self.temp.name) / 'grant')

    def body(self, **changes):
        value = dict(model='deepseek-flash', max_tokens=1024, stream=True,
                     thinking={'type': 'disabled'}, messages=[], tools=[])
        value.update(changes)
        return json.dumps(value).encode()

    def relay(self, opener, body=None, path='/chat/completions', token='test-local-token', count=1):
        records = []
        server = HTTPServer(('127.0.0.1', 0), sse.handler(self.wallet, 'test-provider-key',
                            'test-local-token', '/chat/completions', records))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        answers = []
        try:
            with patch.object(sse.urllib.request, 'build_opener', return_value=opener):
                for _ in range(count):
                    connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
                    connection.request('POST', path, body=self.body() if body is None else body,
                                       headers={'Authorization': 'Bearer ' + token})
                    response = connection.getresponse()
                    answers.append((response.status, response.read()))
                    connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(2)
        return answers, records

    def test_pre_send_bounds_retain_original_readonly_guard(self):
        for changes in [dict(stream=False), dict(model='other'), dict(max_tokens=1025),
                        dict(n=2), dict(thinking={'type': 'enabled'}),
                        dict(tools=[{'type': 'function', 'function': {'name': 'write_file'}}])]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                sse.checked_request('/chat/completions', self.body(**changes))
        sse.checked_request('/chat/completions', self.body())
        self.assertEqual([], self.wallet.audit()['attempts'])

    def test_three_protocol_terminal_framing_and_numeric_usage(self):
        frames = {
            '/chat/completions': CHAT,
            '/messages': b'event: message_start\ndata: {"type":"message_start","message":{"usage":{"input_tokens":3}}}\n\nevent: message_stop\ndata: {"type":"message_stop"}\n\n',
            '/responses': b'event: response.created\ndata: {"type":"response.created"}\n\nevent: response.completed\ndata: {"type":"response.completed","response":{"usage":{"output_tokens":2}}}\n\n'}
        for path, data in frames.items():
            with self.subTest(path=path):
                report = sse.inspect_stream(path, data.replace(b'\n', b'\r\n'))
                self.assertTrue(report['terminal'])
                self.assertEqual(2, report['frames'])
                for bad in [data[:-1], data.split(b'\n\n')[0] + b'\n\n', data + b'data: {}\n\n']:
                    with self.assertRaises(ValueError):
                        sse.inspect_stream(path, bad)
        with self.assertRaises(ValueError):
            sse.inspect_stream('/messages', CHAT)

    def test_response_byte_and_total_time_bounds(self):
        with self.assertRaises(ValueError):
            sse.read_response(Response(b'x' * (sse.MAX_RESPONSE + 1)))
        ticks = iter([0, 0, 66])
        with self.assertRaisesRegex(ValueError, 'deadline'):
            sse.read_response(Response(), clock=lambda: next(ticks))

    def test_reservation_commits_before_send_and_two_send_limit(self):
        from unittest.mock import Mock
        opener = Mock()
        counts = []
        def send(*args, **kwargs):
            counts.append(len(self.wallet.audit()['attempts']))
            return Response()
        opener.open.side_effect = send
        answers, records = self.relay(opener, count=3)
        self.assertEqual([200, 200, 502], [a[0] for a in answers])
        self.assertEqual([1, 2], counts)
        self.assertEqual(2, opener.open.call_count)
        self.assertEqual(['COMPLETE_SSE'] * 2, [r['status'] for r in records])
        self.assertEqual(2 * sse.base.RESERVATION, self.wallet.audit()['reservedMicroCny'])

    def test_unknown_send_keeps_reservation_without_retry(self):
        from unittest.mock import Mock
        opener = Mock()
        opener.open.side_effect = OSError('injected unknown transport')
        answers, records = self.relay(opener)
        self.assertEqual(502, answers[0][0])
        self.assertEqual(1, opener.open.call_count)
        self.assertEqual('RESERVED_UNKNOWN', records[0]['status'])
        resumed = sse.base.Wallet(Path(self.temp.name) / 'grant').audit()
        self.assertEqual(sse.base.RESERVATION, resumed['reservedMicroCny'])
        self.assertIsNone(resumed['attempts'][0][4])

    def test_reflected_credentials_never_reach_client_or_receipt(self):
        from unittest.mock import Mock
        for secret in ['test-provider-key', 'test-local-token']:
            opener = Mock()
            opener.open.return_value = Response(('data: {"text":"' + secret + '"}\n\ndata: [DONE]\n\n').encode())
            answers, _ = self.relay(opener)
            self.assertEqual(502, answers[0][0])
            self.assertNotIn(secret.encode(), answers[0][1])
        self.assertTrue(all(row[4] is None for row in self.wallet.audit()['attempts']))

    def test_json_escaped_credentials_never_reach_client_or_receipt(self):
        from unittest.mock import Mock
        for secret in ['test-provider-key', 'test-local-token']:
            escaped = ''.join('\\u%04x' % ord(character) for character in secret)
            for payload in ['{"nested":[{"text":"' + escaped + '"}]}',
                            '{"' + escaped + '":"value"}']:
                with self.subTest(secret=secret, payloadKind='key' if ':"value"' in payload else 'value'):
                    opener = Mock()
                    opener.open.return_value = Response(('data: ' + payload + '\n\ndata: [DONE]\n\n').encode())
                    answers, records = self.relay(opener)
                    self.assertEqual(502, answers[0][0])
                    self.assertNotIn(secret.encode(), answers[0][1])
                    self.assertNotIn(escaped.encode(), answers[0][1])
                    self.assertEqual('RESERVED_UNKNOWN', records[0]['status'])
        self.assertEqual(4, len(self.wallet.audit()['attempts']))
        self.assertTrue(all(row[4] is None for row in self.wallet.audit()['attempts']))

    def test_bad_auth_path_or_input_does_not_reserve(self):
        from unittest.mock import Mock
        for kwargs in [dict(token='wrong'), dict(path='/other'), dict(body=self.body(stream=False))]:
            opener = Mock()
            answers, _ = self.relay(opener, **kwargs)
            self.assertEqual(502, answers[0][0])
            opener.open.assert_not_called()
        self.assertEqual([], self.wallet.audit()['attempts'])

    def test_real_packaged_cli_consumes_fake_chat_sse_tool_roundtrip(self):
        from unittest.mock import Mock
        opener = Mock()
        def data(value):
            return b'data: ' + json.dumps(value).encode() + b'\n\n'
        tool = data({'choices': [{'index': 0, 'delta': {'role': 'assistant', 'tool_calls': [
            {'index': 0, 'id': 'read-once', 'type': 'function', 'function': {
                'name': 'read_file', 'arguments': '{"path":"fixture.txt"}'}}]}, 'finish_reason': None}]})
        tool += data({'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'tool_calls'}]}) + b'data: [DONE]\n\n'
        answer = data({'choices': [{'index': 0, 'delta': {'role': 'assistant', 'content': 'The sum is 42.'}, 'finish_reason': None}]})
        answer += data({'choices': [{'index': 0, 'delta': {}, 'finish_reason': 'stop'}]}) + b'data: [DONE]\n\n'
        opener.open.side_effect = [Response(tool), Response(answer)]
        with patch.object(sse, 'existing_wallet', return_value=self.wallet), \
                patch.dict(sse.os.environ, DEEPSEEK_API_KEY='test-provider-key'), \
                patch.object(sse.urllib.request, 'build_opener', return_value=opener):
            result = sse.run(['chat-completions'])
        self.assertEqual('PASS', result['status'])
        self.assertEqual(2, opener.open.call_count)
        for call in opener.open.call_args_list:
            value = json.loads(call.args[0].data)
            self.assertIs(True, value['stream'])
        self.assertEqual(2 * sse.base.RESERVATION, self.wallet.audit()['reservedMicroCny'])


if __name__ == '__main__':
    unittest.main()
