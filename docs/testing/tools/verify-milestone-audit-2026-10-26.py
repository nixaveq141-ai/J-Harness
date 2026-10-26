"""Read-only verification of the dated milestone/documentation audit."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
read=lambda name:json.loads((ROOT/name).read_bytes())
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
def require(value,message):
 if not value:raise ValueError(message)
def main():
 data=read('docs/testing/M10-milestone-audit-2026-10-26.json');manifest=read('docs/testing/M10-documentation-refresh-2026-10-26.json')
 require(len(data['milestones'])==19 and len(data['functionalRequirements'])==27,'Incomplete milestone/FR inventory')
 require(len(data['historicalTestLineage'])==20,'Incomplete historical snapshot inventory')
 require(len(data['r5OpenItems'])==13 and sum(f['state']=='OPEN' for f in data['findings'])==12,'Incorrect open-scope accounting')
 contract=read('scripts/contracts/m1059-java-cases.json');current={(s['module'],s['name'],n) for s in contract['suites'] for n in s['cases']};require(len(current)==702,'Current case contract changed')
 aliases={tuple(a['from']):a for a in data['verifiedIdentityAliases']}
 for row in data['historicalTestLineage']:
  old=read(row['file']);identities=set()
  for s in old['suites']:
   module=s.get('module',s.get('path','').split('/')[0]);suite=s.get('suite',s.get('name',Path(s.get('path','')).name.removeprefix('TEST-').removesuffix('.xml')))
   identities.update((module,suite,n) for n in s['cases'])
  require(len(identities)==row['historicalCases'] and len(identities&current)==row['retainedExact'],'Historical case lineage drift')
  require(sorted(identities-current)==[tuple(x) for x in row['notExactNow']],'Unrecorded case identity change')
  for key in identities-current:require(key in aliases and all(tuple(x) in current for x in aliases[key]['to']),'Missing replacement identities')
 for entry in manifest['documents']:
  require(sha(ROOT/entry['path'])==entry['afterSha256'],'Document drift: '+entry['path'])
 for entry in manifest['outputs']:require(sha(ROOT/entry['path'])==entry['sha256'],'Audit output drift: '+entry['path'])
 for name,digest in manifest['protectedRawJson'].items():require(sha(ROOT/name)==digest,'Historical raw JSON changed: '+name)
 sys.path.insert(0,str(ROOT/'scripts'));from evidence_run import inputs
 old=read('docs/testing/M10.5.9-public-final-2026-10-26-results.json')['sourceSha256'];now=inputs(ROOT)
 require(set(old)==set(now),'Build input set changed')
 changed=sorted(k for k in old if old[k]!=now[k]);require(changed==['docs/examples/m1059-distribution.md'],'Unacknowledged runtime/input change')
 require(changed==[r['path'] for r in data['documentationBuildInputDifferences']],'Documentation binding record drift')
 require(not manifest['unresolvedLocalLinks'],'Unresolved local documentation link')
 print('PASS milestone audit: 19 stages / 27 FR / 20 historical snapshots / 13 R5 open IDs; documents and protected raw JSON unchanged; one declared build-input documentation change')
if __name__=='__main__':main()
