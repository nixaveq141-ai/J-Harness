"""Synthetic-only regression: no generated class, compiler or paid request executes."""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('frozen_live', Path(__file__).with_name('live-evaluate.py'))
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)


class HistoricalLiveEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.directory = self.root / '.build-cache/M4-live/calculator-fixture'
        self.directory.mkdir(parents=True)
        self.source = self.directory / live.SOURCE
        self.source.parent.mkdir(parents=True)
        self.source.write_text('class Calculator {}')
        self.test_source = self.directory / live.TEST
        self.test_source.parent.mkdir(parents=True)
        self.test_source.write_text('class CalculatorTest {}')
        self.jar = self.root / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        self.jar.parent.mkdir(parents=True)
        self.jar.write_bytes(b'synthetic jar never loaded')
        self.log = self.directory / '.jharness/sessions/evaluation.jsonl'
        self.log.parent.mkdir(parents=True)
        self.log.write_text('{}\n'+json.dumps(dict(type='budget.reserved', payload=dict(reserved='2.5')))+'\n')
        self.ledger = self.directory.parent / 'ledger.json'
        self.run = dict(fixture='calculator', directory=self.directory.relative_to(self.root).as_posix(),
                        sourceSha256={live.SOURCE:self.sha(self.source),live.TEST:self.sha(self.test_source)}, cliJarSha256=self.sha(self.jar),
                        costUpperBoundCny='2.0', independentOracle=dict(exitCode=0, stdout='historical reference'))
        self.ledger.write_text(json.dumps(dict(runs=[self.run])))

    @staticmethod
    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def test_direct_execution_and_oracle_are_disabled_before_any_process_launch(self):
        with patch.object(live, 'ROOT', self.root), patch('subprocess.run', side_effect=AssertionError('process launched')), \
             patch('subprocess.Popen', side_effect=AssertionError('process launched')):
            for fn, args in [(live.oracle,(self.directory,'calculator')),(live.evaluate,('calculator',1,None,None))]:
                with self.subTest(entry=fn.__name__), self.assertRaisesRegex(RuntimeError,'disabled'):
                    fn(*args)

    def test_generated_environment_reader_unicode_and_oracle_shadow_are_never_approved(self):
        attacks = ['System.getenv("SYNTHETIC_SECRET")', r'\u0053ystem.getenv("SYNTHETIC_SECRET")',
                   'class EvaluationOracle { static { /* hostile */ } }']
        for text in attacks:
            with self.subTest(text=text):
                self.assertFalse(live.allowed('write_file',dict(path=live.SOURCE,content=text),{live.SOURCE},'cmd'))
                self.assertFalse(live.allowed('edit_file',dict(path=live.SOURCE,content='safe decoy',newText=text),{live.SOURCE},'cmd'))
        self.assertFalse(live.allowed('execute_command',dict(argv=['cmd'],mode='CMD'),{live.SOURCE},'cmd'))

    def test_cli_old_opt_in_flags_cannot_enable_execution_or_require_a_key(self):
        script = str(Path(__file__).with_name('live-evaluate.py'))
        result = subprocess.run([sys.executable, script, '--limit-cny','18','--repetitions','2'],
                                capture_output=True, text=True, timeout=15, env={**os.environ,'DEEPSEEK_API_KEY':''})
        self.assertEqual(2,result.returncode)
        self.assertIn('disabled',result.stderr)
        self.assertNotIn('DEEPSEEK_API_KEY',result.stderr)

    def test_audit_is_read_only_and_matching_files_never_become_acceptance(self):
        before = {p:self.sha(p) for p in self.root.rglob('*') if p.is_file()}
        with patch('subprocess.run',side_effect=AssertionError('oracle execution')):
            result = live.audit_historical(self.root)
        self.assertFalse(result['executionEnabled'])
        self.assertFalse(result['evidenceAcceptance'])
        self.assertFalse(result['runs'][0]['executed'])
        self.assertEqual('FILE_HASHES_MATCH_REFERENCE_ONLY',result['runs'][0]['identity'])
        self.assertEqual(before,{p:self.sha(p) for p in self.root.rglob('*') if p.is_file()})

    def test_source_or_jar_drift_is_stale_without_rewriting_original_oracle(self):
        before = self.ledger.read_bytes()
        for path in (self.source,self.jar):
            raw=path.read_bytes();path.write_bytes(raw+b' modified')
            self.assertEqual('STALE',live.audit_historical(self.root)['runs'][0]['identity'])
            self.assertEqual(before,self.ledger.read_bytes())
            path.write_bytes(raw)

    def test_missing_empty_header_only_partial_and_missing_budget_cost_remain_unknown(self):
        for raw in [b'',b'{}\n',b'{}\n{',b'{}\n{"type":"model.requested"}\n']:
            self.log.write_bytes(raw)
            result = live.inspect_cost(self.log)
            self.assertEqual('UNKNOWN',result['status'])
            self.assertIsNone(result['retainedUpperBoundCny'])
            self.assertFalse(result['grantsNewAllowance'])
        self.log.unlink()
        result=live.audit_historical(self.root)['runs'][0]['cost']
        self.assertEqual('UNKNOWN',result['status'])
        self.assertEqual('2.0',result['retainedUpperBoundCny'])

    def test_cancellation_and_malformed_tail_retain_known_reservations(self):
        self.log.write_text('{}\n'+json.dumps(dict(type='budget.reserved',payload=dict(reserved='4.0')))+'\ninvalid\n')
        result=live.inspect_cost(self.log,'2.0')
        self.assertEqual('UNKNOWN',result['status'])
        self.assertEqual('4.0',result['retainedUpperBoundCny'])
        self.assertFalse(result['grantsNewAllowance'])

    def test_nonfinite_negative_and_boolean_costs_do_not_release_allowance(self):
        for cost in ['NaN','Infinity','-1',True,None]:
            self.log.write_text('{}\n'+json.dumps(dict(type='budget.reserved',payload=dict(reserved=cost)))+'\n')
            self.assertEqual('UNKNOWN',live.inspect_cost(self.log)['status'])

    def test_historical_goal_requires_reading_existing_calculator_test(self):
        self.assertIn(live.TEST,live.required_reads('calculator'))
        self.assertNotIn(live.TEST,live.required_reads('broken-project'))

    def test_unsafe_historical_directory_is_not_read_or_exposed(self):
        self.run['directory']='../SYNTHETIC_SECRET'
        self.ledger.write_text(json.dumps(dict(runs=[self.run])))
        result=live.audit_historical(self.root)
        self.assertEqual('INCOMPLETE',result['runs'][0]['identity'])
        self.assertNotIn('SYNTHETIC_SECRET',json.dumps(result))

    def test_audit_cli_failure_keeps_original_evidence_and_prints_no_exception_values(self):
        with patch.object(live,'audit_historical',side_effect=ValueError('SYNTHETIC_SECRET')), \
             contextlib.redirect_stdout(io.StringIO()) as out,contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(2,live.main(['--audit-only']))
        self.assertNotIn('SYNTHETIC_SECRET',out.getvalue()+err.getvalue())


if __name__ == '__main__':
    unittest.main()
