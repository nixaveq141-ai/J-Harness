"""Explicit real-container follow-ups. Never runs in ordinary Maven tests or pulls images."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import inputs, run, valid_run_id

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef'
PROBES = ('M7DockerProbe', 'M7SkillsDockerProbe', 'M7SubagentDockerProbe', 'M7WebDockerProbe',
          'M8FilterDockerProbe', 'M8SchedulingDockerProbe', 'M8ResilienceDockerProbe')


def validate_report(report, jar):
    if (not isinstance(report, dict) or report.get('result') != 'SUCCESS'
            or report.get('actualDocker') is not True or report.get('cleanupVerified') is not True
            or report.get('jarUnchanged') is not True or report.get('jarSha256') != jar
            or report.get('image') != IMAGE):
        raise ValueError('Incomplete same-JAR real-container report')


def run_probe(command, *, input_files, **kwargs):
    """Inputs must survive: evidence_run deliberately deletes generated outputs before launch."""
    root = kwargs.get('root', ROOT)
    before = {p.relative_to(root).as_posix(): stream_sha256(p) for p in input_files}
    if set(input_files).intersection(kwargs.get('outputs', ())):
        raise ValueError('Input artifact cannot be declared as a generated output')
    receipt = run(command, **kwargs)
    after = {p.relative_to(root).as_posix(): stream_sha256(p) for p in input_files}
    receipt['inputArtifactsSha256'] = before
    receipt['inputArtifactsAfterSha256'] = after
    if before != after:
        receipt['status'] = 'INPUT_ARTIFACT_DRIFT'
    atomic_write_json(kwargs['receipt'], receipt)
    if before != after:
        raise ValueError('Input artifact changed during probe')
    return receipt


def main():
    run_id = os.environ.get('JHARNESS_EVIDENCE_RUN_ID')
    if not valid_run_id(run_id):
        raise ValueError('Require an explicit evidence run identity')
    output = ROOT / '.build-cache/gap-docker' / run_id
    output.mkdir(parents=True, exist_ok=False)
    report = dict(status='INCOMPLETE', runId=run_id, paidModelRequests=0, image=IMAGE, probes=[])
    atomic_write_json(output / 'runner.json', report)
    try:
        docker, java = shutil.which('docker'), shutil.which('java')
        if not docker or not java:
            raise ValueError('Docker and Java are required')
        # Caller must deliberately provision this pinned image. Execution itself never pulls.
        inspected = subprocess.run([docker, 'image', 'inspect', IMAGE, '--format', '{{json .RepoDigests}}'],
                                   capture_output=True, check=True, timeout=20)
        digests = json.loads(inspected.stdout)
        if not isinstance(digests, list) or not any(
                isinstance(d, str) and d.endswith('@' + IMAGE.split('@')[1]) for d in digests):
            raise ValueError('Pinned image digest was not verified')
        report['imageRepoDigests'] = digests
        jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        report['jarSha256'] = stream_sha256(jar)
        report['sourceSha256'] = inputs(ROOT)
        classpath = os.pathsep.join(str(p) for p in (jar,
                ROOT / 'harness-testkit/target/test-classes', ROOT / 'harness-testkit/target/classes'))
        # Do not forward unrelated provider credentials into either the probes or their local CLI.
        env = {k: v for k, v in os.environ.items() if k.upper() in {
            'PATH', 'HOME', 'USER', 'TMP', 'TEMP', 'TMPDIR', 'LANG', 'JAVA_HOME',
            'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT', 'JHARNESS_EVIDENCE_RUN_ID'}}
        for name in PROBES:
            result = output / (name + '.json')
            probe_class = ROOT / 'harness-testkit/target/test-classes/io/jharness/testkit' / (name + '.class')
            receipt = run_probe([java, '-cp', classpath, 'io.jharness.testkit.' + name, docker, IMAGE,
                           str(output / 'fixtures'), str(jar), str(result)],
                          name=name, log=output / (name + '.log'), receipt=output / (name + '.receipt.json'),
                          run_id=run_id, timeout=480, env=env, outputs=(result,),
                          input_files=(probe_class, jar))
            data = json.loads(result.read_bytes())
            validate_report(data, report['jarSha256'])
            report['probes'].append(dict(name=name, resultSha256=stream_sha256(result),
                    classSha256=stream_sha256(probe_class), receiptSha256=stream_sha256(output / (name + '.receipt.json'))))
            if receipt['sourceAfterSha256'] != report['sourceSha256']:
                raise ValueError('Source changed between probes')
            atomic_write_json(output / 'runner.json', report)
        if stream_sha256(jar) != report['jarSha256'] or inputs(ROOT) != report['sourceSha256']:
            raise ValueError('Source or JAR changed')
        report['status'] = 'PASS'
    except Exception as failure:
        report['status'] = 'FAILED'
        report['failureType'] = type(failure).__name__
        raise
    finally:
        atomic_write_json(output / 'runner.json', report)


if __name__ == '__main__':
    main()
