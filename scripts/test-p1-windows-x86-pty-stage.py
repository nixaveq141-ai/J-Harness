"""Reject a mismatched DLL/agent pair or altered source/probe evidence."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('pty_stage',ROOT/'scripts/stage-p1-windows-x86-pty.py')
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)


class AcceptedPair(unittest.TestCase):
    def test_actual_pair_and_tampering(self):
        original=ROOT/'.build-cache/p1-win32-pty-inputs'
        self.assertEqual(2,len(stage.load_components(original)))
        for name in ['windows-pty-source/winpty/build/winpty.dll',
                     'windows-pty-source/winpty/build/winpty-agent.exe',
                     'p1-win32-pty-probe/load-command.json']:
            with self.subTest(name=name),tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder)/'inputs';shutil.copytree(original,target)
                with (target/name).open('ab') as stream:stream.write(b'drift')
                with self.assertRaisesRegex(ValueError,'bytes changed'):stage.load_components(target)

    def test_pair_cannot_drop_agent(self):
        receipt=json.loads((ROOT/stage.RECEIPT).read_bytes());receipt['outputs'].pop('.build-cache/windows-pty-source/winpty/build/winpty-agent.exe')
        with self.assertRaisesRegex(ValueError,'Exact paired'):stage.load_components(ROOT/'.build-cache/p1-win32-pty-inputs',receipt)


if __name__=='__main__':unittest.main()
