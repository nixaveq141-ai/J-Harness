"""Reject architecture, endian and ARM float ABI drift before loading JNA."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('jna',Path(__file__).with_name('build-p1-linux-jna.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class TargetIdentityTest(unittest.TestCase):
    def test_exact_compiled_path_and_ant_class_mirror(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);native=root/'build/native-linux-x86/libjnidispatch.so'
            mirror=root/'build/classes/com/sun/jna/linux-x86/libjnidispatch.so'
            for p in [native,mirror]:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'compiled-exact')
            self.assertEqual(native,builder.compiled_library(root,'x86'))
            mirror.write_bytes(b'drift')
            with self.assertRaisesRegex(ValueError,'copies differ'):builder.compiled_library(root,'x86')

    def raw(self,width,endian,machine,flags=0):
        b=bytearray(64);b[:6]=b'\x7fELF'+bytes([width,endian]);order='<' if endian==1 else '>'
        struct.pack_into(order+'HH',b,16,3,machine);struct.pack_into(order+'I',b,36 if width==1 else 48,flags)
        return b

    def test_s390_and_ppc_endian_and_width(self):
        builder.require_elf(self.raw(2,2,22),'s390x')
        builder.require_elf(self.raw(1,2,20),'ppc')
        for raw,target in [(self.raw(2,1,22),'s390x'),(self.raw(2,2,20),'ppc'),(self.raw(2,1,62),'mips64el')]:
            with self.assertRaises(ValueError):builder.require_elf(raw,target)

    def test_arm_hard_and_soft_float_cannot_mix(self):
        hard=self.raw(1,1,40,0x5000400);soft=self.raw(1,1,40,0x5000200)
        builder.require_elf(hard,'arm');builder.require_elf(soft,'armel')
        with self.assertRaises(ValueError):builder.require_elf(hard,'armel')
        with self.assertRaises(ValueError):builder.require_elf(soft,'arm')


if __name__=='__main__':unittest.main()
