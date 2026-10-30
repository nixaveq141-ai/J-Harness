"""Offline failure ownership and source pin checks; no compilers or network."""
import importlib.util
import io
import hashlib
import json
import os
from pathlib import Path
import tempfile
import tarfile
import types
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('builder',Path(__file__).with_name('build-native-from-source.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class SourceBuildTest(unittest.TestCase):
    def test_cfi_backport_moves_only_three_directives_and_rejects_unknown_source(self):
        raw=b''.join(b'\tcfi_startproc\nCNAME('+s+b'):\n\tunchanged_instruction\n' for s in [b'ffi_call_SYSV',b'ffi_closure_SYSV',b'ffi_go_closure_SYSV'])
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'sysv.S';path.write_bytes(raw)
            value=builder.backport_aarch64_cfi(path)
            self.assertEqual('8308bed5b2423878aa20d7884a99cf2e30b8daf7',value['upstreamCommit'])
            self.assertEqual(3,path.read_bytes().count(b'unchanged_instruction'))
            once=path.read_bytes()
            with self.assertRaises(ValueError):builder.backport_aarch64_cfi(path)
            self.assertEqual(once,path.read_bytes())

    def test_generated_release_files_cannot_overwrite_or_mismatch_source(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);jna=root/'jna';source=jna/'native/libffi';source.mkdir(parents=True)
            files={n:b'pinned input\n' for n in ['configure.ac','Makefile.am','include/Makefile.am','doc/Makefile.am','testsuite/Makefile.am','man/Makefile.am']}
            files.update({'configure':b'generated configure','Makefile.in':b'generated make','src/ffi.c':b'release C'})
            archive=io.BytesIO()
            with tarfile.open(fileobj=archive,mode='w:gz') as stream:
                for name,data in files.items():
                    item=tarfile.TarInfo('libffi-3.4.4/'+name);item.size=len(data);stream.addfile(item,io.BytesIO(data))
            raw=archive.getvalue()
            for name,data in files.items():
                if name.endswith('Makefile.am') or name=='configure.ac':
                    p=source/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
            sentinel=source/'src/ffi.c';sentinel.parent.mkdir();sentinel.write_bytes(b'vendored C remains unchanged')
            with patch.object(builder,'FFI_BYTES',len(raw)),patch.object(builder,'FFI_SHA',hashlib.sha256(raw).hexdigest()):
                with self.assertRaisesRegex(ValueError,'release mismatch'):builder.prepare_libffi(jna,root,lambda _:b'tampered')
                (source/'configure.ac').write_bytes(b'changed input')
                with self.assertRaisesRegex(ValueError,'differs'):builder.prepare_libffi(jna,root,lambda _:raw)
                self.assertFalse((source/'configure').exists())
                (source/'configure.ac').write_bytes(files['configure.ac'])
                result=builder.prepare_libffi(jna,root,lambda _:raw)
                self.assertEqual(6,len(result['matchedAutotoolsInputs']))
                self.assertEqual(b'vendored C remains unchanged',sentinel.read_bytes())
                self.assertEqual(files['configure'],(source/'configure').read_bytes())
                self.assertGreaterEqual((source/'configure').stat().st_mtime_ns,(source/'configure.ac').stat().st_mtime_ns)
                self.assertEqual((source/'configure').stat().st_mtime_ns,(source/'Makefile.in').stat().st_mtime_ns)

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
