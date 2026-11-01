"""Require exact runtime JAR byte equality across all three qualification jobs."""
import hashlib
import json
import os
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def check(directory,commit,run,attempt='1'):
    results={}
    for label in ['windows-2022','ubuntu-24.04','macos-latest']:
        base=directory/label
        r=json.loads((base/'.build-cache/source-qualification/result.json').read_bytes())
        if (r['commit']!=commit or r['runId']!='unified-source-'+run+'-'+attempt+'-'+label+'-'+commit
                or not r['status'].endswith('_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE')
                or r['javaTests']!=735 or len(r['javaSuites'])!=124 or r['paidRequests']!=0):
            raise ValueError('Successful same-commit same-run full qualification required')
        jar=base/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        digest=hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest!=r['candidateStage']['runtimeJarSha256']:raise ValueError('Qualified JAR bytes changed')
        results[label]=dict(runtimeJarSha256=digest,candidateZipSha256=r['candidateZipSha256'])
    if len({v['runtimeJarSha256'] for v in results.values()})!=1:
        raise ValueError('All three qualified runtime JARs must be byte-identical')
    return dict(status='THREE_PLATFORM_IDENTICAL_RUNTIME_JAR_QUALIFIED_NOT_RELEASE',
                commit=commit,runId=run,platforms=results,releaseApproved=False,
                scope='Byte-identical runtime only; outer candidate ZIPs include platform-specific evidence. Not full native provenance or OS/CPE certification.')


def main():
    r=check(ROOT/'.build-cache/unified-check',os.environ['GITHUB_SHA'],os.environ['GITHUB_RUN_ID'],os.environ['GITHUB_RUN_ATTEMPT'])
    output=ROOT/'.build-cache/unified-equality.json';output.write_bytes((json.dumps(r,indent=2)+'\n').encode());print(r['status'])


if __name__=='__main__':main()
