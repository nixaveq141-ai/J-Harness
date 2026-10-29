"""Offline artifact counterexamples. Pass the downloaded original ZIP as argument."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import zipfile

spec = importlib.util.spec_from_file_location('auditor', Path(__file__).with_name('audit-fresh-code-isolation.py'))
auditor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(auditor)


def main():
    original = Path(sys.argv[1])
    with zipfile.ZipFile(original) as archive:
        contents = {n:archive.read(n) for n in archive.namelist()}
    evidence = next(n for n in contents if n.endswith('/evidence.json'))
    report = json.loads(contents[evidence])
    prefix = evidence.removesuffix('evidence.json')
    auditor.audit(original, auditor.sha(original.read_bytes()), report['commit'], report['ciRunId'])
    with tempfile.TemporaryDirectory() as temp:
        for case in ['source-drift','missing-control','writable-mount','false-control-log']:
            changed = copy.deepcopy(contents)
            value = json.loads(changed[evidence])
            if case == 'source-drift':
                value['sourcesBefore'][next(iter(value['sourcesBefore']))] = '0'*64
                value['sourcesAfter'] = dict(value['sourcesBefore'])
            elif case == 'missing-control':
                del changed[prefix+'wrong-answer/result.json']
            else:
                index = 0 if case == 'writable-mount' else 1
                label = 'fresh' if index == 0 else 'wrong-answer'
                if case == 'writable-mount':
                    value['runs'][index]['mounts'][0]['RW'] = True
                else:
                    log = b'ISOLATED_FUNCTIONAL_ORACLE_PASS\n'
                    changed[prefix+label+'/execution.log'] = log
                    value['runs'][index]['logSha256'] = auditor.sha(log)
                changed[prefix+label+'/result.json'] = json.dumps(value['runs'][index]).encode()
            changed[evidence] = json.dumps(value).encode()
            path = Path(temp)/(case+'.zip')
            with zipfile.ZipFile(path,'w') as archive:
                for name,data in changed.items(): archive.writestr(name,data)
            try:
                auditor.audit(path,auditor.sha(path.read_bytes()),report['commit'],report['ciRunId'])
            except ValueError:
                continue
            raise AssertionError('Tampered artifact accepted: '+case)
    print('PASS original plus 4 rejected counterexamples: source/control/mount/log')


if __name__ == '__main__': main()
