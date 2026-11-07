"""Exercise exact source-built Console payloads on a real ARM64 JVM and Windows host."""
import importlib.util,json,os,platform,shutil
from pathlib import Path
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run
ROOT=Path(__file__).resolve().parents[1]
CONSOLE_REPORT='73ae8d1c0d7f151df04d93acb9ed8d23dea44716ee632129e060d99cafbaf37a'
OUTPUTS={'OpenConsole.exe':'b0b5c764e2b2960b816f538858f054bee4dabf67b191b3a0e5dad92e32cb556d','conpty.dll':'2aff71f03b604ce4afad2b51dac1b81f3dc7f82aa63d6d618d9410009d64268c'}

def module(name,path):
    s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def load_console(base):
    if stream_sha256(base/'report.json')!=CONSOLE_REPORT:raise ValueError('Exact Console source report required')
    r=json.loads((base/'report.json').read_bytes())
    if (r['status']!='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE' or r['runId']!='42322393412'
            or r['commit']!='ebd89895c41b3570cd5acfe173931894cfc081ef' or r['targetArchitecture']!='arm64'
            or len(r['outputs'])!=2 or r['paidRequests']!=0):raise ValueError('Exact ARM64 Console build identity required')
    arm=module('arm_pe',ROOT/'scripts/build-p1-windows-arm64-jna.py');result={}
    for item in r['outputs']:
        name=Path(item['path']).name;p=base/'console/bin/ARM64/Release'/name
        if name not in OUTPUTS or name in result or p.is_symlink() or stream_sha256(p)!=OUTPUTS[name] or item['sha256']!=OUTPUTS[name]:raise ValueError('Console output drift')
        if arm.require_arm64_pe(p.read_bytes())!=r['outputPeIdentities'][item['path']]:raise ValueError('Console ARM64 identity drift')
        result[name]=p
    return result

def main():
    if os.name!='nt' or platform.machine().lower() not in {'arm64','aarch64'} or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Native ARM64 Windows CI required')
    out=ROOT/'.build-cache/p1-arm64-console-probe';out.mkdir(parents=True,exist_ok=False)
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],commands=[],sourceReportSha256=CONSOLE_REPORT,outputs=OUTPUTS,paidRequests=0,productAccepted=False)
    def command(argv,name,timeout=600):
        receipt=out/(name+'-command.json')
        try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id='p1-arm64-console-'+os.environ['GITHUB_RUN_ID'],timeout=timeout)
        finally:
            if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
    try:
        outputs=load_console(ROOT/'.build-cache/p1-console-input')
        arm=module('arm_stager',ROOT/'scripts/stage-p1-arm64-components.py');components=arm.load_components(ROOT/'.build-cache/p1-arm64-inputs')
        native=out/'native';jni=native/'jni';pty=native/'pty/win/aarch64';jni.mkdir(parents=True);pty.mkdir(parents=True)
        for resource,item in components.items():
            target=(jni if resource.startswith('com/sun/jna/') else pty)/item['path'].name;shutil.copyfile(item['path'],target)
        for name,p in outputs.items():shutil.copyfile(p,pty/name)
        report['nativeInputs']={p.relative_to(native).as_posix():stream_sha256(p) for p in native.rglob('*') if p.is_file()}
        java=Path(os.environ['JAVA_HOME'])/'bin/java.exe';javac=java.with_name('javac.exe')
        command([str(java),'-XshowSettings:properties','-version'],'java',30)
        command(['mvnw.cmd','-B','-DskipTests','package'],'jar-build',1200)
        jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';report['jarSha256']=stream_sha256(jar)
        classes=out/'classes';classes.mkdir()
        sources=[ROOT/'docs/testing/tools/WinPtySourceProbe.java',ROOT/'scripts/probes/Arm64ConPtySourceProbe.java']
        command([str(javac),'--release','21','-proc:none','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*[str(p) for p in sources]],'compile',90)
        report['probeSources']={p.relative_to(ROOT).as_posix():stream_sha256(p) for p in sources}
        for mode in ['present','missing-jni','missing-conpty']:
            work=out/(mode+'-work');work.mkdir();selected_jni=jni;selected_pty=native/'pty'
            if mode=='missing-jni':selected_jni=out/'empty-jni';selected_jni.mkdir()
            if mode=='missing-conpty':
                selected_pty=out/'missing-pty';missing=selected_pty/'win/aarch64';missing.mkdir(parents=True);shutil.copyfile(pty/'win-helper.dll',missing/'win-helper.dll')
            command([str(java),'-Djna.nounpack=true','-Djna.nosys=true','-Djna.debug_load.jna=true','-Djna.boot.library.path='+str(selected_jni),'-Dpty4j.preferred.native.folder='+str(selected_pty),'-cp',str(jar)+os.pathsep+str(classes),'Arm64ConPtySourceProbe',str(work),mode],mode+'-probe',120)
        if report['nativeInputs']!={p.relative_to(native).as_posix():stream_sha256(p) for p in native.rglob('*') if p.is_file()}:raise ValueError('Native inputs changed during probe')
        report.update(status='ARM64_CONPTY_JAVA_SOURCE_COMPONENT_PASS_NOT_FULL_PRODUCT_ACCEPTANCE',hostImage={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH']},limits=['Native ARM64 JVM source-component integration only; full application five-target qualification remains required','Console fixed source/static dependency independent audit still required'])
    finally:atomic_write_json(out/'report.json',report)

if __name__=='__main__':main()
