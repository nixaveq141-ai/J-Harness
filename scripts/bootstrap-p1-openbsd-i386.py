"""Bootstrap an owned native i386 VM from signed official OpenBSD netboot inputs."""
import os,platform,re,shutil,subprocess,time,urllib.request
from pathlib import Path
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run
ROOT=Path(__file__).resolve().parents[1]
BASE='https://cdn.openbsd.org/pub/OpenBSD/7.9/i386/'
PUBLIC_KEY='untrusted comment: openbsd 7.9 base public key\nRWTSdNN9A3yvWNn7mUjXwv9DOCOUnyfuV+mq1iGPIfD+NhN8EYnEQ1at\n'
FILES=['BUILDINFO','pxeboot','bsd.rd','bsd','bsd.mp','base79.tgz','comp79.tgz','man79.tgz','xbase79.tgz','xshare79.tgz']
INPUT_LIMITS={'base79.tgz':320*1024*1024}
SET_SELECTION='-game* -xfont* -xserv*'
WIDTH_PROBE='int main(void) { return sizeof(void *) != 4 || sizeof(long) != 4; }'

def copy_bounded(response,stream,limit):
    size=0
    while chunk:=response.read(65536):
        size+=len(chunk)
        if size>limit:raise ValueError('Bounded official VM input required')
        stream.write(chunk)
    return size

def manifest_digest(manifest,name):
    matches=re.findall(r'^SHA256 \('+re.escape(name)+r'\) = ([a-f0-9]{64})$',manifest,re.M)
    if len(matches)!=1:raise ValueError('Unique signed input digest required')
    return matches[0]

def qemu_arguments(out,install):
    net='user,id=net0,hostfwd=tcp:127.0.0.1:2222-:22'
    if install:net+=',tftp='+(out/'tftp').as_posix()+',bootfile=auto_install'
    return ['qemu-system-i386','-accel','tcg','-m','2048','-smp','2','-display','none','-monitor','none','-serial','stdio',
            '-drive','file='+(out/'disk.qcow2').as_posix()+',format=qcow2,if=virtio','-netdev',net,'-device','e1000,netdev=net0',
            '-boot','n' if install else 'c','-no-reboot']

def wait_installation(vm,serial_path,timeout=1500,idle_timeout=180):
    started=last_progress=time.monotonic();size=0
    while True:
        status=vm.poll()
        if status is not None:return status
        now=time.monotonic();current=serial_path.stat().st_size
        if current!=size:
            size=current;last_progress=now
            print('Owned i386 installer serial progress:',size,'bytes',flush=True)
        with serial_path.open('rb') as stream:
            stream.seek(max(0,current-8192));tail=stream.read(8192)
        if b'failed; check /tmp/ai/ai.log' in tail:raise ValueError('Owned i386 installer reported failure')
        if now-started>=timeout:raise ValueError('Owned i386 installer total time budget exceeded')
        if now-last_progress>=idle_timeout:raise ValueError('Owned i386 installer serial made no progress within idle budget')
        time.sleep(3)

def main():
    if platform.system()!='Linux' or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated Linux CI required')
    if not os.environ.get('GITHUB_RUN_ID','').isdigit() or not re.fullmatch('[a-f0-9]{40}',os.environ.get('GITHUB_SHA','')):raise ValueError('Exact CI identity required')
    out=ROOT/'.build-cache/p1-openbsd32-vm';out.mkdir(parents=True,exist_ok=False)
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],release='7.9',target='i386',commands=[],officialInputs={},paidRequests=0,productAccepted=False)
    processes=[];handles=[]
    def command(argv,name,timeout=600):
        receipt=out/(name+'-command.json')
        try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id='p1-openbsd32-'+os.environ['GITHUB_RUN_ID'],timeout=timeout)
        finally:
            if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
    def fetch(name,target):
        report['fetchingInput']=name
        with urllib.request.urlopen(BASE+name,timeout=60) as response,target.open('xb') as stream:
            copy_bounded(response,stream,INPUT_LIMITS.get(name,256*1024*1024))
    try:
        web=out/'web';sets=web/'7.9/i386';sets.mkdir(parents=True);tftp=out/'tftp';(tftp/'etc').mkdir(parents=True)
        (out/'openbsd-79-base.pub').write_bytes(PUBLIC_KEY.encode());fetch('SHA256.sig',sets/'SHA256.sig')
        manifest=(sets/'SHA256.sig').read_bytes().split(b'\n',2)[2];(sets/'SHA256').write_bytes(manifest)
        command(['signify-openbsd','-V','-p',str(out/'openbsd-79-base.pub'),'-x',str(sets/'SHA256.sig'),'-m',str(sets/'SHA256')],'verify-manifest')
        for name in FILES:
            fetch(name,sets/name);digest=stream_sha256(sets/name)
            if digest!=manifest_digest(manifest.decode(),name):raise ValueError('Signed official VM input drift: '+name)
            report['officialInputs'][name]=dict(sha256=digest,bytes=(sets/name).stat().st_size)
        shutil.copyfile(sets/'pxeboot',tftp/'auto_install');shutil.copyfile(sets/'bsd.rd',tftp/'bsd')
        (tftp/'etc/boot.conf').write_bytes(b'stty com0 115200\nset tty com0\nset timeout 1\n')
        command(['ssh-keygen','-q','-t','ed25519','-N','','-f',str(out/'vmkey')],'owned-key')
        public=(out/'vmkey.pub').read_text().strip()
        config='\n'.join(['System hostname = jh-p1-i386','Password for root = *************','Public ssh key for root account = '+public,
            'Which network interface = em0','IPv4 address for em0 = dhcp','IPv6 address for em0 = none','Which network interface = done',
            'Start sshd(8) by default = yes','Do you expect to run the X Window System = no','Change the default console to com0 = yes',
            'Which speed should com0 use = 115200','Setup a user = no','Allow root ssh login = prohibit-password','What timezone are you in = UTC',
            'Which disk is the root disk = sd0','Use (W)hole disk = whole','Use (A)uto layout = A','Location of sets = http',
            'HTTP Server = 10.0.2.2','Server directory = 7.9/i386','Set name(s) = '+SET_SELECTION,
            'Location of sets = done'])+'\n'
        (web/'install.conf').write_bytes(config.encode());report['responseSha256']=stream_sha256(web/'install.conf')
        http_log=(out/'http.log').open('wb');handles.append(http_log)
        http=subprocess.Popen(['sudo','python3','-m','http.server','80','--bind','127.0.0.1','--directory',str(web)],stdout=http_log,stderr=subprocess.STDOUT,start_new_session=True);processes.append(http)
        time.sleep(.3)
        if http.poll() is not None:raise ValueError('Owned loopback installation server failed')
        command(['qemu-img','create','-f','qcow2',str(out/'disk.qcow2'),'20G'],'owned-disk')
        serial=(out/'install-serial.log').open('wb');handles.append(serial)
        install_args=qemu_arguments(out,True);report['installCommand']=install_args
        vm=subprocess.Popen(install_args,stdin=subprocess.DEVNULL,stdout=serial,stderr=subprocess.STDOUT,start_new_session=True);processes.append(vm)
        status=wait_installation(vm,out/'install-serial.log');report['installExitCode']=status
        if status!=0:raise ValueError('Native i386 installer failed')
        serial2=(out/'boot-serial.log').open('wb');handles.append(serial2)
        boot_args=qemu_arguments(out,False);report['bootCommand']=boot_args
        vm=subprocess.Popen(boot_args,stdin=subprocess.DEVNULL,stdout=serial2,stderr=subprocess.STDOUT,start_new_session=True);processes.append(vm)
        ssh=['ssh','-i',str(out/'vmkey'),'-p','2222','-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','ConnectTimeout=5','-o','StrictHostKeyChecking=accept-new','-o','UserKnownHostsFile='+str(out/'known-hosts'),'root@127.0.0.1']
        deadline=time.monotonic()+600
        while time.monotonic()<deadline:
            if vm.poll() is not None:raise ValueError('Owned i386 VM exited before SSH ready')
            if subprocess.run([*ssh,'true'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:break
            time.sleep(3)
        else:raise ValueError('Owned i386 VM did not become ready')
        # OpenBSD getconf does not implement LONG_BIT. Execute an owned native
        # C ABI probe rather than treating an unavailable query as architecture.
        native='set -eu; uname -a; test "$(uname -m)" = i386; printf \'%s\\n\' \''+WIDTH_PROBE+'\' | cc -x c - -o /root/jh-p1-width; /root/jh-p1-width; echo NATIVE_POINTER_AND_LONG_BITS=32; mount'
        command([*ssh,native],'native-target')
        command([*ssh,'export PKG_PATH=https://cdn.openbsd.org/pub/OpenBSD/7.9/packages/i386/; pkg_add -I python%3.13 git gmake ggrep binutils jdk%21 apache-ant'],'native-tools',1200)
        # Preserve real Git identity for the shared builder; git archive drops
        # .git and cannot satisfy its exact host-commit evidence check.
        commit=os.environ['GITHUB_SHA']
        checkout='set -eu; mkdir /root/jharness; cd /root/jharness; git init; git config core.autocrlf false; git fetch --depth=1 https://github.com/nixaveq141-ai/J-Harness.git '+commit+'; git checkout --detach FETCH_HEAD; test "$(git rev-parse HEAD)" = '+commit
        command([*ssh,checkout],'guest-checkout')
        scp=['scp','-i',str(out/'vmkey'),'-P','2222','-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','StrictHostKeyChecking=accept-new','-o','UserKnownHostsFile='+str(out/'known-hosts')]
        shell='set -eu; cd /root/jharness; export JAVA_HOME=/usr/local/jdk-21 PATH=/usr/local/jdk-21/bin:$PATH GITHUB_ACTIONS=true GITHUB_RUN_ID='+os.environ['GITHUB_RUN_ID']+' GITHUB_SHA='+commit+'; set +e; /usr/local/bin/python3.13 scripts/build-p1-openbsd-jna.py x86; result=$?; tar -czf /root/component.tgz --exclude=jna -C .build-cache/p1-openbsd-jna/x86 .; archive=$?; test "$archive" = 0 || exit "$archive"; exit $result'
        try:command([*ssh,shell],'source-component',1500)
        finally:command([*scp,'root@127.0.0.1:/root/component.tgz',str(out/'component.tgz')],'copy-result')
        report.update(status='NATIVE_I386_OPENBSD_SOURCE_COMPONENT_RETURNED_NOT_INDEPENDENT_ACCEPTANCE',componentArchiveSha256=stream_sha256(out/'component.tgz'))
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                # Kill only process groups started above, including the owned
                # loopback HTTP child behind sudo. No inherited host service.
                subprocess.run(['sudo','kill','-TERM','--','-'+str(process.pid)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    subprocess.run(['sudo','kill','-KILL','--','-'+str(process.pid)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)
                    process.wait(timeout=10)
        for handle in handles:handle.close()
        atomic_write_json(out/'report.json',report)

if __name__=='__main__':main()
