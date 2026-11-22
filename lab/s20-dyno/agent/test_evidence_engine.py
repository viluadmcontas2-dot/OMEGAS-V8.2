import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from dataclasses import replace

import evidence_engine as e


class EvidenceEngineTests(unittest.TestCase):
    def setUp(self):
        self.questions, self.records = e.first_s20_case()

    def test_session_decision_finds_four_descriptive_facts_not_a_cause(self):
        report = e.evaluate(self.questions, self.records)
        self.assertEqual(report["schema"], e.SCHEMA)
        resolved = {x["claim_id"]: x["state"] for x in report["questions"]}
        for key in ["q01-rpm-agreement", "q02-map-agreement", "q04-gnv-gap-event", "q07-fuel-wait"]:
            self.assertEqual(resolved[key], e.Resolution.MEASURED.value)
        for key in ["q03-water-sensor", "q05-gnv-gap-cause", "q06-voltage-byte19", "q08-fuel-wait-meaning"]:
            self.assertEqual(resolved[key], e.Resolution.UNTESTABLE.value)
        self.assertEqual(report["closed_this_capture"], 4)
        self.assertEqual(report["open_or_blocked"], 4)

    def test_repeating_same_sensor_does_not_prove_physical_meaning(self):
        q = next(q for q in self.questions if q.claim_id == "q03-water-sensor")
        repetitive = [e.Record(f"repeated-{i}", q.claim_id, "supports", "MP48",
                                "measurement", None, "Correlated temperatures", True)
                      for i in range(1000)]
        self.assertEqual(e.status(q, repetitive)[0], e.Resolution.UNTESTABLE)

    def test_two_independent_physical_tests_alone_do_not_close_without_source(self):
        q = next(q for q in self.questions if q.claim_id == "q03-water-sensor")
        a = e.Record("phys-01", q.claim_id, "supports", "TEST_BENCH_A",
                     "physical_test", "a" * 64, "Physical sensor measurement", True)
        b = replace(a, evidence_id="phys-02", origin="TEST_BENCH_B")
        self.assertEqual(e.status(q, [a, b])[0], e.Resolution.UNTESTABLE)
        doc = e.Record("original-doc", q.claim_id, "supports", "verified-original",
                       "document", "b" * 64, "Original protocol confirms physical meaning", True)
        self.assertEqual(e.status(q, [a, b, doc])[0], e.Resolution.MEASURED)

    def test_conflicting_direct_observations_block_closure(self):
        q = next(q for q in self.questions if q.claim_id == "q01-rpm-agreement")
        conflict = replace(self.records[0], evidence_id="conflict", verdict="contradicts")
        self.assertEqual(e.status(q, [self.records[0], conflict])[0], e.Resolution.OPEN)

    def test_llm_can_propose_only_read_only_planning(self):
        report = e.evaluate(self.questions, self.records)
        action = e.validate_llm_proposal({
            "claim_id": "q05-gnv-gap-cause", "step": "compare_historical_sessions",
            "reason": "Verificar episódios similares antes de atribuir a causa",
            "evidence_ids": ["S20-20261121-GAP-CAUSE"],
        }, report)
        self.assertEqual(action["state"], "PLANEJAMENTO_NAO_EXECUTADO")
        self.assertFalse(action["allowed_to_write_to_vehicle"])
        self.assertEqual(report["open_or_blocked"], 4)

    def test_llm_cannot_request_reset_or_vehicle_write(self):
        report = e.evaluate(self.questions, self.records)
        proposal = {"claim_id": "q05-gnv-gap-cause", "step": "reset_ecu",
                    "reason": "Resetar ECU para descobrir causa", "evidence_ids": []}
        with self.assertRaises(ValueError):
            e.validate_llm_proposal(proposal, report)
        proposal["step"] = "compare_historical_sessions"
        with self.assertRaises(ValueError):
            e.validate_llm_proposal(proposal, report)

    def test_llm_cannot_invent_reference(self):
        report = e.evaluate(self.questions, self.records)
        with self.assertRaises(ValueError):
            e.validate_llm_proposal({
                "claim_id": "q05-gnv-gap-cause", "step": "compare_historical_sessions",
                "reason": "Consultar observações", "evidence_ids": ["fake-evidence"],
            }, report)

    def test_closed_description_not_reopened_without_new_claim(self):
        report = e.evaluate(self.questions, self.records)
        with self.assertRaises(ValueError):
            e.validate_llm_proposal({
                "claim_id": "q04-gnv-gap-event", "step": "compare_historical_sessions",
                "reason": "Repetir", "evidence_ids": [],
            }, report)

    def test_duplicate_and_unknown_claims_rejected(self):
        with self.assertRaises(ValueError):
            e.evaluate(self.questions, self.records + [replace(self.records[0], claim_id="unknown")])
        with self.assertRaises(ValueError):
            e.evaluate(self.questions, self.records + [self.records[0]])

    def test_archive_hash_integrity_validation_and_read_only(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "capture.zip"
            data = {"schemaVersion": 1, "observations": [
                {"source": "obd", "mono_ns": 100, "data": {"rpm": 870}},
                {"source": "mp48", "mono_ns": 110, "data": {"rpm": 869}},
            ]}
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("dyno-vault.json", json.dumps(data))
            before = path.read_bytes()
            from hashlib import sha256
            result = e.verify_archive(path, sha256(before).hexdigest())
            self.assertEqual(result["observation_count"], 2)
            self.assertEqual(result["source_counts"], {"obd": 1, "mp48": 1})
            self.assertEqual(path.read_bytes(), before)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                e.verify_archive(path, "0" * 64)

    def test_unsafe_new_step_cannot_enter_question_ledger(self):
        with self.assertRaises(ValueError):
            e.Question("x", "what", "causation", 4, "flash_ecu", "proof")

    def test_llm_self_confidence_is_not_evidence(self):
        q = next(q for q in self.questions if q.claim_id == "q06-voltage-byte19")
        fake = e.Record("LLM-YES", q.claim_id, "supports", "LLM", "document",
                        None, "I am 99% certain the voltage formula is valid", True)
        self.assertEqual(e.status(q, [fake])[0], e.Resolution.UNTESTABLE)


if __name__ == "__main__":
    unittest.main(verbosity=2)
