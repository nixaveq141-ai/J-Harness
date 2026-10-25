"""Own one temporary CI keychain and restore the exact previous default/search list."""
import json
import os
from pathlib import Path
import secrets
import shlex
import subprocess
import sys
from evidence_io import atomic_write_json

def security(*args):
    try:return subprocess.check_output(['security',*args],text=True,timeout=15,stderr=subprocess.PIPE).strip()
    except (OSError,subprocess.SubprocessError):raise RuntimeError('CI keychain operation failed') from None

def main(action):
    if sys.platform!='darwin':raise ValueError('macOS keychain CI helper only')
    root=Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    state=root/'jharness-keychain-state.json'
    if action=='setup':
        if state.exists():raise ValueError('Keychain ownership already recorded')
        keychain=root/('jharness-'+secrets.token_hex(12)+'.keychain-db')
        record=dict(default=shlex.split(security('default-keychain','-d','user')),
                    search=shlex.split(security('list-keychains','-d','user')),owned=str(keychain))
        atomic_write_json(state,record)
        password=secrets.token_hex(32)
        security('create-keychain','-p',password,str(keychain))
        security('set-keychain-settings','-lut','3600',str(keychain))
        security('unlock-keychain','-p',password,str(keychain))
        security('default-keychain','-d','user','-s',str(keychain))
        security('list-keychains','-d','user','-s',str(keychain))
    elif action=='cleanup':
        if not state.exists():return
        record=json.loads(state.read_bytes());owned=Path(record['owned'])
        if owned.parent!=root or not owned.name.startswith('jharness-'):raise ValueError('Invalid owned keychain path')
        if record['default']:security('default-keychain','-d','user','-s',*record['default'])
        security('list-keychains','-d','user','-s',*record['search'])
        if owned.exists():security('delete-keychain',str(owned))
        state.unlink()
    else:raise ValueError('Expected setup/cleanup')

if __name__=='__main__':main(sys.argv[1])
