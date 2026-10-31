"""Reject missing, substituted and changed native-library load evidence."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('qualification',Path(__file__).with_name('source-native-qualification.py'))
qualification=importlib.util.module_from_spec(spec);spec.loader.exec_module(qualification)


class LoadEvidenceTest(unittest.TestCase):
    def test_exact_loaded_source_and_missing_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'jnidispatch.dll';path.write_bytes(b'source')
            digest=qualification.sha(b'source')
            log=('[0.1s][info][library] Loaded library '+str(path)+', handle 0x123\n').encode()
            self.assertEqual(['fork.log'],qualification.validate_loads({'fork.log':log},path,digest))
            for raw in [b'',b'Failed to load jnidispatch.dll',
                        log.replace(str(path).encode(),str(Path(folder)/'other/jnidispatch.dll').encode())]:
                with self.subTest(raw=raw),self.assertRaises(ValueError):
                    qualification.validate_loads({'fork.log':raw},path,digest)
            with self.assertRaises(ValueError):
                qualification.validate_loads({'fork.log':log,'other.log':log.replace(str(path).encode(),b'other/jnidispatch.dll')},path,digest)
            path.write_bytes(b'changed')
            with self.assertRaises(ValueError):qualification.validate_loads({'fork.log':log},path,digest)


if __name__=='__main__':unittest.main()
