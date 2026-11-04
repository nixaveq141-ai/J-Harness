"""Snapshot Canonical USN evaluation; findings/unknowns never imply clearance."""
import bz2
import argparse
import collections
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import inputs

ROOT=Path(__file__).resolve().parents[1]
URL='https://security-metadata.canonical.com/oval/com.ubuntu.noble.usn.oval.xml.bz2'
NS='{http://oval.mitre.org/XMLSchema/oval-results-5}'


def summarize(raw,classes):
    if not raw or len(raw)>128*1024*1024 or b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
        raise ValueError('Unsafe OVAL results')
    text=raw.decode('utf-8-sig')
    if '\x00' in text:raise ValueError('UTF-8 OVAL results required')
    document=ET.fromstring(text)
    systems=document.findall('.//'+NS+'results/'+NS+'system')
    if len(systems)!=1:raise ValueError('One actual scanned host required')
    rows=systems[0].findall(NS+'definitions/'+NS+'definition')
    ids=[v.get('definition_id') for v in rows]
    if not ids or any(not v for v in ids) or len(ids)!=len(set(ids)):
        raise ValueError('Nonempty unique evaluated definitions required')
    if set(ids)!=set(classes):raise ValueError('Complete feed definition coverage required')
    assessed=[v for v in rows if classes[v.get('definition_id')] in {'patch','vulnerability'}]
    if not assessed:raise ValueError('Patch/vulnerability definitions required')
    counts=collections.Counter(v.get('result') for v in rows)
    if set(counts)-{'true','false','unknown','error','not evaluated','not applicable'}:
        raise ValueError('Unrecognized OVAL result')
    assessment_counts=collections.Counter(v.get('result') for v in assessed)
    status=('INCOMPLETE' if any(counts[k] for k in ['unknown','error','not evaluated'])
            else 'FINDINGS' if assessment_counts['true'] else 'NO_USN_FINDINGS_IN_SELECTED_HOST_SCOPE')
    return dict(status=status,counts=dict(counts),assessmentCounts=dict(assessment_counts),definitions=len(rows),
                affectedDefinitions=[v.get('definition_id') for v in assessed if v.get('result')=='true'])


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated disposable CI host required')
    parser=argparse.ArgumentParser();parser.add_argument('--phase',choices=['before','after'],default='before');args=parser.parse_args()
    out=ROOT/'.build-cache/ubuntu-oval'/args.phase;out.mkdir(parents=True)
    report=dict(status='INCOMPLETE',commit=os.environ['GITHUB_SHA'],runId=os.environ['GITHUB_RUN_ID'],
        capturedAt=dt.datetime.now(dt.timezone.utc).isoformat(),commands=[],sourceUrl=URL,
        hostImage={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH']},
        fullOsCpeClearance=False,paidRequests=0,sourceBeforeSha256=inputs(ROOT))
    def command(argv,name):
        log=out/(name+'.log')
        with log.open('xb') as stream:
            result=subprocess.run(argv,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,timeout=600)
        report['commands'].append(dict(argv=argv,exitCode=result.returncode,log=name+'.log',logSha256=stream_sha256(log)))
        if result.returncode:raise ValueError('OVAL command failed: '+name)
    atomic_write_json(out/'report.json',report)
    try:
        release=Path('/etc/os-release').read_text()
        if 'ID=ubuntu' not in release or 'VERSION_ID="24.04"' not in release:raise ValueError('Ubuntu 24.04 required')
        (out/'os-release').write_text(release)
        command(['dpkg-query','-W','-f=${binary:Package}\t${Version}\t${Architecture}\n'],'packages')
        command(['oscap','--version'],'scanner-version')
        if args.phase=='after':
            with (out.parent/'before/feed.xml.bz2').open('rb') as stream:data=stream.read(16*1024*1024+1)
            report['feedPolicy']='SAME_FROZEN_FEED_AS_BEFORE'
        else:
            with urllib.request.urlopen(URL,timeout=45) as response:data=response.read(16*1024*1024+1)
            report['feedPolicy']='OFFICIAL_HTTPS_SNAPSHOT'
        if len(data)>16*1024*1024:raise ValueError('Compressed feed bound')
        (out/'feed.xml.bz2').write_bytes(data)
        decoder=bz2.BZ2Decompressor();xml=decoder.decompress(data,max_length=128*1024*1024+1)
        if len(xml)>128*1024*1024 or not decoder.eof or decoder.unused_data:raise ValueError('Feed expansion bound')
        (out/'feed.xml').write_bytes(xml)
        command(['oscap','oval','eval','--results',str(out/'results.xml'),'--report',str(out/'results.html'),str(out/'feed.xml')],'evaluation')
        with (out/'results.xml').open('rb') as stream:raw=stream.read(128*1024*1024+1)
        feed=ET.fromstring(xml)
        definitions=feed.findall('.//{http://oval.mitre.org/XMLSchema/oval-definitions-5}definition')
        classes={v.get('id'):v.get('class') for v in definitions}
        if len(classes)!=len(definitions):raise ValueError('Duplicate feed definitions')
        report.update(summarize(raw,classes))
    finally:
        report['sourceAfterSha256']=inputs(ROOT)
        if report['sourceBeforeSha256']!=report['sourceAfterSha256']:report['status']='INCOMPLETE'
        report['fileSha256']={p.name:stream_sha256(p) for p in out.iterdir() if p.is_file() and p.name!='report.json'}
        atomic_write_json(out/'report.json',report)
    print(report['status'])


if __name__=='__main__':main()
