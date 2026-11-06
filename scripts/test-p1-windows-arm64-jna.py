"""Ensure emulated x64/x86 and truncated files cannot pass native ARM64 gates."""
import importlib.util
from pathlib import Path
import struct
import unittest
spec=importlib.util.spec_from_file_location('builder',Path(__file__).with_name('build-p1-windows-arm64-jna.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
class PeIdentityTest(unittest.TestCase):
 def pe(self,machine=0xaa64,magic=0x20b):
  raw=bytearray(128);raw[:2]=b'MZ';struct.pack_into('<I',raw,60,64);raw[64:68]=b'PE\0\0';struct.pack_into('<H',raw,68,machine);struct.pack_into('<H',raw,88,magic);return raw
 def test_native_only(self):
  self.assertEqual(builder.require_arm64_pe(self.pe())['machine'],0xaa64)
  for raw in [self.pe(0x8664),self.pe(0x14c,0x10b),self.pe(0xa641),self.pe(0xaa64,0x10b)]:
   with self.assertRaises(ValueError):builder.require_arm64_pe(raw)
 def test_bounds(self):
  for raw in [self.pe()[:90],b'MZ'+bytes(126)]:
   with self.assertRaises(ValueError):builder.require_arm64_pe(raw)
  raw=self.pe();struct.pack_into('<I',raw,60,2**32-1)
  with self.assertRaises(ValueError):builder.require_arm64_pe(raw)
if __name__=='__main__':unittest.main()
