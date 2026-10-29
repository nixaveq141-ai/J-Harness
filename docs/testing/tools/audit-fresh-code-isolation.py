"""Independent audit of the original CI ZIP, source identity and negative control."""
import argparse
import hashlib
import io
import importlib.util
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('fresh_oracle', Path(__file__).with_name('fresh-code-isolated-evaluation.py'))
fresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fresh)


def sha(data): return hashlib.sha256(data).hexdigest()


def audit(path, expected_sha, commit, run):
    def require(condition):
        if not condition: raise ValueError('Original fresh-code evidence rejected')
    with path.open('rb') as stream:
        raw_zip = stream.read(1024*1024+1)
    require(len(raw_zip) <= 1024*1024 and sha(raw_zip) == expected_sha)
    with zipfile.ZipFile(io.BytesIO(raw_zip)) as archive:
        names = archive.namelist()
        require(len(names) == 5 and len(set(names)) == 5)
        require(all(i.file_size <= 65536 and '..' not in Path(i.filename).parts for i in archive.infolist()))
        evidence = [n for n in names if n.endswith('/evidence.json')]
        require(len(evidence) == 1)
        prefix = evidence[0].removesuffix('evidence.json')
        require(set(names) == {prefix+n for n in ['evidence.json','fresh/result.json','fresh/execution.log',
                                                'wrong-answer/result.json','wrong-answer/execution.log']})
        report = json.loads(archive.read(evidence[0]))
        require(report['status'] == 'PASS' and report['paidRequests'] == 0
                and report['commit'] == commit and report['ciRunId'] == str(run))
        require(report['sourcesBefore'] == report['sourcesAfter'] and len(report['sourcesBefore']) == 5)
        source_bytes = {}
        for name, digest in report['sourcesBefore'].items():
            require(not name.startswith('-') and '..' not in Path(name).parts)
            raw = subprocess.run(['git','show',commit+':'+name], cwd=ROOT, check=True,
                                 capture_output=True, timeout=15).stdout
            require(sha(raw) == digest)
            source_bytes[name] = raw
        source = source_bytes['docs/testing/fixtures/deepseek-fresh-calculator-2026-10-29.txt']
        require(report['sourceSha256'] == sha(source))
        require(report['oracleSha256'] == sha(fresh.ORACLE.encode()))
        for index, label in enumerate(['fresh','wrong-answer']):
            result = json.loads(archive.read(prefix+label+'/result.json'))
            require(result == report['runs'][index])
            log = archive.read(prefix+label+'/execution.log')
            require(result['logSha256'] == sha(log))
            require(result['image'] == fresh.isolation.IMAGE and result['status'] == 'COMPLETE'
                    and result['inputsUnchanged'] and result['cleanupVerified'] and result['isolationInspectionPassed']
                    and result['oomKilled'] is False)
            require(result['containerExitCode'] == index and result['functionalPass'] == (index == 0))
            require(result['inputSha256']['Calculator.java'] == sha(source if index == 0 else source.replace(b'return total;',b'return 0L;')))
            require(result['inputSha256']['Oracle.java'] == report['oracleSha256'])
            require(result['inputSha256']['host-canary'] == sha(b'original host canary'))
            require(log.strip() == b'ISOLATED_FUNCTIONAL_ORACLE_PASS' if index == 0 else b'FUNCTIONAL_ORACLE_REJECTED' in log)
            fields = result['isolationFields']
            expected = dict(NetworkMode='none',ReadonlyRootfs=True,CapDrop=['ALL'],Memory=268435456,
                            MemorySwap=268435456,NanoCpus=1000000000,PidsLimit=64,Privileged=False)
            require(all(fields.get(k) == v for k,v in expected.items()))
            require(fields['SecurityOpt'] == ['no-new-privileges:true'] and fields['LogConfig']['Type'] == 'none')
            require(fields['Tmpfs'] == {k:'rw,noexec,nosuid,size=16m,mode=1777' for k in ['/tmp','/work']})
            require(result['mounts'] == [dict(Type='bind',Destination='/input',RW=False)])
        require(len(report['runs']) == 2 and report['runs'][0]['owner'] != report['runs'][1]['owner'])
    return dict(status='PASS', commit=commit, ciRunId=str(run), originalZipSha256=expected_sha,
                sourceSha256=report['sourceSha256'], oracleSha256=report['oracleSha256'],
                csvCases=220, freshFunctionalPass=True, wrongImplementationRejected=True,
                isolationAndCleanupVerified=True, exactSourceInputs=report['sourcesBefore'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('zip', type=Path)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--run', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.zip,args.sha256,args.commit,args.run)
    with args.output.open('x',encoding='utf-8') as stream: json.dump(result,stream,indent=2);stream.write('\n')
    print('PASS original ZIP / exact sources / positive and negative oracle / isolation / cleanup')
