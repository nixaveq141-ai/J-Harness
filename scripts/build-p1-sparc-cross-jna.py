"""Build both Solaris SPARC JNA components with an independently fixed toolchain."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import tarfile
import zipfile

from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT = Path(__file__).resolve().parents[1]
REVISION = 'c366bc4fd0c50a30ac8d2475603ec564f6611462'
TOOLCHAIN_SHA = '62f1e00ea0b992c5e4b382ac9304dfcf565154b39936c3f6d5fe1402c448008b'
TRIPLE = 'sparc-sun-solaris2.11'


def require_elf(raw, bits, *, shared=False):
    if bits not in {32,64}:
        raise ValueError('Explicit SPARC word size required')
    if len(raw) < 64 or raw[:6] != b'\x7fELF' + bytes([1 if bits == 32 else 2, 2]):
        raise ValueError('SPARC class/endian mismatch')
    kind, machine = struct.unpack_from('>HH', raw, 16)
    if kind not in ({3} if shared else {2, 3}) or machine not in ({2, 18} if bits == 32 else {43}):
        raise ValueError('SPARC object kind/machine mismatch')
    return dict(bits=bits, endian='big', machine=machine, kind=kind)


def require_runtime_dependencies(text):
    needed=re.findall(r'\(NEEDED\).*?\[([^\]]+)\]',text)
    if 'libc.so.1' not in needed:
        raise ValueError('Explicit Solaris libc dynamic dependency required')
    if any(name.startswith('libgcc_s.so') for name in needed):
        raise ValueError('Guest files must not require an uninstalled GCC shared runtime')
    return needed


def main():
    if platform.system() != 'Linux' or os.environ.get('GITHUB_ACTIONS') != 'true' or not os.environ.get('GITHUB_RUN_ID', '').isdigit():
        raise ValueError('Dedicated Linux CI required')
    tool = ROOT / '.build-cache/p1-sparc-cross-toolchain'
    archive = tool / 'toolchain.tgz'
    if stream_sha256(archive) != TOOLCHAIN_SHA or (tool / 'toolchain').exists():
        raise ValueError('Exact fresh independently verified toolchain required')
    with tarfile.open(archive) as source:
        source.extractall(tool, filter='data')
    prefix = tool / 'toolchain'
    cc = prefix / 'bin' / (TRIPLE + '-gcc')
    sysroot = prefix / 'sysroot'
    if subprocess.check_output([str(cc), '-print-sysroot']).decode().strip() != str(sysroot):
        raise ValueError('Compiler sysroot relocation mismatch')
    os.environ['PATH'] = str(prefix / 'bin') + os.pathsep + os.environ['PATH']
    out = ROOT / '.build-cache/p1-sparc-jna-cross'
    out.mkdir(parents=True, exist_ok=False)
    guest = out / 'guest'
    guest.mkdir()
    report = dict(status='INCOMPLETE', runId=os.environ['GITHUB_RUN_ID'], commit=os.environ['GITHUB_SHA'],
                  sourceRevision=REVISION, toolchainArchiveSha256=TOOLCHAIN_SHA,
                  commands=[], targets={}, productAccepted=False, paidRequests=0)
    rid = 'p1-sparc-jna-cross-' + report['runId']

    def command(argv, label, cwd=ROOT, timeout=600):
        print('P1_SPARC_CROSS_JNA=' + label, flush=True)
        receipt = out / (label + '-command.json')
        try:
            run(argv, name=label, log=out / (label + '.log'), receipt=receipt,
                run_id=rid, root=ROOT, cwd=cwd, timeout=timeout)
        finally:
            if receipt.is_file():
                report['commands'].append(dict(name=label, sha256=stream_sha256(receipt)))

    try:
        command(['dpkg-query', '-W'], 'host-packages')
        command([str(cc), '-v'], 'compiler')
        command(['java', '-version'], 'jdk')
        command(['ant', '-version'], 'ant')
        java = Path(os.environ['JAVA_HOME'])
        # JAWT stays enabled. Only the actual X11 headers are made visible;
        # do not expose the host's entire libc include tree to Solaris GCC.
        portable = out / 'portable-headers'
        portable.mkdir()
        shutil.copytree('/usr/include/X11', portable / 'X11', symlinks=True)
        report['x11Headers'] = {p.relative_to(portable).as_posix(): stream_sha256(p)
                                for p in portable.rglob('*') if p.is_file()}
        report['jniHeaders'] = {str(p.relative_to(java)): stream_sha256(p)
                                for p in [java / 'include/jni.h', java / 'include/linux/jni_md.h',
                                          java / 'include/jawt.h', java / 'include/linux/jawt_md.h']}
        report['headerPolicy'] = 'Genuine JDK Unix JNI/JAWT and X11 declarations interpreted by the Solaris target compiler; Solaris libc headers/libraries only from the fixed real sysroot. No NO_JAWT or synthetic target definitions.'
        helper_path = ROOT / 'docs/testing/tools/build-native-from-source.py'
        spec = importlib.util.spec_from_file_location('ffi_inputs', helper_path)
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        report['generatedHelperSha256'] = stream_sha256(helper_path)
        inventory = json.loads((ROOT / 'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries']
        for bits, target, arch in [(32, 'sparc', 'sparc'), (64, 'sparcv9', 'sparcv9')]:
            source = out / ('jna-' + target)
            command(['git', '-c', 'http.https://github.com/.extraheader=', 'clone', '--no-checkout', '--filter=blob:none',
                     'https://github.com/java-native-access/jna.git', str(source)], target + '-clone')
            command(['git', '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'checkout', '--detach', REVISION], target + '-checkout', source)
            if subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source).decode().strip() != REVISION:
                raise ValueError('Fixed JNA revision mismatch')
            names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=source).decode().split('\0')
            before = {n: stream_sha256(source / n) for n in names if n and (source / n).is_file()}
            native_jar = 'lib/native/sunos-' + target + '.jar'
            resource = 'com/sun/jna/sunos-' + target + '/libjnidispatch.so'
            original_item = next(item for item in inventory if item['resource'] == resource)
            with zipfile.ZipFile(source / native_jar) as original_archive:
                original = original_archive.read('libjnidispatch.so')
            if hashlib.sha256(original).hexdigest() != original_item['sha256']:
                raise ValueError('Original inventory bytes differ')
            require_elf(original, 64, shared=True)  # Both originals are actually 64-bit.
            (out / (target + '-original.so')).write_bytes(original)
            ffi = helper.prepare_libffi(source, out)
            makefile = source / 'native/Makefile'
            raw = makefile.read_bytes()
            anchor = b'FFI_CONFIG=--enable-static --disable-shared --with-pic=yes'
            if raw.count(anchor) != 2:
                raise ValueError('Pinned Makefile anchor mismatch')
            makefile.write_bytes(raw.replace(anchor, anchor + b' --disable-docs --disable-maintainer-mode --disable-dependency-tracking'))
            compiler = [str(cc), '-m' + str(bits)]
            makeopts = 'OS=solaris HOST_CONFIG=--host=' + TRIPLE
            makeopts += ' STRIP=' + str(prefix / 'bin' / (TRIPLE + '-strip'))
            makeopts += ' AR=' + str(prefix / 'bin' / (TRIPLE + '-ar'))
            makeopts += ' RANLIB=' + str(prefix / 'bin' / (TRIPLE + '-ranlib'))
            command(['ant', '-Dbuild-native=true', '-Dmake=make', '-Dos.prefix=sunos-' + target, '-DARCH=' + arch,
                     '-DCC=' + ' '.join(compiler), '-Dcflags_extra.native=-I' + str(java / 'include/linux') + ' -I' + str(portable),
                     '-DEXTRA_MAKE_OPTS=' + makeopts, 'native'], target + '-build', source, 900)
            build = source / 'build' / ('native-sunos-' + target)
            library = build / 'libjnidispatch.so'
            raw = library.read_bytes()
            identity = require_elf(raw, bits, shared=True)
            copies = list((source / 'build').rglob('libjnidispatch.so'))
            if library.is_symlink() or any(p.is_symlink() or stream_sha256(p) != hashlib.sha256(raw).hexdigest() for p in copies):
                raise ValueError('Fresh Ant library copies differ')
            output = guest / (str(bits) + '-libjnidispatch.so')
            output.write_bytes(raw)
            command([str(prefix / 'bin' / (TRIPLE + '-readelf')), '-h', '-d', '-V', '-Ws', str(output)], target + '-elf')
            ffi_headers = build / 'libffi/include'
            promotion = guest / (str(bits) + '-callback-control')
            probe = guest / (str(bits) + '-jni-probe')
            command([*compiler, '-std=c11', '-Wall', '-Wextra', '-Werror', '-static-libgcc', '-I' + str(ffi_headers),
                     str(ROOT / 'scripts/probes/p1-jna-callback-control.c'), '-o', str(promotion)], target + '-promotion-build')
            command([*compiler, '-std=c11', '-Wall', '-Wextra', '-Werror', '-static-libgcc', '-O2', '-I' + str(ffi_headers),
                     '-I' + str(source / 'build/headers'), '-I' + str(java / 'include'), '-I' + str(java / 'include/linux'),
                     str(ROOT / 'scripts/probes/p1-jna.c'), '-ldl', '-o', str(probe)], target + '-probe-build')
            smoke=guest/('smoke-'+str(bits))
            command([*compiler,'-std=c11','-Wall','-Wextra','-Werror','-static-libgcc',
                     str(ROOT/'scripts/probes/p1-sparc-width.c'),'-o',str(smoke)],target+'-smoke-build')
            require_elf(promotion.read_bytes(), bits)
            require_elf(probe.read_bytes(), bits)
            require_elf(smoke.read_bytes(),bits)
            dependencies={}
            for binary in [output,promotion,probe,smoke]:
                label=target+'-dependencies-'+binary.name
                command([str(prefix/'bin'/(TRIPLE+'-readelf')),'-d',str(binary)],label)
                dependencies[binary.name]=require_runtime_dependencies((out/(label+'.log')).read_text())
            after = {n: stream_sha256(source / n) for n in before if (source / n).is_file()}
            changed = sorted(n for n in before if before[n] != after.get(n))
            if set(before) != set(after) or set(changed) != {'native/Makefile', native_jar}:
                raise ValueError('Unexpected JNA tracked source mutation')
            command(['git', 'diff', '--binary'], target + '-source-diff', source)
            report['targets'][target] = dict(bits=bits, resource=resource, elf=identity, sourceTrackedFiles=before,
                                           sourceTrackedFilesAfter=after, sourceChangedPaths=changed,
                                           originalSha256=original_item['sha256'], originalLabelMismatch=(bits == 32),
                                           libffiBuildInputs=ffi, outputSha256=stream_sha256(output),runtimeDependencies=dependencies)
        shutil.copyfile(ROOT / 'scripts/probes/p1-sparc-guest-components.py', guest / 'guest-run.py')
        manifest = dict(runId=report['runId'], commit=report['commit'],
                        members={p.name: stream_sha256(p) for p in guest.iterdir()})
        atomic_write_json(guest / 'manifest.json', manifest)
        bundle = out / 'guest-bundle.tgz'
        with tarfile.open(bundle, 'w:gz') as packed:
            for path in sorted(guest.iterdir()):
                packed.add(path, arcname=path.name)
        report.update(status='SOURCE_COMPONENTS_CROSS_BUILT_NOT_EXECUTED', bundleSha256=stream_sha256(bundle),
                      bundleManifest=manifest, probeSourceSha256={n: stream_sha256(ROOT / n) for n in
                      ['scripts/probes/p1-jna.c', 'scripts/probes/p1-jna-callback.h', 'scripts/probes/p1-jna-callback-control.c',
                       'scripts/probes/p1-sparc-width.c']})
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        for target in ['sparc', 'sparcv9']:
            config = out / ('jna-' + target) / 'build' / ('native-sunos-' + target) / 'libffi/config.log'
            if config.is_file():
                shutil.copyfile(config, out / (target + '-libffi-config.log'))
        atomic_write_json(out / 'report.json', report)


if __name__ == '__main__':
    main()
