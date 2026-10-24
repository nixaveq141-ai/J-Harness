"""Isolated reproductions for the 2026-10-24 R1-R3 OCR assessment.

Compile selected current sources with JDK 21; no Maven, browser, network,
native credential service, or actual SDK child process is used.
"""
import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import tempfile
import threading
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
JAVA = r'''
package io.jharness.agent;
import io.jharness.api.*;
import io.jharness.session.*;
import java.time.Instant;
import java.util.*;
public class RereviewProbe {
  static void result(String name, boolean ok) {
    if (!ok) throw new AssertionError(name);
    System.out.println(name + ":CONFIRMED");
  }
  static SessionEvent event(String id, String type) {
    return new SessionEvent(id,"s",type,Instant.EPOCH,1,EventLevel.STEP,"t","step",null,Map.of());
  }
  public static void main(String[] ignored) {
    var messages = List.<Map<String,Object>>of(
      Map.of("role","system","content","system"), Map.of("role","user","content","user"),
      Map.of("role","assistant","tool_calls",List.of(Map.of("id","c1"))),
      Map.of("role","tool","tool_call_id","c1","content","result"));
    var request = new ChatRequest(messages,List.of(),Map.of());
    var half = new ChatRequest(List.of(messages.get(0),messages.get(1),messages.get(3)),List.of(),Map.of());
    ContextCompactionPlugin.validateCompaction(request,new ContextCompactor.Compaction(half,List.of(2),""));
    result("009_resolved_tool_group_can_be_split",true);
    ContextCompactionPlugin.validateCompaction(request,new ContextCompactor.Compaction(half,List.of(),""));
    result("011_removal_can_be_omitted_from_audit",true);
    var bad = new ChatRequest(List.of(Map.of("role","assistant","tool_calls",List.of(Map.of("id",7)))),List.of(),Map.of());
    boolean cast = false;
    try { ContextCompactionPlugin.validateCompaction(bad,new ContextCompactor.Compaction(bad,List.of(),"")); }
    catch (ClassCastException expected) { cast = true; }
    result("010_malformed_call_id_throws_class_cast",cast);
    Object deep = "value";
    for (int i=0;i<63;i++) deep = List.of(deep);
    Map<String,Object> schema = Map.of("type","array");
    for(int i=0;i<2;i++) schema = Map.of("oneOf",List.of(schema));
    new JsonSchema().validate(schema,deep);
    result("018_claimed_deep_value_counterexample_is_accepted",true);
    boolean invalid = false;
    var success = new ToolResult("c",ToolStatus.SUCCESS,Map.of(),"",Map.of(),Optional.empty());
    try { ToolResults.sanitized(success, input -> { var output=new LinkedHashMap<>(input); output.put("error",Map.of("code","X","message","X")); return output; }); }
    catch(IllegalArgumentException expected) { invalid = true; }
    result("027_injected_success_error_loses_domain_code",invalid);
    var events = List.of(event("r1","model.requested"),event("r2","model.requested"),event("a","assistant.message"));
    boolean blocked = false;
    try { SessionRecovery.isRequestPending(events,"r1"); }
    catch(HarnessException expected) { blocked = expected.code().equals("AMBIGUOUS_MODEL_REQUEST"); }
    result("131_exact_request_verification_is_blocked",blocked);
  }
}
'''


def main():
    output = Path(sys.argv[1]).resolve()
    if not output.is_relative_to(ROOT / '.build-cache') or output.exists():
        raise ValueError('Use a fresh output under repository .build-cache')
    java_home = Path(os.environ['JAVA_HOME'])
    suffix = '.exe' if os.name == 'nt' else ''
    sources = list((ROOT / 'harness-api/src/main/java').rglob('*.java'))
    sources += [ROOT / p for p in (
        'harness-agent/src/main/java/io/jharness/agent/ContextCompactionPlugin.java',
        'harness-session/src/main/java/io/jharness/session/SessionRecovery.java',
        'harness-session/src/main/java/io/jharness/session/EventSessionProjection.java',
        'harness-session/src/main/java/io/jharness/session/EventRequestJournal.java')]
    checks = []
    with tempfile.TemporaryDirectory(prefix='r1r3-review-', dir=ROOT / '.build-cache') as tmp:
        base = Path(tmp)
        source = base / 'RereviewProbe.java'
        source.write_text(JAVA, encoding='utf-8')
        classes = base / 'classes'
        classes.mkdir()
        args = base / 'compile.args'
        args.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in [*sources, source]), encoding='utf-8')
        subprocess.run([str(java_home / ('bin/javac' + suffix)), '-encoding', 'UTF-8', '-d', str(classes), '@' + str(args)], cwd=ROOT, check=True, capture_output=True, timeout=60)
        run = subprocess.run([str(java_home / ('bin/java' + suffix)), '-cp', str(classes), 'io.jharness.agent.RereviewProbe'], check=True, capture_output=True, text=True, timeout=30)
        checks.extend(run.stdout.splitlines())
    spec = importlib.util.spec_from_file_location('rereview_sdk', ROOT / 'sdk/python/jharness_sdk.py')
    sdk = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sdk)
    client = sdk.JHarnessClient.__new__(sdk.JHarnessClient)
    client._lock = threading.RLock()
    client._closed = client._closing = False
    client._expired, client._pending = {}, {}
    client._writes = queue.Queue(32)

    class BoundaryFuture(concurrent.futures.Future):
        def result(self, timeout=None):
            # After timeout, a reader may consume and resolve this id before
            # call() reacquires the lock to record its tombstone.
            try:
                return super().result(timeout=0)
            except concurrent.futures.TimeoutError:
                with client._lock:
                    request_id = next(iter(client._pending))
                    client._pending.pop(request_id).set_result({})
                raise

    with patch.object(sdk.concurrent.futures, 'Future', BoundaryFuture):
        try:
            client.call('ping', timeout=.1)
        except sdk.RpcError as failure:
            assert failure.code == 'REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN'
    assert not client._pending and len(client._expired) == 1
    checks.append('174_consumed_reply_leaves_expired_tombstone:CONFIRMED')
    historical = subprocess.check_output(['git', 'show', '5d8ff4e:docs/development/OCR-R1-implementation-2026-10-21.json'], cwd=ROOT)
    report = json.loads((ROOT / 'docs/testing/OCR-R1-2026-10-21-results.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(historical).hexdigest() == report['implementationLedger']['sha256']
    checks.append('005_historical_ledger_hash_exists_at_R1_commit:CONFIRMED')
    payload = dict(checks=checks, checkedHead=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                   probeSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                   sourceSha256={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources + [ROOT/'sdk/python/jharness_sdk.py']},
                   limits='Eight isolated observations; no full product, browser, native credential, Docker or CI acceptance.')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(dict(checks=checks, output=str(output)), ensure_ascii=False))


if __name__ == '__main__':
    main()
