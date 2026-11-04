"""Build five pinned-source PTY components; keep product acceptance separate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT = Path(__file__).resolve().parents[1]
REVISION = 'b7554d649f6374183040e6554942b6d5b028b5e5'
TARGETS = {
    'x86': ('i686-linux-gnu', 'i386', 1, 3),
    'arm': ('arm-linux-gnueabihf', 'arm', 1, 40),
    'mips64el': ('mips64el-linux-gnuabi64', 'mips64el', 2, 8),
    'ppc64le': ('powerpc64le-linux-gnu', 'ppc64le', 2, 21),
    'riscv64': ('riscv64-linux-gnu', 'riscv64', 2, 243),
}


def elf_identity(raw):
    if len(raw) < 64 or raw[:4] != b'\x7fELF' or raw[4] not in (1, 2) or raw[5] != 1:
        raise ValueError('Complete little-endian ELF required')
    kind, machine = struct.unpack_from('<HH', raw, 16)
    flags = struct.unpack_from('<I', raw, 36 if raw[4] == 1 else 48)[0]
    return dict(elfClass=raw[4], machine=machine, kind=kind, flags=flags)


def require_identity(raw, target):
    identity = elf_identity(raw)
    _, _, width, machine = TARGETS[target]
    if (identity['elfClass'], identity['machine'], identity['kind']) != (width, machine, 3):
        raise ValueError('ELF architecture does not match resource target')
    if target == 'arm' and not identity['flags'] & 0x400:
        raise ValueError('ARM hard-float ABI required by existing resource')
    if target == 'ppc64le' and identity['flags'] & 3 != 2:
        raise ValueError('PPC64 ELFv2 ABI required')
    return identity


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target', choices=TARGETS)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or not os.environ.get('GITHUB_RUN_ID', '').isdigit():
        raise ValueError('Dedicated CI required')
    target = args.target
    prefix, emulator, _, _ = TARGETS[target]
    out = ROOT / '.build-cache/p1-linux-pty' / target
    out.mkdir(parents=True, exist_ok=False)
    rid = 'p1-pty-' + os.environ['GITHUB_RUN_ID'] + '-' + target
    report = dict(status='INCOMPLETE', runId=os.environ['GITHUB_RUN_ID'], target=target,
                  commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                  sourceRevision=REVISION, paidRequests=0, productAccepted=False,
                  hostImage={k: os.environ.get(k) for k in ['ImageOS', 'ImageVersion', 'RUNNER_ARCH']}, commands=[])
    def command(argv, name, cwd=ROOT, timeout=300):
        receipt = out / (name + '-command.json')
        try:
            run(argv, name=name, log=out/(name+'.log'), receipt=receipt, run_id=rid, cwd=cwd,
                timeout=timeout)
        finally:
            if receipt.is_file():
                report['commands'].append(dict(name=name, receipt=receipt.relative_to(ROOT).as_posix(),
                                               sha256=stream_sha256(receipt)))
    try:
        command(['dpkg-query', '-W', '-f=${binary:Package}\t${Version}\t${Architecture}\n'], 'host-packages')
        command([prefix+'-gcc', '--version'], 'compiler')
        command(['qemu-'+emulator, '--version'], 'emulator')
        source = out/'pty4j'
        command(['git', '-c', 'http.https://github.com/.extraheader=', 'clone', '--no-checkout',
                 '--filter=blob:none', 'https://github.com/JetBrains/pty4j.git', str(source)], 'clone')
        command(['git', '-c', 'core.autocrlf=false', 'checkout', '--detach', REVISION], 'checkout', source)
        actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source).decode().strip()
        if actual != REVISION: raise ValueError('Pinned source mismatch')
        tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=source).decode().split('\0')
        before = {n: stream_sha256(source/n) for n in tracked if n and (source/n).is_file()}
        report['sourceTrackedFiles'] = before
        relative = 'os/linux/'+target+'/libpty.so'
        library = source/relative
        if not library.is_file(): raise ValueError('Original resource missing from pinned source')
        report['originalSha256'] = stream_sha256(library)
        report['originalElf'] = elf_identity(library.read_bytes())
        inventory = json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())
        resource = 'resources/com/pty4j/native/linux/'+target+'/libpty.so'
        item = next(r for r in inventory['binaries'] if r['resource'] == resource)
        if report['originalSha256'] != item['sha256']: raise ValueError('Pinned source prebuilt differs from original inventory')
        original = out/'original.so'
        original.write_bytes(library.read_bytes())
        library.unlink()  # The make target must not succeed by retaining a prebuilt.
        command(['make', '-C', 'native', '-B', 'linux_x86_64', 'ARCH_FLAG_X86_64=',
                 'ARCH_X86_64='+target, 'CC='+prefix+'-gcc'], 'build', source)
        identity = require_identity(library.read_bytes(), target)
        output = out/'libpty.so'; output.write_bytes(library.read_bytes())
        command([prefix+'-readelf', '-h', '-d', '-V', '-Ws', str(output)], 'elf-audit')
        probe = out/'pty-probe'
        command([prefix+'-gcc', '-std=c11', '-Wall', '-Wextra', '-Werror', '-O2',
                 str(ROOT/'scripts/probes/p1-pty.c'), '-ldl', '-o', str(probe)], 'probe-build')
        qemu = ['qemu-'+emulator, '-L', '/usr/'+prefix, str(probe)]
        present = ['present', '/usr/bin/qemu-'+emulator, '/usr/'+prefix]
        command([*qemu, str(output), *present], 'candidate-probe', timeout=30)
        command([*qemu, str(out/'absent.so'), 'missing'], 'missing-probe', timeout=30)
        # Never execute an incorrectly labelled original on the host architecture.
        try: require_identity(original.read_bytes(), target)
        except ValueError: report['originalControl'] = 'REJECTED_WRONG_ELF_ARCHITECTURE'
        else:
            command([*qemu, str(original), *present], 'original-probe', timeout=30)
            report['originalControl'] = 'SAME_COMPONENT_PROBE_PASS'
        after = {n: stream_sha256(source/n) for n in before if (source/n).is_file()}
        changed = {n for n in before if before[n] != after.get(n)}
        if changed != {relative}: raise ValueError('Unexpected tracked source mutation')
        report['sourceTrackedFilesAfter'] = after
        command(['git', 'diff', '--binary'], 'source-diff', source)
        report.update(status='SOURCE_PTY_COMPONENT_PROBE_PASS_NOT_PRODUCT_ACCEPTANCE',
                      resource=resource, outputSha256=stream_sha256(output), elf=identity,
                      probeSha256=stream_sha256(probe), probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-pty.c'),
                      sourceChangeScope=[relative], limits=['No full Java product validation on this target',
                          'No product replacement yet', 'No complete OS/CPE or arbitrary Linux compatibility clearance'])
    finally:
        atomic_write_json(out/'report.json', report)
    print(report['status'])


if __name__ == '__main__': main()
