"""Install the actual ZIP and exercise CLI against a loopback model fixture.

No paid model requests. Output/logs live under ignored target/.
"""
import datetime
import hashlib
import http.server
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    archive = ROOT / "target/jharness-0.1.0-SNAPSHOT-candidate.zip"
    output = ROOT / "target/candidate-smoke.json"
    report = {"capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "zipSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
              "status": "INCOMPLETE", "checks": {}}
    replies, received = queue.Queue(), []
    waiting = threading.Event()
    release = threading.Event()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            data = self.rfile.read(int(self.headers["Content-Length"]))
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

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    environment = {**os.environ, "M9_SMOKE_KEY": "m9-local-fixture-only"}
    try:
        with tempfile.TemporaryDirectory(prefix="M9 install space ", dir=ROOT / "target") as temp:
            install = Path(temp)
            with zipfile.ZipFile(archive) as z:
                for name in z.namelist():
                    if ".." in Path(name).parts or Path(name).is_absolute():
                        raise ValueError("Unsafe candidate entry")
                z.extractall(install)
            install /= "jharness"
            manifest = json.loads((install / "manifest.json").read_text())
            for name, digest in manifest["files"].items():
                assert hashlib.sha256((install / name).read_bytes()).hexdigest() == digest, name
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

            assert "API 1.9" in run("--version")
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
        server.shutdown()
        server.server_close()
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
