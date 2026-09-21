import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-point-update-dynamics-v1.json")


class AutoCalPointUpdateDynamicsEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_counter_alone_cannot_reconstruct_point_value(self):
        seq = self.data["sequence_examples"]
        by_counter = {}
        for row in seq:
            by_counter.setdefault(row["counter"], set()).add((row["x_raw"], row["y_raw"]))
        self.assertTrue(any(len(values) > 1 for values in by_counter.values()))
        conclusions = self.data["conclusions"]
        self.assertEqual(
            conclusions["point_value_is_not_a_function_of_counter_alone"],
            "PROVEN_AT_HOST_OBSERVATION_GRANULARITY",
        )
        self.assertEqual(
            conclusions["counter_is_not_safe_to_treat_as_running_average_sample_count"],
            "PROVEN_UNSAFE",
        )
        self.assertEqual(conclusions["exact_firmware_point_update_formula"], "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
