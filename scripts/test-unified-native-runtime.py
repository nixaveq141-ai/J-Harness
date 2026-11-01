"""Reject unreviewed scopes and accepted-JAR drift before unified assembly."""
import importlib.util
from pathlib import Path
import json
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('unified',Path(__file__).with_name('stage-unified-native-runtime.py'))
stage=importlib.util.module_from_spec(spec);spec.loader.exec_module(stage)


class UnifiedScopeTest(unittest.TestCase):
    def test_canonical_bytes_ignore_zip_metadata_and_internal_property_newlines(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for label,ending in [('windows',b'\r\n'),('posix',b'\n')]:
                with zipfile.ZipFile(root/(label+'.jar'),'x') as z:
                    z.writestr('current.class',b'exact-class');z.writestr('LICENSE',b'exact-legal')
                    z.writestr('META-INF/maven/io.jharness/harness-cli/pom.properties',
                               b'artifactId=harness-cli'+ending+b'groupId=io.jharness'+ending+b'version=0.1.0-SNAPSHOT'+ending)
                stage.canonicalize_runtime(root/(label+'.jar'),root/(label+'-canonical.jar'),True)
            self.assertEqual((root/'windows-canonical.jar').read_bytes(),(root/'posix-canonical.jar').read_bytes())
            with zipfile.ZipFile(root/'windows-canonical.jar') as z:
                self.assertEqual(b'exact-class',z.read('current.class'));self.assertEqual(b'exact-legal',z.read('LICENSE'))

    def test_exact_scopes_and_architectures(self):
        self.assertEqual(11,len(set.union(*stage.SCOPES.values())))
        self.assertEqual('windows',stage.active_scope('Windows','AMD64'))
        self.assertEqual('linux',stage.active_scope('Linux','x86_64'))
        self.assertEqual('macos',stage.active_scope('Darwin','arm64'))
        with self.assertRaises(ValueError):stage.active_scope('Darwin','x86_64')

    def test_reviewed_extraction_and_missing_scope_rejection(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);jar=root/'accepted.jar';target=root/'native';target.mkdir()
            items={n:dict(sha256=stage.sha(b'source'),origin=dict(runId='source')) for n in stage.SCOPES['linux']}
            with zipfile.ZipFile(jar,'x') as z:
                for n in items:z.writestr(n,b'source')
                z.writestr('META-INF/jharness/source-native-preview.json',json.dumps(dict(replacements=items)))
            receipt=dict(runtimeJarSha256=stage.sha(jar.read_bytes()),runId='accepted',commit='fixed')
            self.assertEqual(set(items),set(stage.extract_reviewed(jar,receipt,'linux',target)))
            with self.assertRaises(ValueError):stage.extract_reviewed(jar,receipt,'windows',target)
            with jar.open('ab') as stream:stream.write(b'drift')
            with self.assertRaises(ValueError):stage.extract_reviewed(jar,receipt,'linux',target)


if __name__=='__main__':unittest.main()
