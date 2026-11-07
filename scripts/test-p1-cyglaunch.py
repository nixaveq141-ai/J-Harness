"""Reject unpinned setup bytes and any attempt to install outside isolated CI."""
import importlib.util,os,struct,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('builder',ROOT/'scripts/build-p1-cyglaunch.py')
builder=importlib.util.module_from_spec(s);s.loader.exec_module(builder)

class Boundaries(unittest.TestCase):
    def test_actual_caller_accepts_x64_and_rejects_x86_or_truncated_pe(self):
        raw=bytearray(128);raw[:2]=b'MZ';struct.pack_into('<I',raw,60,64);raw[64:68]=b'PE\0\0'
        struct.pack_into('<H',raw,68,0x8664);struct.pack_into('<H',raw,88,0x20b)
        self.assertEqual(0x8664,builder.require_x64_pe(bytes(raw))['machine'])
        struct.pack_into('<H',raw,68,0x14c);struct.pack_into('<H',raw,88,0x10b)
        with self.assertRaises(ValueError):builder.require_x64_pe(bytes(raw))
        with self.assertRaises(ValueError):builder.require_x64_pe(bytes(raw[:70]))

    def test_unreviewed_source_cannot_receive_portability_patch(self):
        with self.assertRaisesRegex(ValueError,'Exact fixed exec_pty source'):
            builder.guard_linux_syscall_header(b'#include <sys/syscall.h>\nUnreviewed code\n')

    def test_explicit_cygwin_tools_prevent_host_mingw_and_strawberry_selection(self):
        command=builder.configure_command('cyg-cmake','source','build','/usr/bin/gcc.exe','/usr/bin/g++.exe','/usr/bin/make.exe')
        for option in ['-DCMAKE_C_COMPILER=/usr/bin/gcc.exe','-DCMAKE_CXX_COMPILER=/usr/bin/g++.exe','-DCMAKE_MAKE_PROGRAM=/usr/bin/make.exe']:
            self.assertIn(option,command)

    def test_wrong_installer_rejected(self):
        for raw in [b'',b'MZunreviewed installer']:
            with self.assertRaisesRegex(ValueError,'official Cygwin setup'):builder.verify_setup(raw)

    def test_local_install_forbidden(self):
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'false'}):
            with self.assertRaisesRegex(ValueError,'Dedicated x64 Windows CI'):builder.main()

if __name__=='__main__':unittest.main()
