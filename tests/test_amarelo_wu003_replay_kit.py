import json
from pathlib import Path
import unittest

from tools.omegas_amarelo.build_portmon_replay_fixture import build_fixture
from tools.omegas_amarelo.portmon_transactions import response_payload

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures"
MANIFEST = FIXTURES / "amarelo-portmon-replay-manifest-v1.json"


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

    def test_manifest_preserves_both_raw_authorities_without_claiming_closure(self):
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
        self.assertEqual(lognovo["status"], "PROVEN_BYTES_TIMING_REPLAY_PENDING_RAW_ACCESS")
        self.assertFalse(manifest["closure"]["closureAllowed"])

        self.assertTrue((ROOT / autocal["compactReplay"]).is_file())
        self.assertTrue((ROOT / lognovo["byteEvidence"]).is_file())
        self.assertTrue((ROOT / lognovo["referenceBytes"]).is_file())
        self.assertEqual(lognovo["provenance"]["timingReplay"], "PENDING")
        self.assertFalse((ROOT / lognovo["compactReplay"]).exists())


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


if __name__ == "__main__":
    unittest.main()
