"""Frozen paid evaluator: only bounded read-only inspection of historical evidence.

Generated Java formerly ran with host authority. Minimal environments and source
blacklists are not sandboxes. Reactivation requires a reviewed isolation/accounting
design and new authorization. Importing this module never launches a process.
"""
import argparse
import hashlib
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import sys
from evidence_io import bounded_path, read_json_snapshot, stream_sha256, strict_json_loads

ROOT = Path(__file__).resolve().parents[1]
FROZEN = 'Historical live evaluation is disabled; generated code and paid requests will not execute'
MAX_BYTES = 16 * 1024 * 1024
SOURCE = 'src/main/java/example/Calculator.java'
TEST = 'src/test/java/example/CalculatorTest.java'


def oracle(*args, **kwargs):
    raise RuntimeError(FROZEN)


def evaluate(*args, **kwargs):
    raise RuntimeError(FROZEN)


def allowed(*args, **kwargs):
    """Direct imports cannot reactivate the frozen approval/execution path."""
    return False


def required_reads(kind):
    """Historical goal contract, not an execution grant."""
    if kind not in ('calculator', 'broken-project'):
        raise ValueError('Unknown historical fixture')
    return ['pom.xml', SOURCE, TEST] if kind == 'calculator' else ['pom.xml', SOURCE]


def money(value):
    if not isinstance(value, str) or len(value) > 64:
        raise ValueError('Missing cost evidence')
    try:
        number = Decimal(value)
    except InvalidOperation:
        raise ValueError('Invalid cost evidence') from None
    if not number.is_finite() or number < 0:
        raise ValueError('Invalid cost evidence')
    return number


def inspect_cost(log, retained=None):
    """Missing/partial logs never imply zero cost or grant more paid allowance."""
    values = []
    try:
        if retained is not None:
            values.append(money(retained))
        with log.open('rb') as stream:
            raw = stream.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise ValueError('Oversized historical log')
        lines = raw.splitlines()
        if len(lines) < 2 or not raw.endswith(b'\n') or not isinstance(strict_json_loads(lines[0]), dict):
            raise ValueError('Missing or partial historical log')
        budget_count = 0
        for line in lines[1:]:
            event = strict_json_loads(line)
            if not isinstance(event, dict) or not isinstance(event.get('type'), str):
                raise ValueError('Invalid historical event')
            if event['type'] in ('budget.reserved', 'budget.settled'):
                values.append(money(event['payload']['reserved']))
                budget_count += 1
        if not budget_count:
            raise ValueError('Missing budget evidence')
        return dict(status='HISTORICAL_REFERENCE_ONLY', retainedUpperBoundCny=str(max(values)),
                    grantsNewAllowance=False, logSha256=hashlib.sha256(raw).hexdigest())
    except (OSError, ValueError, KeyError, TypeError):
        return dict(status='UNKNOWN', retainedUpperBoundCny=str(max(values)) if values else None,
                    grantsNewAllowance=False)


def inspect_identity(root, run):
    try:
        directory = bounded_path(root, run['directory'])
        base = bounded_path(root, '.build-cache/M4-live')
        if directory == base or not directory.is_relative_to(base) or not directory.is_dir():
            raise ValueError('Not a historical fixture directory')
        expected = run.get('sourceSha256')
        kind = run.get('fixture')
        wanted = {SOURCE, TEST} if kind == 'calculator' else {SOURCE} if kind == 'broken-project' else set()
        if not isinstance(expected, dict) or not wanted or set(expected) != wanted:
            raise ValueError('Missing source snapshot')
        for name, digest in expected.items():
            if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('Invalid source digest')
            if stream_sha256(bounded_path(directory, name), max_bytes=MAX_BYTES) != digest:
                return 'STALE'
        digest = run.get('cliJarSha256')
        if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
            raise ValueError('Missing JAR identity')
        jar = bounded_path(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
        if stream_sha256(jar, max_bytes=256 * 1024 * 1024) != digest:
            return 'STALE'
        # A stored task hash alone cannot authenticate an unrecorded original goal.
        return 'FILE_HASHES_MATCH_REFERENCE_ONLY'
    except (OSError, ValueError, KeyError, TypeError):
        return 'INCOMPLETE'


def audit_historical(root=ROOT):
    snapshot = read_json_snapshot(root, '.build-cache/M4-live/ledger.json')
    data = snapshot.data
    if not isinstance(data, dict) or not isinstance(data.get('runs'), list) or len(data['runs']) > 1000:
        raise ValueError('Invalid historical ledger')
    runs = []
    for index, run in enumerate(data['runs']):
        if not isinstance(run, dict):
            raise ValueError('Invalid historical run')
        kind = run.get('fixture')
        result = dict(index=index, fixture=kind if kind in ('calculator', 'broken-project') else 'UNKNOWN',
                      identity=inspect_identity(root, run), executed=False)
        try:
            directory = bounded_path(root, run['directory'])
            base = bounded_path(root, '.build-cache/M4-live')
            if directory == base or not directory.is_relative_to(base):
                raise ValueError('Historical fixture escaped root')
            log = bounded_path(directory, '.jharness/sessions/evaluation.jsonl', must_exist=False)
            result['cost'] = inspect_cost(log, run.get('costUpperBoundCny'))
        except (OSError, ValueError, KeyError, TypeError):
            result['cost'] = dict(status='UNKNOWN', retainedUpperBoundCny=None, grantsNewAllowance=False)
        if result['fixture'] != 'UNKNOWN':
            result['requiredReads'] = required_reads(kind)
        runs.append(result)
    if read_json_snapshot(root, '.build-cache/M4-live/ledger.json').sha256 != snapshot.sha256:
        raise ValueError('Historical ledger changed during inspection')
    return dict(schemaVersion=1, status='READ_ONLY_HISTORICAL_INSPECTION', executionEnabled=False,
                evidenceAcceptance=False, m11Eligible=False, ledgerSha256=snapshot.sha256, runs=runs)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit-only', action='store_true')
    parser.add_argument('--limit-cny')
    parser.add_argument('--repetitions')
    args = parser.parse_args(argv)
    if not args.audit_only:
        print(FROZEN, file=sys.stderr)
        return 2
    try:
        print(json.dumps(audit_historical(), ensure_ascii=False))
        return 0
    except (OSError, ValueError):
        print('Historical inspection incomplete; original evidence remains unchanged', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
