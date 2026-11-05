"""Exercise accepted raw evidence and reject DLL, receipt and scope substitution."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('win32stage',ROOT/'scripts/stage-p1-windows-x86-jna.py')
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)


class AcceptedWindowsX86(unittest.TestCase):
    def test_accepted_and_changed_evidence(self):
        original=ROOT/'.build-cache/p1-win32-inputs'
        self.assertEqual({stage.RESOURCE},set(stage.load_components(original)))
        for name in ['native-source-build/jna/build/native-win32-x86/jnidispatch.dll',
                     'p1-win32-probe/load-command.json',
                     'native-source-build/libffi-release-3.4.4.tar.gz']:
            with self.subTest(name=name),tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder)/'inputs';shutil.copytree(original,target)
                with (target/name).open('ab') as stream:stream.write(b'drift')
                with self.assertRaisesRegex(ValueError,'bytes changed'):stage.load_components(target)

    def test_scope_cannot_be_relabelled(self):
        receipt=json.loads((ROOT/stage.RECEIPT).read_bytes())
        receipt['resource']='com/sun/jna/win32-aarch64/jnidispatch.dll'
        with self.assertRaisesRegex(ValueError,'Exact accepted'):stage.load_components(ROOT/'.build-cache/p1-win32-inputs',receipt)


if __name__=='__main__':unittest.main()
