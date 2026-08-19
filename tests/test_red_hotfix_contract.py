#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


class RedHotfixContractTest(unittest.TestCase):
    def test_serial_backpressure_wrapper_is_bounded_and_threadless(self):
        wrapper_path = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt"
        policy_path = ROOT / "app/src/main/java/com/omegas/prohub/util/RuntimeBackpressurePolicy.kt"
        self.assertTrue(wrapper_path.is_file(), "RED exige Mp48BackpressureScheduler")
        self.assertTrue(policy_path.is_file(), "RED exige RuntimeBackpressurePolicy")

        wrapper = wrapper_path.read_text(encoding="utf-8")
        policy = policy_path.read_text(encoding="utf-8")
        runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")

        self.assertIn("class Mp48BackpressureScheduler", wrapper)
        self.assertIn(": Mp48SerialScheduler", wrapper)
        self.assertIn("Semaphore", wrapper)
        self.assertIn("tryAcquire()", wrapper, "READ_ONLY precisa admission não bloqueante")
        self.assertRegex(
            wrapper,
            r"tryAcquire\(waitTimeoutMs,\s*TimeUnit\.MILLISECONDS\)",
            "lane crítica precisa bounded wait",
        )
        for forbidden in ("Executors.", "Thread(", "ArrayDeque", "UsbSerialManager("):
            self.assertNotIn(forbidden, wrapper, f"wrapper não pode criar runtime/transporte: {forbidden}")

        self.assertIn("SECONDARY_READ_PENDING_CAPACITY", policy)
        self.assertIn("CRITICAL_SERIAL_RESERVED_CAPACITY", policy)
        self.assertIn("private val serialAdmission = Mp48BackpressureScheduler(engine)", runtime)
        self.assertIn("fun serialScheduler(): Mp48SerialScheduler = serialAdmission", runtime)
        self.assertIn("serialAdmission.metricsJson()", runtime)

    def test_internal_webview_float_cannot_boot(self):
        index = read("app/src/main/assets/ui/index.html")
        app = read("app/src/main/assets/ui/app.js")
        shipped_boot = index + "\n" + app
        self.assertNotIn("floating-telemetry.js", shipped_boot)
        self.assertNotIn("FloatingTelemetry(", shipped_boot)
        self.assertNotRegex(shipped_boot, r"new\s+ui\.FloatingTelemetry")

    def test_official_overlay_is_legible_and_stays_observational(self):
        overlay = read("app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt")
        self.assertIn('minWidth = dp(56)', overlay)
        self.assertIn('minHeight = dp(56)', overlay)
        self.assertRegex(overlay, r'textSize\s*=\s*13f')
        self.assertIn('now - lastDrawAt < 250L', overlay, "não remover throttling de desenho")
        self.assertIn('observationalOnly', overlay)
        for forbidden in ("KWriteManager", "ExistingCalibrationWriter", "writeKCell(", "writeKFactor"):
            self.assertNotIn(forbidden, overlay)

    def test_dashboard_promotes_petrol_injection_without_new_polling(self):
        dashboard = read("app/src/main/assets/ui/screens/dashboard.js")
        app = read("app/src/main/assets/ui/app.js")
        self.assertIn('PETROL INJECTION', dashboard)
        self.assertIn('id="dashHeroPetrol"', dashboard)
        self.assertLess(
            dashboard.index('id="dashHeroPetrol"'),
            dashboard.index('id="dashHeroRpm"'),
            "Petrol Injection deve preceder RPM na hierarquia hero",
        )
        self.assertNotIn("setInterval", dashboard, "Dashboard não ganha polling próprio")
        self.assertIn("intervalMs: 200", app)

    def test_tools_heavy_payloads_remain_route_gated(self):
        app = read("app/src/main/assets/ui/app.js")
        match = re.search(
            r"if \(route === 'tools'\) \{(?P<body>.*?)\n\s*\}",
            app,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(match, "Tools deve possuir bloco de contexto dedicado")
        body = match.group("body")
        self.assertIn("api.sessions()", body)
        self.assertIn("api.logs()", body)
        outside = app[: match.start()] + app[match.end() :]
        self.assertNotIn("api.sessions()", outside)
        self.assertNotIn("api.logs()", outside)


if __name__ == "__main__":
    unittest.main()
