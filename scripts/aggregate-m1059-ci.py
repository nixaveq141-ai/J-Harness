"""Require three matching, real CI runner reports before granting M11 eligibility."""
import hashlib
import json
from pathlib import Path
import sys

def aggregate(directory):
    reports=[]
    for path in Path(directory).rglob('runner.json'):
        data=json.loads(path.read_bytes())
        if data.get('milestone')=='M10.5.9':reports.append((path,data))
    if len(reports)!=3:raise ValueError('Require exactly three current runner reports')
    values=[r for _,r in reports]
    if {r['runnerOs'] for r in values}!={'Windows','Linux','macOS'}:raise ValueError('Missing OS')
    if any(r['status']!='RUNNER_PASS' or not r.get('ciRunId') or r.get('paidRequests')!=0 or r.get('workingTreeDirty') is not False for r in values):raise ValueError('Not actual clean passing CI')
    for field in ['commit','ciRunId','sourceSha256','uiSha256']:
        if any(r[field]!=values[0][field] for r in values):raise ValueError('Different CI/source/UI: '+field)
    cases=lambda r:sorted((s['name'],c) for s in r['suites'] for c in s['cases'])
    if any(cases(r)!=cases(values[0]) for r in values):raise ValueError('Different Java cases')
    if any(r['java']['failures'] or r['java']['errors'] or r['java']['skipped'] or r['java']['tests']!=410 or r['browserChecks']!=813 or r['sdkChecks']!=58 or r.get('frontendClientChecks')!=33 for r in values):raise ValueError('Incomplete current matrix')
    return dict(milestone='M10.5.9',status='PASS',m11Eligible=True,commit=values[0]['commit'],ciRunId=values[0]['ciRunId'],
                runnerReports={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p,_ in reports},paidRequests=0)

if __name__=='__main__':print(json.dumps(aggregate(sys.argv[1]),indent=2))
