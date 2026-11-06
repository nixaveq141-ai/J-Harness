"""Combine fourteen base natives and optional reviewed P1 components.

The remaining inventoried resources are retained and explicitly not certified as
source built. No accepted application classes or prior verify receipts are reused.
"""
import argparse
import hashlib
import importlib.util
import json
import os
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
EXTRA_SCOPES={
    'linux-arm64':{'com/sun/jna/linux-aarch64/libjnidispatch.so','resources/com/pty4j/native/linux/aarch64/libpty.so'},
    'macos-intel':{'com/sun/jna/darwin-x86-64/libjnidispatch.jnilib'}}


def sha(raw):return hashlib.sha256(raw).hexdigest()


def canonicalize_runtime(source,target,normalize_properties=False):
    """Fix ZIP metadata; normalize only reviewed internal Maven property newlines.

    Classes, UI, legal files and native resources retain their exact bytes.
    Stored entries avoid platform-specific zlib output in the canonical JAR.
    """
    changes={}
    with zipfile.ZipFile(source) as original:
        names=original.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate canonical input entries')
        entries={}
        for name in sorted(names):
            raw=original.read(name);value=raw
            if normalize_properties and name.startswith('META-INF/maven/io.jharness/') and name.endswith('/pom.properties'):
                artifact=name.split('/')[-2]
                expected=('artifactId='+artifact+'\ngroupId=io.jharness\nversion=0.1.0-SNAPSHOT\n').encode()
                if raw.replace(b'\r\n',b'\n')!=expected:
                    raise ValueError('Unexpected internal Maven identity metadata')
                value=expected
                if value!=raw:changes[name]=dict(beforeSha256=sha(raw),afterSha256=sha(value))
            entries[name]=value
        with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_STORED) as output:
            for name,value in entries.items():
                info=zipfile.ZipInfo(name,(2026,10,16,0,0,0));info.create_system=3
                info.compress_type=zipfile.ZIP_STORED
                info.external_attr=((0o40755 if name.endswith('/') else 0o100644)<<16)|(0x10 if name.endswith('/') else 0)
                output.writestr(info,value)
    with zipfile.ZipFile(target) as result:
        if result.namelist()!=sorted(names) or any(result.read(n)!=v for n,v in entries.items()):
            raise ValueError('Canonical runtime content drift')
    return changes


def active_scope(system,machine):
    value={('Windows','AMD64'):'windows',('Linux','x86_64'):'linux',('Darwin','arm64'):'macos',
           ('Linux','aarch64'):'linux-arm64',('Darwin','x86_64'):'macos-intel'}.get((system,machine))
    if not value:raise ValueError('Unreviewed unified target architecture')
    return value


def active_resources(scope):
    resources=({**SCOPES,**EXTRA_SCOPES})[scope]
    if scope=='macos-intel':
        resources=resources|{n for n in SCOPES['macos'] if not n.startswith('com/sun/jna/')}
    return resources


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
        scoped=payload/scope;scoped.mkdir()
        replacements.update(extract_reviewed(jar,receipt,scope,scoped));receipts[scope]=sha(path.read_bytes())
    if set(replacements)!=set.union(*SCOPES.values()) or len(replacements)!=11:
        raise ValueError('Exact eleven-resource unified scope required')
    load_path=ROOT/'docs/testing/M10-extra-native-load-2026-11-01.json'
    load=json.loads(load_path.read_bytes())
    builds=json.loads((ROOT/'docs/testing/M10-extra-native-source-build-2026-11-01.json').read_bytes())
    if (load['status']!='EXTRA_ARCH_SOURCE_COMPONENT_LOAD_AND_NO_FALLBACK_ACCEPTED_NOT_PRODUCT_ACCEPTANCE'
            or load['runId']!='41286273580' or load['sourceRun']!='41285899341'):
        raise ValueError('Exact extra-architecture component acceptance required')
    spec=importlib.util.spec_from_file_location('extra_source_loader',ROOT/'docs/testing/tools/load-native-source-candidates.py')
    loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)
    for scope,aid in [('linux-arm64','13074972771'),('macos-intel','13074739933')]:
        accepted=next(v for v in load['platforms'] if v['sourceArtifactId']==aid)
        source_report_sha=next(v['reportSha256'] for v in builds['platforms'] if v['artifactId']==aid)
        directory=ROOT/'.build-cache/unified-extra-inputs'/scope
        source,jni,pty,helper=loader.validate(directory,'41285899341')
        if sha((directory/'report.json').read_bytes())!=source_report_sha:
            raise ValueError('Extra source report differs from actual load acceptance')
        scoped=payload/scope;scoped.mkdir()
        for name in EXTRA_SCOPES[scope]:
            is_jni=name.startswith('com/sun/jna/');path=jni if is_jni else pty
            raw=path.read_bytes();expected=accepted['jniSha256' if is_jni else 'ptySha256']
            if sha(raw)!=expected:raise ValueError('Extra accepted component bytes changed')
            dest=scoped/Path(name).name;dest.write_bytes(raw)
            replacements[name]=dict(path=dest,sha256=expected,origin=dict(
                sourceBuildRun='41285899341',sourceBuildCommit=source['commit'],
                componentLoadRun=load['runId'],componentLoadCommit=load['commit'],
                sourceReportSha256=source_report_sha))
        receipts[scope]=sha(load_path.read_bytes())
    scopes={**SCOPES,**EXTRA_SCOPES}
    if len(replacements)!=14 or set(replacements)!=set.union(*scopes.values()):
        raise ValueError('Exact fourteen-resource unified scope required')
    if os.environ.get('JHARNESS_P1_LINUX_PTY')=='1':
        spec=importlib.util.spec_from_file_location('p1_components',ROOT/'scripts/stage-p1-linux-pty.py')
        p1=importlib.util.module_from_spec(spec);spec.loader.exec_module(p1)
        extra=p1.load_components(ROOT/'.build-cache/p1-inputs')
        if set(extra)&set(replacements) or len(extra)!=5:
            raise ValueError('Five disjoint P1 resource replacements required')
        for name,item in extra.items():
            target=payload/'p1'/name.split('/')[-2];target.mkdir(parents=True)
            dest=target/'libpty.so';dest.write_bytes(item['path'].read_bytes())
            replacements[name]={**item,'path':dest}
        receipts['p1-linux-pty']=sha((ROOT/p1.RECEIPT).read_bytes())
    if os.environ.get('JHARNESS_P1_LINUX_JNA')=='1':
        spec=importlib.util.spec_from_file_location('p1_jna_components',ROOT/'scripts/stage-p1-linux-jna.py')
        p1jna=importlib.util.module_from_spec(spec);spec.loader.exec_module(p1jna)
        extra=p1jna.load_components(ROOT/'.build-cache/p1-jna-inputs')
        if set(extra)&set(replacements) or len(extra)!=8:
            raise ValueError('Eight disjoint P1 JNA replacements required')
        for name,item in extra.items():
            target=payload/'p1-jna'/name.split('/')[-2];target.mkdir(parents=True)
            dest=target/'libjnidispatch.so';dest.write_bytes(item['path'].read_bytes())
            replacements[name]={**item,'path':dest}
        receipts['p1-linux-jna']=sha((ROOT/p1jna.RECEIPT).read_bytes())
    if os.environ.get('JHARNESS_P1_WINDOWS_X86_JNA')=='1':
        spec=importlib.util.spec_from_file_location('p1_win32',ROOT/'scripts/stage-p1-windows-x86-jna.py')
        p1win=importlib.util.module_from_spec(spec);spec.loader.exec_module(p1win)
        extra=p1win.load_components(ROOT/'.build-cache/p1-win32-inputs')
        if set(extra)&set(replacements) or set(extra)!={p1win.RESOURCE}:
            raise ValueError('One disjoint Windows x86 JNA replacement required')
        for name,item in extra.items():
            target=payload/'p1-win32';target.mkdir(parents=True)
            dest=target/'jnidispatch.dll';dest.write_bytes(item['path'].read_bytes())
            replacements[name]={**item,'path':dest}
        receipts['p1-windows-x86-jna']=sha((ROOT/p1win.RECEIPT).read_bytes())
    if os.environ.get('JHARNESS_P1_WINDOWS_X86_PTY')=='1':
        spec=importlib.util.spec_from_file_location('p1_win32_pty',ROOT/'scripts/stage-p1-windows-x86-pty.py')
        p1pty=importlib.util.module_from_spec(spec);spec.loader.exec_module(p1pty)
        extra=p1pty.load_components(ROOT/'.build-cache/p1-win32-pty-inputs')
        if set(extra)&set(replacements) or len(extra)!=2:
            raise ValueError('Two disjoint Windows x86 WinPTY replacements required')
        target=payload/'p1-win32-pty';target.mkdir(parents=True)
        for name,item in extra.items():
            dest=target/name.rsplit('/',1)[-1];dest.write_bytes(item['path'].read_bytes())
            replacements[name]={**item,'path':dest}
        receipts['p1-windows-x86-pty']=sha((ROOT/p1pty.RECEIPT).read_bytes())
    if os.environ.get('JHARNESS_P1_FREEBSD_PTY')=='1':
        spec=importlib.util.spec_from_file_location('p1_freebsd',ROOT/'scripts/stage-p1-freebsd-pty.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        extra=module.load_components(ROOT/'.build-cache/p1-freebsd-inputs')
        if set(extra)&set(replacements) or len(extra)!=2:raise ValueError('Two disjoint FreeBSD replacements required')
        for name,item in extra.items():
            target=payload/'p1-freebsd'/name.split('/')[-2];target.mkdir(parents=True)
            dest=target/'libpty.so';dest.write_bytes(item['path'].read_bytes());replacements[name]={**item,'path':dest}
        for target,receipt in module.RECEIPTS.items():receipts['p1-freebsd-'+target]=sha((ROOT/receipt).read_bytes())
    if os.environ.get('JHARNESS_P1_FREEBSD_JNA')=='1':
        spec=importlib.util.spec_from_file_location('p1_freebsd_jna',ROOT/'scripts/stage-p1-freebsd-jna.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        extra=module.load_components(ROOT/'.build-cache/p1-freebsd-jna-inputs')
        if set(extra)&set(replacements) or len(extra)!=2:raise ValueError('Two disjoint FreeBSD JNA replacements required')
        for name,item in extra.items():
            target=payload/'p1-freebsd-jna'/name.split('/')[-2];target.mkdir(parents=True)
            dest=target/'libjnidispatch.so';dest.write_bytes(item['path'].read_bytes());replacements[name]={**item,'path':dest}
        receipts['p1-freebsd-jna']=sha((ROOT/module.RECEIPT).read_bytes())
    if os.environ.get('JHARNESS_P1_LOONGARCH_JNA')=='1':
        spec=importlib.util.spec_from_file_location('p1_loongarch_jna',ROOT/'scripts/stage-p1-loongarch-jna.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        extra=module.load_components(ROOT/'.build-cache/p1-loongarch-jna-inputs')
        if set(extra)&set(replacements) or len(extra)!=1:raise ValueError('One disjoint LoongArch JNA replacement required')
        for name,item in extra.items():
            target=payload/'p1-loongarch-jna';target.mkdir(parents=True)
            dest=target/'libjnidispatch.so';dest.write_bytes(item['path'].read_bytes());replacements[name]={**item,'path':dest}
        receipts['p1-loongarch-jna']=sha((ROOT/module.RECEIPT).read_bytes())
    runtime=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';baseline=out/'baseline-runtime.jar'
    with baseline.open('xb') as stream:stream.write(runtime.read_bytes())
    canonical=out/'canonical-baseline-runtime.jar'
    metadata_changes=canonicalize_runtime(baseline,canonical,normalize_properties=True)
    helper=ROOT/'docs/testing/tools/assemble-native-preview.py';spec=importlib.util.spec_from_file_location('unified_assembler',helper)
    assembler=importlib.util.module_from_spec(spec);spec.loader.exec_module(assembler)
    inventory={v['resource']:v['sha256'] for v in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries']}
    preview=out/'current-source-runtime.jar'
    assembly=assembler.assemble(canonical,preview,replacements,sha(canonical.read_bytes()),inventory,
        'Unified five-target runtime; optional P1 extra-architecture PTY/JNA components; remaining inventoried resources retain original bytes')
    final=out/'canonical-source-runtime.jar';canonicalize_runtime(preview,final)
    temporary=runtime.with_suffix('.source-stage.tmp')
    with temporary.open('xb') as stream:stream.write(final.read_bytes())
    temporary.replace(runtime)
    all_items=assembly['manifest']['replacements']
    report=dict(status='UNIFIED_STAGED_NOT_FUNCTIONALLY_ACCEPTED',runtimeJarSha256=sha(runtime.read_bytes()),
        baselineJarSha256=sha(baseline.read_bytes()),nativeReplacements={n:all_items[n] for n in active_resources(active)},
        nativeInputPaths={n:v['path'].relative_to(out).as_posix() for n,v in replacements.items()},
        canonicalBaselineJarSha256=sha(canonical.read_bytes()),internalMavenNewlineChanges=metadata_changes,
        allNativeReplacements=all_items,activeScope=active,acceptedReceiptSha256=receipts,
        retainedOriginalNativeResources=sorted(set(inventory)-set(all_items)),
        preservedEntries=assembly['preservedEntries'],paidRequests=0,releaseApproved=False)
    (out/'stage.json').write_bytes((json.dumps(report,indent=2)+'\n').encode());print(report['status'])


if __name__=='__main__':main()
