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
        command(['docker','volume','create',volume],'owned-volume');created_volume=True
        command(['docker','create','--name',name,'--network','none','--memory','6g','--cpus','2',
                 '--tmpfs','/run/unit100:rw,size=1200m,mode=0700',
                 '--mount','type=volume,src='+volume+',dst=/var/lib/illumos-appliance',
                 '-e','NIAGARA_NETWORK=off','-e','CONSOLE_MODE=socket',IMAGE],'owned-container')
        created_container=True
        command(['docker','start',name],'guest-start')
        command(['docker','exec','-e','AUTO_BOOT_REQUIRED=1','-e','LOGIN_TIMEOUT_SECONDS=1200',
                 '-e','CONSOLE_SOCKET=/state/console.sock','-e','EVIDENCE_PATH=/state/p1-login.log',
                 name,'/usr/local/bin/appliance-smoke-login'],'native-login',1250)
        probe='uname -a; isainfo -kv; for t in cc gcc gmake make python3 python3.13 python3.12 git java ant pkg; do command -v "$t" || :; done; /usr/bin/pkg publisher; df -h /; true'
        command(['docker','exec',name,'/usr/local/bin/appliance-guest-command','--socket','/state/console.sock',
                 '--timeout','90','--command',probe],'guest-tools',120)
        report['status']='SPARC_GUEST_TOOL_INVENTORY_RETURNED_NOT_BUILD_OR_COMPONENT_ACCEPTANCE'
    finally:
        cleanup=[]
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
