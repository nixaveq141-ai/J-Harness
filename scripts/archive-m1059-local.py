"""Publish fresh local evidence snapshots without rewriting historical JSON."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import re
import uuid
from evidence_io import bounded_path, publish_archive, read_json_snapshot, require_sha256

ROOT = Path(__file__).resolve().parents[1]
ENGINES = {'chromium', 'firefox', 'webkit'}
FIXTURES = {'browser', 'diagnosis', 'history', 'turns', 'models', 'questions',
            'content', 'extensions', 'search', 'settings', 'development'}


def archive(root, destination, *, report_path='.build-cache/M10.5.9/runner.json'):
    root = Path(root).resolve(strict=True)
    destination = bounded_path(root, destination, must_exist=False)
    runner = read_json_snapshot(root, report_path)
    report = runner.data
    if (not isinstance(report, dict) or report.get('milestone') != 'M10.5.9' or
            report.get('status') not in {'LOCAL_PARTIAL_CI_PENDING', 'LOCAL_PASS_CI_PENDING'} or
            report.get('m11Eligible') is not False or
            type(report.get('paidRequests')) is not int or report['paidRequests'] != 0):
        raise ValueError('Not validated local evidence')
    run_id = report.get('runId')
    if not isinstance(run_id, str) or not re.fullmatch('[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', run_id):
        raise ValueError('Missing or invalid archive run identity')
    jar = require_sha256(report.get('jarSha256'))
    inputs = report.get('archiveInputs')
    if not isinstance(inputs, dict) or not inputs:
        raise ValueError('Missing archive input fingerprints')
    required = {'.build-cache/M10.5.8-sdk-results.json', '.build-cache/M10.5.8-examples-results.json',
                '.build-cache/M10.5.4-native-credentials.json', '.build-cache/M10.5.9/browser-runtime.json'}
    required |= {'target/' + name + '.json' for name in
                 ('frontend-audit', 'osv-results', 'sdk-source-inventory', 'candidate-smoke', 'release-evidence')}
    if not required.issubset(inputs):
        raise ValueError('Archive input fingerprints omit required evidence')
    matrix = report.get('browserMatrix')
    if not isinstance(matrix, dict) or matrix.get('status') != 'PASS':
        raise ValueError('Invalid browser matrix')
    records = matrix.get('records')
    if not isinstance(records, list) or not records:
        raise ValueError('Empty browser archive records')
    engines, fixtures = matrix.get('engines'), matrix.get('fixtures')
    if (not isinstance(engines, list) or not engines or not all(isinstance(x, str) for x in engines) or
            len(engines) != len(set(engines)) or not {'chromium', 'webkit'}.issubset(engines) or
            not set(engines).issubset(ENGINES) or not isinstance(fixtures, list) or
            not all(isinstance(x, str) for x in fixtures) or len(fixtures) != len(set(fixtures)) or
            set(fixtures) != FIXTURES):
        raise ValueError('Incomplete browser matrix identity')
    sources, names, seen_cases = dict(inputs), {}, set()
    for record in records:
        if not isinstance(record, dict):
            raise ValueError('Invalid browser archive record')
        engine, fixture = record.get('engine'), record.get('fixture')
        if engine not in ENGINES or fixture not in FIXTURES:
            raise ValueError('Invalid browser archive identity')
        key = (engine, fixture)
        if key in seen_cases:
            raise ValueError('Duplicate browser archive identity')
        seen_cases.add(key)
        path, digest = record.get('path'), require_sha256(record.get('sha256'))
        if not isinstance(path, str):
            raise ValueError('Invalid browser source path')
        if path in sources and sources[path] != digest:
            raise ValueError('Conflicting archive source fingerprint')
        if path in names:
            raise ValueError('Duplicate browser source path')
        sources[path] = digest
        names[path] = engine + '-' + fixture + '.json'
    if seen_cases != {(engine, fixture) for engine in engines for fixture in FIXTURES}:
        raise ValueError('Incomplete browser matrix records')
    files, archived = [], {}
    for index, (path, expected) in enumerate(sorted(sources.items())):
        if not isinstance(path, str) or not path.endswith('.json'):
            raise ValueError('Archive source must be a JSON path')
        snapshot = read_json_snapshot(root, path)
        if snapshot.sha256 != require_sha256(expected):
            raise ValueError('Archive source fingerprint changed: ' + path)
        data = snapshot.data
        if not isinstance(data, dict) or not data:
            raise ValueError('Archive source must be a nonempty JSON object: ' + path)
        if 'runId' in data and data['runId'] != run_id:
            raise ValueError('Archive source run identity mismatch: ' + path)
        if 'jarSha256' in data and data['jarSha256'] != jar:
            raise ValueError('Archive source JAR mismatch: ' + path)
        if path in names and (data.get('browserEngine') != next(
                r['engine'] for r in records if r['path'] == path) or data.get('jarSha256') != jar):
            raise ValueError('Browser source identity mismatch: ' + path)
        name = names.get(path, 'input-' + str(index).zfill(3) + '.json')
        files.append((name, snapshot.raw))
        archived[name] = {'source': path, 'sha256': snapshot.sha256}
    files.append(('runner.json', runner.raw))
    archived['runner.json'] = {'source': report_path, 'sha256': runner.sha256}
    manifest = dict(schemaVersion=1, milestone='M10.5.9', runId=run_id,
                    status=report['status'], m11Eligible=False, paidRequests=0,
                    archivedAt=datetime.now(timezone.utc).isoformat(),
                    remoteCiStatus='NOT_AUTHORIZED_NOT_RUN', archivedRecords=archived,
                    scope='Input snapshots only; archive does not rerun or certify tests')
    publish_archive(destination, files, manifest)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', default='docs/testing/M10.5.9-archives/' + str(uuid.uuid4()),
                        help='Fresh repository-relative directory; existing archives are rejected')
    args = parser.parse_args()
    print(archive(ROOT, args.destination))


if __name__ == '__main__':
    main()
