"""Stage only pinned, previously built POSIX native outputs on a fresh application."""
import argparse
import importlib.util
import json
from pathlib import Path
import platform
import shutil
import hashlib
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result


def select_resources(system,machine):
    if (system,machine)==('Linux','x86_64'):
        return '40995618881',{'jni':'com/sun/jna/linux-x86-64/libjnidispatch.so',
                            'pty':'resources/com/pty4j/native/linux/x86-64/libpty.so'}
    if (system,machine)==('Darwin','arm64'):
        return '40998915552',{'jni':'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib',
                            'pty':'resources/com/pty4j/native/darwin/libpty.dylib',
                            'helper':'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper'}
    raise ValueError('Unreviewed POSIX source platform')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);args=parser.parse_args()
    run,resources=select_resources(platform.system(),platform.machine())
    loader=module('posix_source_loader','docs/testing/tools/load-native-source-candidates.py')
    source,jni,pty,helper=loader.validate(ROOT/'.build-cache/source-candidates',run)
    if source['architecture']!=platform.machine():raise ValueError('Exact source architecture required')
    paths={'jni':jni,'pty':pty,'helper':helper}
    progress=json.loads((ROOT/'docs/testing/M10-native-source-progress-2026-10-30.json').read_bytes())
    matching=[v for v in progress['loadAudits'] if v.get('sourceRun')==run and v.get('jniSha256')==loader.sha(jni)
              and v.get('ptySha256')==loader.sha(pty) and v.get('status')=='SOURCE_CANDIDATE_LOAD_AND_NO_FALLBACK_PASS']
    if helper:
        mac=json.loads((ROOT/'docs/testing/M10-mac-native-drain-2026-10-30.json').read_bytes())
        matching=[v['report'] for v in mac['evidence'] if v['report'].get('sourceRun')==run
                  and v['report'].get('jniSha256')==loader.sha(jni)
                  and v['report'].get('ptySha256')==loader.sha(pty)
                  and v['report'].get('status')=='SOURCE_CANDIDATE_LOAD_AND_NO_FALLBACK_PASS'
                  and v['report'].get('ptyHelperSha256')==loader.sha(helper)]
        if not matching:
            raise ValueError('Reviewed macOS source helper required')
    if not matching:raise ValueError('Previously accepted source load required')
    out=ROOT/args.output
    if not out.resolve().is_relative_to((ROOT/'.build-cache').resolve()):raise ValueError('Private cache output required')
    out.mkdir(parents=True,exist_ok=True)
    runtime=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    baseline=out/'baseline-runtime.jar'
    with baseline.open('xb') as stream:stream.write(runtime.read_bytes())
    payload=out/'native-inputs';payload.mkdir();replacements={}
    for kind,resource in resources.items():
        path=paths[kind]
        if path is None:raise ValueError('Required source native output absent')
        target=payload/Path(resource).name;shutil.copyfile(path,target)
        replacements[resource]=dict(path=target,sha256=loader.sha(target),origin=dict(runId=run,commit=source['commit']))
    assembler=module('posix_assembler','docs/testing/tools/assemble-native-preview.py')
    inventory={v['resource']:v['sha256'] for v in json.loads((ROOT/'docs/testing/M10-native-inventory-48-2026-10-30.json').read_bytes())['binaries']}
    preview=out/'current-source-runtime.jar'
    assembly=assembler.assemble(baseline,preview,replacements,loader.sha(baseline),inventory,'Current POSIX application native-source qualification')
    # Assembler checks exact entry set and every retained byte before install.
    temporary=runtime.with_suffix('.source-stage.tmp')
    with temporary.open('xb') as stream:stream.write(preview.read_bytes())
    temporary.replace(runtime)
    report=dict(status='STAGED_NOT_FUNCTIONALLY_ACCEPTED',runtimeJarSha256=loader.sha(runtime),
                baselineJarSha256=loader.sha(baseline),nativeReplacements=assembly['manifest']['replacements'],
                sourceRunId=run,sourceCommit=source['commit'],preservedEntries=assembly['preservedEntries'],
                sourceReportSha256=loader.sha(next((ROOT/'.build-cache/source-candidates').rglob('report.json'))),
                paidRequests=0,releaseApproved=False)
    (out/'stage.json').write_bytes((json.dumps(report,indent=2)+'\n').encode());print(report['status'])


if __name__=='__main__':main()
