import os
import json
from pathlib import Path
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'python'))
from jharness_sdk import JHarnessClient

for required in ['JAVA_HOME','JHARNESS_SDK_JAR','JHARNESS_SDK_WORKSPACE']:
    if not os.environ.get(required):raise SystemExit('Set '+required+' before running the SDK example')
java = Path(os.environ['JAVA_HOME']) / ('bin/java.exe' if os.name == 'nt' else 'bin/java')
with JHarnessClient(java, os.environ['JHARNESS_SDK_JAR'], os.environ['JHARNESS_SDK_WORKSPACE'], Path(__file__).with_name('demo.json')) as client:
    opened = client.open_session()
    # Keep this ID if a response is lost; call request.get before deciding to submit again.
    receipt = client.send_input(opened['handle'], 'Inspect README.md', 'python-demo-input')
    session = receipt['sessionId']
    end = time.monotonic() + 35
    state=client.result(session)
    while state['phase'] not in ['FINISHED','VERIFIED','UNKNOWN'] and time.monotonic()<end:
        time.sleep(.1);state=client.result(session)
    if state['phase']=='UNKNOWN':print('Outcome unknown: retain python-demo-input/sessionId and use client.call(\"request.get\", params) before any resubmission')
    elif state['phase'] not in ['FINISHED','VERIFIED']:raise SystemExit('Example did not finish within its budget; inspect the existing session')
    # ASCII JSON also works with redirected Windows consoles using legacy code pages.
    print(json.dumps(state, ensure_ascii=True))
    print(json.dumps(client.search('README', session_id=session), ensure_ascii=True))
    print(json.dumps(client.report(session), ensure_ascii=True))
