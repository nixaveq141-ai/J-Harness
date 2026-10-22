"""A semantic probe PASS requires fresh fixed command, source and artifact receipts."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('r1_supplemental', Path(__file__).with_name('run-r1-supplemental.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class R1SupplementalEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.destination = self.root / '.build-cache/R1/supplemental'
        self.destination.mkdir(parents=True)
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(gate, 'ROOT', self.root).start()
        mock.patch.object(gate, 'runtime', return_value={'fixed-runtime': 'verified-fixture'}).start()
        mock.patch.dict(os.environ, JAVA_HOME=str(self.root / 'jdk')).start()
        self.legacy = b'fixed historical legacy fixture'
        mock.patch.object(gate.subprocess, 'check_output', return_value=self.legacy).start()
        for name in ('pom.xml', 'scripts/test-native-credentials.py', gate.JAR,
                     'harness-cli/target/test-classes/Probe.class', 'harness-testkit/target/test-classes/Probe.class',
                     'harness-testkit/target/classes/FakeLlm.class'):
            path = self.root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(name.encode())
        self.source = gate.evidence_run.inputs(self.root)
        self.jar_hash = sha(self.root / gate.JAR)
        self.payloads = {
            'native': dict(passed=True, expectationMet=True, expectedUnavailable=False, exitCode=0,
                           credentialStore='Windows Credential Manager', failureCode='', cleanupFailureCode='',
                           jarSha256=self.jar_hash, checks={k: True for k in gate.NATIVE},
                           sourceSha256={'pom.xml': self.source['pom.xml']},
                           probeScriptSha256=self.source['scripts/test-native-credentials.py']),
            'windows-compat': dict(passed=True, parentCleanup=True, exitCode=0, legacyCommit='fdd326b',
                                   currentJarSha256=self.jar_hash, checks={k: True for k in gate.COMPAT},
                                   legacySourceSha256=hashlib.sha256(self.legacy).hexdigest()),
        }
        base = dict(milestone='M7', actualDocker=True, image=gate.IMAGE, result='SUCCESS', cleanupVerified=True, jarSha256=self.jar_hash)
        sub = dict(status='COMPLETED', parentRequestsReconstructed=4, childRequestsReconstructed=2,
                   independentVersionGuards=True, parentEditVerified=True, originAndArtifactVerified=True)
        self.payloads['docker-subagent'] = dict(base, slice='read-only-subagent', **{'subagent-local': dict(sub), 'subagent-docker': dict(sub)})
        web = dict(status='COMPLETED', parallelReadOnlyScheduling=False, hostExitCode=0, requestsReconstructedAgainstWire=3,
                   actualHttp=True, approvalBeforeWrite=True, submissionDeduplicated=True, environmentClosed=True)
        self.payloads['web-docker'] = dict(base, slice='packaged-cli-web-sdk-docker', cases={'local': dict(web), 'docker': dict(web)})
        self.report = dict(schemaVersion=1, runId='fixture-r1', status='LOCAL_SUPPLEMENTAL_PASS', m11Eligible=False,
                           sourceSha256=self.source, artifacts=gate.artifacts(), runtime=gate.runtime(), commands=[], passingCases=[])
        cases = set()
        for name in gate.GROUPS:
            raw = gate.report_path(name, self.destination)
            self.write(raw, self.payloads[name])
            log = self.destination / (name + '.log'); log.write_text('actual fixture command log')
            receipt = self.destination / (name + '.receipt.json')
            command = gate.command_for(name, self.destination)
            value = dict(schemaVersion=1, status='PASS', runId='fixture-r1', name='r1-' + name, command=command,
                         exitCode=0, cwd='.', sourceSha256=self.source, sourceAfterSha256=self.source,
                         logSha256=sha(log), outputs={raw.relative_to(self.root).as_posix(): sha(raw)},
                         startedAt='2026-10-21T01:00:00+00:00', finishedAt='2026-10-21T01:00:01+00:00')
            self.write(receipt, value)
            self.report['commands'].append(dict(name=name, command=command, report=raw.relative_to(self.root).as_posix(),
                reportSha256=sha(raw), receipt=receipt.relative_to(self.root).as_posix(), receiptSha256=sha(receipt),
                log=log.relative_to(self.root).as_posix()))
            cases.update(gate.semantic_cases(name, self.payloads[name], self.jar_hash))
        self.report['passingCases'] = sorted(cases)
        self.path = self.destination / 'results.json'
        self.save()

    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding='utf-8')

    def save(self):
        self.write(self.path, self.report)

    def verify(self, **kwargs):
        return gate.verify(self.path.relative_to(self.root).as_posix(), **kwargs)

    def test_all_fixed_semantics_have_same_run_read_only_receipts(self):
        before = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        passed = self.verify(run_id='fixture-r1', source=self.source)
        self.assertEqual(18, len(passed)); self.assertIn('r1-native#deleteMissing', passed)
        self.assertEqual(before, {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()})

    def test_missing_group_and_invented_pass_identity_are_rejected(self):
        original = copy.deepcopy(self.report)
        self.report['commands'].pop(); self.save()
        with self.assertRaisesRegex(ValueError, 'groups'): self.verify()
        self.report = original; self.report['passingCases'].append('r1-native#made-up'); self.save()
        with self.assertRaisesRegex(ValueError, 'case identities'): self.verify()

    def test_native_and_compat_cleanup_must_be_true_not_truthy(self):
        for name, field in [('native', 'cleanup'), ('windows-compat', 'cleanup')]:
            data = copy.deepcopy(self.payloads[name]); data['checks'][field] = 1
            with self.assertRaises(ValueError): gate.semantic_cases(name, data, self.jar_hash)
        data = dict(self.payloads['windows-compat'], parentCleanup=False)
        with self.assertRaises(ValueError): gate.semantic_cases('windows-compat', data, self.jar_hash)

    def test_docker_image_counts_and_environment_must_be_exact(self):
        for mutation in ('image', 'count', 'cleanup', 'http'):
            data = copy.deepcopy(self.payloads['web-docker'])
            if mutation == 'image': data['image'] = 'eclipse-temurin:latest'
            elif mutation == 'count': data['cases']['docker']['requestsReconstructedAgainstWire'] = True
            elif mutation == 'cleanup': data['cleanupVerified'] = False
            else: data['cases']['docker']['actualHttp'] = False
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): gate.semantic_cases('web-docker', data, self.jar_hash)

    def test_command_tampering_fails_even_with_updated_receipt_hash(self):
        entry = self.report['commands'][0]
        receipt = self.root / entry['receipt']; value = json.loads(receipt.read_text())
        value['command'] = ['python', 'unrelated.py']; self.write(receipt, value)
        entry['receiptSha256'] = sha(receipt); self.save()
        with self.assertRaisesRegex(ValueError, 'Wrong command'): self.verify()

    def test_run_source_jar_class_and_log_drift_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'run mismatch'): self.verify(run_id='other')
        for name in ['pom.xml', gate.JAR, 'harness-testkit/target/test-classes/Probe.class', '.build-cache/R1/supplemental/native.log']:
            path = self.root / name; before = path.read_bytes(); path.write_bytes(before + b'drift')
            with self.subTest(name=name), self.assertRaises(ValueError): self.verify()
            path.write_bytes(before)

    def test_receipt_output_hash_cannot_be_replaced_by_report_pass(self):
        entry = self.report['commands'][0]; raw = self.root / entry['report']
        data = json.loads(raw.read_text()); data['checks']['deleteMissing'] = False; self.write(raw, data)
        entry['reportSha256'] = sha(raw); self.save()
        with self.assertRaisesRegex(ValueError, 'output drift'): self.verify()

    def test_wrong_working_directory_and_negative_exit_are_rejected(self):
        entry = self.report['commands'][0]; receipt = self.root / entry['receipt']; original = json.loads(receipt.read_text())
        for update in [dict(cwd='other'), dict(exitCode=7)]:
            self.write(receipt, dict(original, **update)); entry['receiptSha256'] = sha(receipt); self.save()
            with self.subTest(update=update), self.assertRaises(ValueError): self.verify()


if __name__ == '__main__':
    unittest.main()
