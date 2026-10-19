"""Collect actual clean-verify reports and hash the tested inputs for each runner."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    log = ROOT / sys.argv[1]
    if "[INFO] BUILD SUCCESS" not in log.read_text(encoding="utf-8", errors="replace"):
        raise ValueError("No successful verify log")
    suites = []
    java = set()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [e.text for e in ET.parse(ROOT / "pom.xml").findall("m:modules/m:module", ns)]
    for module in modules:
        for path in sorted((ROOT / module / "target/surefire-reports").glob("TEST-*.xml")):
            if path.stat().st_mtime > log.stat().st_mtime:
                raise ValueError("Reports newer than verify log")
            suite = ET.parse(path).getroot()
            entry = {k: int(suite.get(k, "0")) for k in ("tests", "failures", "errors", "skipped")}
            entry.update(module=module, name=suite.get("name"),
                         cases=sorted(e.get("name") for e in suite.findall("testcase")), sha256=sha(path))
            suites.append(entry)
            java.update(e.get("value") for e in suite.findall("properties/property") if e.get("name") == "java.version")
    totals = {k: sum(s[k] for s in suites) for k in ("tests", "failures", "errors", "skipped")}
    if totals["tests"] < 296 or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise ValueError("Incomplete or failing test evidence: " + str(totals))
    paths = [ROOT / "pom.xml"]
    for module in modules:
        paths += [ROOT / module / "pom.xml"] + sorted((ROOT / module / "src").rglob("*.java"))
    if any(p.stat().st_mtime > log.stat().st_mtime for p in paths):
        raise ValueError("Sources changed after verification")
    source = {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}
    java_cmd = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    milestone = sys.argv[2] if len(sys.argv) > 2 else "M9"
    if milestone not in {"M9", "M10.5.9"}:
        raise ValueError("Unknown validation milestone")
    evidence = {"milestone": milestone, "capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "buildResult": "SUCCESS", "platform": platform.platform(), "architecture": platform.machine(),
                "runnerOs": os.environ.get("RUNNER_OS", "local"), "javaVersions": sorted(java),
                "javaRuntime": subprocess.run([java_cmd, "-version"], capture_output=True, text=True).stderr,
                "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
                "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
                "verificationLog": sys.argv[1],
                "ciRunUrl": ("https://github.com/" + os.environ["GITHUB_REPOSITORY"] + "/actions/runs/" + os.environ["GITHUB_RUN_ID"]) if os.environ.get("GITHUB_RUN_ID") else None,
                "logSha256": sha(log), "totals": totals, "suites": suites, "sourceSha256": source,
                "jarSha256": sha(ROOT / "harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar")}
    (ROOT / "target/release-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(totals)


if __name__ == "__main__":
    main()
