import unittest
from pathlib import Path

from lab.red_blend.real_corpus import load_governed_fixture
from lab.red_blend.session_science import audit_real_session_regions

FIXTURE_DIR = Path("tests/fixtures/science/episodes")
INDEX_PATH = FIXTURE_DIR / "index.json"

class RealSessionScienceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.episodes = load_governed_fixture(FIXTURE_DIR, INDEX_PATH)

    def test_sparse_two_session_gasoline_candidate_cannot_claim_transfer(self):
        report = audit_real_session_regions(self.episodes, fuel="GASOLINA", min_samples=4, min_independent_sessions=3)
        candidate = {(r.rpm_bin, r.map_bin): r for r in report.regions}[(5, 13)]
        self.assertEqual(candidate.count, 6)
        self.assertEqual(candidate.session_count, 2)
        self.assertEqual(candidate.independent_status, "INSUFFICIENT_INDEPENDENT_SESSIONS")
        self.assertIsNone(candidate.loso)

    def test_dense_gasoline_regions_expose_session_decomposition_and_loso(self):
        report = audit_real_session_regions(self.episodes, fuel="GASOLINA", min_samples=4, min_independent_sessions=3)
        by_region = {(r.rpm_bin, r.map_bin): r for r in report.regions}
        for key in ((5, 9), (5, 10)):
            region = by_region[key]
            self.assertGreaterEqual(region.session_count, 9)
            self.assertEqual(region.independent_status, "SESSION_AUDITED")
            self.assertIsNotNone(region.loso)
            self.assertGreaterEqual(region.decomposition.between_session_variance, 0.0)
            self.assertGreaterEqual(region.decomposition.icc, 0.0)
            self.assertLessEqual(region.decomposition.icc, 1.0)

    def test_real_session_audit_is_deterministic(self):
        a = audit_real_session_regions(self.episodes, fuel="GASOLINA", min_samples=4, min_independent_sessions=3)
        b = audit_real_session_regions(self.episodes, fuel="GASOLINA", min_samples=4, min_independent_sessions=3)
        self.assertEqual(a, b)
        self.assertEqual(a.claim_scope, "SESSION_INDEPENDENCE_DIAGNOSTIC_NOT_PRODUCTION")

if __name__ == "__main__":
    unittest.main()
