"""Reject stale successes, mixed commits and ambiguous retry identities."""
import copy
import importlib.util
from pathlib import Path
import unittest

s=importlib.util.spec_from_file_location('retry',Path(__file__).with_name('check-p1-targeted-retry.py'))
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)


def records():
    run=dict(id=m.RUN,head_sha=m.COMMIT,head_branch='codex/m1059-full-validation',status='completed',
        path='.github/workflows/gap-unified-source-qualification.yml',repository=dict(full_name=m.REPO),run_attempt=2)
    jobs=[dict(id=i,run_id=m.RUN,head_sha=m.COMMIT,run_attempt=1,name='qualification ('+label+')',
        status='completed',conclusion='success') for i,label in enumerate(m.LABELS,1)]
    jobs.append({**jobs[0],'id':100,'run_attempt':2})
    return run,jobs


class RetryIdentity(unittest.TestCase):
    def test_preserve_four_successes_and_require_latest_windows(self):
        run,jobs=records();chosen=m.selected_jobs(run,jobs)
        self.assertEqual(100,chosen['windows-2022']['id'])
        self.assertEqual([1,1,1,1],[chosen[label]['run_attempt'] for label in m.LABELS[1:]])
        for status,conclusion in [('completed','failure'),('in_progress',None),('completed','cancelled')]:
            with self.subTest(status=status,conclusion=conclusion):
                bad=copy.deepcopy(jobs);bad[-1].update(status=status,conclusion=conclusion)
                with self.assertRaisesRegex(ValueError,'Latest'):m.selected_jobs(run,bad)

    def test_reject_run_commit_and_attempt_substitution(self):
        run,jobs=records()
        for key,value in [('run_id',1),('head_sha','other'),('run_attempt',3),('run_attempt',True)]:
            with self.subTest(key=key,value=value):
                bad=copy.deepcopy(jobs);bad[-1][key]=value
                with self.assertRaises(ValueError):m.selected_jobs(run,bad)
        wrong={**run,'head_sha':'other'}
        with self.assertRaises(ValueError):m.selected_jobs(wrong,jobs)

    def test_reject_missing_duplicate_and_unknown_target(self):
        run,jobs=records()
        for bad in [jobs[:1]+jobs[2:],jobs+[jobs[0]],jobs+[{**jobs[-1],'id':101}],
                    jobs+[{**jobs[-1],'id':102,'name':'qualification (other)'}]]:
            with self.assertRaises(ValueError):m.selected_jobs(run,bad)


if __name__=='__main__':unittest.main()
