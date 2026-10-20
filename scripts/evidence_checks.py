"""Independent case identities and strict result validation for current evidence."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

CONTRACT = json.loads(Path(__file__).with_name('contracts').joinpath('m1059-component-checks.json').read_bytes())


def require(ok, message):
    if not ok:
        raise ValueError(message)


def names(actual, expected, message):
    require(isinstance(actual, list) and bool(actual) and all(isinstance(x, str) and x.strip() for x in actual)
            and len(actual) == len(set(actual)) and set(actual) == set(expected), message)


def passed_checks(actual, expected, message):
    require(isinstance(actual, list) and all(isinstance(c, dict) and c.get('passed') is True for c in actual), message)
    names([c.get('name') for c in actual], expected, message)


def sdk_checks(sdk, examples):
    results = sdk.get('results')
    require(isinstance(results, list) and len(results) == 2 and all(isinstance(r, dict) for r in results), 'Missing SDK languages')
    names([r.get('language') for r in results], CONTRACT['sdk'], 'Missing SDK languages')
    for r in results:
        require(r.get('passed') is True, 'Failed SDK result')
        names(r.get('checks'), CONTRACT['sdk'][r['language']], 'Missing SDK cases')
    require(sdk.get('allAuthorized') is True and sdk.get('modelContextRedacted') is True
            and type(sdk.get('requests')) is int and sdk['requests'] > 0, 'Missing SDK traffic facts')
    passed_checks(examples.get('checks'), CONTRACT['examples'], 'Missing example cases')
    require(all(isinstance(c.get('factsSha256'), str) and re.fullmatch('[0-9a-f]{64}', c['factsSha256']) for c in examples['checks']), 'Missing example facts')


def native_checks(native):
    require(native.get('expectationMet') is True and native.get('passed') is True
            and type(native.get('exitCode')) is int and native['exitCode'] == 0, 'Native failure')
    checks = native.get('checks')
    require(isinstance(checks, dict) and set(checks) == set(CONTRACT['native'])
            and all(value is True for value in checks.values()), 'Missing native cases')


def suite_snapshot(path, module):
    raw = path.read_bytes()
    require(len(raw) <= 16 * 1024 * 1024 and b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw, 'Unsafe XML report')
    xml = ET.fromstring(raw)
    require(xml.tag == 'testsuite', 'Not a test suite')
    cases = xml.findall('testcase')
    case_names = [c.get('name') for c in cases]
    require(bool(case_names) and all(isinstance(x, str) and x.strip() for x in case_names)
            and len(set(case_names)) == len(case_names), 'Empty or duplicate Java cases')
    actual = dict(tests=len(cases), failures=sum(bool(c.findall('failure')) for c in cases),
                  errors=sum(bool(c.findall('error')) for c in cases), skipped=sum(bool(c.findall('skipped')) for c in cases))
    require(all(re.fullmatch('[0-9]+', xml.get(k, '')) and int(xml.get(k)) == v for k, v in actual.items()), 'XML case/counter mismatch')
    require(isinstance(xml.get('name'), str) and bool(xml.get('name').strip()), 'Missing suite identity')
    return dict(module=module, name=xml.get('name'), cases=sorted(case_names), sha256=hashlib.sha256(raw).hexdigest(), **actual)
