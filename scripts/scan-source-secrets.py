"""Bounded source-pattern gate; reports paths/lines, never matched secret values."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
PATTERNS={
    'openai-style-key':re.compile(rb'sk-[A-Za-z0-9_-]{32,}'),
    'github-token':re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}'),
    'aws-access-id':re.compile(rb'AKIA[0-9A-Z]{16}'),
    'private-key':re.compile(rb'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----'),
}

def findings(data):
    return [{'pattern':name,'line':data[:m.start()].count(b'\n')+1} for name,pattern in PATTERNS.items() for m in pattern.finditer(data)]

def main():
    paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode('utf-8').split('\0')
    excluded={'docs/development/code-review-2026-10-17.md','docs/testing/code-review-2026-10-17-results.json'}
    hits=[];manifest={}
    for name in sorted(set(paths)-excluded-{''}):
        path=ROOT/name
        if path.suffix not in ['.java','.py','.mjs','.ts','.tsx','.json','.yml','.md','.xml','.cmd','.ps1','.css'] and name!='.agent':continue
        data=path.read_bytes()
        if len(data)>16*1024*1024:raise ValueError('Source too large: '+name)
        manifest[name]=hashlib.sha256(data).hexdigest()
        hits.extend(dict(path=name,**f) for f in findings(data))
    report=dict(status='FINDINGS' if hits else 'PASS',sourceFiles=len(manifest),patterns=sorted(PATTERNS),findings=hits,excludedPreexistingDrafts=sorted(excluded),sourceSha256=manifest)
    output=ROOT/'target/source-secrets.json';output.parent.mkdir(exist_ok=True);output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(report['status'],'files=',len(manifest),'findings=',len(hits))
    return bool(hits)

if __name__=='__main__':raise SystemExit(main())
