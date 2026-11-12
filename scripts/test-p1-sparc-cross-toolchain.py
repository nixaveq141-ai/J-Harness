"""Reject sysroot escapes and wrong ABI before a cross toolchain can be used."""
import importlib.util
import io
import os
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('cross', Path(__file__).with_name('build-p1-sparc-cross-toolchain.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ToolchainInputs(unittest.TestCase):
    def archive(self, root, entries):
        archive = root / 'input.tar'
        with tarfile.open(archive, 'w') as out:
            for name, kind, data in entries:
                member = tarfile.TarInfo(name)
                if kind == 'link':
                    member.type = tarfile.SYMTYPE
                    member.linkname = data
                    out.addfile(member)
                else:
                    raw = data.encode()
                    member.size = len(raw)
                    out.addfile(member, io.BytesIO(raw))
        return archive

    def test_relocates_target_absolute_link_without_touching_host(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = self.archive(root, [('sysroot/lib/libc.so.1', 'file', 'owned'),
                                          ('sysroot/usr/lib/libc.so', 'link', '/lib/libc.so.1')])
            destination = root / 'sdk'
            if os.name == 'nt':
                # Linux CI below exercises the actual symlink and its resolved bytes.
                with patch.object(Path, 'symlink_to') as link:
                    manifest = m.extract_sysroot(archive, destination)
                    link.assert_called_once_with('../../lib/libc.so.1')
            else:
                manifest = m.extract_sysroot(archive, destination)
                self.assertEqual(b'owned', (destination / 'usr/lib/libc.so').read_bytes())
                self.assertTrue((destination / 'usr/lib/libc.so').resolve().is_relative_to(destination.resolve()))
            self.assertEqual('/lib/libc.so.1', manifest['usr/lib/libc.so']['originalLink'])

    def test_rejects_escape_duplicate_and_link_ancestor_before_writing(self):
        cases = [
            [('sysroot/../escape', 'file', 'bad')],
            [('/sysroot/escape', 'file', 'bad')],
            [('sysroot/lib/out', 'link', '../../outside')],
            [('sysroot/lib/x', 'file', 'first'), ('sysroot/lib/x', 'file', 'second')],
            [('sysroot/lib', 'link', '/elsewhere'), ('sysroot/lib/x', 'file', 'bad')],
        ]
        for entries in cases:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                archive = self.archive(root, entries)
                with self.assertRaises(ValueError):
                    m.extract_sysroot(archive, root / 'sdk')
                self.assertFalse((root / 'sdk').exists())

    def test_refuses_reused_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'sdk').mkdir()
            with self.assertRaises(ValueError):
                m.extract_sysroot(root / 'not-opened.tar', root / 'sdk')

    def test_elf_width_endian_machine_and_kind_are_checked(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / 'program'
            for bits, machine in [(32, 2), (32, 18), (64, 43)]:
                raw = bytearray(64)
                raw[:6] = b'\x7fELF' + bytes([1 if bits == 32 else 2, 2])
                struct.pack_into('>HH', raw, 16, 2, machine)
                binary.write_bytes(raw)
                self.assertEqual(machine, m.require_elf(binary, bits)['machine'])
                with self.assertRaises(ValueError):
                    m.require_elf(binary, 64 if bits == 32 else 32)
                for offset, value in [(5, 1), (19, 62), (17, 1)]:
                    changed = raw[:]
                    changed[offset] = value
                    binary.write_bytes(changed)
                    with self.assertRaises(ValueError):
                        m.require_elf(binary, bits)


if __name__ == '__main__':
    unittest.main()
