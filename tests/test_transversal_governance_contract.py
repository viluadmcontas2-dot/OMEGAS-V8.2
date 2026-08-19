import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "docs/contracts/transversal-pass-fail-gate.json"
DOC = ROOT / "docs/contracts/TRANSVERSAL_PASS_FAIL_GATE.md"
AGENTS = ROOT / "AGENTS.md"

EXPECTED_MINIMUM_IDS = {
    "PROGRAM_MASTER",
    "MASTER_TRACE_MAP",
    "GS_001",
    "EVIDENCE_PROVENANCE",
    "AL_001",
    "AL_002",
    "AL_003",
    "AL_003A",
    "AL_004",
    "ADP_001",
    "HW_001",
}

EXPECTED_UIUX_CONTRACTS = {
    "OME-STATE-HUMAN-UI",
    "UIUX-CUSTOMROM",
    "UIUX-OMEGADEV",
}


class TransversalGovernanceContract(unittest.TestCase):
    def test_registry_resolves_live_contract_authority_and_is_fail_closed(self):
        raw = json.loads(REGISTRY.read_text("utf-8"))
        ids = {item["id"] for item in raw["mandatory_sources"]}
        self.assertEqual(EXPECTED_MINIMUM_IDS, ids)

        governance = raw["governance_authorities"]
        self.assertTrue(governance["dynamic_contract_registry_resolution_required"])
        self.assertEqual(
            "6e104f112a43445aaae49db92daa35b0",
            governance["contract_registry_notion_id"],
        )
        self.assertTrue(governance["global_ledger_required"])

        conditional_ids = {item["contract_id"] for item in raw["conditional_bindings"]}
        self.assertTrue(EXPECTED_UIUX_CONTRACTS.issubset(conditional_ids))

        requires = raw["pass_requires"]
        self.assertTrue(requires["all_mandatory_sources_read"])
        self.assertTrue(requires["all_active_applicable_contracts_classified"])
        self.assertTrue(requires["uiux_bindings_classified_when_human_facing"])
        self.assertTrue(requires["independent_auditor"])
        self.assertTrue(requires["behavioral_test_when_behavior_executable"])
        self.assertTrue(requires["hardware_claims_require_target_device"])

        conditions = set(raw["automatic_non_pass_conditions"])
        self.assertIn("ACTIVE_CONTRACT_NOT_CLASSIFIED", conditions)
        self.assertIn("UIUX_BINDING_NOT_CLASSIFIED", conditions)
        self.assertIn("IMPLEMENTER_SELF_PASS", conditions)
        self.assertIn("HELPER_WITHOUT_CONSUMER", conditions)
        self.assertIn("STRING_ONLY_TEST_FOR_EXECUTABLE_BEHAVIOR", conditions)
        self.assertIn("HOST_BENCHMARK_AS_RK3326_EVIDENCE", conditions)
        self.assertIn("PREDICTION_USED_AS_OBSERVATION", conditions)
        self.assertIn("STALE_BY_GOVERNANCE", set(raw["allowed_gate_states"]))

    def test_human_contract_and_agents_bind_machine_registry(self):
        doc = DOC.read_text("utf-8")
        agents = AGENTS.read_text("utf-8")
        for token in (
            "Contract Registry",
            "GLOBAL-LEDGER-001",
            "MASTER TRACE MAP",
            "GS-001",
            "OME-EVIDENCE-PROVENANCE",
            "AL-001",
            "AL-002",
            "AL-003",
            "AL-003A",
            "AL-004",
            "OME-ADP-001",
            "HW-001",
            "OME-STATE-HUMAN-UI",
            "UIUX-CUSTOMROM",
            "UIUX-OMEGADEV",
        ):
            self.assertIn(token, doc)
        self.assertIn("transversal-pass-fail-gate.json", agents)
        self.assertIn("TRANSVERSAL PASS/FAIL", agents)
        self.assertIn("STALE_BY_GOVERNANCE", agents)
        self.assertIn("independent auditor", agents.lower())


if __name__ == "__main__":
    unittest.main()
