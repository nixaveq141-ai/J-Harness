"""Require exact Cygwin payload and both runtime evidence identities."""
import importlib.util,json,shutil,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-cyglaunch.py');stage=importlib.util.module_from_spec(s);s.loader.exec_module(stage)
INPUT=ROOT/'.build-cache/p1-cyg-inputs'
class Controls(unittest.TestCase):
    def test_actual_component_and_payload_or_no_log_evidence_drift(self):
        self.assertEqual({stage.RESOURCE},set(stage.load_components(INPUT)))
        for name in ['cyglaunch.exe','candidate-no-log-probe-command.json','probe.exe']:
            with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder)/'inputs';shutil.copytree(INPUT,target)
                with (target/'p1-cygwin-x64-launcher-source'/name).open('ab') as f:f.write(b'drift')
                with self.assertRaisesRegex(ValueError,'accepted bytes changed'):stage.load_components(target)
    def test_wrong_target_scope(self):
        a=json.loads((ROOT/stage.RECEIPT).read_bytes());a['resource']='resources/com/pty4j/native/win/aarch64/cyglaunch.exe'
        with self.assertRaisesRegex(ValueError,'component acceptance'):stage.load_components(INPUT,a)
if __name__=='__main__':unittest.main()
