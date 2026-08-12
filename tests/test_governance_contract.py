import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class GovernanceContract(unittest.TestCase):
    def test_required_governance_documents_exist(self):
        required = [
            "AGENTS.md",
            "START_HERE.md",
            "SKILLS.md",
            "TESTING_RULES.md",
            "PROJECT_STATE.md",
            "DECISIONS.md",
            "CAPABILITY_MATRIX.md",
            "docs/TEST_STRATEGY.md",
            "docs/BLOCK_1_SESSION_TELEMETRY.md",
            "docs/incidents/2026-08-04-session-state-was-implicit.md",
            "docs/incidents/2026-08-09-multiple-ecu-write-authorities.md",
            "docs/incidents/2026-08-09-curve-suggestion-display-only.md",
            "docs/incidents/2026-08-09-autocal-version-and-extended-status.md",
        ]
        missing = [path for path in required if not (ROOT / path).is_file()]
        self.assertEqual([], missing)

    def test_remote_first_and_non_redundant_authorization_are_documented(self):
        agents = (ROOT / "AGENTS.md").read_text("utf-8").lower()
        self.assertIn("trabalho remoto primeiro", agents)
        self.assertIn("autorizações sem repetição", agents)
        self.assertIn("não pedir nova autorização", agents)
        self.assertIn("microautorizações", agents)

    def test_v8_is_current_repo_and_v7_is_historical_origin(self):
        agents = (ROOT / "AGENTS.md").read_text("utf-8").lower()
        state = (ROOT / "PROJECT_STATE.md").read_text("utf-8").lower()
        start = (ROOT / "START_HERE.md").read_text("utf-8").lower()
        for text in (agents, state, start):
            self.assertIn("viluadmcontas-alt/omegas-v8", text)
        self.assertIn("origem histórica", agents)
        self.assertIn("origem histórica", state)

    def test_customrom_method_and_map_autonomy_are_governed(self):
        agents = (ROOT / "AGENTS.md").read_text("utf-8").lower()
        decisions = (ROOT / "DECISIONS.md").read_text("utf-8").lower()
        for marker in (
            "customrom",
            "intenção humana",
            "complexidade sob demanda",
            "feedback imediato",
            "segurança contextual",
        ):
            self.assertIn(marker, agents)
            self.assertIn(marker, decisions)
        self.assertIn("uma única autoridade", agents)
        self.assertIn("uma autoridade por fluxo", decisions)
        self.assertIn("não de aparência", agents)
        self.assertIn("1 a 144 células", agents)
        self.assertIn("1 a 144 células", decisions)
        self.assertIn("blocos de até 16", agents)

    def test_skills_define_preventive_before_and_after_verification(self):
        skills = (ROOT / "SKILLS.md").read_text("utf-8").lower()
        for concept in (
            "code-verification",
            "code-work",
            "inspeção preventiva",
            "matriz requisito",
            "verificar remoto",
            "project_state.md",
        ):
            self.assertIn(concept, skills)
        self.assertIn("omegas v8", skills)

    def test_ecu_write_invariants_are_documented(self):
        agents = (ROOT / "AGENTS.md").read_text("utf-8")
        required = [
            "nenhuma escrita automática na ECU",
            "ACK e readback são obrigatórios",
            "OBD é observacional",
            "linha técnica do Mapa K não é editável",
            "CalibrationWriteSafetyPolicy",
        ]
        for rule in required:
            self.assertIn(rule, agents)

    def test_quality_gate_workflow_has_required_layers_and_no_push_apk(self):
        workflow = (ROOT / ".github/workflows/quality-gate.yml").read_text("utf-8")
        required = [
            "test_governance_contract.py",
            "tools/run_checks.py",
            "testDebugUnitTest",
            "lintDebug",
            "assembleDebug",
            "android_required",
            "cancel-in-progress: true",
            "upload-artifact",
            "'refactor/**'",
            "find app/src/main/assets/ui -name '*.js'",
            "workflow_dispatch",
            "apk_allowed=false",
        ]
        for item in required:
            self.assertIn(item, workflow)
        self.assertNotIn("app/src/main/assets/ui/map-screen.js", workflow)

    def test_clean_slate_session_and_ui_authority_are_registered(self):
        required = [
            "app/src/main/assets/ui/core/store.js",
            "app/src/main/assets/ui/core/router.js",
            "app/src/main/assets/ui/core/scheduler.js",
            "app/src/main/assets/ui/core/native-api.js",
            "app/src/main/assets/ui/core/learning-model.js",
            "app/src/main/assets/ui/screens/dashboard.js",
            "app/src/main/assets/ui/screens/learning.js",
            "app/src/main/assets/ui/screens/map.js",
            "app/src/main/assets/ui/screens/curve.js",
            "app/src/main/assets/ui/screens/obd.js",
            "tests/test_block1_session_contract.py",
            "tests/test_v7_map_batch_contract.py",
            "tests/test_mp48_extended_status_contract.py",
        ]
        self.assertEqual([], [path for path in required if not (ROOT / path).is_file()])
        forbidden = [
            "app/src/main/assets/ui/session-state.js",
            "app/src/main/assets/ui/session.css",
            "app/src/main/assets/ui/map-screen.js",
            "app/src/main/assets/ui/learning-view.js",
        ]
        self.assertEqual([], [path for path in forbidden if (ROOT / path).exists()])
        runner = (ROOT / "tools/run_checks.py").read_text("utf-8")
        state = (ROOT / "PROJECT_STATE.md").read_text("utf-8").lower()
        testing = (ROOT / "TESTING_RULES.md").read_text("utf-8").lower()
        for test_path in (
            "tests/test_block1_session_contract.py",
            "tests/test_v7_map_batch_contract.py",
            "tests/test_mp48_extended_status_contract.py",
        ):
            self.assertIn(test_path, runner)
        for concept in (
            "ui clean-slate",
            "modo oficina removido",
            "gate nativo",
            "condução provável",
        ):
            self.assertIn(concept, state)
        self.assertIn("gate de sessão, telemetria e uso contínuo", testing)
        self.assertIn("1 a 144", testing)

    def test_deep_learning_matrix_and_emulator_gate_exist(self):
        scenario = ROOT / "app/src/test/java/com/omegas/prohub/learning/LearningScenarioMatrixTest.kt"
        emulator = ROOT / ".github/workflows/emulator-smoke.yml"
        self.assertTrue(scenario.is_file())
        self.assertTrue(emulator.is_file())
        scenario_text = scenario.read_text("utf-8")
        emulator_text = emulator.read_text("utf-8")
        for concept in [
            "conserva todo o peso",
            "preenche quatro celulas",
            "detecta celula ausente",
            "nao inventa erro residual",
            "anomalia local gera sugestao",
            "ordem das comparacoes",
            "dados invalidos",
        ]:
            self.assertIn(concept, scenario_text)
        for evidence in [
            "workflow_dispatch",
            "android-emulator-runner",
            "uiautomator dump",
            "screencap",
            "FATAL EXCEPTION",
            "ForegroundServiceDidNotStopInTimeException",
        ]:
            self.assertIn(evidence, emulator_text)

    def test_no_committed_signing_material_or_plain_secrets(self):
        forbidden = list(ROOT.rglob("*.jks")) + list(ROOT.rglob("*.keystore"))
        self.assertEqual([], forbidden)
        suspicious_names = ["keystore.properties", ".env", "secrets.properties"]
        committed = [name for name in suspicious_names if (ROOT / name).exists()]
        self.assertEqual([], committed)

    def test_active_ui_does_not_gain_automatic_writer_trigger(self):
        ui = ROOT / "app/src/main/assets/ui"
        app = (ui / "app.js").read_text("utf-8")
        map_screen = (ui / "screens/map.js").read_text("utf-8")
        curve_screen = (ui / "screens/curve.js").read_text("utf-8")
        drawers = (ui / "components/drawers.js").read_text("utf-8")
        scheduler = (ui / "core/scheduler.js").read_text("utf-8")
        native_api = (ui / "core/native-api.js").read_text("utf-8")
        self.assertNotIn("startMapBatchWrite(", app + map_screen + curve_screen + drawers)
        self.assertNotIn("startCurveBatchWrite(", app + map_screen + curve_screen + drawers)
        self.assertEqual(1, scheduler.count("setInterval("))
        self.assertNotIn("setInterval(", app)
        self.assertIsNone(re.search(r"setInterval\([^)]*(writeMap|writeCurve)", scheduler, re.S))
        self.assertIn("'startMapBatchWrite'", native_api)
        self.assertIn("'startCurveBatchWrite'", native_api)


if __name__ == "__main__":
    unittest.main()
