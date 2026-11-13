"""Stage exact SPARC bytes from the independently verified original ZIP."""
import hashlib
import io
import json
from pathlib import Path
import struct
import tarfile
import zipfile
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/sparc-cross-jna-component-acceptance.json'
RUN='43348439700'
COMMIT='392872a97434b548de3e40043ec26629513c68e4'
ARTIFACT_SHA='2b80fd66e32b372795b1525ddac89ce792b8ad3ef211d8d65d05fd3b37bd8107'


def load_components(directory,acceptance=None):
    accepted=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (accepted['status']!='TWO_SPARC_JNA_SOURCE_ABI_FFI_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or accepted['runId']!=RUN or accepted['commit']!=COMMIT or accepted['paidRequests']!=0
            or len(accepted['platforms'])!=2 or {p['target'] for p in accepted['platforms']}!={'sparc','sparcv9'}
            or accepted['artifact']['sha256']!=ARTIFACT_SHA or accepted['artifact']['id']!=14003640994):
        raise ValueError('Exact two-target SPARC acceptance required')
    path=directory/'14003640994.zip'
    if path.is_symlink() or not path.is_file() or stream_sha256(path)!=ARTIFACT_SHA:
        raise ValueError('Exact original SPARC artifact required')
    # The complete original ZIP digest pins all 49 already audited receipts,
    # source snapshots and logs; staging does not repeat the historical build.
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist())!=len(set(archive.namelist())):raise ValueError('Duplicate SPARC artifact entries')
        for name,key in [('p1-sparc-jna-cross/report.json','buildReportSha256'),
                         ('p1-sparc-toolchain/report.json','hostReportSha256'),
                         ('p1-sparc-toolchain/guest-component-results.json','guestResultsSha256')]:
            if hashlib.sha256(archive.read(name)).hexdigest()!=accepted[key]:raise ValueError('Accepted SPARC report drift')
        raw=archive.read('p1-sparc-jna-cross/guest-bundle.tgz')
        if hashlib.sha256(raw).hexdigest()!=accepted['bundleSha256']:raise ValueError('SPARC bundle drift')
    files={}
    with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
        for member in archive.getmembers():
            if (not member.isfile() or member.name in files or Path(member.name).name!=member.name
                    or member.size>4*1024**2):raise ValueError('Flat regular SPARC bundle required')
            files[member.name]=archive.extractfile(member).read()
    result={}
    for item in accepted['platforms']:
        target=item['target'];bits=32 if target=='sparc' else 64
        resource='com/sun/jna/sunos-'+target+'/libjnidispatch.so'
        raw=files[str(bits)+'-libjnidispatch.so']
        if resource!=item['resource'] or hashlib.sha256(raw).hexdigest()!=item['outputSha256']:
            raise ValueError('SPARC output scope or bytes changed')
        if (raw[:6]!=b'\x7fELF'+bytes([1 if bits==32 else 2,2])
                or struct.unpack_from('>HH',raw,16) not in ({(3,2),(3,18)} if bits==32 else {(3,43)})):
            raise ValueError('SPARC output ABI changed')
        dest=directory/('verified-'+target+'.so')
        if dest.is_symlink():raise ValueError('Regular owned SPARC staging output required')
        if dest.exists():
            if stream_sha256(dest)!=item['outputSha256']:raise ValueError('Staged SPARC output drift')
        else:
            with dest.open('xb') as output:output.write(raw)
        result[resource]=dict(path=dest,sha256=item['outputSha256'],origin=dict(
            sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=accepted['sourceRevision'],
            componentProbeRun=RUN,componentReportSha256=accepted['buildReportSha256'],
            guestResultsSha256=accepted['guestResultsSha256'],toolchainArchiveSha256=accepted['toolchainArchiveSha256'],
            libffiReleaseSha256=item['libffiReleaseSha256'],
            acceptanceReceipt=RECEIPT,scope='Actual SunOS guest C JNI primitives and ffi/closure; complete JVM/AWT not certified'))
    return result
