#!/usr/bin/env python3
"""Contratos do replay original LOGNOVO sem o arquivo privado no CI."""
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.omegas.lognovo_live_replay import (RAW_SHA, ZIP_SHA, SCHEMA, payload,
                                               decode_live, iter_events, iter_transactions)

SAMPLE = ROOT / "tests/fixtures/portmon-autocal-real-sample.json"
EPOCHS = ROOT / "tests/fixtures/portmon-lognovo-autocal-epochs-v1.json"


class LognovoRealReplayContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference = json.loads(SAMPLE.read_text(encoding="utf-8"))["transactions"]
        cls.epochs = json.loads(EPOCHS.read_text(encoding="utf-8"))

    def test_provenance_matches_original_epoch_oracle(self):
        p = self.epochs["provenance"]
        self.assertEqual(p["sourceRawSha256"], RAW_SHA)
        self.assertEqual(p["sourceZipSha256"], ZIP_SHA)
        self.assertEqual(SCHEMA, "omegas.lognovo.real-live.v1")

    def test_real_mp48_frame_has_expected_physical_scales(self):
        first = self.reference[0]
        frame = decode_live(first["request"], first["response"])
        self.assertIsNotNone(frame)
        self.assertEqual(frame["rpm"], 869)
        self.assertEqual(frame["fuel"], "GASOLINA")
        self.assertAlmostEqual(frame["petrol_ms"], 1772 * 0.00256, places=5)
        self.assertTrue(0.0 <= frame["load_bar"] <= 2.5)
        self.assertIsNone(frame["gas_ms_diagnostic"])

    def test_corrupted_telemetry_must_not_become_fake_measurement(self):
        row = self.reference[0]
        self.assertIsNone(decode_live(row["request"], row["response"][:-2]+"00"))
        self.assertIsNone(decode_live(row["request"], row["response"][:-3]))
        self.assertIsNone(payload("48 01 49", "48 01 49 53 22", 34))

    def test_status_counter_from_original_epochs_is_authoritative(self):
        transitions = self.epochs["epochs"]
        self.assertEqual([e["autoMatchCountAfter"] for e in transitions], [1, 2, 3])
        for epoch in transitions:
            row = epoch["statusAfter"]
            data = payload(row["request"], row["response"], 14)
            self.assertIsNotNone(data)
            self.assertEqual(data[13], epoch["autoMatchCountAfter"])

    def test_can_stream_original_portmon_event_format(self):
        row = self.reference[0]
        response = row["response"]
        req = row["request"]
        log = [
            f"700  0.00000000  ProgBase.exe  IRP_MJ_WRITE  Silabser0  Length 3: {req}\n",
            "700  0.00005001  SUCCESS  \n",
            f"701  0.00000000  ProgBase.exe  IRP_MJ_READ  Silabser0  Length 40\n",
            f"701  0.00000223  SUCCESS  Length 40: {response}\n",
        ]
        results = list(iter_transactions(iter_events(log)))
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].request_hex, req)
        self.assertEqual(results[0].response_hex, response)
        self.assertEqual(decode_live(results[0].request_hex, results[0].response_hex)["rpm"],869)


if __name__ == "__main__":
    unittest.main()
