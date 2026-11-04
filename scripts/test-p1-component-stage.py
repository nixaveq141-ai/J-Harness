"""Use the accepted original artifacts to check staging and tamper rejection."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('p1stage', ROOT/'scripts/stage-p1-linux-pty.py')
stage = importlib.util.module_from_spec(spec); spec.loader.exec_module(stage)


class AcceptedComponentControl(unittest.TestCase):
    def test_original_five_and_changed_payload_refused(self):
        original = ROOT/'.build-cache/p1-inputs'
        self.assertEqual(5, len(stage.load_components(original)))
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root = Path(folder); shutil.copytree(original, root/'inputs')
            payload = root/'inputs/p1-linux-pty-mips64el/libpty.so'
            with payload.open('ab') as stream: stream.write(b'drift')
            with self.assertRaisesRegex(ValueError, 'bytes changed'):
                stage.load_components(root/'inputs')

    def test_changed_command_and_scope_refused(self):
        original = ROOT/'.build-cache/p1-inputs'
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            root = Path(folder); shutil.copytree(original, root/'inputs')
            path = root/'inputs/p1-linux-pty-arm/candidate-probe-command.json'
            data = json.loads(path.read_bytes()); data['exitCode'] = 1
            path.write_text(json.dumps(data), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'receipt drift'):
                stage.load_components(root/'inputs')
        receipt = json.loads((ROOT/stage.RECEIPT).read_bytes())
        receipt['platforms'][0]['target'] = receipt['platforms'][1]['target']
        with self.assertRaisesRegex(ValueError, 'five-component'):
            stage.load_components(original, receipt)


if __name__ == '__main__': unittest.main()
