"""Run actual directory publication and a controlled absolute-path regression on supported providers."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[3]


def main():
    base = ROOT / '.build-cache/index-capability' / uuid.uuid4().hex
    base.mkdir(parents=True)
    source = ROOT / 'harness-session/src/main/java/io/jharness/session/IndexFiles.java'
    probe = ROOT / 'harness-session/src/test/java/io/jharness/session/IndexCapabilityProbe.java'
    suffix = '.exe' if os.name == 'nt' else ''
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    api = ROOT / 'harness-api/target/classes'
    workspace = ROOT / 'harness-workspace/target/classes'
    jna = Path(os.environ.get('JHARNESS_MAVEN_REPO',str(Path.home()/'.m2/repository'))) / 'net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar'
    if not jna.is_file():raise ValueError('Pinned JNA dependency missing')
    attack=ROOT/'harness-workspace/src/test/java/io/jharness/workspace/WindowsDirectoryPinAttackProbe.java'
    dependencies=os.pathsep.join([str(api),str(workspace),str(jna)])
    report = dict(status='INCOMPLETE', sources={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__).resolve(),source,probe,attack,ROOT/'harness-workspace/src/main/java/io/jharness/workspace/DirectoryCapability.java',ROOT/'harness-workspace/src/main/java/io/jharness/workspace/WindowsDirectoryPin.java']})
    try:
        classes = base / 'classes'
        classes.mkdir()
        with (base / 'compile.log').open('xb') as out:
            subprocess.run([str(java / ('javac'+suffix)), '-encoding', 'UTF-8', '-cp', dependencies, '-d', str(classes),str(source),str(probe),str(attack)],stdout=out,stderr=subprocess.STDOUT,timeout=60,check=True)
        command = [str(java / ('java'+suffix)), '-cp',os.pathsep.join([str(classes),dependencies]),'io.jharness.session.IndexCapabilityProbe']
        with (base / 'current.log').open('xb') as out:
            subprocess.run(command+[str(base/'current')],stdout=out,stderr=subprocess.STDOUT,timeout=30,check=True)
        if os.name=='nt':
            with (base/'native-reparse.log').open('xb') as out:
                subprocess.run(command[:-1]+['io.jharness.workspace.WindowsDirectoryPinAttackProbe',str(base/'reparse')],stdout=out,stderr=subprocess.STDOUT,timeout=30,check=True)
            report['nativeReparseAndCleanup']='PASS'
        report['current'] = json.loads((base/'current/result.json').read_bytes())
        if report['current']['secureProvider'] or report['current'].get('windowsPinned') or report['current'].get('posixDescriptor'):
            old = base / 'IndexFiles.java'
            text=source.read_text(encoding='utf-8')
            needle='if (directory instanceof SecureDirectoryStream<Path> secure)'
            assert text.count(needle)==2
            text=text.replace(needle,'if (false && directory instanceof SecureDirectoryStream<Path> secure)')
            pin='return WindowsDirectoryPin.open(root, identity.fileKey());'
            assert text.count(pin)==1
            text=text.replace(pin,'return () -> {};')
            text=text.replace('private static boolean nativeEnabled() {\n        return true;\n    }','private static boolean nativeEnabled() {return false;}')
            old.write_text(text,encoding='utf-8')
            with (base/'controlled-compile.log').open('xb') as out:
                subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-cp',dependencies,'-d',str(classes),str(old),str(probe)],stdout=out,stderr=subprocess.STDOUT,timeout=60,check=True)
            for phase in ['publish','read','lock']:
                log=base/('controlled-old-'+phase+'.log')
                with log.open('xb') as out:
                    answer=subprocess.run(command+[str(base/('controlled-old-'+phase)),phase],stdout=out,stderr=subprocess.STDOUT,timeout=30)
                expected=b'redirected index '+(b'publication' if phase=='publish' else phase.encode())
                assert answer.returncode!=0 and expected in log.read_bytes()
            report['controlledOldRejected']=True
            report['controlledOldPhasesRejected']=['publish','read','lock']
        report['status']=report['current']['status']
        report['logs']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in base.glob('*.log')}
        assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==digest for p,digest in report['sources'].items())
    finally:
        (base/'evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=report['status'],directory=base.relative_to(ROOT).as_posix())))


if __name__=='__main__':
    main()
