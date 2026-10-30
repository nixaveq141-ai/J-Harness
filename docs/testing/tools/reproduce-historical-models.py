"""Instrument the exact historical models fixture; run only trusted product code, no paid calls."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[3]
REVISION='11e4024c090bf1c63c0adecc823de8a86c541d69'


def instrument(source):
    replacements=[
        ("import assert from 'node:assert/strict'", "import assert from 'node:assert/strict'\nimport { installModelInputTrace } from './model-input-trace.mjs'"),
        (';page=await context.newPage();',';await context.addInitScript(installModelInputTrace);page=await context.newPage();'),
        ('} finally { if(browser)', "} finally { if(page)await writeFile(join(out,'historical-input-trace.json'),JSON.stringify(await page.evaluate(()=>window.__jhModelInputTrace??[]).catch(()=>[]))).catch(()=>{}); if(browser)")]
    for old,new in replacements:
        if source.count(old)!=1:raise ValueError('Historical injection anchor mismatch')
        source=source.replace(old,new,1)
    return source


def main():
    if os.environ.get('RUNNER_OS')!='macOS':raise ValueError('Use dedicated historical macOS CI')
    target=ROOT/'.build-cache/historical-models-src'
    original=subprocess.run(['git','show',REVISION+':frontend/tests/models.mjs'],cwd=ROOT,check=True,capture_output=True).stdout
    fixture=target/'frontend/tests/models.mjs'
    if fixture.read_bytes()!=original:raise ValueError('Historical source drift')
    fixture.write_bytes(instrument(original.decode()).encode())
    shutil.copyfile(ROOT/'frontend/tests/model-input-trace.mjs',target/'frontend/tests/model-input-trace.mjs')
    output=ROOT/'.build-cache/historical-models-evidence';output.mkdir()
    env={**os.environ,'JHARNESS_BROWSER_ENGINE':'webkit'}
    report=dict(status='INCOMPLETE',historicalRevision=REVISION,currentCommit=os.environ.get('GITHUB_SHA'),
        ciRunId=os.environ.get('GITHUB_RUN_ID'),originalFixtureSha256=hashlib.sha256(original).hexdigest(),
        instrumentedFixtureSha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),paidRequests=0,rounds=[])
    try:
        for index in range(1,11):
            directory=output/str(index);directory.mkdir()
            resultfile=target/'.build-cache/M10.5.4-models-results.json'
            tracefile=target/'.build-cache/historical-input-trace.json'
            resultfile.unlink(missing_ok=True);tracefile.unlink(missing_ok=True)
            result=subprocess.run(['node','frontend/tests/models.mjs'],cwd=target,env=env,capture_output=True,timeout=240)
            if len(result.stdout)+len(result.stderr)>1024*1024:raise ValueError('Diagnostic log bound')
            (directory/'fixture.log').write_bytes(result.stdout+result.stderr)
            for file in [resultfile,tracefile]:
                if file.exists():shutil.copyfile(file,directory/file.name)
            row=dict(round=index,exitCode=result.returncode,
                files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.iterdir()})
            report['rounds'].append(row)
            if result.returncode:
                report['status']='HISTORICAL_FAILURE_REPRODUCED_REQUIRES_CAUSAL_AUDIT'
                break
            value=json.loads(resultfile.read_bytes())
            if value['status']!='PASS' or len(value['checks'])!=22 or not all(c['passed'] for c in value['checks']):raise ValueError('Original checks rejected')
        else:report['status']='NOT_REPRODUCED_IN_10_ROUNDS'
    finally:(output/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
