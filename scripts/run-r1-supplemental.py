"""Fresh local R1 native and Docker probes; fixed commands and semantic verification."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from evidence_io import atomic_write_json, bounded_path, read_json_snapshot, stream_sha256
import evidence_run

ROOT = Path(__file__).resolve().parents[1]
JAR = 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
IMAGE = 'eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef'
DOCKER = 'C:/Users/Lenovo/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe'
GROUPS = ('native', 'windows-compat', 'docker-subagent', 'web-docker')
NATIVE = ('available', 'missing', 'writeRestartRead', 'rotateRestartRead', 'deleteRestartRead', 'deleteMissing', 'cleanup')
COMPAT = ('oldWriteNewRead', 'newRotateOldRead', 'newDeleteOldRead', 'cleanup')


def require(value, message):
    if not value:
        raise ValueError(message)


def artifacts():
    paths = [ROOT / JAR]
    for folder in ('harness-cli/target/test-classes', 'harness-testkit/target/test-classes', 'harness-testkit/target/classes'):
        classes = sorted((ROOT / folder).rglob('*.class'))
        require(classes, 'Missing compiled probe classes: ' + folder)
        paths.extend(classes)
    return {p.relative_to(ROOT).as_posix(): stream_sha256(bounded_path(ROOT, p.relative_to(ROOT).as_posix())) for p in paths}


def runtime():
    require(os.name == 'nt', 'R1 supplemental requires real local Windows; unsupported is not PASS')
    java = Path(os.environ['JAVA_HOME']) / 'bin/java.exe'
    require(java.is_absolute() and java.is_file(), 'Missing explicit Java runtime')
    return {str(p.resolve()): stream_sha256(p) for p in (Path(sys.executable), java, Path(DOCKER))}


def report_path(name, destination):
    if name == 'native':
        return ROOT / '.build-cache/M10.5.4-native-credentials.json'
    if name == 'windows-compat':
        return ROOT / '.build-cache/R1/windows-compat.json'
    return destination / (name + '.json')


def command_for(name, destination):
    if name in ('native', 'windows-compat'):
        script = 'test-native-credentials.py' if name == 'native' else 'test-windows-credential-compat.py'
        return [sys.executable, 'scripts/' + script]
    probe = 'M7SubagentDockerProbe' if name == 'docker-subagent' else 'M7WebDockerProbe'
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe')
    cp = os.pathsep.join([str(ROOT / 'harness-testkit/target/test-classes'), str(ROOT / 'harness-testkit/target/classes'), str(ROOT / JAR)])
    return [java, '-cp', cp, 'io.jharness.testkit.' + probe, DOCKER, IMAGE,
            str(destination / (name + '-workspace')), str(ROOT / JAR), str(report_path(name, destination))]


def semantic_cases(name, report, jar_hash):
    require(isinstance(report, dict), 'Invalid probe report: ' + name)
    if name == 'native':
        checks = report.get('checks')
        require(report.get('passed') is True and report.get('expectationMet') is True
                and report.get('expectedUnavailable') is False and type(report.get('exitCode')) is int
                and report['exitCode'] == 0 and report.get('credentialStore') == 'Windows Credential Manager'
                and report.get('failureCode') == '' and report.get('cleanupFailureCode') == ''
                and report.get('jarSha256') == jar_hash, 'Native probe did not succeed')
        require(isinstance(checks, dict) and set(checks) == set(NATIVE)
                and all(v is True for v in checks.values()), 'Native checks missing or false')
        return {'r1-native#' + key for key in NATIVE}
    if name == 'windows-compat':
        checks = report.get('checks')
        require(report.get('passed') is True and report.get('parentCleanup') is True
                and type(report.get('exitCode')) is int and report['exitCode'] == 0
                and report.get('legacyCommit') == 'fdd326b' and report.get('currentJarSha256') == jar_hash,
                'Compatibility probe did not succeed')
        require(isinstance(checks, dict) and set(checks) == set(COMPAT)
                and all(v is True for v in checks.values()), 'Compatibility checks missing or false')
        return {'r1-windows-compat#' + key for key in (*COMPAT, 'parentCleanup')}
    require(report.get('milestone') == 'M7' and report.get('actualDocker') is True
            and report.get('image') == IMAGE and report.get('result') == 'SUCCESS'
            and report.get('cleanupVerified') is True and report.get('jarSha256') == jar_hash,
            'Docker probe identity or cleanup mismatch')
    if name == 'docker-subagent':
        require(report.get('slice') == 'read-only-subagent', 'Wrong Docker subagent slice')
        for key in ('subagent-local', 'subagent-docker'):
            row = report.get(key)
            require(isinstance(row, dict) and row.get('status') == 'COMPLETED'
                    and type(row.get('parentRequestsReconstructed')) is int and row['parentRequestsReconstructed'] == 4
                    and type(row.get('childRequestsReconstructed')) is int and row['childRequestsReconstructed'] == 2
                    and all(row.get(field) is True for field in ('independentVersionGuards', 'parentEditVerified', 'originAndArtifactVerified')),
                    'Docker subagent semantics missing: ' + key)
        return {'r1-docker-subagent#local', 'r1-docker-subagent#docker', 'r1-docker-subagent#cleanup'}
    require(name == 'web-docker' and report.get('slice') == 'packaged-cli-web-sdk-docker', 'Wrong Docker Web slice')
    cases = report.get('cases')
    require(isinstance(cases, dict) and set(cases) == {'local', 'docker'}, 'Missing Web environments')
    for key, row in cases.items():
        require(isinstance(row, dict) and row.get('status') == 'COMPLETED'
                and row.get('parallelReadOnlyScheduling') is False
                and type(row.get('hostExitCode')) is int and row['hostExitCode'] == 0
                and type(row.get('requestsReconstructedAgainstWire')) is int and row['requestsReconstructedAgainstWire'] == 3
                and all(row.get(field) is True for field in ('actualHttp', 'approvalBeforeWrite', 'submissionDeduplicated', 'environmentClosed')),
                'Docker Web semantics missing: ' + key)
    return {'r1-web-docker#local', 'r1-web-docker#docker', 'r1-web-docker#cleanup'}


def verify(relative, *, run_id=None, source=None):
    snapshot = read_json_snapshot(ROOT, relative)
    report = snapshot.data
    require(isinstance(report, dict) and type(report.get('schemaVersion')) is int
            and report['schemaVersion'] == 1 and report.get('status') == 'LOCAL_SUPPLEMENTAL_PASS'
            and report.get('m11Eligible') is False, 'Incomplete R1 supplemental report')
    identity = report.get('runId')
    require(evidence_run.valid_run_id(identity) and (run_id is None or identity == run_id), 'Supplemental run mismatch')
    current = evidence_run.inputs(ROOT)
    require(report.get('sourceSha256') == current and (source is None or source == current), 'Supplemental source drift')
    require(report.get('artifacts') == artifacts(), 'Supplemental compiled artifact drift')
    require(report.get('runtime') == runtime(), 'Supplemental executable drift')
    destination = snapshot.path.parent
    entries = report.get('commands')
    require(isinstance(entries, list) and len(entries) == len(GROUPS)
            and [e.get('name') for e in entries] == list(GROUPS), 'Supplemental groups missing or duplicated')
    passed = set()
    for entry in entries:
        name = entry['name']
        output = report_path(name, destination)
        expected_paths = dict(receipt=(destination / (name + '.receipt.json')).relative_to(ROOT).as_posix(),
                              log=(destination / (name + '.log')).relative_to(ROOT).as_posix(),
                              report=output.relative_to(ROOT).as_posix())
        require(all(entry.get(k) == v for k, v in expected_paths.items()), 'Supplemental evidence path mismatch')
        raw = read_json_snapshot(ROOT, expected_paths['report'])
        receipt = read_json_snapshot(ROOT, expected_paths['receipt'])
        require(raw.sha256 == entry.get('reportSha256') and receipt.sha256 == entry.get('receiptSha256'), 'Supplemental evidence hash drift')
        evidence_run.validate(receipt.data, name='r1-' + name, log=bounded_path(ROOT, expected_paths['log']),
                              run_id=identity, source=current, command=command_for(name, destination),
                              outputs={expected_paths['report']: raw.sha256}, cwd='.')
        require(entry.get('command') == command_for(name, destination), 'Supplemental command mismatch')
        if name == 'native':
            expected_java = {p: h for p, h in current.items() if p == 'pom.xml' or p.startswith('harness-') and (p.endswith('/pom.xml') or '/src/' in p and p.endswith('.java'))}
            require(raw.data.get('sourceSha256') == expected_java, 'Native Java source drift')
            require(raw.data.get('probeScriptSha256') == current['scripts/test-native-credentials.py'], 'Native probe script drift')
        if name == 'windows-compat':
            import hashlib
            legacy = subprocess.check_output(['git', 'show', 'fdd326b:harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java'], cwd=ROOT)
            require(raw.data.get('legacySourceSha256') == hashlib.sha256(legacy).hexdigest(), 'Compatibility legacy source drift')
        passed.update(semantic_cases(name, raw.data, report['artifacts'][JAR]))
    require(report.get('passingCases') == sorted(passed), 'Supplemental case identities differ from verified semantics')
    return passed


def collect(relative, run_id):
    require(evidence_run.valid_run_id(run_id), 'Explicit safe run ID required')
    destination = bounded_path(ROOT, relative, must_exist=False)
    require(not destination.exists(), 'Supplemental destination must be fresh')
    destination.mkdir(parents=True)
    report = dict(schemaVersion=1, runId=run_id, status='INCOMPLETE', m11Eligible=False,
                  sourceSha256=evidence_run.inputs(ROOT), artifacts=artifacts(), runtime=runtime(), commands=[], passingCases=[])
    output = destination / 'results.json'
    atomic_write_json(output, report)
    try:
        passed = set()
        for name in GROUPS:
            command = command_for(name, destination)
            raw = report_path(name, destination)
            log, receipt = destination / (name + '.log'), destination / (name + '.receipt.json')
            evidence_run.run(command, name='r1-' + name, log=log, receipt=receipt, run_id=run_id,
                             root=ROOT, cwd=ROOT, outputs=(raw,), timeout=600)
            require(artifacts() == report['artifacts'], 'Compiled probe artifact changed during command')
            data = read_json_snapshot(ROOT, raw.relative_to(ROOT).as_posix())
            passed.update(semantic_cases(name, data.data, report['artifacts'][JAR]))
            report['commands'].append(dict(name=name, command=command,
                receipt=receipt.relative_to(ROOT).as_posix(), receiptSha256=stream_sha256(receipt),
                log=log.relative_to(ROOT).as_posix(), report=raw.relative_to(ROOT).as_posix(), reportSha256=data.sha256))
        report['passingCases'] = sorted(passed)
        report['status'] = 'LOCAL_SUPPLEMENTAL_PASS'
    finally:
        atomic_write_json(output, report)
    try:
        verify(output.relative_to(ROOT).as_posix(), run_id=run_id, source=report['sourceSha256'])
    except Exception:
        report['status'] = 'INCOMPLETE'
        atomic_write_json(output, report)
        raise
    print('PASS R1 supplemental:', len(report['passingCases']), 'fixed semantic cases')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id')
    parser.add_argument('--output')
    parser.add_argument('--verify')
    args = parser.parse_args()
    if args.verify:
        passed = verify(args.verify, run_id=args.run_id)
        print('PASS verified R1 supplemental:', len(passed))
    else:
        require(args.output is not None and args.run_id is not None, '--output and --run-id required')
        collect(args.output, args.run_id)


if __name__ == '__main__':
    main()
