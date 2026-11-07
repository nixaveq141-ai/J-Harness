"""Require the exact reviewed Console original report and both ARM64 payloads."""
import importlib.util,shutil,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('probe',ROOT/'scripts/probe-p1-windows-arm64-console.py')
probe=importlib.util.module_from_spec(s);s.loader.exec_module(probe)
INPUT=ROOT/'.build-cache/p1-console-input'

class InputsControl(unittest.TestCase):
    def test_exact_pair_and_changed_console_payloads(self):
        self.assertEqual(set(probe.OUTPUTS),set(probe.load_console(INPUT)))
        for name in probe.OUTPUTS:
            with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder)/'input';shutil.copytree(INPUT,target)
                with (target/'console/bin/ARM64/Release'/name).open('ab') as f:f.write(b'drift')
                with self.assertRaisesRegex(ValueError,'output drift'):probe.load_console(target)

    def test_changed_report_rejected(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
            target=Path(folder)/'input';shutil.copytree(INPUT,target)
            with (target/'report.json').open('ab') as f:f.write(b' ')
            with self.assertRaisesRegex(ValueError,'source report'):probe.load_console(target)

if __name__=='__main__':unittest.main()
