import shutil
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
WINDOW = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/NativeAnchorTelemetryWindow.kt'
CORRELATOR = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/NativeAutoCalAnchorCorrelator.kt'

class NativeAutoCalContract(unittest.TestCase):
    def setUp(self):
        self.protocol = PROTOCOL.read_text('utf-8')
        self.action = ACTION.read_text('utf-8')
        self.bridge = BRIDGE.read_text('utf-8')
        self.acq = ACQ.read_text('utf-8')
        self.monitor = MONITOR.read_text('utf-8')
        self.maturity = MATURITY.read_text('utf-8')
        self.service = SERVICE.read_text('utf-8')
        self.scheduler = SCHEDULER.read_text('utf-8')
        self.engine = ENGINE.read_text('utf-8')
        self.window = WINDOW.read_text('utf-8')
        self.correlator = CORRELATOR.read_text('utf-8')

    def test_native_autocal_scale_and_action_identity_matches_recovered_progbase(self):
        scale = SCALE.read_text('utf-8')
        self.assertIn('INJECTION_COUNTS_PER_MS = 512.0', scale)
        self.assertIn('MAP_COUNTS_PER_BAR = 1_024.0', scale)
        # Clean forensics: command 0x24/sub-op 0x04, AutoMatch=0x08, petrol=0x01, gas=0x02, all=0x04.
        self.assertNotIn('MANUAL_AUTOMATCH', self.action)
        self.assertIn('ManualActionMode.RESET_PETROL', self.action)
        self.assertIn('ManualActionMode.RESET_GAS', self.action)
        self.assertIn('ManualActionMode.RESET_ALL', self.action)

    def test_manual_automatch_is_human_confirmed_and_separate_from_native_epochs(self):
        self.assertNotIn('NATIVE_AUTOMATCH', self.action)
        self.assertNotIn('NATIVE_AUTOMATCH', self.bridge)
        self.assertIn('manualAutoMatchExposed", false', self.bridge)
        self.assertIn('manualAutoMatchExposed", false', self.monitor)
        self.assertNotIn('manualAutoMatchExposed", true', self.bridge + self.monitor)
        self.assertIn('requiresCriticalConfirmation', self.action)
        self.assertEqual(self.bridge.count('AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH'), 0)
        self.assertGreaterEqual(self.bridge.count('AutoCalNativeActionManager.Action.FINISH_AUTOCAL'), 2)
        self.assertGreaterEqual(self.bridge.count('AutoCalNativeActionManager.Action.FINISH_AUTOMATCH'), 2)
        self.assertIn('executeFinish(prepared, startedAt)', self.action)
        self.assertIn('AutoCalProtocol.MAX_AUTOMATCH', self.action)
        self.assertIn('AutoCalProtocol.NUM_AUTOMATCH_EXECUTED', self.action)
        self.assertIn('finishSource", "MAX_AUTOMATCH', self.action)
        self.assertIn('finishTarget", "NUM_AUTOMATCH_EXECUTED', self.action)
        self.assertNotIn('finishSource", "VECT_AUTOCAL_U8_1', self.action)
        self.assertNotIn('finishTarget", "VECT_AUTOCAL_U8_0', self.action)
        self.assertIn('counterWidthBytes', self.action)
        self.assertIn('Thread.sleep(100L)', self.action)
        self.assertNotIn('Thread.sleep(250L)', self.action)
        self.assertNotIn('Thread.sleep(500L)', self.action)
        self.assertIn('Finish AutoCal não persistiu', self.action)
        self.assertIn('Encerrar cota AutoMatch (técnico)', self.action)
        self.assertIn('originalmente desabilitada no DFM', self.action)
        self.assertIn('PanelDbg oculto do ProgBase', self.action)
        self.assertIn('Cota AutoMatch ajustada · $committed/$max confirmado pela ECU · aquisição não foi pausada', self.action)
        self.assertNotIn('AutoCal finalizado', self.action)

    def test_legacy_k_reset_alias_converges_to_single_progbase_path(self):
        self.assertNotIn('if (requested == "NEUTRALIZE_LIVE_K") "RESET_K_FACTOR"', self.bridge)
        self.assertIn('NEUTRALIZE_LIVE_K foi removido', self.bridge)
        self.assertNotIn('startKFactorReset()', self.bridge)
        self.assertNotIn('kFactorResetPreparationId', self.bridge)
        self.assertGreaterEqual(
            self.bridge.count('AutoCalNativeActionManager.Action.RESET_K_FACTOR'),
            2,
        )
        self.assertIn('Action.RESET_K_FACTOR -> executeResetKFactor', self.action)

    def test_enable_disable_and_status_are_exact_portmon_frames(self):
        self.assertIn('CMD_NATIVE_STATUS = byteArrayOf(0x48, 0x0B, 0x53)', self.protocol)
        self.assertIn('fun setEnabled(enabled: Boolean)', self.protocol)
        self.assertIn('WRITE_U8 = 0x12', self.protocol)
        self.assertIn('ENABLE_AUTO_CAL', self.action)
        self.assertIn('DISABLE_AUTO_CAL', self.action)
        self.assertIn('expectedEnableReadback', self.action)
        self.assertIn('validateActionReadback', self.action)
        self.assertIn('Readback obrigatório ausente para', self.action)
        self.assertIn('Action.ENABLE_AUTO_CAL, Action.DISABLE_AUTO_CAL -> listOf(AutoCalProtocol.AUTO_CAL_ENABLE)', self.action)
        self.assertIn('Action.RESET_PETROL -> petrolAcquisitionReadbackFields()', self.action)
        self.assertIn('Action.RESET_GAS -> gasAcquisitionReadbackFields()', self.action)
        self.assertIn('Action.RESET_ALL -> petrolAcquisitionReadbackFields() +', self.action)
        self.assertIn('Action.RESET_K_FACTOR -> listOf(AutoCalProtocol.MUL_ACT)', self.action)
        self.assertIn('Action.FINISH_AUTOCAL, Action.FINISH_AUTOMATCH -> listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)', self.action)
        self.assertIn('.put("readbackWitnesses", JSONArray(actionReadbackWitnesses(prepared).map { it.key }))', self.action)
        self.assertIn('.put("readbackValid", true)', self.action)

    def test_max_automatch_is_semantic_and_not_threshold(self):
        self.assertIn('val MAX_AUTOMATCH = Field("MAX_AUTOMATCH", 0x0165', self.protocol)
        self.assertIn('val VECT_AUTOCAL_U8_2 = MAX_AUTOMATCH', self.protocol)
        self.assertIn('fields["MAX_AUTOMATCH"] ?: fields["VECT_AUTOCAL_U8_2"]', self.acq)
        self.assertIn('"maturityThresholdsPromoted", maturityThresholdsAvailable', self.acq)
        self.assertIn('"calibrationValueMapping", "PROGBASE_DUMP_GRID_PROVEN"', self.acq)
        self.assertIn('val petrolNormalThreshold = calibration.getOrNull(2)', self.acq)
        self.assertIn('val gasLowThreshold = calibration.getOrNull(5)', self.acq)
        self.assertIn('val gasNormalThreshold = calibration.getOrNull(8)', self.acq)
        self.assertIn('source.fuel == "GASOLINA" && index <= LOW_BAND_MAX_INDEX', self.acq)
        self.assertIn('source.fuel == "GNV" && index <= LOW_BAND_MAX_INDEX', self.acq)

    def test_dump_proven_gas_thresholds_are_wired_into_runtime_maturity_tracker(self):
        self.assertIn('val acquisition = AutoCalAcquisition.fromSnapshot(decorated)', self.monitor)
        self.assertIn('val thresholds = acquisition.optJSONObject("thresholds") ?: JSONObject()', self.monitor)
        self.assertIn('val newGasLowThreshold = thresholds.nullableInt("gasLow")', self.monitor)
        self.assertIn('val newGasNormalThreshold = thresholds.nullableInt("gasNormal")', self.monitor)
        self.assertIn('gasLowThreshold = newGasLowThreshold', self.monitor)
        self.assertIn('gasNormalThreshold = newGasNormalThreshold', self.monitor)
        self.assertIn('gasLowThreshold = thresholds.first', self.monitor)
        self.assertIn('gasNormalThreshold = thresholds.second', self.monitor)
        self.assertNotIn('maxAutomatch = thresholds.first', self.monitor)
        self.assertNotIn('maxAutomatch = thresholds.second', self.monitor)

    def test_reset_k_matches_progbase_mul_act_loop(self):
        self.assertIn('resetKFactorMulActFrames', self.protocol)
        self.assertIn('writeIndexedU16(MUL_ACT.address, index, 0x4000)', self.protocol)
        self.assertIn('Action.RESET_K_FACTOR -> executeResetKFactor(prepared, startedAt, before)', self.action)
        self.assertNotIn('resetKFactorEeprom()', self.action)
        self.assertNotIn('VECT_AUTOCAL_EE divergente após Reset K', self.action)

    def test_monitor_uses_existing_health_tick_and_event_driven_snapshot(self):
        self.assertNotIn('Executors.', self.monitor)
        self.assertNotIn('ScheduledExecutor', self.monitor)
        self.assertNotIn('Thread(', self.monitor)
        self.assertIn('AutoCalProtocol.CMD_NATIVE_STATUS', self.monitor)
        self.assertIn('AUTOMATCH_COUNT_CHANGED', self.monitor)
        self.assertIn('snapshotRequested', self.monitor)
        self.assertIn('nativeAutoCal.tick()', self.service)
        self.assertIn('refreshAcquisitionGroup(currentSession, group)', self.monitor)
        self.assertIn('refreshReferenceGroup(currentSession, group)', self.monitor)
        # Lote D: guarda de época POR GRUPO (probe de confirmação) + a do snapshot completo.
        self.assertIn('NativeAutoCalEpochGuard.sameEpoch(pending.beforeEpoch, probe)', self.monitor)
        self.assertIn('NativeAutoCalEpochGuard.sameEpoch(probe, afterEpoch)', self.monitor)
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
        self.assertIn('if (previous == null)', self.maturity)
        self.assertIn('if (!enabled) return emptyList()', self.maturity)
        self.assertIn('recordCorrelationResult', self.maturity)
        self.assertIn('correlationRetry', self.maturity)
        self.assertIn('maturityTracker.recordCorrelationResult(', self.monitor)
        self.assertIn('nativeCorrelationState', self.monitor)
        self.assertIn('correlatedBandIndexes', self.maturity)
        self.assertIn('retryableCorrelationBandIndexes', self.maturity)
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

    def test_actual_protocol_kotlin_frames_and_status_decoder(self):
        kotlinc = shutil.which('kotlinc')
        java = shutil.which('java')
        if kotlinc is None or java is None:
            self.skipTest('kotlinc/java unavailable; Gradle/JUnit remains authoritative')
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
                    check(AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_GAS).hex() == "02 24 04 02 2C")
                    check(AutoCalProtocol.MAX_AUTOMATCH.address == 0x0165)
                    check(AutoCalProtocol.MAX_AUTOMATCH.index == 2)
                    check(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED.address == 0x0174)
                    check(AutoCalProtocol.finishAutoCalCommit(3, 1).hex() == "12 74 01 03 8A")
                    check(AutoCalProtocol.finishAutoCalCommit(3, 2).hex() == "13 74 01 03 00 8B")
                    check(AutoCalProtocol.resetKFactorMulActFrames().size == 30)
                    check(AutoCalProtocol.resetKFactorMulActFrames().first().hex() == "14 61 01 00 00 40 B6")
                    check(AutoCalProtocol.expectedElements(AutoCalProtocol.MUL_ACT, 100) == 30)
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
            compile_cmd = [kotlinc, str(PROTOCOL), str(SCALE), str(tmp/'Mp48Protocol.kt'), str(tmp/'Main.kt'), '-include-runtime', '-d', str(jar)]
            subprocess.run(compile_cmd, check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run([java,'-jar',str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn('NATIVE_AUTOCAL_PROTOCOL=PASS', result.stdout)

if __name__ == '__main__':
    unittest.main()