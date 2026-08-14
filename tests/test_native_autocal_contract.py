import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt'
SCALE = ROOT / 'app/src/main/java/com/omegas/prohub/ecu/AutoCalScale.kt'
ACTION = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt'
BRIDGE = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'
ACQ = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt'
MONITOR = ROOT / 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt'
SERVICE = ROOT / 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'
LEARNING = ROOT / 'app/src/main/java/com/omegas/prohub/learning/LiveOnlyLearningStore.kt'

class NativeAutoCalContract(unittest.TestCase):
    def setUp(self):
        self.protocol = PROTOCOL.read_text('utf-8')
        self.action = ACTION.read_text('utf-8')
        self.bridge = BRIDGE.read_text('utf-8')
        self.acq = ACQ.read_text('utf-8')
        self.monitor = MONITOR.read_text('utf-8')
        self.service = SERVICE.read_text('utf-8')
        self.learning = LEARNING.read_text('utf-8')

    def test_manual_automatch_route_is_removed(self):
        self.assertNotIn('NATIVE_AUTOMATCH', self.action)
        self.assertNotIn('NATIVE_AUTOMATCH', self.bridge)
        self.assertNotIn('02 24 04 08 32', self.action)
        self.assertIn('manualAutoMatchExposed", false', self.bridge)

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
