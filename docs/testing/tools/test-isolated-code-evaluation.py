"""Offline fail-closed checks; no Docker, compilation or provider requests."""
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('isolated',Path(__file__).with_name('isolated-code-evaluation.py'))
isolated=importlib.util.module_from_spec(spec);spec.loader.exec_module(isolated)


def inspection(owner,directory):
    return dict(Name='/'+owner,Config=dict(Labels={'io.jharness.code-eval':owner},Image=isolated.IMAGE,User='65534:65534'),
        HostConfig=dict(NetworkMode='none',ReadonlyRootfs=True,CapDrop=['ALL'],Memory=256*1024*1024,
            MemorySwap=256*1024*1024,NanoCpus=1000000000,PidsLimit=64,Privileged=False,
            SecurityOpt=['no-new-privileges:true'],LogConfig={'Type':'none'},
            Tmpfs={p:'rw,noexec,nosuid,size=16m' for p in ['/work','/tmp']}),
        Mounts=[dict(Type='bind',RW=False,Destination='/input',Source=str(directory.resolve())),
                dict(Type='tmpfs',RW=True,Destination='/work'),dict(Type='tmpfs',RW=True,Destination='/tmp')],
        State=dict(ExitCode=0,OOMKilled=False))


class IsolationTest(unittest.TestCase):
    def test_rejects_each_weakened_boundary(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d);valid=inspection('owner',directory)
            isolated.validate_inspection(valid,'owner',directory)
            for key,value in [('NetworkMode','host'),('ReadonlyRootfs',False),('Privileged',True),
                              ('CapDrop',[]),('Memory',0),('MemorySwap',-1),('NanoCpus',0),
                              ('PidsLimit',0),('SecurityOpt',[]),('LogConfig',{'Type':'json-file'})]:
                with self.subTest(key=key),self.assertRaises(ValueError):
                    invalid=copy.deepcopy(valid);invalid['HostConfig'][key]=value
                    isolated.validate_inspection(invalid,'owner',directory)
            for mutate in [lambda v:v['Config'].update(User='0'),
                           lambda v:v['Config']['Labels'].clear(),
                           lambda v:v['Mounts'][0].update(RW=True),
                           lambda v:v['Mounts'].append(dict(Type='bind',Destination='/host',RW=True)),
                           lambda v:v['HostConfig']['Tmpfs'].update({'/work':'rw,size=1g'})]:
                invalid=copy.deepcopy(valid);mutate(invalid)
                with self.assertRaises(ValueError):isolated.validate_inspection(invalid,'owner',directory)

    def test_all_compilation_stays_in_docker_and_cleanup_is_owned(self):
        with tempfile.TemporaryDirectory() as temp:
            directory=Path(temp)/'run';calls=[];popen=[]
            def run(command,**kwargs):
                calls.append(command);self.assertEqual('fake-docker',command[0])
                self.assertNotIn('DEEPSEEK_API_KEY',kwargs['env'])
                if command[1]=='create':
                    self.assertIn('--read-only',command);self.assertIn('--pull=never',command)
                    self.assertTrue(command[-1].startswith('javac -J-Xmx64m -proc:none'))
                    self.owner=command[command.index('--name')+1]
                data=[inspection(self.owner,directory/'input')] if command[1]=='inspect' else []
                return types.SimpleNamespace(stdout=json.dumps(data).encode() if command[1]=='inspect' else b'')
            class Process:
                def __init__(self,command,**kwargs):
                    popen.append(command)
                    self.stdout=io.BytesIO(b'ISOLATED_FUNCTIONAL_ORACLE_PASS\n')
                def poll(self):return 0
                def wait(self,**kwargs):return 0
            with patch.object(isolated.subprocess,'run',side_effect=run),patch.object(isolated.subprocess,'Popen',Process):
                report=isolated.evaluate(b'not compiled by this offline fixture',directory,'fake-docker')
            self.assertTrue(report['functionalPass']);self.assertTrue(report['cleanupVerified'])
            self.assertEqual(1,len(popen));self.assertEqual(['fake-docker','start','--attach',self.owner],popen[0])
            self.assertIn(['fake-docker','rm','--force',self.owner],calls)
            self.assertLessEqual((directory/'execution.log').stat().st_size,isolated.MAX_LOG)

    def test_historical_source_matches_fixed_original_receipt(self):
        source=isolated.ROOT/'docs/testing/fixtures/historical-calculator.txt'
        manifest=json.loads(source.with_suffix('.provenance.json').read_bytes())
        original=json.loads((isolated.ROOT/manifest['originalRecord']).read_bytes())['runs'][0]
        self.assertEqual(original['sourceSha256']['src/main/java/example/Calculator.java'],isolated.sha(source))
        self.assertEqual(manifest['sourceSha256'],isolated.sha(source))

    def test_output_overflow_is_bounded_and_foreign_ownership_is_never_removed(self):
        for bad_owner in [False,True]:
            with self.subTest(bad_owner=bad_owner),tempfile.TemporaryDirectory() as temp:
                directory=Path(temp)/'run';calls=[]
                def run(command,**kwargs):
                    calls.append(command)
                    if command[1]=='create':self.owner=command[command.index('--name')+1]
                    value=inspection(self.owner,directory/'input')
                    if bad_owner:value['Config']['Labels']['io.jharness.code-eval']='foreign-owner'
                    return types.SimpleNamespace(stdout=json.dumps([value]).encode() if command[1]=='inspect' else b'')
                class Process:
                    def __init__(self,*args,**kwargs):self.stdout=io.BytesIO(b'X'*(isolated.MAX_LOG+8192))
                    def poll(self):return 0
                    def wait(self,**kwargs):return 0
                with patch.object(isolated.subprocess,'run',side_effect=run),patch.object(isolated.subprocess,'Popen',Process):
                    with self.assertRaises(ValueError):isolated.evaluate(b'untrusted',directory,'fake-docker')
                result=json.loads((directory/'result.json').read_bytes())
                self.assertEqual('INCOMPLETE',result['status'])
                if bad_owner:
                    self.assertFalse(any(c[1] in ['rm','kill'] for c in calls))
                else:
                    self.assertTrue(result['cleanupVerified'])
                    self.assertEqual(isolated.MAX_LOG,(directory/'execution.log').stat().st_size)


if __name__=='__main__':unittest.main()
