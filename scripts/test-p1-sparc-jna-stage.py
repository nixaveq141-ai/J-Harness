"""Exercise accepted original SPARC bytes and reject artifact/output/scope drift."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-sparc-jna.py')
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
ORIGINAL=ROOT/'.build-cache/p1-sparc-jna-inputs'
if not (ORIGINAL/'14003640994.zip').exists():ORIGINAL=ROOT/'.build-cache/p1-raw-43348439700'


class AcceptedComponents(unittest.TestCase):
    def test_actual_original_and_changed_staged_library(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copyfile(ORIGINAL/'14003640994.zip',root/'14003640994.zip')
            self.assertEqual(2,len(m.load_components(root)))
            (root/'verified-sparc.so').write_bytes(b'drift')
            with self.assertRaisesRegex(ValueError,'output drift'):m.load_components(root)

    def test_changed_artifact_and_duplicate_scope(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copyfile(ORIGINAL/'14003640994.zip',root/'14003640994.zip')
            with (root/'14003640994.zip').open('ab') as out:out.write(b'drift')
            with self.assertRaisesRegex(ValueError,'original SPARC'):m.load_components(root)
        accepted=json.loads((ROOT/m.RECEIPT).read_bytes())
        accepted['platforms'][0]['target']=accepted['platforms'][1]['target']
        with self.assertRaisesRegex(ValueError,'two-target'):m.load_components(ORIGINAL,accepted)


if __name__=='__main__':unittest.main()
