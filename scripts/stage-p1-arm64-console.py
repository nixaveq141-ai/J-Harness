"""Stage only the independently audited ARM64 Console pair and native JVM proof."""
import importlib.util,json
from pathlib import Path
from evidence_io import stream_sha256
ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/windows-arm64-console-components-acceptance.json'
BUILD_RUN='42322393412'
BUILD_COMMIT='ebd89895c41b3570cd5acfe173931894cfc081ef'
PROBE_RUN='42323927902'
PROBE_COMMIT='2a283294e5497f99b013ce636cde497a7af1f5e3'
RESOURCES={'resources/com/pty4j/native/win/aarch64/'+n for n in ['OpenConsole.exe','conpty.dll']}
COMMANDS={'java','jar-build','compile','present-probe','missing-jni-probe','missing-conpty-probe'}

def load_components(directory,acceptance=None):
    a=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (a['status']!='ARM64_CONSOLE_TWO_SOURCE_JVM_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or a['buildRunId']!=BUILD_RUN or a['buildCommit']!=BUILD_COMMIT or a['probeRunId']!=PROBE_RUN or a['probeCommit']!=PROBE_COMMIT
            or set(a['outputs'])!=RESOURCES or a['productAccepted'] is not False or a['paidRequests']!=0):raise ValueError('Exact ARM64 Console pair acceptance required')
    source=directory/'source';base=directory/'probe/p1-arm64-console-probe'
    def verified(path,digest):
        if path.is_symlink() or not path.is_file() or stream_sha256(path)!=digest:raise ValueError('ARM64 Console accepted bytes changed: '+path.name)
        return path
    build=json.loads(verified(source/'report.json',a['buildReportSha256']).read_bytes())
    if (build['status']!='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE' or build['runId']!=BUILD_RUN or build['commit']!=BUILD_COMMIT
            or build['targetArchitecture']!='arm64' or len(build['commands'])!=20):raise ValueError('Wrong ARM64 Console build identity')
    for c in build['commands']:
        if c['exitCode']!=0 or '/' in c['log'] or '\\' in c['log']:raise ValueError('Failed Console build command')
        verified(source/c['log'],c['logSha256'])
    r=json.loads(verified(base/'report.json',a['probeReportSha256']).read_bytes())
    if (r['status']!='ARM64_CONPTY_JAVA_SOURCE_COMPONENT_PASS_NOT_FULL_PRODUCT_ACCEPTANCE' or r['runId']!=PROBE_RUN or r['commit']!=PROBE_COMMIT
            or r['sourceReportSha256']!=a['buildReportSha256'] or r['nativeInputs']!=a['nativeInputs'] or r['productAccepted'] is not False):raise ValueError('Wrong ARM64 Console JVM identity')
    for name,h in a['nativeInputs'].items():verified(base/'native'/name,h)
    for name,h in a['classSha256'].items():verified(base/name,h)
    if r['probeSources']!=a['probeSources']:raise ValueError('Console JVM probe source drift')
    seen=set();inputs=None
    for row in r['commands']:
        name=row['name']
        if name in seen or name not in COMMANDS or row['sha256']!=a['commandReceiptSha256'][name]:raise ValueError('Exact Console JVM commands required')
        seen.add(name);c=json.loads(verified(base/(name+'-command.json'),row['sha256']).read_bytes());verified(base/(name+'.log'),c['logSha256'])
        if c['status']!='PASS' or c['exitCode']!=0 or c['runId']!='p1-arm64-console-'+PROBE_RUN or c['name']!=name or c['sourceSha256']!=c['sourceAfterSha256']:raise ValueError('Failed Console JVM command')
        if inputs is None:inputs=c['sourceSha256']
        elif inputs!=c['sourceSha256']:raise ValueError('Mixed Console JVM command sources')
    if seen!=COMMANDS:raise ValueError('Incomplete Console JVM commands')
    spec=importlib.util.spec_from_file_location('console_probe',ROOT/'scripts/probe-p1-windows-arm64-console.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    paths=m.load_console(source);result={}
    for resource,item in a['outputs'].items():
        path=verified(paths[resource.rsplit('/',1)[-1]],item['sha256'])
        result[resource]=dict(path=path,sha256=item['sha256'],origin=dict(sourceBuildRun=BUILD_RUN,sourceBuildCommit=BUILD_COMMIT,
            sourceRevision=a['sourceRevision'],vcpkgRevision=a['vcpkgRevision'],componentProbeRun=PROBE_RUN,componentProbeCommit=PROBE_COMMIT,
            sourceReportSha256=a['buildReportSha256'],componentReportSha256=a['probeReportSha256'],acceptanceReceipt=RECEIPT,
            scope='Actual ARM64 JVM ConPTY source-component cwd/process-list/resize/exit and missing-library controls; full application on ARM64 not certified'))
    return result
