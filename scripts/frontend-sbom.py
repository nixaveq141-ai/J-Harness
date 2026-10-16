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
        return 'pkg:npm/' + quote(name(path), safe='/') + '@' + v['version']

    def resolve(parent, dependency):
        current = parent
        while True:
            candidate = current + '/node_modules/' + dependency if current else 'node_modules/' + dependency
            if candidate in runtime:
                return candidate
            if not current:
                raise ValueError('Missing runtime edge: ' + parent + ' -> ' + dependency)
            current = current.rsplit('/node_modules/', 1)[0] if '/node_modules/' in current else ''

    components, dependencies = [], []
    for path, item in sorted(runtime.items()):
        integrity = item['integrity']
        if not integrity.startswith('sha512-') or not isinstance(item.get('license'), str):
            raise ValueError('Runtime package lacks SHA512/license metadata: ' + path)
        n = name(path)
        component = {'type': 'library', 'bom-ref': ref(path), 'name': n, 'version': item['version'],
                     'purl': ref(path), 'licenses': [{'license': {'id': item['license']}}],
                     'hashes': [{'alg': 'SHA-512', 'content': base64.b64decode(integrity[7:], validate=True).hex()}],
                     'externalReferences': [{'type': 'distribution', 'url': item['resolved']}]}
        components.append(component)
        edges = {**item.get('dependencies', {}), **item.get('optionalDependencies', {}), **item.get('peerDependencies', {})}
        dependencies.append({'ref': ref(path), 'dependsOn': sorted(ref(resolve(path, dep)) for dep in edges)})
    root_ref = 'jharness-web'
    dependencies.append({'ref': root_ref, 'dependsOn': sorted(ref(resolve('', dep)) for dep in packages['']['dependencies'])})
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
