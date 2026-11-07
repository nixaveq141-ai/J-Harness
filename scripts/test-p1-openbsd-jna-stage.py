"""Run actual accepted JNA artifacts through staging and reject changed evidence."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('p1jna',ROOT/'scripts/stage-p1-openbsd-jna.py')
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)


class AcceptedJnaControl(unittest.TestCase):
    def test_original_one_and_changed_library(self):
        original=ROOT/'.build-cache/p1-openbsd-jna-inputs'
        self.assertEqual(1,len(stage.load_components(original)))
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copytree(original,root/'inputs')
            with (root/'inputs/p1-openbsd-x86-64-jna-source/libjnidispatch.so').open('ab') as stream:stream.write(b'drift')
            with self.assertRaisesRegex(ValueError,'bytes changed'):stage.load_components(root/'inputs')

    def test_changed_release_and_wrong_scope(self):
        original=ROOT/'.build-cache/p1-openbsd-jna-inputs'
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root=Path(folder);shutil.copytree(original,root/'inputs')
            with (root/'inputs/p1-openbsd-x86-64-jna-source/libffi-release-3.4.4.tar.gz').open('ab') as stream:stream.write(b'drift')
            with self.assertRaisesRegex(ValueError,'identity changed'):stage.load_components(root/'inputs')
        receipt=json.loads((ROOT/stage.RECEIPT).read_bytes());receipt['platforms'][0]['target']='x86'
        with self.assertRaisesRegex(ValueError,'one-component'):stage.load_components(original,receipt)


if __name__=='__main__':unittest.main()
