"""Check refusal of reused resources and cleanup after guest/Docker failures."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

s=importlib.util.spec_from_file_location('probe',Path(__file__).with_name('probe-p1-sparc-toolchain.py'))
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)

class Isolation(unittest.TestCase):
    def invoke(self,root,*,existing=False,fail=(),provision=False):
        calls=[]
        def run(argv,**kw):
            calls.append((kw['name'],argv))
            if kw['name'] in fail:raise RuntimeError(kw['name'])
        with patch.object(m,'ROOT',root),patch.object(m.platform,'system',return_value='Linux'),\
             patch.dict(m.os.environ,{'GITHUB_ACTIONS':'true','GITHUB_RUN_ID':'123','GITHUB_RUN_ATTEMPT':'1','GITHUB_SHA':'a'*40,'JHARNESS_SPARC_PROVISION':'1' if provision else '0'}),\
             patch.object(Path,'is_char_device',return_value=True),\
             patch.object(m.shutil,'disk_usage',return_value=SimpleNamespace(free=20*1024**3)),\
             patch.object(m.subprocess,'run',return_value=SimpleNamespace(returncode=0 if existing else 1)),\
             patch.object(m,'run',side_effect=run):
            with self.assertRaises((RuntimeError,ValueError)):m.main()
        return calls,json.loads((root/'.build-cache/p1-sparc-toolchain/report.json').read_bytes())

    def test_existing_resource_is_never_removed(self):
        with tempfile.TemporaryDirectory() as folder:
            calls,report=self.invoke(Path(folder),existing=True)
            self.assertEqual([],calls)
            self.assertFalse(report['productAccepted'])

    def test_volume_cleanup_survives_guest_and_container_cleanup_failures(self):
        with tempfile.TemporaryDirectory() as folder:
            calls,report=self.invoke(Path(folder),fail=('guest-tools','owned-container-cleanup'))
            self.assertEqual('owned-volume-cleanup',calls[-1][0])
            self.assertEqual(['owned-container-cleanup'],[x['name'] for x in report['cleanupFailures']])
            create=next(argv for name,argv in calls if name=='owned-container')
            self.assertEqual('none',create[create.index('--network')+1])
            self.assertNotIn('--privileged',create)
            self.assertNotIn('--cap-add',create)
            self.assertNotIn('--device',create)
            self.assertFalse(report['productAccepted'])

    def test_opt_in_network_keeps_capabilities_and_device_scoped(self):
        with tempfile.TemporaryDirectory() as folder:
            calls,report=self.invoke(Path(folder),provision=True,fail=('guest-tools',))
            create=next(argv for name,argv in calls if name=='owned-container')
            self.assertEqual('NET_ADMIN',create[create.index('--cap-add')+1])
            self.assertEqual('/dev/ppp',create[create.index('--device')+1])
            self.assertNotIn('--privileged',create)
            self.assertNotIn('--network',create)
            self.assertEqual('owned-volume-cleanup',calls[-1][0])
            self.assertFalse(report['productAccepted'])

if __name__=='__main__':unittest.main()
