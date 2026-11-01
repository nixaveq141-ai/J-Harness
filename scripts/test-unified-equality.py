import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('equality',Path(__file__).with_name('check-unified-qualification.py'))
equality=importlib.util.module_from_spec(spec);spec.loader.exec_module(equality)


class EqualityGateTest(unittest.TestCase):
    def test_same_bytes_required_and_other_runs_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for label in ['windows-2022','ubuntu-24.04','macos-latest']:
                base=root/label;p=base/'.build-cache/source-qualification';p.mkdir(parents=True)
                jar=base/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';jar.parent.mkdir(parents=True);jar.write_bytes(b'qualified')
                digest=equality.hashlib.sha256(jar.read_bytes()).hexdigest()
                platform={'windows-2022':'WINDOWS','ubuntu-24.04':'LINUX','macos-latest':'DARWIN'}[label]
                r=dict(commit='fixed',runId='unified-source-123-1-'+label+'-fixed',
                       status=platform+'_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE',
                       javaTests=735,javaSuites=[None]*124,paidRequests=0,
                       candidateStage=dict(runtimeJarSha256=digest),candidateZipSha256='different-per-platform')
                (p/'result.json').write_text(json.dumps(r))
            # The former count-only gate accepted these 124 null suites.
            # Actual case identity and XML evidence must now reject them.
            with self.assertRaises(ValueError):equality.check(root,'fixed','123')
            with self.assertRaises(ValueError):equality.check(root,'other','123')
            jar.write_bytes(b'changed')
            with self.assertRaises(ValueError):equality.check(root,'fixed','123')

    def test_all_three_hashes_are_required(self):
        rows={label:dict(runtimeJarSha256='same') for label in ['windows-2022','ubuntu-24.04','macos-latest']}
        equality.require_identical_hashes(rows)
        rows['windows-2022']['runtimeJarSha256']='different'
        with self.assertRaises(ValueError):equality.require_identical_hashes(rows)
        del rows['windows-2022']
        with self.assertRaises(ValueError):equality.require_identical_hashes(rows)

    def test_wrong_process_cannot_stand_in_for_verify(self):
        with self.assertRaises(ValueError):
            equality.verify_command_identity('source-verify',['echo','PASS'],True)

    def test_failure_revokes_stale_success_before_reading_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);output=root/'.build-cache/unified-equality.json';output.parent.mkdir()
            output.write_text('{"status":"OLD_PASS"}')
            with patch.object(equality,'ROOT',root),patch.dict(equality.os.environ,dict(GITHUB_SHA='missing',GITHUB_RUN_ID='missing',GITHUB_RUN_ATTEMPT='1')):
                with self.assertRaises(FileNotFoundError):equality.main()
            self.assertEqual('INCOMPLETE',json.loads(output.read_bytes())['status'])


if __name__=='__main__':unittest.main()
