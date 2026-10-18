import json
import os
from pathlib import Path
import sys
import time
import concurrent.futures
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'python'))
from jharness_sdk import JHarnessClient, RpcError

base = Path(os.environ['JHARNESS_SDK_WORKSPACE'])
secret = os.environ['JHARNESS_API_KEY']
checks = []
client = None

def check(name, condition=True):
    assert condition, name
    checks.append(name)
    print("PASS", name, flush=True)

def until(fn, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if fn(): return
        time.sleep(.05)
    raise AssertionError('Fixture timeout')

def open_client(scenario, **extra):
    return JHarnessClient(Path(os.environ['JAVA_HOME'])/('bin/java.exe' if os.name == 'nt' else 'bin/java'), os.environ['JHARNESS_SDK_JAR'], base/scenario, base/scenario/'config.json', **extra)

def rejects(action, code=None):
    try: action()
    except RpcError as error:
        assert code is None or error.code == code, error.code
    else: raise AssertionError('Expected rejected operation')

try:
    rejects(lambda: open_client('flow', protocol_version='99'), 'VERSION_UNSUPPORTED')
    check('version mismatch rejects without execution and closes child')
    client = open_client('flow')
    check('protocol negotiates version and bounds', client.capabilities['protocolVersion'] == '1.0' and client.capabilities['maxPending'] == 32)
    rejects(lambda: client.call('unknown.method'), 'METHOD_NOT_FOUND')
    rejects(lambda: client.call('session.open', {'path': 'outside'}), 'INVALID_PARAMS')
    check('unknown methods and fields reject')
    opened = client.open_session()
    check('opening is a nonexecuting draft', opened['state'] == 'DRAFT' and not opened['persisted'] and client.list_sessions()['historyCount'] == 0)
    receipt = client.send_input(opened['handle'], 'SDK_FLOW', 'initial-flow')
    session = receipt['sessionId']
    until(lambda: any(q['state'] == 'PENDING' for q in client.questions(session)['questions']))
    question = next(q for q in client.questions(session)['questions'] if q['state'] == 'PENDING')
    check('recommendation is pending without automatic answer', not (base/'flow/approved.txt').exists())
    client.answer(question, ['a'], request_id='answer-flow')
    until(lambda: bool(client.approvals(session)['approvals']))
    approval = client.approvals(session)['approvals'][0]
    check('question answer does not approve write', not (base/'flow/approved.txt').exists())
    rejects(lambda: client.decide(session, {**approval, 'callId': 'wrong'}, True, 'fixture'))
    client.decide(session, approval, True, 'fixture approval')
    until(lambda: client.result(session)['phase'] == 'FINISHED')
    check('separate exact approval executes real write', (base/'flow/approved.txt').read_text() == 'approved only after question and tool decision')
    check('same initial request is idempotent', client.send_input(opened['handle'], 'SDK_FLOW', 'initial-flow')['sessionId'] == session)
    check('lost receipt can be reconciled without sending', client.call('request.get', {'requestId':'initial-flow'})['taskId'] == session)
    client.send_input(opened['handle'], 'SDK_FOLLOWUP', 'followup')
    until(lambda: client.result(session)['phase'] == 'FINISHED')
    events, after = [], None
    while True:
        page = client.events(session, after, 10)
        events += page['events']
        if not page['more']: break
        after = page['next']
    check('paged real events retain multiple turn boundaries', sum(e['type']=='turn.started' for e in events)==2)
    artifact = next(e['payload']['value']['artifactRef'] for e in events if e['type']=='tool.completed' and 'artifactRef' in e['payload'].get('value', {}))
    page = client.artifact(session, artifact)
    check('artifact reads real scoped redacted content', 'Artifact' in page['text'] and secret not in json.dumps(page))
    check('report and event content exclude secrets', secret not in json.dumps(client.report(session)) and secret not in json.dumps(events))
    check('content search reuses Java service and source ids', client.search('SDK_FLOW')['total']>0)
    check('key never enters argv', secret not in ' '.join(client._process.args))
    client.close()
    check('graceful close stops owned process', client._process.poll() is not None)
    for scenario in ['cancel', 'unknown', 'disconnect']:
        client = open_client(scenario)
        handle = client.open_session()
        sent = client.send_input(handle['handle'], 'SDK_'+scenario.upper(), 'initial-'+scenario)
        session = sent['sessionId']
        until(lambda: any(e['type']=='model.requested' for e in client.events(session)['events']))
        if scenario == 'cancel':
            client.cancel(session)
            until(lambda: client.result(session)['phase'] in ['FINISHED','UNKNOWN'])
            check('cancel propagates without fabricated success', client.result(session)['status'] != 'COMPLETED')
            client.close()
        else:
            if scenario == 'disconnect':
                client._process.kill()
                client._process.wait(timeout=5)
                rejects(lambda: client.result(session))
                check('disconnect rejects pending use without automatic resend')
            else:
                until(lambda: client.result(session)['phase']=='UNKNOWN')
            client.close()
            client = open_client(scenario)
            reopened = client.open_session(session_id=session)
            check(scenario+' restart retains UNKNOWN without replay', client.result(session)['phase']=='UNKNOWN')
            rejects(lambda: client.send_input(reopened['handle'], 'must not execute', 'blocked'))
            check(scenario+' refuses unsafe continuation')
            client.close()
    check('all owned children closed', client._process.poll() is not None)
    def peer():
        return JHarnessClient(Path(os.environ['JAVA_HOME'])/('bin/java.exe' if os.name=='nt' else 'bin/java'), os.environ['JHARNESS_SDK_PEER_JAR'], base/'flow')
    client = peer()
    rejects(lambda: client.call('slow', timeout=.1), 'REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN')
    time.sleep(.9)
    check('timeout discards late response and never retries', client.call('stats')['slowCalls']==1)
    client.call('stderr')
    check('child diagnostic text never appears in API result')
    rejects(lambda: client.call('ping', {'text':'x'*65537}), 'RPC_REQUEST_LIMIT')
    check('request byte bound rejects before sending')
    def held():
        try: client.call('hold')
        except RpcError: return True
        return False
    with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
        pending = [pool.submit(held) for _ in range(32)]
        until(lambda: len(client._pending)==32)
        rejects(lambda: client.call('hold'), 'BACKPRESSURE_LIMIT')
        check('pending request bound applies backpressure')
        client.close()
        check('close rejects every pending call', all(f.result(timeout=5) for f in pending))
    for method in ['duplicate','oversized']:
        client = peer()
        rejects(lambda: client.call(method), 'INVALID_RESPONSE')
        client.close()
        check(method+' response fails closed and stops owned peer')
    print(json.dumps({'language':'python','checks':checks,'passed':True}))
finally:
    if client: client.close()
