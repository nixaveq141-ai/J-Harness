"""Run the packaged matrix, keeping same-run exit-code and output receipts."""
import importlib.util
import os
from pathlib import Path
import sys
from evidence_run import run

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.build-cache/M10.5.9'
IS_WINDOWS = os.name == 'nt'


def main():
    run_id = os.environ.get('JHARNESS_EVIDENCE_RUN_ID')
    if not run_id:
        raise ValueError('Set JHARNESS_EVIDENCE_RUN_ID for the entire build and verification run')
    env = dict(os.environ, JHARNESS_PYTHON=sys.executable)
    if IS_WINDOWS:
        run([sys.executable, 'scripts/probe-firefox.py'], name='firefox-probe',
            log=OUT / 'firefox-probe.log', receipt=OUT / 'commands/firefox-probe.json',
            run_id=run_id, env=env, outputs=(OUT / 'availability.json',))
    spec = importlib.util.spec_from_file_location('browser_prepare', ROOT / 'scripts/prepare-test-browsers.py')
    prepare = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(prepare)
    firefox = prepare.prepare()
    if firefox:
        env['JHARNESS_FIREFOX_EXECUTABLE'] = str(firefox)
    commands = [
        ('native', [sys.executable, 'scripts/test-native-credentials.py'], '.build-cache/M10.5.4-native-credentials.json'),
        ('sdk', ['node', 'sdk/tests/run.mjs'], '.build-cache/M10.5.8-sdk-results.json'),
        ('examples', ['node', 'sdk/tests/examples.mjs'], '.build-cache/M10.5.8-examples-results.json'),
        ('browser', ['node', 'scripts/run-workbench-matrix.mjs'], '.build-cache/M10.5.9/browser/matrix.json')]
    if IS_WINDOWS:
        commands.insert(1, ('launcher', [sys.executable, 'scripts/test-open-workbench.py'], '.build-cache/M10-launcher-results.json'))
    for name, command, output in commands:
        run(command, name=name, log=OUT / (name + '.log'), receipt=OUT / 'commands' / (name + '.json'),
            run_id=run_id, env=env, outputs=(ROOT / output,))
        print('PASS', name, flush=True)


if __name__ == '__main__':
    main()
