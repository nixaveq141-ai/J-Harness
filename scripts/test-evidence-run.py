"""Offline regressions: real failing commands cannot become passing receipts."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from evidence_run import run, validate, inputs
from evidence_checks import suite_snapshot, sdk_checks, native_checks, CONTRACT


class ReceiptTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'pom.xml').write_text('<project/>')
        self.log = self.root / 'run.log'
        self.receipt = self.root / 'receipt.json'

    def execute(self, code, **kwargs):
        return run([sys.executable, '-c', code], name='fixture', log=self.log,
                   receipt=self.receipt, run_id='offline-test-run', root=self.root,
                   cwd=self.root, timeout=5, **kwargs)

    def test_success_has_exit_log_source_and_time_identity(self):
        self.execute("print('actual command')")
        value = json.loads(self.receipt.read_bytes())
        validate(value, name='fixture', log=self.log, run_id='offline-test-run', source=inputs(self.root))
        self.assertEqual(value['exitCode'], 0)

    def test_failure_with_success_words_revokes_old_pass(self):
        self.execute("print('old success')")
        with self.assertRaisesRegex(ValueError, 'command failed'):
            self.execute("print('BUILD SUCCESS pass 33 fail 0'); raise SystemExit(9)")
        self.assertEqual(json.loads(self.receipt.read_bytes())['status'], 'INCOMPLETE')
        self.assertEqual(json.loads(self.receipt.read_bytes())['exitCode'], 9)

    def test_source_change_during_command_rejects(self):
        with self.assertRaisesRegex(ValueError, 'inputs changed'):
            self.execute("from pathlib import Path; Path('pom.xml').write_text('changed')")
        self.assertEqual(json.loads(self.receipt.read_bytes())['status'], 'INCOMPLETE')

    def test_stale_output_is_removed_and_missing_output_fails(self):
        output = self.root / 'result.json'
        output.write_text('{"status":"PASS"}')
        with self.assertRaises(FileNotFoundError):
            self.execute('pass', outputs=(output,))
        self.assertFalse(output.exists())
        self.assertEqual(json.loads(self.receipt.read_bytes())['status'], 'INCOMPLETE')

    def test_timeout_has_bounded_failure_receipt(self):
        with self.assertRaisesRegex(ValueError, 'timed out'):
            run([sys.executable, '-c', 'import time; time.sleep(20)'], name='fixture', log=self.log,
                receipt=self.receipt, run_id='offline-test-run', root=self.root, cwd=self.root, timeout=.2)
        self.assertIs(json.loads(self.receipt.read_bytes())['timedOut'], True)
        self.assertEqual(json.loads(self.receipt.read_bytes())['status'], 'INCOMPLETE')

    def test_single_field_mutations_reject_for_their_own_reason(self):
        self.execute('pass')
        valid = json.loads(self.receipt.read_bytes())
        for field, value, reason in [('runId', 'other-run', 'same-run'), ('exitCode', False, 'same-run'),
                                     ('sourceSha256', {}, 'source drift'), ('logSha256', '0'*64, 'log drift'),
                                     ('finishedAt', 'not-a-time', 'command time')]:
            with self.subTest(field=field):
                changed = {**valid, field: value}
                with self.assertRaisesRegex(ValueError, reason):
                    validate(changed, name='fixture', log=self.log, run_id='offline-test-run', source=inputs(self.root))

    def test_wrong_command_and_working_directory_reject(self):
        self.execute('pass')
        value=json.loads(self.receipt.read_bytes())
        for expected, reason in [({'command':['invented']}, 'Wrong command receipt'), ({'cwd':'frontend'}, 'working directory')]:
            with self.assertRaisesRegex(ValueError,reason):
                validate(value,name='fixture',log=self.log,run_id='offline-test-run',source=inputs(self.root),**expected)

    def test_current_collector_revokes_pass_before_missing_identity_or_receipt(self):
        spec=importlib.util.spec_from_file_location('current_collector',Path(__file__).with_name('collect-m1059-evidence.py'))
        collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)
        out=self.root/'.build-cache/M10.5.9';out.mkdir(parents=True)
        runner=out/'runner.json'
        for run_id, error in [(None, ValueError), ('new-run', FileNotFoundError)]:
            runner.write_text('{"status":"RUNNER_PASS","m11Eligible":true}')
            with patch.object(collector,'ROOT',self.root),patch.object(collector,'OUT',out),patch.dict('os.environ',{},clear=True):
                if run_id:
                    import os
                    os.environ['JHARNESS_EVIDENCE_RUN_ID']=run_id
                with self.assertRaises(error):collector.collect(self.log)
            self.assertEqual(json.loads(runner.read_bytes())['status'],'INCOMPLETE')
            self.assertIs(json.loads(runner.read_bytes())['m11Eligible'],False)


class ComponentTests(unittest.TestCase):
    def test_xml_declared_count_must_match_real_cases(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'report.xml'
            path.write_text('<testsuite name="real" tests="410" failures="0" errors="0" skipped="0"><testcase name="one"/></testsuite>')
            with self.assertRaisesRegex(ValueError, 'counter mismatch'):
                suite_snapshot(path, 'harness-fixture')
            path.write_text('<testsuite name="real" tests="1" failures="0" errors="0" skipped="0"><testcase name="one"/></testsuite>')
            self.assertEqual(suite_snapshot(path, 'harness-fixture')['cases'], ['one'])

    def fixtures(self):
        sdk = dict(results=[dict(language=k, checks=v[:], passed=True) for k,v in CONTRACT['sdk'].items()],
                   allAuthorized=True, modelContextRedacted=True, requests=12)
        examples = dict(checks=[dict(name=n, passed=True, factsSha256='a'*64) for n in CONTRACT['examples']])
        return sdk, examples

    def test_component_contract_positive_and_strict_negatives(self):
        sdk, examples = self.fixtures()
        sdk_checks(sdk, examples)
        mutations = [lambda s,e:s.update(requests=0), lambda s,e:s.update(allAuthorized='true'),
                     lambda s,e:s['results'][0].update(passed=1),
                     lambda s,e:s['results'][0]['checks'].__setitem__(0, 'invented'),
                     lambda s,e:e['checks'][0].update(passed=1), lambda s,e:e['checks'][0].update(factsSha256='')]
        for mutation in mutations:
            s,e = copy.deepcopy((sdk,examples)); mutation(s,e)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                sdk_checks(s,e)

    def test_native_requires_exact_cases_and_actual_booleans(self):
        native = dict(expectationMet=True, passed=True, exitCode=0, checks={n:True for n in CONTRACT['native']})
        native_checks(native)
        for checks in [{}, {**native['checks'], 'cleanup':1}, {**native['checks'], 'invented':True}]:
            with self.assertRaisesRegex(ValueError, 'native cases'):
                native_checks({**native, 'checks':checks})


if __name__ == '__main__':
    unittest.main()
