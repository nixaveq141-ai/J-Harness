"""Audit the latest server-confirmed attempts of one fixed P1 qualification run."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RUN=43360569439
COMMIT='f03a214ba1df3a23cbcc501730e4e9a3286ea6df'
REPO='nixaveq141-ai/J-Harness'
LABELS=('windows-2022','ubuntu-24.04','macos-latest','ubuntu-24.04-arm','macos-15-intel')
OUT=ROOT/'.build-cache/p1-retry-proof'
FROZEN=ROOT/'.build-cache/p1-retry-qualified-source'
DATA=ROOT/'.build-cache/p1-retry-check'


def selected_jobs(run,jobs):
    if (run['id']!=RUN or run['head_sha']!=COMMIT or run['head_branch']!='codex/m1059-full-validation'
            or run['status']!='completed' or run['path']!='.github/workflows/gap-unified-source-qualification.yml'
            or run['repository']['full_name']!=REPO or type(run['run_attempt']) is not int or run['run_attempt']<1):
        raise ValueError('Fixed completed qualification run required')
    rows={label:[] for label in LABELS};ids=set()
    for j in jobs:
        if j['id'] in ids:raise ValueError('Duplicate job identity')
        ids.add(j['id'])
        if not j['name'].startswith('qualification ('):continue
        label=j['name'].removeprefix('qualification (').removesuffix(')')
        if label not in rows or j['name']!='qualification ('+label+')':raise ValueError('Unknown target')
        if (j['run_id']!=RUN or j['head_sha']!=COMMIT or type(j['run_attempt']) is not int
                or not 1<=j['run_attempt']<=run['run_attempt']):raise ValueError('Job run/commit/attempt drift')
        if any(v['run_attempt']==j['run_attempt'] for v in rows[label]):raise ValueError('Ambiguous target attempt')
        rows[label].append(j)
    selected={}
    for label,items in rows.items():
        if not items:raise ValueError('Missing target')
        job=max(items,key=lambda j:j['run_attempt'])
        if job['status']!='completed' or job['conclusion']!='success':raise ValueError('Latest target attempt not successful')
        selected[label]=job
    return selected


def api(path):
    request=urllib.request.Request('https://api.github.com/repos/'+REPO+'/'+path,headers={
        'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json',
        'User-Agent':'J-Harness-P1-retry-proof','Cache-Control':'no-cache'})
    with urllib.request.urlopen(request,timeout=30) as response:raw=response.read(16*1024**2+1)
    if len(raw)>16*1024**2:raise ValueError('Bounded API response required')
    return json.loads(raw)


def snapshot():
    run=api('actions/runs/'+str(RUN));jobs=[]
    for page in range(1,11):
        data=api('actions/runs/'+str(RUN)+'/jobs?filter=all&per_page=100&page='+str(page));jobs+=data['jobs']
        if len(jobs)==data['total_count']:break
        if len(jobs)>data['total_count']:raise ValueError('Jobs pagination drift')
    else:raise ValueError('Incomplete jobs pagination')
    chosen=selected_jobs(run,jobs)
    artifacts=api('actions/runs/'+str(RUN)+'/artifacts?per_page=100')
    if artifacts['total_count']!=len(artifacts['artifacts']):raise ValueError('Incomplete artifact metadata')
    selected={}
    for label,job in chosen.items():
        found=[a for a in artifacts['artifacts'] if a['name']=='source-native-unified-'+label]
        if len(found)!=1:raise ValueError('One immutable artifact per target required')
        a=found[0]
        if (a['expired'] or a['workflow_run']['id']!=RUN or a['workflow_run']['head_sha']!=COMMIT
                or not job['started_at']<=a['created_at']<=job['completed_at']
                or not a.get('digest','').startswith('sha256:')):raise ValueError('Latest target artifact binding failed')
        selected[label]=a
    return dict(run=run,jobs=jobs,selectedJobs=chosen,selectedArtifacts=selected)


def identities(s):
    return {label:(s['selectedJobs'][label]['id'],s['selectedJobs'][label]['run_attempt'],
                   s['selectedArtifacts'][label]['id'],s['selectedArtifacts'][label]['digest']) for label in LABELS}


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if sys.argv[1]=='prepare':
        proof=snapshot();(OUT/'server-before.json').write_bytes((json.dumps(proof,indent=2)+'\n').encode())
        print('Latest successful target jobs bound:',identities(proof));return
    if sys.argv[1]!='verify':raise ValueError('Use prepare or verify')
    before=json.loads((OUT/'server-before.json').read_bytes());after=snapshot()
    if identities(before)!=identities(after):raise ValueError('Selected server attempts changed during audit')
    (OUT/'server-after.json').write_bytes((json.dumps(after,indent=2)+'\n').encode())
    if subprocess.check_output(['git','rev-parse','HEAD'],cwd=FROZEN).decode().strip()!=COMMIT:
        raise ValueError('Exact qualified source checkout required')
    for name in ['scripts/evidence_checks.py','scripts/evidence_io.py','scripts/evidence_run.py',
                 'scripts/r4_faults.py','scripts/contracts/m1059-java-cases.json',
                 'scripts/contracts/r4-fault-cases.json','scripts/workbench-case-contract.json']:
        if (ROOT/name).read_bytes()!=(FROZEN/name).read_bytes():raise ValueError('Original qualification rules changed')
    spec=importlib.util.spec_from_file_location('equality',ROOT/'scripts/check-unified-qualification.py')
    gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate);gate.ROOT=FROZEN
    attempts={label:str(after['selectedJobs'][label]['run_attempt']) for label in LABELS}
    equality=gate.check(DATA,COMMIT,str(RUN),extended=True,attempts=attempts)
    reviewed=json.loads((FROZEN/'docs/testing/native-security/reviewed-native-source-map.json').read_bytes())['resources']
    inventory=json.loads((FROZEN/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries']
    if len(reviewed)!=48 or len(inventory)!=50:raise ValueError('Exact48/50 inventory required')
    for label in LABELS:
        base=DATA/label;result=json.loads((base/'.build-cache/source-qualification/result.json').read_bytes())
        coverage=result['nativeCoverage']
        if (result['candidateStage']['allNativeReplacements']!=reviewed or coverage['inventoryCount']!=50
                or coverage['reviewedSourceCount']!=48 or coverage['retainedOriginalCount']!=2
                or coverage['fullOsCpeClearance'] is not False):raise ValueError('Exact reviewed native scope required')
        with zipfile.ZipFile(base/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar') as jar:
            for item in inventory:
                name=item['resource'];expected=reviewed[name]['sha256'] if name in reviewed else item['sha256']
                if hashlib.sha256(jar.read(name)).hexdigest()!=expected:raise ValueError('Native inventory bytes drift')
    proof=dict(status='P1_FIVE_TARGET_LATEST_ATTEMPTS_48_RESOURCE_IDENTICAL_RUNTIME_QUALIFIED_NOT_RELEASE',
        qualificationRunId=str(RUN),qualificationCommit=COMMIT,verifierCommit=os.environ.get('GITHUB_SHA'),
        attempts=attempts,selectedIdentities=identities(after),equality=equality,reviewedSourceCount=48,
        nativeInventoryCount=50,retainedOriginalCount=2,releaseApproved=False,paidRequests=0,
        scope='Same qualification run and commit, each target latest server-confirmed successful attempt. Full Java, browser, R4, install, receipt and byte equality checks retained. Two AIX source chains unresolved; P1 not complete.')
    (OUT/'equality.json').write_bytes((json.dumps(proof,indent=2)+'\n').encode());print(proof['status'])


if __name__=='__main__':main()
