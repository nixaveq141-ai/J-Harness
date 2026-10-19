import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('sdk_inventory',Path(__file__).with_name('sdk-inventory.py'))
inventory=importlib.util.module_from_spec(spec);spec.loader.exec_module(inventory)

class SdkInventoryTest(unittest.TestCase):
    def test_actual_standard_library_sources_and_license_are_complete(self):
        report=inventory.build()
        self.assertEqual(0,report['externalRuntimeDependencyCount'])
        self.assertEqual(9,len(report['sourceSha256']))
        self.assertEqual('1.0',report['protocolVersion'])
        self.assertEqual('CycloneDX',report['sbom']['bomFormat'])
        self.assertEqual({'bomFormat','specVersion','version','metadata','components','dependencies'},set(report['sbom']))

    def probe(self,path,content):
        with tempfile.TemporaryDirectory() as name:
            root=Path(name)
            for p in inventory.FILES+['LICENSE']:
                target=root/p;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(inventory.ROOT/p,target)
            (root/path).write_text(content,encoding='utf-8')
            with self.assertRaises(ValueError):inventory.build(root)

    def test_undeclared_python_dependency_cannot_receive_zero_dependency_bom(self):
        self.probe('sdk/python/jharness_sdk.py','import requests\n')

    def test_dynamic_typescript_import_cannot_receive_zero_dependency_bom(self):
        self.probe('sdk/typescript/src/index.ts',"import { x } from 'node:fs'; const y=import('external');")

    def test_added_package_dependency_requires_review(self):
        self.probe('sdk/typescript/package.json','{"dependencies":{"external":"1"}}')

if __name__=='__main__':unittest.main()
