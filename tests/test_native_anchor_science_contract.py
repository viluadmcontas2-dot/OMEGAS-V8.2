import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORRELATOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeAutoCalEventCorrelator.kt'
ANCHOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeLearningAnchor.kt'
SIGNAL = ROOT / 'app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt'
MONITOR = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt'
PROJECTOR = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMaturityEventProjector.kt'
ENGINE = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt'
WINDOW = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeAnchorTelemetryWindow.kt'


class NativeAnchorScienceContract(unittest.TestCase):
    def setUp(self):
        self.correlator = CORRELATOR.read_text('utf-8')
        self.anchor = ANCHOR.read_text('utf-8')
        self.signal = SIGNAL.read_text('utf-8')
        self.monitor = MONITOR.read_text('utf-8')
        self.projector = PROJECTOR.read_text('utf-8')
        self.engine = ENGINE.read_text('utf-8')
        self.window = WINDOW.read_text('utf-8')

    def test_correlation_is_bounded_and_uses_no_frame_count_confidence_bonus(self):
        self.assertIn('private val maxFrames: Int = 256', self.window)
        self.assertIn('private val maxAgeMs: Long = 10_000L', self.window)
        self.assertIn('active.breakingGapMs', self.correlator)
        self.assertIn('active.petrolCenterPercent', self.correlator)
        self.assertIn('active.mapCenterBar', self.correlator)
        self.assertIn('active.rpmOscillationPercent', self.correlator)
        self.assertNotIn('requiredFrames', self.correlator)
        confidence_formula = self.correlator.split('val confidence = (', 1)[1].split('val overlapKey =', 1)[0]
        self.assertNotIn('compatible.size', confidence_formula)
        self.assertNotIn('matchedFrames', confidence_formula)
        self.assertNotIn('countConfidence', confidence_formula)
        self.assertIn('Early-close não usa N/confidence target arbitrário', self.correlator)
        self.assertIn('compatible.size == inWindow.size', self.correlator)

    def test_refusal_is_explicit_and_reaches_projected_native_event(self):
        self.assertIn('state = "INCONCLUSIVE"', self.correlator)
        self.assertIn('reason = reason', self.correlator)
        for reason in (
            'INVALID_WINDOW',
            'NO_NATIVE_CONTEXT',
            'EMPTY_OR_FUEL_MISMATCH',
            'STALE_WINDOW',
            'CONTINUITY_GAP',
            'INSUFFICIENT_PHYSICAL_SUPPORT',
            'RPM_AMBIGUITY',
            'NO_CANDIDATE',
        ):
            self.assertIn(reason, self.correlator)
        self.assertIn('NativeAutoCalMaturityEventProjector.project(', self.monitor)
        self.assertIn('NativeAutoCalEventCorrelator.correlate(', self.projector)
        self.assertIn('.put("correlationReason", correlation.reason)', self.projector)
        self.assertIn('.put("rawOnly", correlation.state != "CORRELATED")', self.projector)

    def test_anchor_requires_real_correlated_dual_fuel_context_without_second_vote(self):
        self.assertIn('event.optString("correlationState") != "CORRELATED"', self.anchor)
        self.assertIn('sessionId <= 0L', self.anchor)
        self.assertIn('"PETROL", "GASOLINA" -> "PETROL"', self.anchor)
        self.assertIn('"GNV", "CNG" -> "GNV"', self.anchor)
        self.assertIn('matchedFrames < 2', self.anchor)
        self.assertIn('correlatedFrameElapsedMs', self.anchor)
        self.assertIn('lagMs', self.anchor)
        self.assertIn('.put("comparisonVote", false)', self.anchor)
        self.assertIn('.put("effectiveComparisonWeight", effectiveComparisonWeight)', self.anchor)
        self.assertIn('.put("automaticWrite", false)', self.anchor)

    def test_new_physical_fingerprint_revises_once_and_persists_through_store(self):
        self.assertIn('if (anchors.containsKey(anchor.fingerprint) || anchor.overlapKey in overlaps) return false', self.anchor)
        self.assertIn('nextRevision += 1L', self.anchor)
        self.assertIn('scientificRevision', self.anchor)
        self.assertIn('nativeLearningAnchors', self.signal)
        self.assertIn('nativeAnchors.upsert(anchor)', self.signal)
        self.assertIn('NativeLearningAnchor.fromJson', self.signal)
        self.assertIn('nativeAnchors.replaceAll', self.signal)

    def test_anchor_path_has_no_writer_or_second_serial_authority(self):
        for source in (self.anchor, self.correlator, self.projector, self.window):
            self.assertNotIn('Mp48WorkClass.MANUAL_WRITE', source)
            self.assertNotIn('protocolTransaction(', source)
            self.assertNotIn('KWriteManager', source)
            self.assertNotIn('KFactorManager', source)
            self.assertNotIn('Executors.', source)
            self.assertNotIn('Thread(', source)
        self.assertIn('nativeTelemetryWindow.record(', self.engine)
        self.assertNotIn('NativeAutoCalAnchorCorrelator', self.monitor)
        import_section = self.signal.split('fun importNativeSnapshot', 1)[1].split('fun onCalibrationAdjustment', 1)[0]
        self.assertNotIn('scheduleAdvisorRefresh', import_section)
        self.assertNotIn('delegate.ingest', import_section)
        self.assertNotIn('MANUAL_WRITE', import_section)


if __name__ == '__main__':
    unittest.main()
