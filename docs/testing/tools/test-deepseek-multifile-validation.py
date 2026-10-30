"""Offline response gate counterexamples; no network/provider requests."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('multi',Path(__file__).with_name('deepseek-multifile-validation.py'))
multi=importlib.util.module_from_spec(spec);spec.loader.exec_module(multi)


class MultiTest(unittest.TestCase):
    def test_only_exact_versioned_synthetic_writes(self):
        args=dict(path='Price.java',content='package example; class Price {}',overwrite=True,
                  expectedVersion=multi.fresh.digest(multi.FILES['Price.java']))
        def response(arguments,name='write_file'):
            return json.dumps(dict(choices=[dict(message=dict(tool_calls=[dict(function=dict(name=name,arguments=json.dumps(arguments)))]))])).encode()
        multi.checked_response(response(args),('provider-secret',))
        for change in [dict(path='../escape.java'),dict(path='Invoice.java'),dict(expectedVersion='wrong'),
                       dict(overwrite=False),dict(content='x'*16385),dict(content='provider-secret')]:
            with self.subTest(change=list(change)),self.assertRaises(ValueError):
                multi.checked_response(response(dict(args,**change)),('provider-secret',))
        with self.assertRaises(ValueError):multi.checked_response(response(args,'execute_command'),())

    def test_original_wallet_identity_and_twelve_attempt_bound(self):
        self.assertEqual(12,multi.base.MAX_ATTEMPTS)
        self.assertEqual(5_000_000,multi.base.LIMIT_MICRO_CNY)
        self.assertEqual(multi.fresh.DIRECTORY.name,'deepseek-fresh-code-2026-10-29')

    def test_request_cannot_enable_other_tools(self):
        request=dict(model='deepseek-flash',max_tokens=2048,stream=False,thinking={'type':'disabled'},messages=[],
                     tools=[dict(type='function',function=dict(name='write_file'))])
        multi.checked_request(json.dumps(request).encode())
        request['tools'][0]['function']['name']='execute_command'
        with self.assertRaises(ValueError):multi.checked_request(json.dumps(request).encode())


if __name__=='__main__':unittest.main()
