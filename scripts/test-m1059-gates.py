import copy
import hashlib
import importlib.util
import json
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('aggregate',Path(__file__).with_name('aggregate-m1059-ci.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
browser_spec=importlib.util.spec_from_file_location('browser_prepare',Path(__file__).with_name('prepare-test-browsers.py'))
browser=importlib.util.module_from_spec(browser_spec);browser_spec.loader.exec_module(browser)


class CiGateTest(unittest.TestCase):
    """Synthetic reports exercise schema only; they never represent a real CI run."""
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        contract = json.loads(gate.CONTRACT_PATH.read_bytes())
        self.suites = [dict(s, sha256='a'*64, tests=len(s['cases']), failures=0, errors=0, skipped=0)
                       for s in contract['suites']]
        self.base = dict(milestone='M10.5.9', status='RUNNER_PASS', ciRunId='123456789', runId='synthetic-schema-test',
                         commit='1'*40, paidRequests=0, workingTreeDirty=False, remoteExecution=True, m11Eligible=False,
                         jarSha256='b'*64, sourceSha256={p:'c'*64 for p in contract['requiredSourcePaths']},
                         uiSha256={p:'d'*64 for p in ['index.html','ui-license.txt','assets/index.js','assets/index.css']},
                         suites=self.suites, java=dict(tests=sum(len(s['cases']) for s in self.suites),failures=0,errors=0,skipped=0),
                         browserChecks=813,sdkChecks=66,frontendClientChecks=42)

    def write_reports(self, change=None):
        for os_name in ['Windows','Linux','macOS']:
            data = copy.deepcopy(self.base)
            data['runnerOs'] = os_name
            if change and os_name == 'Linux': change(data)
            p = self.directory/os_name/'runner.json';p.parent.mkdir(exist_ok=True)
            p.write_text(json.dumps(data), encoding='utf-8')

    def reject(self, change, reason):
        self.write_reports(change)
        with self.assertRaisesRegex(ValueError, reason): gate.aggregate(self.directory)

    def test_complete_matching_synthetic_structure_can_pass(self):
        self.write_reports()
        report = gate.aggregate(self.directory)
        self.assertIs(report['m11Eligible'], True)
        self.assertEqual(3, len(report['runnerReports']))
        for name, digest in report['runnerReports'].items():
            self.assertEqual(hashlib.sha256(Path(name).read_bytes()).hexdigest(), digest)

    def test_one_field_identity_failures_have_specific_reasons(self):
        for field, value, reason in [
            ('status','LOCAL_PASS_CI_PENDING','Not actual clean passing CI'),
            ('remoteExecution',False,'Not actual clean passing CI'),
            ('workingTreeDirty',True,'Not actual clean passing CI'),
            ('m11Eligible',True,'Not actual clean passing CI'),
            ('ciRunId',None,'Invalid CI run identity'),
            ('ciRunId','fixture-run','Invalid CI run identity'),
            ('commit','','Invalid commit identity'),
            ('commit','fixture-commit','Invalid commit identity'),
            ('runId','','Invalid evidence run identity'),
            ('runnerOs','local','Invalid runner OS'),
            ('jarSha256','','Invalid JAR fingerprint'),
            ('paidRequests',False,'paidRequests: incorrect integer count'),
            ('browserChecks',True,'browserChecks: incorrect integer count'),
            ('sdkChecks',1,'sdkChecks: incorrect integer count'),
            ('sdkChecks',58,'sdkChecks: incorrect integer count'),
            ('frontendClientChecks',33,'frontendClientChecks: incorrect integer count'),
            ('frontendClientChecks','33','frontendClientChecks: incorrect integer count')]:
            with self.subTest(field=field,value=value): self.reject(lambda d:d.update({field:value}),reason)

    def test_one_field_fingerprint_failures_have_specific_reasons(self):
        for field,value,reason in [
            ('sourceSha256',{},'sourceSha256: empty fingerprint'),
            ('sourceSha256',{'pom.xml':'c'*64},'sourceSha256: missing required source paths'),
            ('sourceSha256',{'../outside':'c'*64},'sourceSha256: invalid path or SHA256'),
            ('sourceSha256',{'pom.xml':'PASS'},'sourceSha256: invalid path or SHA256'),
            ('uiSha256',{},'uiSha256: empty fingerprint'),
            ('uiSha256',{'index.html':'d'*64,'ui-license.txt':'d'*64},'Incomplete UI asset set')]:
            with self.subTest(field=field,value=value): self.reject(lambda d:d.update({field:value}),reason)
        self.reject(lambda d:d['sourceSha256'].update({'pom.xml':'e'*64}), 'Different CI/source/UI: sourceSha256')
        self.reject(lambda d:d.update(ciRunId='987654321'), 'Different CI/source/UI: ciRunId')
        self.reject(lambda d:d.update(runId='another-run'), 'Different CI/source/UI: runId')
        self.reject(lambda d:d.update(runnerOs='Windows'), 'Missing or duplicate runner OS')

    def test_empty_missing_duplicate_or_invented_cases_reject(self):
        self.reject(lambda d:d.update(suites=[]), 'Empty Java suites')
        self.reject(lambda d:d['suites'].pop(), 'Missing Java suite')
        self.reject(lambda d:d['suites'].append(copy.deepcopy(d['suites'][0])), 'Duplicate Java suite')
        self.reject(lambda d:d['suites'][0].update(cases=[]), 'Invalid or empty Java cases')
        self.reject(lambda d:d['suites'][0]['cases'].append(d['suites'][0]['cases'][0]), 'Duplicate Java case')
        self.reject(lambda d:d['suites'][0]['cases'].__setitem__(0,'invented-case'), 'Incomplete Java case identities')
        self.reject(lambda d:d['suites'][0].pop('module'), 'Invalid Java suite identity')
        self.reject(lambda d:d['suites'][0].update(module='harness-wrong'), 'Unexpected Java suite')
        self.reject(lambda d:d['suites'][0].update(tests=True), 'Suite tests: incorrect integer count')
        self.reject(lambda d:d['suites'][0].update(failures=1), 'Suite failures: incorrect integer count')
        self.reject(lambda d:d['java'].update(tests=409), 'Java tests: incorrect integer count')
        self.reject(lambda d:d['java'].update(errors=False), 'Java errors: incorrect integer count')

    def test_missing_runner_rejects(self):
        self.write_reports();(self.directory/'Linux/runner.json').unlink()
        with self.assertRaisesRegex(ValueError, 'exactly three'): gate.aggregate(self.directory)

    def test_duplicate_json_keys_reject(self):
        self.write_reports()
        p=self.directory/'Linux/runner.json';p.write_text(p.read_text()[:-1]+',"paidRequests":0}')
        with self.assertRaisesRegex(ValueError,'Duplicate JSON key: paidRequests'):gate.aggregate(self.directory)

    def test_oversize_report_rejects(self):
        self.write_reports();(self.directory/'Linux/runner.json').write_bytes(b' '*(gate.MAX_REPORT_BYTES+1))
        with self.assertRaisesRegex(ValueError,'size limit'):gate.aggregate(self.directory)

    def test_report_hash_uses_exact_parsed_bytes_even_if_file_changes(self):
        self.write_reports()
        target = self.directory/'Linux/runner.json'
        original = target.read_bytes()
        load = gate.load_json
        def replace_after_parse(raw):
            value = load(raw)
            if type(value) is dict and value.get('runnerOs') == 'Linux':
                target.write_text('{"changed":true}',encoding='utf-8')
            return value
        with patch.object(gate,'load_json',side_effect=replace_after_parse):result=gate.aggregate(self.directory)
        self.assertEqual(hashlib.sha256(original).hexdigest(),result['runnerReports'][str(target.resolve())])
        self.assertNotEqual(hashlib.sha256(target.read_bytes()).hexdigest(),result['runnerReports'][str(target.resolve())])

    def test_report_hash_under_aliased_temporary_root(self):
        with tempfile.TemporaryDirectory() as container:
            alias = Path(container) / 'alias'
            if os.name == 'nt':
                subprocess.run(['cmd.exe', '/c', 'mklink', '/J', str(alias), str(self.directory.resolve())],
                               check=True, capture_output=True)
            else:
                alias.symlink_to(self.directory.resolve(), target_is_directory=True)
            original = self.directory
            try:
                self.directory = alias
                self.test_report_hash_uses_exact_parsed_bytes_even_if_file_changes()
            finally:
                self.directory = original
                if os.name == 'nt': alias.rmdir()
                else: alias.unlink()


class BrowserManifestTest(unittest.TestCase):
    value=b'<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0"><assemblyIdentity version="1.0.0.0" name="mozglue" type="win32"/><file name="mozglue.dll"/></assembly>'
    def test_embedded_manifest_is_copied_exactly(self):self.assertEqual(self.value,browser.manifest(b'prefix'+self.value+b'suffix'))
    def test_foreign_identity_and_paths_are_rejected(self):
        for value in [self.value.replace(b'name="mozglue"',b'name="foreign"'),self.value.replace(b'mozglue.dll',b'../outside.dll'),self.value.replace(b'mozglue.dll',b'C:\\outside.dll')]:
            with self.assertRaisesRegex(ValueError,'Unexpected assembly identity or file'):browser.manifest(value)
    def test_external_declarations_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'Unsupported assembly manifest'):browser.manifest(self.value.replace(b'<file',b'<!DOCTYPE file><file'))

if __name__=='__main__':unittest.main()
