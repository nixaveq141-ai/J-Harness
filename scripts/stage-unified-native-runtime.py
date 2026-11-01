"""Combine eleven reviewed native resources on the current application baseline.

The remaining inventoried resources are retained and explicitly not certified as
source built. No accepted application classes or prior verify receipts are reused.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SCOPES={
    'windows':{'com/sun/jna/win32-x86-64/jnidispatch.dll'}|{
        'resources/com/pty4j/native/win/x86-64/'+n for n in
        ['winpty.dll','winpty-agent.exe','win-helper.dll','conpty.dll','OpenConsole.exe']},
    'linux':{'com/sun/jna/linux-x86-64/libjnidispatch.so',
             'resources/com/pty4j/native/linux/x86-64/libpty.so'},
    'macos':{'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib',
             'resources/com/pty4j/native/darwin/libpty.dylib',
             'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper'}}
RECEIPTS={'windows':'M10-source-install-2026-11-01.json',
          'linux':'M10-posix-source-linux-2026-11-01.json',
          'macos':'M10-posix-source-macos-2026-11-01.json'}


def sha(raw):return hashlib.sha256(raw).hexdigest()


def active_scope(system,machine):
    value={('Windows','AMD64'):'windows',('Linux','x86_64'):'linux',('Darwin','arm64'):'macos'}.get((system,machine))
    if not value:raise ValueError('Unreviewed unified target architecture')
    return value


def extract_reviewed(jar,receipt,scope,target):
    if sha(jar.read_bytes())!=receipt['runtimeJarSha256']:
        raise ValueError('Previously accepted runtime bytes required')
    replacements={}
    with zipfile.ZipFile(jar) as archive:
        if len(archive.namelist())!=len(set(archive.namelist())):raise ValueError('Duplicate accepted entries')
        metadata=json.loads(archive.read('META-INF/jharness/source-native-preview.json'))
        if set(metadata['replacements'])!=SCOPES[scope]:raise ValueError('Exact reviewed platform resource scope required')
        for name,item in metadata['replacements'].items():
            raw=archive.read(name)
            if sha(raw)!=item['sha256']:raise ValueError('Reviewed native resource drift')
            path=target/Path(name).name
            with path.open('xb') as stream:stream.write(raw)
            replacements[name]=dict(path=path,sha256=sha(raw),origin=dict(
                qualificationRun=receipt['runId'],qualificationCommit=receipt['commit'],
                acceptedRuntimeJarSha256=receipt['runtimeJarSha256'],source=item['origin']))
    return replacements


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);args=parser.parse_args()
    active=active_scope(platform.system(),platform.machine())
    out=ROOT/args.output
    if not out.resolve().is_relative_to((ROOT/'.build-cache').resolve()):raise ValueError('Private output required')
    out.mkdir(parents=True,exist_ok=True)
    payload=out/'native-inputs';payload.mkdir();replacements={};receipts={}
    for scope,filename in RECEIPTS.items():
        path=ROOT/'docs/testing'/filename;receipt=json.loads(path.read_bytes())
        if (not receipt['status'].endswith('_ACCEPTED_NOT_RELEASE') or receipt['javaTests']!=735
                or any(receipt[k]!=0 for k in ['failures','errors','skipped'])):
            raise ValueError('Successful reviewed qualification receipt required')
        jar=ROOT/'.build-cache/unified-inputs'/scope/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        replacements.update(extract_reviewed(jar,receipt,scope,payload));receipts[scope]=sha(path.read_bytes())
    if set(replacements)!=set.union(*SCOPES.values()) or len(replacements)!=11:
        raise ValueError('Exact eleven-resource unified scope required')
    runtime=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';baseline=out/'baseline-runtime.jar'
    with baseline.open('xb') as stream:stream.write(runtime.read_bytes())
    helper=ROOT/'docs/testing/tools/assemble-native-preview.py';spec=importlib.util.spec_from_file_location('unified_assembler',helper)
    assembler=importlib.util.module_from_spec(spec);spec.loader.exec_module(assembler)
    inventory={v['resource']:v['sha256'] for v in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries']}
    preview=out/'current-source-runtime.jar'
    assembly=assembler.assemble(baseline,preview,replacements,sha(baseline.read_bytes()),inventory,
        'Unified Windows x64, Linux x64, macOS arm64; remaining inventoried resources retain original bytes')
    temporary=runtime.with_suffix('.source-stage.tmp')
    with temporary.open('xb') as stream:stream.write(preview.read_bytes())
    temporary.replace(runtime)
    all_items=assembly['manifest']['replacements']
    report=dict(status='UNIFIED_STAGED_NOT_FUNCTIONALLY_ACCEPTED',runtimeJarSha256=sha(runtime.read_bytes()),
        baselineJarSha256=sha(baseline.read_bytes()),nativeReplacements={n:all_items[n] for n in SCOPES[active]},
        allNativeReplacements=all_items,activeScope=active,acceptedReceiptSha256=receipts,
        retainedOriginalNativeResources=sorted(set(inventory)-set(all_items)),
        preservedEntries=assembly['preservedEntries'],paidRequests=0,releaseApproved=False)
    (out/'stage.json').write_bytes((json.dumps(report,indent=2)+'\n').encode());print(report['status'])


if __name__=='__main__':main()
