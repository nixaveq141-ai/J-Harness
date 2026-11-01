"""POSIX source platform scope and inventoried JNI assembly rejection checks."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result


stage=module('stage_posix','scripts/stage-posix-native-runtime.py')
assembler=module('assemble_posix','docs/testing/tools/assemble-native-preview.py')


class PosixStagingTest(unittest.TestCase):
    def test_supported_platforms_and_unreviewed_architecture_rejection(self):
        self.assertEqual(2,len(stage.select_resources('Linux','x86_64')[1]))
        self.assertEqual(3,len(stage.select_resources('Darwin','arm64')[1]))
        for system,arch in [('Linux','arm64'),('Darwin','x86_64'),('Windows','AMD64')]:
            with self.subTest(system=system,arch=arch),self.assertRaises(ValueError):
                stage.select_resources(system,arch)

    def test_inventoried_jnilib_accepted_and_drift_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);base=root/'base.jar';payload=root/'libjnidispatch.jnilib'
            name='com/sun/jna/darwin-aarch64/libjnidispatch.jnilib';payload.write_bytes(b'source-native')
            with zipfile.ZipFile(base,'x') as z:z.writestr(name,b'original');z.writestr('current.class',b'current')
            item={name:dict(path=payload,sha256=assembler.digest(payload.read_bytes()),origin=dict(runId='reviewed'))}
            inventory={name:assembler.digest(b'original')}
            assembler.assemble(base,root/'pass.jar',item,assembler.digest(base.read_bytes()),inventory,'test')
            with zipfile.ZipFile(root/'pass.jar') as z:self.assertEqual(b'current',z.read('current.class'))
            payload.write_bytes(b'changed')
            with self.assertRaises(ValueError):
                assembler.assemble(base,root/'bad.jar',item,assembler.digest(base.read_bytes()),inventory,'test')
            self.assertFalse((root/'bad.jar').exists())


if __name__=='__main__':unittest.main()
