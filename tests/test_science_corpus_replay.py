import io
import json
import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.science.corpus_replay import (
    Episode,
    SessionCandidate,
    choose_representative,
    derive_stable_windows,
    derive_episodes_from_frames,
    deterministic_gzip_jsonl_bytes,
    merge_windows_to_episodes,
    normalize_confirmed_k_history,
    parse_portmon_map_k_writes,
    process_session_candidate,
    privacy_session_key,
    walk_forward_pairs,
)


class CorpusReplayContractTest(unittest.TestCase):
    def test_privacy_key_is_stable_and_does_not_leak_session_id(self):
        raw = "session_2026-08-20_13-21-58_secret-device"
        key1 = privacy_session_key(raw)
        key2 = privacy_session_key(raw)
        self.assertEqual(key1, key2)
        self.assertEqual(16, len(key1))
        self.assertNotIn("session", key1)
        self.assertNotIn("secret", key1)

    def test_representative_prefers_largest_declared_event_stream(self):
        candidates = [
            SessionCandidate("s1", "a.zip", "aaa", 100, 1, "8.0.0-test-debug"),
            SessionCandidate("s1", "b.zip", "bbb", 250, 1, "8.0.0-test-debug"),
        ]
        self.assertEqual("b.zip", choose_representative(candidates).path)

    def test_representative_tie_break_is_deterministic(self):
        candidates = [
            SessionCandidate("s1", "z.zip", "bbb", 250, 1, None),
            SessionCandidate("s1", "a.zip", "aaa", 250, 1, None),
        ]
        self.assertEqual("a.zip", choose_representative(candidates).path)

    def test_stable_window_ignores_runtime_sample_state(self):
        frames = []
        for i in range(10):
            frames.append({
                "t_ms": i * 150,
                "fuel": "GASOLINA",
                "rpm": 2000 + (i % 2) * 10,
                "map_bar": 0.50 + (i % 2) * 0.002,
                "petrol_ms": 4.0 + (i % 2) * 0.01,
                "sample_state": "REJECTED_BY_RUNTIME",
            })
        windows = derive_stable_windows(frames)
        self.assertEqual(1, len(windows))
        self.assertEqual("GASOLINA", windows[0].fuel)

    def test_unstable_window_is_rejected(self):
        frames = []
        for i in range(10):
            frames.append({
                "t_ms": i * 150,
                "fuel": "GNV",
                "rpm": 1500 + i * 120,
                "map_bar": 0.4,
                "petrol_ms": 4.0,
            })
        self.assertEqual([], derive_stable_windows(frames))

    def test_windows_are_non_overlapping(self):
        frames = [{
            "t_ms": i * 150,
            "fuel": "GNV",
            "rpm": 2000,
            "map_bar": 0.5,
            "petrol_ms": 4.0,
        } for i in range(20)]
        windows = derive_stable_windows(frames)
        self.assertEqual(2, len(windows))
        self.assertLess(windows[0].end_ms, windows[1].start_ms)

    def test_episode_merge_respects_region_and_gap(self):
        frames = [{
            "t_ms": i * 150,
            "fuel": "GNV",
            "rpm": 2000,
            "map_bar": 0.5,
            "petrol_ms": 4.0,
        } for i in range(20)]
        windows = derive_stable_windows(frames)
        eps = merge_windows_to_episodes(windows, session_key="abc", order=4)
        self.assertEqual(1, len(eps))
        self.assertEqual(2, eps[0].window_count)

    def test_walk_forward_never_trains_on_current_or_future_order(self):
        eps = [
            Episode("a", 1, "GASOLINA", 10, 20, 1000, 0.5, 4.0, 1, 0, 0),
            Episode("b", 2, "GASOLINA", 30, 40, 1000, 0.5, 4.1, 1, 0, 0),
            Episode("c", 3, "GASOLINA", 50, 60, 1000, 0.5, 4.2, 1, 0, 0),
        ]
        pairs = walk_forward_pairs(eps)
        self.assertEqual([("a", "b"), ("a", "c"), ("b", "c")], [(tr.session_key, te.session_key) for tr, te in pairs])
        self.assertTrue(all(tr.order < te.order for tr, te in pairs))

    def test_portmon_map_k_write_checksum_and_bounds_fail_closed(self):
        body = [0x14, 0x54, 0x00, 0x03, 0x04, 0x64]
        checksum = sum(body) & 0xFF
        valid = f"IRP_MJ_WRITE Length 7: {' '.join(f'{x:02X}' for x in body+[checksum])}"
        writes = parse_portmon_map_k_writes(valid)
        self.assertEqual(1, len(writes))
        self.assertEqual((3, 4, 100), (writes[0]["row"], writes[0]["column"], writes[0]["value"]))
        bad = valid[:-2] + "00"
        self.assertEqual([], parse_portmon_map_k_writes(bad))
        out_of_bounds_body = [0x14, 0x54, 0x00, 0x0C, 0x00, 0x64]
        c2 = sum(out_of_bounds_body) & 0xFF
        out = f"Length 7: {' '.join(f'{x:02X}' for x in out_of_bounds_body+[c2])}"
        self.assertEqual([], parse_portmon_map_k_writes(out))

    def test_k_history_requires_causal_proof_envelope(self):
        valid = {
            "confirmed": True,
            "row": 4,
            "column": 4,
            "before": 142,
            "after": 145,
            "readback": 145,
            "batchFinalized": True,
            "finalMapHash": "abc123",
            "adjustmentId": "adj-secret",
            "timestampMs": 12345,
            "rpm": 3000,
            "petrolMs": 4.5,
        }
        rows = normalize_confirmed_k_history([valid])
        self.assertEqual(1, len(rows))
        self.assertNotEqual("adj-secret", rows[0]["adjustment_key"])
        bad = dict(valid, readback=144)
        with self.assertRaises(ValueError):
            normalize_confirmed_k_history([bad])

    def test_compressed_episode_fixture_is_deterministic(self):
        e = Episode("abc", 1, "GNV", 10, 20, 2000, 0.5, 4.0, 1, 12, 12)
        a = deterministic_gzip_jsonl_bytes([e])
        b = deterministic_gzip_jsonl_bytes([e])
        self.assertEqual(a, b)
        import gzip
        payload = gzip.decompress(a).decode("utf-8")
        self.assertIn('"session_key":"abc"', payload)

    def test_trajectory_break_cannot_be_merged_back_into_same_episode(self):
        frames = []
        for i in range(10):
            frames.append({"t_ms": i * 150, "fuel": "GNV", "rpm": 2000, "map_bar": 0.5, "petrol_ms": 4.0})
        base = frames[-1]["t_ms"] + 2000
        for i in range(10):
            frames.append({"t_ms": base + i * 150, "fuel": "GNV", "rpm": 2000, "map_bar": 0.5, "petrol_ms": 4.0})
        eps = derive_episodes_from_frames(frames, session_key="abc", order=1)
        self.assertEqual(2, len(eps))

    def test_single_pass_processing_fails_closed_on_corrupt_declared_hash(self):
        event = {
            "type": "telemetry",
            "recordedAtMs": 1000,
            "data": {
                "telemetry_scale_schema": "mp48-progbase-v2",
                "fuel": "GASOLINA",
                "plausible": True,
                "rpm": 2000,
                "load_bar": 0.5,
                "petrol_ms": 4.0,
            },
        }
        event_bytes = (json.dumps(event) + "\n").encode()
        summary = {"files": [{"path": "events_0001.jsonl", "bytes": len(event_bytes), "sha256": "0" * 64}]}
        bio = io.BytesIO()
        with zipfile.ZipFile(bio, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("events_0001.jsonl", event_bytes)
            z.writestr("export_summary.json", json.dumps(summary))
        c = SessionCandidate("s", "s.zip", "sha", len(event_bytes), 1, "8.0.0-test-debug", package_bytes=bio.getvalue())
        with self.assertRaises(ValueError):
            process_session_candidate(c, order=0)


if __name__ == "__main__":
    unittest.main()