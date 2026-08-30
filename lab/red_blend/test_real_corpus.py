import math
import re
import unittest
from collections import Counter
from pathlib import Path

from lab.red_blend.real_corpus import analyze_real_regions, load_governed_fixture


FIXTURE_DIR = Path("tests/fixtures/science/episodes")
INDEX_PATH = FIXTURE_DIR / "index.json"
EXPECTED_FIELDS = {
    "session_key",
    "order",
    "fuel",
    "start_ms",
    "end_ms",
    "rpm",
    "map_bar",
    "petrol_ms",
    "window_count",
    "rpm_bin",
    "map_bin",
}


class RealCorpusBlendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.episodes = load_governed_fixture(FIXTURE_DIR, INDEX_PATH)

    def test_governed_fixture_identity_and_privacy_shape(self):
        self.assertEqual(len(self.episodes), 1708)
        self.assertEqual(Counter(e["fuel"] for e in self.episodes), {"GASOLINA": 266, "GNV": 1442})
        for episode in self.episodes:
            self.assertEqual(set(episode), EXPECTED_FIELDS)
            self.assertRegex(episode["session_key"], re.compile(r"^[0-9a-f]{16}$"))
            self.assertIn(episode["fuel"], {"GASOLINA", "GNV"})
            self.assertTrue(math.isfinite(float(episode["rpm"])))
            self.assertTrue(math.isfinite(float(episode["map_bar"])))
            self.assertTrue(math.isfinite(float(episode["petrol_ms"])))

    def test_real_gasoline_regions_are_analyzed_without_claiming_transfer(self):
        report = analyze_real_regions(self.episodes, fuel="GASOLINA", min_samples=4, bootstrap_draws=600, seed=20260830)
        self.assertEqual(report.fuel, "GASOLINA")
        self.assertEqual(report.total_fuel_episodes, 266)
        self.assertGreater(report.analyzed_regions, 0)
        self.assertGreater(report.analyzed_episodes, 0)
        self.assertLessEqual(report.analyzed_episodes, 266)
        self.assertEqual(
            report.analyzed_regions,
            report.unimodal_regions + report.multimodal_regions + report.ambiguous_regions,
        )
        self.assertEqual(report.claim_scope, "REAL_CORPUS_LOCAL_ONLY_NOT_TRANSFER")
        self.assertEqual(report.policy_label, "LAB_HEURISTIC")
        for region in report.regions:
            self.assertGreaterEqual(region.count, 4)
            self.assertGreater(region.bootstrap.high, region.bootstrap.low)
            self.assertTrue(math.isfinite(region.summary.mean))
            self.assertTrue(math.isfinite(region.multimodality.bic_gain))
            self.assertGreaterEqual(region.unique_sessions, 1)

    def test_real_region_audit_is_seed_deterministic(self):
        a = analyze_real_regions(self.episodes, fuel="GASOLINA", min_samples=4, bootstrap_draws=400, seed=17)
        b = analyze_real_regions(self.episodes, fuel="GASOLINA", min_samples=4, bootstrap_draws=400, seed=17)
        self.assertEqual(a, b)


if __name__ == "__main__":
    unittest.main()
