"""No provider calls. Fresh real CLI output is compiled only inside pinned Docker."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

spec = importlib.util.spec_from_file_location('isolation', Path(__file__).with_name('isolated-code-evaluation.py'))
isolation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(isolation)
ROOT = isolation.ROOT
CSV_ASSERTIONS = r'''
  String[] good={"", "  ", "1,2,3", " -2, +7 , 0 ", "9223372036854775807", "-9223372036854775808", "9223372036854775807,-9223372036854775807"};
  long[] expected={0,0,6,5,Long.MAX_VALUE,Long.MIN_VALUE,0};
  for(int i=0;i<good.length;i++) if(c.sumCsv(good[i])!=expected[i]) throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  String[] bad={null,",", "1,", ",1", "1,,2", "1,  ,2", "1.5", "0x10", "abc", "9223372036854775808", "-9223372036854775809", "9223372036854775807,1", "-9223372036854775808,-1"};
  for(String input:bad) {
   boolean rejected=false;
   try { c.sumCsv(input); } catch(IllegalArgumentException expectedException) { rejected=true; }
   if(!rejected) throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  }
  var random=new java.util.Random(1059);
  for(int n=0;n<200;n++) {
   var csv=new StringBuilder(); long sum=0;
   for(int j=0;j<20;j++) { int number=random.nextInt(); if(j>0)csv.append(',');csv.append(' ').append(number).append(' ');sum+=number; }
   if(c.sumCsv(csv.toString())!=sum)throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  }
'''
ORACLE = isolation.ORACLE.replace('  System.out.println("ISOLATED_FUNCTIONAL_ORACLE_PASS");',
                                  CSV_ASSERTIONS+'  System.out.println("ISOLATED_FUNCTIONAL_ORACLE_PASS");')


def main():
    if os.name != 'posix':
        raise ValueError('Dedicated Linux Docker CI required; no host compilation')
    docker = shutil.which('docker')
    if not docker: raise ValueError('Docker required')
    source = ROOT/'docs/testing/fixtures/deepseek-fresh-calculator-2026-10-29.txt'
    receipt = source.with_suffix('.provenance.json')
    provenance = json.loads(receipt.read_bytes())
    if isolation.sha(source) != provenance['sourceSha256']:
        raise ValueError('Generated source receipt mismatch')
    raw = source.read_bytes()
    wrong = raw.replace(b'return total;', b'return 0L;')
    if wrong == raw: raise ValueError('Wrong implementation control not constructed')
    digests = subprocess.run([docker,'image','inspect',isolation.IMAGE,'--format','{{json .RepoDigests}}'],
                             check=True,capture_output=True,timeout=20).stdout
    if not any(v.endswith('@'+isolation.IMAGE.split('@')[1]) for v in json.loads(digests)):
        raise ValueError('Pinned image missing')
    directory = ROOT/'.build-cache/fresh-code-isolation'/uuid.uuid4().hex
    directory.mkdir(parents=True)
    tracked = [Path(__file__).resolve(), Path(isolation.__file__).resolve(), source, receipt,
               ROOT/'.github/workflows/gap-fresh-code-isolation.yml']
    report = dict(status='INCOMPLETE', paidRequests=0, commit=os.environ.get('GITHUB_SHA'),
        ciRunId=os.environ.get('GITHUB_RUN_ID'), sourceSha256=isolation.sha(source),
        oracleSha256=__import__('hashlib').sha256(ORACLE.encode()).hexdigest(),
        sourcesBefore={p.relative_to(ROOT).as_posix():isolation.sha(p) for p in tracked}, runs=[])
    try:
        for label, code in [('fresh',raw), ('wrong-answer',wrong)]:
            result = isolation.evaluate(code,directory/label,docker,oracle=ORACLE)
            report['runs'].append(result)
            if result['functionalPass'] != (label=='fresh') or not result['cleanupVerified']:
                raise ValueError('Fresh functional oracle or cleanup failed')
            if label=='wrong-answer' and b'FUNCTIONAL_ORACLE_REJECTED' not in (directory/label/'execution.log').read_bytes():
                raise ValueError('Wrong source must compile and reach behavioral rejection')
        report['sourcesAfter'] = {p.relative_to(ROOT).as_posix():isolation.sha(p) for p in tracked}
        if report['sourcesAfter'] != report['sourcesBefore']: raise ValueError('Source drift')
        report['status'] = 'PASS'
    finally:
        (directory/'evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('PASS fresh code / 220 CSV cases / arithmetic / incorrect code rejected / isolation / cleanup')


if __name__ == '__main__': main()
