import json
import tempfile
import unittest
from pathlib import Path

from equivalence_oracle import (
    bootstrap_interval,
    load_manifest,
    percentile,
    split_by_session,
)


class EquivalenceOracleTest(unittest.TestCase):
    def test_manifest_loading_is_deterministic_and_deduplicates_session_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "manifest.json"
            path.write_text(json.dumps({"sessions": [
                {"session_id": "b", "file": "b.zip"},
                {"session_id": "a", "file": "a.zip"},
                {"session_id": "a", "file": "duplicate.zip"},
            ]}), encoding="utf-8")
            loaded = load_manifest(path)
            self.assertEqual(["a", "b"], [x["session_id"] for x in loaded])
            self.assertEqual("a.zip", loaded[0]["file"])

    def test_holdout_never_trains_on_same_session(self):
        rows = [
            {"session_id": "a", "value": 1},
            {"session_id": "b", "value": 2},
            {"session_id": "c", "value": 3},
        ]
        train, test = split_by_session(rows, "b")
        self.assertEqual({"a", "c"}, {x["session_id"] for x in train})
        self.assertEqual({"b"}, {x["session_id"] for x in test})

    def test_percentile_and_bootstrap_are_seed_reproducible(self):
        values = [0.01, 0.02, 0.03, 0.04]
        # Linear interpolation at p90 uses position (n-1)*q = 2.7 => 0.037.
        self.assertAlmostEqual(0.037, percentile(values, 0.90), places=12)
        first = bootstrap_interval(values, seed=8206, iterations=500)
        second = bootstrap_interval(values, seed=8206, iterations=500)
        self.assertEqual(first, second)
        self.assertLessEqual(first[0], first[1])
        self.assertLessEqual(first[1], first[2])


if __name__ == "__main__":
    unittest.main()
