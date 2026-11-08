"""Probe an exact public SPARC appliance; this is not component acceptance."""
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]
IMAGE='ghcr.io/ryancnelson/sparc64-qemu-openindiana-20g@sha256:b92b4014f49d2adcac709d68902257c65076ccdcbf23b4e446e4a82ed899bd40'

def main():
    if platform.system()!='Linux' or os.environ.get('GITHUB_ACTIONS')!='true':
        raise ValueError('Dedicated Linux CI required')
    identity=[os.environ.get(k,'') for k in ['GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT']]
    if not all(re.fullmatch('[0-9]+',v) for v in identity):raise ValueError('Exact CI identity required')
    provision=os.environ.get('JHARNESS_SPARC_PROVISION','0')
    if provision not in {'0','1'}:raise ValueError('Explicit provisioning mode required')
    provision=provision=='1'
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
            if not Path('/dev/ppp').is_char_device():raise ValueError('CI PPP device required')
        command(['docker','volume','create',volume],'owned-volume');created_volume=True
        network=['--network','none'] if not provision else ['--cap-add','NET_ADMIN','--device','/dev/ppp','--sysctl','net.ipv4.ip_forward=1']
        command(['docker','create','--name',name,*network,'--memory','6g','--cpus','2',
                 '--tmpfs','/run/unit100:rw,size=1200m,mode=0700',
                 '--mount','type=volume,src='+volume+',dst=/var/lib/illumos-appliance',
                 '-e','NIAGARA_NETWORK='+('required' if provision else 'off'),'-e','CONSOLE_MODE=socket',IMAGE],'owned-container')
        created_container=True
        command(['docker','start',name],'guest-start')
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
                         '--timeout',str(timeout),'--command',shell],label,timeout+30)
            guest('set -eu; /usr/sbin/devfsadm -i sppp -i sppptun; export NIAG_CHAN_DEV=/dev/rdsk/c1d0s2; nohup /opt/niag/bin/guest-chand 0 /tmp/niag0 </dev/null >/tmp/p1-chand.log 2>&1 & i=0; until test -S /tmp/niag0; do i=$((i+1)); if test "$i" -ge 120; then cat /tmp/p1-chand.log; exit 1; fi; sleep 1; done; nohup /usr/bin/perl /opt/niag/bin/guest-ppp-chan.pl 0 10.0.5.15:10.0.5.1 </dev/null >/tmp/p1-ppp.log 2>&1 & printf "nameserver 10.0.5.1\\n" > /etc/resolv.conf; echo P1_PPP_REQUESTED','guest-network',600)
            command(['docker','exec',name,'bash','-c','for ((i=0;i<180;i++)); do if grep -qx phase=ready /state/network/status.env 2>/dev/null; then cat /state/network/status.env; exit 0; fi; sleep 2; done; exit 1'],'ppp-ready',390)
            prefix='set -eu; export http_proxy=http://10.0.5.1:8888 https_proxy=http://10.0.5.1:8888; '
            guest(prefix+'pkg set-publisher -G "*" -g https://pkg.openindiana.aurora-opencloud.org/oi-sparc/ openindiana.org','guest-publisher',1200)
            packages='developer/gcc-10 runtime/java/openjdk17 developer/build/ant developer/versioning/git developer/build/gnu-make developer/gnu-binutils system/header developer/linker'
            guest(prefix+'pkg install --accept '+packages,'guest-install-tools',2400)
            guest('set -eu; pkg list -Hv; pkg contents -m '+packages,'guest-tool-inputs',600)
            guest('set -eu; /usr/gcc/10/bin/gcc --version; java -version; ant -version; /usr/gnu/bin/make --version; git --version; /usr/bin/python3.14 -V','guest-tool-versions',300)
            report['status']='SPARC_BUILD_TOOLS_PROVISIONED_NOT_NATIVE_COMPONENT_ACCEPTANCE'
    finally:
        cleanup=[]
        if created_container and provision:
            cleanup.append((['docker','exec',name,'bash','-c','for f in /state/network-helper.log /state/network/*.log /state/network/status.env; do if test -f "$f"; then echo "$f"; tail -c 16384 "$f"; fi; done'],'network-diagnostics',30))
            cleanup.append((['docker','exec',name,'/usr/local/bin/appliance-guest-command','--socket','/state/console.sock','--timeout','45','--command','for f in /tmp/p1-chand.log /tmp/p1-ppp.log; do if test -f "$f"; then echo "$f"; tail -c 16384 "$f"; fi; done; true'],'guest-network-diagnostics',60))
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
