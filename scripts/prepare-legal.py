"""Preserve coordinate-specific legal resources from the actual SBOM artifacts.

Run after -Prelease-audit verify; this reads the local Maven repository only.
Upstream supplemental files are reviewed, pinned and stored in third-party/upstream.
"""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    bom = json.loads((ROOT / "target/bom.json").read_text(encoding="utf-8"))
    repo = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / ".m2/repository"
    output = ROOT / "third-party"
    entries = []
    for component in sorted(bom["components"], key=lambda c: c["bom-ref"]):
        group, name, version = (component[k] for k in ("group", "name", "version"))
        if group == "io.jharness":
            continue
        coordinate = f"{group}:{name}:{version}"
        base = repo / group.replace(".", "/") / name / version
        jar, pom = (base / f"{name}-{version}.{suffix}" for suffix in ("jar", "pom"))
        target = output / "artifacts" / group / name / version
        target.mkdir(parents=True, exist_ok=True)
        legal = []
        with zipfile.ZipFile(jar) as archive:
            for filename in sorted(archive.namelist()):
                if filename.endswith("/") or not any(
                        word in filename.lower() for word in ("license", "notice", "copying")):
                    continue
                path = Path(filename)
                if path.is_absolute() or ".." in path.parts:
                    raise ValueError("Unsafe JAR entry: " + filename)
                data = archive.read(filename)
                dest = target / path
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(data)
                legal.append({"file": dest.relative_to(output).as_posix(), "sha256": sha(data)})
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        model = ET.fromstring(pom.read_bytes())
        licenses = [{"name": item.findtext("m:name", namespaces=ns),
                     "url": item.findtext("m:url", namespaces=ns)}
                    for item in model.findall("m:licenses/m:license", ns)]
        supplemental = {"pty4j": ["pty4j-LICENSE", "pty4j-NOTICE", "winpty-LICENSE",
                                   "terminal-LICENSE", "terminal-NOTICE"],
                        "kotlin-stdlib": ["kotlin-LICENSE", "kotlin-NOTICE"],
                        "annotations": ["annotations-LICENSE"]}.get(name, [])
        if not legal and not supplemental:
            raise ValueError("Missing legal material: " + coordinate)
        for filename in supplemental:
            if not (output / "upstream" / filename).is_file():
                raise ValueError("Missing supplemental material: " + filename)
        entries.append({"coordinate": coordinate,
                        "usage": "test" if group.startswith(("org.junit", "org.opentest4j", "org.apiguardian")) else "runtime",
                        "artifactUrl": "https://repo.maven.apache.org/maven2/" + jar.relative_to(repo).as_posix(),
                        "sourceUrl": "https://repo.maven.apache.org/maven2/" + jar.relative_to(repo).as_posix().replace(".jar", "-sources.jar"),
                        "jarSha256": sha(jar.read_bytes()), "pomSha256": sha(pom.read_bytes()),
                        "declaredLicenses": licenses, "legal": legal, "supplemental": supplemental})
    (output / "inventory.json").write_bytes((json.dumps(entries, indent=2) + "\n").encode("utf-8"))
    print("Preserved legal materials for", len(entries), "external dependencies")


if __name__ == "__main__":
    main()
