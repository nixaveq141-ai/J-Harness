"""Archive validated local evidence without granting the pending CI/M11 gate."""
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.build-cache/M10.5.9'
DEST=ROOT/'docs/testing'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def load(path):return json.loads(path.read_bytes())

def main():
    report=load(CACHE/'runner.json')
    if report['status'] not in ['LOCAL_PARTIAL_CI_PENDING','LOCAL_PASS_CI_PENDING'] or report['m11Eligible'] is not False:
        raise ValueError('Not validated local evidence')
    records={}
    def copy(source,name):
        target=DEST/name;target.write_bytes(source.read_bytes());records[name]=dict(sha256=sha(target),source=source.relative_to(ROOT).as_posix())
    for record in report['browserMatrix']['records']:
        source=ROOT/record['path']
        if sha(source)!=record['sha256']:raise ValueError('Browser snapshot changed')
        copy(source,'M10.5.9-'+record['engine']+'-'+record['fixture']+'-results.json')
    for name in ['sdk','examples','native']:
        copy(CACHE/(name+'.json'),'M10.5.9-'+name+'-results.json')
    copy(CACHE/'browser-runtime.json','M10.5.9-browser-runtime.json')
    if (CACHE/'availability.json').exists():copy(CACHE/'availability.json','M10.5.9-browser-availability.json')
    for name in ['frontend-audit','osv-results','sdk-source-inventory']:
        copy(ROOT/'target'/(name+'.json'),'M10.5.9-'+name+'.json')
    copy(ROOT/'target/candidate-smoke.json','M10.5.9-candidate-smoke.json')
    copy(ROOT/'target/release-evidence.json','M10.5.9-release-evidence.json')
    report.update(archivedRecords=records,remoteCiStatus='NOT_AUTHORIZED_NOT_RUN',
                  outstanding=['Current Windows/Linux/macOS real CI','Actual Firefox browser launch and matrix'],
                  developmentAttempts={p.relative_to(ROOT).as_posix():sha(p) for p in sorted((ROOT/'.dsh-tmp').glob('m1059*.log'))
                                       if p.is_file() and not any(word in p.name for word in ['stage','collect','archive','commit','final-audit'])})
    (DEST/'M10.5.9-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(report['status'],'Java',report['java']['tests'],'browser',report['browserChecks'],'archived',len(records))

if __name__=='__main__':main()
