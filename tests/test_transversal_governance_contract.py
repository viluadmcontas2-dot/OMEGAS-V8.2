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
        self.assertEqual("omegas-v8.2-transversal-gate-v2", raw["schema"])
        ids = {item["id"] for item in raw["mandatory_sources"]}
        self.assertEqual(EXPECTED_MINIMUM_IDS, ids)

        governance = raw["governance_authorities"]
        self.assertTrue(governance["dynamic_contract_registry_resolution_required"])
        self.assertEqual(
            "6e104f112a43445aaae49db92daa35b0",
            governance["contract_registry_notion_id"],
        )
        self.assertTrue(governance["global_ledger_required"])
        self.assertEqual("GLOBAL-LEDGER-001", governance["audit_independence_authority"])
        self.assertEqual("PROVENANCE_BASED", governance["audit_independence_model"])

        conditional_ids = {item["contract_id"] for item in raw["conditional_bindings"]}
        self.assertTrue(EXPECTED_UIUX_CONTRACTS.issubset(conditional_ids))

        requires = raw["pass_requires"]
        self.assertTrue(requires["all_mandatory_sources_read"])
        self.assertTrue(requires["all_active_applicable_contracts_classified"])
        self.assertTrue(requires["uiux_bindings_classified_when_human_facing"])
        self.assertTrue(requires["independent_auditor"])
        self.assertEqual("PROVENANCE_BASED", requires["audit_independence_model"])
        self.assertTrue(requires["audit_epoch_run_scope_fingerprint_required"])
        self.assertTrue(requires["auditor_read_only_normative_required"])
        self.assertTrue(requires["auditor_normative_writes_zero_required"])
        self.assertTrue(requires["meta_audit_distinct_run_required"])
        self.assertTrue(requires["behavioral_test_when_behavior_executable"])
        self.assertTrue(requires["hardware_claims_require_target_device"])

        required_fields = set(raw["required_pass_fields"])
        for field in (
            "IMPLEMENTATION_RUN_ID",
            "AUDIT_EPOCH_ID",
            "AUDIT_RUN_ID",
            "AUDIT_SCOPE_FINGERPRINT",
            "AUDITOR_MODE",
            "AUDITOR_NORMATIVE_WRITES",
            "META_AUDIT_RUN_ID",
        ):
            self.assertIn(field, required_fields)

        conditions = set(raw["automatic_non_pass_conditions"])
        self.assertIn("ACTIVE_CONTRACT_NOT_CLASSIFIED", conditions)
        self.assertIn("UIUX_BINDING_NOT_CLASSIFIED", conditions)
        self.assertIn("AUDIT_PROVENANCE_NOT_INDEPENDENT", conditions)
        self.assertIn("AUDIT_RUN_NORMATIVE_AUDITED_MUTATION", conditions)
        self.assertIn("META_AUDIT_RUN_REUSED", conditions)
        self.assertNotIn("IMPLEMENTER_SELF_PASS", conditions)
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
            "AUDIT_INDEPENDENCE=PROVENANCE_BASED",
            "AUDIT_EPOCH_ID",
            "AUDIT_RUN_ID",
            "AUDIT_SCOPE_FINGERPRINT",
            "NORMATIVE_AUDITED_MUTATION",
            "META_AUDIT_RUN_ID != AUDIT_RUN_ID",
        ):
            self.assertIn(token, doc)
        self.assertIn("transversal-pass-fail-gate.json", agents)
        self.assertIn("TRANSVERSAL PASS/FAIL", agents)
        self.assertIn("STALE_BY_GOVERNANCE", agents)
        self.assertIn("AUDIT_INDEPENDENCE=PROVENANCE_BASED", agents)
        self.assertIn("AUDITOR_MODE=READ_ONLY_NORMATIVE", agents)
        self.assertIn("AUDITOR_NORMATIVE_WRITES=0", agents)
        self.assertIn("NORMATIVE_AUDITED_MUTATION", agents)
        self.assertIn("META_AUDIT_RUN_ID == AUDIT_RUN_ID", agents)


if __name__ == "__main__":
    unittest.main()
