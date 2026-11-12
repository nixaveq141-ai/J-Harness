"""Build a Solaris-target C compiler from fixed sources; not native acceptance."""
import hashlib
import os
from pathlib import Path, PurePosixPath
import platform
import posixpath
import re
import shutil
import struct
import subprocess
import tarfile
import urllib.request

from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'sparc-sun-solaris2.11'
INPUTS = {
    'gcc-13.4.0.tar.xz': (
        'https://gcc.gnu.org/pub/gcc/releases/gcc-13.4.0/gcc-13.4.0.tar.xz',
        'sha512', '9b4b83ecf51ef355b868608b8d257b2fa435c06d2719cb86657a7c2c2a0828ff4ce04e9bac1055bbcad8ed5b4da524cafaef654785e23a50233d95d89201e35f',
        100 * 1024**2),
    'binutils-2.40.tar.xz': (
        'https://ftp.gnu.org/gnu/binutils/binutils-2.40.tar.xz',
        'sha256', '0f8a4c272d7f17f369ded10a4aca28b8e304828e95526da482b0ccc4dfc9d8e1',
        30 * 1024**2),
    'illumos-sysroot-sparc-20260117-31d3d510d0-v1.tar.gz': (
        'https://dlc.openindiana.aurora-opencloud.org/SPARC/illumos-sysroot-sparc-20260117-31d3d510d0-v1.tar.gz',
        'sha256', '753ca4b2e24a2a2c3d3ac5de3a590c6b1a106f8fe5cbd89f78f70f80431cb027',
        20 * 1024**2),
}


def fetch(name, destination):
    url, algorithm, expected, limit = INPUTS[name]
    digest = hashlib.new(algorithm)
    total = 0
    with urllib.request.urlopen(url, timeout=45) as response, destination.open('xb') as out:
        while chunk := response.read(65536):
            total += len(chunk)
            if total > limit:
                raise ValueError('Source archive exceeds bound')
            digest.update(chunk)
            out.write(chunk)
    if digest.hexdigest() != expected:
        raise ValueError('Fixed source archive digest mismatch')
    return dict(url=url, bytes=total, algorithm=algorithm, digest=expected,
                sha256=stream_sha256(destination))


def extract_sysroot(archive, destination):
    """Relocate absolute target links inside a fresh owned sysroot, never host /."""
    if destination.exists():
        raise ValueError('Fresh sysroot destination required')
    with tarfile.open(archive) as source:
        members = source.getmembers()
        records = {}
        links = {}
        total = 0
        for member in members:
            parts = PurePosixPath(member.name).parts
            if (not parts or parts[0] != 'sysroot' or '..' in parts
                    or '\\' in member.name or member.name.startswith('/')):
                raise ValueError('Sysroot member path rejected')
            relative = '/'.join(parts[1:])
            if relative in records:
                raise ValueError('Duplicate sysroot member')
            if not (member.isfile() or member.isdir() or member.issym()):
                raise ValueError('Sysroot member type rejected')
            total += member.size
            if member.size > 64 * 1024**2 or total > 512 * 1024**2 or len(records) >= 20000:
                raise ValueError('Sysroot member budget exceeded')
            records[relative] = member
            if member.issym():
                target = posixpath.normpath(member.linkname.lstrip('/') if member.linkname.startswith('/')
                                            else posixpath.join(posixpath.dirname(relative), member.linkname))
                if target == '..' or target.startswith('../') or '\\' in target:
                    raise ValueError('Sysroot link escapes target root')
                links[relative] = posixpath.relpath(target, posixpath.dirname(relative) or '.')
        for relative in records:
            parts = relative.split('/')
            if any('/'.join(parts[:i]) in links for i in range(1, len(parts))):
                raise ValueError('Sysroot symlink ancestor rejected')
        destination.mkdir(parents=True)
        manifest = {}
        for relative, member in records.items():
            path = destination / relative
            if member.isdir():
                path.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                path.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as src, path.open('xb') as dst:
                    shutil.copyfileobj(src, dst)
                path.chmod(0o755 if member.mode & 0o111 else 0o644)
                manifest[relative] = dict(sha256=stream_sha256(path), bytes=member.size)
        for relative, target in links.items():
            path = destination / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.symlink_to(target)
            manifest[relative] = dict(originalLink=records[relative].linkname, relocatedLink=target)
    return manifest


def require_elf(path, bits):
    raw = path.read_bytes()
    if len(raw) < 64 or raw[:6] != b'\x7fELF' + bytes([1 if bits == 32 else 2, 2]):
        raise ValueError('SPARC ELF class/endian mismatch')
    kind, machine = struct.unpack_from('>HH', raw, 16)
    if kind not in {2, 3} or machine not in ({2, 18} if bits == 32 else {43}):
        raise ValueError('SPARC ELF machine mismatch')
    return dict(bits=bits, endian='big', machine=machine, kind=kind, sha256=stream_sha256(path))


def main():
    if platform.system() != 'Linux' or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Dedicated Linux CI required')
    rid = os.environ.get('GITHUB_RUN_ID', '')
    if not re.fullmatch('[0-9]+', rid):
        raise ValueError('Exact CI run identity required')
    out = ROOT / '.build-cache/p1-sparc-cross-toolchain'
    out.mkdir(parents=True, exist_ok=False)
    prefix = out / 'toolchain'
    report = dict(status='INCOMPLETE', target=TARGET, commit=os.environ['GITHUB_SHA'],
                  runId=rid, inputs={}, commands=[], productAccepted=False, paidRequests=0)

    def command(argv, label, cwd=out, timeout=600):
        print('P1_CROSS_COMMAND=' + label, flush=True)
        receipt = out / (label + '-command.json')
        try:
            run(argv, name=label, log=out / (label + '.log'), receipt=receipt,
                run_id='p1-sparc-cross-' + rid, cwd=cwd, root=ROOT, timeout=timeout)
        finally:
            if receipt.is_file():
                report['commands'].append(dict(name=label, sha256=stream_sha256(receipt)))

    try:
        command(['dpkg-query', '-W'], 'host-packages')
        command(['gcc', '--version'], 'host-compiler')
        for name in INPUTS:
            archive = out / name
            report['inputs'][name] = fetch(name, archive)
            if name.startswith('illumos-'):
                manifest = extract_sysroot(archive, prefix / 'sysroot')
                atomic_write_json(out / 'sysroot-inputs.json', manifest)
                report['sysrootManifestSha256'] = stream_sha256(out / 'sysroot-inputs.json')
            else:
                with tarfile.open(archive) as source:
                    source.extractall(out, filter='data')
        sysroot = prefix / 'sysroot'
        jobs = str(min(4, os.cpu_count() or 1))
        common = ['--prefix=' + str(prefix), '--target=' + TARGET, '--with-sysroot=' + str(sysroot), '--disable-nls']
        build = out / 'build-binutils'
        build.mkdir()
        command([str(out / 'binutils-2.40/configure'), *common, '--enable-64-bit-bfd', '--disable-werror', '--disable-gdb', '--disable-gprofng'], 'binutils-configure', build)
        command(['make', '-j' + jobs], 'binutils-build', build, 1200)
        command(['make', 'install'], 'binutils-install', build)
        build = out / 'build-gcc'
        build.mkdir()
        command([str(out / 'gcc-13.4.0/configure'), *common, '--enable-languages=c', '--disable-bootstrap',
                 '--with-gnu-as', '--with-as=' + str(prefix / 'bin' / (TARGET + '-as')),
                 '--with-gnu-ld', '--with-ld=' + str(prefix / 'bin' / (TARGET + '-ld')),
                 '--disable-libsanitizer', '--disable-libquadmath', '--disable-libgomp',
                 '--disable-libssp', '--disable-libvtv', '--without-isl'], 'gcc-configure', build)
        command(['make', '-j' + jobs, 'all-gcc'], 'gcc-build', build, 2400)
        command(['make', 'install-gcc'], 'gcc-install', build)
        command(['make', '-j' + jobs, 'all-target-libgcc'], 'libgcc-build', build, 1200)
        command(['make', 'install-target-libgcc'], 'libgcc-install', build)
        cc = str(prefix / 'bin' / (TARGET + '-gcc'))
        command([cc, '-v'], 'target-compiler')
        smoke = out / 'smoke.c'
        smoke.write_text('#include <stdio.h>\nint main(void){printf("P1_SPARC_CROSS_BITS=%u\\n",(unsigned)(8*sizeof(void*)));return 0;}\n', encoding='ascii')
        report['smokeSourceSha256'] = stream_sha256(smoke)
        report['smokeOutputs'] = {}
        for bits in [32, 64]:
            binary = out / ('smoke-' + str(bits))
            command([cc, '-m' + str(bits), '-O2', '-Wall', '-Wextra', '-Werror', str(smoke), '-o', str(binary)], 'smoke-link-' + str(bits))
            report['smokeOutputs'][str(bits)] = require_elf(binary, bits)
        # Relocation and runtime execution must still be checked by the guest stage.
        command(['tar', '-czf', str(out / 'toolchain.tgz'), '-C', str(out), 'toolchain'], 'archive', timeout=600)
        report.update(status='CROSS_COMPILER_AND_LINKED_SPARC_PROGRAMS_NOT_EXECUTED',
                      archiveSha256=stream_sha256(out / 'toolchain.tgz'))
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        atomic_write_json(out / 'report.json', report)


if __name__ == '__main__':
    main()
