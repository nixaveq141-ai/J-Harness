"""Guard signed input scope and native i386 VM isolation before real CI boot."""
import importlib.util,io,unittest,fnmatch
from unittest.mock import patch
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('bootstrap',ROOT/'scripts/bootstrap-p1-openbsd-i386.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)

class BootstrapScope(unittest.TestCase):
    def test_installer_default_sets_are_explicitly_unselected_when_not_served(self):
        selected={'bsd','bsd.mp','bsd.rd','base79.tgz','comp79.tgz','man79.tgz','game79.tgz','xbase79.tgz','xshare79.tgz','xfont79.tgz','xserv79.tgz'}
        for option in m.SET_SELECTION.split():
            self.assertTrue(option.startswith('-'))
            selected={n for n in selected if not fnmatch.fnmatchcase(n,option[1:])}
        self.assertTrue(selected<=set(m.FILES))
        self.assertTrue({'base79.tgz','comp79.tgz','xbase79.tgz','xshare79.tgz'}<=selected)

    def test_installer_wait_distinguishes_terminal_from_stalled_live_vm(self):
        class Vm:
            def __init__(self,status):self.status=status
            def poll(self):return self.status
        class Serial:
            def __init__(self,data=b''):self.data=data
            def stat(self):return type('Stat',(),{'st_size':len(self.data)})()
            def open(self,mode):return io.BytesIO(self.data)
        self.assertEqual(0,m.wait_installation(Vm(0),Serial()))
        self.assertEqual(9,m.wait_installation(Vm(9),Serial()))
        with patch.object(m.time,'monotonic',side_effect=[0,181]),self.assertRaisesRegex(ValueError,'no progress'):
            m.wait_installation(Vm(None),Serial())
        with self.assertRaisesRegex(ValueError,'reported failure'):
            m.wait_installation(Vm(None),Serial(b'failed; check /tmp/ai/ai.log'))

    def test_official_base_size_fits_and_stream_budget_stays_enforced(self):
        self.assertGreaterEqual(m.INPUT_LIMITS['base79.tgz'],282575165)
        out=io.BytesIO();self.assertEqual(4,m.copy_bounded(io.BytesIO(b'1234'),out,4))
        self.assertEqual(b'1234',out.getvalue())
        with self.assertRaisesRegex(ValueError,'Bounded official'):m.copy_bounded(io.BytesIO(b'12345'),io.BytesIO(),4)

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
