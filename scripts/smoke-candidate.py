"""Install the actual ZIP and exercise CLI against a loopback model fixture.

No paid model requests. Output/logs live under ignored target/.
"""
import datetime
import hashlib
import http.server
import io
import json
import os
from pathlib import Path, PureWindowsPath
import queue
import re
import stat
import struct
import zlib
import subprocess
import tempfile
import threading
import zipfile

from evidence_io import atomic_write_json, bounded_path, require_sha256, strict_json_loads

ROOT = Path(__file__).resolve().parents[1]
MAX_ZIP_BYTES = 256 * 1024 * 1024
MAX_EXPANDED_BYTES = 512 * 1024 * 1024
REPLY_TIMEOUT_SECONDS = 10
REQUEST_TIMEOUT_SECONDS = 10


def candidate_name(name):
    if not isinstance(name, str) or not name or "\\" in name or "\x00" in name:
        raise ValueError("Unsafe candidate path")
    windows = PureWindowsPath(name)
    parts = name.split("/")
    if (windows.drive or windows.root or name.startswith("/") or
            any(p in ("", ".", "..") or ":" in p or p.endswith((" ", ".")) or
                re.fullmatch(r"(?i:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?", p) for p in parts)):
        raise ValueError("Unsafe candidate path")
    return name


def required_legal_files(root):
    paths = {"LICENSE", "NOTICE", "third-party/inventory.json", "third-party/upstream/sources.json"}
    legal_root = root / "third-party"
    if not legal_root.is_dir():
        raise ValueError("Missing legal source tree")
    for path in legal_root.rglob("*"):
        name = path.relative_to(root).as_posix()
        resolved = bounded_path(root, name)
        if resolved.is_file():
            paths.add(name)
    return paths



def read_member(raw, entry, budget, *, retain=True):
    """Read candidate-builder STORED/DEFLATE entries with actual output/CRC limits.

    ZipExtFile trusts file_size to truncate output; validate the raw compressed span
    instead. ZIP64, encryption and other compression methods are intentionally unsupported.
    """
    offset = entry.header_offset
    if offset < 0 or offset + 30 > len(raw):
        raise ValueError('Invalid ZIP local header')
    header = struct.unpack_from('<4s5H3I2H', raw, offset)
    _, _, flags, method, _, _, crc, compressed, expanded, name_len, extra_len = header
    if (header[0] != b'PK\x03\x04' or flags & ~0x808 or method not in (0, 8) or
            method != entry.compress_type or flags != entry.flag_bits or
            max(compressed, expanded, entry.compress_size, entry.file_size) >= 0xffffffff):
        raise ValueError('Unsupported ZIP method/flags/ZIP64 or header integrity')
    name_start = offset + 30
    start = name_start + name_len + extra_len
    end = start + entry.compress_size
    if start > len(raw) or end > len(raw):
        raise ValueError('Truncated ZIP compressed span')
    name = raw[name_start:name_start+name_len].decode('utf-8' if flags & 0x800 else 'cp437')
    if name != entry.orig_filename:
        raise ValueError('ZIP local/central name mismatch')
    extra = raw[name_start+name_len:start]
    at = 0
    while at < len(extra):
        if at + 4 > len(extra):raise ValueError('Malformed ZIP extra field')
        tag, length = struct.unpack_from('<HH', extra, at)
        if tag == 1:raise ValueError('Unsupported ZIP64 extra field')
        at += 4 + length
        if at > len(extra):raise ValueError('Malformed ZIP extra field')
    if not flags & 8 and (crc, compressed, expanded) != (entry.CRC, entry.compress_size, entry.file_size):
        raise ValueError('ZIP local/central header integrity mismatch')
    inflater = zlib.decompressobj(-15) if method == 8 else None
    total = checksum = 0
    chunks = []
    for position in range(start, end, 64 * 1024):
        pending = raw[position:min(position + 64 * 1024, end)]
        while pending:
            limit = min(64 * 1024, MAX_EXPANDED_BYTES - budget[0] + 1, entry.file_size - total + 1)
            if limit <= 0:raise ValueError('ZIP actual expansion budget exceeded')
            value = inflater.decompress(pending, limit) if inflater else pending[:limit]
            rest = inflater.unconsumed_tail if inflater else pending[len(value):]
            total += len(value); budget[0] += len(value)
            if budget[0] > MAX_EXPANDED_BYTES:raise ValueError('ZIP actual expansion budget exceeded')
            if total > entry.file_size:raise ValueError('ZIP actual uncompressed size mismatch')
            checksum = zlib.crc32(value, checksum)
            if retain:chunks.append(value)
            if inflater and inflater.unused_data:raise ValueError('Trailing ZIP compressed data')
            if rest == pending and not value:raise ValueError('ZIP decompression made no progress')
            pending = rest
    if inflater and not inflater.eof:raise ValueError('Truncated ZIP DEFLATE stream')
    if total != entry.file_size:raise ValueError('ZIP actual uncompressed size mismatch')
    if checksum != entry.CRC:raise ValueError('ZIP CRC mismatch')
    return b''.join(chunks) if retain else None


def finish_cleanup(report, actions, primary=None):
    failures = []
    for label, action in actions:
        try:action()
        except BaseException as error:
            failures.append(error)
            report.setdefault('secondaryFailures', []).append({'operation':label, 'type':type(error).__name__})
            if primary is not None:primary.add_note(label + ': ' + type(error).__name__)
    if failures and primary is None:
        for failure in failures[1:]:
            failures[0].add_note('additional cleanup failure: ' + type(failure).__name__)
        raise failures[0]

def validate_candidate(raw, legal_files):
    """Validate a bounded in-memory snapshot before any candidate bytes are installed."""
    if not isinstance(raw, bytes) or not raw or len(raw) > MAX_ZIP_BYTES:
        raise ValueError("Candidate ZIP byte limit or empty input")
    if not legal_files or not {"LICENSE", "NOTICE", "third-party/inventory.json", "third-party/upstream/sources.json"} <= set(legal_files):
        raise ValueError("Incomplete required legal set")
    files, seen = {}, set()
    budget = [0]
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        if not entries or len(entries) > 10000 or sum(i.file_size for i in entries) > MAX_EXPANDED_BYTES:
            raise ValueError("Candidate expanded size or entry limit")
        for entry in entries:
            # ZipInfo may normalize backslashes or truncate NUL on the current host.
            candidate_name(entry.orig_filename)
            full = candidate_name(entry.filename)
            if not full.startswith("jharness/") or entry.is_dir():
                raise ValueError("Candidate member outside file root")
            name = candidate_name(full[len("jharness/"):])
            if name.casefold() in seen:
                raise ValueError("Duplicate candidate member")
            seen.add(name.casefold())
            mode = entry.external_attr >> 16
            if stat.S_IFMT(mode) not in (0, stat.S_IFREG) or entry.flag_bits & 1:
                raise ValueError("Linked or unsupported candidate member")
            files[name] = read_member(raw, entry, budget)
    if "manifest.json" not in files or len(files["manifest.json"]) > 16 * 1024 * 1024:
        raise ValueError("Missing or oversized candidate manifest")
    manifest = strict_json_loads(files["manifest.json"])
    declared = manifest.get("files") if isinstance(manifest, dict) else None
    if not isinstance(declared, dict) or not declared:
        raise ValueError("Empty candidate manifest files")
    for name, digest in declared.items():
        candidate_name(name)
        require_sha256(digest)
    if set(declared) != set(files) - {"manifest.json"}:
        raise ValueError("Candidate manifest does not exactly cover files")
    mandatory = {"lib/jharness.jar", "bom.json", "osv-results.json", "sdk-bom.json", "sdk-source-inventory.json",
                 "frontend-bom.json", "jharness", "jharness.cmd", "README.md", "docs/examples/deepseek-config.json", "docs/examples/m7-cli.md"} | set(legal_files)
    if not mandatory <= set(declared):
        raise ValueError("Missing required candidate or legal files")
    for name, digest in declared.items():
        if hashlib.sha256(files[name]).hexdigest() != digest:
            raise ValueError("Candidate file hash mismatch: " + name)
    with zipfile.ZipFile(io.BytesIO(files["lib/jharness.jar"])) as jar:
        jar_entries = jar.infolist()
        if len(jar_entries) > 100000 or sum(i.file_size for i in jar_entries) > MAX_EXPANDED_BYTES:
            raise ValueError("Shaded JAR expanded size or entry limit")
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate shaded JAR member")
        required = {"META-INF/jharness/" + name:files[name] for name in legal_files if name.startswith("third-party/")}
        if not required.keys() <= set(names):
            raise ValueError("Legal file not preserved in shaded JAR")
        for entry in jar_entries:
            value = read_member(files["lib/jharness.jar"], entry, budget, retain=entry.filename in required)
            if entry.filename in required and value != required[entry.filename]:
                raise ValueError("Legal file not preserved in shaded JAR")
        if any(name.startswith("org/junit/") for name in names):
            raise ValueError("Test runtime leaked into candidate")
    return manifest, files


def main():
    output = ROOT / "target/candidate-smoke.json"
    report = {"capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "status": "INCOMPLETE", "checks": {}}
    atomic_write_json(output, report)
    primary = None
    try:
        if not __debug__:
            raise RuntimeError("Smoke requires Python assertions enabled; do not use -O")
        archive = bounded_path(ROOT, "target/jharness-0.1.0-SNAPSHOT-candidate.zip")
        with archive.open("rb") as stream:
            raw = stream.read(MAX_ZIP_BYTES + 1)
        manifest, files = validate_candidate(raw, required_legal_files(ROOT))
        report["zipSha256"] = hashlib.sha256(raw).hexdigest()
        exercise(report, manifest, files)
    except BaseException as error:
        primary = error
        report["status"] = "INCOMPLETE"
        report["failureType"] = type(error).__name__
        raise
    finally:
        finish_cleanup(report, [('receiptPublication', lambda: atomic_write_json(output, report))], primary)
    print(json.dumps(report))



def make_handler(replies, received, waiting, release, failures):
    class Handler(http.server.BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(REQUEST_TIMEOUT_SECONDS)

        def send_json(self, status, value):
            body = json.dumps(value, allow_nan=False).encode('utf-8')
            self.close_connection = True
            try:
                self.send_response(status)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.send_header('Connection', 'close')
                self.end_headers()
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass  # The cancellation scenario deliberately abandons its response.

        def fail(self, status, code):
            failures.append({'status':status, 'code':code})
            self.send_json(status, {'error':{'code':code, 'message':'Loopback fixture request rejected'}})

        def do_POST(self):
            if self.headers.get('Transfer-Encoding') is not None:
                self.fail(400, 'UNSUPPORTED_TRANSFER_ENCODING'); return
            lengths = self.headers.get_all('Content-Length', [])
            if not lengths:
                self.fail(411, 'LENGTH_REQUIRED'); return
            if len(lengths) != 1 or not re.fullmatch('[0-9]{1,8}', lengths[0]):
                self.fail(400, 'INVALID_CONTENT_LENGTH'); return
            length = int(lengths[0])
            if not 0 < length <= 1024 * 1024:
                self.fail(413, 'BODY_TOO_LARGE'); return
            try:
                data = self.rfile.read(length)
            except TimeoutError:
                self.fail(408, 'BODY_READ_TIMEOUT'); return
            if len(data) != length:
                self.fail(400, 'INCOMPLETE_BODY'); return
            try:
                request = strict_json_loads(data)
            except (ValueError, UnicodeError):
                self.fail(400, 'INVALID_JSON'); return
            if not isinstance(request, dict):
                self.fail(400, 'JSON_OBJECT_REQUIRED'); return
            received.append(request)
            try:
                message = replies.get(timeout=REPLY_TIMEOUT_SECONDS)
            except queue.Empty:
                self.fail(503, 'FIXTURE_REPLY_UNAVAILABLE'); return
            if message == 'WAIT':
                waiting.set()
                release.wait(25)
                message = {'role':'assistant', 'content':'late response'}
            if not isinstance(message, dict):
                self.fail(500, 'INVALID_FIXTURE_REPLY'); return
            self.send_json(200, {'choices':[{'message':message,
                'finish_reason':'tool_calls' if 'tool_calls' in message else 'stop'}],
                'usage':{'prompt_tokens':10, 'completion_tokens':5}})

        def log_message(self, *args):
            pass
    return Handler

def exercise(report, manifest, files):
    replies, received = queue.Queue(), []
    http_failures = []
    waiting = threading.Event()
    release = threading.Event()
    Handler = make_handler(replies, received, waiting, release, http_failures)

    server = None
    thread = None
    environment = {**os.environ, "M9_SMOKE_KEY": "m9-local-fixture-only"}
    primary = None
    try:
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        server.daemon_threads = True
        server.timeout = 10
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        with tempfile.TemporaryDirectory(prefix="M9 install space ", dir=ROOT / "target") as temp:
            install = Path(temp)
            install /= "jharness"
            install.mkdir()
            for name, raw in files.items():
                path = bounded_path(install, name, must_exist=False)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
            for name, digest in manifest["files"].items():
                assert hashlib.sha256(bounded_path(install, name).read_bytes()).hexdigest() == digest, name
            report["checks"]["installedManifest"] = True
            workspace = install / "workspace space"
            workspace.mkdir()
            config = workspace / "config.json"
            config.write_text(json.dumps({"baseUrl": f"http://127.0.0.1:{server.server_port}",
                                         "model": "fixture", "apiKeyEnv": "M9_SMOKE_KEY",
                                         "requestTimeoutSeconds": 30, "contextTokens": 65536}))
            launch = (["cmd.exe", "/d", "/c", str(install / "jharness.cmd")] if os.name == "nt"
                      else ["sh", str(install / "jharness")])

            def command(*args):
                tail = list(args) + ["--workspace", str(workspace), "--config", str(config)]
                if os.name == "nt":
                    inner = subprocess.list2cmdline([str(install / "jharness.cmd")] + tail)
                    return 'cmd.exe /d /s /c "' + inner + '"'
                return launch + tail

            def run(*args, code=0, stdin=""):
                proc = subprocess.run(command(*args), input=stdin, text=True, encoding="utf-8",
                                      errors="replace", capture_output=True, env=environment, timeout=40)
                assert proc.returncode == code, (args, proc.returncode, proc.stdout, proc.stderr)
                assert "m9-local-fixture-only" not in proc.stdout + proc.stderr
                return proc.stdout

            def text(value):
                replies.put({"role": "assistant", "content": value})

            def call(name, args):
                replies.put({"role": "assistant", "content": "", "tool_calls": [
                    {"id": "call-" + str(len(received)), "type": "function",
                     "function": {"name": name, "arguments": json.dumps(args)}}]})

            def events(session):
                records = [json.loads(line) for line in (workspace / ".jharness/sessions" / (session + ".jsonl")).read_text(encoding="utf-8").splitlines()]
                assert records[0]["format"] == "jharness-jsonl" and records[0]["version"] == 1
                return records[1:]

            assert "API 1.14" in run("--version")
            run("--help")
            assert json.loads(run("doctor"))["ready"]
            assert not received
            report["checks"]["launcherDoctorNoNetwork"] = True
            call("write_file", {"path": "delivered.txt", "content": "M9 delivered"})
            text("file delivered")
            run("run", "write file", "--session", "approved", "--expect-file", "delivered.txt", stdin="y\n")
            assert (workspace / "delivered.txt").read_text() == "M9 delivered"
            assert any(e["type"] == "delivery.registered" for e in events("approved"))
            run("report", "--session", "approved")
            report["checks"]["approvedWriteAndReport"] = True
            call("write_file", {"path": "denied.txt", "content": "forbidden"})
            text("not delivered")
            run("run", "deny write", "--session", "denied", "--expect-file", "denied.txt", code=2)
            assert not (workspace / "denied.txt").exists()
            assert not any(e["type"] == "tool.intent" for e in events("denied"))
            report["checks"]["approvalEofDenies"] = True
            replies.put("WAIT")
            process = subprocess.Popen(command("run", "wait", "--session", "cancelled"),
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       text=True, encoding="utf-8", errors="replace", env=environment)
            cancel_primary = None
            try:
                assert waiting.wait(15), "Model request not observed"
                controls = [p for p in (workspace / ".jharness/runs").glob("*.run")
                            if "\nstatus=RUNNING\n" in p.read_text(encoding="utf-8")]
                assert len(controls) == 1
                run("cancel", "--run", controls[0].stem)
                stdout, stderr = process.communicate(timeout=15)
                assert process.returncode == 3, (stdout, stderr)
            except BaseException as error:
                cancel_primary = error
                raise
            finally:
                release.set()
                def reap_cancelled_process():
                    if process.poll() is not None:
                        return
                    try:
                        process.communicate(timeout=10)
                    except subprocess.TimeoutExpired:
                        if os.name == "nt":
                            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                           capture_output=True, timeout=10)
                        else:
                            process.kill()
                        process.communicate(timeout=5)
                finish_cleanup(report, [('cancelledProcessReap', reap_cancelled_process)], cancel_primary)
            pending = [e for e in events("cancelled") if e["type"] == "model.requested"]
            assert len(pending) == 1
            count = len(received)
            run("resume", "must refuse", "--session", "cancelled", code=6)
            assert len(received) == count
            run("sessions", "verify-request", "--session", "cancelled", "--event", pending[0]["eventId"],
                "--reason", "Loopback fixture stopped; abandoned response")
            call("read_file", {"path": "delivered.txt"})
            text("read existing delivery")
            run("resume", "read existing file", "--session", "cancelled", "--require-read", "delivered.txt")
            assert (workspace / "delivered.txt").read_text() == "M9 delivered"
            report["checks"]["cancelUnknownRefusesReplayAndExplicitRecovery"] = True
            for path in (workspace / ".jharness").rglob("*"):
                if path.is_file():
                    assert b"m9-local-fixture-only" not in path.read_bytes()
            report['httpFailures'] = list(http_failures)
            if http_failures:
                raise AssertionError('Loopback fixture HTTP protocol failures')
            report.update(status="PASS", modelRequests=len(received), paidRequests=0)
    except BaseException as error:
        primary = error
        raise
    finally:
        report['httpFailures'] = list(http_failures)
        release.set()
        if server is not None:
            def stop_server():
                if thread is not None and thread.is_alive():
                    server.shutdown()
                    thread.join(timeout=5)
                    if thread.is_alive():
                        raise RuntimeError("Smoke HTTP server did not stop")
            finish_cleanup(report, [('serverShutdown', stop_server), ('serverClose', server.server_close)], primary)



if __name__ == "__main__":
    main()
