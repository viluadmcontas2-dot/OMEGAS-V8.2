import json
from pathlib import Path
from statistics import median
import unittest

from tools.omegas_amarelo.build_portmon_replay_fixture import build_fixture
from tools.omegas_amarelo.portmon_transactions import response_payload

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures"
MANIFEST = FIXTURES / "amarelo-portmon-replay-manifest-v1.json"


def cadence_from_compact(rows, request, max_gap_ms):
    stamps = [row["at_ms"] for row in rows if row["request"] == request]
    deltas = [
        b - a
        for a, b in zip(stamps, stamps[1:])
        if 0.0 < (b - a) <= max_gap_ms
    ]
    return median(deltas) if deltas else None


def assert_strictly_increasing(testcase, values):
    testcase.assertTrue(all(a < b for a, b in zip(values, values[1:])))


class AmareloWu003ReplayKitTest(unittest.TestCase):
    def test_builder_is_deterministic_and_matches_canonical_autocal_clock(self):
        raw = FIXTURES / "portmon-autocal-head-real.log"
        first = build_fixture(raw, window_ms=500.0)
        second = build_fixture(raw, window_ms=500.0)
        self.assertEqual(first, second)

        canonical = json.loads(
            (FIXTURES / "portmon-autocal-cycle-v1.json").read_text(encoding="utf-8")
        )["transactions"]
        self.assertGreaterEqual(len(first["transactions"]), 3)
        for actual, expected in zip(first["transactions"][:3], canonical[:3]):
            self.assertEqual(actual["request"], expected["request"])
            self.assertEqual(actual["response"], expected["response"])
            self.assertAlmostEqual(actual["at_ms"], expected["at_ms"], places=3)

        self.assertEqual(
            first["timingRule"],
            "transaction.at_ms = cumulative duration of every completed Portmon operation "
            "before the IRP_MJ_WRITE that starts the transaction",
        )
        self.assertEqual(
            first["recipe"]["builder"],
            "tools/omegas_amarelo/build_portmon_replay_fixture.py",
        )

    def test_manifest_promotes_both_raw_authorities_to_proven_replay(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["schema"], "omegas.amarelo.portmon-replay-manifest.v1")

        autocal = manifest["corpora"]["PortmonAUTOCAL"]
        lognovo = manifest["corpora"]["PortmonLOGNOVO"]

        self.assertEqual(
            autocal["sourceRawSha256"],
            "4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b",
        )
        self.assertEqual(
            lognovo["sourceRawSha256"],
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
        )
        self.assertEqual(autocal["status"], "PROVEN_COMPACT_REPLAY")
        self.assertEqual(lognovo["status"], "PROVEN_COMPACT_REPLAY")
        self.assertTrue(manifest["closure"]["closureAllowed"])

        self.assertTrue((ROOT / autocal["compactReplay"]).is_file())
        self.assertTrue((ROOT / lognovo["compactReplay"]).is_file())
        self.assertTrue((ROOT / lognovo["byteEvidence"]).is_file())
        self.assertTrue((ROOT / lognovo["referenceBytes"]).is_file())
        self.assertEqual(lognovo["provenance"]["timingReplay"], "PROVEN_RAW_DERIVED")
        self.assertEqual(lognovo["observedWriteCount"], 39517)
        self.assertEqual(lognovo["observedUniqueCommands"], 567)
        self.assertEqual(
            lognovo["compactReplaySha256"],
            "62001a5c38206466e1444b24438eba608440c0192b9e923721e0504c446de70e",
        )

    def test_lognovo_reference_bytes_are_original_derived_and_do_not_claim_timing(self):
        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-autocal-reference-v1.json").read_text(encoding="utf-8")
        )
        self.assertEqual(fixture["classification"], "ORIGINAL_DERIVED")
        provenance = fixture["provenance"]
        self.assertEqual(
            provenance["sourceRawSha256"],
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
        )
        self.assertIn("timing is not claimed as an original measurement", provenance["extractionRule"])
        self.assertEqual(len(fixture["transactions"]), 5)
        for row in fixture["transactions"]:
            request = bytes.fromhex(row["request"])
            response = bytes.fromhex(row["response"])
            self.assertTrue(response.startswith(request))
            self.assertGreater(len(response_payload(request, response)), 0)
            self.assertNotIn("at_ms", row)

    def test_existing_autocal_replay_has_real_cadence_and_slow_families(self):
        fixture = json.loads(
            (FIXTURES / "portmon-autocal-cycle-v1.json").read_text(encoding="utf-8")
        )
        self.assertEqual(fixture["observedWriteCount"], 36463)
        self.assertEqual(fixture["observedUniqueCommands"], 21)
        counts = fixture["commandCounts"]
        self.assertGreater(counts["48 01 49"], 20_000)
        for command in (
            "29 5B 01 85",
            "29 5C 01 86",
            "29 5F 01 89",
            "29 60 01 8A",
            "29 61 01 8B",
            "29 6F 01 99",
            "29 70 01 9A",
            "29 8D 01 B7",
            "29 8E 01 B8",
        ):
            self.assertGreater(counts[command], 0)

        rows = fixture["transactions"]
        assert_strictly_increasing(self, [row["sequence"] for row in rows])
        assert_strictly_increasing(self, [row["at_ms"] for row in rows])
        live_median = cadence_from_compact(rows, "48 01 49", 200.0)
        self.assertIsNotNone(live_median)
        self.assertGreater(live_median, 35.0)
        self.assertLess(live_median, 65.0)

        autocal_median = cadence_from_compact(rows, "29 5B 01 85", 10_000.0)
        self.assertIsNotNone(autocal_median)
        self.assertGreater(autocal_median, 1_500.0)
        self.assertLess(autocal_median, 2_500.0)

        reference_median = cadence_from_compact(rows, "29 8D 01 B7", 10_000.0)
        self.assertIsNotNone(reference_median)
        self.assertGreater(reference_median, 3_000.0)
        self.assertLess(reference_median, 5_000.0)

    def test_lognovo_replay_preserves_raw_cadence_ordering_and_control_edges(self):
        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-replay-v1.json").read_text(encoding="utf-8")
        )
        self.assertEqual(fixture["schema"], "omegas.amarelo.portmon-replay.v1")
        self.assertEqual(
            fixture["sourceRawSha256"],
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
        )
        self.assertEqual(fixture["observedWriteCount"], 39517)
        self.assertEqual(fixture["observedUniqueCommands"], 567)
        self.assertEqual(fixture["recipe"]["initialTimingWindowMs"], 20_000.0)

        rows = fixture["transactions"]
        self.assertGreater(len(rows), 900)
        assert_strictly_increasing(self, [row["sequence"] for row in rows])
        assert_strictly_increasing(self, [row["portmon_index"] for row in rows])
        assert_strictly_increasing(self, [row["at_ms"] for row in rows])

        counts = fixture["commandCounts"]
        self.assertEqual(counts["48 01 49"], 20451)
        self.assertEqual(counts["09 74 01 7E"], 7)
        self.assertEqual(counts["12 4A 01 01 5E"], 4)
        self.assertEqual(counts["12 4A 01 00 5D"], 4)
        self.assertEqual(counts["02 24 04 04 2E"], 1)

        metrics = fixture["metrics"]
        self.assertAlmostEqual(metrics["liveMedianMs"], 59.628, places=3)
        for request in (
            "29 5B 01 85",
            "29 5C 01 86",
            "29 5F 01 89",
            "29 60 01 8A",
            "29 61 01 8B",
            "29 6F 01 99",
            "29 70 01 9A",
            "48 0B 53",
        ):
            self.assertGreater(metrics["slowMedianMs"][request], 1_500.0)
            self.assertLess(metrics["slowMedianMs"][request], 2_500.0)
        for request in ("29 8D 01 B7", "29 8E 01 B8"):
            self.assertGreater(metrics["slowMedianMs"][request], 3_500.0)
            self.assertLess(metrics["slowMedianMs"][request], 4_500.0)

        reasons = {reason for row in rows for reason in row["selectionReasons"]}
        self.assertIn("CONTROL_EVENT", reasons)
        self.assertIn("AUTOMATCH_COUNT_EDGE", reasons)
        self.assertIn("INITIAL_TIMING_WINDOW", reasons)


if __name__ == "__main__":
    unittest.main()
