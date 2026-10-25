"""Offline regressions for fresh R1-R3 evidence; never opens the native vault."""
import importlib.util
import json
import os
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('r123_supplemental', ROOT / 'scripts/run-r1-supplemental.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
spec = importlib.util.spec_from_file_location('r123_coverage', ROOT / 'scripts/verify-r123r-implementation.py')
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)


class R123EvidenceTest(unittest.TestCase):
    def test_coverage_rejects_incomplete_duplicate_or_changed_original(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'source.py').write_text('# concrete fixture')
            original = dict(id='R123R-001', path='source.py', verdict='采纳')
            row = dict(original, status='IMPLEMENTED_LOCAL_VALIDATED', implementation='A concrete bounded fix',
                       files=['source.py'], tests=['actual-case'], limitations=[])
            coverage.validate_rows([row], [original], {'actual-case'}, root)
            for changed in ([], [row, row], [dict(row, verdict='不采纳')], [dict(row, status='IN_PROGRESS')]):
                with self.subTest(changed=changed), self.assertRaises(ValueError):
                    coverage.validate_rows(changed, [original], {'actual-case'}, root)

    def test_coverage_requires_passed_identity_and_real_bounded_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'source.py').write_text('# concrete fixture')
            original = dict(id='R123R-001', path='source.py', verdict='采纳')
            row = dict(original, status='IMPLEMENTED_LOCAL_VALIDATED', implementation='A concrete bounded fix',
                       files=['source.py'], tests=['actual-case'], limitations=[])
            for change in (dict(tests=['invented-pass']), dict(files=['../escape.py']), dict(implementation='')):
                with self.subTest(change=change), self.assertRaises(ValueError):
                    coverage.validate_rows([dict(row, **change)], [original], {'actual-case'}, root)

    def test_rejected_scope_cannot_be_promoted(self):
        original = dict(id='R123R-018', path='source.py', verdict='不采纳')
        coverage.validate_rows([dict(original, status='OUT_OF_SCOPE')], [original], set())
        with self.assertRaises(ValueError):
            coverage.validate_rows([dict(original, status='IMPLEMENTED_LOCAL_VALIDATED')], [original], set())
    def test_native_launch_failure_replaces_stale_pass_before_start(self):
        with tempfile.TemporaryDirectory(prefix='r123-evidence-', dir=ROOT / '.build-cache') as directory:
            output = Path(directory) / 'nested' / 'native.json'
            output.parent.mkdir()
            output.write_text('{"passed":true,"expectationMet":true}', encoding='utf-8')
            with patch.object(sys, 'argv', ['test-native-credentials.py', '--output', output.relative_to(ROOT).as_posix()]), \
                    patch.dict(os.environ, JAVA_HOME=str(Path(directory) / 'missing-jdk')), \
                    patch('subprocess.run', side_effect=OSError('injected launch failure')) as launch:
                with self.assertRaises(OSError):
                    runpy.run_path(str(ROOT / 'scripts/test-native-credentials.py'), run_name='__main__')
            self.assertEqual(1, launch.call_count)
            report = json.loads(output.read_text())
            self.assertEqual('INCOMPLETE', report['status'])
            self.assertIs(False, report['passed'])
            self.assertIs(False, report['expectationMet'])

    def test_native_output_parent_is_created_before_child_launch(self):
        with tempfile.TemporaryDirectory(prefix='r123-evidence-', dir=ROOT / '.build-cache') as directory:
            output = Path(directory) / 'new' / 'nested' / 'native.json'
            def reject_launch(*args, **kwargs):
                self.assertTrue(output.is_file())
                self.assertIs(False, json.loads(output.read_text())['passed'])
                raise OSError('injected launch failure')
            with patch.object(sys, 'argv', ['test-native-credentials.py', '--output', output.relative_to(ROOT).as_posix()]), \
                    patch.dict(os.environ, JAVA_HOME=str(Path(directory) / 'missing-jdk')), \
                    patch('subprocess.run', side_effect=reject_launch):
                with self.assertRaises(OSError):
                    runpy.run_path(str(ROOT / 'scripts/test-native-credentials.py'), run_name='__main__')

    def test_native_and_compat_reports_are_owned_by_each_fresh_run(self):
        for name in ('native', 'windows-compat'):
            first = ROOT / '.build-cache/R1/fresh-one'
            second = ROOT / '.build-cache/R1/fresh-two'
            self.assertNotEqual(gate.report_path(name, first), gate.report_path(name, second))
            command = gate.command_for(name, first)
            self.assertEqual('--output', command[-2])
            self.assertEqual(gate.report_path(name, first).relative_to(ROOT).as_posix(), command[-1])


if __name__ == '__main__':
    unittest.main()
