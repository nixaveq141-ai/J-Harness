"""Reject foreign ABI, unbound identities and unsafe guest archives."""
import hashlib
import importlib.util
import io
from pathlib import Path
import struct
import tarfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch


def module(name, path):
    spec=importlib.util.spec_from_file_location(name,path)
    loaded=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


ROOT=Path(__file__).resolve().parents[1]
builder=module('cross_jna',ROOT/'scripts/build-p1-sparc-cross-jna.py')
probe=module('guest_driver',ROOT/'scripts/probe-p1-sparc-toolchain.py')
guest=module('guest_components',ROOT/'scripts/probes/p1-sparc-guest-components.py')


class ComponentBounds(unittest.TestCase):
    def test_uninstalled_gcc_shared_runtime_is_rejected_before_guest_boot(self):
        libc=' 0x00000001 (NEEDED) Shared library: [libc.so.1]\n'
        self.assertEqual(['libc.so.1'],builder.require_runtime_dependencies(libc))
        with self.assertRaisesRegex(ValueError,'Solaris libc'):
            builder.require_runtime_dependencies('no parsed dependencies')
        for dependency in ['libgcc_s.so.1','libgcc_s.so']:
            with self.assertRaisesRegex(ValueError,'uninstalled GCC'):
                builder.require_runtime_dependencies(libc+' (NEEDED) Shared library: ['+dependency+']\n')

    def test_shared_library_requires_target_abi_and_shared_kind(self):
        for bits,machine in [(32,2),(32,18),(64,43)]:
            raw=bytearray(64)
            raw[:6]=b'\x7fELF'+bytes([1 if bits==32 else 2,2])
            struct.pack_into('>HH',raw,16,3,machine)
            self.assertEqual(machine,builder.require_elf(raw,bits,shared=True)['machine'])
            for other in [16,64 if bits==32 else 32]:
                with self.assertRaises(ValueError):builder.require_elf(raw,other,shared=True)
            for offset,value in [(5,1),(19,62),(17,2)]:
                changed=raw[:];changed[offset]=value
                with self.assertRaises(ValueError):builder.require_elf(changed,bits,shared=True)

    def test_guest_refuses_non_sparc_system_before_reading_bundle(self):
        with patch.object(guest.platform,'system',return_value='Linux'):
            with self.assertRaisesRegex(ValueError,'Actual Solaris SPARC'):guest.main()

    def test_guest_download_identity_is_bounded_and_generated_code_compiles(self):
        compile(probe.component_download_code('123','a'*40,'b'*64),'guest download','exec')
        for rid,commit,digest in [('123;false','a'*40,'b'*64),('123','../bad','b'*64),('123','a'*40,'b')]:
            with self.assertRaises(ValueError):probe.component_download_code(rid,commit,digest)

    def test_guest_download_rejects_foreign_path_or_link_before_extracting(self):
        expected=['manifest.json','guest-run.py','smoke-32','smoke-64']
        expected += [str(bits)+'-'+name for bits in [32,64] for name in ['libjnidispatch.so','jni-probe','callback-control']]
        for bad in ['../escape','link']:
            data=io.BytesIO()
            with tarfile.open(fileobj=data,mode='w:gz') as archive:
                for index,name in enumerate(expected):
                    item=tarfile.TarInfo('../escape' if bad=='../escape' and index==0 else name)
                    if bad=='link' and index==0:
                        item.type=tarfile.SYMTYPE;item.linkname='/outside'
                    archive.addfile(item)
            raw=data.getvalue()
            code=probe.component_download_code('123','a'*40,hashlib.sha256(raw).hexdigest())
            opener=SimpleNamespace(open=lambda *args,**kwargs:io.BytesIO(raw))
            with patch('urllib.request.build_opener',return_value=opener),patch.object(Path,'mkdir') as mkdir:
                with self.assertRaises(AssertionError):exec(compile(code,'guest download','exec'),{})
                mkdir.assert_not_called()


if __name__=='__main__':unittest.main()
