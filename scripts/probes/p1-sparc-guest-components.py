"""Execute only the exact owned cross-built SPARC component bundle in SunOS."""
import hashlib
import json
from pathlib import Path
import platform
import subprocess


def main():
    if platform.system() != 'SunOS' or platform.machine() not in {'sun4v', 'sun4u', 'sparc'}:
        raise ValueError('Actual Solaris SPARC guest required')
    base = Path(__file__).resolve().parent
    if not str(base).startswith('/var/tmp/jh-p1-') or base.is_symlink():
        raise ValueError('Owned guest bundle root required')
    manifest = json.loads((base / 'manifest.json').read_bytes())
    expected = {'guest-run.py','smoke-32','smoke-64'}
    expected.update(str(bits)+'-'+name for bits in [32,64] for name in ['libjnidispatch.so','jni-probe','callback-control'])
    if set(manifest['members']) != expected:
        raise ValueError('Complete exact component bundle required')
    for name, digest in manifest['members'].items():
        if Path(name).name != name or name in {'.', '..'}:
            raise ValueError('Flat owned input names required')
        path = base / name
        if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Guest bundle input drift')
        if name != 'guest-run.py':
            path.chmod(0o700)
    report = dict(status='INCOMPLETE', runId=manifest['runId'], commit=manifest['commit'],
                  system=platform.uname()._asdict(), commands=[], productAccepted=False, paidRequests=0)

    def execute(argv, marker):
        completed = subprocess.run(argv, capture_output=True, timeout=120)
        report['commands'].append(dict(argv=argv, exitCode=completed.returncode,
                                       stdout=completed.stdout.decode(errors='replace'),
                                       stderr=completed.stderr.decode(errors='replace')))
        if completed.returncode or marker not in completed.stdout:
            raise ValueError('Exact guest component execution failed')

    try:
        for bits in [32, 64]:
            execute([str(base / ('smoke-' + str(bits)))], ('P1_SPARC_CROSS_BITS=' + str(bits)).encode())
            execute([str(base / (str(bits) + '-callback-control'))], b'PASS complete ffi_arg result and adjacent guards')
            execute([str(base / (str(bits) + '-jni-probe')), str(base / (str(bits) + '-libjnidispatch.so')), 'present'], b'ffi call41 and closure46')
            execute([str(base / (str(bits) + '-jni-probe')), str(base / ('absent-' + str(bits) + '.so')), 'missing'], b'PASS missing exact JNI library refused')
        report['status'] = 'BOTH_SPARC_COMPONENTS_NATIVE_PRIMITIVES_FFI_CLOSURE_AND_MISSING_PASS'
    finally:
        print('P1_COMPONENT_RESULTS_JSON=' + json.dumps(report, separators=(',', ':')), flush=True)


if __name__ == '__main__':
    main()
