"""Two-file generated repair and two independent wrong controls, Docker only."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

spec=importlib.util.spec_from_file_location('isolation',Path(__file__).with_name('isolated-code-evaluation.py'))
isolation=importlib.util.module_from_spec(spec);spec.loader.exec_module(isolation)
ROOT=isolation.ROOT
ASSERTIONS=r'''
  String[] good={"0","12","12.5",".75"," 1.20 ","92233720368547758.07","1.000"};
  long[] values={0,1200,1250,75,120,Long.MAX_VALUE,100};
  for(int i=0;i<good.length;i++)if(example.Price.cents(good[i])!=values[i])throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  String[] bad={null,""," ","-0.01","1.001","92233720368547758.08","NaN","Infinity","abc","1e2147483647"};
  for(String text:bad){boolean rejected=false;try{example.Price.cents(text);}catch(IllegalArgumentException e){rejected=true;}
   if(!rejected)throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");}
  if(example.Invoice.total(new String[0])!=0 || example.Invoice.total(new String[]{"1.25","2.50","0.01"})!=376)throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  String[][] badInvoices={null,new String[]{null},new String[]{"1.001"},new String[]{"92233720368547758.07","0.01"}};
  for(String[] invoice:badInvoices){boolean rejected=false;try{example.Invoice.total(invoice);}catch(IllegalArgumentException e){rejected=true;}
   if(!rejected)throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");}
  var random=new java.util.Random(1059);
  for(int n=0;n<200;n++){String[] invoice=new String[20];long expected=0;
   for(int j=0;j<invoice.length;j++){int cents=random.nextInt(1000000);expected+=cents;invoice[j]=(cents/100)+"."+String.format(java.util.Locale.ROOT,"%02d",cents%100);}
   if(example.Invoice.total(invoice)!=expected)throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");}
'''
ORACLE='public final class Oracle { public static void main(String[] args) throws Exception {\n'+ASSERTIONS+isolation.ORACLE[isolation.ORACLE.index('  if(!java.nio.file.Files.readString'):]


def main():
    if os.name!='posix':raise ValueError('Use Linux Docker CI; no host compilation')
    docker=shutil.which('docker')
    if not docker:raise ValueError('Docker required')
    fixtures=ROOT/'docs/testing/fixtures/deepseek-multifile-2026-10-29'
    provenance=json.loads((fixtures/'provenance.json').read_bytes())
    sources={name:(fixtures/(name+'.txt')).read_bytes() for name in ['Price.java','Invoice.java']}
    if {n:__import__('hashlib').sha256(b).hexdigest() for n,b in sources.items()}!=provenance['sourceSha256']:raise ValueError('Generated source drift')
    subprocess.run([docker,'image','inspect',isolation.IMAGE],check=True,capture_output=True,timeout=20)
    directory=ROOT/'.build-cache/multifile-isolation'/uuid.uuid4().hex;directory.mkdir(parents=True)
    tracked=[Path(__file__).resolve(),Path(isolation.__file__).resolve(),fixtures/'provenance.json',
             *[fixtures/(n+'.txt') for n in sources],ROOT/'.github/workflows/gap-multifile-isolation.yml']
    report=dict(status='INCOMPLETE',paidRequests=0,commit=os.environ.get('GITHUB_SHA'),ciRunId=os.environ.get('GITHUB_RUN_ID'),
        sourcesBefore={p.relative_to(ROOT).as_posix():isolation.sha(p) for p in tracked},runs=[])
    variants=[('fresh',sources),('wrong-price',dict(sources,**{'Price.java':b'package example; public final class Price {public static long cents(String text){return 0;}}'})),
              ('wrong-total',dict(sources,**{'Invoice.java':b'package example; public final class Invoice {public static long total(String[] items){return 0;}}'}))]
    try:
        for label,code in variants:
            result=isolation.evaluate(code['Price.java'],directory/label,docker,oracle=ORACLE,source_name='Price.java',extra_sources={'Invoice.java':code['Invoice.java']})
            report['runs'].append(dict(label=label,**result))
            if result['functionalPass']!=(label=='fresh') or not result['cleanupVerified']:raise ValueError('Multi-file functional oracle/cleanup rejected')
            if label!='fresh' and b'FUNCTIONAL_ORACLE_REJECTED' not in (directory/label/'execution.log').read_bytes():raise ValueError('Wrong source must reach behavior assertion')
        report['sourcesAfter']={p.relative_to(ROOT).as_posix():isolation.sha(p) for p in tracked}
        if report['sourcesBefore']!=report['sourcesAfter']:raise ValueError('Source drift')
        report['status']='PASS'
    finally:(directory/'evidence.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print('PASS multi-file generated repair / two wrong controls / isolation / owned cleanup')


if __name__=='__main__':main()
