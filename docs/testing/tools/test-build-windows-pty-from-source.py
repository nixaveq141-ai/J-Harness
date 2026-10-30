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
    def test_pinned_console_overlay_precedes_registry_and_rejects_version_drift(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);tool=root/'tool';tool.mkdir();out=root/'out';out.mkdir();console=root/'console'
            overlay=console/'dep/overlay/fmt/vcpkg.json';overlay.parent.mkdir(parents=True)
            overlay.write_bytes(json.dumps(dict(name='fmt',version='11.1.4')).encode())
            manifest={'overrides':[dict(name='fmt',version='11.1.4')],
                      'vcpkg-configuration':{'overlay-ports':['./dep/overlay']}}
            def run(*args):self.fail('Overlay source is already present; do not fetch a different registry port')
            result=builder.prefetch_port_trees(tool,manifest,out,run,console)
            self.assertEqual('PINNED_CONSOLE_OVERLAY',result[0]['sourceKind'])
            self.assertEqual('dep/overlay/fmt/vcpkg.json',result[0]['manifestPath'])
            self.assertEqual(builder.sha(overlay),result[0]['manifestSha256'])
            with self.assertRaisesRegex(ValueError,'manifest root'):builder.prefetch_port_trees(tool,manifest,out,run)
            overlay.write_bytes(json.dumps(dict(name='fmt',version='11.0.2')).encode())
            with self.assertRaisesRegex(ValueError,'overlay version drift'):
                builder.prefetch_port_trees(tool,manifest,out,run,console)

    def test_historical_port_tree_prefetch_binds_manifest_and_rejects_reuse(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);tool=root/'tool';out=root/'out';out.mkdir()
            versions=tool/'versions/f-/fmt.json';versions.parent.mkdir(parents=True)
            versions.write_bytes(json.dumps(dict(versions=[dict(version='11.1.4',**{'port-version':0,'git-tree':'a'*40})])).encode())
            calls=[]
            def run(argv,cwd):
                calls.append((argv,cwd));Path(next(a.removeprefix('--output=') for a in argv if a.startswith('--output='))).write_bytes(b'controlled Git archive')
            manifest=dict(overrides=[dict(name='fmt',version='11.1.4')])
            result=builder.prefetch_port_trees(tool,manifest,out,run)
            self.assertEqual('a'*40,result[0]['gitTree'])
            self.assertEqual('a'*40,calls[0][0][-1])
            self.assertEqual(builder.sha(versions),result[0]['versionManifestSha256'])
            with self.assertRaisesRegex(ValueError,'Fresh port'):builder.prefetch_port_trees(tool,manifest,out,run)
            self.assertEqual(1,len(calls))
            with self.assertRaisesRegex(ValueError,'Unique pinned'):
                builder.prefetch_port_trees(tool,dict(overrides=[dict(name='fmt',version='99.0')]),out,run)
            with self.assertRaisesRegex(ValueError,'Safe pinned'):
                builder.prefetch_port_trees(tool,dict(overrides=[dict(name='../escape',version='1.0')]),out,run)

    def test_missing_extension_names_reject_replacement_and_presence_is_not_acceptance(self):
        absent=builder.winpty_eligibility(b'MZcontrolled original ABI')
        self.assertEqual('REJECTED_REQUIRED_NAMES_ABSENT',absent['status'])
        self.assertEqual(2,len(absent['missingRequiredNames']))
        present=builder.winpty_eligibility(b'winpty_get_console_process_list\0winpty_get_current_directory\0')
        self.assertEqual('ABI_RUNTIME_VALIDATION_REQUIRED',present['status'])
        self.assertFalse(present['productReplacementApproved'])

    def test_shared_toolchain_roots_survive_but_arbitrary_credentials_do_not(self):
        with patch.dict(os.environ,{'ProgramData':'C:/ProgramData','ALLUSERSPROFILE':'C:/ProgramData',
                                  'ComSpec':'C:/Windows/System32/cmd.exe','CommonProgramFiles':'C:/Program Files/Common Files',
                                  'SystemDrive':'C:','SYNTHETIC_PRIVATE_CREDENTIAL':'canary'},clear=True):
            env=builder.build_environment()
            self.assertEqual('C:/ProgramData',env['ProgramData'])
            self.assertEqual('C:/ProgramData',env['ALLUSERSPROFILE'])
            self.assertIn('ComSpec',env)
            self.assertIn('CommonProgramFiles',env)
            self.assertEqual('C:',env['SystemDrive'])
            self.assertNotIn('SYNTHETIC_PRIVATE_CREDENTIAL',env)

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
