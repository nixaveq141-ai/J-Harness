"""Check real archived input identity and refusal of partial or changed inputs."""
import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('fetch',ROOT/'scripts/fetch-p1-sparc-artifact.py')
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)


class InputIdentity(unittest.TestCase):
    def test_openbsd_archive_cannot_be_substituted_for_sparc(self):
        original=ROOT/'.build-cache/p1-openbsd-i386-inputs/14005682398.zip'
        if not original.exists():original=ROOT/'.build-cache/p1-raw-43351632149/14005682398.zip'
        m.verify(original,'openbsd-i386')
        with self.assertRaises(ValueError):m.verify(original,'sparc')

    def test_actual_original_and_changed_or_incomplete_copy(self):
        original=ROOT/'.build-cache/p1-sparc-jna-inputs/14003640994.zip'
        if not original.exists():original=ROOT/'.build-cache/p1-raw-43348439700/14003640994.zip'
        m.verify(original)
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            path=Path(folder)/'input.zip';path.write_bytes(b'partial')
            with self.assertRaises(ValueError):m.verify(path)
            shutil.copyfile(original,path)
            with path.open('r+b') as stream:stream.write(b'bad')
            with self.assertRaises(ValueError):m.verify(path)


if __name__=='__main__':unittest.main()
