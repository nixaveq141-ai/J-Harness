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
                    self.assertIn('collect-m1059-evidence.py', result.stderr)
                    self.assertIn('archive-m1059-local.py', result.stderr)
        self.assertEqual(evidence_fingerprints(), before,
                         'Historical evidence JSON content or membership changed')

    def test_no_current_workflow_invokes_frozen_clis(self):
        for workflow in (ROOT / '.github/workflows').glob('*'):
            if workflow.is_file():
                text = workflow.read_text(encoding='utf-8')
                for path in FROZEN:
                    with self.subTest(workflow=workflow.name, script=path.name):
                        self.assertIsNone(re.search(r'(?<![\w-])' + re.escape(path.name) + r'(?![\w-])', text))


if __name__ == '__main__':
    unittest.main()
