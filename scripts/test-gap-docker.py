"""Offline oracle checks; no Docker or network required."""
import importlib.util
from pathlib import Path
import unittest
import sys
import tempfile
import json

spec = importlib.util.spec_from_file_location('gap_docker', Path(__file__).with_name('run-gap-docker.py'))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class DockerReportTest(unittest.TestCase):
    def test_old_generated_output_declaration_removes_inputs_before_child_entry(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'pom.xml').write_text('<project/>', encoding='utf-8')
            jar, compiled = root / 'runtime.jar', root / 'Probe.class'
            jar.write_bytes(b'jar'); compiled.write_bytes(b'class')
            with self.assertRaises(ValueError):
                driver.run([sys.executable, '-c', "from pathlib import Path; Path('runtime.jar').read_bytes(); Path('entered').touch()"],
                    name='old-output-inputs', root=root, cwd=root, log=root / 'log.txt',
                    receipt=root / 'receipt.json', run_id='offline', outputs=(jar, compiled))
            self.assertFalse(jar.exists()); self.assertFalse(compiled.exists())
            self.assertFalse((root / 'entered').exists())

    def test_actual_receipt_execution_preserves_jar_and_class_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'pom.xml').write_text('<project/>', encoding='utf-8')
            jar, compiled, result = root / 'runtime.jar', root / 'Probe.class', root / 'result.json'
            jar.write_bytes(b'jar-input'); compiled.write_bytes(b'class-input')
            receipt = driver.run_probe([sys.executable, '-c',
                "from pathlib import Path; assert Path('runtime.jar').read_bytes()==b'jar-input'; "
                "assert Path('Probe.class').read_bytes()==b'class-input'; Path('result.json').write_text('{}')"],
                input_files=(jar, compiled), name='read-inputs', root=root, cwd=root,
                log=root / 'log.txt', receipt=root / 'receipt.json', run_id='offline', outputs=(result,))
            self.assertEqual('PASS', receipt['status'])
            self.assertEqual(b'jar-input', jar.read_bytes())
            self.assertEqual(b'class-input', compiled.read_bytes())
            self.assertEqual(receipt['inputArtifactsSha256'], receipt['inputArtifactsAfterSha256'])
            self.assertEqual({'result.json'}, set(receipt['outputs']))
            with self.assertRaises(ValueError):
                driver.run_probe([], input_files=(jar,), outputs=(jar,), root=root)
            self.assertEqual(b'jar-input', jar.read_bytes())

    def test_changed_input_artifact_revokes_successful_command_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'pom.xml').write_text('<project/>', encoding='utf-8')
            jar = root / 'runtime.jar'; jar.write_bytes(b'original')
            with self.assertRaises(ValueError):
                driver.run_probe([sys.executable, '-c', "from pathlib import Path; Path('runtime.jar').write_bytes(b'changed')"],
                    input_files=(jar,), outputs=(), name='mutate-input', root=root, cwd=root,
                    log=root / 'log.txt', receipt=root / 'receipt.json', run_id='offline')
            self.assertEqual('INPUT_ARTIFACT_DRIFT', json.loads((root / 'receipt.json').read_bytes())['status'])

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
