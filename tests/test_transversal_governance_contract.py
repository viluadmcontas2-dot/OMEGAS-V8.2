import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "docs/contracts/transversal-pass-fail-gate.json"
DOC = ROOT / "docs/contracts/TRANSVERSAL_PASS_FAIL_GATE.md"
AGENTS = ROOT / "AGENTS.md"

EXPECTED_IDS = {
    "PROGRAM_MASTER",
    "MASTER_TRACE_MAP",
    "AL_001",
    "AL_002",
    "AL_003",
    "AL_004",
    "HW_001",
}

class TransversalGovernanceContract(unittest.TestCase):
    def test_registry_is_complete_and_fail_closed(self):
        raw = json.loads(REGISTRY.read_text("utf-8"))
        ids = {item["id"] for item in raw["mandatory_sources"]}
        self.assertEqual(EXPECTED_IDS, ids)
        self.assertTrue(raw["pass_requires"]["all_mandatory_sources_read"])
        self.assertTrue(raw["pass_requires"]["independent_auditor"])
        self.assertTrue(raw["pass_requires"]["behavioral_test_when_behavior_executable"])
        self.assertTrue(raw["pass_requires"]["hardware_claims_require_target_device"])
        conditions = set(raw["automatic_non_pass_conditions"])
        self.assertIn("IMPLEMENTER_SELF_PASS", conditions)
        self.assertIn("HELPER_WITHOUT_CONSUMER", conditions)
        self.assertIn("STRING_ONLY_TEST_FOR_EXECUTABLE_BEHAVIOR", conditions)
        self.assertIn("HOST_BENCHMARK_AS_RK3326_EVIDENCE", conditions)
        self.assertIn("PREDICTION_USED_AS_OBSERVATION", conditions)
        self.assertIn("STALE_BY_GOVERNANCE", set(raw["allowed_gate_states"]))

    def test_human_contract_and_agents_bind_machine_registry(self):
        doc = DOC.read_text("utf-8")
        agents = AGENTS.read_text("utf-8")
        for token in ("MASTER TRACE MAP", "AL-001", "AL-002", "AL-003", "AL-004", "HW-001"):
            self.assertIn(token, doc)
        self.assertIn("transversal-pass-fail-gate.json", agents)
        self.assertIn("TRANSVERSAL PASS/FAIL", agents)
        self.assertIn("STALE_BY_GOVERNANCE", agents)
        self.assertIn("independent auditor", agents.lower())

if __name__ == "__main__":
    unittest.main()
