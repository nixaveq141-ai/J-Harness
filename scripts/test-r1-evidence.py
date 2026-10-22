"""R1 adds cases: gates must require reviewed identities rather than an old count."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
from evidence_checks import validate_java_suites


class ExpandedJavaEvidenceTest(unittest.TestCase):
    def test_r1_builds_before_runtime_gates_and_failed_build_never_runs_them(self):
        spec=importlib.util.spec_from_file_location('r1_order_fixtures',Path(__file__).with_name('test-r0-validation.py'))
        fixtures=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixtures)
        fixture=fixtures.RegressionReportTest('test_gate_only_and_with_product_exact_complete_sets_can_pass')
        fixture.setUp()
        try:
            for fail_build in (False,True):
                calls=[]
                def build(output, run_id, report):
                    calls.append('java')
                    if fail_build: raise RuntimeError('build failed')
                    report['commands'].append(dict(name='java',tests=1))
                def gate(*args,**kwargs):
                    calls.append('gate');raise RuntimeError('gate reached')
                destination='.build-cache/R1/order-'+str(fail_build)
                with patch.object(fixtures.gate,'JAVA_FIRST',True),patch.object(fixtures.gate,'collect_java',side_effect=build),patch.object(fixtures.gate,'run',side_effect=gate):
                    with self.assertRaisesRegex(RuntimeError,'build failed' if fail_build else 'gate reached'):
                        fixtures.gate.collect(destination,'r1-order',with_product=True)
                self.assertEqual(['java'] if fail_build else ['java','gate'],calls)
                self.assertEqual('INCOMPLETE',json.loads((fixture.root/destination/'results.json').read_bytes())['status'])
        finally:
            fixture.doCleanups()

    def setUp(self):
        self.contract = dict(schemaVersion=1,suites=[dict(module='fixture',name='Suite',cases=['oldBaseline','newR1Case'])])
        self.suites = [dict(self.contract['suites'][0],tests=2,failures=0,errors=0,skipped=0)]

    def test_exact_old_and_new_identities_pass_without_a_magic_410_count(self):
        self.assertEqual(2,validate_java_suites(self.suites,self.contract))

    def test_old_baseline_without_new_regression_is_not_accepted(self):
        self.suites[0].update(tests=1,cases=['oldBaseline'])
        with self.assertRaisesRegex(ValueError,'Wrong Java case identities'):
            validate_java_suites(self.suites,self.contract)

    def test_duplicate_contract_or_observed_suite_and_cases_reject(self):
        for target,key in [(self.contract,'suites'),(dict(suites=self.suites),'suites')]:
            value=copy.deepcopy(target);value[key].append(copy.deepcopy(value[key][0]))
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'Duplicate or invalid'):
                validate_java_suites(self.suites,value) if target is self.contract else validate_java_suites(value['suites'],self.contract)
        for rows in [self.contract['suites'],self.suites]:
            changed=copy.deepcopy(rows);changed[0]['cases'].append('oldBaseline')
            with self.assertRaisesRegex(ValueError,'Empty or duplicate'):
                validate_java_suites(self.suites,dict(schemaVersion=1,suites=changed)) if rows is self.contract['suites'] else validate_java_suites(changed,self.contract)

    def test_empty_wrong_schema_and_invented_case_contracts_reject(self):
        for contract in [dict(schemaVersion=True,suites=self.contract['suites']),dict(schemaVersion=1,suites=[]),
                         dict(schemaVersion=1,suites=[dict(module='fixture',name='Suite',cases=['invented'])])]:
            with self.subTest(contract=contract),self.assertRaises(ValueError):
                validate_java_suites(self.suites,contract)

    def test_failed_skipped_or_boolean_counters_reject_even_with_correct_identities(self):
        for field,value in [('failures',1),('errors',1),('skipped',1),('tests',True),('tests',3),('failures',False)]:
            rows=copy.deepcopy(self.suites);rows[0][field]=value
            with self.subTest(field=field,value=value),self.assertRaisesRegex(ValueError,'Incomplete or failing'):
                validate_java_suites(rows,self.contract)

    def test_added_or_substituted_suite_is_not_accepted(self):
        rows=copy.deepcopy(self.suites);rows.append(dict(module='fixture',name='Other',cases=['one'],tests=1,failures=0,errors=0,skipped=0))
        with self.assertRaisesRegex(ValueError,'Wrong Java case identities'):
            validate_java_suites(rows,self.contract)

    def test_fresh_java_receipt_rejects_a_substituted_built_jar(self):
        spec=importlib.util.spec_from_file_location('r1_receipt_fixtures',Path(__file__).with_name('test-r0-validation.py'))
        fixtures=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixtures)
        fixture=fixtures.RegressionReportTest('test_gate_only_and_with_product_exact_complete_sets_can_pass')
        fixture.setUp()
        try:
            report=fixture.write_report(True)
            jar=fixture.root/'harness-cli/target/test.jar';jar.parent.mkdir(parents=True);jar.write_bytes(b'actual synthetic build')
            entry=report['commands'][-1]
            digest=hashlib.sha256(jar.read_bytes()).hexdigest()
            entry['artifact']=dict(path=jar.relative_to(fixture.root).as_posix(),sha256=digest)
            receipt_path=fixture.root/entry['receipt'];receipt=json.loads(receipt_path.read_bytes())
            receipt['outputs'][entry['artifact']['path']]=digest
            receipt_path.write_text(json.dumps(receipt));entry['receiptSha256']=fixtures.digest(receipt_path.read_bytes())
            fixture.save(report)
            with patch.object(fixtures.gate,'JAVA_ARTIFACT',jar):
                fixtures.gate.verify_report(fixture.relative)
                jar.write_bytes(b'substituted after build')
                with self.assertRaisesRegex(ValueError,'Java build artifact drift'):
                    fixtures.gate.verify_report(fixture.relative)
        finally:
            fixture.doCleanups()


if __name__ == '__main__':
    unittest.main()
