"""Collect actual clean-verify reports and hash the tested inputs for each runner."""
import datetime
import argparse
import os
from pathlib import Path
import platform
import subprocess
from evidence_io import atomic_write_json, read_json_snapshot, bounded_path, stream_sha256
from evidence_run import inputs, validate
from evidence_checks import suite_details, xml_document_snapshot

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return stream_sha256(path)


def main(argv=None):
    output = ROOT / 'target/release-evidence.json'
    atomic_write_json(output, dict(status='INCOMPLETE', buildResult='INCOMPLETE'))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', help='Repository-relative verification log')
    parser.add_argument('milestone', nargs='?', default='M10.5.9', choices=['M10.5.9'])
    args = parser.parse_args(argv)
    log = bounded_path(ROOT, args.log)
    source = inputs(ROOT)
    receipt = read_json_snapshot(ROOT, '.build-cache/M10.5.9/commands/verify.json')
    validate(receipt.data, name='verify', log=log, run_id=os.environ.get('JHARNESS_EVIDENCE_RUN_ID'), source=source, cwd='.')
    suites = []
    java = set()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    _, pom = xml_document_snapshot(ROOT / 'pom.xml')
    modules = [e.text for e in pom.findall("m:modules/m:module", ns)]
    for module in modules:
        module_path = bounded_path(ROOT, module)
        for path in sorted((module_path / "target/surefire-reports").glob("TEST-*.xml")):
            if path.stat().st_mtime > log.stat().st_mtime:
                raise ValueError("Reports newer than verify log")
            entry, suite = suite_details(path, module)
            suites.append(entry)
            java.update(e.get("value") for e in suite.findall("properties/property") if e.get("name") == "java.version")
    totals = {k: sum(s[k] for s in suites) for k in ("tests", "failures", "errors", "skipped")}
    if totals["tests"] != 410 or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise ValueError("Incomplete or failing test evidence: " + str(totals))
    paths = [ROOT / "pom.xml"]
    for module in modules:
        paths += [ROOT / module / "pom.xml"] + sorted((ROOT / module / "src").rglob("*.java"))
    if any(p.stat().st_mtime > log.stat().st_mtime for p in paths):
        raise ValueError("Sources changed after verification")
    java_cmd = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    milestone = args.milestone
    runtime = subprocess.run([java_cmd, '-version'], capture_output=True, text=True, timeout=15, check=True)
    if not java or any(not isinstance(v, str) or not v.strip() for v in java):
        raise ValueError('Missing Java version evidence')
    evidence = {"milestone": milestone, "status": "PASS", "runId": receipt.data['runId'], "capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "buildResult": "SUCCESS", "platform": platform.platform(), "architecture": platform.machine(),
                "runnerOs": os.environ.get("RUNNER_OS", "local"), "javaVersions": sorted(java),
                "javaRuntime": runtime.stderr,
                "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
                "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
                "verificationLog": args.log,
                "ciRunUrl": ("https://github.com/" + os.environ["GITHUB_REPOSITORY"] + "/actions/runs/" + os.environ["GITHUB_RUN_ID"]) if os.environ.get("GITHUB_RUN_ID") else None,
                "logSha256": sha(log), "totals": totals, "suites": suites, "sourceSha256": source,
                "jarSha256": sha(ROOT / "harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar")}
    if inputs(ROOT) != source:
        raise ValueError('Build inputs changed during release evidence collection')
    atomic_write_json(output, evidence)
    print(totals)


if __name__ == "__main__":
    main()
