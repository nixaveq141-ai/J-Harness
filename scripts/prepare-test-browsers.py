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
from evidence_io import bounded_path, atomic_write_json, stream_sha256

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return stream_sha256(path,max_bytes=512*1024*1024)
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

def _prepare(report):
    out=ROOT/'.build-cache/M10.5.9';out.mkdir(parents=True,exist_ok=True)
    selected=None
    if os.name=='nt':
        code="import {firefox} from './frontend/node_modules/playwright/index.mjs';console.log(JSON.stringify(firefox.executablePath()))"
        source=Path(json.loads(subprocess.check_output(['node','--input-type=module','-e',code],cwd=ROOT,timeout=15))).parent
        if source.resolve(strict=True)!=source.absolute():raise ValueError('Linked browser source root')
        try:
            version=subprocess.run([str(source/'firefox.exe'),'--version'],capture_output=True,text=True,timeout=10,check=True)
            report['version']=version.stdout.strip()
        except OSError as error:
            if error.winerror!=14001:raise
            value=manifest((source/'mozglue.dll').read_bytes())
            target=Path(tempfile.mkdtemp(prefix='m1059-firefox-',dir=ROOT/'.build-cache')).resolve()
            if not target.is_relative_to((ROOT/'.build-cache').resolve()):raise ValueError('Private runtime outside cache')
            report['privateRoot']=str(target)
            copied={}
            for p in source.rglob('*'):
                checked=bounded_path(source,p.relative_to(source).as_posix())
                if checked.is_file():copied[p.relative_to(source).as_posix()]=sha(checked)
            shutil.copytree(source,target,dirs_exist_ok=True)
            after={p.relative_to(source).as_posix():sha(bounded_path(source,p.relative_to(source).as_posix())) for p in source.rglob('*') if p.is_file()}
            if copied!=after:raise ValueError('Browser source changed during copy')
            if set(copied)!={p.relative_to(target).as_posix() for p in target.rglob('*') if p.is_file()}:raise ValueError('Browser copy file set mismatch')
            if not all(sha(target/p)==h for p,h in copied.items()):raise ValueError('Browser copy mismatch')
            if (target/'mozglue.manifest').exists():raise ValueError('Unexpected existing private manifest')
            (target/'mozglue.manifest').write_bytes(value)
            selected=target/'firefox.exe'
            version=subprocess.run([str(selected),'--version'],capture_output=True,text=True,timeout=10,check=True)
            report.update(status='PRIVATE_ASSEMBLY_MANIFEST',originalWinError=14001,version=version.stdout.strip(),privateRoot=str(target),
                          originalFilesSha256=copied,privateManifestSha256=sha(target/'mozglue.manifest'),originalFilesUnchanged=True)
    if selected is not None:report.update(executable=str(selected),executableSha256=sha(selected))
    atomic_write_json(out/'browser-runtime.json',report)
    return selected

def prepare():
    output=ROOT/'.build-cache/M10.5.9/browser-runtime.json'
    report=dict(status='INCOMPLETE',platform=os.name,capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),scriptSha256=sha(Path(__file__)))
    atomic_write_json(output,report)
    try:
        report['status']='DEFAULT_RUNTIME'
        return _prepare(report)
    except BaseException as failure:
        report.update(status='INCOMPLETE',failureType=type(failure).__name__)
        private=report.get('privateRoot')
        if private:
            owned=Path(private).resolve();cache=(ROOT/'.build-cache').resolve()
            if owned.parent==cache and owned.name.startswith('m1059-firefox-'):shutil.rmtree(owned)
        atomic_write_json(output,report)
        raise

if __name__=='__main__':print(prepare() or 'Default browser runtime')
