"""Stage an accepted source-native preview for a fresh packaged functional run.

Preserve the freshly built baseline; no original verify receipt is reused. This
does not approve release, full native provenance or OS/CPE clearance.
"""
import hashlib
import argparse
import io
import importlib.util
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
WINDOWS_NATIVE={'com/sun/jna/win32-x86-64/jnidispatch.dll'} | {
    'resources/com/pty4j/native/win/x86-64/'+name for name in
    ['winpty.dll','winpty-agent.exe','win-helper.dll','conpty.dll','OpenConsole.exe']}


def sha(raw):return hashlib.sha256(raw).hexdigest()


def validate_runtime(baseline,preview,replacements):
    with zipfile.ZipFile(io.BytesIO(baseline)) as base,zipfile.ZipFile(io.BytesIO(preview)) as candidate:
        names=base.namelist();other=candidate.namelist()
        marker='META-INF/jharness/source-native-preview.json'
        if (len(names)!=len(set(names)) or len(other)!=len(set(other)) or
                set(other)!=set(names)|{marker} or marker in names):
            raise ValueError('Exact runtime entry set required')
        if set(replacements)!=WINDOWS_NATIVE or not set(replacements).issubset(names):
            raise ValueError('Six reviewed Windows native substitutions required')
        for name in names:
            raw=candidate.read(name)
            if name in replacements:
                if not name.endswith(('.dll','.exe')) or sha(raw)!=replacements[name]['sha256']:
                    raise ValueError('Reviewed native byte identity rejected')
            elif base.read(name)!=raw:
                raise ValueError('Current Java/UI/legal runtime drift rejected: '+name)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',default='.build-cache/source-product')
    args=parser.parse_args()
    approved=ROOT/'docs/testing/M10-console-fixed-acceptance-2026-10-31.json'
    receipt=json.loads(approved.read_bytes())
    if receipt['status']!='FIXED_CONSOLE_SOURCE_ABI_AND_PRODUCT_TERMINAL_COMPONENT_ACCEPTED':
        raise ValueError('Accepted source component receipt required')
    downloaded=ROOT/'.build-cache/accepted-console'
    load=json.loads((downloaded/'.build-cache/windows-console-load/report.json').read_bytes())
    if load!=receipt['load']:raise ValueError('Accepted source load receipt drift rejected')
    preview=(downloaded/'.build-cache/windows-console-load/source-native-preview.jar').read_bytes()
    if sha(preview)!=load['assembly']['previewJarSha256']:raise ValueError('Accepted preview JAR drift rejected')
    runtime=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    if runtime.is_symlink():raise ValueError('Regular working build JAR required')
    baseline=runtime.read_bytes()
    out=ROOT/args.output
    if not out.resolve().is_relative_to((ROOT/'.build-cache').resolve()):
        raise ValueError('Output must stay in private build cache')
    out.mkdir(parents=True,exist_ok=True)
    if out.is_symlink() or not out.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError('Private staging directory required')
    saved=out/'baseline-runtime.jar'
    with saved.open('xb') as stream:stream.write(baseline)
    accepted=load['assembly']['manifest']['replacements']
    if set(accepted)!=WINDOWS_NATIVE:raise ValueError('Exact accepted native scope required')
    payload=out/'native-inputs';payload.mkdir();replacements={}
    with zipfile.ZipFile(io.BytesIO(preview)) as archive:
        for name,item in accepted.items():
            raw=archive.read(name)
            if sha(raw)!=item['sha256']:raise ValueError('Accepted native extraction drift rejected')
            path=payload/Path(name).name
            with path.open('xb') as stream:stream.write(raw)
            replacements[name]=dict(path=path,sha256=item['sha256'],origin=item['origin'])
    helper=ROOT/'docs/testing/tools/assemble-native-preview.py'
    spec=importlib.util.spec_from_file_location('native_assembler',helper)
    assembler=importlib.util.module_from_spec(spec);spec.loader.exec_module(assembler)
    inventory={v['resource']:v['sha256'] for v in json.loads((ROOT/'docs/testing/M10-native-inventory-48-2026-10-30.json').read_bytes())['binaries']}
    assembled=out/'current-source-runtime.jar'
    assembly=assembler.assemble(saved,assembled,replacements,sha(baseline),inventory,
        'Fresh current application with six independently accepted Windows native resources')
    current=assembled.read_bytes();validate_runtime(baseline,current,accepted)
    # This fresh job has not issued a verify receipt for runtime. Preserve its
    # baseline first; replace only the disposable working build output.
    temporary=runtime.with_suffix('.source-stage.tmp')
    with temporary.open('xb') as stream:stream.write(current)
    temporary.replace(runtime)
    report=dict(status='STAGED_NOT_FUNCTIONALLY_ACCEPTED',baselineJarSha256=sha(baseline),
                runtimeJarSha256=sha(current),acceptedSourcePreviewSha256=sha(preview),
                assemblerSha256=sha(helper.read_bytes()),acceptedReceiptSha256=sha(approved.read_bytes()),
                sourceRunId=receipt['runId'],sourceCommit=receipt['commit'],
                nativeReplacements=load['assembly']['manifest']['replacements'],
                preservedEntries=assembly['preservedEntries'],paidRequests=0,
                originalVerifyReceiptReused=False,releaseApproved=False)
    (out/'stage.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
