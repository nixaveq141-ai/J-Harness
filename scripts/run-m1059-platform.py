"""Run the same packaged runtime matrix on each CI OS. Requires a usable credential service."""
import os
import importlib.util
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.build-cache/M10.5.9'
OUT.mkdir(parents=True,exist_ok=True)
env=dict(os.environ,JHARNESS_PYTHON=sys.executable)
spec=importlib.util.spec_from_file_location('browser_prepare',ROOT/'scripts/prepare-test-browsers.py')
prepare=importlib.util.module_from_spec(spec);spec.loader.exec_module(prepare)
firefox=prepare.prepare()
if firefox:env['JHARNESS_FIREFOX_EXECUTABLE']=str(firefox)
commands=[('native',[sys.executable,'scripts/test-native-credentials.py']),
          ('sdk',['node','sdk/tests/run.mjs']),('examples',['node','sdk/tests/examples.mjs']),
          ('browser',['node','scripts/run-workbench-matrix.mjs'])]
if os.name=='nt': commands.insert(1,('launcher',[sys.executable,'scripts/test-open-workbench.py']))
for name,command in commands:
    with (OUT/(name+'.log')).open('w',encoding='utf-8') as log:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
    if result.returncode: raise SystemExit(name+' failed; see .build-cache/M10.5.9/'+name+'.log')
    print('PASS',name,flush=True)
