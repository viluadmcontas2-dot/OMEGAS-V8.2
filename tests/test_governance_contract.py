import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class StableRepositoryContract(unittest.TestCase):
    def test_minimal_operational_contract_exists(self):
        agents_path = ROOT / "AGENTS.md"
        self.assertTrue(agents_path.is_file())
        agents = agents_path.read_text("utf-8").lower()
        for marker in (
            "este repositório é a fonte canônica",
            "uma issue → a branch red",
            "notion e linear são somente memória histórica",
            "mapa k editável fica limitado a `100..180`",
            "github actions é último recurso",
        ):
            self.assertIn(marker, agents)
        for path in (
            "PROJECT.md",
            "STATUS.md",
            "docs/workunits/OMEGAS-RED-WU-001.md",
            "docs/superpowers/specs/2026-08-30-red-continuous-fast-learning-design.md",
            "docs/superpowers/plans/2026-08-30-red-continuous-fast-learning.md",
        ):
            self.assertTrue((ROOT / path).is_file(), path)

    def test_core_product_surfaces_are_present(self):
        required = [
            "app/src/main/assets/ui/core/store.js",
            "app/src/main/assets/ui/core/router.js",
            "app/src/main/assets/ui/core/scheduler.js",
            "app/src/main/assets/ui/core/native-api.js",
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

    def test_single_ui_scheduler_and_no_automatic_writer_trigger(self):
        ui = ROOT / "app/src/main/assets/ui"
        app = (ui / "app.js").read_text("utf-8")
        map_screen = (ui / "screens/map.js").read_text("utf-8")
        curve_screen = (ui / "screens/curve.js").read_text("utf-8")
        drawers = (ui / "components/drawers.js").read_text("utf-8")
        scheduler = (ui / "core/scheduler.js").read_text("utf-8")
        native_api = (ui / "core/native-api.js").read_text("utf-8")
        self.assertEqual(1, scheduler.count("setInterval("))
        self.assertNotIn("setInterval(", app)
        self.assertNotIn("startMapBatchWrite(", app + map_screen + curve_screen + drawers)
        self.assertNotIn("startCurveBatchWrite(", app + map_screen + curve_screen + drawers)
        self.assertIsNone(re.search(r"setInterval\([^)]*(writeMap|writeCurve)", scheduler, re.S))
        self.assertIn("'startMapBatchWrite'", native_api)
        self.assertIn("'startCurveBatchWrite'", native_api)

    def test_critical_regression_corpora_remain_present(self):
        required = [
            "app/src/test/java/com/omegas/prohub/learning/LearningScenarioMatrixTest.kt",
            "docs/incidents/2026-08-06-multimedia-telemetry-backpressure.md",
            "docs/incidents/2026-08-09-multiple-ecu-write-authorities.md",
            "docs/incidents/2026-08-11-consolidated-learning-volatility.md",
            "docs/TEST_STRATEGY.md",
        ]
        self.assertEqual([], [path for path in required if not (ROOT / path).is_file()])

    def test_no_committed_signing_material_or_plain_secrets(self):
        forbidden = list(ROOT.rglob("*.jks")) + list(ROOT.rglob("*.keystore"))
        self.assertEqual([], forbidden)
        suspicious_names = ["keystore.properties", ".env", "secrets.properties"]
        committed = [name for name in suspicious_names if (ROOT / name).exists()]
        self.assertEqual([], committed)


if __name__ == "__main__":
    unittest.main()
