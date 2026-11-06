"""Bind native-only ARM64 configuration to fixed upstream source."""
import importlib.util,unittest
from pathlib import Path
s=importlib.util.spec_from_file_location('build',Path(__file__).with_name('build-p1-windows-arm64-winpty.py'));build=importlib.util.module_from_spec(s);s.loader.exec_module(build)
class ConfigTest(unittest.TestCase):
 def test_fixed_config(self):
  raw=build.native_config(build.REVISION);self.assertIn(b'MINGW_CXX=/clangarm64/bin/clang++\n',raw);self.assertIn(build.REVISION.encode(),raw)
  with self.assertRaises(ValueError):build.native_config('0'*40)
if __name__=='__main__':unittest.main()
