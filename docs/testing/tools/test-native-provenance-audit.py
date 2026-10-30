"""Offline counterexamples: byte identity must never become source/CPE attestation."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('native_audit', Path(__file__).with_name('native-provenance-audit.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class NativeAuditTest(unittest.TestCase):
    def test_codeview_clues_are_bounded_and_not_attestations(self):
        record=b'RSDS'+b'\x11'*16+struct.pack('<I',2)+b'Z:\\BuildAgent\\work\\example\\native.pdb\0'
        rows=audit.codeview_candidates(b'MZ'+record*100)
        self.assertEqual(16,len(rows))
        self.assertEqual(2,rows[0]['age'])
        self.assertEqual('EMBEDDED_CLUE_NOT_BUILD_ATTESTATION',rows[0]['status'])
        self.assertEqual([],audit.codeview_candidates(b'MZRSDS'))
        self.assertEqual([],audit.codeview_candidates(b'ELF'+record))
    def fixture(self, root):
        (root/'third-party').mkdir()
        jar = root/'repo/org/jetbrains/pty4j/1/pty4j-1.jar'
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('resources/com/pty4j/native/win/x86/conpty.dll', b'MZfixture')
        (root/'third-party/inventory.json').write_text(json.dumps([
            dict(coordinate='org.jetbrains:pty4j:1', usage='runtime', jarSha256=audit.sha(jar.read_bytes()))]), encoding='utf-8')
        return jar

    def test_pinned_bytes_and_upstream_match_are_not_attestation(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); self.fixture(root)
            original = audit.ROOT
            try:
                audit.ROOT = root
                report = audit.audit(root/'repo', True, lambda url: b'MZfixture')
                self.assertEqual('BUILD_PROVENANCE_INCOMPLETE', report['status'])
                self.assertTrue(report['binaries'][0]['distributionBytesMatch'])
                self.assertEqual('NOT_ASSESSED', report['binaries'][0]['cpeAssessment'])
                with self.assertRaisesRegex(ValueError, 'mismatch'):
                    audit.audit(root/'repo', True, lambda url: b'wrong binary')
            finally:
                audit.ROOT = original

    def test_modified_local_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); jar = self.fixture(root)
            jar.write_bytes(jar.read_bytes()+b'tamper')
            original = audit.ROOT
            try:
                audit.ROOT = root
                with self.assertRaisesRegex(ValueError, 'pinned legal inventory'):
                    audit.audit(root/'repo')
            finally:
                audit.ROOT = original

    def test_embedded_version_is_only_a_candidate_and_truncation_is_ignored(self):
        signature = struct.pack('<IIII', 0xFEEF04BD, 0x10000, (1 << 16)|22, (3 << 16)|4)
        self.assertEqual(['1.22.3.4'], audit.versions(b'MZ'+signature+b'\0'*36))
        self.assertEqual([], audit.versions(b'MZ'+signature))
        self.assertEqual([], audit.versions(b'ELF'+signature+b'\0'*36))


if __name__ == '__main__':
    unittest.main()
