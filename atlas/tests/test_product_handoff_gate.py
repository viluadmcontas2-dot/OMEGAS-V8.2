import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "atlas/manifests/product-handoff-gate.json"
TOOL = ROOT / "atlas/tools/audit_product_handoff.py"
TRANSVERSAL = ROOT / "docs/contracts/transversal-pass-fail-gate.json"

EXPECTED_NOTION = {
    "CUSTOMROM_BLUEPRINT": "3b68ee52ac5481839046f36b482aab44",
    "OMEGADEV_UIUX": "3b78ee52ac5481eb89b2cc4bb711700b",
    "FINAL_PREIMPLEMENTATION_AUDIT": "3bf8ee52ac54813bac00cbe631255507",
    "OMEGAS_CLEAN_HEART": "3bd8ee52ac548168bc66fa1998de0586",
}

class ProductHandoffGateContract(unittest.TestCase):
    def load_tool(self):
        self.assertTrue(TOOL.is_file(), "product handoff audit tool is missing")
        spec = importlib.util.spec_from_file_location("atlas_product_handoff", TOOL)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod

    def test_manifest_binds_existing_transversal_gate_and_blueprints(self):
        self.assertTrue(MANIFEST.is_file(), "product handoff gate manifest is missing")
        gate = json.loads(MANIFEST.read_text("utf-8"))
        transversal = json.loads(TRANSVERSAL.read_text("utf-8"))

        self.assertEqual("omegas.atlas.product-handoff-gate.v1", gate["schema"])
        self.assertEqual("SCIENTIFIC_AUTHORITY", gate["branch_roles"]["OmegasAtlas"])
        self.assertEqual("REPLAY_RUNTIME_CHALLENGER", gate["branch_roles"]["work/omegas-amarelo-foundation"])
        self.assertEqual("PRODUCT_CONSUMER_AND_LEAD_SOURCE", gate["branch_roles"]["OmegasVerde"])
        self.assertEqual(EXPECTED_NOTION, gate["notion_sources"])

        conditional = {x["contract_id"] for x in transversal["conditional_bindings"]}
        self.assertTrue({"OME-STATE-HUMAN-UI", "UIUX-CUSTOMROM", "UIUX-OMEGADEV"}.issubset(conditional))
        self.assertEqual(
            ["CANONICAL_INPUTS", "ATLAS_PROOF", "CONSUMER_RUNTIME", "PRODUCT_UIUX"],
            gate["authority_order"],
        )

        required = set(gate["required_invariants"])
        for item in {
            "HUMAN_INTENT_BEFORE_TECHNOLOGY",
            "UI_PROJECTS_TYPED_STATE_ONLY",
            "UI_MUST_NOT_RECREATE_SCIENCE",
            "TECHNICAL_DETAIL_ON_DEMAND",
            "NORMALITY_COMPACT_PROBLEM_EXPANDS",
            "VISIBLE_FAILURE_AND_RECOVERY",
            "REVERSIBLE_ACTION_SHOWS_ROLLBACK",
            "SCREEN_LIFECYCLE_MUST_NOT_OWN_SERIAL_IO",
            "ROUTE_CHURN_MUST_NOT_CHANGE_SCIENCE",
            "ONE_STORE_ROUTER_SCHEDULER",
            "NO_SILENT_CLAMP",
            "STABLE_INTENT_ID_FOR_MATERIAL_ACTIONS",
            "NO_FAKE_PROGRESS_PERCENT",
            "WRITE_PREVIEW_BEFORE_HUMAN_COMMIT",
            "ACK_READBACK_RECOVERY_MANDATORY_AFTER_WRITE",
        }:
            self.assertIn(item, required)

    def test_fail_closed_when_consumer_attempts_to_be_scientific_oracle(self):
        audit = self.load_tool()
        receipt = audit.example_receipt()
        receipt["science_authority"] = "OmegasVerde"
        result = audit.audit(receipt)
        self.assertEqual("FAIL", result["status"])
        self.assertIn("PRODUCT_CANNOT_OVERRIDE_ATLAS_SCIENCE", result["violations"])

    def test_fail_closed_when_ui_recreates_science_or_route_changes_runtime(self):
        audit = self.load_tool()
        receipt = audit.example_receipt()
        receipt["ui"]["derives_scientific_truth"] = True
        receipt["runtime"]["route_churn_changes_science"] = True
        result = audit.audit(receipt)
        self.assertEqual("FAIL", result["status"])
        self.assertIn("UI_PARALLEL_SCIENCE_AUTHORITY", result["violations"])
        self.assertIn("ROUTE_CHURN_CHANGES_SCIENCE", result["violations"])

    def test_fail_closed_when_write_has_no_preview_or_readback_recovery(self):
        audit = self.load_tool()
        receipt = audit.example_receipt()
        receipt["writes"]["preview_before_commit"] = False
        receipt["writes"]["ack_readback_required"] = False
        receipt["writes"]["recovery_defined"] = False
        result = audit.audit(receipt)
        self.assertEqual("FAIL", result["status"])
        self.assertIn("WRITE_WITHOUT_PREVIEW", result["violations"])
        self.assertIn("WRITE_WITHOUT_ACK_READBACK", result["violations"])
        self.assertIn("WRITE_WITHOUT_RECOVERY", result["violations"])


    def test_fail_closed_without_live_governance_and_independent_audit(self):
        audit = self.load_tool()
        receipt = audit.example_receipt()
        receipt["governance"]["contract_registry_resolved"] = False
        receipt["governance"]["global_ledger_loaded"] = False
        receipt["governance"]["transversal_status"] = "PARTIAL"
        receipt["governance"]["audit_independence_model"] = "SELF_ASSERTED"
        receipt["governance"]["auditor_normative_writes"] = 1
        receipt["governance"]["meta_audit_distinct"] = False
        result = audit.audit(receipt)
        self.assertEqual("FAIL", result["status"])
        for code in {
            "LIVE_CONTRACT_REGISTRY_NOT_RESOLVED",
            "GLOBAL_LEDGER_NOT_LOADED",
            "TRANSVERSAL_GATE_NOT_PASS",
            "AUDIT_NOT_PROVENANCE_INDEPENDENT",
            "AUDITOR_MUTATED_NORMATIVE_TARGET",
            "META_AUDIT_NOT_DISTINCT",
        }:
            self.assertIn(code, result["violations"])

    def test_good_cross_branch_handoff_passes(self):
        audit = self.load_tool()
        result = audit.audit(audit.example_receipt())
        self.assertEqual("PASS", result["status"])
        self.assertEqual([], result["violations"])

if __name__ == "__main__":
    unittest.main()
