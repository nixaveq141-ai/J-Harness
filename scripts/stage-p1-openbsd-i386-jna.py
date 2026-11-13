"""Stage the exact independently reviewed native OpenBSD i386 component."""
import hashlib
import io
import json
from pathlib import Path
import struct
import tarfile
import zipfile
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/openbsd-i386-jna-component-acceptance.json'
RUN='43351632149'
COMMIT='5791e864417f40fe77e0085bc036712bd39aa014'
SHA='6a3ea0a2e282d119ade6400bdf42984f1181e02c71a26fe3d797e147fabb5417'
RESOURCE='com/sun/jna/openbsd-x86/libjnidispatch.so'


def load_components(directory,acceptance=None):
    a=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (a['status']!='ONE_OPENBSD_JNA_SOURCE_ABI_FFI_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or a['runId']!=RUN or a['commit']!=COMMIT or a['paidRequests']!=0 or len(a['platforms'])!=1):
        raise ValueError('Exact OpenBSD i386 acceptance required')
    item=a['platforms'][0]
    if item['target']!='x86' or item['resource']!=RESOURCE or item['artifactSha256']!=SHA or item['artifactId']!='14005682398':
        raise ValueError('Exact OpenBSD i386 resource scope required')
    archive=directory/'14005682398.zip'
    if archive.is_symlink() or not archive.is_file() or stream_sha256(archive)!=SHA:
        raise ValueError('Exact original OpenBSD i386 archive required')
    # This complete original digest pins the independently audited source,
    # installation, compiler mode and all native execution receipts and logs.
    with zipfile.ZipFile(archive) as z:
        if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate original entries')
        outer=json.loads(z.read('report.json'));raw=z.read('component.tgz')
        if (outer['runId']!=RUN or outer['commit']!=COMMIT or not outer['installCompleted']
                or outer['componentArchiveSha256']!=hashlib.sha256(raw).hexdigest()):raise ValueError('OpenBSD native VM identity drift')
    files={}
    with tarfile.open(fileobj=io.BytesIO(raw)) as t:
        for member in t.getmembers():
            if member.isdir():continue
            if not member.isfile() or member.name in files or Path(member.name).name!=member.name or member.size>128*1024**2:
                raise ValueError('Flat regular component files required')
            files[member.name]=t.extractfile(member).read()
    for name,expected in [('report.json',item['reportSha256']),('libjnidispatch.so',item['outputSha256']),
                          ('original.so',item['originalSha256']),('jni-probe',item['probeSha256']),
                          ('libffi-release-3.4.4.tar.gz',item['libffiReleaseSha256'])]:
        if hashlib.sha256(files[name]).hexdigest()!=expected:raise ValueError('Accepted component bytes drift')
    raw=files['libjnidispatch.so']
    if raw[:6]!=b'\x7fELF\x01\x01' or struct.unpack_from('<HH',raw,16)!=(3,3):raise ValueError('Native i386 ELF required')
    dest=directory/'verified-openbsd-i386.so'
    if dest.is_symlink():raise ValueError('Regular owned staging output required')
    if dest.exists():
        if stream_sha256(dest)!=item['outputSha256']:raise ValueError('Staged OpenBSD output drift')
    else:
        with dest.open('xb') as out:out.write(raw)
    return {RESOURCE:dict(path=dest,sha256=item['outputSha256'],origin=dict(sourceBuildRun=RUN,
        sourceBuildCommit=COMMIT,sourceRevision=a['sourceRevision'],componentProbeRun=RUN,
        componentReportSha256=item['reportSha256'],libffiReleaseSha256=item['libffiReleaseSha256'],
        acceptanceReceipt=RECEIPT,scope='Native OpenBSD i386 C JNI primitives and real ffi/closure; full Java product qualification pending'))}
