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
import urllib.parse
import socket
import ctypes
from ctypes import wintypes
from collections import deque

from evidence_io import atomic_write_json

ROOT = Path(__file__).resolve().parents[1]
TOKEN = 'launcher_test_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'
PS = ['powershell.exe', '-NoProfile', '-STA', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'scripts/open-workbench.ps1')]
ENV = {**os.environ, 'LAUNCHER_TEST_TOKEN': TOKEN}
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request(url, authorized=True, token=TOKEN):
    headers = {'Authorization': 'Bearer ' + token} if authorized else {}
    with HTTP.open(urllib.request.Request(url, headers=headers), timeout=3) as response:
        return response.status, response.read()


OWNED={}
OWNED_RECORDS=[]
def capture_owner(p):
    script="$all=Get-CimInstance Win32_Process; $ids=[System.Collections.Generic.HashSet[int]]::new(); [void]$ids.Add("+str(p.pid)+"); do {$added=$false;foreach($item in $all){if($ids.Contains([int]$item.ParentProcessId) -and $ids.Add([int]$item.ProcessId)){$added=$true}}}while($added);$all|Where-Object {$ids.Contains([int]$_.ProcessId) -and $_.Name -eq 'java.exe'}|ForEach-Object {$_.ProcessId}"
    ids=[int(v) for v in subprocess.check_output(['powershell.exe','-NoProfile','-Command',script],text=True,timeout=10).splitlines() if v.strip()]
    assert len(ids)==1,'Expected exactly one launcher-owned Java host'
    kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.restype=wintypes.HANDLE
    handle=kernel.OpenProcess(0x100000|0x1000,False,ids[0])
    assert handle,'Cannot retain owned Java process identity'
    kernel.GetProcessTimes.argtypes=[wintypes.HANDLE]+[ctypes.POINTER(wintypes.FILETIME)]*4
    times=[wintypes.FILETIME() for _ in range(4)];assert kernel.GetProcessTimes(handle,*[ctypes.byref(t) for t in times])
    record=dict(pid=ids[0],createdFileTime=(times[0].dwHighDateTime<<32)|times[0].dwLowDateTime,launcherPid=p.pid)
    OWNED[p.pid]=(handle,record);OWNED_RECORDS.append(record)

def confirm_owner_stopped(p):
    handle,record=OWNED.pop(p.pid)
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    try:assert kernel.WaitForSingleObject(handle,5000)==0,'Launcher-owned Java has not exited'
    finally:kernel.CloseHandle(handle)
    record['exitSignalConfirmed']=True

def start(arguments, cmd=False, stub=None):
    if cmd:
        inner = subprocess.list2cmdline([str(ROOT / 'web-ui.cmd')] + arguments)
        command = 'cmd.exe /d /s /c "' + inner + '"'
    else:
        command = (PS if stub is None else PS[:6]+[str(stub)]) + arguments
    p = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding='utf-8', errors='replace', cwd=ROOT, env=ENV)
    output, messages = deque(maxlen=256), queue.Queue(maxsize=128)

    def read():
        for line in p.stdout:
            output.append(line)
            try:messages.put_nowait(line)
            except queue.Full:pass
    threading.Thread(target=read, daemon=True).start()
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            line = messages.get(timeout=0.2)
            if line.startswith('WORKBENCH_READY '):
                capture_owner(p)
                return p, line.strip().split(' ', 1)[1], output
        except queue.Empty:
            if p.poll() is not None:
                raise AssertionError('Launcher did not start: ' + ''.join(output))
    p.stdin.close()
    try:p.wait(timeout=15)
    except subprocess.TimeoutExpired:
        if p.poll() is None:subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],capture_output=True,timeout=10)
        p.wait(timeout=5)
    raise AssertionError('Launcher readiness timeout; only this launcher-owned tree was stopped')


def stop(p, url, output):
    p.stdin.write(':quit\n')
    p.stdin.flush()
    p.stdin.close()
    assert p.wait(timeout=15) == 0, ''.join(output)
    confirm_owner_stopped(p)
    assert TOKEN not in ''.join(output)
    assert 'Paste it' not in ''.join(output) and 'Credential copied' not in ''.join(output)
    with socket.socket() as listener:
        listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        listener.bind(('127.0.0.1',urllib.parse.urlparse(url).port));listener.listen(1)


def main():
    output = ROOT / '.build-cache/M10-launcher-results.json'
    report = {'status': 'INCOMPLETE', 'checks': [],
              'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    atomic_write_json(output, report)
    try:
        if not __debug__:
            raise RuntimeError('Launcher smoke requires Python assertions enabled; do not use -O')
        exercise()
    except BaseException as error:
        report['failureType'] = type(error).__name__
        atomic_write_json(output, report)
        raise


def exercise():
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
        # Replace only the browser opener in a child PowerShell, leaving actual Java ownership intact.
        marker=base/'browser-marker.txt';stub=base/'browser-stub.ps1'
        quote=lambda value:"'"+str(value).replace("'","''")+"'"
        stub.write_text("function global:Start-Process {param($FilePath,$ErrorAction) [IO.File]::WriteAllText("+quote(marker)+",$FilePath)}\n& "+quote(ROOT/'scripts/open-workbench.ps1')+" @args\n",encoding='utf-8')
        p,url,output=start([a for a in args if a!='-NoBrowser'],stub=stub)
        try:assert marker.read_text().strip()==url
        finally:stop(p,url,output)
        checks.append('web-ui.cmd starts and stops the real packaged UI')
        saved = base / '.jharness/config.json'
        settings['model']='saved-configuration-unique-model'
        saved.write_text(json.dumps(settings), encoding='utf-8')
        ENV.pop('LAUNCHER_TEST_TOKEN')
        try:
            p, url, output = start(['-Workspace', str(base), '-NoBrowser', '-NoClipboard'])
            try:
                assert request(url, False)[0] == 200
                bootstrap=urllib.request.Request(url+'v1/browser-session',data=b'{}',headers={'Content-Type':'application/json','Origin':url.rstrip('/'),'Sec-Fetch-Site':'same-origin'})
                with HTTP.open(bootstrap,timeout=3) as response:fresh=json.loads(response.read())['token']
                metadata=request(url+'v1/models',token=fresh)[1]
                assert b'saved-configuration-unique-model' in metadata, 'Saved branch silently fell back to defaults'
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
    result = {'status': 'PASS', 'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'checks': checks, 'browserAndClipboardSideEffects': 'browser opening replaced by a controlled child-PowerShell marker; no user browser/clipboard mutation',
              'ownedJavaProcesses':OWNED_RECORDS, 'paidRequests': 0, 'modelEndpoint': 'unreachable loopback :9; no task submissions',
              'sourceSha256': {p: sha(p) for p in ['web-ui.cmd', 'scripts/open-workbench.ps1', 'scripts/test-open-workbench.py']},
              'jarSha256': sha('harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')}
    atomic_write_json(ROOT / '.build-cache/M10-launcher-results.json', result)
    print('PASS: ' + str(len(checks)) + ' real launcher checks; no browser/clipboard changes, paid calls or lingering host')


if __name__ == '__main__':
    main()
