"""Run opt-in native vault integration using the packaged runtime; never emits secrets."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--case', choices=['credentials', 'missing-service', 'locked-service'], default='credentials')
case = parser.parse_args().case
jar = root / "harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar"
java = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
classpath = os.pathsep.join([str(root / "harness-cli/target/test-classes"), str(jar)])
result = subprocess.run([str(java), "-cp", classpath, "io.jharness.cli.CredentialStoreProbe"],
                        capture_output=True, text=True, timeout=180, cwd=root)
try:
    report = json.loads(result.stdout.strip())
except (ValueError, TypeError):
    report = {"passed": False, "checks": {}, "error": "Probe did not return a valid report"}
expected_unavailable = case != 'credentials'
expectation_met = (result.returncode != 0 and report.get('passed') is False
                   and report.get('credentialStore') == 'unavailable'
                   and report.get('checks') == {'cleanup': False}) if expected_unavailable else (
                       result.returncode == 0 and report.get('passed') is True)
sources = [root / "pom.xml"] + sorted(root.glob("harness-*/pom.xml")) + sorted(root.glob("harness-*/src/**/*.java"))
source_manifest = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
report.update({"sourceSha256": source_manifest, "probeScriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
               "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
               "exitCode": result.returncode, "expectedUnavailable": expected_unavailable,
               "expectationMet": expectation_met})
output = root / f".build-cache/M10.5.4-native-{case}.json"
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k, v in report.items() if k != "sourceSha256"} | {"sourceFiles": len(source_manifest)}))
sys.exit(0 if expectation_met else 1)
