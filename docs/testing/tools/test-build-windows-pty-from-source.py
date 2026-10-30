"""Offline source revision and fresh-output rejection controls."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('windows_builder',Path(__file__).with_name('build-windows-pty-from-source.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class WindowsBuildTest(unittest.TestCase):
    def test_revision_mismatch_is_rejected_before_source_is_accepted(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);calls=[]
            with patch.object(builder.subprocess,'run',return_value=types.SimpleNamespace(stdout=b'wrong revision\n')):
                with self.assertRaisesRegex(ValueError,'revision rejected'):
                    builder.checkout('winpty',root,lambda argv,cwd:calls.append(argv),{})
            self.assertEqual(2,len(calls))
            self.assertIn(builder.PINS['winpty'][1],calls[-1])

    def test_outputs_cannot_escape_or_reuse_non_binary_input(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(builder,'ROOT',Path(temp)):
            root=Path(temp);source=root/'source';source.mkdir();outside=root/'elsewhere.dll';outside.write_bytes(b'MZoutside')
            with self.assertRaisesRegex(ValueError,'Fresh source'):builder.outputs([outside],source)
            missing=source/'missing.dll'
            with self.assertRaisesRegex(ValueError,'Fresh source'):builder.outputs([missing],source)
            wrong=source/'wrong.dll';wrong.write_bytes(b'C source is not a native output')
            with self.assertRaisesRegex(ValueError,'PE output'):builder.outputs([wrong],source)

    def test_missing_tool_still_records_incomplete_attempt_and_log_hash(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(builder,'ROOT',Path(temp)),patch.dict(os.environ,{'GITHUB_ACTIONS':'true'}),patch.object(sys,'argv',['builder','--component','winpty']):
            with patch.object(builder.subprocess,'run',side_effect=FileNotFoundError('controlled missing git')):
                with self.assertRaises(FileNotFoundError):builder.main()
            report=json.loads((Path(temp)/'.build-cache/windows-pty-source/report.json').read_bytes())
            self.assertEqual('INCOMPLETE',report['status'])
            self.assertEqual([],report['outputs'])
            self.assertEqual('FileNotFoundError',report['commands'][0]['failureType'])
            self.assertEqual(64,len(report['commands'][0]['logSha256']))


if __name__=='__main__':unittest.main()
