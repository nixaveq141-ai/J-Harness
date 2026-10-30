"""Block promotion of reviewed Console candidates below vendor security floors.

This is a gate for the enumerated vendor advisories, not a complete vulnerability
scan. Full Windows Terminal product ranges do not prove bundled-subset exposure.
"""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
PRODUCTS = {'Windows Terminal for Windows 10', 'Windows Terminal for Windows 11', 'Windows Terminal App'}


def version(value):
    if not isinstance(value, str) or not re.fullmatch(r'\d+(?:\.\d+){1,3}', value):
        raise ValueError('Reviewed numeric vendor version required')
    fields = tuple(int(v) for v in value.split('.'))
    return fields + (0,) * (4 - len(fields))


def validate(revision, candidate_version, manifest=None):
    manifest = manifest or ROOT/'docs/testing/native-security/console-baseline.json'
    policy = json.loads(Path(manifest).read_bytes())
    if (revision != policy['sourceRevision'] or candidate_version != policy['sourceVersion']
            or policy.get('component') != 'microsoft/terminal'):
        raise ValueError('Console security source identity rejected')
    candidate = version(candidate_version)
    reviewed = []
    for item in policy['advisories']:
        path = Path(manifest).parent/item['file']
        if (not path.resolve().is_relative_to(Path(manifest).parent.resolve()) or path.is_symlink()
                or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']):
            raise ValueError('Vendor advisory identity rejected')
        record = json.loads(path.read_bytes())
        if record['cveMetadata']['cveId'] != item['id'] or record['cveMetadata']['state'] != 'PUBLISHED':
            raise ValueError('Published exact CVE identity required')
        floors = []
        for product in record['containers']['cna']['affected']:
            if product.get('vendor') != 'Microsoft' or product.get('product') not in PRODUCTS:
                continue
            for row in product['versions']:
                if row.get('status') != 'affected' or row.get('versionType') != 'custom' or 'lessThan' not in row:
                    raise ValueError('Unreviewed vendor range semantics')
                floor = version(row['lessThan'])
                if candidate < floor:
                    raise ValueError('Console candidate below reviewed security floor: '+item['id'])
                floors.append(row['lessThan'])
        if not floors or set(floors) != {item['fixedVersion']}:
            raise ValueError('Terminal product scope or reviewed floor drift rejected')
        reviewed.append(dict(id=item['id'], fixedVersion=item['fixedVersion'], recordSha256=item['sha256']))
    if not reviewed or len({v['id'] for v in reviewed}) != len(reviewed):
        raise ValueError('Nonempty unique advisory set required')
    return dict(status='ENUMERATED_VENDOR_SECURITY_FLOORS_SATISFIED_NOT_FULL_CPE_CLEARANCE',
                sourceRevision=revision, sourceVersion=candidate_version, advisories=reviewed,
                policySha256=hashlib.sha256(Path(manifest).read_bytes()).hexdigest(),
                bundledSubsetExposureProven=False, fullVulnerabilityCoverage=False)
