"""Compile the trusted local probe and retain source/JAR-bound real-force measurements.

Usage: python docs/testing/tools/run-durable-session-cost.py --run-id <fresh-id>
Requires JAVA_HOME pointing to JDK21 and the currently validated packaged CLI JAR.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
PROBE = ROOT / 'docs/testing/tools/DurableSessionCost.java'
JAR = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def save(path, value):
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,100}', args.run_id):
        parser.error('Require a safe fresh run ID')
    base = ROOT / '.build-cache/durable-session-cost' / args.run_id
    base.mkdir(parents=True, exist_ok=False)
    classes = base / 'classes'
    classes.mkdir()
    report = dict(schemaVersion=1, status='INCOMPLETE', runId=args.run_id, paidRequests=0)
    inputs = [Path(__file__).resolve(), PROBE, JAR]
    result_path = base / 'evidence.json'
    try:
        report['inputsBeforeSha256'] = {p.relative_to(ROOT).as_posix(): sha(p) for p in inputs}
        save(result_path, report)
        java_home = Path(os.environ['JAVA_HOME'])
        suffix = '.exe' if os.name == 'nt' else ''
        commands = [
            [str(java_home / ('bin/javac' + suffix)), '-encoding', 'UTF-8', '-cp', str(JAR),
             '-d', str(classes), str(PROBE)],
            [str(java_home / ('bin/java' + suffix)), '-Xmx128m', '-cp',
             os.pathsep.join([str(classes), str(JAR)]), 'DurableSessionCost', str(base / 'probe')]]
        report['commands'] = commands
        report['exitCodes'] = []
        for index, command in enumerate(commands):
            log = base / ('compile.log' if index == 0 else 'probe.log')
            with log.open('xb') as output:
                process = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL,
                                         stdout=output, stderr=subprocess.STDOUT, timeout=180)
            report['exitCodes'].append(process.returncode)
            if process.returncode:
                raise ValueError('Compile/probe command failed; preserve original log')
        probe = json.loads((base / 'probe/result.json').read_bytes())
        if (probe['status'] != 'PASS' or probe['realAppendForceCalls'] != 10000
                or probe['sessionCount'] != 2 or probe['eventsPerSession'] != 5000
                or probe['concurrentReaders'] != 4 or probe['concurrentFullReads'] != 16
                or probe['maxHeapBytes'] > 160 * 1024 * 1024):
            raise ValueError('Incomplete durable-cost sample')
        ids = set()
        for shard in range(2):
            log = base / 'probe/sessions' / f'durable-cost-{shard}.jsonl'
            if sha(log) != probe['logSha256'][log.name]:
                raise ValueError('Durable journal hash changed')
            rows = [json.loads(line) for line in log.read_bytes().splitlines()]
            if rows[0] != dict(format='jharness-jsonl', version=1,
                               sessionId=f'durable-cost-{shard}'):
                raise ValueError('Original journal header mismatch')
            facts = rows[1:]
            if len(facts) != 5000 or any(r.get('type') != 'user.message' for r in facts):
                raise ValueError('Original journal event count mismatch')
            for index, fact in enumerate(facts):
                expected = str(shard * 5000 + index) + ':' + 'durable fact ' * 10
                if (fact['payload'] != dict(role='user', content=expected)
                        or fact['eventId'] in ids or fact['sessionId'] != f'durable-cost-{shard}'):
                    raise ValueError('Original journal body/order/identity mismatch')
                ids.add(fact['eventId'])
        if len(ids) != 10000:
            raise ValueError('Missing original event identities')
        report['probe'] = probe
        report['originalJournalFactsVerified'] = len(ids)
        report['classSha256'] = sha(classes / 'DurableSessionCost.class')
        report['logSha256'] = {p.name: sha(p) for p in [base / 'compile.log', base / 'probe.log']}
        report['inputsAfterSha256'] = {p.relative_to(ROOT).as_posix(): sha(p) for p in inputs}
        if report['inputsBeforeSha256'] != report['inputsAfterSha256']:
            raise ValueError('Probe source or runtime JAR changed')
        report['status'] = 'PASS'
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        save(result_path, report)
    print('PASS 10000 real append forces / 2 sessions / 16 concurrent full reads; source/JAR unchanged')


if __name__ == '__main__':
    main()
