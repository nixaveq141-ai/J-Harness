"""Run trusted mixed-load probe with bounded heap and independently audit original facts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,100}', args.run_id):
        parser.error('Require safe fresh run ID')
    directory = ROOT / '.build-cache/mixed-session-cost' / args.run_id
    directory.mkdir(parents=True, exist_ok=False)
    classes = directory / 'classes'
    classes.mkdir()
    source = Path(__file__).with_name('MixedSessionCost.java')
    jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    inputs = [Path(__file__).resolve(), source, jar]
    report = dict(status='INCOMPLETE', runId=args.run_id, paidRequests=0)
    try:
        report['sourcesBefore'] = {p.relative_to(ROOT).as_posix(): sha(p) for p in inputs}
        suffix = '.exe' if os.name == 'nt' else ''
        java = Path(os.environ['JAVA_HOME']) / 'bin'
        commands = [[str(java / ('javac'+suffix)), '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), str(source)],
                    [str(java / ('java'+suffix)), '-Xmx128m', '-cp', os.pathsep.join([str(classes),str(jar)]), 'MixedSessionCost', str(directory / 'probe')]]
        report['commands'] = commands
        for index, command in enumerate(commands):
            with (directory / f'command-{index}.log').open('xb') as log:
                subprocess.run(command, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                               timeout=180, check=True, cwd=ROOT)
        result = json.loads((directory / 'probe/result.json').read_bytes())
        assert result['status'] == 'PASS' and result['verifiedPrefixReads'] == 64
        journal = directory / 'probe/sessions/mixed-cost.jsonl'
        rows = [json.loads(line) for line in journal.read_bytes().splitlines()]
        assert rows[0] == dict(format='jharness-jsonl',version=1,sessionId='mixed-cost')
        assert len(rows) == 2049
        ids = set()
        for index, row in enumerate(rows[1:]):
            assert row['sessionId'] == 'mixed-cost' and row['type'] == 'user.message'
            assert row['payload'] == dict(role='user',content=str(index)+':'+'mixed durable fact '*8)
            assert row['eventId'] not in ids
            ids.add(row['eventId'])
        report['probe'] = result
        report['originalJournalSha256'] = sha(journal)
        report['independentOriginalFacts'] = len(ids)
        report['classSha256'] = sha(classes / 'MixedSessionCost.class')
        report['logSha256'] = {p.name:sha(p) for p in directory.glob('*.log')}
        report['sourcesAfter'] = {p.relative_to(ROOT).as_posix():sha(p) for p in inputs}
        assert report['sourcesAfter'] == report['sourcesBefore']
        report['status'] = 'PASS'
    finally:
        (directory / 'evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('PASS 2048 real appends / four readers / 64 exact-prefix projections / independent original facts')


if __name__ == '__main__':
    main()
