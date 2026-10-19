"""Build a hash-bound binary candidate after verify, legal inventory and OSV scan."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    bom = (ROOT / "target/bom.json").read_bytes()
    scan = (ROOT / "target/osv-results.json").read_bytes()
    result = json.loads(scan)
    if result["status"] != "PASS" or result["bomSha256"] != digest(bom):
        raise ValueError("Candidate requires a complete, passing scan of this BOM")
    inventory = json.loads((ROOT / "third-party/inventory.json").read_bytes())
    external = {f'{c["group"]}:{c["name"]}:{c["version"]}': c for c in json.loads(bom)["components"]
                if c["group"] != "io.jharness"}
    if set(external) != {i["coordinate"] for i in inventory}:
        raise ValueError("BOM / legal inventory coordinate mismatch")
    for item in inventory:
        hashes = {h["alg"]: h["content"] for h in external[item["coordinate"]]["hashes"]}
        if item["jarSha256"] != hashes["SHA-256"]:
            raise ValueError("BOM / original JAR mismatch: " + item["coordinate"])
        for legal in item["legal"]:
            if digest((ROOT / "third-party" / legal["file"]).read_bytes()) != legal["sha256"]:
                raise ValueError("Legal resource mismatch: " + legal["file"])
    for upstream in json.loads((ROOT / "third-party/upstream/sources.json").read_bytes()):
        if digest((ROOT / "third-party/upstream" / upstream["file"]).read_bytes()) != upstream["sha256"]:
            raise ValueError("Upstream legal resource mismatch")
    jar = ROOT / "harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar"
    files = {"lib/jharness.jar": jar.read_bytes(), "bom.json": bom, "osv-results.json": scan}
    for name in ["LICENSE", "NOTICE", "docs/examples/deepseek-config.json", "docs/examples/m7-cli.md"]:
        files[name] = (ROOT / name).read_bytes()
    for path in sorted((ROOT / "third-party").rglob("*")):
        if path.is_file():
            files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    sdk_bom = json.loads((ROOT / "target/sdk-source-inventory.json").read_bytes())
    if sdk_bom["bomSha256"] != digest((ROOT / "target/sdk-bom.json").read_bytes()):
        raise ValueError("SDK BOM/source inventory mismatch")
    if sdk_bom.get("externalRuntimeDependencyCount") != 0:
        raise ValueError("Unexpected SDK dependency inventory")
    for name, expected in sdk_bom["sourceSha256"].items():
        if not name.startswith("sdk/") or ".." in Path(name).parts:
            raise ValueError("Invalid SDK source path")
        data = (ROOT / name).read_bytes()
        if digest(data) != expected:
            raise ValueError("SDK inventory/source mismatch")
        files[name] = data
    if sdk_bom["licenseSha256"] != digest((ROOT / "LICENSE").read_bytes()):
        raise ValueError("SDK license/source mismatch")
    files["sdk-bom.json"] = (ROOT / "target/sdk-bom.json").read_bytes()
    files["sdk-source-inventory.json"] = (ROOT / "target/sdk-source-inventory.json").read_bytes()
    files["frontend-bom.json"] = (ROOT / "target/frontend-bom.json").read_bytes()
    with zipfile.ZipFile(jar) as archive:
        for name, data in files.items():
            if name.startswith("third-party/") and archive.read("META-INF/jharness/" + name) != data:
                raise ValueError("Legal file not preserved in shaded JAR: " + name)
        if any(name.startswith("org/junit/") for name in archive.namelist()):
            raise ValueError("Test runtime leaked into candidate")
    files["jharness"] = b'''#!/bin/sh
set -eu
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then exec "$JAVA_HOME/bin/java" -jar "$base/lib/jharness.jar" "$@"; fi
exec java -jar "$base/lib/jharness.jar" "$@"
'''
    files["jharness.cmd"] = b'''@echo off\r
setlocal\r
if "%JAVA_HOME%"=="" (\r
  echo Set JAVA_HOME to Java 21 or newer. 1>&2\r
  exit /b 5\r
)\r
"%JAVA_HOME%\\bin\\java.exe" -jar "%~dp0lib\\jharness.jar" %*\r
exit /b %ERRORLEVEL%\r
'''
    files["README.md"] = (ROOT / "docs/examples/m1059-distribution.md").read_bytes()
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    sources = {name: digest((ROOT / name).read_bytes()) for name in tracked if name and
               (name.endswith((".java", "pom.xml")) or name.startswith(("scripts/", ".github/", "sdk/", "frontend/src/")))}
    manifest = {"version": "0.1.0-SNAPSHOT", "candidateOnly": True,
                "gitCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
                "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
                "sourceSha256": sources,
                "files": {name: digest(data) for name, data in sorted(files.items())}}
    files["manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    output = ROOT / "target/jharness-0.1.0-SNAPSHOT-candidate.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo("jharness/" + name, (2026, 10, 16, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if name == "jharness" else 0o100644) << 16
            archive.writestr(info, data)
    (ROOT / "target/candidate-sha256.txt").write_text(digest(output.read_bytes()) + "  " + output.name + "\n")
    print(output.name, digest(output.read_bytes()))


if __name__ == "__main__":
    main()
