"""Probe an exact public SPARC appliance; this is not component acceptance."""
import os
import json
import platform
import re
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]
IMAGE='ghcr.io/ryancnelson/sparc64-qemu-openindiana-20g@sha256:b92b4014f49d2adcac709d68902257c65076ccdcbf23b4e446e4a82ed899bd40'


def component_download_code(run_id,commit,digest):
    if not (run_id.isdigit() and re.fullmatch('[a-f0-9]{40}',commit) and re.fullmatch('[a-f0-9]{64}',digest)):
        raise ValueError('Exact owned bundle identity required')
    return '''import hashlib,io,json,subprocess,sys,tarfile,urllib.request
from pathlib import Path
base=Path('/var/tmp/jh-p1-'''+run_id+'''')
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
with opener.open('http://10.0.5.1:8000/guest-bundle.tgz',timeout=60) as response:
    data=response.read(8*1024*1024+1)
assert len(data)<=8*1024*1024 and hashlib.sha256(data).hexdigest()=='''+repr(digest)+'''
with tarfile.open(fileobj=io.BytesIO(data)) as archive:
    members=archive.getmembers()
    expected={'manifest.json','guest-run.py','smoke-32','smoke-64'}
    expected.update(str(bits)+'-'+name for bits in [32,64] for name in ['libjnidispatch.so','jni-probe','callback-control'])
    assert len(members)==len(expected) and {m.name for m in members}==expected
    assert all(m.isfile() and m.size<=4*1024*1024 for m in members)
    base.mkdir(mode=0o700)
    for member in members:
        with (base/member.name).open('xb') as out:out.write(archive.extractfile(member).read())
manifest=json.loads((base/'manifest.json').read_bytes())
assert manifest['runId']=='''+repr(run_id)+''' and manifest['commit']=='''+repr(commit)+'''
subprocess.run([sys.executable,str(base/'guest-run.py')],check=True,timeout=1100)
'''

def isolated_guest_shell(shell):
    # Keep the interactive console alive so its driver can report a real
    # failure exit code instead of waiting for a marker after logout.
    return 'set +e; ( '+shell+' )'

def main():
    if platform.system()!='Linux' or os.environ.get('GITHUB_ACTIONS')!='true':
        raise ValueError('Dedicated Linux CI required')
    identity=[os.environ.get(k,'') for k in ['GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT']]
    if not all(re.fullmatch('[0-9]+',v) for v in identity):raise ValueError('Exact CI identity required')
    provision=os.environ.get('JHARNESS_SPARC_PROVISION','0')
    if provision not in {'0','1'}:raise ValueError('Explicit provisioning mode required')
    provision=provision=='1'
    components=os.environ.get('JHARNESS_SPARC_COMPONENTS','0')
    if components not in {'0','1'} or (components=='1' and not provision):raise ValueError('Components require explicit owned guest networking')
    components=components=='1'
    bundle=ROOT/'.build-cache/p1-sparc-jna-cross/guest-bundle.tgz'
    if components and not bundle.is_file():raise ValueError('Fresh cross-built component bundle required')
    out=ROOT/'.build-cache/p1-sparc-toolchain';out.mkdir(parents=True,exist_ok=False)
    name='jh-p1-sparc-'+'-'.join(identity);volume=name+'-disk'
    report=dict(status='INCOMPLETE',image=IMAGE,runId=identity[0],attempt=identity[1],
                commit=os.environ['GITHUB_SHA'],commands=[],productAccepted=False,paidRequests=0)
    created_container=created_volume=False
    def command(argv,label,timeout=120):
        receipt=out/(label+'-command.json')
        try:run(argv,name=label,log=out/(label+'.log'),receipt=receipt,run_id=name,timeout=timeout)
        finally:
            if receipt.is_file():report['commands'].append(dict(name=label,sha256=stream_sha256(receipt)))
    try:
        if shutil.disk_usage(ROOT).free<10*1024**3:raise ValueError('At least 10 GiB free CI disk required')
        # Refuse to take ownership of any preexisting container or volume.
        for kind,value in [('container',name),('volume',volume)]:
            if subprocess.run(['docker',kind,'inspect',value],capture_output=True).returncode==0:
                raise ValueError('Fresh owned Docker resource required')
        command(['docker','pull',IMAGE],'image-pull',600)
        command(['docker','image','inspect',IMAGE],'image-identity')
        if provision:
            command(['sudo','modprobe','ppp_generic'],'ppp-host-module')
            command(['sudo','modprobe','ppp_async'],'ppp-host-async-module')
            if not Path('/dev/ppp').is_char_device():raise ValueError('CI PPP device required')
        command(['docker','volume','create',volume],'owned-volume');created_volume=True
        network=['--network','none'] if not provision else ['--cap-add','NET_ADMIN','--device','/dev/ppp','--sysctl','net.ipv4.ip_forward=1','-e','PPPD=/tmp/jh-p1-pppd']
        command(['docker','create','--name',name,*network,'--memory','6g','--cpus','2',
                 '--tmpfs','/run/unit100:rw,size=1200m,mode=0700',
                 '--mount','type=volume,src='+volume+',dst=/var/lib/illumos-appliance',
                 '-e','NIAGARA_NETWORK='+('required' if provision else 'off'),'-e','CONSOLE_MODE=socket',IMAGE],'owned-container')
        created_container=True
        command(['docker','start',name],'guest-start')
        if provision:
            # PPP payload stays on stdin/stdout; diagnostics use stderr only.
            command(['docker','exec',name,'sh','-c','printf \'#!/bin/sh\\nexec /usr/sbin/pppd logfd 2 "$@"\\n\' > /tmp/jh-p1-pppd; chmod 0700 /tmp/jh-p1-pppd'],'ppp-log-wrapper')
        command(['docker','exec','-e','AUTO_BOOT_REQUIRED=1','-e','LOGIN_TIMEOUT_SECONDS=1200',
                 '-e','CONSOLE_SOCKET=/state/console.sock','-e','EVIDENCE_PATH=/state/p1-login.log',
                 name,'/usr/local/bin/appliance-smoke-login'],'native-login',1250)
        # pkg publisher invokes the IPS Python stack on a cold emulated guest;
        # inventory executable paths directly before attempting package setup.
        probe='uname -a; isainfo -kv; for t in cc gcc gmake make python3 python3.13 python3.12 git java ant pkg; do command -v "$t" || :; done; for p in /usr/gcc/*/bin/gcc /usr/jdk/*/bin/java /usr/bin/python* /usr/gnu/bin/make /usr/bin/git /usr/bin/ant; do if test -x "$p"; then echo "EXECUTABLE=$p"; fi; done; /usr/bin/python3 -V; echo P1_TOOL_PATH_INVENTORY_DONE'
        command(['docker','exec',name,'/usr/local/bin/appliance-guest-command','--socket','/state/console.sock',
                 '--timeout','90','--command',probe],'guest-tools',120)
        report['status']='SPARC_GUEST_TOOL_INVENTORY_RETURNED_NOT_BUILD_OR_COMPONENT_ACCEPTANCE'
        if provision:
            def guest(shell,label,timeout):
                command(['docker','exec',name,'/usr/local/bin/appliance-guest-command','--socket','/state/console.sock',
                         '--timeout',str(timeout),'--command',isolated_guest_shell(shell)],label,timeout+30)
            guest('set -eu; /usr/sbin/devfsadm -i sppp -i sppptun; export NIAG_CHAN_DEV=/dev/rdsk/c1d0s2; nohup /opt/niag/bin/guest-chand 0 /tmp/niag0 </dev/null >/tmp/p1-chand.log 2>&1 & i=0; until test -S /tmp/niag0; do i=$((i+1)); if test "$i" -ge 120; then cat /tmp/p1-chand.log; exit 1; fi; sleep 1; done; nohup /usr/bin/perl /opt/niag/bin/guest-ppp-chan.pl 0 10.0.5.15:10.0.5.1 </dev/null >/tmp/p1-ppp.log 2>&1 & printf "nameserver 10.0.5.1\\n" > /etc/resolv.conf; echo P1_PPP_REQUESTED','guest-network',600)
            command(['docker','exec',name,'bash','-c','for ((i=0;i<180;i++)); do if grep -qx phase=ready /state/network/status.env 2>/dev/null; then cat /state/network/status.env; exit 0; fi; sleep 2; done; exit 1'],'ppp-ready',390)
            if components:
                build_report=ROOT/'.build-cache/p1-sparc-jna-cross/report.json'
                built=json.loads(build_report.read_bytes())
                digest=stream_sha256(bundle)
                if (built['status']!='SOURCE_COMPONENTS_CROSS_BUILT_NOT_EXECUTED' or built['bundleSha256']!=digest
                        or built['runId']!=identity[0] or built['commit']!=os.environ['GITHUB_SHA']):
                    raise ValueError('Same-run cross-built bundle required')
                command(['docker','exec',name,'mkdir','/tmp/jh-p1-http'],'owned-component-http-root')
                command(['docker','cp',str(bundle),name+':/tmp/jh-p1-http/guest-bundle.tgz'],'copy-component-bundle')
                command(['docker','exec','-d',name,'python3','-m','http.server','8000','--bind','10.0.5.1','--directory','/tmp/jh-p1-http'],'owned-component-http')
                code=component_download_code(identity[0],os.environ['GITHUB_SHA'],digest)
                guest('set -eu; /usr/bin/python3.14 -c '+shlex.quote(code),'guest-cross-components',1200)
                results=[]
                for line in (out/'guest-cross-components.log').read_text(errors='replace').splitlines():
                    if line.startswith('P1_COMPONENT_RESULTS_JSON='):
                        results.append(json.loads(line.split('=',1)[1]))
                if (len(results)!=1 or results[0]['status']!='BOTH_SPARC_COMPONENTS_NATIVE_PRIMITIVES_FFI_CLOSURE_AND_MISSING_PASS'
                        or results[0]['runId']!=identity[0] or results[0]['commit']!=os.environ['GITHUB_SHA']
                        or len(results[0]['commands'])!=8 or any(c['exitCode']!=0 for c in results[0]['commands'])):
                    raise ValueError('Exact guest component results required')
                atomic_write_json(out/'guest-component-results.json',results[0])
                report.update(status='SPARC_CROSS_BUILT_COMPONENTS_EXECUTED_NOT_INDEPENDENT_ACCEPTANCE',
                              componentBundleSha256=digest,crossBuildReportSha256=stream_sha256(build_report),
                              guestResultsSha256=stream_sha256(out/'guest-component-results.json'))
                return
            prefix='set -eu; export http_proxy=http://10.0.5.1:8888 https_proxy=http://10.0.5.1:8888; '
            guest(prefix+'pkg set-publisher -G "*" -g https://pkg.openindiana.aurora-opencloud.org/oi-sparc/ openindiana.org','guest-publisher',1200)
            packages='developer/gcc-10 runtime/java/openjdk17 developer/build/ant developer/versioning/git developer/build/gnu-make developer/gnu-binutils system/header developer/linker'
            request='consolidation/userland/userland-incorporation@latest '+packages
            # The real install performs and records the same solver plan;
            # avoid solving the whole emulated image twice.
            guest(prefix+'pkg install --accept '+request,'guest-install-tools',2400)
            guest('set -eu; pkg list -Hv; pkg contents -m '+packages,'guest-tool-inputs',600)
            guest('set -eu; /usr/gcc/10/bin/gcc --version; java -version; ant -version; /usr/gnu/bin/make --version; git --version; /usr/bin/python3.14 -V','guest-tool-versions',300)
            report['status']='SPARC_BUILD_TOOLS_PROVISIONED_NOT_NATIVE_COMPONENT_ACCEPTANCE'
    finally:
        cleanup=[]
        if created_container and provision:
            cleanup.append((['docker','exec',name,'bash','-c','for f in /state/network-helper.log /state/network/*.log /state/network/status.env; do if test -f "$f"; then echo "$f"; tail -c 16384 "$f"; fi; done'],'network-diagnostics',30))
            cleanup.append((['docker','exec',name,'/usr/local/bin/appliance-guest-command','--socket','/state/console.sock','--timeout','45','--command','for f in /tmp/p1-chand.log /tmp/p1-ppp.log /tmp/gppp-chan0.log /tmp/gpppd-chan0.log /tmp/gppp-chan0.wait /var/adm/messages /opt/niag/bin/guest-ppp-chan.pl; do if test -f "$f"; then echo "$f"; tail -c 16384 "$f"; fi; done; true'],'guest-network-diagnostics',60))
        if created_container:
            cleanup.extend([(['docker','logs','--tail','160',name],'container-log',30),
                            (['docker','rm','--force',name],'owned-container-cleanup',60)])
        if created_volume:cleanup.append((['docker','volume','rm',volume],'owned-volume-cleanup',60))
        errors=[]
        for argv,label,timeout in cleanup:
            try:command(argv,label,timeout)
            except Exception as error:errors.append(dict(name=label,failureType=type(error).__name__))
        if errors:report['cleanupFailures']=errors
        atomic_write_json(out/'report.json',report)
        if errors and sys.exc_info()[0] is None:raise ValueError('Owned SPARC cleanup or log collection failed')

if __name__=='__main__':main()
