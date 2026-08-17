from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/ui/app.js"
MAP = ROOT / "app/src/main/assets/ui/screens/map.js"
CURVE = ROOT / "app/src/main/assets/ui/screens/curve.js"
PREDICTOR = ROOT / "app/src/main/assets/ui/core/predictor-model.js"


def function_body(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


class UiRouteRuntimeIndependence(unittest.TestCase):
    def test_route_activation_contains_no_native_write_or_read_start_intent(self):
        source = APP.read_text("utf-8")
        body = function_body(source, "function activateRoute(route, context)", "router.onNavigate")
        for forbidden in (
            "startMapRead",
            "startCurveRead",
            "restartEngine",
            "connectUsb",
            "disconnectUsb",
            "applySuggestion",
            "startBatchWrite",
        ):
            self.assertNotIn(forbidden, body)

    def test_map_and_curve_on_enter_do_not_start_ecu_reads(self):
        map_source = MAP.read_text("utf-8")
        curve_source = CURVE.read_text("utf-8")
        map_body = function_body(map_source, "    onEnter(context) {", "    startRead(")
        curve_body = function_body(curve_source, "    onEnter(context) {", "    startRead(")
        self.assertNotIn("startRead(", map_body)
        self.assertNotIn("startMapRead", map_body)
        self.assertNotIn("startRead(", curve_body)
        self.assertNotIn("startCurveRead", curve_body)

    def test_predictor_formatter_has_no_scheduler_serial_or_writer(self):
        source = PREDICTOR.read_text("utf-8")
        for forbidden in (
            "setInterval",
            "setTimeout",
            "requestAnimationFrame",
            "OmegasNative",
            "startMapRead",
            "startCurveRead",
            "write",
            "applySuggestionToEcu",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
