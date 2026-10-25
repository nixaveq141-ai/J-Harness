"""Exit-code receipts tied to one explicit run and a complete build-input snapshot.

These receipts prove local command execution, not a trusted remote attestation.
"""
import argparse
import datetime as dt
import hashlib
import os
import re
from pathlib import Path
import shutil
import signal
import subprocess
import time
from evidence_io import bounded_path, atomic_write_json as atomic_json, stream_sha256

ROOT = Path(__file__).resolve().parents[1]
IS_WINDOWS = os.name == 'nt'
MAX_LOG_BYTES = 128 * 1024 * 1024  # monitored every 250ms; not an OS disk quota
FOLDERS = ('frontend/src', 'frontend/tests', 'frontend/scripts', 'frontend/public', 'sdk', 'scripts',
           '.github/workflows', '.mvn', 'third-party')
FILES = ('.gitignore', 'jharness', 'jharness.cmd', 'pom.xml', 'mvnw', 'mvnw.cmd', 'frontend/package.json',
         'frontend/package-lock.json', 'frontend/tsconfig.json', 'frontend/vite.config.ts',
         'frontend/index.html', 'frontend/upstream.json', 'frontend/LICENSE-DeepSeek-Harness.txt', 'LICENSE', 'NOTICE', 'web-ui.cmd',
         'docs/examples/m1059-distribution.md', '.gitattributes')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def valid_run_id(value):
    return isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', value) is not None


def inputs(root=ROOT):
    paths = {root / p for p in FILES if (root / p).is_file()}
    paths.update(p for p in (root / 'frontend').glob('*.json') if p.is_file())
    for folder in FOLDERS:
        paths.update(p for p in (root / folder).rglob('*') if p.is_file()
                     and '__pycache__' not in p.parts and 'node_modules' not in p.parts)
    for module in root.glob('harness-*'):
        if (module / 'pom.xml').is_file():
            paths.add(module / 'pom.xml')
        paths.update(p for p in (module / 'src').rglob('*') if p.is_file())
    if not paths:
        raise ValueError('Empty build input snapshot')
    for p in paths:
        bounded_path(root, p.relative_to(root).as_posix())
    return {p.relative_to(root).as_posix(): digest(p.read_bytes()) for p in sorted(paths)}


def terminate_and_reap(process, report):
    """A vanished group is already stopped; always attempt direct-child reaping."""
    errors = []
    try:
        if IS_WINDOWS:
            tree = subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                  capture_output=True, timeout=15, check=False)
            report['treeCleanupExitCode'] = tree.returncode
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
    except Exception as error:
        errors.append(type(error).__name__)
    finally:
        try:
            if IS_WINDOWS and process.poll() is None:
                process.kill()
            process.wait(timeout=15)
        except Exception as error:
            errors.append(type(error).__name__)
    if errors:
        report['cleanupFailures'] = errors


def run(command, *, name, log, receipt, run_id, cwd=ROOT, root=ROOT, env=None, timeout=1800, outputs=(), max_log_bytes=MAX_LOG_BYTES):
    if not valid_run_id(run_id):
        raise ValueError('JHARNESS_EVIDENCE_RUN_ID must be 1-128 safe letters, digits, dots, underscores or hyphens')
    report = dict(schemaVersion=1, status='INCOMPLETE', runId=run_id, name=name,
                  command=command, startedAt=dt.datetime.now(dt.timezone.utc).isoformat(),
                  exitCode=None, cwd=cwd.resolve().relative_to(root.resolve()).as_posix(), logByteBudget=max_log_bytes)
    bounded_path(root, receipt.relative_to(root).as_posix(), must_exist=False)
    atomic_json(receipt, report)  # Supersede a stale success even if launch fails.
    process = None
    try:
        bounded_path(root, log.relative_to(root).as_posix(), must_exist=False)
        bounded_path(root, receipt.relative_to(root).as_posix(), must_exist=False)
        report['sourceSha256'] = inputs(root)
        for output in outputs:
            bounded_path(root, output.relative_to(root).as_posix(), must_exist=False)
            output.unlink(missing_ok=True)
        log.parent.mkdir(parents=True, exist_ok=True)
        executable = shutil.which(command[0]) or command[0]
        if type(max_log_bytes) is not int or max_log_bytes <= 0:
            raise ValueError('Invalid command log byte budget')
        with log.open('wb') as stream:
            process = subprocess.Popen([executable, *command[1:]], cwd=cwd, env=env,
                                       stdout=stream, stderr=subprocess.STDOUT,
                                       start_new_session=not IS_WINDOWS)
            deadline = time.monotonic() + timeout
            while True:
                try:
                    report['exitCode'] = process.wait(timeout=max(.001, min(.25, deadline - time.monotonic())))
                    break
                except subprocess.TimeoutExpired:
                    if log.stat().st_size > max_log_bytes:
                        report['logBudgetExceeded'] = True
                        terminate_and_reap(process, report)
                        raise ValueError('Evidence command log exceeds byte budget: ' + name)
                    if time.monotonic() >= deadline:
                        report['timedOut'] = True
                        terminate_and_reap(process, report)
                        raise ValueError('Evidence command timed out: ' + name)
        if log.stat().st_size > max_log_bytes:
            report['logBudgetExceeded'] = True
            raise ValueError('Evidence command log exceeds byte budget: ' + name)
        report['logSha256'] = stream_sha256(log, max_bytes=max_log_bytes)
        report['sourceAfterSha256'] = inputs(root)
        if report['exitCode'] != 0:
            raise ValueError('Evidence command failed: ' + name)
        if report['sourceSha256'] != report['sourceAfterSha256']:
            raise ValueError('Build inputs changed during command: ' + name)
        report['outputs'] = {p.relative_to(root).as_posix(): stream_sha256(p) for p in outputs}
        report['status'] = 'PASS'
        return report
    finally:
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        atomic_json(receipt, report)


def validate(report, *, name, log, run_id, source, command=None, outputs=None, cwd=None):
    if (type(report.get('schemaVersion')) is not int or report.get('schemaVersion') != 1 or report.get('status') != 'PASS'
            or type(report.get('exitCode')) is not int or report['exitCode'] != 0
            or report.get('runId') != run_id or not valid_run_id(run_id)
            or report.get('name') != name):
        raise ValueError('Missing successful same-run command receipt: ' + name)
    if command is not None and report.get('command') != command:
        raise ValueError('Wrong command receipt: ' + name)
    if cwd is not None and report.get('cwd') != cwd:
        raise ValueError('Wrong command working directory: ' + name)
    if not source or report.get('sourceSha256') != source or report.get('sourceAfterSha256') != source:
        raise ValueError('Command source drift: ' + name)
    if report.get('logSha256') != stream_sha256(log, max_bytes=MAX_LOG_BYTES):
        raise ValueError('Command log drift: ' + name)
    if outputs is not None and report.get('outputs') != outputs:
        raise ValueError('Command output drift: ' + name)
    try:
        start, end = (dt.datetime.fromisoformat(report[k]) for k in ('startedAt', 'finishedAt'))
        if start.utcoffset() is None or end.utcoffset() is None or end < start:
            raise ValueError()
    except (KeyError, TypeError, ValueError):
        raise ValueError('Invalid command time: ' + name) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--log', required=True, type=Path)
    parser.add_argument('--cwd', type=Path, default=ROOT)
    parser.add_argument('--timeout', type=int, default=1800)
    parser.add_argument('--output', action='append', type=Path, default=[])
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command or not args.name.replace('-', '').isalnum():
        parser.error('Require a command and an alphanumeric receipt name')
    run(command, name=args.name, log=args.log.resolve(),
        receipt=ROOT / '.build-cache/M10.5.9/commands' / (args.name + '.json'),
        run_id=os.environ.get('JHARNESS_EVIDENCE_RUN_ID'), cwd=args.cwd.resolve(), timeout=args.timeout,
        outputs=tuple(p.resolve() for p in args.output))


if __name__ == '__main__':
    main()
