import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('oval',Path(__file__).with_name('scan-ubuntu-oval.py'))
oval=importlib.util.module_from_spec(spec);spec.loader.exec_module(oval)
summarize=lambda raw:oval.summarize(raw,{'x':'patch'})


class OvalTest(unittest.TestCase):
    def data(self,rows):
        return ('<oval_results xmlns="'+oval.NS[1:-1]+'"><results><system><definitions>'+rows+'</definitions></system></results></oval_results>').encode()

    def test_findings_and_unknown_cannot_be_clearance(self):
        self.assertEqual('FINDINGS',summarize(self.data('<definition definition_id="x" result="true"/>'))['status'])
        self.assertEqual('INCOMPLETE',summarize(self.data('<definition definition_id="x" result="unknown"/>'))['status'])
        self.assertEqual('NO_USN_FINDINGS_IN_SELECTED_HOST_SCOPE',summarize(self.data('<definition definition_id="x" result="false"/>'))['status'])

    def test_inventory_match_is_not_a_vulnerability_and_missing_definition_rejected(self):
        raw=self.data('<definition definition_id="x" result="true"/><definition definition_id="y" result="false"/>')
        self.assertEqual([],oval.summarize(raw,{'x':'inventory','y':'patch'})['affectedDefinitions'])
        with self.assertRaises(ValueError):oval.summarize(raw,{'x':'inventory','y':'patch','z':'patch'})

    def test_empty_duplicate_and_unsafe_documents_rejected(self):
        for data in [self.data(''),self.data('<definition definition_id="x" result="false"/>'*2),b'<!DOCTYPE x><x/>',self.data('<definition definition_id="x" result="PASS"/>')]:
            with self.assertRaises(ValueError):summarize(data)


if __name__=='__main__':unittest.main()
