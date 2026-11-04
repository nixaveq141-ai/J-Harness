"""Observe one exact failed candidate fixture; no retries or repaired assertions."""
import json
import os
from pathlib import Path
import shutil
import subprocess
from evidence_run import run
from evidence_io import atomic_write_json, stream_sha256

ROOT=Path(__file__).resolve().parents[1]


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated CI required')
    original=json.loads((ROOT/'docs/testing/gap-p1-2026-11-04/unified19-first-windows.json').read_bytes())
    source=ROOT/'.build-cache/p1-turn-input/harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    if source.is_symlink() or stream_sha256(source)!=original['runtimeJarSha256']:raise ValueError('Exact failed candidate JAR required')
    jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';jar.parent.mkdir(parents=True,exist_ok=True)
    if jar.exists():raise ValueError('Fresh runtime staging required')
    shutil.copyfile(source,jar)
    out=ROOT/'.build-cache/p1-turn-diagnostic';out.mkdir(exist_ok=False)
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),
        runtimeSourceRun='41824728486',runtimeSourceCommit=original['commit'],runtimeJarSha256=original['runtimeJarSha256'],paidRequests=0,rootCauseEstablished=False)
    try:
        run(['node','frontend/tests/turns.mjs'],name='exact-turn-fixture',log=out/'fixture.log',receipt=out/'command.json',run_id='p1-turn-'+os.environ['GITHUB_RUN_ID'],
            env={**os.environ,'JHARNESS_BROWSER_ENGINE':'webkit'},timeout=300)
        result=json.loads((ROOT/'.build-cache/M10.5.3-turns-results.json').read_bytes())
        if result['status']!='PASS' or result['paidRequests']!=0 or result['jarSha256']!=report['runtimeJarSha256'] or len(result['checks'])!=15:raise ValueError('Original fifteen turn checks required')
        report['status']='EXACT_FAILED_CANDIDATE_OBSERVATION_PASS_NOT_ROOT_CAUSE'
    finally:
        if stream_sha256(jar)!=report['runtimeJarSha256']:report['status']='RUNTIME_DRIFT'
        atomic_write_json(out/'report.json',report)
    print(report['status'])


if __name__=='__main__':main()
