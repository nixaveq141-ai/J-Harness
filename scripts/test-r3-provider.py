"""R3 loopback example protocol boundaries; no external requests."""
import importlib.util
import json
from pathlib import Path
import socket
import io
import queue
import subprocess
from types import SimpleNamespace
import threading
import concurrent.futures
import time
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("r3_provider", ROOT / "sdk/examples/loopback_provider.py")
provider = importlib.util.module_from_spec(spec)
spec.loader.exec_module(provider)
sdk_spec = importlib.util.spec_from_file_location("r3_sdk", ROOT / "sdk/python/jharness_sdk.py")
sdk = importlib.util.module_from_spec(sdk_spec)
sdk_spec.loader.exec_module(sdk)


class ClientCleanupTest(unittest.TestCase):
    def test_consumed_response_does_not_create_a_permanent_timeout_tombstone(self):
        client = self.client()
        client._closed = False
        client._expired = {}
        class BoundaryFuture(concurrent.futures.Future):
            def result(self, timeout=None):
                try:
                    return super().result(timeout=0)
                except concurrent.futures.TimeoutError:
                    with client._lock:
                        request_id = next(iter(client._pending))
                        client._pending.pop(request_id).set_result({})
                    raise
        with patch.object(sdk.concurrent.futures, "Future", BoundaryFuture):
            with self.assertRaises(sdk.RpcError) as error:
                client.call("ping", timeout=.1)
        self.assertEqual("REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN", error.exception.code)
        self.assertEqual({}, client._pending)
        self.assertEqual({}, client._expired)

    def test_oserror_attempts_kill_and_preserves_primary_diagnostic(self):
        client = self.client()
        killed = []
        client._process.wait = lambda **_: (_ for _ in ()).throw(OSError())
        client._process.kill = lambda: killed.append(True)
        primary = RuntimeError("primary")
        client.__exit__(type(primary), primary, None)
        self.assertEqual([True], killed)
        self.assertIn("PROCESS_CLEANUP_UNKNOWN", primary.__notes__[0])

    def client(self):
        client = sdk.JHarnessClient.__new__(sdk.JHarnessClient)
        client._lock = threading.RLock()
        client._closed, client._closing = True, False
        client._pending = {}
        client._writes = queue.Queue(32)
        def timeout(**_):
            raise subprocess.TimeoutExpired("owned-fault-process", 2)
        client._process = SimpleNamespace(wait=timeout, poll=lambda: None,
                                          terminate=lambda: None, kill=lambda: None,
                                          stdin=io.BytesIO(), stdout=io.BytesIO(), stderr=io.BytesIO())
        return client

    def test_final_kill_timeout_still_closes_all_owned_streams(self):
        client = self.client()
        with self.assertRaises(sdk.RpcError) as failure:
            client.close()
        self.assertEqual("PROCESS_CLEANUP_UNKNOWN", failure.exception.code)
        self.assertTrue(all(stream.closed for stream in (client._process.stdin, client._process.stdout, client._process.stderr)))
        self.assertTrue(client._closing)

    def test_context_exit_preserves_primary_exception_on_cleanup_failure(self):
        client = self.client()
        original = RuntimeError("primary failure")
        self.assertIsNone(client.__exit__(type(original), original, None))
        self.assertTrue(client._process.stdin.closed)


class ProviderBoundaryTest(unittest.TestCase):
    def setUp(self):
        with patch.object(socket, "getfqdn", side_effect=AssertionError("Offline provider must not resolve DNS")):
            self.server = provider.LoopbackServer(("127.0.0.1", 0), provider.Provider)
        self.assertEqual("localhost", self.server.server_name)
        self.assertEqual("127.0.0.1", self.server.server_address[0])
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(3)
        self.assertFalse(self.worker.is_alive())

    def exchange(self, size, body):
        with socket.create_connection(self.server.server_address, timeout=4) as connection:
            connection.sendall(b"POST / HTTP/1.0\r\nContent-Length: " + size + b"\r\n\r\n" + body)
            data = bytearray()
            while chunk := connection.recv(65536):
                data.extend(chunk)
            return bytes(data)

    def test_invalid_length_and_json_shape_have_controlled_errors(self):
        for size, body, status in [(b"wrong", b"", b"400"), (b"1048577", b"", b"413"),
                                   (b"2", b"[]", b"400"), (b"16", b'{"messages":[1]}', b"400")]:
            with self.subTest(size=size, body=body):
                response = self.exchange(size, body)
                self.assertIn(status, response.split(b"\r\n", 1)[0])
                self.assertNotIn(b"Traceback", response)

    def test_partial_body_times_out_and_releases_connection(self):
        started = time.monotonic()
        response = self.exchange(b"100", b"{")
        self.assertEqual(b"", response)
        self.assertLess(time.monotonic() - started, 3.5)

    def test_valid_messages_produce_complete_local_tool_response(self):
        body = json.dumps({"messages": [{"role": "user", "content": "inspect"}]}).encode()
        response = self.exchange(str(len(body)).encode(), body)
        header, content = response.split(b"\r\n\r\n", 1)
        self.assertIn(b"200", header.split(b"\r\n", 1)[0])
        choice = json.loads(content)["choices"][0]
        self.assertEqual("tool_calls", choice["finish_reason"])
        self.assertEqual("read_file", choice["message"]["tool_calls"][0]["function"]["name"])


if __name__ == "__main__":
    unittest.main()
