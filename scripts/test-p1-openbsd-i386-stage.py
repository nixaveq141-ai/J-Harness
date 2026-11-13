"""Validate real reviewed bytes and reject changed artifacts and foreign scope."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-openbsd-i386-jna.py')
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
ORIGINAL=ROOT/'.build-cache/p1-openbsd-i386-inputs/14005682398.zip'
if not ORIGINAL.exists():ORIGINAL=ROOT/'.build-cache/p1-raw-43351632149/14005682398.zip'


class AcceptedInput(unittest.TestCase):
    def test_real_original_and_changed_output(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copyfile(ORIGINAL,root/ORIGINAL.name)
            self.assertEqual({m.RESOURCE},set(m.load_components(root)))
            (root/'verified-openbsd-i386.so').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'output drift'):m.load_components(root)

    def test_changed_archive_and_wrong_scope(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copyfile(ORIGINAL,root/ORIGINAL.name)
            with (root/ORIGINAL.name).open('ab') as out:out.write(b'drift')
            with self.assertRaisesRegex(ValueError,'original OpenBSD'):m.load_components(root)
        a=json.loads((ROOT/m.RECEIPT).read_bytes());a['platforms'][0]['target']='x86-64'
        with self.assertRaisesRegex(ValueError,'resource scope'):m.load_components(ORIGINAL.parent,a)


if __name__=='__main__':unittest.main()
