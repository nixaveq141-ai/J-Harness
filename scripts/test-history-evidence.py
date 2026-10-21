"""Historical collectors must fail closed before reading or rewriting old evidence."""
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
FROZEN = sorted(p for p in (ROOT / 'scripts').glob('collect-m10*-evidence.py')
                if p.name != 'collect-m1059-evidence.py')
FROZEN += [ROOT / 'scripts/verify-m8-closure.py', ROOT / 'docs/testing/tools/verify-m9-evidence.py']


def evidence_fingerprints():
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT / 'docs/testing').rglob('*.json'))}


class HistoricalEvidenceTest(unittest.TestCase):
    def test_all_fifteen_historical_clis_fail_closed_without_modifying_evidence(self):
        self.assertEqual(len(FROZEN), 15)
        before = evidence_fingerprints()
        for path in FROZEN:
            for args in ([], ['--help'], ['--allow-historical-overwrite']):
                with self.subTest(script=path.name, args=args):
                    result = subprocess.run([sys.executable, str(path), *args], cwd=ROOT,
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 2, result.stderr)
                    self.assertEqual(result.stdout, '')
                    self.assertIn('HISTORICAL_EVIDENCE_FROZEN:', result.stderr)
                    expected_kind = 'verifier' if path.name.startswith('verify-') else 'collector'
                    self.assertIn('historical ' + expected_kind, result.stderr)
                    if path.name.startswith('verify-m8'):
                        self.assertIn('M8', result.stderr)
                        self.assertIn('cannot prove M8', result.stderr)
        self.assertEqual(evidence_fingerprints(), before,
                         'Historical evidence JSON content or membership changed')

    def test_import_and_runpy_are_frozen_before_historical_io(self):
        # The child blocks evidence reads and every write before executing historical code.
        # A broken guard can therefore be demonstrated without rewriting old JSON.
        for path in FROZEN:
            for mode in ('import', 'runpy', 'runpy_main'):
                code = r"""
import importlib.util, json, os, pathlib, runpy, sys
path, mode, root = sys.argv[1:]
reads = []
def audit(event, args):
    if event == 'open':
        name, flags = args[0], args[1]
        if isinstance(name, (str, bytes, os.PathLike)):
            value = os.fsdecode(name).replace('\\', '/')
            if isinstance(flags, str) and any(c in flags for c in 'wax+'):
                raise AssertionError('Historical write attempted: ' + value)
            if '/docs/testing/' in value and not value.endswith(('.py', '.pyc')):
                reads.append(value)
                raise AssertionError('Historical evidence read attempted')
    if event in ('os.rename','os.remove','os.mkdir','shutil.copyfile'):
        raise AssertionError('Historical mutation attempted: ' + event)
sys.addaudithook(audit)
try:
    if mode == 'import':
        spec = importlib.util.spec_from_file_location('frozen_probe', path)
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    else:
        runpy.run_path(path, run_name='__main__' if mode == 'runpy_main' else '<run_path>')
except BaseException as error:
    print(json.dumps({'type':type(error).__name__, 'message':str(error), 'reads':reads}))
else:
    print(json.dumps({'type':'NO_ERROR', 'reads':reads}))
"""
                result = subprocess.run([sys.executable, '-B', '-c', code, str(path), mode, str(ROOT)],
                                        cwd=ROOT, capture_output=True, text=True, timeout=10)
                with self.subTest(script=path.name, mode=mode):
                    self.assertEqual(result.returncode, 0, result.stderr)
                    value = __import__('json').loads(result.stdout)
                    expected = 'SystemExit' if mode == 'runpy_main' else 'RuntimeError'
                    self.assertEqual(expected, value['type'], value)
                    if mode != 'runpy_main':
                        self.assertIn('HISTORICAL_EVIDENCE_FROZEN:', value['message'])
                    self.assertEqual([], value['reads'])

    def test_no_current_workflow_invokes_frozen_clis(self):
        for workflow in (ROOT / '.github/workflows').glob('*'):
            if workflow.is_file():
                text = workflow.read_text(encoding='utf-8')
                for path in FROZEN:
                    with self.subTest(workflow=workflow.name, script=path.name):
                        self.assertIsNone(re.search(r'(?<![\w-])' + re.escape(path.name) + r'(?![\w-])', text))


if __name__ == '__main__':
    unittest.main()
