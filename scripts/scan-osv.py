"""Query exact Maven coordinates from a CycloneDX BOM; fail closed on errors/findings.

Only package coordinates leave the machine. No project source or credentials are sent.
Usage: python scripts/scan-osv.py target/bom.json target/osv-results.json
"""

import datetime
import hashlib
import json
from pathlib import Path
import sys
import time
from evidence_io import strict_json_loads, atomic_write_json
import urllib.parse
import urllib.request


def request(path, data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        "https://api.osv.dev/v1/" + path, data=body,
        headers={"Content-Type": "application/json", "User-Agent": "J-Harness-M9"})
    with urllib.request.urlopen(req, timeout=30) as response:
        raw=response.read(4*1024*1024+1)
        if len(raw)>4*1024*1024:raise ValueError("OSV response limit")
        return strict_json_loads(raw)


def scan(bom_path, output, api=request):
    report={"capturedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"endpoint":"https://api.osv.dev/v1/querybatch","status":"INCOMPLETE","packages":[],"records":{}}
    atomic_write_json(output,report)
    started=time.monotonic()
    def checked_api(path,data=None):
        if time.monotonic()-started>120:raise TimeoutError("OSV total deadline")
        value=api(path,data) if data is not None else api(path)
        if time.monotonic()-started>120:raise TimeoutError("OSV total deadline")
        if not isinstance(value,dict):raise ValueError("OSV response object required")
        return value
    try:
        with Path(bom_path).open('rb') as stream:raw=stream.read(16*1024*1024+1)
        if len(raw)>16*1024*1024:raise ValueError("BOM byte limit")
        report['bomSha256']=hashlib.sha256(raw).hexdigest()
        bom=strict_json_loads(raw)
        if not isinstance(bom,dict) or bom.get('bomFormat')!='CycloneDX' or not isinstance(bom.get('components'),list):raise ValueError("Invalid CycloneDX BOM")
        if len(bom['components'])>2048:raise ValueError("BOM component limit")
        components=sorted({(c['group'],c['name'],c['version']) for c in bom['components'] if c['group']!='io.jharness'})
        if not components or any(not all(isinstance(v,str) and 0<len(v)<=256 for v in c) for c in components):raise ValueError("Invalid Maven component")
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
            results = checked_api("querybatch", {"queries": [q for _, q in pending]})["results"]
            if len(results) != len(pending):
                raise ValueError("OSV result count mismatch")
            following = []
            for (index, query), result in zip(pending, results):
                if not isinstance(result,dict) or not isinstance(result.get("vulns",[]),list):raise ValueError("Invalid OSV result")
                identifiers=[v['id'] for v in result.get('vulns',[])]
                if len(identifiers)>1000 or any(not isinstance(v,str) or not 0<len(v)<=256 for v in identifiers):raise ValueError("OSV record limit")
                findings[index].update(identifiers)
                if sum(len(ids) for ids in findings)>1000:raise ValueError("OSV total record limit")
                token = result.get("next_page_token")
                if token:
                    if not isinstance(token,str) or len(token)>4096:raise ValueError("Invalid OSV token")
                    if token in seen[index]:
                        raise ValueError("Repeated OSV page token")
                    seen[index].add(token)
                    following.append((index, {**query, "page_token": token}))
            pending = following
        for query, ids in zip(queries, findings):
            report["packages"].append({**query, "vulnerabilities": sorted(ids)})
        for identifier in sorted(set().union(*findings)):
            record = checked_api("vulns/" + urllib.parse.quote(identifier, safe=""))
            if record["id"] != identifier:
                raise ValueError("OSV record ID mismatch")
            report["records"][identifier] = record
        active = [v for v in report["records"].values() if not v.get("withdrawn")]
        report.update(status="FINDINGS" if active else "PASS", pages=pages,
                      activeFindings=len(active), packageCount=len(components))
    except Exception as error:
        report["error"] = type(error).__name__
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    atomic_write_json(output,report)
    return report


if __name__ == "__main__":
    result = scan(*sys.argv[1:])
    print(result["status"], "packages=", len(result["packages"]))
    sys.exit(0 if result["status"] == "PASS" else 1)
