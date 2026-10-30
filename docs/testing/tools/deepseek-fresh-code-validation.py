"""Explicit RMB5 grant: fresh code through real CLI, compilation only in Docker CI.

No paid request on import or --audit. Original RMB2 ledger is never opened here.
Fixed synthetic fixture, read_file only, twelve total upstream attempts, no retries.
"""
import argparse
import hashlib
from http.server import HTTPServer
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import threading

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('fresh_grant_primitives', ROOT/'scripts/deepseek-readonly-validation.py')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
# This is a separate module instance and a distinct immutable grant identity/path.
base.GRANT = 'deepseek-fresh-code-2026-10-29-CNY5'
base.LIMIT_MICRO_CNY = 5_000_000
base.OUTPUT_CAP = 2048
base.MAX_ATTEMPTS = 12
base.RESERVATION = base.INPUT_CAP*2 + base.OUTPUT_CAP*8
DIRECTORY = ROOT/'.build-cache/deepseek-fresh-code-2026-10-29'
REQUIREMENTS = b'''Write Java 21 source, package example; public final class Calculator.
public int add(int a,int b) and public int multiply(int a,int b): Java int arithmetic, including wraparound.
public long sumCsv(String csv): parse comma-separated signed base-10 long integers and sum with overflow detection.
Empty or whitespace-only input returns 0. Trim whitespace around each field. Reject null, empty fields,
non-integers, individual integer overflow and sum overflow with IllegalArgumentException (subclasses allowed).
Use only JDK classes, no IO, environment, network, processes, reflection, native calls or external dependencies.
'''


def digest(data):
    return hashlib.sha256(data).hexdigest()


def extract(facts, fixture):
    tools = [e['payload'] for e in facts if e['type'] == 'tool.completed']
    ended = [e['payload'] for e in facts if e['type'] == 'turn.ended']
    answers = [e['payload'].get('content', '') for e in facts if e['type'] == 'assistant.message']
    if (len(tools) != 1 or tools[0].get('tool') != 'read_file' or tools[0].get('status') != 'SUCCESS'
            or tools[0].get('sideEffectLevel') != 'READ_ONLY'
            or tools[0].get('value', {}).get('path') != 'requirements.txt'
            or tools[0].get('value', {}).get('version') != digest(fixture)
            or len(ended) != 1 or ended[0].get('status') != 'COMPLETED' or not answers):
        raise ValueError('Independent CLI fact verification rejected')
    text = answers[-1].strip()
    blocks = re.findall(r'```java\n(.*?)\n```', text, flags=re.S)
    if blocks:
        if len(blocks) != 1 or text.count('```') != 2:
            raise ValueError('Ambiguous source blocks')
        text = blocks[0].strip()
    if not text.startswith('package example;') or len(text.encode('utf-8')) > 16384:
        raise ValueError('Expected bounded Java source, not prose')
    return (text.rstrip()+'\n').encode('utf-8')


def run(wallet):
    key = os.environ.get('DEEPSEEK_API_KEY')
    if not key:
        raise ValueError('DEEPSEEK_API_KEY missing')
    java = Path(os.environ['JAVA_HOME'])/'bin/java.exe'
    jar = ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    if not java.is_file() or not jar.is_file():
        raise ValueError('Built Windows CLI required')
    token = secrets.token_hex(32)
    server = HTTPServer(('127.0.0.1', 0), base.handler(wallet, key, token))
    directory = DIRECTORY/('task-'+secrets.token_hex(8))
    directory.mkdir()
    (directory/'requirements.txt').write_bytes(REQUIREMENTS)
    config = dict(baseUrl='http://127.0.0.1:'+str(server.server_port), model='deepseek-flash',
        apiKeyEnv='JHARNESS_LIVE_RELAY_TOKEN', protocol='chat-completions', maxSteps=3,
        durationSeconds=180, requestTimeoutSeconds=75, contextTokens=base.INPUT_CAP,
        inputTokenCeiling=base.INPUT_CAP, maxOutputTokens=base.OUTPUT_CAP, maxCost='0.60',
        currency='CNY', inputPerMillion='2', outputPerMillion='8', streaming=False,
        disableThinking=True, policy='read-only', enabledTools=['read_file'])
    (directory/'config.json').write_text(json.dumps(config), encoding='utf-8')
    before = digest(jar.read_bytes())
    env = {k:os.environ[k] for k in ('SystemRoot','WINDIR','TEMP','TMP','JAVA_HOME','PATH') if k in os.environ}
    env['JHARNESS_LIVE_RELAY_TOKEN'] = token
    report = dict(status='INCOMPLETE', grant=base.GRANT, model='deepseek-flash',
        fixtureSha256=digest(REQUIREMENTS), jarSha256=before,
        pricingSource='https://api-docs.deepseek.com/zh-cn/quick_start/pricing/',
        peakCnyPerMillion=dict(input=2, output=8), isolatedFunctionalStatus='NOT_RUN')
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = subprocess.run([str(java), '-jar', str(jar), '--workspace', str(directory),
            '--config', str(directory/'config.json'), 'run',
            'Read requirements.txt exactly once with read_file. Then return only the complete requested Java source, no explanation. Do not execute commands or modify files.',
            '--session', 'fresh-code', '--require-read', 'requirements.txt'],
            env=env, input=b'', stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=200)
        (directory/'cli.log').write_bytes(result.stdout[:1024*1024].replace(key.encode(), b'[REDACTED]').replace(token.encode(), b'[REDACTED]'))
        report['exitCode'] = result.returncode
        if result.returncode:
            raise ValueError('CLI task failed; no automatic retry')
        path = directory/'.jharness/sessions/fresh-code.jsonl'
        facts = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines()][1:]
        source = extract(facts, REQUIREMENTS)
        if any(secret.encode() in source for secret in (key, token)):
            raise ValueError('Reflected credential')
        if digest(jar.read_bytes()) != before or (directory/'requirements.txt').read_bytes() != REQUIREMENTS:
            raise ValueError('Input drift')
        (directory/'generated.txt').write_bytes(source)
        report.update(status='GENERATED_NOT_EXECUTED', sourceSha256=digest(source), factsSha256=digest(path.read_bytes()))
    finally:
        server.shutdown(); server.server_close(); thread.join(10)
        report['wallet'] = wallet.audit()
        (directory/'receipt.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    return dict(directory=directory.relative_to(ROOT).as_posix(), **report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--run', action='store_true')
    group.add_argument('--audit', action='store_true')
    args = parser.parse_args()
    if args.audit and not DIRECTORY.exists():
        print(json.dumps(dict(grant=base.GRANT, status='NOT_STARTED', reservedMicroCny=0)))
        return
    wallet = base.Wallet(DIRECTORY)
    print(json.dumps(run(wallet) if args.run else wallet.audit(), indent=2))


if __name__ == '__main__':
    main()
