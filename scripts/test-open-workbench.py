"""Real Windows launcher smoke: no browser/clipboard mutation and no model requests."""
import hashlib
import datetime
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
TOKEN = 'launcher_test_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'
PS = ['powershell.exe', '-NoProfile', '-STA', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'scripts/open-workbench.ps1')]
ENV = {**os.environ, 'LAUNCHER_TEST_TOKEN': TOKEN}
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request(url, authorized=True):
    headers = {'Authorization': 'Bearer ' + TOKEN} if authorized else {}
    with HTTP.open(urllib.request.Request(url, headers=headers), timeout=3) as response:
        return response.status, response.read()


def start(arguments, cmd=False):
    if cmd:
        inner = subprocess.list2cmdline([str(ROOT / 'web-ui.cmd')] + arguments)
        command = 'cmd.exe /d /s /c "' + inner + '"'
    else:
        command = PS + arguments
    p = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding='utf-8', errors='replace', cwd=ROOT, env=ENV)
    output, messages = [], queue.Queue()

    def read():
        for line in p.stdout:
            output.append(line)
            messages.put(line)
    threading.Thread(target=read, daemon=True).start()
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            line = messages.get(timeout=0.2)
            if line.startswith('WORKBENCH_READY '):
                return p, line.strip().split(' ', 1)[1], output
        except queue.Empty:
            if p.poll() is not None:
                raise AssertionError('Launcher did not start: ' + ''.join(output))
    p.stdin.close()
    p.wait(timeout=15)
    raise AssertionError('Launcher readiness timeout')


def stop(p, url, output):
    p.stdin.write(':quit\n')
    p.stdin.flush()
    p.stdin.close()
    assert p.wait(timeout=15) == 0, ''.join(output)
    assert TOKEN not in ''.join(output)
    assert 'Paste it' not in ''.join(output) and 'Credential copied' not in ''.join(output)
    try:
        request(url)
        raise AssertionError('Owned HTTP host remained alive after quit')
    except (urllib.error.URLError, ConnectionError, TimeoutError):
        pass


def main():
    if os.name != 'nt':
        raise SystemExit('This smoke requires Windows PowerShell 5.1 and Java21')
    checks = []
    with tempfile.TemporaryDirectory(prefix='M10 launcher space & boundary ', dir=ROOT / '.build-cache') as directory:
        base = Path(directory)
        config = base / 'fixture config.json'
        settings = {'baseUrl': 'http://127.0.0.1:9', 'model': 'fixture', 'apiKeyEnv': 'LAUNCHER_MODEL_KEY',
                    'ecosystem': {'web': {'tokenEnv': 'LAUNCHER_TEST_TOKEN', 'catalogId': 'launcher-catalog'}}}
        config.write_text(json.dumps(settings), encoding='utf-8')
        args = ['-Workspace', str(base), '-Config', str(config), '-NoBrowser', '-NoClipboard']
        p, url, output = start(args)
        try:
            status, body = request(url + 'v1/info')
            assert status == 200 and json.loads(body)['profiles'] == ['default']
            assert request(url, False)[0] == 200
            try:
                request(url + 'v1/info', False)
                raise AssertionError('API exposed without token')
            except urllib.error.HTTPError as e:
                assert e.code == 401
            checks.append('PowerShell entry: actual UI/API ready, authenticated metadata, private API and paths with spaces/ampersand')
            # A second launcher must not stop or reuse the first host with a new token.
            other = subprocess.run(PS + args, input=':quit\n', text=True, encoding='utf-8', errors='replace', capture_output=True, env=ENV, timeout=40)
            assert other.returncode == 1 and 'did not become ready' in other.stdout
            assert request(url + 'v1/info')[0] == 200
            assert TOKEN not in other.stdout + other.stderr
            checks.append('duplicate launch fails without stopping existing service or exposing token')
        finally:
            stop(p, url, output)
        checks.append('quit gracefully closes owned Java service and leaves no listening port')
        # Exercise the same ASCII-named .cmd the user double-clicks, not only the ps1.
        p, url, output = start(args, cmd=True)
        try:
            assert request(url)[0] == 200
        finally:
            stop(p, url, output)
        checks.append('web-ui.cmd starts and stops the real packaged UI')
        saved = base / '.jharness/config.json'
        saved.write_text(json.dumps(settings), encoding='utf-8')
        ENV.pop('LAUNCHER_TEST_TOKEN')
        try:
            p, url, output = start(['-Workspace', str(base), '-NoBrowser', '-NoClipboard'])
            try:
                assert request(url, False)[0] == 200
                try:
                    request(url + 'v1/info')
                    raise AssertionError('A previous credential authenticated to a fresh host')
                except urllib.error.HTTPError as e:
                    assert e.code == 401
            finally:
                stop(p, url, output)
        finally:
            ENV['LAUNCHER_TEST_TOKEN'] = TOKEN
        checks.append('saved workspace config is selected and absent credential generates a fresh private token')
        bad = subprocess.run(PS + args + ['-JavaHome', str(base / 'missing-java')], input='', text=True, encoding='utf-8', errors='replace', capture_output=True, env=ENV, timeout=10)
        assert bad.returncode == 1 and 'Java 21 was not found' in bad.stdout
        checks.append('missing Java produces bounded actionable error')
        settings['ecosystem']['web']['tokenEnv'] = 'PATH'
        config.write_text(json.dumps(settings), encoding='utf-8')
        bad = subprocess.run(PS + args, input='', text=True, encoding='utf-8', errors='replace', capture_output=True, env=ENV, timeout=10)
        assert bad.returncode == 1 and 'different environment variable names' in bad.stdout
        checks.append('token cannot overwrite protected environment variables')
        assert not (ROOT / '打开工作台.cmd').exists()
        checks.append('entry filename is ASCII web-ui.cmd')
    sha = lambda p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest()
    result = {'status': 'PASS', 'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'checks': checks, 'browserAndClipboardSideEffects': 'browser opening disabled for automation; production opens browser directly and never copies a credential',
              'paidRequests': 0, 'modelEndpoint': 'unreachable loopback :9; no task submissions',
              'sourceSha256': {p: sha(p) for p in ['web-ui.cmd', 'scripts/open-workbench.ps1', 'scripts/test-open-workbench.py']},
              'jarSha256': sha('harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')}
    (ROOT / '.build-cache/M10-launcher-results.json').write_bytes((json.dumps(result, indent=2) + '\n').encode())
    print('PASS: ' + str(len(checks)) + ' real launcher checks; no browser/clipboard changes, paid calls or lingering host')


if __name__ == '__main__':
    main()
