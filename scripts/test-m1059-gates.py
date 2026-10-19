import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('aggregate',Path(__file__).with_name('aggregate-m1059-ci.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
browser_spec=importlib.util.spec_from_file_location('browser_prepare',Path(__file__).with_name('prepare-test-browsers.py'))
browser=importlib.util.module_from_spec(browser_spec);browser_spec.loader.exec_module(browser)

class CiGateTest(unittest.TestCase):
    def reports(self,change=None):
        with tempfile.TemporaryDirectory() as directory:
            for os_name in ['Windows','Linux','macOS']:
                data=dict(milestone='M10.5.9',runnerOs=os_name,status='RUNNER_PASS',ciRunId='fixture-run',commit='fixture-commit',paidRequests=0,workingTreeDirty=False,
                          sourceSha256={'fixture':'same'},uiSha256={'fixture':'same'},suites=[{'name':'fixture','cases':['same']}],
                          java=dict(tests=410,failures=0,errors=0,skipped=0),browserChecks=813,sdkChecks=58,frontendClientChecks=33)
                if change:change(data)
                p=Path(directory)/os_name/'runner.json';p.parent.mkdir();p.write_text(json.dumps(data))
            return gate.aggregate(directory)

    def test_complete_matching_fixture_can_pass(self):self.assertTrue(self.reports()['m11Eligible'])
    def test_local_reports_never_grant_ci_gate(self):
        with self.assertRaises(ValueError):self.reports(lambda d:d.update(status='LOCAL_PASS_CI_PENDING'))
    def test_different_source_rejects(self):
        with self.assertRaises(ValueError):self.reports(lambda d:d.update(sourceSha256={'fixture':d['runnerOs']}))
    def test_missing_browser_cases_rejects(self):
        with self.assertRaises(ValueError):self.reports(lambda d:d.update(browserChecks=1))
    def test_missing_run_identity_rejects(self):
        with self.assertRaises(ValueError):self.reports(lambda d:d.update(ciRunId=None))

class BrowserManifestTest(unittest.TestCase):
    value=b'<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0"><assemblyIdentity version="1.0.0.0" name="mozglue" type="win32"/><file name="mozglue.dll"/></assembly>'
    def test_embedded_manifest_is_copied_exactly(self):self.assertEqual(self.value,browser.manifest(b'prefix'+self.value+b'suffix'))
    def test_foreign_identity_and_paths_are_rejected(self):
        for value in [self.value.replace(b'name="mozglue"',b'name="foreign"'),self.value.replace(b'mozglue.dll',b'../outside.dll')]:
            with self.assertRaises(ValueError):browser.manifest(value)
    def test_external_declarations_are_rejected(self):
        with self.assertRaises(ValueError):browser.manifest(self.value.replace(b'<file',b'<!DOCTYPE file><file'))

if __name__=='__main__':unittest.main()
