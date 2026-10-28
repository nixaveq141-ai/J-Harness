"""Bounded real SSE read-only validation under the existing cumulative CNY2 grant.

No request on import or --audit. --run sends at most two requests per selected
protocol, never retries, and never compiles/executes generated code. The relay
buffers bounded SSE before credential checks; this tests wire protocol, not
incremental delivery latency. The historical evaluator remains frozen.
"""
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('readonly_grant', ROOT / 'scripts/deepseek-readonly-validation.py')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
MAX_RESPONSE = 2 * 1024 * 1024
PROTOCOLS = {'chat-completions': '/chat/completions', 'anthropic': '/messages', 'responses': '/responses'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def checked_request(path, body):
    value = json.loads(body)
    if value.get('stream') is not True:
        raise ValueError('SSE required')
    checked = dict(value, stream=False)
    base.checked_request(path, json.dumps(checked).encode())
    if len(body) > 32768:
        raise ValueError('Original input bound')
    return value


def contains_credential(value, credentials):
    """Check decoded JSON fields as well as the unchanged upstream wire bytes."""
    if isinstance(value, str):
        return any(credential in value for credential in credentials)
    if isinstance(value, dict):
        return any(contains_credential(k, credentials) or contains_credential(v, credentials)
                   for k, v in value.items())
    if isinstance(value, list):
        return any(contains_credential(item, credentials) for item in value)
    return False


def inspect_stream(path, data, *, credentials=()):
    if len(data) > MAX_RESPONSE:
        raise ValueError('SSE response bound')
    text = data.decode('utf-8', errors='strict').replace('\r\n', '\n')
    if not text.endswith('\n\n'):
        raise ValueError('Unterminated SSE frame')
    terminal = False
    count = 0
    numeric_usage = {}
    for block in text.split('\n\n'):
        lines = block.splitlines()
        values = [line[5:].lstrip(' ') for line in lines if line.startswith('data:')]
        if not values:
            continue
        if terminal:
            raise ValueError('Data after terminal event')
        payload = '\n'.join(values)
        count += 1
        if payload == '[DONE]':
            if path != '/chat/completions':
                raise ValueError('Wrong terminal framing')
            terminal = True
            continue
        value = json.loads(payload)
        if not isinstance(value, dict):
            raise ValueError('SSE JSON object required')
        if contains_credential(value, credentials):
            raise ValueError('Decoded reflected credential')
        if path == '/messages':
            terminal = value.get('type') == 'message_stop'
        elif path == '/responses':
            terminal = value.get('type') == 'response.completed'
        for holder in [value, value.get('message', {}), value.get('response', {})]:
            usage = holder.get('usage', {}) if isinstance(holder, dict) else {}
            if isinstance(usage, dict):
                for name in ('input_tokens', 'output_tokens', 'prompt_tokens', 'completion_tokens', 'total_tokens'):
                    number = usage.get(name)
                    if type(number) is int and number >= 0:
                        numeric_usage[name] = number
    if not terminal or count < 2:
        raise ValueError('Missing complete SSE terminal or payload')
    return dict(frames=count, terminal=True, numericUsage=numeric_usage, responseSha256=sha(data))


def read_response(response, *, clock=time.monotonic):
    deadline = clock() + 65
    data = bytearray()
    while True:
        if clock() >= deadline:
            raise ValueError('Total response deadline')
        block = response.read1(min(65536, MAX_RESPONSE + 1 - len(data)))
        if clock() >= deadline:
            raise ValueError('Total response deadline')
        if not block:
            return bytes(data)
        data.extend(block)
        if len(data) > MAX_RESPONSE:
            raise ValueError('SSE response bound')


def handler(wallet, key, token, path, records):
    class Relay(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            status, data, content_type = 502, b'{"error":"BOUNDED_SSE_RELAY_FAILURE"}', 'application/json'
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 < length <= 32768 or self.headers.get('Transfer-Encoding'):
                    raise ValueError('Input framing bound')
                self.connection.settimeout(10)
                body = self.rfile.read(length)
                if len(body) != length:
                    raise ValueError('Partial input')
                if self.path != path or len(records) >= 2:
                    raise ValueError('Fixed path/two-send bound')
                if (self.headers.get('Authorization') != 'Bearer ' + token
                        and self.headers.get('x-api-key') != token):
                    raise ValueError('Local authentication failed')
                checked_request(path, body)
                attempt = wallet.reserve(path, body)
                record = dict(attempt=attempt, status='RESERVED_UNKNOWN', requestSha256=sha(body))
                records.append(record)
                headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key}
                if path == '/messages':
                    headers.update({'x-api-key': key, 'anthropic-version': '2023-06-01'})
                request = urllib.request.Request(base.PATHS[path], body, headers)
                opener = urllib.request.build_opener(base.NoRedirect())
                try:
                    response = opener.open(request, timeout=10)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    upstream = response.code
                    raw = read_response(response)
                    if key.encode() in raw or token.encode() in raw:
                        raise ValueError('Reflected credential')
                    record['httpStatus'] = upstream
                    if upstream != 200 or response.headers.get_content_type() != 'text/event-stream':
                        wallet.receipt(attempt, upstream, raw)
                        raise ValueError('Successful SSE content type required')
                    inspected = inspect_stream(path, raw, credentials=(key, token))
                    wallet.receipt(attempt, upstream, raw)
                    record.update(inspected, status='COMPLETE_SSE')
                    status, data, content_type = upstream, raw, 'text/event-stream'
            except Exception:
                # The durable reservation survives any unknown send; no transparent retry.
                pass
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(data)
    return Relay


def existing_wallet():
    if not (base.DIRECTORY / 'wallet.sqlite').is_file():
        raise ValueError('Existing grant required; no new ledger or reset')
    return base.Wallet(base.DIRECTORY)


def run(protocols):
    wallet = existing_wallet()
    if len(protocols) != len(set(protocols)):
        raise ValueError('Duplicate protocol')
    audit = wallet.audit()
    if (len(audit['attempts']) + 2 * len(protocols) > base.MAX_ATTEMPTS
            or audit['remainingMicroCny'] < 2 * len(protocols) * base.RESERVATION):
        raise ValueError('Insufficient existing cumulative grant')
    key = os.environ.get('DEEPSEEK_API_KEY')
    if not key:
        raise ValueError('DEEPSEEK_API_KEY missing')
    java = Path(os.environ['JAVA_HOME']) / ('bin/java.exe' if os.name == 'nt' else 'bin/java')
    jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    inputs = [Path(__file__).resolve(), Path(base.__file__), jar]
    sources = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in inputs}
    directory = ROOT / '.build-cache' / ('deepseek-sse-' + secrets.token_hex(8))
    directory.mkdir()
    result = dict(schemaVersion=1, status='INCOMPLETE', sourcesBeforeSha256=sources,
                  protocols=[], accounting='EXISTING_CUMULATIVE_CNY2_NO_REFUNDS')
    try:
        for protocol in protocols:
            token = secrets.token_hex(32)
            records = []
            path = PROTOCOLS[protocol]
            server = HTTPServer(('127.0.0.1', 0), handler(wallet, key, token, path, records))
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            work = directory / protocol
            work.mkdir()
            fixture = b'Validation fixture: amber=17, cobalt=25. Sum=42.\n'
            (work / 'fixture.txt').write_bytes(fixture)
            config = dict(baseUrl='http://127.0.0.1:' + str(server.server_port), model='deepseek-flash',
                          apiKeyEnv='JHARNESS_SSE_RELAY_TOKEN', protocol=protocol, maxSteps=3,
                          durationSeconds=150, requestTimeoutSeconds=80, contextTokens=base.INPUT_CAP,
                          inputTokenCeiling=base.INPUT_CAP, maxOutputTokens=base.OUTPUT_CAP,
                          maxCost='0.60', currency='CNY', inputPerMillion='2', outputPerMillion='8',
                          streaming=True, streamSupported=True, policy='read-only', enabledTools=['read_file'])
            config.update(dict(reasoningEffort='none') if protocol == 'responses' else dict(disableThinking=True))
            config_path = work / 'config.json'
            config_path.write_bytes(json.dumps(config).encode())
            env = {k: os.environ[k] for k in ('SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'JAVA_HOME', 'PATH') if k in os.environ}
            env['JHARNESS_SSE_RELAY_TOKEN'] = token
            try:
                command = [str(java), '-jar', str(jar), '--workspace', str(work), '--config', str(config_path),
                           'run', 'Read fixture.txt with read_file exactly once, then state its sum. Do not modify files or run commands.',
                           '--session', 'sse-readonly', '--require-read', 'fixture.txt']
                process = subprocess.run(command, env=env, input=b'', stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, timeout=180)
                (work / 'cli.log').write_bytes(process.stdout[:1024*1024].replace(key.encode(), b'[REDACTED]').replace(token.encode(), b'[REDACTED]'))
                facts_path = work / '.jharness/sessions/sse-readonly.jsonl'
                facts = [json.loads(line) for line in facts_path.read_bytes().splitlines()][1:] if facts_path.is_file() else []
                passed = (process.returncode == 0 and len(records) == 2
                          and all(r.get('status') == 'COMPLETE_SSE' for r in records)
                          and base.inspect_facts(facts, fixture) and (work / 'fixture.txt').read_bytes() == fixture)
                result['protocols'].append(dict(protocol=protocol, exitCode=process.returncode,
                    passed=passed, requests=records, factsSha256=sha(facts_path.read_bytes()) if facts_path.is_file() else None))
                if not passed:
                    raise ValueError('SSE/tool/final-fact oracle failed; no retry')
            finally:
                server.shutdown()
                server.server_close()
                thread.join(2)
        result['sourcesAfterSha256'] = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in inputs}
        if result['sourcesAfterSha256'] != sources:
            raise ValueError('Probe source/JAR drift')
        result['status'] = 'PASS'
    finally:
        result['wallet'] = wallet.audit()
        (directory / 'result.json').write_bytes((json.dumps(result, indent=2) + '\n').encode())
    return dict(status=result['status'], directory=directory.relative_to(ROOT).as_posix(),
                protocols=[dict(protocol=r['protocol'], passed=r['passed'], requests=len(r['requests'])) for r in result['protocols']],
                reservedMicroCny=result['wallet']['reservedMicroCny'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--run', action='store_true')
    mode.add_argument('--audit', action='store_true')
    parser.add_argument('--protocol', action='append', choices=sorted(PROTOCOLS))
    args = parser.parse_args()
    print(json.dumps(run(args.protocol or ['chat-completions', 'anthropic']) if args.run
                     else existing_wallet().audit(), indent=2))
