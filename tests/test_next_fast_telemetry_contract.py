#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt"


class NextFastTelemetryContractTest(unittest.TestCase):
    def test_fast_payload_has_only_present_time_operational_fields(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index("fun fastLiveJson(): String")
        end = source.index("\n    fun fullJson(): String", start)
        method = source[start:end]

        required = [
            '"sequence"',
            '"capturedAtMs"',
            '"ageMs"',
            '"valid"',
            '"sessionId"',
            '"rpm"',
            '"petrolMs"',
            '"gasMsDiagnostic"',
            '"mapBar"',
            '"fuel"',
            '"engineState"',
        ]
        for token in required:
            self.assertIn(token, method)

        forbidden = [
            "history",
            "fullSnapshot",
            "regions",
            "comparisons",
            "advisor",
            "predictor",
            'put("runtime"',
            'put("gps"',
        ]
        for token in forbidden:
            self.assertNotIn(token, method)

    def test_legacy_and_full_payloads_remain_separate(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("fun liveJson(): String", source)
        self.assertIn("fun fullJson(): String", source)
        self.assertIn("fun fastLiveJson(): String", source)
        self.assertIn('put("history"', source)
        self.assertIn('put("native_history"', source)


if __name__ == "__main__":
    unittest.main()
