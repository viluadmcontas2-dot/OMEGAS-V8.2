import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CalibrationWriteSafetyPolicy.kt"


class Block1SessionContract(unittest.TestCase):
    def setUp(self):
        self.html = (UI / "index.html").read_text("utf-8")
        self.app = (UI / "app.js").read_text("utf-8")
        self.scheduler = (UI / "core/scheduler.js").read_text("utf-8")
        self.dashboard = (UI / "screens/dashboard.js").read_text("utf-8")
        self.bridge = BRIDGE.read_text("utf-8")
        self.policy = POLICY.read_text("utf-8")
        self.css = (UI / "styles.css").read_text("utf-8")

    def test_workshop_mode_and_redundant_checkbox_are_removed_from_active_ui(self):
        active = self.html + self.app
        for marker in ('workshopModeButton', 'workshopRequested', 'confirmWriteCheckbox', 'Ative o modo oficina'):
            self.assertNotIn(marker, active)
        self.assertIn('Gravar alterações na ECU', self.html)
        self.assertIn('Gravar pontos na ECU', self.html)

    def test_native_gate_rejects_unsafe_write_conditions(self):
        for marker in (
            'MAX_SAFE_TELEMETRY_AGE_MS = 2_500L',
            'DRIVING_PROBABLE_RPM = 1_200',
            '!status.serviceRunning',
            '!status.usbConnected',
            'status.usbPermissionPending',
            '!status.engineRunning || !status.engineReady || status.engineStuck',
            'status.directTelemetryAgeMs < 0L || status.directTelemetryAgeMs > MAX_SAFE_TELEMETRY_AGE_MS',
            'status.rpm >= DRIVING_PROBABLE_RPM',
        ):
            self.assertIn(marker, self.policy)
        self.assertIn('CalibrationWriteSafetyPolicy.unsafeReason(service.status())', self.bridge)
        self.assertIn('.put("safetyBlocked", true)', self.bridge)

    def test_map_rechecks_safety_before_each_internal_chunk(self):
        self.assertGreaterEqual(self.bridge.count('unsafeCalibrationWriteReason(service)'), 3)
        self.assertIn('plan.chunks.forEachIndexed', self.bridge)
        self.assertIn('BATCH_PARTIAL_FAILED', self.bridge)

    def test_one_visual_scheduler_and_visibility_reuses_it(self):
        self.assertEqual(1, self.scheduler.count('setInterval('))
        self.assertNotIn('setInterval(', self.app)
        self.assertIn("document.addEventListener('visibilitychange'", self.app)
        self.assertIn('scheduler.stop()', self.app)
        self.assertIn('scheduler.start()', self.app)
        self.assertNotIn('MutationObserver', self.app + self.scheduler)

    def test_dashboard_makes_stale_expired_and_stuck_visible(self):
        for marker in ('stale', 'expired', 'engineStuck', 'Telemetria atrasada', 'Telemetria expirada', 'Comunicação travada'):
            self.assertIn(marker, self.dashboard)
        self.assertIn('Ajustes permanecem bloqueados até a condição normalizar', self.dashboard)

    def test_multimedia_1280x720_is_the_explicit_primary_surface(self):
        self.assertIn('--rail-width:202px', self.css)
        self.assertIn('grid-template-columns:var(--rail-width) minmax(0,1fr)', self.css)
        self.assertIn('O layout é intencionalmente fixado para a multimídia 1280×720', self.css)
        self.assertNotIn('@media (max-width:680px)', self.css.replace(' ', ''))
        self.assertIn('side-rail', self.css)

    def test_no_duplicate_ids_in_html(self):
        ids = re.findall(r'id="([^"]+)"', self.html)
        duplicates = sorted({item for item in ids if ids.count(item) > 1})
        self.assertEqual([], duplicates)


if __name__ == '__main__':
    unittest.main()
