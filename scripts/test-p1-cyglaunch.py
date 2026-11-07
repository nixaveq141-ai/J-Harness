"""Reject unpinned setup bytes and any attempt to install outside isolated CI."""
import importlib.util,os,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('builder',ROOT/'scripts/build-p1-cyglaunch.py')
builder=importlib.util.module_from_spec(s);s.loader.exec_module(builder)

class Boundaries(unittest.TestCase):
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
