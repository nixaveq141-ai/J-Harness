"""Offline oracle checks; no Docker or network required."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('gap_docker', Path(__file__).with_name('run-gap-docker.py'))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class DockerReportTest(unittest.TestCase):
    def test_exact_image_jar_and_all_success_flags_are_required(self):
        good = dict(result='SUCCESS', actualDocker=True, cleanupVerified=True, jarUnchanged=True,
                    jarSha256='a' * 64, image=driver.IMAGE)
        driver.validate_report(good, 'a' * 64)
        for key, value in [('result', 'FAILED'), ('actualDocker', False), ('cleanupVerified', False),
                           ('jarUnchanged', False), ('jarSha256', 'b' * 64), ('image', 'latest')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                driver.validate_report({**good, key: value}, 'a' * 64)
        for key in good:
            with self.subTest(missing=key), self.assertRaises(ValueError):
                driver.validate_report({k: v for k, v in good.items() if k != key}, 'a' * 64)


if __name__ == '__main__':
    unittest.main()
