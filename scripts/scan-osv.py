"""Query exact Maven coordinates from a CycloneDX BOM; fail closed on errors/findings.

Only package coordinates leave the machine. No project source or credentials are sent.
Usage: python scripts/scan-osv.py target/bom.json target/osv-results.json
"""

import datetime
import hashlib
import json
from pathlib import Path
import sys
import urllib.parse
import urllib.request


def request(path, data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        "https://api.osv.dev/v1/" + path, data=body,
        headers={"Content-Type": "application/json", "User-Agent": "J-Harness-M9"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def scan(bom_path, output, api=request):
    raw = Path(bom_path).read_bytes()
    bom = json.loads(raw)
    assert bom["bomFormat"] == "CycloneDX"
    components = sorted({(c["group"], c["name"], c["version"])
                         for c in bom["components"] if c["group"] != "io.jharness"})
    if not components:
        raise ValueError("No external Maven dependencies in BOM")
    report = {"capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "endpoint": "https://api.osv.dev/v1/querybatch",
              "bomSha256": hashlib.sha256(raw).hexdigest(),
              "status": "INCOMPLETE", "packages": [], "records": {}}
    try:
        queries = [{"package": {"ecosystem": "Maven", "name": g + ":" + n},
                    "version": v} for g, n, v in components]
        pending = list(enumerate(queries))
        findings = [set() for _ in queries]
        seen = [set() for _ in queries]
        pages = 0
        while pending:
            pages += 1
            if pages > 100:
                raise ValueError("OSV pagination limit exceeded")
            results = api("querybatch", {"queries": [q for _, q in pending]})["results"]
            if len(results) != len(pending):
                raise ValueError("OSV result count mismatch")
            following = []
            for (index, query), result in zip(pending, results):
                findings[index].update(v["id"] for v in result.get("vulns", []))
                token = result.get("next_page_token")
                if token:
                    if token in seen[index]:
                        raise ValueError("Repeated OSV page token")
                    seen[index].add(token)
                    following.append((index, {**query, "page_token": token}))
            pending = following
        for query, ids in zip(queries, findings):
            report["packages"].append({**query, "vulnerabilities": sorted(ids)})
        for identifier in sorted(set().union(*findings)):
            record = api("vulns/" + urllib.parse.quote(identifier, safe=""))
            if record["id"] != identifier:
                raise ValueError("OSV record ID mismatch")
            report["records"][identifier] = record
        active = [v for v in report["records"].values() if not v.get("withdrawn")]
        report.update(status="FINDINGS" if active else "PASS", pages=pages,
                      activeFindings=len(active), packageCount=len(components))
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


if __name__ == "__main__":
    result = scan(*sys.argv[1:])
    print(result["status"], "packages=", len(result["packages"]))
    sys.exit(0 if result["status"] == "PASS" else 1)
