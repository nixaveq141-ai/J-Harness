"""Dedicated 2026-10-26 CNY2 grant. No generated code is compiled or executed.

Only the explicit --run entry sends requests. Imports and --audit are offline.
The frozen historical live-evaluate.py remains frozen. Reservations never refund.
"""
import argparse
from contextlib import contextmanager
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import secrets
import sqlite3
import subprocess
import threading
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / '.build-cache/deepseek-readonly-2026-10-26'
GRANT = 'deepseek-readonly-2026-10-26-CNY2'
LIMIT_MICRO_CNY = 2_000_000
INPUT_CAP = 69_632
OUTPUT_CAP = 1024
RESERVATION = INPUT_CAP * 2 + OUTPUT_CAP * 8
MAX_ATTEMPTS = 12
PATHS = {'/chat/completions': 'https://api.deepseek.com/chat/completions',
         '/responses': 'https://api.deepseek.com/responses',
         '/messages': 'https://api.deepseek.com/anthropic/v1/messages'}


class Wallet:
    """SQLite FULL synchronous transactions reserve before send, across processes.

    The directory is created once. A missing, damaged or mismatched existing wallet
    fails closed. No CLI option creates a second wallet or resets this grant.
    """
    def __init__(self, directory):
        self.path = directory / 'wallet.sqlite'
        try:
            directory.mkdir()
            fresh = True
        except FileExistsError:
            fresh = False
        if not fresh and not self.path.is_file():
            raise ValueError('Existing grant directory has no wallet; manual reconciliation required')
        with self.connect() as db:
            if fresh:
                db.execute('CREATE TABLE grant_info (identity TEXT, cap INTEGER)')
                db.execute('INSERT INTO grant_info VALUES (?, ?)', (GRANT, LIMIT_MICRO_CNY))
                db.execute('CREATE TABLE attempts (id INTEGER PRIMARY KEY, path TEXT, sha TEXT, '
                           'reserved INTEGER, status INTEGER, response_sha TEXT, usage TEXT)')
            if db.execute('SELECT identity, cap FROM grant_info').fetchall() != [(GRANT, LIMIT_MICRO_CNY)]:
                raise ValueError('Grant mismatch')
            if db.execute('PRAGMA integrity_check').fetchone() != ('ok',):
                raise ValueError('Wallet integrity failure')

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        try:
            db.execute('PRAGMA synchronous=FULL')
            with db:
                yield db
        finally:
            db.close()

    def reserve(self, path, body):
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            count, used = db.execute('SELECT COUNT(*), COALESCE(SUM(reserved),0) FROM attempts').fetchone()
            if count >= MAX_ATTEMPTS or used + RESERVATION > LIMIT_MICRO_CNY:
                raise ValueError('Cumulative grant exhausted')
            cursor = db.execute('INSERT INTO attempts(path,sha,reserved) VALUES (?,?,?)',
                                (path, hashlib.sha256(body).hexdigest(), RESERVATION))
            return cursor.lastrowid

    def receipt(self, attempt, status, body):
        # Only numeric usage and hashes persist here; never raw bodies or errors.
        usage = {}
        try:
            raw = json.loads(body).get('usage', {})
            usage = {k: v for k, v in raw.items() if isinstance(v, int) and not isinstance(v, bool) and v >= 0}
        except (ValueError, TypeError, AttributeError):
            pass
        with self.connect() as db:
            db.execute('UPDATE attempts SET status=?,response_sha=?,usage=? WHERE id=?',
                       (status, hashlib.sha256(body).hexdigest(), json.dumps(usage), attempt))

    def audit(self):
        with self.connect() as db:
            rows = db.execute('SELECT id,path,sha,reserved,status,response_sha,usage FROM attempts ORDER BY id').fetchall()
        used = sum(row[3] for row in rows)
        return dict(grant=GRANT, currency='CNY', capMicroCny=LIMIT_MICRO_CNY,
                    reservedMicroCny=used, remainingMicroCny=LIMIT_MICRO_CNY-used,
                    accounting='NON_REFUNDABLE_UPPER_BOUND_NOT_PROVIDER_BILL', attempts=rows)


def checked_request(path, body):
    if path not in PATHS or len(body) > 32768:
        raise ValueError('Unsupported path or input bound')
    value = json.loads(body)
    cap = value.get('max_output_tokens' if path == '/responses' else 'max_tokens')
    if value.get('model') != 'deepseek-flash' or type(cap) is not int or not 1 <= cap <= OUTPUT_CAP:
        raise ValueError('Model/output bound mismatch')
    if value.get('stream') is not False:
        raise ValueError('Only bounded non-streaming requests allowed')
    if type(value.get('n', 1)) is not int or value.get('n', 1) != 1:
        raise ValueError('Multiple completions forbidden')
    messages = value.get('input' if path == '/responses' else 'messages', [])
    if not isinstance(messages, list) or len(messages) > 32:
        raise ValueError('Message framing bound')
    if path == '/responses':
        if value.get('reasoning') != {'effort': 'none'}:
            raise ValueError('Thinking must be disabled')
    elif value.get('thinking') != {'type': 'disabled'}:
        raise ValueError('Thinking must be disabled')
    tools = value.get('tools', [])
    for tool in tools:
        function = tool.get('function', tool)
        if function.get('name') != 'read_file' or tool.get('type', 'function') != 'function':
            raise ValueError('Only read_file function allowed')
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('Provider redirects forbidden')


def handler(wallet, key, local_token):
    class Relay(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            status, data = 502, b'{"error":"BOUNDED_RELAY_FAILURE"}'
            try:
                if self.headers.get('Authorization') != 'Bearer ' + local_token and self.headers.get('x-api-key') != local_token:
                    raise ValueError('Local authentication failed')
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 < length <= 32768 or self.headers.get('Transfer-Encoding'):
                    raise ValueError('Input bound')
                self.connection.settimeout(10)
                body = self.rfile.read(length)
                if len(body) != length:
                    raise ValueError('Partial input')
                checked_request(self.path, body)
                attempt = wallet.reserve(self.path, body)
                headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key}
                if self.path == '/messages':
                    headers.update({'x-api-key': key, 'anthropic-version': '2023-06-01'})
                request = urllib.request.Request(PATHS[self.path], body, headers)
                opener = urllib.request.build_opener(NoRedirect())
                try:
                    response = opener.open(request, timeout=65)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    status = response.code
                    data = response.read(2 * 1024 * 1024 + 1)
                if len(data) > 2 * 1024 * 1024:
                    raise ValueError('Response bound')
                if key.encode() in data or local_token.encode() in data:
                    raise ValueError('Credential reflected in response')
                wallet.receipt(attempt, status, data)
            except Exception:
                # An unknown send retains its committed reservation. No retry here.
                status, data = 502, b'{"error":"BOUNDED_RELAY_FAILURE"}'
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
    return Relay


def inspect_facts(facts, fixture):
    completed = [e['payload'] for e in facts if e['type'] == 'tool.completed']
    ended = [e['payload'] for e in facts if e['type'] == 'turn.ended']
    answers = [e['payload'].get('content', '') for e in facts if e['type'] == 'assistant.message']
    return (len(completed) == 1 and completed[0].get('tool') == 'read_file'
            and completed[0].get('status') == 'SUCCESS'
            and completed[0].get('sideEffectLevel') == 'READ_ONLY'
            and completed[0].get('value', {}).get('path') == 'fixture.txt'
            and completed[0].get('value', {}).get('version') == hashlib.sha256(fixture).hexdigest()
            and completed[0].get('value', {}).get('text') == '1: ' + fixture.decode()
            and len(ended) == 1 and ended[0].get('status') == 'COMPLETED'
            and bool(answers) and '42' in answers[-1])


def run(wallet):
    key = os.environ.get('DEEPSEEK_API_KEY')
    if not key:
        raise ValueError('DEEPSEEK_API_KEY missing')
    jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    java = Path(os.environ['JAVA_HOME']) / 'bin/java.exe'
    if not jar.is_file() or not java.is_file():
        raise ValueError('Built JAR or Java missing')
    local_token = secrets.token_hex(32)
    server = HTTPServer(('127.0.0.1', 0), handler(wallet, key, local_token))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    results = []
    try:
        for protocol in ('chat-completions', 'responses', 'anthropic'):
            directory = DIRECTORY / (protocol + '-' + secrets.token_hex(4))
            directory.mkdir()
            fixture = b'Validation fixture: amber=17, cobalt=25. Sum=42.\n'
            (directory / 'fixture.txt').write_bytes(fixture)
            config = dict(baseUrl='http://127.0.0.1:' + str(server.server_port),
                          model='deepseek-flash', apiKeyEnv='JHARNESS_LIVE_RELAY_TOKEN',
                          protocol=protocol, maxSteps=3, durationSeconds=180, requestTimeoutSeconds=75,
                          contextTokens=INPUT_CAP, inputTokenCeiling=INPUT_CAP,
                          maxOutputTokens=OUTPUT_CAP, maxCost='0.60', currency='CNY',
                          inputPerMillion='2', outputPerMillion='8', streaming=False,
                          policy='read-only', enabledTools=['read_file'])
            config.update(dict(reasoningEffort='none') if protocol == 'responses' else dict(disableThinking=True))
            path = directory / 'config.json'
            path.write_text(json.dumps(config), encoding='utf-8')
            env = {name: os.environ[name] for name in ('SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'JAVA_HOME', 'PATH') if name in os.environ}
            env['JHARNESS_LIVE_RELAY_TOKEN'] = local_token
            command = [str(java), '-jar', str(jar), '--workspace', str(directory), '--config', str(path),
                       'run', 'Read fixture.txt with read_file once, then state its sum. Do not modify files or run commands.',
                       '--session', 'live-readonly', '--require-read', 'fixture.txt']
            result = subprocess.run(command, env=env, input=b'', stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=200)
            # Keep bounded local diagnostics; never print raw provider/CLI output.
            (directory / 'cli.log').write_bytes(result.stdout[:1024*1024]
                                                .replace(key.encode(), b'[REDACTED]')
                                                .replace(local_token.encode(), b'[REDACTED]'))
            facts_path = directory / '.jharness/sessions/live-readonly.jsonl'
            facts = [json.loads(line) for line in facts_path.read_text(encoding='utf-8').splitlines()][1:] if facts_path.exists() else []
            passed = (result.returncode == 0 and inspect_facts(facts, fixture)
                      and (directory / 'fixture.txt').read_bytes() == fixture)
            results.append(dict(protocol=protocol, exitCode=result.returncode, passed=passed,
                                jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                                factsSha256=hashlib.sha256(facts_path.read_bytes()).hexdigest() if facts_path.exists() else None,
                                directory=directory.relative_to(ROOT).as_posix()))
            if not passed:
                break
    finally:
        server.shutdown()
        server.server_close()
    output = dict(results=results, wallet=wallet.audit())
    (DIRECTORY / 'latest.json').write_text(json.dumps(output, indent=2) + '\n', encoding='utf-8')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', action='store_true', help='Use the authorized cumulative CNY2 grant')
    parser.add_argument('--audit', action='store_true', help='Offline wallet inspection')
    args = parser.parse_args()
    if args.run == args.audit:
        parser.error('Choose exactly one of --run or --audit')
    if args.audit and not DIRECTORY.exists():
        print(json.dumps(dict(grant=GRANT, status='NOT_STARTED', reservedMicroCny=0)))
        return
    wallet = Wallet(DIRECTORY)
    print(json.dumps(run(wallet) if args.run else wallet.audit(), indent=2))


if __name__ == '__main__':
    main()
