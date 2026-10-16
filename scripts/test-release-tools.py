"""Offline tests for release-gate failure handling, no external services."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("scan_osv", Path(__file__).with_name("scan-osv.py"))
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class OsvGateTest(unittest.TestCase):
    def run_scan(self, api):
        target = Path(__file__).resolve().parents[1] / "target"
        target.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=target) as temp:
            path = Path(temp) / "bom.json"
            path.write_text(json.dumps({"bomFormat": "CycloneDX", "components": [
                {"group": "example", "name": "a", "version": "1"},
                {"group": "example", "name": "b", "version": "2"}]}))
            return scanner.scan(path, Path(temp) / "result.json", api)

    def test_only_pending_query_is_paginated_and_finding_blocks(self):
        calls = []

        def api(path, body=None):
            calls.append((path, body))
            if path.startswith("vulns/"):
                return {"id": "GHSA-fixture", "summary": "fixture"}
            if len(calls) == 1:
                return {"results": [{"vulns": [{"id": "GHSA-fixture"}], "next_page_token": "next"}, {}]}
            self.assertEqual(1, len(body["queries"]))
            self.assertEqual("example:a", body["queries"][0]["package"]["name"])
            self.assertEqual("next", body["queries"][0]["page_token"])
            return {"results": [{}]}

        result = self.run_scan(api)
        self.assertEqual("FINDINGS", result["status"])
        self.assertEqual(1, result["activeFindings"])
        self.assertEqual(2, result["pages"])

    def test_network_failure_is_incomplete(self):
        def api(*args):
            raise TimeoutError("fixture timeout")
        self.assertEqual("INCOMPLETE", self.run_scan(api)["status"])

    def test_truncated_batch_is_incomplete(self):
        self.assertEqual("INCOMPLETE", self.run_scan(lambda *a: {"results": [{}]})["status"])

    def test_repeated_page_token_is_incomplete(self):
        def api(*args):
            return {"results": [{"next_page_token": "same"}, {"next_page_token": "same"}]}
        self.assertEqual("INCOMPLETE", self.run_scan(api)["status"])

    def test_no_findings_passes(self):
        self.assertEqual("PASS", self.run_scan(lambda *a: {"results": [{}, {}]})["status"])


if __name__ == "__main__":
    unittest.main()
