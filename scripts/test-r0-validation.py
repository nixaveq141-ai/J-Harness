"""Offline adversarial tests for fresh R0 regression contracts and receipts."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('r0_validation',Path(__file__).with_name('run-r0-validation.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)


def digest(raw):return hashlib.sha256(raw).hexdigest()

def node_events(name='client case'):
    return [{'type':'case','caseId':name,'outcome':'PASS'}, {'type':'summary','counts':dict(tests=1,passed=1,failed=0,cancelled=0,skipped=0,todo=0)}]


class FixtureBase(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name).resolve()
        for name in ('scripts/test-unit.py','frontend/tests/client.test.ts'):
            path=self.root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('# synthetic test contract fixture\n')
        self.contract={'schemaVersion':1,'groups':[
            {'name':'gate-test','role':'gate','kind':'python','path':'scripts/test-unit.py','caseIds':['unit.case']},
            {'name':'client','role':'product','kind':'node','path':'tests/client.test.ts','cwd':'frontend','nodeArgs':['--import','tsx'],'caseIds':['client case']}]}
        self.addCleanup(patch.stopall)
        patch.object(gate,'ROOT',self.root).start()

    def reject(self,change,reason):
        contract=copy.deepcopy(self.contract);change(contract)
        with self.assertRaisesRegex(ValueError,reason):gate.validate_contract(contract)



class ValidationContractTest(FixtureBase):
    def test_valid_contract_schema_and_roles_pass(self):
        self.assertIs(self.contract,gate.validate_contract(self.contract))
        # Node's ESM loader requires a file URL, including on Windows drive paths.
        root_with_spaces = self.root / 'repo with spaces'
        root_with_spaces.mkdir()
        (root_with_spaces / 'frontend').mkdir()
        with patch.object(gate, 'ROOT', root_with_spaces):
            command, cwd = gate.command_for(self.contract['groups'][1], root_with_spaces / 'client.cases.json')
        reporter = next(arg.split('=', 1)[1] for arg in command if arg.startswith('--test-reporter=file:'))
        self.assertEqual((root_with_spaces / 'scripts/test-evidence-reporter.mjs').resolve().as_uri(), reporter)
        self.assertIn('repo%20with%20spaces', reporter)
        self.assertNotIn(' ', reporter)
        self.assertEqual(root_with_spaces / 'frontend', cwd)

    def test_contract_schema_duplicate_group_role_kind_and_path_reject(self):
        for change,reason in [
            (lambda c:c.update(schemaVersion=True),'contract schema'),
            (lambda c:c.update(groups=[]),'contract groups'),
            (lambda c:c['groups'].append(copy.deepcopy(c['groups'][0])),'Duplicate validation group name'),
            (lambda c:c['groups'][0].update(name='java'),'reserved validation group name'),
            (lambda c:c['groups'][0].update(role='optional'),'group role'),
            (lambda c:c['groups'][0].update(kind='shell'),'group kind'),
            (lambda c:c['groups'][0].update(path='../outside.py'),'Unsafe evidence path'),
            (lambda c:c['groups'][0].update(cwd='../outside'),'Unsafe evidence path'),
            (lambda c:c['groups'][0].update(caseIds=[]),'expected case contract'),
            (lambda c:c['groups'][0].update(caseIds=['same','same']),'expected case contract'),
            (lambda c:c['groups'][1].update(nodeArgs=['--eval','process.exit(0)']),'Node arguments')]:
            with self.subTest(reason=reason):self.reject(change,reason)

    def test_case_schema_missing_duplicate_skip_and_wrong_ids_reject(self):
        base={'schemaVersion':1,'status':'PASS','cases':[{'caseId':'a','outcome':'PASS'}]}
        for change,reason in [
            (lambda d:d.update(schemaVersion=True),'case schema'),
            (lambda d:d.update(cases=[]),'terminal cases'),
            (lambda d:d['cases'].append(copy.deepcopy(d['cases'][0])),'Duplicate or invalid'),
            (lambda d:d['cases'][0].update(outcome='SKIP'),'Failed, skipped'),
            (lambda d:d['cases'][0].update(caseId='wrong'),'contract mismatch')]:
            data=copy.deepcopy(base);change(data)
            with self.subTest(reason=reason),self.assertRaisesRegex(ValueError,reason):gate.validate_cases(data,['a'])
        self.assertEqual(1,gate.validate_cases(base,['a']))

    def test_node_events_require_one_final_summary_and_exact_integer_totals(self):
        def parse(events):return gate.node_cases(b'\n'.join(json.dumps(e).encode() for e in events))
        self.assertEqual('PASS',parse(node_events())['status'])
        for change,reason in [
            (lambda e:e.pop(),'unique Node test summary'),
            (lambda e:e.append(copy.deepcopy(e[-1])),'unique Node test summary'),
            (lambda e:e[-1]['counts'].update(tests=True),'Invalid Node count schema'),
            (lambda e:e[-1]['counts'].update(passed=0),'passing count mismatch'),
            (lambda e:e[-1]['counts'].update(skipped=1),'did not fully pass: skipped'),
            (lambda e:e[0].update(outcome='SKIP'),'Failed, skipped'),
            (lambda e:e.append({'type':'case','caseId':'later','outcome':'PASS'}),'summary must follow'),
            (lambda e:e.insert(0,{'type':'unknown'}),'invalid test events')]:
            events=node_events();change(events)
            with self.subTest(reason=reason),self.assertRaisesRegex(ValueError,reason):parse(events)


class RegressionReportTest(FixtureBase):
    def setUp(self):
        super().setUp()
        self.contract_path=self.root/'scripts/contracts/r0-validation-cases.json'
        self.contract_path.parent.mkdir(parents=True)
        self.contract_path.write_text(json.dumps(self.contract))
        patch.object(gate,'CONTRACT',self.contract_path).start()
        self.source={'scripts/synthetic.py':'a'*64}
        patch.object(gate,'inputs',return_value=self.source).start()
        self.destination=self.root/'.build-cache/R0/synthetic';self.destination.mkdir(parents=True)
        self.relative='.build-cache/R0/synthetic/results.json'
        self.java=dict(module='harness-fixture',name='example.Suite',cases=['case'],tests=1,failures=0,errors=0,skipped=0,sha256='b'*64)
        java_contract={'suites':[{k:self.java[k] for k in ('module','name','cases')}]}
        (self.contract_path.parent/'m1059-java-cases.json').write_text(json.dumps(java_contract))
        patch('evidence_checks.suite_snapshot',return_value=self.java).start()

    def write_report(self,with_product=False):
        report=dict(schemaVersion=1,status='LOCAL_REGRESSION_PASS',m11Eligible=False,runId='synthetic',
                    withProduct=with_product,sourceSha256=self.source,commands=[])
        groups=self.contract['groups'] if with_product else self.contract['groups'][:1]
        for group in groups:
            name=group['name'];cases_path=self.destination/(name+'.cases.json')
            command,cwd=gate.command_for(group,cases_path)
            raw=(b'\n'.join(json.dumps(e).encode() for e in node_events()) if group['kind']=='node' else
                 json.dumps(dict(schemaVersion=1,status='PASS',cases=[dict(caseId='unit.case',outcome='PASS')])).encode())
            cases_path.write_bytes(raw)
            self.add_entry(report,name,command,cwd,cases_path,{cases_path.relative_to(self.root).as_posix():digest(raw)},1)
        if with_product:
            path=self.destination/'java.cases.json';path.write_text(json.dumps(dict(schemaVersion=1,status='PASS',suites=[self.java])))
            outputs={'harness-fixture/target/surefire-reports/TEST-example.Suite.xml':self.java['sha256']}
            self.add_entry(report,'java',gate.java_command(),self.root,path,outputs,1)
        (self.root/self.relative).write_text(json.dumps(report))
        return report

    def add_entry(self,report,name,command,cwd,path,outputs,count):
        log=self.destination/(name+'.log');log.write_bytes(b'synthetic command log')
        receipt_path=self.destination/(name+'.receipt.json')
        receipt=dict(schemaVersion=1,status='PASS',name=name,runId='synthetic',command=command,cwd=cwd.relative_to(self.root).as_posix(),
                     exitCode=0,sourceSha256=self.source,sourceAfterSha256=self.source,logSha256=digest(log.read_bytes()),outputs=outputs,
                     startedAt='2026-01-01T00:00:00+00:00',finishedAt='2026-01-01T00:00:01+00:00')
        receipt_path.write_text(json.dumps(receipt))
        report['commands'].append(dict(name=name,command=command,cwd=receipt['cwd'],tests=count,
            receipt=receipt_path.relative_to(self.root).as_posix(),receiptSha256=digest(receipt_path.read_bytes()),
            cases=path.relative_to(self.root).as_posix(),casesSha256=digest(path.read_bytes()),
            log=log.relative_to(self.root).as_posix(),logSha256=digest(log.read_bytes())))

    def save(self,report):(self.root/self.relative).write_text(json.dumps(report))

    def test_gate_only_and_with_product_exact_complete_sets_can_pass(self):
        for flag,count in [(False,1),(True,3)]:
            with self.subTest(withProduct=flag):
                self.write_report(flag)
                result=gate.verify_report(self.relative)
                self.assertEqual(count,len(result['commands']))

    def test_with_product_bool_missing_product_or_java_and_extra_group_reject(self):
        for flag,change,reason in [
            (False,lambda d:d.update(withProduct=1),'withProduct flag'),
            (False,lambda d:d.pop('withProduct'),'withProduct flag'),
            (True,lambda d:d['commands'].pop(),'unexpected regression groups'),
            (True,lambda d:d['commands'].pop(1),'unexpected regression groups'),
            (True,lambda d:d.update(withProduct=False),'unexpected regression groups'),
            (False,lambda d:d['commands'].clear(),'Missing regression commands'),
            (False,lambda d:d['commands'].append(copy.deepcopy(d['commands'][0])),'Duplicate or invalid')]:
            report=self.write_report(flag);change(report);self.save(report)
            with self.subTest(reason=reason),self.assertRaisesRegex(ValueError,reason):gate.verify_report(self.relative)

    def test_command_and_cwd_tampering_even_with_matching_receipt_reject(self):
        for field,value,reason in [('command',['node','-e','process.exit(0)'],'Wrong regression command'),('cwd','frontend','Wrong regression working directory')]:
            report=self.write_report();entry=report['commands'][0];entry[field]=value
            path=self.root/entry['receipt'];receipt=json.loads(path.read_bytes());receipt[field]=value
            path.write_text(json.dumps(receipt));entry['receiptSha256']=digest(path.read_bytes());self.save(report)
            with self.subTest(field=field),self.assertRaisesRegex(ValueError,reason):gate.verify_report(self.relative)

    def test_outcome_count_and_artifact_path_tampering_reject(self):
        report=self.write_report();report['commands'][0]['tests']=True;self.save(report)
        with self.assertRaisesRegex(ValueError,'Regression count mismatch'):gate.verify_report(self.relative)
        report=self.write_report();report['commands'][0]['cases']='../outside.json';self.save(report)
        with self.assertRaisesRegex(ValueError,'Wrong regression artifact path'):gate.verify_report(self.relative)


if __name__=='__main__':unittest.main()
