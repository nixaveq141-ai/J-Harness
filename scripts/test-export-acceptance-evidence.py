import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('export_ci', Path(__file__).with_name('export-acceptance-evidence.py'))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


class EvidenceExportTest(unittest.TestCase):
    def fixture(self, root):
        directory = root / 'originals'; directory.mkdir()
        rows = []
        for i, name in enumerate(sorted(exporter.NAMES)):
            path = directory / (str(i) + '.zip')
            with zipfile.ZipFile(path, 'x') as archive:
                archive.writestr('evidence.xml', '<testsuite/>')
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            rows.append(dict(name=name, file=path.name, sha256=digest, githubDigest='sha256:' + digest,
                             commit='a' * 40, runId='123'))
        report = dict(remoteAcceptanceComplete=True, originalArtifactsIndependentlyAudited=True,
                      commit='a' * 40, runId='123', acquisition=rows)
        (root / 'result.json').write_text(json.dumps(report), encoding='utf-8')
        return report

    def test_exact_originals_roundtrip_and_existing_export_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); report = self.fixture(root)
            result = exporter.export(root, 'result.json', 'originals', 'vault/evidence.zip')
            with zipfile.ZipFile(root / result['path']) as archive:
                for row in report['acquisition']:
                    self.assertEqual((root / 'originals' / row['file']).read_bytes(), archive.read('artifacts/' + row['file']))
            before = (root / result['path']).read_bytes()
            with self.assertRaises(FileExistsError):
                exporter.export(root, 'result.json', 'originals', 'vault/evidence.zip')
            self.assertEqual(before, (root / result['path']).read_bytes())

    def test_mixed_duplicate_tampered_and_unverified_sources_cannot_publish(self):
        for change in ['mixed', 'duplicate', 'tampered', 'unverified', 'escape']:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary); report = self.fixture(root)
                if change == 'mixed': report['acquisition'][0]['commit'] = 'b' * 40
                if change == 'duplicate': report['acquisition'][1]['name'] = report['acquisition'][0]['name']
                if change == 'tampered': (root / 'originals/0.zip').write_bytes(b'changed')
                if change == 'unverified': report['originalArtifactsIndependentlyAudited'] = False
                if change == 'escape': report['acquisition'][0]['file'] = '../other.zip'
                (root / 'result.json').write_text(json.dumps(report), encoding='utf-8')
                with self.assertRaises(ValueError):
                    exporter.export(root, 'result.json', 'originals', 'vault/evidence.zip')
                self.assertFalse((root / 'vault/evidence.zip').exists())


if __name__ == '__main__':
    unittest.main()
