"""Verify SDK standard-library-only runtime imports and generate a source/Apache license BOM."""
import ast
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FILES = ['sdk/README.md', 'sdk/package.json', 'sdk/typescript/package.json', 'sdk/typescript/src/index.ts',
         'sdk/python/jharness_sdk.py', 'sdk/examples/demo.json', 'sdk/examples/loopback_provider.py',
         'sdk/examples/run.ts', 'sdk/examples/run.py']

def build(root=ROOT):
    for name in ['sdk/package.json','sdk/typescript/package.json']:
        package = json.loads((root/name).read_bytes())
        if any(package.get(k) for k in ['dependencies','optionalDependencies','peerDependencies']):
            raise ValueError('Unexpected SDK runtime dependency')
    tree = ast.parse((root/'sdk/python/jharness_sdk.py').read_text(encoding='utf-8'))
    imports = set()
    for node in ast.walk(tree):
        if isinstance(node,ast.Import): imports.update(n.name.split('.')[0] for n in node.names)
        elif isinstance(node,ast.ImportFrom): imports.add((node.module or '').split('.')[0])
        elif isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id in ['__import__','eval','exec']:
            raise ValueError('Unsupported dynamic SDK import or execution')
    if not imports <= sys.stdlib_module_names:
        raise ValueError('Nonstandard Python import')
    text = (root/'sdk/typescript/src/index.ts').read_text(encoding='utf-8')
    modules = re.findall(r"from\s+['\"]([^'\"]+)",text)
    if not modules or any(not m.startswith('node:') for m in modules) or re.search(r'\b(?:require|import)\s*\(',text):
        raise ValueError('Nonstandard or dynamic TypeScript runtime import')
    digest=lambda p:hashlib.sha256((root/p).read_bytes()).hexdigest()
    sbom=dict(bomFormat='CycloneDX',specVersion='1.5',version=1,
              metadata={'component':{'type':'application','name':'jharness-stdio-sdk','version':'1.0.0','licenses':[{'license':{'id':'Apache-2.0'}}]}},components=[],dependencies=[])
    return dict(format='jharness-sdk-source-inventory-1',sbom=sbom,externalRuntimeDependencyCount=0,
                pythonImports=sorted(imports),typescriptImports=sorted(modules),
                sourceSha256={p:digest(p) for p in FILES},licenseSha256=digest('LICENSE'),protocolVersion='1.0')

if __name__=='__main__':
    target=Path(sys.argv[1] if len(sys.argv)>1 else ROOT/'target/sdk-bom.json');target.parent.mkdir(parents=True,exist_ok=True)
    report=build();target.write_text(json.dumps(report.pop('sbom'),indent=2)+'\n',encoding='utf-8')
    report['bomSha256']=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_name('sdk-source-inventory.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('SDK BOM: 0 external runtime dependencies; Apache-2.0 source manifest verified')
