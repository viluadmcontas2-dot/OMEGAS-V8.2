import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"


def function_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    raise AssertionError(f"unclosed function: {signature}")


class Owner116EventDrivenSnapshotContract(unittest.TestCase):
    def test_render_status_path_never_triggers_full_snapshot(self):
        source = MONITOR.read_text("utf-8")
        status = function_body(source, "fun statusJson()")
        latest = function_body(source, "fun latestSnapshotJson()")
        for _ in range(1000):
            self.assertNotIn("readFullSnapshot(", status)
            self.assertNotIn("requestSnapshot(", status)
            self.assertNotIn("serial.transaction(", status)
            self.assertNotIn("readFullSnapshot(", latest)
            self.assertNotIn("requestSnapshot(", latest)
            self.assertNotIn("serial.transaction(", latest)

    def test_full_snapshot_is_guarded_by_material_request(self):
        source = MONITOR.read_text("utf-8")
        tick = function_body(source, "fun tick()")
        self.assertRegex(
            tick,
            re.compile(
                r"if \(synchronized\(lock\) \{ snapshotRequested \}\) \{\s*readFullSnapshot\(currentSession, probe, countIncreased\)",
                re.S,
            ),
        )
        self.assertIn("snapshotReason = when {", tick)
        self.assertIn('"NATIVE_PETROL_BAND_MATURED"', tick)
        self.assertIn('"NATIVE_CNG_BAND_MATURED"', tick)
        self.assertIn('else -> "NATIVE_BAND_MATURED"', tick)
        self.assertIn('"AUTOMATCH_COUNT_CHANGED"', tick)
        self.assertIn('"NATIVE_STATUS_CHANGED"', tick)

    def test_manual_and_reconcile_requests_are_explicit_not_render_driven(self):
        source = MONITOR.read_text("utf-8")
        manual = function_body(source, "fun onManualActionConfirmed(")
        request = function_body(source, "fun requestSnapshot(")
        self.assertIn('requestSnapshot("ACTION_${receipt.optString("action", "UNKNOWN")}")', manual)
        self.assertIn("snapshotRequested = true", request)
        self.assertIn("snapshotReason = reason.take(80)", request)
        self.assertNotIn("render", request.lower())
        self.assertNotIn("route", request.lower())
        self.assertNotIn("screen", request.lower())


if __name__ == "__main__":
    unittest.main()
