"""J-Harness stdio protocol 1.0. Standard library only; no automatic request retries."""
from __future__ import annotations

import concurrent.futures
import json
import os
import queue
import subprocess
import threading
import uuid
from collections import OrderedDict


class RpcError(RuntimeError):
    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate field")
        result[key] = value
    return result


class JHarnessClient:
    """Owns one Java child. Timeouts leave acceptance unknown; reconcile explicitly."""

    def __init__(self, java, jar, workspace, config=None, *, env=None, timeout=15, protocol_version="1.0"):
        if not 0.1 <= timeout <= 60:
            raise ValueError("timeout must be 0.1..60 seconds")
        self.timeout = timeout
        self._lock = threading.RLock()
        self._pending = {}
        self._expired = OrderedDict()
        self._writes = queue.Queue(maxsize=32)
        self._closed = False
        self._closing = False
        args = [str(java), "-Dfile.encoding=UTF-8", "-jar", str(jar), "--workspace", str(workspace)]
        if config is not None:
            args += ["--config", str(config)]
        args += ["sdk-stdio"]
        try:
            self._process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                             env=dict(os.environ if env is None else env), bufsize=0,
                                             **({"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}))
        except OSError:
            raise RpcError("PROCESS_START_FAILED") from None
        for target in (self._read, self._write, self._drain):
            threading.Thread(target=target, daemon=True).start()
        try:
            self.capabilities = self.call("initialize", {"protocolVersion": protocol_version})
            if self.capabilities.get("protocolVersion") != "1.0":
                raise RpcError("VERSION_UNSUPPORTED")
        except Exception:
            self.close()
            raise

    def call(self, method, params=None, *, timeout=None):
        deadline = self.timeout if timeout is None else timeout
        if not isinstance(deadline, (float, int)) or not 0.1 <= deadline <= 60:
            raise ValueError("timeout must be 0.1..60 seconds")
        request_id = str(uuid.uuid4())
        try:
            frame = (json.dumps({"jsonrpc": "2.0", "id": request_id, "method": method, "params": {} if params is None else params}, ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
        except (TypeError, ValueError):
            raise RpcError("INVALID_REQUEST") from None
        if len(frame) - 1 > 65536:
            raise RpcError("RPC_REQUEST_LIMIT")
        future = concurrent.futures.Future()
        with self._lock:
            if self._closed or self._closing and method != "shutdown":
                raise RpcError("CLIENT_CLOSED")
            if len(self._expired) >= 4096:
                raise RpcError("EXPIRED_HISTORY_LIMIT")
            if len(self._pending) >= 32:
                raise RpcError("BACKPRESSURE_LIMIT")
            self._pending[request_id] = future
            try:
                self._writes.put_nowait((request_id, frame))
            except queue.Full:
                del self._pending[request_id]
                raise RpcError("BACKPRESSURE_LIMIT") from None
        try:
            return future.result(timeout=deadline)
        except concurrent.futures.TimeoutError:
            with self._lock:
                if self._pending.pop(request_id, None) is not None:
                    self._expired[request_id] = True
            raise RpcError("REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN") from None

    def _write(self):
        try:
            while True:
                item = self._writes.get()
                if item is None:
                    self._process.stdin.close()
                    return
                request_id, frame = item
                with self._lock:
                    if request_id not in self._pending:
                        continue
                remaining = memoryview(frame)
                while remaining:
                    written = self._process.stdin.write(remaining)
                    if not written:
                        raise OSError()
                    remaining = remaining[written:]
                self._process.stdin.flush()
        except (OSError, ValueError):
            self._fail("PROCESS_DISCONNECTED")
            self._terminate()

    def _read(self):
        try:
            while True:
                frame = self._process.stdout.readline(524290)
                if not frame:
                    self._fail("PROCESS_DISCONNECTED")
                    return
                if not frame.endswith(b"\n") or len(frame) - 1 > 524288:
                    raise ValueError()
                response = json.loads(frame.decode("utf-8"), object_pairs_hook=_object, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                if not isinstance(response, dict) or response.get("jsonrpc") != "2.0" or not isinstance(response.get("id"), str):
                    raise ValueError()
                if set(response) not in ({"jsonrpc", "id", "result"}, {"jsonrpc", "id", "error"}):
                    raise ValueError()
                code = None
                if "error" in response:
                    code = response["error"].get("data", {}).get("code")
                    if not isinstance(code, str) or not code or len(code) > 64 or any(c not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for c in code):
                        raise ValueError()
                elif not isinstance(response["result"], dict):
                    raise ValueError()
                with self._lock:
                    if response["id"] in self._expired:
                        del self._expired[response["id"]]
                        continue
                    future = self._pending.pop(response["id"], None)
                    if future is None:
                        raise ValueError()
                    if code is not None:
                        future.set_exception(RpcError(code))
                    else:
                        future.set_result(response["result"])
        except (ValueError, UnicodeError, AttributeError, OSError, RecursionError):
            self._fail("INVALID_RESPONSE")
            self._terminate()

    def _drain(self):
        try:
            while self._process.stderr.read(8192):
                pass  # Never retain or echo a child's potentially sensitive diagnostics.
        except (OSError, ValueError):
            pass

    def _fail(self, code):
        with self._lock:
            self._closed = True
            for future in self._pending.values():
                if not future.done():
                    future.set_exception(RpcError(code))
            self._pending.clear()

    @property
    def pid(self):
        return self._process.pid

    def _terminate(self):
        if self._process.poll() is None:
            self._process.terminate()

    def close(self):
        with self._lock:
            if self._closing:
                return
            self._closing = True
        try:
            if not self._closed and self._process.poll() is None:
                self.call("shutdown", timeout=3)
        except RpcError:
            pass
        try:
            self._writes.put_nowait(None)
        except queue.Full:
            pass
        try:
            try:
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._terminate()
                try:
                    self._process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    self._process.kill()
                    try:
                        self._process.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        raise RpcError("PROCESS_CLEANUP_UNKNOWN") from None
        except OSError:
            try:
                if self._process.poll() is None:
                    self._process.kill()
                    self._process.wait(timeout=2)
            except (OSError, subprocess.TimeoutExpired):
                pass
            raise RpcError("PROCESS_CLEANUP_UNKNOWN") from None
        finally:
            self._fail("CLIENT_CLOSED")
            try:
                self._writes.put_nowait(None)
            except queue.Full:
                pass
            for stream in (self._process.stdin, self._process.stdout, self._process.stderr):
                try:
                    stream.close()
                except (OSError, ValueError):
                    pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        try:
            self.close()
        except RpcError as cleanup:
            if exc is None:
                raise
            exc.add_note("J-Harness cleanup: " + cleanup.code)

    def open_session(self, profile="default", session_id=None):
        return self.call("session.open", {"profile": profile, **({"sessionId": session_id} if session_id else {})})

    def close_session(self, handle):
        return self.call("session.close", {"handle": handle})

    def list_sessions(self, profile=None, view="all", after=None):
        return self.call("sessions.list", {"view": view, **({"profile": profile} if profile else {}), **({"after": after} if after else {})})

    def send_input(self, handle, text, request_id=None, *, model_id=None, kind="message"):
        return self.call("input.send", {"handle": handle, "text": text, "requestId": request_id or str(uuid.uuid4()), "kind": kind, **({"modelId": model_id} if model_id else {})})

    def events(self, session_id, after=None, limit=32):
        return self.call("events.list", {"sessionId": session_id, "limit": limit, **({"after": after} if after else {})})

    def questions(self, session_id, offset=0):
        return self.call("questions.list", {"sessionId": session_id, "offset": offset})

    def answer(self, question, selected=None, text="", skip=False, request_id=None):
        return self.call("question.answer", {key: question[key] for key in ("sessionId", "questionId", "turnId", "callId")} | {"requestId": request_id or str(uuid.uuid4()), "selected": selected or [], "text": text, "skip": skip})

    def approvals(self, session_id):
        return self.call("approvals.list", {"sessionId": session_id})

    def decide(self, session_id, approval, approved, reason):
        return self.call("approval.decide", {"sessionId": session_id, "approvalId": approval["approvalId"], "callId": approval["callId"], "approved": approved, "reason": reason})

    def cancel(self, session_id):
        return self.call("session.cancel", {"sessionId": session_id})

    def result(self, session_id):
        return self.call("session.result", {"sessionId": session_id})

    def report(self, session_id):
        return self.call("session.report", {"sessionId": session_id})

    def artifact(self, session_id, artifact_id, offset=0, version=None):
        return self.call("session.artifact", {"sessionId": session_id, "artifactId": artifact_id, "offset": offset, **({"version": version} if version else {})})

    def search(self, text, *, mode="tokens", profile="", view="active", session_id="", cursor="", limit=20, rebuild=False):
        return self.call("content.search", {"text": text, "mode": mode, "profile": profile, "view": view, "sessionId": session_id, "cursor": cursor, "limit": limit, "rebuild": rebuild})
