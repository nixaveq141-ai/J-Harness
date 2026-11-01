import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import json
import sys
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('coverage',Path(__file__).with_name('audit-native-coverage.py'))
coverage=importlib.util.module_from_spec(spec);spec.loader.exec_module(coverage)


class NativeCoverageTest(unittest.TestCase):
    def test_empty_inventory_cannot_certify_empty_jar(self):
        with tempfile.TemporaryDirectory() as folder:
            jar=Path(folder)/'empty.jar'
            with zipfile.ZipFile(jar,'x'):pass
            with self.assertRaisesRegex(ValueError,'Nonempty'):
                coverage.audit(jar,dict(binaries=[]),{})

    def test_source_hash_without_origin_is_not_source_proof(self):
        inventory=dict(binaries=[dict(resource='a.so',coordinate='fixed:1',sha256=coverage.sha(b'a'))])
        with self.assertRaisesRegex(ValueError,'origin'):
            coverage.audit(Path('not-opened.jar'),inventory,{'a.so':dict(sha256=coverage.sha(b'a'),origin={})})

    def test_missing_runtime_revokes_previous_success(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);output=root/'coverage.json';output.write_text('{"status":"SOURCE_COVERAGE_COMPLETE"}')
            with patch.object(sys,'argv',['audit',str(root/'missing.jar'),str(output)]):
                with self.assertRaises(FileNotFoundError):coverage.main()
            self.assertEqual('INCOMPLETE',json.loads(output.read_bytes())['status'])

    def test_unknown_is_not_complete_and_unreviewed_bytes_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            jar=Path(folder)/'runtime.jar'
            with zipfile.ZipFile(jar,'x') as z:z.writestr('a.so',b'source');z.writestr('b.so',b'original')
            inventory=dict(binaries=[dict(resource=n,coordinate='fixed:coordinate:1',sha256=coverage.sha(v))
                                     for n,v in [('a.so',b'original'),('b.so',b'original')]])
            reviewed={'a.so':dict(sha256=coverage.sha(b'source'),origin=dict(runId='accepted'))}
            result=coverage.audit(jar,inventory,reviewed)
            self.assertEqual('SOURCE_COVERAGE_INCOMPLETE',result['status'])
            self.assertEqual(1,result['retainedOriginalCount']);self.assertFalse(result['fullOsCpeClearance'])
            with self.assertRaises(ValueError):coverage.audit(jar,inventory,{})
            reviewed['b.so']=dict(sha256=coverage.sha(b'original'),origin=dict(runId='separately-reviewed'))
            self.assertEqual('SOURCE_COVERAGE_COMPLETE',coverage.audit(jar,inventory,reviewed)['status'])
            with zipfile.ZipFile(jar,'a') as z:
                z.writestr('com/sun/jna/unknown/native-payload-without-extension',b'unlisted')
            with self.assertRaisesRegex(ValueError,'Uninventoried'):
                coverage.audit(jar,inventory,reviewed)


if __name__=='__main__':unittest.main()
