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
