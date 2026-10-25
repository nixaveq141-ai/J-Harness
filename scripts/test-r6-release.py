"""Offline R6 supply-chain and cache fault tests; no browser/native/model service required."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/f'{name}.py');value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

class R6ReleaseTest(unittest.TestCase):
    def test_secret_scanner_checks_size_before_read_and_replaces_old_pass(self):
        scanner=module('scan-source-secrets')
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'target').mkdir();output=root/'target/source-secrets.json';output.write_text('{"status":"PASS"}')
            with (root/'huge.py').open('wb') as f:f.truncate(16*1024*1024+1)
            with patch.object(scanner,'ROOT',root),patch.object(scanner.subprocess,'check_output',return_value=b'huge.py\0'):
                self.assertTrue(scanner.main())
            report=json.loads(output.read_bytes());self.assertEqual('INCOMPLETE',report['status']);self.assertEqual(0,report['sourceFiles'])

    def test_environment_variants_are_ignored_but_templates_remain_reviewable(self):
        result=subprocess.run(['git','check-ignore','--no-index','--stdin','-z'],input=b'.env.local\0.env.production\0.env.example\0.env.local.example\0',capture_output=True,cwd=ROOT)
        self.assertEqual({'.env.local','.env.production'},{p.decode() for p in result.stdout.split(b'\0') if p})

    def test_osv_invalid_bom_revokes_old_pass_even_under_python_optimization(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);source=root/'bom.json';output=root/'result.json'
            source.write_text('{"bomFormat":"wrong","components":[]}');output.write_text('{"status":"PASS"}')
            result=subprocess.run([os.environ.get('JHARNESS_PYTHON',os.sys.executable),'-O',str(ROOT/'scripts/scan-osv.py'),str(source),str(output)],capture_output=True,timeout=15)
            self.assertNotEqual(0,result.returncode);self.assertEqual('INCOMPLETE',json.loads(output.read_bytes())['status'])

    def test_bom_duplicate_versions_keep_instance_edges_and_optional_absence_is_valid(self):
        sbom=module('frontend-sbom')
        def item(dependencies=None):return dict(version='1',integrity='sha512-'+base64.b64encode(b'x'*64).decode(),license='MIT',resolved='https://registry.npmjs.org/fixture',**({'dependencies':dependencies} if dependencies else {}))
        packages={'':{'dependencies':{'a':'1'},'optionalDependencies':{'missing':'1'}},'node_modules/a':item({'b':'1'}),'node_modules/a/node_modules/b':item()}
        packages['node_modules/a']['optionalDependencies']={'absent':'1'}
        packages['node_modules/a/node_modules/b']['optionalDependencies']={'b':'1'}
        packages['node_modules/a/node_modules/b/node_modules/b']=item()
        bom=sbom.build(json.dumps(dict(name='fixture',version='1',packages=packages)).encode())
        refs=[c['bom-ref'] for c in bom['components']]
        self.assertEqual(3,len(set(refs)));self.assertEqual(2,len({c['purl'] for c in bom['components']}))
        self.assertEqual([{'license':{'name':'SEE LICENSE IN file'}}],sbom.license_value('SEE LICENSE IN file'))

    def test_missing_registry_metadata_has_controlled_diagnostic(self):
        with self.assertRaisesRegex(ValueError,'metadata missing'):
            module('frontend-sbom').build(json.dumps(dict(packages={'':{},'node_modules/a':{'version':'1'}})).encode())

    def test_sdk_side_effect_import_examples_and_aliases_cannot_get_zero_dependency_bom(self):
        inventory=module('sdk-inventory')
        for name,append in [('sdk/examples/run.ts',"\nimport 'external-side-effect';\n"),('sdk/examples/run.py','\nimport requests\n'),('sdk/python/jharness_sdk.py','\nfrom importlib import import_module as alias\nalias("external")\n')]:
            with self.subTest(name=name),tempfile.TemporaryDirectory() as d:
                root=Path(d)
                for p in inventory.FILES+['LICENSE']:
                    target=root/p;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/p,target)
                with (root/name).open('a',encoding='utf-8') as f:f.write(append)
                with self.assertRaises(ValueError):inventory.build(root)

    def test_ui_build_rejects_stale_inputs_extra_outputs_and_wrong_packaged_bytes(self):
        ui=module('ui-bundle')
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            names=['LICENSE']+['frontend/'+n for n in ['package.json','package-lock.json','tsconfig.json','vite.config.ts','index.html','upstream.json','LICENSE-DeepSeek-Harness.txt','src/main.ts','public/a.txt','scripts/package-ui.mjs']]
            for name in names:
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'input')
            dist=root/'frontend/dist';dist.mkdir();files={'index.html':b'index','ui-license.txt':b'license','ui-files.txt':b'index.html\nui-license.txt\n'}
            for name,data in files.items():(dist/name).write_bytes(data)
            digest=lambda b:hashlib.sha256(b).hexdigest()
            record=dict(schemaVersion=1,sourceSha256={n:digest((root/n).read_bytes()) for n in names},files={n:digest(b) for n,b in files.items()})
            (dist/'ui-build.json').write_text(json.dumps(record));jar=root/'app.jar'
            with zipfile.ZipFile(jar,'w') as z:
                for p in dist.iterdir():z.writestr('ui/'+p.name,p.read_bytes())
            self.assertEqual(4,len(ui.verify(root,jar)))
            (root/'frontend/src/main.ts').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'Stale'):ui.verify(root,jar)
            (root/'frontend/src/main.ts').write_bytes(b'input');(dist/'extra.txt').write_bytes(b'extra')
            with self.assertRaisesRegex(ValueError,'set changed'):ui.verify(root,jar)

    def test_wrapper_rejects_modified_zip_extracted_cache_and_partial_cache(self):
        java=Path(os.environ['JAVA_HOME'])/'bin';suffix='.exe' if os.name=='nt' else ''
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);probe=root/'CacheProbe.java'
            probe.write_text('public class CacheProbe {public static void main(String[] a)throws Exception{if(a[0].equals("install"))Bootstrap.install(java.nio.file.Path.of(a[1]),java.nio.file.Path.of(a[2]),a[3]);else Bootstrap.verify(java.nio.file.Path.of(a[2]),a[3]);}}')
            subprocess.run([str(java/('javac'+suffix)),'-d',str(root),str(ROOT/'.mvn/wrapper/Bootstrap.java'),str(probe)],check=True,capture_output=True,timeout=30)
            archive=root/'maven.zip'
            with zipfile.ZipFile(archive,'w') as z:
                for n in ['bin/mvn','bin/mvn.cmd','lib/core.jar']:z.writestr('apache-maven-3.9.9/'+n,b'original')
            checksum=hashlib.sha256(archive.read_bytes()).hexdigest();home=root/'home'
            def run(mode):return subprocess.run([str(java/('java'+suffix)),'-cp',str(root),'CacheProbe',mode,str(archive),str(home),checksum],capture_output=True,timeout=15).returncode
            self.assertEqual(0,run('install'));self.assertEqual(0,run('verify'))
            (home/'lib/core.jar').write_bytes(b'tampered');self.assertNotEqual(0,run('verify'))
            (home/'lib/core.jar').write_bytes(b'original');(home/'.complete').unlink();self.assertNotEqual(0,run('verify'))
            archive.write_bytes(b'tampered');self.assertNotEqual(0,run('install'))

if __name__=='__main__':unittest.main()
