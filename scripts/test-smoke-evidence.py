"""Offline candidate parsing and stale-success regressions; never launch Java/browser."""
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
import warnings
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('smoke', Path(__file__).with_name('smoke-candidate.py'))
smoke = importlib.util.module_from_spec(spec); spec.loader.exec_module(smoke)
launcher_spec = importlib.util.spec_from_file_location('launcher', Path(__file__).with_name('test-open-workbench.py'))
launcher = importlib.util.module_from_spec(launcher_spec); launcher_spec.loader.exec_module(launcher)
LEGAL = {'LICENSE', 'NOTICE', 'third-party/inventory.json', 'third-party/upstream/sources.json'}


def zip_bytes(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            for name, data in entries:
                if isinstance(name, str):
                    entry = zipfile.ZipInfo('placeholder')
                    entry.filename = name  # Preserve malicious separators instead of host normalization.
                    entry.compress_type = zipfile.ZIP_DEFLATED
                else:
                    entry = name
                archive.writestr(entry, data)
    return out.getvalue()


def candidate_files():
    names = LEGAL | {'bom.json', 'osv-results.json', 'sdk-bom.json', 'sdk-source-inventory.json',
                     'frontend-bom.json', 'jharness', 'jharness.cmd', 'README.md',
                     'docs/examples/deepseek-config.json', 'docs/examples/m7-cli.md'}
    files = {n: ('synthetic:' + n).encode() for n in names}
    files['lib/jharness.jar'] = zip_bytes([('META-INF/jharness/' + n, files[n]) for n in LEGAL if n.startswith('third-party/')])
    return files


def candidate(files=None, change_manifest=None, extra=()):
    files = candidate_files() if files is None else files
    manifest = {'files': {n: hashlib.sha256(raw).hexdigest() for n, raw in files.items()}}
    if change_manifest: change_manifest(manifest)
    entries = [('jharness/' + n, raw) for n, raw in files.items()]
    entries.append(('jharness/manifest.json', json.dumps(manifest).encode()))
    return zip_bytes(entries + list(extra))


class CandidateParsingTest(unittest.TestCase):
    def test_complete_synthetic_candidate_and_exact_manifest_pass(self):
        manifest, files = smoke.validate_candidate(candidate(), LEGAL)
        self.assertEqual(set(files) - {'manifest.json'}, set(manifest['files']))
        self.assertEqual(LEGAL, LEGAL & files.keys())

    def test_manifest_missing_or_extra_file_is_rejected(self):
        for change in [lambda m:m['files'].pop('NOTICE'), lambda m:m['files'].update({'missing.txt':'a'*64})]:
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'exactly cover files'):
                smoke.validate_candidate(candidate(change_manifest=change), LEGAL)
        with self.assertRaisesRegex(ValueError, 'exactly cover files'):
            smoke.validate_candidate(candidate(extra=[('jharness/undeclared.txt',b'undeclared')]),LEGAL)

    def test_legal_set_cannot_be_empty_or_omitted(self):
        with self.assertRaisesRegex(ValueError,'Incomplete required legal set'):smoke.validate_candidate(candidate(),set())
        files=candidate_files();files.pop('third-party/inventory.json')
        with self.assertRaisesRegex(ValueError,'Missing required candidate or legal files'):smoke.validate_candidate(candidate(files),LEGAL)

    def test_packaged_legal_files_are_required_independently(self):
        files=candidate_files();files['lib/jharness.jar']=zip_bytes([('something',b'no legal files')])
        with self.assertRaisesRegex(ValueError,'Legal file not preserved'):smoke.validate_candidate(candidate(files),LEGAL)

    def test_one_field_hash_tampering_rejects(self):
        with self.assertRaisesRegex(ValueError,'Candidate file hash mismatch: NOTICE'):
            smoke.validate_candidate(candidate(change_manifest=lambda m:m['files'].update(NOTICE='a'*64)),LEGAL)

    def test_member_paths_reject_traversal_unc_ads_and_case_collision(self):
        for name in ['../outside','jharness/../outside','/jharness/evil','C:/outside','//server/share','jharness/x:ads','jharness/a\\b','jharness/NUL.txt','jharness/a./x']:
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'Unsafe candidate path'):
                smoke.validate_candidate(candidate(extra=[(name,b'bad')]),LEGAL)
        for name in ['jharness/NOTICE','jharness/notice']:
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'Duplicate candidate member'):
                smoke.validate_candidate(candidate(extra=[(name,b'duplicate')]),LEGAL)

    def test_manifest_paths_are_checked_before_file_access(self):
        for name in ['../outside','C:/outside','//server/share','x:ads']:
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'Unsafe candidate path'):
                smoke.validate_candidate(candidate(change_manifest=lambda m:m['files'].update({name:'a'*64})),LEGAL)

    def test_zip_links_are_rejected(self):
        entry=zipfile.ZipInfo('jharness/link');entry.create_system=3;entry.external_attr=(stat.S_IFLNK|0o777)<<16
        with self.assertRaisesRegex(ValueError,'Linked or unsupported'):
            smoke.validate_candidate(candidate(extra=[(entry,b'outside')]),LEGAL)

    def test_bounded_input_and_expansion_reject(self):
        raw=candidate()
        with patch.object(smoke,'MAX_ZIP_BYTES',len(raw)-1),self.assertRaisesRegex(ValueError,'ZIP byte limit'):
            smoke.validate_candidate(raw,LEGAL)
        with patch.object(smoke,'MAX_EXPANDED_BYTES',1),self.assertRaisesRegex(ValueError,'expanded size'):
            smoke.validate_candidate(raw,LEGAL)

    def test_empty_manifest_rejects(self):
        with self.assertRaisesRegex(ValueError,'Empty candidate manifest files'):
            smoke.validate_candidate(candidate(change_manifest=lambda m:m.update(files={})),LEGAL)


class ReceiptLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);(self.root/'target').mkdir();(self.root/'.build-cache').mkdir()
        self.output=self.root/'target/candidate-smoke.json'
        self.output.write_text('{"status":"PASS"}')
        self.launcher_output=self.root/'.build-cache/M10-launcher-results.json'
        self.launcher_output.write_text('{"status":"PASS"}')

    def test_missing_zip_clears_old_success_before_read(self):
        with patch.object(smoke,'ROOT',self.root),self.assertRaises(FileNotFoundError):smoke.main()
        report=json.loads(self.output.read_bytes())
        self.assertEqual('INCOMPLETE',report['status']);self.assertEqual('FileNotFoundError',report['failureType'])

    def test_server_start_failure_keeps_incomplete_receipt(self):
        (self.root/'target/jharness-0.1.0-SNAPSHOT-candidate.zip').write_bytes(candidate())
        with patch.object(smoke,'ROOT',self.root),patch.object(smoke,'required_legal_files',return_value=LEGAL),patch.object(smoke.http.server,'ThreadingHTTPServer',side_effect=OSError('synthetic bind failure')):
            with self.assertRaisesRegex(OSError,'synthetic bind failure'):smoke.main()
        self.assertEqual('INCOMPLETE',json.loads(self.output.read_bytes())['status'])

    def test_optimized_invocation_clears_success_and_refuses_without_launch(self):
        for filename,relative in [('smoke-candidate.py','target/candidate-smoke.json'),('test-open-workbench.py','.build-cache/M10-launcher-results.json')]:
            path=Path(__file__).with_name(filename).resolve()
            code=("import importlib.util,sys;from pathlib import Path;"
                  "sys.path.insert(0,"+repr(str(path.parent))+");"
                  "s=importlib.util.spec_from_file_location('tested',"+repr(str(path))+");"
                  "m=importlib.util.module_from_spec(s);s.loader.exec_module(m);"
                  "m.ROOT=Path("+repr(str(self.root))+");m.main()")
            result=subprocess.run([sys.executable,'-O','-c',code],capture_output=True,text=True,timeout=15)
            with self.subTest(filename=filename):
                self.assertNotEqual(0,result.returncode)
                self.assertIn('do not use -O',result.stderr)
                self.assertEqual('INCOMPLETE',json.loads((self.root/relative).read_bytes())['status'])

    def test_launcher_start_failure_clears_success(self):
        with patch.object(launcher,'ROOT',self.root),patch.object(launcher,'exercise',side_effect=OSError('synthetic start failure')):
            with self.assertRaisesRegex(OSError,'synthetic start failure'):launcher.main()
        self.assertEqual('INCOMPLETE',json.loads(self.launcher_output.read_bytes())['status'])

    def test_zip_hash_uses_validated_snapshot_when_original_changes(self):
        path=self.root/'target/jharness-0.1.0-SNAPSHOT-candidate.zip';raw=candidate();path.write_bytes(raw)
        validate=smoke.validate_candidate
        def change_after_validation(data,legal):
            result=validate(data,legal);path.write_bytes(b'changed after snapshot');return result
        def finish(report,*args):report['status']='PASS'
        with patch.object(smoke,'ROOT',self.root),patch.object(smoke,'required_legal_files',return_value=LEGAL),patch.object(smoke,'validate_candidate',side_effect=change_after_validation),patch.object(smoke,'exercise',side_effect=finish),patch('builtins.print'):
            smoke.main()
        report=json.loads(self.output.read_bytes())
        self.assertEqual(hashlib.sha256(raw).hexdigest(),report['zipSha256'])
        self.assertNotEqual(hashlib.sha256(path.read_bytes()).hexdigest(),report['zipSha256'])


if __name__=='__main__':unittest.main()
