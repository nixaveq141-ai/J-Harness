"""Fetch immutable reviewed SPARC/OpenBSD originals for batch staging."""
import argparse
import os
from pathlib import Path
import subprocess
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
SHA256='2b80fd66e32b372795b1525ddac89ce792b8ad3ef211d8d65d05fd3b37bd8107'
BYTES=5616563
INPUTS={'sparc':('14003640994','p1-sparc-jna-inputs',BYTES,SHA256),
        'openbsd-i386':('14005682398','p1-openbsd-i386-inputs',3535133,'6a3ea0a2e282d119ade6400bdf42984f1181e02c71a26fe3d797e147fabb5417')}


def verify(path,kind='sparc'):
    _,_,size,digest=INPUTS[kind]
    if path.is_symlink() or not path.is_file() or path.stat().st_size!=size or stream_sha256(path)!=digest:
        raise ValueError('Exact independently reviewed original archive required')


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated batch CI required')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('kind',choices=INPUTS,nargs='?',default='sparc');kind=parser.parse_args().kind
    artifact,folder,_,_=INPUTS[kind]
    directory=ROOT/'.build-cache'/folder
    directory.mkdir(parents=True,exist_ok=True)
    destination=directory/(artifact+'.zip')
    if destination.exists():
        verify(destination,kind)
        print('Reused exact reviewed archive:',kind)
        return
    partial=directory/(artifact+'.zip.part')
    # GH_TOKEN stays in the runner environment, never command arguments or logs.
    # Retain incomplete downloads on failure without presenting them as inputs.
    with partial.open('xb') as output:
        subprocess.run(['gh','api','repos/nixaveq141-ai/J-Harness/actions/artifacts/'+artifact+'/zip'],
                       stdout=output,check=True,timeout=180)
    verify(partial,kind)
    if destination.exists():raise ValueError('Owned artifact destination changed')
    partial.rename(destination)
    print('Verified exact reviewed archive:',kind)


if __name__=='__main__':main()
