import copy
import unittest
import json
import hashlib
from pathlib import Path
import tempfile
from r4_faults import validate_record, validate_file, frontend_sources


class FaultEvidenceTest(unittest.TestCase):
    def sample(self):
        return dict(status='PASS', runId='run', engine='chromium', errors=[],
                    sourceSha256={'src/App.tsx': 'a' * 64},
                    checks=[dict(name='one', passed=True), dict(name='two', passed=True)],
                    screenshot=dict(path='.build-cache/R4/run/a.png', sha256='b' * 64))

    def check(self, record):
        return validate_record(record, ['one', 'two'], engine='chromium', run_id='run',
                               sources={'src/App.tsx': 'a' * 64})

    def test_exact_case_identity_run_engine_source_and_success_are_required(self):
        self.assertEqual(2, self.check(self.sample()))
        for field, value in [('runId', 'old'), ('engine', 'webkit'), ('status', 'FAILED'),
                             ('errors', ['uncaught']), ('sourceSha256', {})]:
            record = self.sample(); record[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check(record)

    def test_missing_duplicate_reordered_numeric_success_and_missing_digest_fail(self):
        for change in ['missing', 'duplicate', 'reordered', 'numeric', 'digest']:
            record = copy.deepcopy(self.sample())
            if change == 'missing': record['checks'].pop()
            if change == 'duplicate': record['checks'][1]['name'] = 'one'
            if change == 'reordered': record['checks'].reverse()
            if change == 'numeric': record['checks'][1]['passed'] = 1
            if change == 'digest': record['screenshot'].pop('sha256')
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.check(record)

    def test_file_source_and_screenshot_bytes_are_checked_inside_the_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            for name in ('frontend/src/App.tsx','frontend/tests/r4.mjs','frontend/tests/r4-fixture.tsx','frontend/tests/runtime.mjs'):
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'fixture')
            (root/'scripts/contracts').mkdir(parents=True)
            (root/'scripts/contracts/r4-fault-cases.json').write_text(json.dumps(dict(cases=['one','two'])),encoding='utf-8')
            shot=root/'.build-cache/R4/run/a.png';shot.parent.mkdir(parents=True);shot.write_bytes(b'fixed picture')
            record=self.sample();record['sourceSha256']=frontend_sources(root);record['screenshot']['sha256']=hashlib.sha256(shot.read_bytes()).hexdigest()
            output=root/'.build-cache/R4/run/browser-chromium.json';output.write_text(json.dumps(record),encoding='utf-8')
            self.assertEqual(2,validate_file(root,output.relative_to(root).as_posix(),'chromium','run'))
            shot.write_bytes(b'changed picture')
            with self.assertRaisesRegex(ValueError,'screenshot changed'):
                validate_file(root,output.relative_to(root).as_posix(),'chromium','run')


if __name__ == '__main__':
    unittest.main()
