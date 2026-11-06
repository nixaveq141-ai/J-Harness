"""Exercise real ARM64 component originals and reject drift and wrong resource scope."""
import importlib.util,json,shutil,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('stage',ROOT/'scripts/stage-p1-arm64-components.py');stage=importlib.util.module_from_spec(s);s.loader.exec_module(stage)
class Controls(unittest.TestCase):
 def test_originals_and_payload_receipt_drift(self):
  original=ROOT/'.build-cache/p1-arm64-inputs';self.assertEqual(2,len(stage.load_components(original)))
  for name in ['p1-windows-arm64-jna-source/jnidispatch.dll','p1-windows-arm64-helper-source/report.json','p1-windows-arm64-jna-source/libffi-release-3.4.4.tar.gz']:
   with tempfile.TemporaryDirectory(dir=ROOT/'.build-cache') as temp:
    dest=Path(temp)/'inputs';shutil.copytree(original,dest)
    with (dest/name).open('ab') as f:f.write(b'drift')
    with self.assertRaisesRegex(ValueError,'bytes changed'):stage.load_components(dest)
 def test_resource_and_target_scope(self):
  accepted={k:json.loads((ROOT/p).read_bytes()) for k,p in stage.receipts().items()};accepted['jna']['resource']=accepted['helper']['resource']
  with self.assertRaisesRegex(ValueError,'identity required'):stage.load_components(ROOT/'.build-cache/p1-arm64-inputs',accepted)
if __name__=='__main__':unittest.main()
