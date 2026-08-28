import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANCHOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/NativeLearningAnchor.kt"
STORE = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"

class Owner119AnchorOverlapContract(unittest.TestCase):
    def test_anchor_is_context_not_second_comparison_vote(self):
        source = ANCHOR.read_text("utf-8")
        self.assertIn('val sourceFuel: String', source)
        self.assertIn('"PETROL"', source)
        self.assertIn('"GNV"', source)
        self.assertIn('val overlapKey:', source)
        self.assertIn('val effectiveComparisonWeight: Double', source)
        self.assertIn('get() = 0.0', source)
        self.assertIn('.put("comparisonVote", false)', source)
        self.assertIn('.put("directKTarget", false)', source)
        self.assertIn('anchor.overlapKey in overlaps', source)

    def test_real_learning_import_only_ledgers_anchor(self):
        store = STORE.read_text("utf-8")
        start = store.index('fun importNativeSnapshot(')
        end = store.index('\n    fun onCalibrationAdjustment', start)
        body = store[start:end]
        self.assertIn('NativeLearningAnchor.fromMaturityEvent', body)
        self.assertIn('nativeAnchors.upsert(anchor)', body)
        self.assertNotIn('previewKWrite', body)
        self.assertNotIn('write', body.lower())
        self.assertNotIn('suggested_value', body)
        self.assertNotIn('target', body.lower())

if __name__ == "__main__":
    unittest.main()
