"""Offline negative regressions for bounded snapshots and transactional archives."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from evidence_io import (atomic_write_json, bounded_path, publish_archive,
                         read_json_snapshot, strict_json_loads)

spec = importlib.util.spec_from_file_location('archive_local', Path(__file__).with_name('archive-m1059-local.py'))
archive_local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive_local)


class EvidenceIoTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_strict_json_rejects_duplicate_and_nonfinite(self):
        for raw in (b'{"a":1,"a":2}', b'{"a":{"b":1,"b":2}}', b'{"x":NaN}',
                    b'{"x":Infinity}', b'{"x":1e999}'):
            with self.subTest(raw=raw), self.assertRaisesRegex(ValueError, 'Duplicate|Non-finite'):
                strict_json_loads(raw)

    def test_cross_platform_path_escape_rejected(self):
        for path in ('', '../outside', 'a/../../b', '/tmp/a', 'C:/a', 'C:a',
                     '\\\\server\\share\\a', '\\rooted', 'a:stream', 'a//b', './a',
                     'a/..', 'a.', 'NUL', 'a/COM1.txt'):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, 'path'):
                bounded_path(self.root, path, must_exist=False)

    def test_link_is_rejected_even_when_it_points_inside(self):
        # This assertion covers symlink rejection; junction/reparse have separate regressions.
        (self.root / 'target').write_text('{}')
        with patch.object(Path, 'is_symlink', return_value=True):
            with self.assertRaisesRegex(ValueError, 'Linked'):
                bounded_path(self.root, 'target')

    def test_snapshot_hash_is_of_parsed_bytes(self):
        raw = b'{"name":"snapshot"}\n'
        (self.root / 'e.json').write_bytes(raw)
        snap = read_json_snapshot(self.root, 'e.json')
        (self.root / 'e.json').write_bytes(b'{"name":"changed"}')
        self.assertEqual(snap.data, {'name': 'snapshot'})
        self.assertEqual(snap.raw, raw)
        self.assertEqual(snap.sha256, hashlib.sha256(raw).hexdigest())

    def test_empty_oversized_and_malformed_sources_rejected(self):
        for raw, limit, error in ((b'', 10, 'Empty'), (b'{} ', 2, 'exceeds'), (b'{', 10, '')):
            (self.root / 'e.json').write_bytes(raw)
            with self.subTest(raw=raw), self.assertRaisesRegex(ValueError, error):
                read_json_snapshot(self.root, 'e.json', max_bytes=limit)

    def test_atomic_serialization_failure_preserves_previous_bytes(self):
        target = self.root / 'receipt.json'
        target.write_bytes(b'previous failure receipt')
        with self.assertRaises(ValueError):
            atomic_write_json(target, {'status': 'PASS', 'value': float('nan')})
        self.assertEqual(target.read_bytes(), b'previous failure receipt')
        self.assertEqual(list(self.root.iterdir()), [target])

    def test_archive_is_fresh_and_complete_or_absent(self):
        target = self.root / 'archive'
        with patch('evidence_io.os.rename', side_effect=OSError('injected publish failure')):
            with self.assertRaisesRegex(OSError, 'injected'):
                publish_archive(target, [('one.json', b'{}')], {'status': 'PASS'})
        self.assertFalse(target.exists())
        self.assertEqual(list(self.root.iterdir()), [])
        publish_archive(target, [('one.json', b'{"one":1}')], {'status': 'PASS'})
        self.assertEqual({p.name for p in target.iterdir()}, {'one.json', 'manifest.json'})
        with self.assertRaisesRegex(ValueError, 'already exists'):
            publish_archive(target, [('two.json', b'{}')], {})
        self.assertEqual((target / 'one.json').read_bytes(), b'{"one":1}')

    def test_duplicate_and_traversal_destination_rejected_before_write(self):
        for files in ([('one.json', b'{}'), ('ONE.json', b'{}')], [('../escape', b'{}')],
                      [('manifest.json', b'{}')], [('empty.json', b'')]):
            with self.subTest(files=files), self.assertRaises(ValueError):
                publish_archive(self.root / 'archive', files, {})
            self.assertEqual(list(self.root.iterdir()), [])

    def test_archive_write_interruption_removes_staging(self):
        original = Path.write_bytes
        def interrupted(path, raw):
            if path.name == 'manifest.json':
                raise OSError('injected manifest write failure')
            return original(path, raw)
        with patch.object(Path, 'write_bytes', interrupted):
            with self.assertRaisesRegex(OSError, 'manifest write failure'):
                publish_archive(self.root / 'archive', [('one.json', b'{}')], {'status': 'PASS'})
        self.assertEqual(list(self.root.iterdir()), [])

    def test_atomic_replace_failure_preserves_previous_receipt(self):
        target = self.root / 'receipt.json'
        target.write_bytes(b'{"status":"FAILED"}')
        with patch('evidence_io.os.replace', side_effect=OSError('injected replace failure')):
            with self.assertRaisesRegex(OSError, 'replace failure'):
                atomic_write_json(target, {'status': 'PASS'})
        self.assertEqual(target.read_bytes(), b'{"status":"FAILED"}')
        self.assertEqual(list(self.root.iterdir()), [target])

    def fixture(self):
        report = dict(milestone='M10.5.9', status='LOCAL_PARTIAL_CI_PENDING', m11Eligible=False,
                      paidRequests=0, runId='unit-test-run', jarSha256='a' * 64,
                      archiveInputs={}, browserMatrix={'status': 'PASS', 'engines': ['chromium', 'webkit'],
                            'fixtures': sorted(archive_local.FIXTURES), 'records': []})
        paths = ['.build-cache/M10.5.8-sdk-results.json', '.build-cache/M10.5.8-examples-results.json',
                 '.build-cache/M10.5.4-native-credentials.json', '.build-cache/M10.5.9/browser-runtime.json']
        paths += ['target/' + n + '.json' for n in ('frontend-audit', 'osv-results', 'sdk-source-inventory',
                                                  'candidate-smoke', 'release-evidence')]
        for path in paths:
            atomic_write_json(self.root / path, {'fixture': True, 'runId': report['runId']})
            report['archiveInputs'][path] = hashlib.sha256((self.root / path).read_bytes()).hexdigest()
        for engine in report['browserMatrix']['engines']:
            for fixture in sorted(archive_local.FIXTURES):
                browser = '.build-cache/M10.5.9/browser/' + engine + '/' + fixture + '.json'
                atomic_write_json(self.root / browser, {'browserEngine': engine, 'jarSha256': report['jarSha256'],
                                                      'runId': report['runId']})
                report['browserMatrix']['records'].append(dict(engine=engine, fixture=fixture, path=browser,
                                  sha256=hashlib.sha256((self.root / browser).read_bytes()).hexdigest()))
        return report

    def save_runner(self, report):
        atomic_write_json(self.root / '.build-cache/M10.5.9/runner.json', report)

    def test_archive_valid_fixture_preserves_input_bytes_and_local_boundary(self):
        report = self.fixture()
        self.save_runner(report)
        destination = archive_local.archive(self.root, 'docs/new-archive')
        manifest = json.loads((destination / 'manifest.json').read_bytes())
        self.assertEqual(manifest['status'], 'LOCAL_PARTIAL_CI_PENDING')
        self.assertIs(manifest['m11Eligible'], False)
        for name, entry in manifest['archivedRecords'].items():
            self.assertEqual((destination / name).read_bytes(), (self.root / entry['source']).read_bytes())

    def test_corrupt_late_input_never_publishes_partial_archive(self):
        report = self.fixture()
        self.save_runner(report)
        (self.root / 'target/release-evidence.json').write_text('{"tampered":true}')
        with self.assertRaisesRegex(ValueError, 'fingerprint changed'):
            archive_local.archive(self.root, 'docs/new-archive')
        self.assertFalse((self.root / 'docs/new-archive').exists())

    def test_archive_rejects_stale_identity_and_empty_records(self):
        for field, value, reason in (('runId', '', 'run identity'), ('archiveInputs', {}, 'fingerprints'),
                                      ('paidRequests', False, 'validated local')):
            report = self.fixture()
            report[field] = value
            self.save_runner(report)
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, reason):
                archive_local.archive(self.root, 'docs/new-archive')
            self.assertFalse((self.root / 'docs/new-archive').exists())
        report = self.fixture()
        report['browserMatrix']['records'] = []
        self.save_runner(report)
        with self.assertRaisesRegex(ValueError, 'Empty browser'):
            archive_local.archive(self.root, 'docs/new-archive')

    def test_partial_and_duplicate_matrix_records_are_rejected(self):
        for mutation, reason in ((lambda r: r['browserMatrix']['records'].pop(), 'Incomplete'),
                                 (lambda r: r['browserMatrix']['records'].append(r['browserMatrix']['records'][0]), 'Duplicate')):
            report = self.fixture()
            mutation(report)
            self.save_runner(report)
            with self.subTest(reason=reason), self.assertRaisesRegex(ValueError, reason):
                archive_local.archive(self.root, 'docs/new-archive')
            self.assertFalse((self.root / 'docs/new-archive').exists())


if __name__ == '__main__':
    unittest.main()
