"""Create a deterministic CycloneDX runtime BOM from npm's complete lockfile closure.

The local npm sbom omitted js-tokens although it is a required production edge of
loose-envify. Resolve and verify every production edge explicitly instead.
"""
import base64
import hashlib
import json
from pathlib import Path
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]


def license_value(value):
    # Preserve unknown strings as names; never label arbitrary text as an SPDX id.
    ids={'MIT','Apache-2.0','BSD-2-Clause','BSD-3-Clause','ISC','MPL-2.0','Unlicense','CC0-1.0','LGPL-2.1-or-later','GPL-2.0-only','GPL-3.0-only'}
    if value in ids:return [{'license':{'id':value}}]
    import re
    tokens=re.findall(r'[A-Za-z0-9.+-]+|[()]',value)
    if any(t in {'AND','OR','WITH'} for t in tokens) and all(t in ids|{'AND','OR','WITH','(',')'} for t in tokens):return [{'expression':value}]
    return [{'license':{'name':value}}]

def build(lock_bytes):
    lock = json.loads(lock_bytes)
    packages = lock['packages']
    runtime = {p: v for p, v in packages.items() if p and not v.get('dev', False)}
    if not runtime or any(v.get('link') for v in runtime.values()):
        raise ValueError('Runtime dependency closure missing or contains unsupported links')

    def name(path):
        return path.rsplit('node_modules/', 1)[1]

    def ref(path):
        v = runtime[path]
        return 'npm-instance:' + path

    def resolve(parent, dependency, optional=False):
        current = parent
        while True:
            candidate = current + '/node_modules/' + dependency if current else 'node_modules/' + dependency
            if candidate in runtime:
                return candidate
            if not current:
                if optional:return None
                raise ValueError('Missing runtime edge: ' + parent + ' -> ' + dependency)
            current = current.rsplit('/node_modules/', 1)[0] if '/node_modules/' in current else ''

    components, dependencies = [], []
    for path, item in sorted(runtime.items()):
        if any(not isinstance(item.get(k),str) or not item[k] for k in ['version','integrity','resolved','license']):raise ValueError('Runtime package metadata missing: '+path)
        integrity = item['integrity']
        if not integrity.startswith('sha512-') or not isinstance(item.get('license'), str):
            raise ValueError('Runtime package lacks SHA512/license metadata: ' + path)
        n = name(path)
        component = {'type': 'library', 'bom-ref': ref(path), 'name': n, 'version': item['version'],
                     'purl': 'pkg:npm/'+quote(n,safe='/')+'@'+item['version'], 'licenses': license_value(item['license']),
                     'hashes': [{'alg': 'SHA-512', 'content': base64.b64decode(integrity[7:], validate=True).hex()}],
                     'externalReferences': [{'type': 'distribution', 'url': item['resolved']}]}
        components.append(component)
        edges=[]
        optional=item.get('optionalDependencies',{})
        for dep in item.get('dependencies',{}):
            target=resolve(path,dep,dep in optional)
            if target:edges.append(ref(target))
        for dep in optional:
            target=resolve(path,dep,True)
            if target:edges.append(ref(target))
        for dep in item.get('peerDependencies',{}):
            target=resolve(path,dep,item.get('peerDependenciesMeta',{}).get(dep,{}).get('optional',False))
            if target:edges.append(ref(target))
        dependencies.append({'ref':ref(path),'dependsOn':sorted(set(edges))})
    root_ref = 'jharness-web'
    roots={ref(resolve('',dep)) for dep in packages[''].get('dependencies',{}) if dep not in packages[''].get('optionalDependencies',{})}
    roots.update(ref(target) for dep in packages[''].get('optionalDependencies',{}) if (target:=resolve('',dep,True)))
    dependencies.append({'ref':root_ref,'dependsOn':sorted(roots)})
    graph={d['ref']:d['dependsOn'] for d in dependencies};seen=set();pending=[root_ref]
    while pending:
        node=pending.pop()
        if node in seen:continue
        seen.add(node);pending.extend(graph[node])
    if seen!=set(graph):raise ValueError('Unreachable runtime package')
    return {'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'version': 1,
            'metadata': {'component': {'type': 'application', 'bom-ref': root_ref,
                                      'name': lock['name'], 'version': lock['version']},
                         'properties': [{'name': 'jharness:npm-lock-sha256', 'value': hashlib.sha256(lock_bytes).hexdigest()},
                                        {'name': 'jharness:scope', 'value': 'All declared production npm dependencies; bundled UI may tree-shake build-only portions'}]},
            'components': components, 'dependencies': dependencies}


if __name__ == '__main__':
    output = ROOT / (sys.argv[1] if len(sys.argv) > 1 else 'target/frontend-bom.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    bom = build((ROOT / 'frontend/package-lock.json').read_bytes())
    output.write_bytes((json.dumps(bom, indent=2) + '\n').encode())
    print('Frontend runtime BOM:', len(bom['components']), 'packages')
