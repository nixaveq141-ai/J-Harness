import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('runtime',Path(__file__).with_name('run-runtime-oval.py'))
runtime=importlib.util.module_from_spec(spec);spec.loader.exec_module(runtime)


class RuntimeImageTest(unittest.TestCase):
    def test_mutable_or_wrong_architecture_image_is_rejected(self):
        locked=dict(architecture='arm64',digest='sha256:'+'a'*64)
        good=dict(Architecture='arm64',Os='linux',RepoDigests=['eclipse-temurin@'+locked['digest']])
        runtime.verify_image(good,locked)
        for bad in [dict(good,Architecture='amd64'),dict(good,RepoDigests=['eclipse-temurin:21']),dict(good,Os='windows')]:
            with self.assertRaises(ValueError):runtime.verify_image(bad,locked)


if __name__=='__main__':unittest.main()
