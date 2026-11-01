import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

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
                r=dict(commit='fixed',runId='unified-source-123-1-'+label+'-fixed',
                       status='LINUX_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE',
                       javaTests=735,javaSuites=[None]*124,paidRequests=0,
                       candidateStage=dict(runtimeJarSha256=digest),candidateZipSha256='different-per-platform')
                (p/'result.json').write_text(json.dumps(r))
            self.assertEqual(3,len(equality.check(root,'fixed','123')['platforms']))
            with self.assertRaises(ValueError):equality.check(root,'other','123')
            jar.write_bytes(b'changed')
            with self.assertRaises(ValueError):equality.check(root,'fixed','123')


if __name__=='__main__':unittest.main()
