"""Run opt-in native vault integration using the packaged runtime; never emits secrets."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from evidence_io import atomic_write_json, bounded_path, stream_sha256, strict_json_loads

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--case', choices=['credentials', 'missing-service', 'locked-service'], default='credentials')
parser.add_argument('--output')
args = parser.parse_args()
case = args.case
output = bounded_path(root, args.output or f'.build-cache/M10.5.4-native-{case}.json', must_exist=False)
atomic_write_json(output, dict(passed=False, expectationMet=False, status='INCOMPLETE', exitCode=1))
report=dict(passed=False,expectationMet=False,status='INCOMPLETE',exitCode=1)
try:
    jar=bounded_path(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
    before=stream_sha256(jar)
    java=Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')
    if not java.is_file():raise ValueError('Java executable unavailable')
    classpath=os.pathsep.join([str(root/'harness-cli/target/test-classes'),str(jar)])
    result=subprocess.run([str(java),'-cp',classpath,'io.jharness.cli.CredentialStoreProbe'],capture_output=True,timeout=180,cwd=root)
    if len(result.stdout)>65536:raise ValueError('Native probe report limit')
    parsed=strict_json_loads(result.stdout.strip())
    if not isinstance(parsed,dict):raise ValueError('Native probe report object required')
    report.update(parsed)
    expected_unavailable=case!='credentials'
    checks=report.get('checks')
    required={'available','missing','writeRestartRead','rotateRestartRead','deleteRestartRead','deleteMissing','cleanup'}
    expectation_met=(result.returncode!=0 and report.get('passed') is False and report.get('credentialStore')=='unavailable' and checks=={'cleanup':False}) if expected_unavailable else (result.returncode==0 and report.get('passed') is True and isinstance(checks,dict) and set(checks)==required and all(v is True for v in checks.values()))
    sources=[root/'pom.xml']+sorted(root.glob('harness-*/pom.xml'))+sorted(root.glob('harness-*/src/**/*.java'))
    source_manifest={p.relative_to(root).as_posix():stream_sha256(p) for p in sources}
    after=stream_sha256(jar)
    if before!=after:raise ValueError('Native JAR changed during probe')
    report.update(sourceSha256=source_manifest,probeScriptSha256=stream_sha256(Path(__file__)),timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat(),jarSha256=before,jarUnchanged=True,exitCode=result.returncode,expectedUnavailable=expected_unavailable,expectationMet=expectation_met,status='PASS' if expectation_met else 'FAILED')
except (Exception,KeyboardInterrupt) as failure:
    report.update(passed=False,expectationMet=False,status='INCOMPLETE',failureType=type(failure).__name__)
atomic_write_json(output,report)
print(json.dumps({k:v for k,v in report.items() if k!='sourceSha256'}|{'sourceFiles':len(report.get('sourceSha256',{}))}))
sys.exit(0 if report['expectationMet'] else 1)
