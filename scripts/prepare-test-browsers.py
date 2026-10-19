"""Prepare an isolated Firefox copy only for Windows ERROR_SXS_CANT_GEN_ACTCTX.

The official EXE/DLL bytes stay unchanged. The DLL's own manifest is exposed as
an application-local assembly manifest; no global cache, registry or system DLL is modified.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def manifest(data):
    start=data.index(b'<assembly xmlns=');end=data.index(b'</assembly>',start)+len(b'</assembly>')
    value=data[start:end]
    if len(value)>2048 or b'<!' in value:raise ValueError('Unsupported assembly manifest')
    xml=ET.fromstring(value);ns='{urn:schemas-microsoft-com:asm.v1}'
    if xml.tag!=ns+'assembly' or len(xml)!=2:raise ValueError('Unexpected assembly structure')
    identity=xml.find(ns+'assemblyIdentity');file=xml.find(ns+'file')
    if identity is None or identity.attrib!={'version':'1.0.0.0','name':'mozglue','type':'win32'} or file is None or file.attrib!={'name':'mozglue.dll'}:
        raise ValueError('Unexpected assembly identity or file')
    return value

def prepare():
    out=ROOT/'.build-cache/M10.5.9';out.mkdir(parents=True,exist_ok=True)
    report=dict(status='DEFAULT_RUNTIME',platform=os.name,capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),scriptSha256=sha(Path(__file__)))
    selected=None
    if os.name=='nt':
        code="import {firefox} from './frontend/node_modules/playwright/index.mjs';console.log(JSON.stringify(firefox.executablePath()))"
        source=Path(json.loads(subprocess.check_output(['node','--input-type=module','-e',code],cwd=ROOT))).parent
        try:
            version=subprocess.run([str(source/'firefox.exe'),'--version'],capture_output=True,text=True,timeout=10,check=True)
            report['version']=version.stdout.strip()
        except OSError as error:
            if error.winerror!=14001:raise
            value=manifest((source/'mozglue.dll').read_bytes())
            target=Path(tempfile.mkdtemp(prefix='m1059-firefox-',dir=ROOT/'.build-cache')).resolve()
            if not target.is_relative_to((ROOT/'.build-cache').resolve()):raise ValueError('Private runtime outside cache')
            for p in source.rglob('*'):
                if p.is_symlink():raise ValueError('Linked browser runtime')
            shutil.copytree(source,target,dirs_exist_ok=True)
            copied={p.relative_to(source).as_posix():sha(p) for p in source.rglob('*') if p.is_file()}
            if not all(sha(target/p)==h for p,h in copied.items()):raise ValueError('Browser copy mismatch')
            if (target/'mozglue.manifest').exists():raise ValueError('Unexpected existing private manifest')
            (target/'mozglue.manifest').write_bytes(value)
            selected=target/'firefox.exe'
            version=subprocess.run([str(selected),'--version'],capture_output=True,text=True,timeout=10,check=True)
            report.update(status='PRIVATE_ASSEMBLY_MANIFEST',originalWinError=14001,version=version.stdout.strip(),privateRoot=str(target),
                          originalFilesSha256=copied,privateManifestSha256=sha(target/'mozglue.manifest'),originalFilesUnchanged=True)
    (out/'browser-runtime.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    return selected

if __name__=='__main__':print(prepare() or 'Default browser runtime')
