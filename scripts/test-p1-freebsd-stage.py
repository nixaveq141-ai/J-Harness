"""Exercise actual FreeBSD inputs and reject library and receipt substitution."""
from pathlib import Path
import importlib.util
import shutil
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-freebsd-pty.py')
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)
class FreeBSDInputs(unittest.TestCase):
    def test_actual_inputs_and_tampering(self):
        original=ROOT/'.build-cache/p1-freebsd-inputs'
        self.assertEqual(2,len(stage.load_components(original)))
        for name in ['p1-freebsd-x86-pty-source/libpty.so','p1-freebsd-x86-64-pty-source/candidate-probe-command.json','p1-freebsd-x86-pty-source/original.so']:
            with self.subTest(name=name),tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                dest=Path(folder)/'inputs';shutil.copytree(original,dest)
                with (dest/name).open('ab') as stream:stream.write(b'drift')
                with self.assertRaisesRegex(ValueError,'bytes changed'):stage.load_components(dest)
if __name__=='__main__':unittest.main()
