"""Stage only exact independently reviewed two-target FreeBSD JNA component bytes."""
import importlib.util
import json
from pathlib import Path
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/freebsd-jna-components-acceptance.json'
RUN='42150888437'
COMMIT='ca7586024dd6200ce27ca30c8e490377f095426f'
TARGETS={'x86','x86-64'}


def load_components(directory,acceptance=None):
    accepted=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (accepted['status']!='TWO_FREEBSD_JNA_SOURCE_ABI_FFI_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or accepted['runId']!=RUN or accepted['commit']!=COMMIT or accepted['paidRequests']!=0
            or len(accepted['platforms'])!=2 or {r['target'] for r in accepted['platforms']}!=TARGETS):
        raise ValueError('Exact two-component acceptance required')
    spec=importlib.util.spec_from_file_location('jna_builder',ROOT/'scripts/build-p1-freebsd-jna.py')
    builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
    result={}
    for item in accepted['platforms']:
        target=item['target'];base=directory/('p1-freebsd-'+target+'-jna-source')
        for name in ['report.json','libjnidispatch.so','original.so','jni-probe','libffi-release-3.4.4.tar.gz']:
            if (base/name).is_symlink() or not (base/name).is_file():raise ValueError('Regular accepted JNA files required')
        if stream_sha256(base/'report.json')!=item['reportSha256']:raise ValueError('JNA report drift')
        report=json.loads((base/'report.json').read_bytes())
        if (report['status']!='SOURCE_JNA_ABI_AND_FFI_PROBE_PASS_NOT_PRODUCT_ACCEPTANCE'
                or report['runId']!=RUN or report['commit']!=COMMIT or report['target']!=target
                or report['sourceRevision']!=builder.REVISION or report['productAccepted'] is not False):
            raise ValueError('Wrong JNA source component identity')
        output=base/'libjnidispatch.so'
        if stream_sha256(output)!=item['outputSha256'] or report['outputSha256']!=item['outputSha256']:
            raise ValueError('Accepted JNA bytes changed')
        if (stream_sha256(base/'original.so')!=item['originalSha256']
                or stream_sha256(base/'jni-probe')!=item['probeSha256']
                or stream_sha256(base/'libffi-release-3.4.4.tar.gz')!=item['libffiReleaseSha256']
                or report['probeSourceSha256']!=item['probeSourceSha256']
                or report['probeSupportSha256']!=item['probeSupportSha256']
                or report['generatedHelperSha256']!=item['generatedHelperSha256']):
            raise ValueError('Original/probe/generated JNA identity changed')
        if builder.require_elf(output.read_bytes(),target)!=item['elf']:raise ValueError('JNA ELF identity changed')
        seen=set();source=None
        for command in report['commands']:
            name=command['name']
            if name in seen or '/' in name or '\\' in name:raise ValueError('Unique JNA command names required')
            seen.add(name);path=base/(name+'-command.json')
            if stream_sha256(path)!=command['sha256']:raise ValueError('JNA command receipt drift')
            record=json.loads(path.read_bytes())
            if (record['status']!='PASS' or record['exitCode']!=0 or record['name']!=name
                    or record['runId']!='p1-jna-'+RUN+'-'+target
                    or record['sourceSha256']!=record['sourceAfterSha256']
                    or stream_sha256(base/(name+'.log'))!=record['logSha256']):raise ValueError('Failed or changed JNA command')
            if source is None:source=record['sourceSha256']
            elif source!=record['sourceSha256']:raise ValueError('Mixed JNA command sources')
        if seen!={'host-packages','compiler','system','jdk','ant','clone','checkout','build','elf-audit','promotion-build','promotion-control','probe-build','candidate-probe','missing-probe','source-diff'}:
            raise ValueError('Complete JNA command set required')
        resource='com/sun/jna/freebsd-'+target+'/libjnidispatch.so'
        if report['resource']!=resource or item['resource']!=resource:raise ValueError('Exact JNA resource scope required')
        result[resource]=dict(path=output,sha256=item['outputSha256'],origin=dict(
            sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=builder.REVISION,
            componentProbeRun=RUN,componentReportSha256=item['reportSha256'],libffiReleaseSha256=item['libffiReleaseSha256'],
            acceptanceReceipt=RECEIPT,scope='FreeBSD VM C-level JNI primitives and real libffi call/closure; complete target JVM/Java not certified'))
    return result
