"""Record Windows process-start availability; this is never a browser acceptance pass."""
import datetime
import os
from pathlib import Path
import subprocess
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import valid_run_id
from evidence_checks import firefox_executable

ROOT = Path(__file__).resolve().parents[1]


def probe(root=ROOT, *, run_id, executable=None):
    output = root / '.build-cache/M10.5.9/availability.json'
    report = dict(status='INCOMPLETE', runId=run_id,
                  capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  probeScriptSha256=stream_sha256(Path(__file__)))
    atomic_write_json(output, report)
    if not valid_run_id(run_id):
        raise ValueError('Missing safe Firefox probe run identity')
    path = executable.resolve(strict=True) if executable else firefox_executable(root)
    record = dict(executable=str(path), executableSha256=stream_sha256(path),
                  command=[str(path), '--version'], status='NOT_TESTED', exitCode=None)
    report['firefox'] = record
    try:
        result = subprocess.run(record['command'], capture_output=True, text=True, timeout=10)
        record.update(status='VERSION_ONLY' if result.returncode == 0 else 'START_FAILED',
                      exitCode=result.returncode, stdout=result.stdout[:1024], stderr=result.stderr[:1024],
                      processStartFailed=False)
    except OSError as error:
        record.update(status='UNAVAILABLE', winerror=getattr(error, 'winerror', None),
                      failureType=type(error).__name__, processStartFailed=True)
    except subprocess.TimeoutExpired:
        record.update(status='TIMED_OUT', processStartFailed=False)
        raise
    finally:
        # Detect a changed executable instead of signing an old identity onto new bytes.
        report['executableUnchanged'] = stream_sha256(path) == record['executableSha256']
        report['status'] = 'RECORDED' if report['executableUnchanged'] else 'INCOMPLETE'
        atomic_write_json(output, report)
    if not report['executableUnchanged']:
        raise ValueError('Firefox executable changed during probe')
    return report


if __name__ == '__main__':
    value = probe(run_id=os.environ.get('JHARNESS_EVIDENCE_RUN_ID'))
    print(value['firefox']['status'], value['firefox'].get('winerror'))
