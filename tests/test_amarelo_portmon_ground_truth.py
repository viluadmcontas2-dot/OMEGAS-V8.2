import json
from pathlib import Path
import unittest

from tools.omegas_amarelo.portmon_transactions import (
    additive_checksum_ok,
    iter_transactions,
    q14_values,
    response_payload,
    u16le_values,
    valid_echo_response,
    progbase_autocal_action_frame,
    PROGBASE_AUTOCAL_ACTION_CODES,
)

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures"

class AmareloPortmonGroundTruthTest(unittest.TestCase):
    def test_real_raw_excerpt_reconstructs_canonical_fixture_clock_and_bytes(self):
        raw_lines = (FIXTURES / "portmon-autocal-head-real.log").read_text(
            encoding="utf-8", errors="replace"
        ).splitlines()
        parsed = [tx for tx in iter_transactions(raw_lines) if valid_echo_response(tx)]
        canonical = json.loads(
            (FIXTURES / "portmon-autocal-cycle-v1.json").read_text(encoding="utf-8")
        )["transactions"]

        self.assertGreaterEqual(len(parsed), 3)
        for actual, expected in zip(parsed[:3], canonical[:3]):
            self.assertEqual(actual.request.hex(" ").upper(), expected["request"])
            self.assertEqual(actual.response.hex(" ").upper(), expected["response"])
            self.assertAlmostEqual(actual.at_ms, expected["at_ms"], places=3)

    def test_real_lognovo_max_rpm_and_automatch_counter_decode(self):
        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-control-v1.json").read_text(encoding="utf-8")
        )
        max_rpm = fixture["evidence"]["READ_MAX_RPM_AUTOCAL"][0]
        payload = response_payload(bytes.fromhex(max_rpm["request"]), bytes.fromhex(max_rpm["response"]))
        self.assertEqual(u16le_values(payload), (3000,))

        counts = []
        for row in fixture["evidence"]["READ_AUTOMATCH_COUNT"]:
            payload = response_payload(bytes.fromhex(row["request"]), bytes.fromhex(row["response"]))
            self.assertEqual(len(payload), 1)
            counts.append(payload[0])
        self.assertEqual(counts, [3, 0, 1, 2, 3])

    def test_real_lognovo_mul_act_reset_observation_is_q14_unity_after_candidate(self):
        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-control-v1.json").read_text(encoding="utf-8")
        )
        before, after = fixture["evidence"]["READ_MUL_ACT"]
        before_values = q14_values(response_payload(bytes.fromhex(before["request"]), bytes.fromhex(before["response"])))
        after_values = q14_values(response_payload(bytes.fromhex(after["request"]), bytes.fromhex(after["response"])))
        self.assertEqual(len(after_values), 30)
        self.assertTrue(any(abs(v - 1.0) > 1e-9 for v in before_values))
        self.assertTrue(all(abs(v - 1.0) < 1e-12 for v in after_values))

    def test_real_write_frames_have_valid_additive_checksums_and_echo_ack(self):
        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-control-v1.json").read_text(encoding="utf-8")
        )
        for key in ("ENABLE_AUTOCAL_0", "ENABLE_AUTOCAL_1", "ACTION_CODE_4_FRAME_CANDIDATE"):
            for row in fixture["evidence"][key]:
                request = bytes.fromhex(row["request"])
                response = bytes.fromhex(row["response"])
                self.assertTrue(additive_checksum_ok(request), key)
                self.assertEqual(response_payload(request, response), b"")

    def test_progbase_binary_proves_action_frame_family_without_claiming_wire_observation(self):
        expected = {
            "RESET_PETROL": "02 24 04 01 2B",
            "RESET_GAS": "02 24 04 02 2C",
            "RESET_ALL": "02 24 04 04 2E",
            "AUTO_MATCH": "02 24 04 08 32",
        }
        for name, code in PROGBASE_AUTOCAL_ACTION_CODES.items():
            self.assertEqual(
                progbase_autocal_action_frame(code).hex(" ").upper(),
                expected[name],
            )

        fixture = json.loads(
            (FIXTURES / "portmon-lognovo-control-v1.json").read_text(encoding="utf-8")
        )
        observed = fixture["evidence"]["ACTION_CODE_4_FRAME_CANDIDATE"][0]["request"]
        self.assertEqual(
            progbase_autocal_action_frame(PROGBASE_AUTOCAL_ACTION_CODES["RESET_ALL"]).hex(" ").upper(),
            observed,
        )

if __name__ == "__main__":
    unittest.main()
