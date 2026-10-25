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
import re
from evidence_io import bounded_path, read_json_snapshot, stream_sha256, atomic_write_json

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    bom = read_json_snapshot(ROOT,"target/bom.json").data
    repo = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / ".m2/repository"
    output = ROOT / "third-party"
    entries = []; writes={}; total_legal=0
    for component in sorted(bom["components"], key=lambda c: c["bom-ref"]):
        group, name, version = (component[k] for k in ("group", "name", "version"))
        if group == "io.jharness":
            continue
        if any(not isinstance(v,str) or not re.fullmatch(r"[A-Za-z0-9_.+-]{1,256}",v) for v in [group,name,version]):raise ValueError("Invalid Maven coordinate")
        coordinate = f"{group}:{name}:{version}"
        base = repo / group.replace(".", "/") / name / version
        jar, pom = (base / f"{name}-{version}.{suffix}" for suffix in ("jar", "pom"))
        target = output / "artifacts" / group / name / version
        jar=bounded_path(repo,jar.relative_to(repo).as_posix())
        pom=bounded_path(repo,pom.relative_to(repo).as_posix())
        if jar.stat().st_size>256*1024*1024 or pom.stat().st_size>1024*1024:raise ValueError("Artifact size limit")
        legal = []
        with zipfile.ZipFile(jar) as archive:
            if len(archive.namelist())>100000:raise ValueError("Artifact entry limit")
            for filename in sorted(archive.namelist()):
                if filename.endswith("/") or not any(
                        word in filename.lower() for word in ("license", "notice", "copying")):
                    continue
                path = Path(filename)
                if path.is_absolute() or ".." in path.parts:
                    raise ValueError("Unsafe JAR entry: " + filename)
                if archive.getinfo(filename).file_size>1024*1024:raise ValueError("Legal entry limit")
                data=archive.read(filename);total_legal+=len(data)
                if total_legal>16*1024*1024:raise ValueError("Legal aggregate limit")
                dest=target/path
                bounded_path(ROOT,dest.relative_to(ROOT).as_posix(),must_exist=False)
                writes[dest]=data
                legal.append({"file": dest.relative_to(output).as_posix(), "sha256": sha(data)})
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        pom_bytes=pom.read_bytes()
        if b"<!DOCTYPE" in pom_bytes.upper() or b"<!ENTITY" in pom_bytes.upper():raise ValueError("POM DTD unsupported")
        model = ET.fromstring(pom_bytes)
        licenses = [{"name": item.findtext("m:name", namespaces=ns),
                     "url": item.findtext("m:url", namespaces=ns)}
                    for item in model.findall("m:licenses/m:license", ns)]
        supplemental = {"org.jetbrains.pty4j:pty4j": ["pty4j-LICENSE", "pty4j-NOTICE", "winpty-LICENSE",
                                   "terminal-LICENSE", "terminal-NOTICE"],
                        "org.jetbrains.kotlin:kotlin-stdlib": ["kotlin-LICENSE", "kotlin-NOTICE"],
                        "org.jetbrains:annotations": ["annotations-LICENSE"]}.get(group+":"+name, [])
        if not legal and not supplemental:
            raise ValueError("Missing legal material: " + coordinate)
        for filename in supplemental:
            if not (output / "upstream" / filename).is_file():
                raise ValueError("Missing supplemental material: " + filename)
        entries.append({"coordinate": coordinate,
                        "usage": "test" if group.startswith(("org.junit", "org.opentest4j", "org.apiguardian")) else "runtime",
                        "artifactUrl": "https://repo.maven.apache.org/maven2/" + jar.relative_to(repo).as_posix(),
                        "sourceUrl": "https://repo.maven.apache.org/maven2/" + jar.relative_to(repo).as_posix().replace(".jar", "-sources.jar"),
                        "jarSha256": stream_sha256(jar), "pomSha256": stream_sha256(pom),
                        "declaredLicenses": licenses, "legal": legal, "supplemental": supplemental})
    for destination,data in writes.items():
        destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data)
    (output / "inventory.json").write_bytes((json.dumps(entries, indent=2) + "\n").encode("utf-8"))
    print("Preserved legal materials for", len(entries), "external dependencies")


if __name__ == "__main__":
    main()
