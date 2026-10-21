#!/usr/bin/env python3
"""Verify M8 closure against current sources, full suites, logs and the final packaged JAR.

Reads local evidence only; never runs a model, tool, container or recovery operation.
"""

# Historical acceptance is immutable; retained code below is reference only.
_FROZEN_MESSAGE = 'HISTORICAL_EVIDENCE_FROZEN: historical verifier; retained implementation is reference only and cannot be invoked by CLI, import, or runpy. Preserve original M8 acceptance bytes. Reproduction requires its original recorded revision and isolated evidence copy; current M10.5.9 evidence cannot prove M8. '
if __name__ == '__main__':
    import sys
    sys.stderr.write(_FROZEN_MESSAGE + '\n')
    raise SystemExit(2)
raise RuntimeError(_FROZEN_MESSAGE)
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
IMAGE = "eclipse-temurin@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef"
PROBES = ("environment", "skills", "subagent", "web", "filter", "scheduling", "resilience")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def local(name):
    path = (ROOT / name).resolve()
    require(path.is_relative_to(ROOT), "Evidence path escapes workspace")
    return path


def full_run(report, platform):
    require(report.get("milestone") == "M8" and report.get("slice") == "M8.4", platform + ": wrong slice")
    require(report.get("buildResult") == "SUCCESS", platform + ": build did not pass")
    totals = report["totals"]
    suites = report["suites"]
    require(suites and totals["tests"] >= 295, platform + ": incomplete full suite")
    require(len({s["suite"] for s in suites}) == len(suites), platform + ": duplicate suites")
    for field in ("tests", "failures", "errors", "skipped"):
        require(sum(s[field] for s in suites) == totals[field], platform + ": inconsistent totals")
    require(all(totals[k] == 0 for k in ("failures", "errors", "skipped")), platform + ": tests did not all pass")
    require(all(len(s["cases"]) == s["tests"] for s in suites), platform + ": missing test cases")
    log = local(report["log"])
    require(sha(log) == report["logSha256"], platform + ": log hash mismatch")
    require("[INFO] BUILD SUCCESS" in log.read_text(encoding="utf-8", errors="replace"), platform + ": no build success in log")
    require("[INFO] BUILD FAILURE" not in log.read_text(encoding="utf-8", errors="replace"), platform + ": failed build mixed into log")
    return {s["suite"]: sorted(s["cases"]) for s in suites}


def verify(evidence, jar):
    windows_path = evidence / "M8-results.json"
    linux_path = evidence / "M8-linux-results.json"
    windows, linux = read(windows_path), read(linux_path)
    win_cases, linux_cases = full_run(windows, "Windows"), full_run(linux, "Linux")
    require(win_cases == linux_cases, "Windows/Linux test cases differ")
    require(windows["javaVersion"].startswith("21."), "Windows must use Java 21")
    require(linux["exitCode"] == 0 and linux["javaMajor"] == 21, "Linux did not complete on Java 21")
    require(linux["cleanupVerified"] is True, "Linux build container cleanup not verified")
    pom = ET.parse(ROOT / "pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [m.text for m in pom.findall("m:modules/m:module", ns)]
    files = [ROOT / "pom.xml"]
    for module in modules:
        files += [ROOT / module / "pom.xml", *sorted((ROOT / module / "src").rglob("*.java"))]
    current = {p.relative_to(ROOT).as_posix(): sha(p) for p in files}
    require(set(linux["sourceSha256"]) == set(current), "Linux source file set differs")
    for name, digest in current.items():
        require(windows["sourceSha256"].get(name) == digest, "Windows source mismatch: " + name)
        require(linux["sourceSha256"].get(name) == digest, "Linux source mismatch: " + name)
    for name, digest in windows["sourceSha256"].items():
        require(sha(local(name)) == digest, "Source/document changed after evidence: " + name)
    require("scripts/verify-m8-closure.py" in windows["sourceSha256"], "Verifier is absent from source evidence")
    jar_sha = sha(jar)
    bindings = {}
    for probe in PROBES:
        path = evidence / ("M8.4-" + probe + "-results.json")
        report = read(path)
        require(report.get("result") == "SUCCESS", probe + ": probe did not pass")
        require(report.get("actualDocker") is True and report.get("cleanupVerified") is True, probe + ": real Docker/cleanup missing")
        require(report.get("image") == IMAGE, probe + ": wrong production image")
        require(report.get("jarSha256") == jar_sha, probe + ": stale or different JAR")
        captured = datetime.fromisoformat(report["capturedAt"].replace("Z", "+00:00"))
        require(captured.timestamp() >= jar.stat().st_mtime, probe + ": probe predates final JAR")
        bindings[path.name] = sha(path)
    snapshot_sha = hashlib.sha256(json.dumps(current, sort_keys=True).encode()).hexdigest()
    return {
        "milestone": "M8", "slice": "M8.4", "result": "SUCCESS",
        "scope": "M8 contracted local closure; not a release/remote-CI certification",
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "jarSha256": jar_sha, "javaPomFiles": len(current), "sourceSnapshotSha256": snapshot_sha,
        "windowsTotals": windows["totals"], "linuxTotals": linux["totals"],
        "sameTestCases": True, "sameJavaPomSources": True,
        "productionImage": IMAGE, "actualDocker": True, "cleanupVerified": True,
        "evidenceSha256": {windows_path.name: sha(windows_path), linux_path.name: sha(linux_path), **bindings},
        "verifierSha256": sha(Path(__file__)),
        "paidModelRequests": False, "remoteCi": "not run", "macos": "not verified",
        "releaseComplete": False, "next": "M9 release preparation and platform validation"
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", type=Path, default=ROOT / "docs/testing")
    parser.add_argument("--jar", type=Path, default=ROOT / "harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/testing/M8-closure-results.json")
    args = parser.parse_args()
    try:
        result = verify(args.evidence_dir, args.jar)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("M8.4 closure SUCCESS: same sources, full suites and seven final-JAR probes")
    except (ValueError, KeyError, TypeError, OSError) as error:
        print("M8.4 closure REJECTED: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
