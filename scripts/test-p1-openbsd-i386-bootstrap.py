"""Guard signed input scope and native i386 VM isolation before real CI boot."""
import importlib.util,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('bootstrap',ROOT/'scripts/bootstrap-p1-openbsd-i386.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)

class BootstrapScope(unittest.TestCase):
    def test_exact_signed_digest_and_duplicates(self):
        manifest='SHA256 (bsd.rd) = '+'a'*64+'\n'
        self.assertEqual('a'*64,m.manifest_digest(manifest,'bsd.rd'))
        for bad in ['',manifest+manifest,manifest.replace('bsd.rd','bsdXrd')]:
            with self.assertRaisesRegex(ValueError,'Unique signed'):m.manifest_digest(bad,'bsd.rd')

    def test_owned_i386_vm_uses_loopback_forward_no_host_mount(self):
        for install in [False,True]:
            args=m.qemu_arguments(Path('/owned/ci-work'),install)
            self.assertEqual('qemu-system-i386',args[0])
            self.assertIn('file=/owned/ci-work/disk.qcow2,format=qcow2,if=virtio',args)
            self.assertTrue(any('hostfwd=tcp:127.0.0.1:2222-:22' in a for a in args))
            self.assertFalse(any('virtfs' in a or '0.0.0.0' in a or 'mount' in a for a in args))
        self.assertTrue(any('bootfile=auto_install' in a for a in m.qemu_arguments(Path('/owned/ci-work'),True)))

if __name__=='__main__':unittest.main()
