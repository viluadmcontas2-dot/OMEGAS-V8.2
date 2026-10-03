import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORRELATOR = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/NativeAutoCalAnchorCorrelator.kt'
MONITOR = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt'
ENGINE = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt'
WINDOW = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/NativeAnchorTelemetryWindow.kt'


class NativeAnchorScienceContract(unittest.TestCase):
    def setUp(self):
        self.correlator = CORRELATOR.read_text('utf-8')
        self.monitor = MONITOR.read_text('utf-8')
        self.engine = ENGINE.read_text('utf-8')
        self.window = WINDOW.read_text('utf-8')

    def test_correlation_is_bounded_and_uses_no_frame_count_confidence_bonus(self):
        self.assertIn('private val maxFrames: Int = 256', self.window)
        self.assertIn('private val maxAgeMs: Long = 10_000L', self.window)
        self.assertIn('active.breakingGapMs', self.correlator)
        self.assertIn('active.petrolOscillationPercent', self.correlator)
        self.assertIn('active.mapOscillationBar', self.correlator)
        self.assertIn('active.rpmOscillationPercent', self.correlator)
        self.assertNotIn('requiredFrames', self.correlator)
        confidence_formula = self.correlator.split('val confidence = (', 1)[1].split('val support =', 1)[0]
        self.assertNotIn('fresh.size', confidence_formula)
        self.assertNotIn('matchedFrames', confidence_formula)
        self.assertNotIn('support', confidence_formula)
        self.assertIn('Quantidade de frames não participa da confiança', self.correlator)

    def test_refusal_is_explicit_and_reaches_native_event(self):
        self.assertIn('state = "NO_RELIABLE_CORRELATION"', self.correlator)
        self.assertIn('reason = reason', self.correlator)
        for reason in (
            'SESSION_MISMATCH',
            'IMPLAUSIBLE_TELEMETRY',
            'FUEL_MISMATCH',
            'CONTINUITY_GAP',
            'OUTSIDE_NATIVE_TOLERANCE',
            'STALE_CORRELATION',
            'RPM_AMBIGUITY',
            'PETROL_UNSTABLE',
            'MAP_UNSTABLE',
        ):
            self.assertIn(reason, self.correlator)
        self.assertIn('.put("correlationReason", correlation.reason)', self.monitor)

    def test_anchor_path_has_no_writer_or_second_serial_authority(self):
        for source in (self.correlator, self.window):
            self.assertNotIn('Mp48WorkClass.MANUAL_WRITE', source)
            self.assertNotIn('protocolTransaction(', source)
            self.assertNotIn('KWriteManager', source)
            self.assertNotIn('KFactorManager', source)
            self.assertNotIn('Executors.', source)
            self.assertNotIn('Thread(', source)
        self.assertIn('nativeTelemetryWindow.record(', self.engine)


if __name__ == '__main__':
    unittest.main()
