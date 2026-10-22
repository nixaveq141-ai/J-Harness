"""A completion label cannot substitute for coverage, original scope or passed cases."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('r1_ledger',Path(__file__).with_name('verify-r1-implementation.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)


class R1ImplementationEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);(self.root/'source.py').write_text('# test source')
        self.expected={'OCR-0001':dict(task='R1-01',verdict='partial')}
        self.row=dict(id='OCR-0001',task='R1-01',originalVerdict='partial',status='IMPLEMENTED_LOCAL_VALIDATED',
                      implementation='Concrete bounded change with a real regression',files=['source.py'],tests=['Suite#regression'])
        self.data=dict(schemaVersion=1,phase='R1',opinions=[self.row])

    def check(self,data=None,**kwargs):
        return gate.validate_ledger(self.data if data is None else data,self.expected,root=self.root,**kwargs)

    def test_complete_ledger_requires_the_actual_passing_case(self):
        self.assertEqual(1,self.check(passed={'Suite#regression'},complete=True)['IMPLEMENTED_LOCAL_VALIDATED'])
        with self.assertRaisesRegex(ValueError,'case did not pass'):self.check(passed={'Suite#unrelated'},complete=True)

    def test_duplicate_missing_and_foreign_opinions_are_rejected(self):
        for rows in [[],[self.row,self.row],[dict(self.row,id='OCR-9999')]]:
            with self.subTest(rows=rows),self.assertRaises(ValueError):self.check(dict(self.data,opinions=rows))

    def test_original_partial_scope_and_assignment_cannot_be_changed(self):
        for change in [dict(originalVerdict='accepted'),dict(task='R1-02')]:
            with self.assertRaisesRegex(ValueError,'assignment/verdict changed'):
                self.check(dict(self.data,opinions=[dict(self.row,**change)]))

    def test_work_in_progress_is_not_completion_even_with_a_test_reference(self):
        data=dict(self.data,opinions=[dict(self.row,status='IN_PROGRESS')])
        self.check(data)
        with self.assertRaisesRegex(ValueError,'remains incomplete'):self.check(data,complete=True)

    def test_implementation_labels_without_files_reason_or_tests_are_rejected(self):
        for field,value in [('implementation',''),('files',[]),('tests',[]),('tests',['Suite#regression','Suite#regression'])]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                self.check(dict(self.data,opinions=[dict(self.row,**{field:value})]))

    def test_partial_verification_requires_an_explicit_limitation(self):
        data=dict(self.data,opinions=[dict(self.row,status='LOCAL_PARTIAL')])
        with self.assertRaisesRegex(ValueError,'Unexplained'):self.check(data)
        data['opinions'][0]['limitations']=['Native POSIX has not run']
        self.assertEqual(1,self.check(data,complete=True,passed={'Suite#regression'})['LOCAL_PARTIAL'])

    def test_escaping_and_runtime_cache_evidence_is_not_source_proof(self):
        for path in ['../escape.py','.build-cache/test.py']:
            with self.assertRaises(ValueError):self.check(dict(self.data,opinions=[dict(self.row,files=[path])]))

    def test_validation_is_read_only(self):
        before=copy.deepcopy(self.data);source=(self.root/'source.py').read_bytes()
        self.check(complete=True,passed={'Suite#regression'})
        self.assertEqual(before,self.data);self.assertEqual(source,(self.root/'source.py').read_bytes())


if __name__=='__main__':
    unittest.main()
