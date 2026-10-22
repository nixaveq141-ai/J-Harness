"""Independent case identities and strict result validation for current evidence."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from evidence_io import stream_sha256, strict_json_loads

CONTRACT = json.loads(Path(__file__).with_name('contracts').joinpath('m1059-component-checks.json').read_bytes())


def firefox_executable(root):
    code = "import {firefox} from './frontend/node_modules/playwright/index.mjs';console.log(JSON.stringify(firefox.executablePath()))"
    value = strict_json_loads(subprocess.check_output(['node', '--input-type=module', '-e', code], cwd=root, timeout=15))
    require(isinstance(value, str) and bool(value.strip()), 'Missing Firefox executable identity')
    path = Path(value).resolve(strict=True)
    require(path.is_file(), 'Firefox executable is not a file')
    return path


def firefox_unavailable(record, *, run_id, executable, script_sha256):
    """Only a same-run actual Windows process-start failure supports local partial."""
    require(isinstance(record, dict) and record.get('runId') == run_id, 'Firefox probe from another run')
    require(record.get('status') == 'RECORDED' and record.get('executableUnchanged') is True, 'Incomplete Firefox probe')
    require(record.get('probeScriptSha256') == script_sha256, 'Firefox probe source drift')
    result = record.get('firefox')
    require(isinstance(result, dict) and result.get('status') == 'UNAVAILABLE'
            and result.get('processStartFailed') is True and result.get('exitCode') is None
            and type(result.get('winerror')) is int and result['winerror'] == 14001,
            'Missing explicit Windows Firefox availability evidence')
    require(result.get('executable') == str(executable.resolve(strict=True))
            and result.get('command') == [str(executable.resolve(strict=True)), '--version']
            and result.get('executableSha256') == stream_sha256(executable), 'Firefox executable identity drift')


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


def xml_document_snapshot(path, *, max_bytes=16 * 1024 * 1024):
    """Parse one limited UTF-8/ASCII snapshot; DTDs and entity declarations forbidden."""
    require(type(max_bytes) is int and max_bytes > 0, 'Invalid XML byte budget')
    with path.open('rb') as stream:
        raw = stream.read(max_bytes + 1)
    require(bool(raw) and len(raw) <= max_bytes, 'Unsafe XML report size')
    try:
        text = raw.decode('utf-8-sig')
    except UnicodeDecodeError:
        raise ValueError('Unsafe XML encoding; expected UTF-8') from None
    require('\x00' not in text, 'Unsafe XML encoding')
    declared = re.match(r'<\?xml\s+[^?]*encoding\s*=\s*[\"\']([^\"\']+)[\"\']', text, re.I)
    require(not declared or declared.group(1).lower() in {'utf-8', 'utf8', 'ascii', 'us-ascii'}, 'Unsafe XML encoding')
    require(not re.search(r'<!\s*(?:DOCTYPE|ENTITY)\b', text, re.I), 'Unsafe XML declarations')
    try:
        root = ET.fromstring(text)
    except ET.ParseError:
        raise ValueError('Malformed XML report') from None
    return raw, root


def suite_details(path, module):
    raw, xml = xml_document_snapshot(path)
    require(xml.tag == 'testsuite', 'Not a test suite')
    cases = xml.findall('testcase')
    case_names = [c.get('name') for c in cases]
    require(bool(case_names) and all(isinstance(x, str) and x.strip() for x in case_names)
            and len(set(case_names)) == len(case_names), 'Empty or duplicate Java cases')
    actual = dict(tests=len(cases), failures=sum(bool(c.findall('failure')) for c in cases),
                  errors=sum(bool(c.findall('error')) for c in cases), skipped=sum(bool(c.findall('skipped')) for c in cases))
    require(all(re.fullmatch('[0-9]+', xml.get(k, '')) and int(xml.get(k)) == v for k, v in actual.items()), 'XML case/counter mismatch')
    require(isinstance(xml.get('name'), str) and bool(xml.get('name').strip()), 'Missing suite identity')
    return dict(module=module, name=xml.get('name'), cases=sorted(case_names), sha256=hashlib.sha256(raw).hexdigest(), **actual), xml


def suite_snapshot(path, module):
    return suite_details(path, module)[0]


def validate_java_suites(suites, contract):
    """Use the reviewed case set as the gate as new regressions expand the suite."""
    require(isinstance(contract, dict) and type(contract.get('schemaVersion')) is int
            and contract['schemaVersion'] == 1, 'Invalid Java case contract')
    expected = contract.get('suites')
    require(isinstance(expected, list) and bool(expected), 'Empty Java case contract')
    def identities(rows, label):
        result = {}
        require(isinstance(rows, list) and bool(rows), 'Empty ' + label)
        for row in rows:
            require(isinstance(row, dict), 'Invalid ' + label)
            key = (row.get('module'), row.get('name'))
            require(all(isinstance(k, str) and bool(k.strip()) for k in key) and key not in result,
                    'Duplicate or invalid ' + label)
            cases = row.get('cases')
            require(isinstance(cases, list) and bool(cases) and all(isinstance(c, str) and c.strip() for c in cases)
                    and len(set(cases)) == len(cases), 'Empty or duplicate ' + label + ' cases')
            result[key] = set(cases)
        return result
    require(identities(suites, 'Java suites') == identities(expected, 'Java contract'), 'Wrong Java case identities')
    require(all(type(s.get('tests')) is int and s['tests'] == len(s['cases'])
                and all(type(s.get(k)) is int and s[k] == 0 for k in ('failures', 'errors', 'skipped'))
                for s in suites), 'Incomplete or failing Java evidence')
    return sum(len(s['cases']) for s in suites)
