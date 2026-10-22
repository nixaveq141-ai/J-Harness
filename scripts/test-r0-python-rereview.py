"""Independent negative regressions for the R0 Python rereview fixes."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

import evidence_checks
import evidence_io
import evidence_run

XML = '<testsuite name="fixture" tests="1" failures="0" errors="0" skipped="0"><properties><property name="java.version" value="21"/></properties><testcase name="real"/></testsuite>'


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class PythonRereviewTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    def test_xml_does_not_use_unbounded_read_bytes(self):
        path = self.root / 'report.xml'
        path.write_text(XML, encoding='utf-8')
        with patch.object(Path, 'read_bytes', side_effect=AssertionError('unbounded read')):
            self.assertEqual(evidence_checks.suite_snapshot(path, 'fixture')['tests'], 1)

    def test_xml_size_limit_is_enforced_at_stream_read(self):
        stream = io.BytesIO(b'x' * 1024)
        read = Mock(wraps=stream.read)
        stream.read = read
        path = Mock()
        path.open.return_value.__enter__ = Mock(return_value=stream)
        path.open.return_value.__exit__ = Mock(return_value=False)
        with self.assertRaisesRegex(ValueError, 'XML report size'):
            evidence_checks.xml_document_snapshot(path, max_bytes=16)
        read.assert_called_once_with(17)

    def test_suite_root_versions_and_hash_share_one_read(self):
        path = self.root / 'report.xml'
        raw = XML.encode('utf-8')
        path.write_bytes(raw)
        original = Path.open
        calls = []
        def opened(path, *args, **kwargs):
            calls.append(path)
            return original(path, *args, **kwargs)
        with patch.object(Path, 'open', opened):
            entry, root = evidence_checks.suite_details(path, 'fixture')
        self.assertEqual(calls, [path])
        self.assertEqual(entry['sha256'], evidence_run.digest(raw))
        self.assertEqual(root.find('properties/property').get('value'), '21')

    def test_xml_unsupported_encoding_and_utf8_dtd_are_rejected(self):
        for text in ('<?xml version="1.0" encoding="ISO-8859-1"?>' + XML,
                     '<!DOCTYPE testsuite []>' + XML):
            path = self.root / 'report.xml'
            path.write_text(text, encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Unsafe XML'):
                evidence_checks.suite_snapshot(path, 'fixture')

    def test_utf16_and_utf32_entity_documents_are_rejected(self):
        text = '<!DOCTYPE testsuite [<!ENTITY item "expanded">]>' + XML.replace('name="real"', 'name="&item;"')
        for encoding in ('utf-16', 'utf-32'):
            path = self.root / (encoding + '.xml')
            path.write_bytes(text.encode(encoding))
            with self.subTest(encoding=encoding), self.assertRaisesRegex(ValueError, 'XML'):
                evidence_checks.suite_snapshot(path, 'fixture')

    def test_manifest_name_rejected_before_creating_any_files(self):
        for name in ('../escaped.json', '/absolute.json', 'C:\\escape.json', '', 'CON'):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'manifest|destination'):
                evidence_io.publish_archive(self.root / 'archive', [('one.json', b'{}')], {}, manifest_name=name)
            self.assertEqual(list(self.root.iterdir()), [])

    def test_log_validation_never_reads_whole_log(self):
        (self.root / 'pom.xml').write_text('<project/>')
        log = self.root / 'run.log'
        receipt = self.root / 'receipt.json'
        value = evidence_run.run([sys.executable, '-c', 'print("real")'], name='fixture',
                log=log, receipt=receipt, run_id='unit-test', root=self.root, cwd=self.root)
        original = Path.read_bytes
        def bounded_reads(path):
            if path == log:
                raise AssertionError('whole log read')
            return original(path)
        with patch.object(Path, 'read_bytes', bounded_reads):
            evidence_run.validate(value, name='fixture', log=log, run_id='unit-test', source=evidence_run.inputs(self.root))

    def test_actual_command_log_budget_fails_with_incomplete_receipt(self):
        (self.root / 'pom.xml').write_text('<project/>')
        receipt = self.root / 'receipt.json'
        with self.assertRaisesRegex(ValueError, 'log exceeds byte budget'):
            evidence_run.run([sys.executable, '-c', 'print("x" * 4096)'], name='fixture',
                log=self.root/'run.log', receipt=receipt, run_id='unit-test', root=self.root,
                cwd=self.root, max_log_bytes=128)
        value = json.loads(receipt.read_bytes())
        self.assertTrue(value['logBudgetExceeded'])
        self.assertEqual(value['status'], 'INCOMPLETE')

    def test_running_command_log_budget_stops_command_before_deadline(self):
        (self.root / 'pom.xml').write_text('<project/>')
        receipt = self.root / 'receipt.json'
        with self.assertRaisesRegex(ValueError, 'log exceeds byte budget'):
            evidence_run.run([sys.executable, '-c', 'import time; print("x" * 65536, flush=True); time.sleep(20)'],
                name='fixture', log=self.root/'run.log', receipt=receipt, run_id='unit-test',
                root=self.root, cwd=self.root, timeout=5, max_log_bytes=128)
        value = json.loads(receipt.read_bytes())
        self.assertTrue(value['logBudgetExceeded'])
        self.assertNotIn('timedOut', value)
        self.assertEqual(value['status'], 'INCOMPLETE')

    def test_stream_digest_reads_fixed_size_chunks_and_budget_rejects(self):
        path = self.root / 'large.log'
        path.write_bytes(b'x' * (256 * 1024))
        with patch.object(Path, 'read_bytes', side_effect=AssertionError('whole file read')):
            self.assertEqual(evidence_io.stream_sha256(path), evidence_run.digest(b'x' * (256 * 1024)))
            with self.assertRaisesRegex(ValueError, 'byte budget'):
                evidence_io.stream_sha256(path, max_bytes=128)

    def test_posix_disappeared_group_still_reaps_child(self):
        (self.root / 'pom.xml').write_text('<project/>')
        process = Mock()
        process.wait.side_effect = [subprocess.TimeoutExpired('fixture', 1), 0]
        process.poll.return_value = None
        with patch.object(evidence_run.subprocess, 'Popen', return_value=process), \
             patch.object(evidence_run, 'IS_WINDOWS', False), \
             patch.object(evidence_run.signal, 'SIGKILL', 9, create=True), \
             patch.object(evidence_run.time, 'monotonic', side_effect=[0, 1, 1]), \
             patch.object(evidence_run.os, 'killpg', create=True, side_effect=ProcessLookupError) as killed:
            with self.assertRaisesRegex(ValueError, 'timed out'):
                evidence_run.run(['fixture'], name='fixture', log=self.root/'run.log',
                    receipt=self.root/'receipt.json', run_id='unit-test', root=self.root, cwd=self.root, timeout=.01)
        self.assertGreaterEqual(process.wait.call_count, 2)
        killed.assert_called_once_with(process.pid, 9)
        self.assertEqual(json.loads((self.root/'receipt.json').read_bytes())['status'], 'INCOMPLETE')

    def test_posix_cleanup_failure_preserves_timeout_and_attempts_reap(self):
        (self.root / 'pom.xml').write_text('<project/>')
        process = Mock()
        process.wait.side_effect = [subprocess.TimeoutExpired('fixture', 1), 0]
        with patch.object(evidence_run.subprocess, 'Popen', return_value=process), \
             patch.object(evidence_run, 'IS_WINDOWS', False), \
             patch.object(evidence_run.signal, 'SIGKILL', 9, create=True), \
             patch.object(evidence_run.time, 'monotonic', side_effect=[0, 1, 1]), \
             patch.object(evidence_run.os, 'killpg', create=True, side_effect=PermissionError):
            with self.assertRaisesRegex(ValueError, 'timed out'):
                evidence_run.run(['fixture'], name='fixture', log=self.root/'run.log',
                    receipt=self.root/'receipt.json', run_id='unit-test', root=self.root, cwd=self.root, timeout=.01)
        self.assertEqual(process.wait.call_count, 2)
        value = json.loads((self.root/'receipt.json').read_bytes())
        self.assertEqual(value['cleanupFailures'], ['PermissionError'])
        self.assertTrue(value['timedOut'])

    def test_junction_branch_is_independent_from_symlink(self):
        (self.root / 'report.json').write_text('{}')
        with patch.object(Path, 'is_symlink', return_value=False), \
             patch.object(Path, 'is_junction', create=True, return_value=True):
            with self.assertRaisesRegex(ValueError, 'Linked'):
                evidence_io.bounded_path(self.root, 'report.json')

    def test_reparse_attribute_branch_is_independent_from_links(self):
        (self.root / 'report.json').write_text('{}')
        original = Path.lstat
        def attributes(path):
            result = original(path)
            return Mock(st_file_attributes=0x400) if path.name == 'report.json' else result
        with patch.object(Path, 'is_symlink', return_value=False), \
             patch.object(Path, 'is_junction', create=True, return_value=False), \
             patch.object(Path, 'lstat', attributes):
            with self.assertRaisesRegex(ValueError, 'Reparse'):
                evidence_io.bounded_path(self.root, 'report.json')

    def test_release_missing_or_escaping_log_revokes_old_receipt(self):
        collector = module('release_collector', 'collect-release-evidence.py')
        output = self.root / 'target/release-evidence.json'
        output.parent.mkdir()
        for args, error in (([], SystemExit), (['../outside.log'], ValueError),
                            ([str(self.root/'absolute.log')], ValueError)):
            output.write_text('{"status":"PASS"}')
            with self.subTest(args=args), patch.object(collector, 'ROOT', self.root), \
                 patch('sys.stderr', io.StringIO()), self.assertRaises(error):
                collector.main(args)
            self.assertEqual(json.loads(output.read_bytes())['status'], 'INCOMPLETE')

    def test_release_collector_reuses_single_xml_snapshot(self):
        collector = module('release_single_snapshot', 'collect-release-evidence.py')
        (self.root/'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>harness-fixture</module></modules></project>')
        target = self.root/'harness-fixture/target/surefire-reports'
        target.mkdir(parents=True)
        (self.root/'harness-fixture/pom.xml').write_text('<project/>')
        suite_path = target/'TEST-fixture.xml'
        suite_path.write_text(XML.replace('tests="1"', 'tests="410"').replace('<testcase name="real"/>',
            ''.join('<testcase name="case'+str(i)+'"/>' for i in range(410))))
        evidence_io.atomic_write_json(self.root/'scripts/contracts/m1059-java-cases.json',
            dict(schemaVersion=1,suites=[dict(module='harness-fixture',name='fixture',cases=['case'+str(i) for i in range(410)])]))
        jar = self.root/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        jar.parent.mkdir(parents=True)
        jar.write_bytes(b'unit test jar bytes')
        log = self.root/'verify.log'
        log.write_text('unit test log')
        receipt = self.root/'.build-cache/M10.5.9/commands/verify.json'
        evidence_io.atomic_write_json(receipt, {'runId':'unit-test'})
        original = Path.open
        reads = []
        def opened(path, *args, **kwargs):
            if path == suite_path:
                reads.append(path)
            return original(path, *args, **kwargs)
        with patch.object(collector, 'ROOT', self.root), patch.object(collector, 'inputs', return_value={'source':'fixture'}), \
             patch.object(collector, 'validate'), patch.dict('os.environ', {'JAVA_HOME':str(self.root)}), \
             patch.object(collector.platform, 'platform', return_value='fixture-platform'), \
             patch.object(collector.platform, 'machine', return_value='fixture-machine'), \
             patch('sys.stdout', io.StringIO()), \
             patch.object(collector.subprocess, 'run', return_value=Mock(stderr='java21')), \
             patch.object(collector.subprocess, 'check_output', return_value=b''), patch.object(Path, 'open', opened):
            collector.main(['verify.log'])
        self.assertEqual(reads, [suite_path])
        output = json.loads((self.root/'target/release-evidence.json').read_bytes())
        self.assertEqual(output['javaVersions'], ['21'])
        self.assertEqual(output['totals']['tests'], 410)

    def test_firefox_probe_binds_actual_start_failure_and_rejects_stale_identity(self):
        producer = module('firefox_probe_fixture', 'probe-firefox.py')
        executable = self.root/'firefox.exe'
        executable.write_bytes(b'synthetic executable identity')
        error = OSError('synthetic start failure')
        error.winerror = 14001
        with patch.object(producer.subprocess, 'run', side_effect=error) as launched:
            record = producer.probe(self.root, run_id='unit-test', executable=executable)
        launched.assert_called_once_with([str(executable), '--version'], capture_output=True, text=True, timeout=10)
        validate = lambda r: evidence_checks.firefox_unavailable(r, run_id='unit-test', executable=executable,
                                                                script_sha256=record['probeScriptSha256'])
        validate(record)
        with self.assertRaisesRegex(ValueError, 'another run'):
            validate({**record, 'runId':'old-run'})
        changed = {**record, 'firefox':{**record['firefox'], 'executableSha256':'a'*64}}
        with self.assertRaisesRegex(ValueError, 'identity drift'):
            validate(changed)
        old = {'firefox':{'status':'UNAVAILABLE', 'winerror':14001}}
        with self.assertRaisesRegex(ValueError, 'another run'):
            validate(old)

    def test_platform_producer_records_firefox_probe_before_browser_prepare(self):
        platform_runner = module('platform_probe_wiring', 'run-m1059-platform.py')
        prepare = Mock()
        prepare.prepare.return_value = None
        with patch.object(platform_runner, 'IS_WINDOWS', True), \
             patch.dict('os.environ', {'JHARNESS_EVIDENCE_RUN_ID':'unit-test'}), \
             patch('sys.stdout', io.StringIO()), \
             patch.object(platform_runner, 'run') as executed, \
             patch.object(platform_runner.importlib.util, 'spec_from_file_location'), \
             patch.object(platform_runner.importlib.util, 'module_from_spec', return_value=prepare):
            platform_runner.main()
        first = executed.call_args_list[0]
        self.assertEqual(first.args[0], [sys.executable, 'scripts/probe-firefox.py'])
        self.assertEqual(first.kwargs['name'], 'firefox-probe')
        self.assertEqual(first.kwargs['run_id'], 'unit-test')
        self.assertEqual(first.kwargs['outputs'], (platform_runner.OUT/'availability.json',))

    def test_windows_collector_requires_firefox_probe_exit_receipt(self):
        collector = module('collector_probe_wiring', 'collect-m1059-evidence.py')
        out = self.root/'.build-cache/M10.5.9'
        reached = []
        class ProbeReceiptReached(Exception):
            pass
        def load(path):
            reached.append(path.name)
            if path.name == 'firefox-probe.json':
                raise ProbeReceiptReached()
            return {}
        def load_with_snapshot(path):
            collector.SNAPSHOTS[path.relative_to(self.root).as_posix()] = Mock(sha256='a'*64)
            return load(path)
        with patch.object(collector, 'ROOT', self.root), patch.object(collector, 'OUT', out), \
             patch.dict('os.environ', {'JHARNESS_EVIDENCE_RUN_ID':'unit-test'}), \
             patch.object(collector, 'inputs', return_value={'fixture':'hash'}), \
             patch.object(collector, 'load', side_effect=load_with_snapshot), \
             patch.object(collector, 'sha', return_value='a'*64), \
             patch.object(collector, 'validate_command'), \
             patch.object(collector.sys, 'platform', 'win32'), self.assertRaises(ProbeReceiptReached):
            collector.collect(self.root/'verify.log')
        self.assertIn('firefox-probe.json', reached)
        self.assertEqual(json.loads((out/'runner.json').read_bytes())['status'], 'INCOMPLETE')


if __name__ == '__main__':
    unittest.main()
