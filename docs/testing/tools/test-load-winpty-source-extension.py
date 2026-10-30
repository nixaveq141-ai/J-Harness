"""Reject replayed source reports, drifted sources and escaped native paths."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('loader',Path(__file__).with_name('load-winpty-source-extension.py'))
loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)


class SourceLoaderTest(unittest.TestCase):
    def test_current_source_and_candidate_identity_are_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(loader,'ROOT',Path(temp)):
            root=Path(temp);out=root/'.build-cache/windows-pty-source';out.mkdir(parents=True)
            extension=loader.module('patch_controls','patch-winpty-directory.py')
            helper=root/'docs/testing/native/winpty-owned-directory.h';helper.parent.mkdir(parents=True);helper.write_bytes(b'controlled helper fixture')
            changes=[]
            for name,before in extension.EXPECTED.items():
                path=out/'winpty'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'controlled patched source')
                changes.append(dict(path=name,beforeSha256=before,afterSha256=loader.sha(path)))
            actual=out/'winpty/src/agent/jh-owned-directory.h';actual.write_bytes(helper.read_bytes())
            log=out/'1.log';log.write_bytes(b'controlled successful compile log')
            outputs=[]
            for name in ['winpty.dll','winpty-agent.exe','win-helper.dll']:
                path=out/name;path.write_bytes(b'MZcontrolled native fixture')
                outputs.append(dict(path=path.relative_to(root).as_posix(),sha256=loader.sha(path)))
            report=dict(status='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE',commit='current',runId='owned-run',
                sources=dict(winpty=dict(revision=extension.REVISION,directoryExtension=dict(baseRevision=extension.REVISION,
                    ownedChildHelperSha256=loader.sha(helper),changes=changes))),
                winptyEligibility=dict(status='ABI_RUNTIME_VALIDATION_REQUIRED'),outputs=outputs,
                commands=[dict(argv=['C:/msys64/usr/bin/make.exe','-j2','build/winpty.dll','build/winpty-agent.exe'],exitCode=0,log='1.log',logSha256=loader.sha(log))])
            file=out/'report.json';file.write_text(json.dumps(report),encoding='utf-8')
            self.assertEqual(3,len(loader.validate_source(out,'current','owned-run')))
            for mutate in [lambda r:r.update(commit='old'),lambda r:r.update(runId='other'),
                lambda r:r['sources']['winpty']['directoryExtension']['changes'].pop(),
                lambda r:r['sources']['winpty']['directoryExtension']['changes'][0].update(afterSha256='0'*64),
                lambda r:r['outputs'][0].update(path='../escape.dll'),lambda r:r['commands'][0].update(exitCode=1),
                lambda r:r.update(commands=[])]:
                bad=copy.deepcopy(report);mutate(bad);file.write_text(json.dumps(bad),encoding='utf-8')
                with self.assertRaises((ValueError,FileNotFoundError)):loader.validate_source(out,'current','owned-run')


if __name__=='__main__':unittest.main()
