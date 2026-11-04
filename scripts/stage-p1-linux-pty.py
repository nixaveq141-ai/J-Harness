"""Load only the five independently reviewed P1 component outputs for assembly."""
import hashlib
import importlib.util
import json
from pathlib import Path
from evidence_io import stream_sha256

ROOT = Path(__file__).resolve().parents[1]
RECEIPT = 'docs/testing/gap-p1-2026-11-04/components-acceptance.json'
RUN = '41823433458'
COMMIT = '1c4cc6da5fd62d6f23c0459036cd9b560403b8b7'
TARGETS = {'x86', 'arm', 'mips64el', 'ppc64le', 'riscv64'}


def load_components(directory, acceptance=None):
    accepted = acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (accepted['status'] != 'FIVE_PTY_SOURCE_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or accepted['runId'] != RUN or accepted['commit'] != COMMIT or accepted['paidRequests'] != 0
            or len(accepted['platforms']) != 5 or {r['target'] for r in accepted['platforms']} != TARGETS):
        raise ValueError('Exact five-component acceptance required')
    spec = importlib.util.spec_from_file_location('p1builder', ROOT/'scripts/build-p1-linux-pty.py')
    builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(builder)
    result = {}
    for item in accepted['platforms']:
        target = item['target']; base = directory / ('p1-linux-pty-'+target)
        for name in ['report.json', 'libpty.so', 'original.so', 'pty-probe']:
            if (base/name).is_symlink() or not (base/name).is_file():
                raise ValueError('Regular accepted component files required')
        if stream_sha256(base/'report.json') != item['reportSha256']:
            raise ValueError('Accepted component report drift')
        report = json.loads((base/'report.json').read_bytes())
        if (report['status'] != 'SOURCE_PTY_COMPONENT_PROBE_PASS_NOT_PRODUCT_ACCEPTANCE'
                or report['runId'] != RUN or report['commit'] != COMMIT or report['target'] != target
                or report['sourceRevision'] != builder.REVISION or report['productAccepted'] is not False):
            raise ValueError('Wrong source component identity')
        output = base/'libpty.so'
        if stream_sha256(output) != item['outputSha256'] or report['outputSha256'] != item['outputSha256']:
            raise ValueError('Accepted component bytes changed')
        if (stream_sha256(base/'original.so') != item['originalSha256']
                or stream_sha256(base/'pty-probe') != item['probeSha256']
                or report['probeSourceSha256'] != item['probeSourceSha256']):
            raise ValueError('Original/probe identity changed')
        if builder.require_identity(output.read_bytes(), target) != item['elf']:
            raise ValueError('Accepted ELF identity changed')
        seen = set(); source = None
        for command in report['commands']:
            name = command['name']
            if name in seen or '/' in name or '\\' in name: raise ValueError('Unique command names required')
            seen.add(name); path = base/(name+'-command.json')
            if stream_sha256(path) != command['sha256']: raise ValueError('Command receipt drift')
            record = json.loads(path.read_bytes())
            if (record['status'] != 'PASS' or record['exitCode'] != 0 or record['name'] != name
                    or record['runId'] != 'p1-pty-'+RUN+'-'+target
                    or record['sourceSha256'] != record['sourceAfterSha256']
                    or stream_sha256(base/(name+'.log')) != record['logSha256']):
                raise ValueError('Failed or changed component command')
            if source is None: source = record['sourceSha256']
            elif source != record['sourceSha256']: raise ValueError('Mixed component command inputs')
        expected = {'host-packages', 'compiler', 'emulator', 'clone', 'checkout', 'build',
                    'elf-audit', 'probe-build', 'candidate-probe', 'missing-probe', 'source-diff'}
        if target != 'mips64el': expected.add('original-probe')
        if seen != expected: raise ValueError('Complete command set required')
        resource = 'resources/com/pty4j/native/linux/'+target+'/libpty.so'
        if report['resource'] != resource or item['resource'] != resource:
            raise ValueError('Exact component resource scope required')
        result[resource] = dict(path=output, sha256=item['outputSha256'], origin=dict(
            sourceBuildRun=RUN, sourceBuildCommit=COMMIT, sourceRevision=builder.REVISION,
            componentProbeRun=RUN, componentReportSha256=item['reportSha256'],
            acceptanceReceipt=RECEIPT, scope='QEMU native component ABI/interaction; target Java product not certified'))
    return result
