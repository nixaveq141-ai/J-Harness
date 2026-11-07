"""Reject payload, bytecode and receipt drift in the accepted Console pair."""
import importlib.util,json,shutil,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-arm64-console.py');stage=importlib.util.module_from_spec(s);s.loader.exec_module(stage)
INPUT=ROOT/'.build-cache/p1-arm64-console-inputs'

class PairControls(unittest.TestCase):
    def test_exact_pair_and_changed_payload_or_proof(self):
        self.assertEqual(stage.RESOURCES,set(stage.load_components(INPUT)))
        for name in ['source/console/bin/ARM64/Release/OpenConsole.exe','source/console/bin/ARM64/Release/conpty.dll','probe/p1-arm64-console-probe/classes/Arm64ConPtySourceProbe.class','probe/p1-arm64-console-probe/present-probe-command.json']:
            with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as folder:
                target=Path(folder)/'input';shutil.copytree(INPUT,target)
                with (target/name).open('ab') as f:f.write(b'drift')
                with self.assertRaisesRegex(ValueError,'accepted bytes changed|output drift'):stage.load_components(target)

    def test_incomplete_resource_scope(self):
        a=json.loads((ROOT/stage.RECEIPT).read_bytes());a['outputs'].pop('resources/com/pty4j/native/win/aarch64/OpenConsole.exe')
        with self.assertRaisesRegex(ValueError,'pair acceptance'):stage.load_components(INPUT,a)

if __name__=='__main__':unittest.main()
