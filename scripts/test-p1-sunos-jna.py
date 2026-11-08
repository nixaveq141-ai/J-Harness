"""Reject Solaris architecture and Ant output substitution before component loading."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('jna',Path(__file__).with_name('build-p1-sunos-jna.py'))
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class TargetIdentityTest(unittest.TestCase):
    def test_native_target_family_is_not_substituted(self):
        for target in builder.TARGETS:
            sparc=target in {'sparc','sparcv9'}
            builder.require_host(target,'SunOS','sun4v' if sparc else 'i86pc')
            for system,machine in [('Linux','sun4v'),('SunOS','i86pc' if sparc else 'sun4v')]:
                with self.assertRaises(ValueError):builder.require_host(target,system,machine)

    def test_exact_compiled_path_and_ant_class_mirror(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);native=root/'build/native-sunos-x86/libjnidispatch.so'
            mirror=root/'build/classes/com/sun/jna/sunos-x86/libjnidispatch.so'
            for p in [native,mirror]:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'compiled-exact')
            self.assertEqual(native,builder.compiled_library(root,'x86'))
            mirror.write_bytes(b'drift')
            with self.assertRaisesRegex(ValueError,'copies differ'):builder.compiled_library(root,'x86')

    def raw(self,width,endian,machine,flags=0):
        b=bytearray(64);b[:6]=b'\x7fELF'+bytes([width,endian]);order='<' if endian==1 else '>'
        struct.pack_into(order+'HH',b,16,3,machine);struct.pack_into(order+'I',b,36 if width==1 else 48,flags)
        return b

    def test_target_width_endian_and_machine(self):
        builder.require_elf(self.raw(1,1,3),'x86')
        builder.require_elf(self.raw(2,1,62),'x86-64')
        builder.require_elf(self.raw(1,2,2),'sparc')
        builder.require_elf(self.raw(2,2,43),'sparcv9')
        for raw,target in [(self.raw(2,1,62),'x86'),(self.raw(1,1,3),'x86-64'),(self.raw(2,2,62),'x86-64'),
                           (self.raw(2,2,43),'sparc'),(self.raw(1,2,2),'sparcv9'),(self.raw(2,1,43),'sparcv9'),
                           (self.raw(2,2,62),'sparcv9')]:
            with self.assertRaises(ValueError):builder.require_elf(raw,target)


if __name__=='__main__':unittest.main()
