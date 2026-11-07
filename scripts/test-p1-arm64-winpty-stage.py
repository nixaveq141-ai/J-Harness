"""Accepted ARM64 DLL-agent pair must survive evidence and scope drift controls."""
import importlib.util,json,shutil,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-arm64-winpty.py')
stage=importlib.util.module_from_spec(s);s.loader.exec_module(stage)
INPUT=ROOT/'.build-cache/p1-arm64-winpty-inputs'

class PairControls(unittest.TestCase):
    def test_actual_pair_and_each_payload_drift(self):
        self.assertEqual(stage.RESOURCES,set(stage.load_components(INPUT)))
        for name in ['winpty.dll','winpty-agent.exe','candidate-probe-command.json','probe.exe']:
            with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder);shutil.copytree(INPUT,target/'inputs')
                with (target/'inputs/p1-windows-arm64-winpty-source'/name).open('ab') as f:f.write(b'drift')
                with self.assertRaisesRegex(ValueError,'accepted bytes changed'):stage.load_components(target/'inputs')

    def test_missing_agent_scope_rejected(self):
        a=json.loads((ROOT/stage.RECEIPT).read_bytes());a['outputs'].pop('resources/com/pty4j/native/win/aarch64/winpty-agent.exe')
        with self.assertRaisesRegex(ValueError,'pair acceptance'):stage.load_components(INPUT,a)

if __name__=='__main__':unittest.main()
