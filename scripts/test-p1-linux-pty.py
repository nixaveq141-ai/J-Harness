"""Wrong architecture and float ABI must fail even for valid ELF bytes."""
import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location('p1pty', Path(__file__).with_name('build-p1-linux-pty.py'))
builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(builder)


class IdentityTest(unittest.TestCase):
    def image(self, width, machine, flags=0):
        raw = bytearray(64); raw[:6] = b'\x7fELF'+bytes([width, 1])
        struct.pack_into('<HH', raw, 16, 3, machine)
        struct.pack_into('<I', raw, 36 if width == 1 else 48, flags)
        return raw

    def test_mislabeled_x64_cannot_be_mips(self):
        with self.assertRaisesRegex(ValueError, 'architecture'):
            builder.require_identity(self.image(2, 62), 'mips64el')
        self.assertEqual(8, builder.require_identity(self.image(2, 8), 'mips64el')['machine'])

    def test_arm_and_ppc_abi_and_truncation(self):
        with self.assertRaisesRegex(ValueError, 'hard-float'):
            builder.require_identity(self.image(1, 40), 'arm')
        builder.require_identity(self.image(1, 40, 0x5000400), 'arm')
        with self.assertRaisesRegex(ValueError, 'ELFv2'):
            builder.require_identity(self.image(2, 21, 1), 'ppc64le')
        builder.require_identity(self.image(2, 21, 2), 'ppc64le')
        with self.assertRaises(ValueError): builder.require_identity(b'\x7fELF', 'x86')


if __name__ == '__main__': unittest.main()
