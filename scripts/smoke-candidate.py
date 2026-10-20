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
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

from evidence_io import atomic_write_json, bounded_path, require_sha256, strict_json_loads

ROOT = Path(__file__).resolve().parents[1]
MAX_ZIP_BYTES = 256 * 1024 * 1024
MAX_EXPANDED_BYTES = 512 * 1024 * 1024


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


def validate_candidate(raw, legal_files):
    """Validate a bounded in-memory snapshot before any candidate bytes are installed."""
    if not isinstance(raw, bytes) or not raw or len(raw) > MAX_ZIP_BYTES:
        raise ValueError("Candidate ZIP byte limit or empty input")
    if not legal_files or not {"LICENSE", "NOTICE", "third-party/inventory.json", "third-party/upstream/sources.json"} <= set(legal_files):
        raise ValueError("Incomplete required legal set")
    files, seen = {}, set()
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
            files[name] = archive.read(entry)
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
        for name in legal_files:
            if name.startswith("third-party/"):
                packaged = "META-INF/jharness/" + name
                if packaged not in names or jar.read(packaged) != files[name]:
                    raise ValueError("Legal file not preserved in shaded JAR")
        if any(name.startswith("org/junit/") for name in names):
            raise ValueError("Test runtime leaked into candidate")
    return manifest, files


def main():
    output = ROOT / "target/candidate-smoke.json"
    report = {"capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "status": "INCOMPLETE", "checks": {}}
    atomic_write_json(output, report)
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
        report["status"] = "INCOMPLETE"
        report["failureType"] = type(error).__name__
        raise
    finally:
        atomic_write_json(output, report)
    print(json.dumps(report))


def exercise(report, manifest, files):
    replies, received = queue.Queue(), []
    waiting = threading.Event()
    release = threading.Event()

    class Handler(http.server.BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def do_POST(self):
            length = int(self.headers["Content-Length"])
            if not 0 < length <= 1024 * 1024:
                self.send_error(413)
                return
            data = self.rfile.read(length)
            received.append(json.loads(data))
            message = replies.get(timeout=10)
            if message == "WAIT":
                waiting.set()
                release.wait(25)
                message = {"role": "assistant", "content": "late response"}
            body = json.dumps({"choices": [{"message": message,
                               "finish_reason": "tool_calls" if "tool_calls" in message else "stop"}],
                               "usage": {"prompt_tokens": 10, "completion_tokens": 5}}).encode()
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass

        def log_message(self, *args):
            pass

    server = None
    thread = None
    environment = {**os.environ, "M9_SMOKE_KEY": "m9-local-fixture-only"}
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
            try:
                assert waiting.wait(15), "Model request not observed"
                controls = [p for p in (workspace / ".jharness/runs").glob("*.run")
                            if "\nstatus=RUNNING\n" in p.read_text(encoding="utf-8")]
                assert len(controls) == 1
                run("cancel", "--run", controls[0].stem)
                stdout, stderr = process.communicate(timeout=15)
                assert process.returncode == 3, (stdout, stderr)
            finally:
                release.set()
                if process.poll() is None:
                    try:
                        process.communicate(timeout=10)
                    except subprocess.TimeoutExpired:
                        if os.name == "nt":
                            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                           capture_output=True, timeout=10)
                        else:
                            process.kill()
                        process.communicate(timeout=5)
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
            report.update(status="PASS", modelRequests=len(received), paidRequests=0)
    finally:
        release.set()
        if server is not None:
            try:
                if thread is not None and thread.is_alive():
                    server.shutdown()
                    thread.join(timeout=5)
                    if thread.is_alive():
                        raise RuntimeError("Smoke HTTP server did not stop")
            finally:
                server.server_close()


if __name__ == "__main__":
    main()
