"""Explicit Docker-only compilation of untrusted historical model output. No paid requests."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
IMAGE = 'eclipse-temurin@sha256:f9bf7e9fe2f9bc4657537c1a7e20c768f0c3bafca41598263be4714783dd62eb'
MAX_LOG = 65536
ORACLE = r'''import example.Calculator;
public final class Oracle {
 public static void main(String[] args) throws Exception {
  var c=new Calculator();
  int[][] pairs={{2,3},{-2,3},{0,5},{-7,-9},{100,200},{Integer.MAX_VALUE,2}};
  for(var p:pairs) if(c.add(p[0],p[1])!=p[0]+p[1] || c.multiply(p[0],p[1])!=p[0]*p[1])
   throw new AssertionError("FUNCTIONAL_ORACLE_REJECTED");
  if(!java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status")).matches("(?s).*NoNewPrivs:\\s*1.*"))
   throw new AssertionError("ISOLATION_PRIVILEGES");
  for(String name:java.util.List.of("DEEPSEEK_API_KEY","GITHUB_TOKEN","JHARNESS_API_KEY"))
   if(System.getenv(name)!=null) throw new AssertionError("ISOLATION_CREDENTIAL");
  for(String path:java.util.List.of("/input/host-canary","/host-canary")) {
   boolean denied=false;
   try {java.nio.file.Files.writeString(java.nio.file.Path.of(path),"escape");}
   catch(java.io.IOException expected){denied=true;}
   if(!denied)throw new AssertionError("ISOLATION_WRITE");
  }
  if(java.nio.file.Files.exists(java.nio.file.Path.of("/var/run/docker.sock")))throw new AssertionError("ISOLATION_SOCKET");
  System.out.println("ISOLATED_FUNCTIONAL_ORACLE_PASS");
 }
}
'''


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_inspection(value, owner, input_dir):
    host, config = value['HostConfig'], value['Config']
    if (value['Name'] != '/' + owner or config['Labels'].get('io.jharness.code-eval') != owner
            or config['Image'] != IMAGE or config['User'] != '65534:65534'
            or host['NetworkMode'] != 'none' or host['ReadonlyRootfs'] is not True
            or host['CapDrop'] != ['ALL'] or host['Memory'] != 256*1024*1024
            or host['MemorySwap'] != 256*1024*1024 or host['NanoCpus'] != 1_000_000_000
            or host['PidsLimit'] != 64 or host['Privileged'] is not False
            or host['LogConfig']['Type'] != 'none'
            or host['SecurityOpt'] not in [['no-new-privileges:true'], ['no-new-privileges'], ['no-new-privileges=true']]):
        raise ValueError('Container isolation inspection rejected')
    mounts = value['Mounts']
    if (set(host['Tmpfs']) != {'/work','/tmp'} or
            any(m.get('Destination') not in ['/input','/work','/tmp'] for m in mounts) or
            len({m['Destination'] for m in mounts}) != len(mounts) or
            any(m['Type'] not in ['bind','tmpfs'] for m in mounts)):
        raise ValueError('Unexpected mounts')
    binds = [m for m in mounts if m['Type'] == 'bind']
    if (len(binds) != 1 or binds[0]['RW'] is not False or binds[0]['Destination'] != '/input'
            or Path(binds[0]['Source']).resolve() != input_dir.resolve()):
        raise ValueError('Unexpected host mount')
    for target in ['/work', '/tmp']:
        options = host['Tmpfs'].get(target, '')
        if not all(v in options.split(',') for v in ['rw','noexec','nosuid','size=16m','mode=1777']):
            raise ValueError('Unbounded writable filesystem')


def evaluate(source, directory, docker, *, oracle=ORACLE):
    """Never invokes host Java/javac; generated content is only an input file."""
    directory.mkdir()
    inputs = directory / 'input'
    inputs.mkdir()
    (inputs / 'Calculator.java').write_bytes(source)
    (inputs / 'Oracle.java').write_text(oracle, encoding='utf-8')
    (inputs / 'host-canary').write_bytes(b'original host canary')
    before = {p.name:sha(p) for p in inputs.iterdir()}
    owner = 'jharness-code-' + uuid.uuid4().hex
    env = {k:os.environ[k] for k in ['PATH','SystemRoot','WINDIR','TEMP','TMP'] if k in os.environ}
    def control(*args):
        return subprocess.run([docker,*args],env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=20,check=True).stdout
    report = dict(status='INCOMPLETE', owner=owner, image=IMAGE, inputSha256=before)
    created = False
    try:
        control('create','--pull=never','--name',owner,'--label','io.jharness.code-eval='+owner,
                '--network=none','--read-only','--cap-drop=ALL','--security-opt=no-new-privileges:true',
                '--user=65534:65534','--memory=256m','--memory-swap=256m','--cpus=1','--pids-limit=64',
                '--log-driver=none','--tmpfs','/work:rw,noexec,nosuid,size=16m,mode=1777',
                '--tmpfs','/tmp:rw,noexec,nosuid,size=16m,mode=1777','--mount','type=bind,source='+str(inputs.resolve())+',target=/input,readonly',
                '--workdir','/work',IMAGE,'/bin/sh','-c',
                'javac -J-Xmx64m -proc:none -d /work /input/Calculator.java /input/Oracle.java && java -Xmx64m -cp /work Oracle')
        created = True
        inspection = json.loads(control('inspect',owner))[0]
        report['isolationFields'] = {k:inspection['HostConfig'].get(k) for k in
            ['NetworkMode','ReadonlyRootfs','CapDrop','Memory','MemorySwap','NanoCpus','PidsLimit','Privileged','SecurityOpt','Tmpfs','LogConfig']}
        report['mounts'] = [{k:m.get(k) for k in ['Type','Destination','RW']} for m in inspection['Mounts']]
        validate_inspection(inspection,owner,inputs)
        report['isolationInspectionPassed'] = True
        log = directory / 'execution.log'
        with log.open('xb') as output:
            process = subprocess.Popen([docker,'start','--attach',owner],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            exceeded = threading.Event()
            def pump():
                kept = 0
                for block in iter(lambda:process.stdout.read(8192),b''):
                    room = MAX_LOG-kept
                    output.write(block[:room])
                    kept += min(room,len(block))
                    if len(block)>room:
                        exceeded.set()
            reader = threading.Thread(target=pump,daemon=True)
            reader.start()
            try:
                deadline = time.monotonic()+45
                while process.poll() is None:
                    if exceeded.is_set() or time.monotonic() >= deadline:
                        raise ValueError('Untrusted execution output/time bound')
                    time.sleep(.05)
            finally:
                if process.poll() is None:
                    # Container ownership was validated above; stop the owned execution first.
                    try:
                        control('kill',owner)
                    finally:
                        process.kill()
                process.wait(timeout=10)
                reader.join(5)
                process.stdout.close()
                if reader.is_alive():
                    raise ValueError('Untrusted output reader did not stop')
            if exceeded.is_set():
                raise ValueError('Untrusted output bound')
        state = json.loads(control('inspect',owner))[0]['State']
        report['containerExitCode'] = state['ExitCode']
        report['oomKilled'] = state['OOMKilled']
        report['logSha256'] = sha(log)
        report['functionalPass'] = (state['ExitCode']==0 and not state['OOMKilled']
                                    and log.read_bytes().strip()==b'ISOLATED_FUNCTIONAL_ORACLE_PASS')
        if {p.name:sha(p) for p in inputs.iterdir()} != before:
            raise ValueError('Read-only inputs mutated')
        report['inputsUnchanged'] = True
        report['status'] = 'COMPLETE'
    finally:
        try:
            if created:
                actual = json.loads(control('inspect',owner))[0]
                if actual['Config']['Labels'].get('io.jharness.code-eval') != owner:
                    raise ValueError('Cleanup ownership mismatch')
                control('rm','--force',owner)
                listing = control('ps','-aq','--filter','label=io.jharness.code-eval='+owner)
                report['cleanupVerified'] = not listing.strip()
                if not report['cleanupVerified']:
                    raise ValueError('Owned container remains')
        finally:
            (directory / 'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    return report


def main():
    if os.name != 'posix':
        raise ValueError('Use the dedicated Linux CI; no host compilation fallback')
    docker = shutil.which('docker')
    if not docker:
        raise ValueError('Docker required')
    base = ROOT / '.build-cache/isolated-code' / uuid.uuid4().hex
    base.mkdir(parents=True)
    source = ROOT / 'docs/testing/fixtures/historical-calculator.txt'
    provenance = json.loads(source.with_suffix('.provenance.json').read_bytes())
    if sha(source) != provenance['sourceSha256']:
        raise ValueError('Historical source drift')
    digest = subprocess.run([docker,'image','inspect',IMAGE,'--format','{{json .RepoDigests}}'],capture_output=True,check=True,timeout=20).stdout
    if not any(v.endswith('@'+IMAGE.split('@')[1]) for v in json.loads(digest)):
        raise ValueError('Pinned image missing')
    tracked = [Path(__file__).resolve(),source,source.with_suffix('.provenance.json'),
               ROOT/'.github/workflows/gap-code-isolation.yml']
    report = dict(status='INCOMPLETE',paidRequests=0,image=IMAGE,sourceSha256=sha(source),runs=[],
        commit=os.environ.get('GITHUB_SHA'),ciRunId=os.environ.get('GITHUB_RUN_ID'),
        sourcesBefore={p.relative_to(ROOT).as_posix():sha(p) for p in tracked})
    try:
        for label,code in [('historical',source.read_bytes()),('wrong-answer',source.read_bytes().replace(b'return a * b;',b'return 0;'))]:
            run = evaluate(code,base/label,docker)
            if run['functionalPass'] != (label=='historical') or not run['cleanupVerified']:
                raise ValueError('Independent functional oracle mismatch')
            if label=='wrong-answer' and b'FUNCTIONAL_ORACLE_REJECTED' not in (base/label/'execution.log').read_bytes():
                raise ValueError('Incorrect code must reach the behavioral oracle')
            report['runs'].append(run)
        report['sourcesAfter'] = {p.relative_to(ROOT).as_posix():sha(p) for p in tracked}
        if report['sourcesAfter'] != report['sourcesBefore']:
            raise ValueError('Source drift')
        report['status']='PASS'
    finally:
        (base/'evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('PASS historical generated code isolated / incorrect implementation rejected / owned cleanup verified')


if __name__ == '__main__':
    main()
