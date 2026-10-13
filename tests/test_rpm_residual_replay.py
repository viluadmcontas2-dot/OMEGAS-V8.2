import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "autocal_refine"))

import rpm_residual_replay as replay


class RpmResidualReplayTest(unittest.TestCase):
    def frame(self, t, fuel, rpm=1500.0, map_bar=0.40, petrol_ms=5.0, epoch=0):
        return replay.Frame(t, fuel, rpm, map_bar, petrol_ms, epoch)

    def test_stable_observation_requires_three_comparable_frames(self):
        frames = [
            self.frame(0, "GASOLINA", 1400, 0.40, 5.0),
            self.frame(300, "GASOLINA", 1450, 0.41, 5.1),
            self.frame(600, "GASOLINA", 1500, 0.42, 5.2),
            self.frame(900, "GNV", 1500, 0.42, 5.4),
        ]
        obs = replay.stable_observations(frames)
        self.assertEqual(1, len(obs))
        self.assertEqual("GASOLINA", obs[0].fuel)
        self.assertAlmostEqual(1450.0, obs[0].rpm)
        self.assertAlmostEqual(5.1, obs[0].petrol_ms)

    def test_online_pair_never_uses_future_petrol(self):
        observations = [
            replay.Observation(100, "GNV", 1500, 0.40, 5.5, 0),
            replay.Observation(200, "GASOLINA", 1500, 0.40, 5.0, 0),
            replay.Observation(300, "GASOLINA", 1510, 0.40, 5.1, 0),
            replay.Observation(400, "GNV", 1505, 0.40, 5.5, 0),
        ]
        pairs = replay.online_pairs(observations)
        self.assertEqual(1, len(pairs))
        self.assertEqual(400, pairs[0].t)
        self.assertAlmostEqual(5.1, pairs[0].petrol_ref_ms)

    def test_epoch_prevents_cross_calibration_pair(self):
        observations = [
            replay.Observation(100, "GASOLINA", 1500, 0.40, 5.0, 0),
            replay.Observation(200, "GASOLINA", 1510, 0.40, 5.1, 0),
            replay.Observation(300, "GNV", 1505, 0.40, 5.5, 1),
        ]
        self.assertEqual([], replay.online_pairs(observations))

    def test_native_calibration_epoch_is_a_boundary(self):
        events = [
            {"recordedAtMs": 100, "sequence": 1, "type": "telemetry", "data": {
                "fuel": "GASOLINA", "rpm": 1500, "load_bar": 0.4,
                "petrol_ms": 5.0, "plausible": True,
            }},
            {"recordedAtMs": 200, "sequence": 2, "type": "autocal_native_calibration_epoch", "data": {}},
            {"recordedAtMs": 300, "sequence": 3, "type": "telemetry", "data": {
                "fuel": "GNV", "rpm": 1500, "load_bar": 0.4,
                "petrol_ms": 5.5, "plausible": True,
            }},
        ]
        frames = replay.frames_from_events(events, "session")
        self.assertEqual([0, 1], [frame.epoch for frame in frames])

    def test_previous_physical_session_same_lineage_can_supply_reference(self):
        observations = [
            replay.Observation(100, "GASOLINA", 1500, 0.40, 5.0, 0, "session_a_car1"),
            replay.Observation(200, "GASOLINA", 1510, 0.40, 5.1, 0, "session_a_car1"),
            replay.Observation(300, "GNV", 1505, 0.40, 5.5, 0, "session_b_car1"),
        ]
        self.assertEqual([], replay.online_pairs(observations))
        pairs = replay.online_pairs(observations, carry_across_sessions=True)
        self.assertEqual(1, len(pairs))
        self.assertEqual("session_b_car1", pairs[0].session)

    def test_different_lineage_never_shares_reference(self):
        observations = [
            replay.Observation(100, "GASOLINA", 1500, 0.40, 5.0, 0, "session_a_car1"),
            replay.Observation(200, "GASOLINA", 1510, 0.40, 5.1, 0, "session_a_car1"),
            replay.Observation(300, "GNV", 1505, 0.40, 5.5, 0, "session_b_car2"),
        ]
        self.assertEqual([], replay.online_pairs(observations, carry_across_sessions=True))

    def test_attribution_marks_repeated_rpm_residual_local(self):
        pairs = []
        # Three visits in the low-RPM column, consistently +12%.
        for visit, base_t in enumerate((0, 40_000, 80_000)):
            for i in range(3):
                pairs.append(replay.Pair("s", 0, base_t + i * 1_000, 850, 0.40, 5.0, 5.6))
        # Other RPM columns at the same Tpet, near zero residual.
        for rpm in (1850, 2500):
            for i in range(5):
                pairs.append(replay.Pair("s", 0, 5_000 + i * 1_000, rpm, 0.40, 5.0, 5.0))
        groups = replay.classify(pairs)
        low = next(g for g in groups if g.column == 0 and g.band == 1)
        self.assertTrue(low.local)
        self.assertEqual("LOCAL", low.reason)
        self.assertGreater(low.residual_log, replay.ENTER_LOG)

    def test_attribution_does_not_invent_local_without_comparator(self):
        pairs = [replay.Pair("s", 0, i * 40_000, 850, 0.40, 5.0, 5.6) for i in range(9)]
        group = replay.classify(pairs)[0]
        self.assertFalse(group.local)
        self.assertEqual("SEM_OUTRA_COLUNA_NO_MESMO_T", group.reason)

    def test_manifest_marks_exact_duplicate_and_rejects_missing_session(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            valid = root / "a.json"
            duplicate = root / "b.json"
            invalid = root / "c.json"
            event = {
                "sequence": 1,
                "recordedAtMs": 1,
                "type": "full_snapshot",
                "data": {"session_recorder": {"sessionId": "session_physical_1", "droppedEvents": 0}},
            }
            payload = json.dumps(event) + "\n"
            valid.write_text(payload)
            duplicate.write_text(payload)
            invalid.write_text(json.dumps({"sequence": 1, "recordedAtMs": 1, "type": "telemetry", "data": {}}) + "\n")
            manifest = replay.build_manifest([valid, duplicate, invalid])
            by_name = {Path(row["path"]).name: row for row in manifest["files"]}
            self.assertEqual("ACCEPTED", by_name["a.json"]["classification"])
            self.assertEqual("DUPLICATE", by_name["b.json"]["classification"])
            self.assertEqual("REJECTED", by_name["c.json"]["classification"])
            self.assertEqual(hashlib.sha256(payload.encode()).hexdigest(), by_name["a.json"]["sha256"])

    def test_manifest_accepts_session_identity_from_canonical_sidecar(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "session_sidecar_car"
            root.mkdir()
            events = root / "events_0001.jsonl"
            events.write_text(json.dumps({"sequence": 1, "recordedAtMs": 1, "type": "telemetry", "data": {}}) + "\n")
            (root / "session_summary.json").write_text(json.dumps({"sessionId": "session_sidecar_car", "droppedEvents": 0}))
            row = replay.build_manifest([events])["files"][0]
            self.assertEqual("ACCEPTED", row["classification"])
            self.assertEqual(["session_sidecar_car"], row["sessions"])
            self.assertEqual("session_summary.json", row["sessionIdentitySource"])


if __name__ == "__main__":
    unittest.main()
