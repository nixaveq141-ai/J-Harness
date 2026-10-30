"""Reject stale Console source receipts, security bypass and native drift."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('loader',Path(__file__).with_name('load-console-source-candidate.py'))
loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)


class ConsoleCandidateTest(unittest.TestCase):
    def test_current_source_identity_and_negative_controls(self):
        builder=loader.module('pins','build-windows-pty-from-source.py')
        gate=loader.module('security','console-security-baseline.py')
        with tempfile.TemporaryDirectory() as temp,patch.object(loader,'ROOT',Path(temp)):
            root=Path(temp);source=root/'.build-cache/windows-pty-source';source.mkdir(parents=True)
            log=source/'build.log';log.write_bytes(b'controlled successful source compile')
            entries=[]
            for name in ['conpty.dll','OpenConsole.exe']:
                path=source/name;path.write_bytes(b'MZcontrolled '+name.encode())
                entries.append(dict(path=path.relative_to(root).as_posix(),bytes=path.stat().st_size,sha256=loader.sha(path)))
            report=dict(status='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE',component='console',commit='current',runId='owned',
                sources=dict(console=dict(revision=builder.PINS['console'][1],vcpkgBinaryCachePolicy='clear; source builds, no external binary cache'),
                             vcpkg=dict(revision=builder.PINS['vcpkg'][1])),
                enumeratedVendorSecurityGate=gate.validate(builder.PINS['console'][1],builder.CONSOLE_VERSION),outputs=entries,
                commands=[dict(argv=['MSBuild.exe',p],exitCode=0,log='build.log',logSha256=loader.sha(log)) for p in
                          ['src/host/exe/Host.EXE.vcxproj','src/winconpty/dll/winconptydll.vcxproj']])
            def check(value):
                (source/'report.json').write_bytes(json.dumps(value).encode())
                return loader.validate_console(source,'current','owned')
            self.assertEqual({'conpty.dll','OpenConsole.exe'},set(check(report)[1]))
            cases=[]
            r=copy.deepcopy(report);r['runId']='replayed';cases.append(r)
            r=copy.deepcopy(report);r['sources']['console']['revision']='6ffde34897e99a57a56ec52c12e1bf2730ceed71';cases.append(r)
            r=copy.deepcopy(report);r['sources']['console']['vcpkgBinaryCachePolicy']='readwrite';cases.append(r)
            r=copy.deepcopy(report);r['enumeratedVendorSecurityGate']['fullVulnerabilityCoverage']=True;cases.append(r)
            r=copy.deepcopy(report);r['outputs'][0]['sha256']='0'*64;cases.append(r)
            r=copy.deepcopy(report);r['commands'][0]['exitCode']=1;cases.append(r)
            r=copy.deepcopy(report);r['outputs'].append(r['outputs'][0]);cases.append(r)
            r=copy.deepcopy(report);r['commands']=r['commands'][:1];cases.append(r)
            for i,r in enumerate(cases):
                with self.subTest(case=i),self.assertRaises(ValueError):check(r)
            outside=root/'conpty.dll';outside.write_bytes(b'MZoutside')
            r=copy.deepcopy(report);r['outputs'][0]=dict(path='conpty.dll',bytes=outside.stat().st_size,sha256=loader.sha(outside))
            with self.assertRaisesRegex(ValueError,'boundary'):check(r)


if __name__=='__main__':unittest.main()
