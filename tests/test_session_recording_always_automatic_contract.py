import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
DRAWERS = ROOT / "app/src/main/assets/ui/components/drawers.js"


class SessionRecordingAlwaysAutomatic(unittest.TestCase):
    def test_service_starts_recording_on_usb_without_preferences(self):
        source = SERVICE.read_text("utf-8")
        self.assertIn("A gravação é sempre automática", source)
        self.assertNotIn("settings.sessionRecorderEnabled && settings.sessionRecorderAutoStartOnUsb", source)

    def test_ui_has_no_manual_start_stop_or_autostart_switch(self):
        ui = DRAWERS.read_text("utf-8")
        for marker in ("data-session-start", "data-session-stop", "data-session-autostart", "Iniciar sessão"):
            self.assertNotIn(marker, ui)


if __name__ == "__main__":
    unittest.main()
