"""Audit every inventoried runtime native byte; unknown provenance stays unknown."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import zipfile
from evidence_io import atomic_write_json, stream_sha256

ROOT=Path(__file__).resolve().parents[1]


def sha(raw):return hashlib.sha256(raw).hexdigest()


def audit(jar,inventory,reviewed):
    if (not isinstance(inventory,dict) or not isinstance(inventory.get('binaries'),list)
            or not inventory['binaries'] or not isinstance(reviewed,dict)):
        raise ValueError('Nonempty native inventory required')
    for row in inventory['binaries']:
        if (not isinstance(row,dict) or not isinstance(row.get('resource'),str) or not row['resource']
                or not isinstance(row.get('coordinate'),str) or not row['coordinate']
                or not re.fullmatch('[0-9a-f]{64}',str(row.get('sha256','')))):
            raise ValueError('Invalid native inventory identity')
    for row in reviewed.values():
        if (not isinstance(row,dict) or not re.fullmatch('[0-9a-f]{64}',str(row.get('sha256','')))
                or not isinstance(row.get('origin'),dict) or not row['origin']):
            raise ValueError('Reviewed source origin required')
    original={v['resource']:v for v in inventory['binaries']}
    if len(original)!=len(inventory['binaries']) or not set(reviewed).issubset(original):
        raise ValueError('Unique inventoried native resource scope required')
    rows=[]
    with zipfile.ZipFile(jar) as z:
        if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate runtime entries')
        for name in z.namelist():
            # jna/platform is Java code plus package HTML/COM source templates,
            # while architecture subdirectories under jna hold the native payloads.
            native_container=(name.startswith('resources/com/pty4j/native/') or
                              (name.startswith('com/sun/jna/') and not name.startswith('com/sun/jna/platform/')))
            native_suffix=name.endswith(('.so','.dll','.exe','.dylib','.jnilib','.a'))
            if (not name.endswith(('/','.class')) and (native_container or native_suffix)
                    and name not in original):
                raise ValueError('Uninventoried native runtime resource: '+name)
        for name,item in sorted(original.items()):
            raw=z.read(name);digest=sha(raw)
            source=reviewed.get(name)
            expected=source['sha256'] if source else item['sha256']
            if digest!=expected:raise ValueError('Unreviewed runtime native bytes: '+name)
            rows.append(dict(resource=name,sha256=digest,coordinate=item['coordinate'],
                provenance='REVIEWED_SOURCE_BUILD' if source else 'OFFICIAL_BYTES_EXACT_BUILD_UNRESOLVED',
                sourceOrigin=source['origin'] if source else None,
                vulnerabilityCoverage='NOT_COMPLETE_OS_CPE_CLEARANCE'))
    complete=all(v['provenance']=='REVIEWED_SOURCE_BUILD' for v in rows)
    return dict(status='SOURCE_COVERAGE_COMPLETE' if complete else 'SOURCE_COVERAGE_INCOMPLETE',
        jarSha256=stream_sha256(jar),resources=rows,inventoryCount=len(rows),
        reviewedSourceCount=len(reviewed),retainedOriginalCount=len(rows)-len(reviewed),
        fullOsCpeClearance=False,releaseApproved=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('jar',type=Path)
    parser.add_argument('output',type=Path);parser.add_argument('--require-source-complete',action='store_true');args=parser.parse_args()
    if args.jar.resolve()==args.output.resolve():raise ValueError('Output must not overwrite runtime')
    atomic_write_json(args.output,dict(status='INCOMPLETE',fullOsCpeClearance=False,releaseApproved=False))
    inventory=json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())
    reviewed=json.loads((ROOT/'docs/testing/native-security/reviewed-native-source-map.json').read_bytes())['resources']
    report=audit(args.jar,inventory,reviewed)
    atomic_write_json(args.output,report);print(report['status'])
    if args.require_source_complete and report['status']!='SOURCE_COVERAGE_COMPLETE':raise SystemExit(2)


if __name__=='__main__':main()
