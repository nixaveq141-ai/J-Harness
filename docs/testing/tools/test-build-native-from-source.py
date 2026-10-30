"""Offline failure ownership and source pin checks; no compilers or network."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('builder',Path(__file__).with_name('build-native-from-source.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class SourceBuildTest(unittest.TestCase):
    def test_missing_cache_parent_and_missing_tool_still_preserve_attempt(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(builder,'ROOT',Path(temp)),patch.dict(os.environ,{'GITHUB_ACTIONS':'true'}),patch.object(builder.subprocess,'run',side_effect=FileNotFoundError('synthetic missing tool')):
            with self.assertRaises(FileNotFoundError):builder.main()
            report=json.loads((Path(temp)/'.build-cache/native-source-build/report.json').read_bytes())
            self.assertEqual('INCOMPLETE',report['status'])
            self.assertEqual('FileNotFoundError',report['commands'][0]['failureType'])
            self.assertIsNone(report['commands'][0]['exitCode'])

    def test_source_revision_mismatch_never_reaches_compilation(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(builder,'ROOT',Path(temp)),patch.dict(os.environ,{'GITHUB_ACTIONS':'true'}):
            calls=[]
            def run(argv,**kwargs):
                calls.append(argv)
                if 'clone' in argv:Path(argv[-1]).mkdir()
                return types.SimpleNamespace(returncode=0,stdout=b'wrong-revision\n')
            with patch.object(builder.subprocess,'run',side_effect=run),self.assertRaisesRegex(ValueError,'revision mismatch'):builder.main()
            self.assertFalse(any('native' in argv for argv in calls))
            self.assertEqual('INCOMPLETE',json.loads((Path(temp)/'.build-cache/native-source-build/report.json').read_bytes())['status'])


if __name__=='__main__':unittest.main()
