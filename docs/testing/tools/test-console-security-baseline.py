"""Vendor range rollback, false-product and evidence drift controls."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('gate',Path(__file__).with_name('console-security-baseline.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)


class ConsoleSecurityTest(unittest.TestCase):
    def test_reviewed_baseline_is_not_full_cpe_clearance(self):
        policy=json.loads((gate.ROOT/'docs/testing/native-security/console-baseline.json').read_bytes())
        result=gate.validate(policy['sourceRevision'],policy['sourceVersion'])
        self.assertEqual(2,len(result['advisories']))
        self.assertFalse(result['fullVulnerabilityCoverage'])
        self.assertFalse(result['bundledSubsetExposureProven'])

    def test_rollback_scope_and_snapshot_controls(self):
        original=gate.ROOT/'docs/testing/native-security/console-baseline.json'
        baseline=json.loads(original.read_bytes())
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);manifest=root/'policy.json'
            def reset():
                policy=copy.deepcopy(baseline)
                for item in policy['advisories']:
                    (root/item['file']).write_bytes((original.parent/item['file']).read_bytes())
                return policy
            def check(policy):
                manifest.write_bytes(json.dumps(policy).encode())
                return gate.validate(policy['sourceRevision'],policy['sourceVersion'],manifest)
            def change_record(policy, mutate):
                item=policy['advisories'][0];path=root/item['file'];record=json.loads(path.read_bytes());mutate(record)
                path.write_bytes(json.dumps(record).encode());item['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
            policy=reset();policy['sourceVersion']='1.22.11141.0'
            with self.assertRaisesRegex(ValueError,'below reviewed security floor'):check(policy)
            policy=reset();policy['sourceVersion']='1.24.12741.0-preview'
            with self.assertRaisesRegex(ValueError,'numeric'):check(policy)
            policy=reset();(root/policy['advisories'][0]['file']).write_bytes(b'{}')
            with self.assertRaisesRegex(ValueError,'identity'):check(policy)
            policy=reset()
            change_record(policy,lambda r:[p.update(product='Windows Terminal Server') for p in r['containers']['cna']['affected']])
            with self.assertRaisesRegex(ValueError,'product scope'):check(policy)
            policy=reset()
            change_record(policy,lambda r:[p['versions'][0].pop('lessThan',None) for p in r['containers']['cna']['affected'] if p.get('product') in gate.PRODUCTS])
            with self.assertRaisesRegex(ValueError,'range semantics'):check(policy)
            policy=reset();policy['advisories'].append(copy.deepcopy(policy['advisories'][0]))
            with self.assertRaisesRegex(ValueError,'unique advisory'):check(policy)
            policy=reset();policy['advisories'][0]['file']='../outside.json'
            with self.assertRaisesRegex(ValueError,'identity'):check(policy)


if __name__=='__main__':unittest.main()
