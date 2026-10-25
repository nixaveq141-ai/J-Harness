"""Verify SDK standard-library-only runtime imports and generate a source/Apache license BOM."""
import ast
import hashlib
import json
from pathlib import Path
import re
import sys
import subprocess

ROOT = Path(__file__).resolve().parents[1]
FILES = ['sdk/README.md', 'sdk/package.json', 'sdk/typescript/package.json', 'sdk/typescript/src/index.ts',
         'sdk/python/jharness_sdk.py', 'sdk/examples/demo.json', 'sdk/examples/loopback_provider.py',
         'sdk/examples/run.ts', 'sdk/examples/run.py']

def build(root=ROOT):
    for name in ['sdk/package.json','sdk/typescript/package.json']:
        package = json.loads((root/name).read_bytes())
        if any(package.get(k) for k in ['dependencies','optionalDependencies','peerDependencies']):
            raise ValueError('Unexpected SDK runtime dependency')
    package=json.loads((root/'sdk/package.json').read_bytes())
    other=json.loads((root/'sdk/typescript/package.json').read_bytes())
    if not isinstance(package.get('version'),str) or package['version']!=other.get('version'):raise ValueError('SDK package version mismatch')
    imports=set();modules=[]
    for name in FILES:
        if name.endswith('.py'):
            tree=ast.parse((root/name).read_text(encoding='utf-8'))
            for node in ast.walk(tree):
                if isinstance(node,ast.Import):imports.update(n.name.split('.')[0] for n in node.names)
                elif isinstance(node,ast.ImportFrom):
                    imports.add((node.module or '').split('.')[0])
                    if node.module in ['importlib','builtins'] and any(n.name in ['import_module','__import__','eval','exec'] for n in node.names):raise ValueError('Dynamic execution alias')
                elif isinstance(node,ast.Call):
                    function=node.func
                    if isinstance(function,ast.Name) and function.id in ['__import__','eval','exec'] or isinstance(function,ast.Attribute) and function.attr in ['import_module','__import__','eval','exec']:
                        raise ValueError('Unsupported dynamic SDK import or execution')
        if name.endswith('.ts'):
            try:found=json.loads(subprocess.check_output(['node',str(ROOT/'scripts/ts-runtime-imports.mjs'),str(root/name)],timeout=15,stderr=subprocess.PIPE))
            except subprocess.SubprocessError as failure:raise ValueError('Invalid TypeScript runtime imports') from failure
            for module in found:
                if module.startswith('node:'):modules.append(module)
                elif module.startswith('.'):
                    dependency=(root/name).parent/module
                    if dependency.resolve() not in {(root/p).resolve() for p in FILES if p.endswith('.ts')}:raise ValueError('Undistributed SDK relative import')
                    modules.append(module)
                else:raise ValueError('Nonstandard TypeScript import')
    # This is a static source policy, not a sandbox or proof about arbitrary computed execution.
    if not imports <= sys.stdlib_module_names|{'jharness_sdk'}:raise ValueError('Nonstandard Python import')
    if not modules:raise ValueError('Missing TypeScript imports')
    digest=lambda p:hashlib.sha256((root/p).read_bytes()).hexdigest()
    sbom=dict(bomFormat='CycloneDX',specVersion='1.5',version=1,
              metadata={'component':{'type':'application','name':'jharness-stdio-sdk','version':package['version'],'licenses':[{'license':{'id':'Apache-2.0'}}]}},components=[],dependencies=[])
    return dict(format='jharness-sdk-source-inventory-1',sbom=sbom,externalRuntimeDependencyCount=0,
                pythonImports=sorted(imports),typescriptImports=sorted(modules),
                sourceSha256={p:digest(p) for p in FILES},licenseSha256=digest('LICENSE'),protocolVersion='1.0')

if __name__=='__main__':
    target=Path(sys.argv[1] if len(sys.argv)>1 else ROOT/'target/sdk-bom.json');target.parent.mkdir(parents=True,exist_ok=True)
    report=build();target.write_text(json.dumps(report.pop('sbom'),indent=2)+'\n',encoding='utf-8')
    report['bomSha256']=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_name('sdk-source-inventory.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('SDK BOM: 0 external runtime dependencies; Apache-2.0 source manifest verified')
