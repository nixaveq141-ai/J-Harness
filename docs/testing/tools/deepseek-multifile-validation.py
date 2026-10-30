"""Real multi-file repair under the EXISTING RMB5 grant; no host code execution.

Only --run sends requests. Exact two synthetic write targets, version pins,
two explicit CLI approvals, read/write tools only; no new wallet or retry.
"""
import argparse
from http.server import BaseHTTPRequestHandler, HTTPServer
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import urllib.error
import urllib.request

spec = importlib.util.spec_from_file_location('fresh', Path(__file__).with_name('deepseek-fresh-code-validation.py'))
fresh = importlib.util.module_from_spec(spec); spec.loader.exec_module(fresh)
base = fresh.base
FILES = {
    'Price.java': b'package example; public final class Price { public static long cents(String text) { return 0; } }\n',
    'Invoice.java': b'package example; public final class Invoice { public static long total(String[] items) { return 0; } }\n'}
REQUIREMENTS = ('''Repair both existing Java21 files using write_file (overwrite=true, expectedVersion from below).
Price.cents(String): trim and parse a nonnegative decimal amount into exact long cents. No rounding.
Reject null, blank, negative, malformed amounts, fractional cents and overflow using IllegalArgumentException.
Invoice.total(String[]): sum amounts using Price.cents, reject null array or invalid element and sum overflow
using IllegalArgumentException. Empty array returns zero. Use only JDK; no IO/env/network/process/reflection.
Read requirements.txt once, then issue both write_file calls together, then finish. Do not run commands.
Initial files and their SHA256 expectedVersion values follow:\n'''+
    '\n'.join(name+' SHA256='+fresh.digest(raw)+'\n'+raw.decode() for name,raw in FILES.items())).encode()


def checked_request(body):
    value = json.loads(body)
    tools = value.get('tools', [])
    if not isinstance(tools,list) or len(tools)>2: raise ValueError('Tool inventory rejected')
    for tool in tools:
        if tool.get('type') != 'function' or tool.get('function',{}).get('name') not in ['read_file','write_file']:
            raise ValueError('Tool inventory rejected')
    base.checked_request('/chat/completions',json.dumps(dict(value,tools=[])).encode())
    return value


def checked_response(data, credentials):
    value = json.loads(data)
    def clean(item):
        if isinstance(item,str): return not any(key in item for key in credentials)
        if isinstance(item,list): return all(clean(i) for i in item)
        if isinstance(item,dict): return all(clean(k) and clean(v) for k,v in item.items())
        return True
    if not clean(value): raise ValueError('Reflected credential')
    choices = value.get('choices',[])
    if len(choices)!=1: raise ValueError('One completion required')
    for call in choices[0]['message'].get('tool_calls',[]):
        function = call['function']; args = json.loads(function['arguments']); name = function['name']
        path = args.get('path')
        if name=='read_file' and path in [*FILES,'requirements.txt']: continue
        if (name!='write_file' or path not in FILES or args.get('overwrite') is not True
                or args.get('expectedVersion')!=fresh.digest(FILES[path])
                or not isinstance(args.get('content'),str) or len(args['content'].encode())>16384):
            raise ValueError('Exact write target/version rejected')
    return value


def handler(wallet,key,token,directory):
    class Relay(BaseHTTPRequestHandler):
        def log_message(self,*args): pass
        def do_POST(self):
            status,data=502,b'{"error":"BOUNDED_MULTIFILE_RELAY_FAILURE"}'
            try:
                if self.path!='/chat/completions' or self.headers.get('Authorization')!='Bearer '+token:
                    raise ValueError('Authentication/path rejected')
                length=int(self.headers.get('Content-Length','-1'))
                if not 0<length<=32768 or self.headers.get('Transfer-Encoding'):raise ValueError('Input bound')
                self.connection.settimeout(10);body=self.rfile.read(length)
                if len(body)!=length:raise ValueError('Partial request')
                checked_request(body);attempt=wallet.reserve(self.path,body)
                request=urllib.request.Request(base.PATHS[self.path],body,
                    {'Content-Type':'application/json','Authorization':'Bearer '+key})
                try: response=urllib.request.build_opener(base.NoRedirect()).open(request,timeout=65)
                except urllib.error.HTTPError as error: response=error
                with response:status=response.code;data=response.read(2*1024*1024+1)
                if len(data)>2*1024*1024:raise ValueError('Response bound')
                wallet.receipt(attempt,status,data)
                # Preserve bounded local provider evidence before the tool gate. Never upload this file.
                parsed=json.loads(data)
                safe=json.dumps(parsed,ensure_ascii=False).encode()
                if key.encode() in safe or token.encode() in safe:raise ValueError('Reflected credential')
                (directory/('upstream-'+str(attempt)+'.json')).write_bytes(safe)
                checked_response(data,(key,token))
            except Exception as error:
                with (directory/'relay-failures.jsonl').open('ab') as out:
                    out.write((json.dumps(dict(type=type(error).__name__,reason=str(error) if isinstance(error,ValueError) else 'TRANSPORT_OR_FRAMING',upstreamStatus=status))+'\n').encode())
                status,data=502,b'{"error":"BOUNDED_MULTIFILE_RELAY_FAILURE"}'
            self.send_response(status);self.send_header('Content-Type','application/json')
            self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    return Relay


def inspect_facts(facts,directory):
    completed=[e['payload'] for e in facts if e['type']=='tool.completed']
    writes=[p for p in completed if p.get('tool')=='write_file']
    reads=[p for p in completed if p.get('tool')=='read_file']
    ended=[e['payload'] for e in facts if e['type']=='turn.ended']
    approvals=[e['payload'] for e in facts if e['type']=='approval.decided' and e['payload'].get('actor')=='user']
    allowed_reads={'requirements.txt':fresh.digest(REQUIREMENTS),**{n:fresh.digest(b) for n,b in FILES.items()}}
    requirement_reads=[p for p in reads if p.get('value',{}).get('path')=='requirements.txt']
    if (len(writes)!=len(FILES) or not 1<=len(reads)<=len(FILES)+1 or len(completed)!=len(FILES)+len(reads) or len(ended)!=1
            or ended[0].get('status')!='COMPLETED' or len(approvals)!=len(FILES)
            or not all(p.get('approved') is True for p in approvals)
            or len(requirement_reads)!=1
            or any(p.get('status')!='SUCCESS' or p.get('value',{}).get('path') not in allowed_reads
                   or p['value'].get('version')!=allowed_reads[p['value']['path']] for p in reads)):
        raise ValueError('Task facts rejected')
    for name,initial in FILES.items():
        data=(directory/name).read_bytes();matched=[p for p in writes if p.get('value',{}).get('path')==name]
        if (len(matched)!=1 or matched[0].get('status')!='SUCCESS' or fresh.digest(data)==fresh.digest(initial)
                or matched[0]['value'].get('afterVersion')!=fresh.digest(data)
                or matched[0]['value'].get('beforeVersion')!=fresh.digest(initial)):
            raise ValueError('Write evidence rejected')
    return {n:fresh.digest((directory/n).read_bytes()) for n in FILES}


def run(wallet):
    key=os.environ.get('DEEPSEEK_API_KEY')
    if not key:raise ValueError('DEEPSEEK_API_KEY missing')
    directory=fresh.DIRECTORY/('multifile-'+secrets.token_hex(8));directory.mkdir()
    for name,raw in {**FILES,'requirements.txt':REQUIREMENTS}.items():(directory/name).write_bytes(raw)
    token=secrets.token_hex(32);server=HTTPServer(('127.0.0.1',0),handler(wallet,key,token,directory))
    config=dict(baseUrl='http://127.0.0.1:'+str(server.server_port),model='deepseek-flash',
        apiKeyEnv='JHARNESS_LIVE_RELAY_TOKEN',protocol='chat-completions',maxSteps=4,durationSeconds=240,
        requestTimeoutSeconds=75,contextTokens=base.INPUT_CAP,inputTokenCeiling=base.INPUT_CAP,
        maxOutputTokens=base.OUTPUT_CAP,maxCost='0.90',currency='CNY',inputPerMillion='2',outputPerMillion='8',
        streaming=False,disableThinking=True,policy='default',enabledTools=['read_file','write_file'])
    (directory/'config.json').write_text(json.dumps(config),encoding='utf-8')
    java=Path(os.environ['JAVA_HOME'])/'bin/java.exe';jar=fresh.ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    before=fresh.digest(jar.read_bytes());env={k:os.environ[k] for k in ['SystemRoot','WINDIR','TEMP','TMP','JAVA_HOME','PATH'] if k in os.environ}
    env['JHARNESS_LIVE_RELAY_TOKEN']=token
    report=dict(status='INCOMPLETE',jarSha256=before,initialSha256={n:fresh.digest(b) for n,b in FILES.items()},
                requirementsSha256=fresh.digest(REQUIREMENTS),hostExecution=False)
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        command=[str(java),'-jar',str(jar),'--workspace',str(directory),'--config',str(directory/'config.json'),
            'run','Read requirements.txt once, repair specified files, then finish. Only read_file/write_file, no commands. '
            'EVERY write_file MUST include overwrite=true AND expectedVersion exactly from this map: '+
            json.dumps({n:fresh.digest(b) for n,b in FILES.items()}),
            '--session','multifile','--require-read','requirements.txt']
        for name in FILES:command.extend(['--expect-file',name])
        result=subprocess.run(command,env=env,input=b'y\n'*len(FILES),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=260)
        (directory/'cli.log').write_bytes(result.stdout[:1024*1024].replace(key.encode(),b'[REDACTED]').replace(token.encode(),b'[REDACTED]'))
        report['exitCode']=result.returncode
        facts_path=directory/'.jharness/sessions/multifile.jsonl'
        facts=[json.loads(l) for l in facts_path.read_text(encoding='utf-8').splitlines()][1:]
        if result.returncode:raise ValueError('CLI failed')
        hashes=inspect_facts(facts,directory)
        if any(key.encode() in (directory/n).read_bytes() for n in FILES):raise ValueError('Reflected credential')
        if fresh.digest(jar.read_bytes())!=before or (directory/'requirements.txt').read_bytes()!=REQUIREMENTS:raise ValueError('Input drift')
        report.update(status='WRITTEN_NOT_EXECUTED',sourceSha256=hashes,factsSha256=fresh.digest(facts_path.read_bytes()))
    finally:
        server.shutdown();server.server_close();thread.join(10);report['wallet']=wallet.audit()
        (directory/'receipt.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    return dict(directory=directory.relative_to(fresh.ROOT).as_posix(),**report)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',action='store_true',required=True)
    parser.add_argument('--repair-overflow',action='store_true');args=parser.parse_args()
    if args.repair_overflow:
        source=fresh.ROOT/'docs/testing/fixtures/deepseek-multifile-2026-10-29/Price.java.txt'
        FILES={'Price.java':source.read_bytes()}
        REQUIREMENTS=('''Fix only Price.java. Preserve its API and valid behavior. The independent isolated oracle
found Price.cents("1e2147483647") throws java.lang.ArithmeticException: Overflow at BigDecimal.movePointRight.
All invalid, fractional-cent, nonnegative long-cent overflow or extreme exponent inputs must be rejected with
IllegalArgumentException, without allocating huge integers or unbounded exponent expansions. Catch/convert
arithmetic failures and reject out-of-range values BEFORE conversion/expansion. Only JDK classes, no IO/env/process.
Read requirements.txt once then write_file Price.java overwrite=true using the SHA256 below, then finish.
SHA256='''+fresh.digest(FILES['Price.java'])+'\n'+FILES['Price.java'].decode()).encode()
    print(json.dumps(run(base.Wallet(fresh.DIRECTORY)),indent=2))
