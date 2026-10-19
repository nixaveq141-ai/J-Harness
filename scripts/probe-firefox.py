"""Record Windows process-start availability; this is never a browser acceptance pass."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
code="import {firefox} from './frontend/node_modules/playwright/index.mjs';console.log(JSON.stringify(firefox.executablePath()))"
path=Path(json.loads(subprocess.check_output(['node','--input-type=module','-e',code],cwd=ROOT)))
record=dict(executableSha256=hashlib.sha256(path.read_bytes()).hexdigest(),status='NOT_TESTED')
try:
    result=subprocess.run([str(path),'--version'],capture_output=True,text=True,timeout=10)
    record.update(status='VERSION_ONLY' if result.returncode==0 else 'START_FAILED',exitCode=result.returncode)
except OSError as error:
    record.update(status='UNAVAILABLE',winerror=getattr(error,'winerror',None),reason=str(error)[:512])
output=ROOT/'.build-cache/M10.5.9/availability.json';output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(dict(capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),firefox=record),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(record['status'],record.get('winerror'))
