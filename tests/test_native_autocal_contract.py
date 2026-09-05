import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt'
SCALE = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/AutoCalScale.kt'
SCHEDULER = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt'
ENGINE = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt'
ACTION = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt'
BRIDGE = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'
ACQ = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt'
MONITOR = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt'
MATURITY = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMaturityTracker.kt'
SERVICE = ROOT / 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'
LEARNING = ROOT / 'app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt'
SIGNAL = ROOT / 'app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt'
WINDOW = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeAnchorTelemetryWindow.kt'
CORRELATOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeAutoCalAnchorCorrelator.kt'
ANCHOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/NativeLearningAnchor.kt'

class NativeAutoCalContract(unittest.TestCase):
    def setUp(self):
        self.protocol = PROTOCOL.read_text('utf-8')
        self.action = ACTION.read_text('utf-8')
        self.bridge = BRIDGE.read_text('utf-8')
        self.acq = ACQ.read_text('utf-8')
        self.monitor = MONITOR.read_text('utf-8')
        self.maturity = MATURITY.read_text('utf-8')
        self.service = SERVICE.read_text('utf-8')
        self.learning = LEARNING.read_text('utf-8')
        self.scheduler = SCHEDULER.read_text('utf-8')
        self.engine = ENGINE.read_text('utf-8')
        self.signal = SIGNAL.read_text('utf-8')
        self.window = WINDOW.read_text('utf-8')
        self.correlator = CORRELATOR.read_text('utf-8')
        self.anchor = ANCHOR.read_text('utf-8')

    def test_manual_automatch_route_is_removed(self):
        self.assertNotIn('NATIVE_AUTOMATCH', self.action)
        self.assertNotIn('NATIVE_AUTOMATCH', self.bridge)
        self.assertNotIn('02 24 04 08 32', self.action)
        self.assertIn('legacyDraftEngine", false', self.bridge)
        self.assertIn('decisionAuthority", "BLUE_CAUSAL_ENGINE', self.bridge)

    def test_enable_disable_and_status_are_exact_portmon_frames(self):
        self.assertIn('CMD_NATIVE_STATUS = byteArrayOf(0x48, 0x0B, 0x53)', self.protocol)
        self.assertIn('fun setEnabled(enabled: Boolean)', self.protocol)
        self.assertIn('WRITE_U8 = 0x12', self.protocol)
        self.assertIn('ENABLE_AUTO_CAL', self.action)
        self.assertIn('DISABLE_AUTO_CAL', self.action)
        self.assertIn('expectedEnableReadback', self.action)
        self.assertIn('validateActionReadback', self.action)
        self.assertIn('.put("readbackValid", true)', self.action)

    def test_max_automatch_is_semantic_and_not_threshold(self):
        self.assertIn('val MAX_AUTOMATCH = Field("MAX_AUTOMATCH", 0x0165', self.protocol)
        self.assertIn('val VECT_AUTOCAL_U8_2 = MAX_AUTOMATCH', self.protocol)
        self.assertIn('fields["MAX_AUTOMATCH"] ?: fields["VECT_AUTOCAL_U8_2"]', self.acq)
        threshold_section = self.acq.split('val threshold = when {', 1)[1].split('val state = when {', 1)[0]
        self.assertNotIn('maxAutomatch', threshold_section)

    def test_monitor_uses_existing_health_tick_and_event_driven_snapshot(self):
        self.assertNotIn('Executors.', self.monitor)
        self.assertNotIn('ScheduledExecutor', self.monitor)
        self.assertNotIn('Thread(', self.monitor)
        self.assertIn('AutoCalProtocol.CMD_NATIVE_STATUS', self.monitor)
        self.assertIn('AUTOMATCH_COUNT_CHANGED', self.monitor)
        self.assertIn('snapshotRequested', self.monitor)
        self.assertIn('nativeAutoCal.tick()', self.service)
        self.assertIn('scheduleWithFixedDelay(::healthTick, 200L, 3000L', self.service)

    def test_native_maturity_is_read_only_banded_monotonic_and_deduplicated(self):
        self.assertIn('AutoCalProtocol.NUM_BUF_UPD_GAS', self.monitor)
        self.assertIn('reason = "AutoCal maturidade GNV"', self.monitor)
        self.assertIn('workClass = Mp48WorkClass.READ_ONLY', self.monitor)
        self.assertIn('SystemClock.elapsedRealtime()', self.monitor)
        self.assertIn('NATIVE_BAND_MATURED', self.monitor)
        self.assertIn('nativeMaturityEvents', self.monitor)
        self.assertIn('counterPayloadHex', self.monitor)
        self.assertIn('before < threshold && after >= threshold', self.maturity)
        self.assertIn('if (previous == null || !enabled) return emptyList()', self.maturity)
        self.assertNotIn('Thread(', self.maturity)
        self.assertNotIn('Executors.', self.maturity)

    def test_anchor_window_is_bounded_session_aware_and_pre_visual(self):
        self.assertIn('private val maxFrames: Int = 256', self.window)
        self.assertIn('private val maxAgeMs: Long = 10_000L', self.window)
        self.assertIn('val sessionId: Long = 0L', self.window)
        self.assertIn('val gasMsDiagnostic: Double? = null', self.window)
        self.assertIn('val plausible: Boolean = true', self.window)
        self.assertIn('nativeTelemetryWindow.record(', self.engine)
        self.assertIn('sessionId = physicalSessionId', self.engine)
        self.assertIn('gasMsDiagnostic = decoded.gasMsDiagnostic', self.engine)
        self.assertIn('plausible = decoded.plausible', self.engine)
        self.assertIn('nativeTelemetryWindow.reset()', self.engine)
        self.assertIn('recentTelemetryFrames(', self.scheduler)

    def test_anchor_correlation_uses_real_gnv_same_session_and_never_invents_position(self):
        self.assertIn('plausible.filter { it.fuel in setOf("GNV", "CNG") }', self.correlator)
        self.assertIn('sameSession.filter { it.plausible }', self.correlator)
        self.assertIn('frames.filter { it.sessionId == sessionId }', self.correlator)
        self.assertIn('NO_RELIABLE_CORRELATION', self.correlator)
        self.assertIn('correlatedFrameElapsedMs', self.correlator)
        self.assertIn('gasMsDiagnostic', self.correlator)
        self.assertIn('sessionId = expectedSessionId', self.monitor)
        self.assertIn('correlatedGasMs', self.monitor)
        self.assertIn('correlatedFuel', self.monitor)
        self.assertIn('correlatedFrameElapsedMs', self.monitor)

    def test_native_learning_anchor_requires_reliable_correlation_and_has_no_writer(self):
        self.assertIn('if (event.optString("correlationState") != "CORRELATED") return null', self.anchor)
        self.assertIn('require(fuel == "GNV")', self.anchor)
        self.assertIn('.put("comparisonVote", false)', self.anchor)
        self.assertIn('.put("automaticWrite", false)', self.anchor)
        self.assertIn('scientificRevision', self.anchor)
        self.assertIn('if (anchors.containsKey(anchor.fingerprint)) return false', self.anchor)
        self.assertIn('nextRevision += 1L', self.anchor)
        self.assertNotIn('protocolTransaction(', self.anchor)
        self.assertNotIn('Mp48WorkClass.MANUAL_WRITE', self.anchor)
        self.assertNotIn('KWriteManager', self.anchor)
        self.assertNotIn('KFactorManager', self.anchor)

    def test_anchor_propagates_only_through_learning_sidecar_without_double_vote(self):
        self.assertIn('nativeLearningAnchors', self.signal)
        self.assertIn('NativeLearningAnchor.fromMaturityEvent', self.signal)
        self.assertIn('nativeAnchors.upsert(anchor)', self.signal)
        self.assertIn('nativeAnchors.clear()', self.signal)
        import_section = self.signal.split('fun importNativeSnapshot', 1)[1].split('fun onCalibrationAdjustment', 1)[0]
        self.assertNotIn('scheduleAdvisorRefresh', import_section)
        self.assertNotIn('delegate.ingest', import_section)
        self.assertNotIn('previewKWrite', import_section)
        self.assertNotIn('MANUAL_WRITE', import_section)

    def test_paused_snapshot_is_not_fresh_learning_and_native_epoch_requires_readback(self):
        self.assertIn('AUTOCAL_PAUSED_SNAPSHOT', self.learning)
        self.assertIn('enabled == 0', self.learning)
        self.assertIn('payload.optString("source") == "ECU_NATIVE_AUTOCAL"', self.learning)
        self.assertIn('payload.optBoolean("ecuNativeObserved", false)', self.learning)
        self.assertIn('!payload.optBoolean("appWritePerformed", true)', self.learning)
        self.assertIn('readbackValid', self.learning)
        self.assertIn('ECU_NATIVE_AUTOCAL_EPOCH', self.learning)

    def test_actual_protocol_kotlin_frames_and_status_decoder(self):
        with tempfile.TemporaryDirectory(prefix='autocal-protocol-contract-') as tmp:
            tmp = Path(tmp)
            (tmp/'Mp48Protocol.kt').write_text(textwrap.dedent('''
                package com.omegas.prohub.ecu
                object Mp48Protocol {
                    const val STATUS_ACK = 0x53
                    fun frame(body: ByteArray): ByteArray = body + byteArrayOf(checksum(body).toByte())
                    fun checksum(bytes: ByteArray): Int = bytes.sumOf { it.toInt() and 0xFF } and 0xFF
                }
            '''), encoding='utf-8')
            (tmp/'Main.kt').write_text(textwrap.dedent('''
                import com.omegas.prohub.ecu.AutoCalProtocol
                fun ByteArray.hex() = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                fun main() {
                    check(AutoCalProtocol.setEnabled(true).hex() == "12 4A 01 01 5E")
                    check(AutoCalProtocol.setEnabled(false).hex() == "12 4A 01 00 5D")
                    check(AutoCalProtocol.CMD_NATIVE_STATUS.hex() == "48 0B 53")
                    val payload = ByteArray(14)
                    payload[12] = 1
                    payload[13] = 3
                    val decoded = AutoCalProtocol.decodeNativeStatus(0x53, payload)
                    check(decoded.nativeFlag13 == 1)
                    check(decoded.autoMatchCount == 3)
                    check(AutoCalProtocol.MAX_AUTOMATCH.address == 0x0165)
                    check(AutoCalProtocol.MAX_AUTOMATCH.index == 2)
                    println("NATIVE_AUTOCAL_PROTOCOL=PASS")
                }
            '''), encoding='utf-8')
            jar = tmp/'test.jar'
            compile_cmd = ['kotlinc', str(PROTOCOL), str(SCALE), str(tmp/'Mp48Protocol.kt'), str(tmp/'Main.kt'), '-include-runtime', '-d', str(jar)]
            subprocess.run(compile_cmd, check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run(['java','-jar',str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn('NATIVE_AUTOCAL_PROTOCOL=PASS', result.stdout)

if __name__ == '__main__':
    unittest.main()
