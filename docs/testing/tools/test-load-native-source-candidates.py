"""Offline source identity, path and hash counterexamples; no library loading."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('loader',Path(__file__).with_name('load-native-source-candidates.py'))
loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)


class CandidateTest(unittest.TestCase):
    def test_original_mac_controls_require_inventory_bytes_and_preserve_identity(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);inventory=root/'docs/testing/M10-native-inventory-48-2026-10-30.json'
            inventory.parent.mkdir(parents=True);jar=root/'fixture.jar'
            names=['com/sun/jna/darwin-aarch64/libjnidispatch.jnilib',
                'resources/com/pty4j/native/darwin/libpty.dylib',
                'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper']
            rows=[]
            with zipfile.ZipFile(jar,'w') as archive:
                for i,name in enumerate(names):
                    raw=('controlled native fixture '+str(i)).encode();archive.writestr(name,raw)
                    rows.append(dict(resource=name,sha256=hashlib.sha256(raw).hexdigest()))
            inventory.write_text(json.dumps(dict(binaries=rows)),encoding='utf-8')
            with patch.object(loader,'ROOT',root),patch.object(loader.platform,'machine',return_value='arm64'):
                jni,pty=loader.original_mac_controls(jar,root/'good')
                self.assertEqual(rows[0]['sha256'],loader.sha(jni/'libjnidispatch.jnilib'))
                self.assertEqual(rows[2]['sha256'],loader.sha(pty/'darwin/pty4j-unix-spawn-helper'))
                rows[2]['sha256']='0'*64
                inventory.write_text(json.dumps(dict(binaries=rows)),encoding='utf-8')
                with self.assertRaisesRegex(ValueError,'inventory mismatch'):
                    loader.original_mac_controls(jar,root/'rejected')

    def test_source_command_identity_path_and_bytes_are_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);log=root/'1.log';log.write_bytes(b'controlled compile receipt')
            binary=root/'jna/build/native/libjnidispatch.so';binary.parent.mkdir(parents=True);binary.write_bytes(b'not executed')
            report=dict(status='SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE',runId='40995618881',commit=loader.SOURCE_RUNS['40995618881'],
                sources=dict(jna=dict(revision='c366bc4fd0c50a30ac8d2475603ec564f6611462',libffiBuildInputs=dict(sha256='d66c56ad259a82cf2a9dfc408b32bf5da52371500b84745f7fb8b645712df676')),
                             pty4j=dict(revision='b7554d649f6374183040e6554942b6d5b028b5e5')),
                commands=[dict(argv=['ant','-Dbuild-native=true','native'],exitCode=0,log='1.log',logSha256=loader.sha(log))],
                outputs=dict(jna=[dict(path='.build-cache/native-source-build/jna/build/native/libjnidispatch.so',sha256=loader.sha(binary))]))
            path=root/'report.json'
            path.write_text(json.dumps(report),encoding='utf-8');loader.validate(root,'40995618881')
            for mutate in [lambda r:r.update(commit='wrong'),lambda r:r.update(status='INCOMPLETE'),
                           lambda r:r.update(commands=[]),lambda r:r['commands'][0].update(exitCode=1),
                           lambda r:r.update(os='macOS-controlled-missing-helper'),
                           lambda r:r['outputs']['jna'][0].update(path='.build-cache/native-source-build/../../escape.so'),
                           lambda r:r['outputs']['jna'][0].update(sha256='0'*64)]:
                bad=copy.deepcopy(report);mutate(bad);path.write_text(json.dumps(bad),encoding='utf-8')
                with self.assertRaises(ValueError):loader.validate(root,'40995618881')
            with self.assertRaises(ValueError):loader.validate(root,'unreviewed-run')


if __name__=='__main__':unittest.main()
