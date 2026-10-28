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
    report = dict(status='INCOMPLETE', sources={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__).resolve(),source,probe]})
    try:
        classes = base / 'classes'
        classes.mkdir()
        with (base / 'compile.log').open('xb') as out:
            subprocess.run([str(java / ('javac'+suffix)), '-encoding', 'UTF-8', '-cp', str(api), '-d', str(classes),str(source),str(probe)],stdout=out,stderr=subprocess.STDOUT,timeout=60,check=True)
        command = [str(java / ('java'+suffix)), '-cp',os.pathsep.join([str(classes),str(api)]),'io.jharness.session.IndexCapabilityProbe']
        with (base / 'current.log').open('xb') as out:
            subprocess.run(command+[str(base/'current')],stdout=out,stderr=subprocess.STDOUT,timeout=30,check=True)
        report['current'] = json.loads((base/'current/result.json').read_bytes())
        if report['current']['secureProvider']:
            old = base / 'IndexFiles.java'
            text=source.read_text(encoding='utf-8')
            needle='if (directory instanceof SecureDirectoryStream<Path> secure)'
            assert text.count(needle)==1
            old.write_text(text.replace(needle,'if (false && directory instanceof SecureDirectoryStream<Path> secure)'),encoding='utf-8')
            with (base/'controlled-compile.log').open('xb') as out:
                subprocess.run([str(java/('javac'+suffix)),'-encoding','UTF-8','-cp',str(api),'-d',str(classes),str(old),str(probe)],stdout=out,stderr=subprocess.STDOUT,timeout=60,check=True)
            with (base/'controlled-old.log').open('xb') as out:
                answer=subprocess.run(command+[str(base/'controlled-old')],stdout=out,stderr=subprocess.STDOUT,timeout=30)
            assert answer.returncode!=0 and b'redirected index publication' in (base/'controlled-old.log').read_bytes()
            report['controlledOldRejected']=True
        report['status']=report['current']['status']
        report['logs']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in base.glob('*.log')}
        assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==digest for p,digest in report['sources'].items())
    finally:
        (base/'evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=report['status'],directory=base.relative_to(ROOT).as_posix())))


if __name__=='__main__':
    main()
