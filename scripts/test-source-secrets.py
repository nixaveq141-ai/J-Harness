import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('scanner',Path(__file__).with_name('scan-source-secrets.py'))
scanner=importlib.util.module_from_spec(spec);spec.loader.exec_module(scanner)

class SourceSecretTest(unittest.TestCase):
    def test_key_patterns_report_only_location(self):
        for value in [b'sk-'+b'A'*48,b'ghp_'+b'A'*40,b'AKIA'+b'A'*16,b'-'*5+b'BEGIN PRIVATE KEY'+b'-'*5]:
            result=scanner.findings(b'first\n'+value)
            self.assertTrue(result);self.assertEqual(2,result[0]['line']);self.assertNotIn(value.decode(),str(result))
    def test_environment_references_and_short_synthetic_fixture_are_not_credentials(self):
        self.assertEqual([],scanner.findings(b'JHARNESS_API_KEY synthetic-demo-key sk-demo'))

if __name__=='__main__':unittest.main()
