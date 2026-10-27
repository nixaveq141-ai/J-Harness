"""Audit original GitHub ZIP bytes against current inputs and their exact inner evidence."""
import hashlib
import io
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'scripts'))
from evidence_io import bounded_path, stream_sha256, strict_json_loads, atomic_write_json
from evidence_run import inputs
from evidence_checks import suite_snapshot, validate_java_suites, names, CONTRACT
from r4_faults import validate_record as validate_r4, frontend_sources

def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/f'{name}.py');value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

def require(ok,reason):
    if not ok:raise ValueError(reason)

def audit(directory,commit,run_id):
    root=Path(directory).resolve(strict=True)
    acquisition=strict_json_loads((root/'acquisition.json').read_bytes())
    require(len(acquisition)==3,'Exactly three acquired original ZIPs required')
    contract=strict_json_loads((ROOT/'scripts/contracts/m1059-java-cases.json').read_bytes())
    component=strict_json_loads((ROOT/'scripts/workbench-case-contract.json').read_bytes())['fixtures']
    aggregator=module('aggregate-m1059-ci');smoke=module('smoke-candidate')
    extracted=Path(tempfile.mkdtemp(prefix='audit-',dir=root))
    summaries=[]
    for artifact in acquisition:
        archive=bounded_path(root,artifact['file']);digest=stream_sha256(archive,max_bytes=256*1024*1024)
        require(digest==artifact['sha256'],'Acquired ZIP drift')
        if artifact.get('githubDigest'):require(artifact['githubDigest']=='sha256:'+digest,'GitHub ZIP digest mismatch')
        require(artifact['runId']==str(run_id) and artifact['commit']==commit,'Acquisition run/commit mismatch')
        target=extracted/archive.stem;target.mkdir()
        expanded=0;seen=set()
        with zipfile.ZipFile(archive) as z:
            require(len(z.infolist())<=10000,'Artifact entry limit')
            for entry in z.infolist():
                name=entry.filename.rstrip('/')
                smoke.candidate_name(name)
                if entry.is_dir():continue
                require(name.casefold() not in seen,'Duplicate artifact path');seen.add(name.casefold())
                require(entry.file_size<=256*1024*1024,'Artifact member limit')
                expanded+=entry.file_size;require(expanded<=1024*1024*1024,'Artifact expansion limit')
                path=bounded_path(target,name,must_exist=False);path.parent.mkdir(parents=True,exist_ok=True)
                with z.open(entry) as incoming,path.open('xb') as output:
                    actual=0
                    for chunk in iter(lambda:incoming.read(65536),b''):
                        actual+=len(chunk);require(actual<=entry.file_size,'Artifact actual expansion mismatch');output.write(chunk)
                    require(actual==entry.file_size,'Artifact size mismatch')
        runner_path=target/'.build-cache/M10.5.9/runner.json'
        report=strict_json_loads(runner_path.read_bytes());aggregator.validate_report(report,contract)
        require(report['commit']==commit and report['ciRunId']==str(run_id),'Runner run/commit mismatch')
        require(report['sourceSha256']==inputs(ROOT),'Runner/current build input mismatch')
        def verify(path,expected):
            aliases={'.build-cache/M10.5.8-sdk-results.json':'.build-cache/M10.5.9/sdk.json','.build-cache/M10.5.8-examples-results.json':'.build-cache/M10.5.9/examples.json'}
            if (target/path).is_file():actual=bounded_path(target,path)
            elif path in aliases:actual=bounded_path(target,aliases[path])
            elif path in report['sourceSha256'] or path=='frontend/node_modules/playwright/package.json':actual=bounded_path(ROOT,path)
            else:raise ValueError('Missing original inner artifact: '+path)
            require(stream_sha256(actual)==expected,'Inner artifact drift: '+path)
        verify('verify.log',report['verificationLogSha256'])
        suites=[suite_snapshot(target/s['module']/'target/surefire-reports'/('TEST-'+s['name']+'.xml'),s['module']) for s in contract['suites']]
        require(sorted(suites,key=lambda s:(s['module'],s['name']))==sorted(report['suites'],key=lambda s:(s['module'],s['name'])),'Original XML/runner suite mismatch')
        expected_java=sum(len(suite['cases']) for suite in contract['suites'])
        require(validate_java_suites(suites,contract)==expected_java,'Java case identities incomplete')
        for name,digest in report['supplyChain'].items():verify('target/'+name,digest)
        for name,digest in report['archiveInputs'].items():verify(name,digest)
        for name,digest in report['records'].items():verify(name,digest)
        for name,digest in report['commandReceipts'].items():
            verify(name,digest);receipt=strict_json_loads((target/name).read_bytes())
            require(receipt['status']=='PASS' and receipt['exitCode']==0 and receipt['runId']==report['runId'],'Failed or foreign command receipt')
            require(receipt['sourceSha256']==receipt['sourceAfterSha256']==report['sourceSha256'],'Command source mismatch')
            verify('.build-cache/M10.5.9/'+receipt['name']+'.log' if receipt['name']!='verify' else 'verify.log',receipt['logSha256'])
            for output,digest in receipt['outputs'].items():
                if output=='harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar':require(digest==report['jarSha256'],'Receipt JAR mismatch')
                else:verify(output,digest)
        for engine in ['chromium','firefox','webkit']:
            for fixture,expected in component.items():
                path=target/f'.build-cache/M10.5.9/browser/{engine}/{fixture}.json'
                value=strict_json_loads(path.read_bytes())
                require(value['status']=='PASS' and value['runId']==report['runId'] and value['jarSha256']==report['jarSha256'],'Browser identity mismatch')
                names([c['name'] for c in value['checks']],expected,'Browser case identities')
                require(all(c['passed'] is True for c in value['checks']),'Browser case failed')
                for screenshot in value['screenshots']:verify(screenshot['path'],screenshot['sha256'])
        for name in ['sdk','native','examples']:
            value=strict_json_loads((target/f'.build-cache/M10.5.9/{name}.json').read_bytes())
            require(value['jarSha256']==report['jarSha256'],'Component JAR mismatch')
            if name=='sdk':
                require(value['status']=='PASS' and value['allAuthorized'] is True and value['modelContextRedacted'] is True,'SDK authorization/redaction')
                for language in value['results']:names(language['checks'],CONTRACT['sdk'][language['language']],'SDK cases')
            if name=='native':require(value['passed'] is True and set(value['checks'])==set(CONTRACT['native']) and all(v is True for v in value['checks'].values()),'Native cases')
            if name=='examples':require(value['status']=='PASS' and len(value['checks'])==2 and all(v['passed'] is True for v in value['checks']),'Example cases')
        r4_contract=strict_json_loads((ROOT/'scripts/contracts/r4-fault-cases.json').read_bytes())
        require(set(report['r4Faults']['engineChecks'])=={'chromium','firefox','webkit'},'Missing R4 engine')
        for engine in ['chromium','firefox','webkit']:
            path=f'.build-cache/R4/{report["runId"]}/browser-{engine}.json'
            verify(path,report['r4Faults']['records'][path])
            value=strict_json_loads((target/path).read_bytes())
            count=validate_r4(value,r4_contract['cases'],engine=engine,run_id=report['runId'],sources=frontend_sources(ROOT))
            require(report['r4Faults']['engineChecks'][engine]==count,'Wrong R4 count')
            verify(value['screenshot']['path'],value['screenshot']['sha256'])
        candidate=target/'target/jharness-0.1.0-SNAPSHOT-candidate.zip'
        manifest,files=smoke.validate_candidate(candidate.read_bytes(),smoke.required_legal_files(ROOT))
        require(manifest['gitCommit']==commit and manifest['workingTreeDirty'] is False,'Candidate source identity')
        require(hashlib.sha256(files['lib/jharness.jar']).hexdigest()==report['jarSha256'],'Candidate JAR mismatch')
        require(manifest['sourceSha256']==report['sourceSha256'] and manifest['uiSha256']==report['uiSha256'],'Candidate input/UI mismatch')
        with zipfile.ZipFile(io.BytesIO(files['lib/jharness.jar'])) as jar:
            for name,digest in report['uiSha256'].items():require(hashlib.sha256(jar.read('ui/'+name)).hexdigest()==digest,'Candidate packaged UI mismatch')
        for filename in ['osv-results.json','source-secrets.json','candidate-smoke.json']:
            require(strict_json_loads((target/'target'/filename).read_bytes())['status']=='PASS','Supply-chain gate failed')
        require(strict_json_loads((target/'target/frontend-audit.json').read_bytes())['metadata']['vulnerabilities']['total']==0,'npm findings')
        summaries.append(dict(os=report['runnerOs'],archiveSha256=artifact['sha256'],artifactId=artifact['artifactId'],runnerSha256=stream_sha256(runner_path),jarSha256=report['jarSha256'],java=report['java'],browserChecks=report['browserChecks'],sdkChecks=report['sdkChecks'],nativeChecks=report['nativeChecks']))
    combined=aggregator.aggregate(extracted)
    require(combined['m11Eligible'] is True,'Incomplete aggregate')
    result=dict(schemaVersion=1,status='PASS',commit=commit,ciRunId=str(run_id),m11Eligible=True,paidRequests=0,runners=summaries,sourceSha256=inputs(ROOT),extractDirectory=str(extracted),aggregate=combined)
    atomic_write_json(root/'audited.json',result)
    print('PASS original three-runner ZIP/XML/case/JAR/browser/SDK/native/candidate audit')
    return result

if __name__=='__main__':audit(*sys.argv[1:])
