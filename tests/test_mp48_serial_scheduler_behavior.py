#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"
SCHEDULER = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt"

class Mp48SerialSchedulerBehaviorTest(unittest.TestCase):
    def test_real_engine_scheduler_interleaves_reads_but_not_atomic_write_readback(self):
        kotlinc = shutil.which("kotlinc")
        java = shutil.which("java")
        if not kotlinc or not java:
            self.skipTest("kotlinc/java indisponível")

        with tempfile.TemporaryDirectory(prefix="omegas-serial-scheduler-") as td:
            tmp = Path(td)
            stubs = {
                "android/os/SystemClock.kt": r'''
                    package android.os
                    object SystemClock {
                        fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
                        fun sleep(ms: Long) { if (ms > 0) Thread.sleep(ms.coerceAtMost(5L)) }
                    }
                ''',
                "org/json/JSONObject.kt": r'''
                    package org.json
                    class JSONObject {
                        fun put(key: String, value: Any?): JSONObject = this
                    }
                ''',
                "com/omegas/prohub/learning/Stubs.kt": r'''
                    package com.omegas.prohub.learning
                    import com.omegas.prohub.ecu.Mp48Telemetry
                    import org.json.JSONObject
                    data class SampleDecision(val state: String = "OK")
                    class MotorSampleAnalyzer {
                        fun reset() {}
                        fun markPlannedOperation() {}
                        fun markContinuityLost() {}
                        fun add(t: Mp48Telemetry, plannedGap: Boolean, toleratedGap: Boolean): SampleDecision = SampleDecision()
                    }
                    data class Tolerances(
                        val toleratedSerialFailures: Int = 2,
                        val hardRecoveryFailures: Int = 5,
                        val hardRecoverySilenceMs: Long = 5000L,
                    ) { fun toJson(): JSONObject = JSONObject() }
                    object LearningToleranceSettings { val current = Tolerances() }
                ''',
                "com/omegas/prohub/util/RingLog.kt": r'''
                    package com.omegas.prohub.util
                    class RingLog { fun add(level: String, tag: String, message: String) {} }
                ''',
                "com/omegas/prohub/usb/Stubs.kt": r'''
                    package com.omegas.prohub.usb
                    import com.omegas.prohub.ecu.Mp48Protocol
                    import java.util.Collections
                    enum class UsbProtocolStatusClass { ACK, EXTENDED_RETRYABLE, EXTENDED_NON_RETRYABLE, EXTENDED_UNKNOWN, UNKNOWN }
                    data class UsbProtocolReply(
                        val ok: Boolean,
                        val status: Int = -1,
                        val payload: ByteArray = byteArrayOf(),
                        val request: ByteArray = byteArrayOf(),
                        val echo: ByteArray = byteArrayOf(),
                        val rawResponse: ByteArray = byteArrayOf(),
                        val error: String = "",
                        val elapsedMs: Long = 0L,
                    ) { val statusClass: UsbProtocolStatusClass get() = if (ok) UsbProtocolStatusClass.ACK else UsbProtocolStatusClass.UNKNOWN }
                    class UsbSerialManager {
                        @Volatile var connected: Boolean = true
                        val events = Collections.synchronizedList(mutableListOf<String>())
                        fun purge(reason: String) {}
                        fun protocolTransaction(
                            request: ByteArray,
                            reason: String,
                            timeoutMs: Int = 1800,
                            purgeBefore: Boolean = false,
                            expectedSessionId: Long = 0L,
                        ): UsbProtocolReply {
                            val name = when {
                                request.contentEquals(Mp48Protocol.CMD_TELEMETRY) -> "TEL"
                                request.contentEquals(Mp48Protocol.CMD_INIT_1) -> "INIT1"
                                request.contentEquals(Mp48Protocol.CMD_INIT_2) -> "INIT2"
                                request.contentEquals(Mp48Protocol.CMD_IDENTIFY) -> "IDENT"
                                request.contentEquals(Mp48Protocol.CMD_DISCONNECT) -> "DISC"
                                else -> request.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
                            }
                            events += name
                            val payload = if (name == "TEL") ByteArray(Mp48Protocol.TELEMETRY_PAYLOAD_SIZE) else byteArrayOf(1)
                            return UsbProtocolReply(true, Mp48Protocol.STATUS_ACK, payload, request = request)
                        }
                    }
                ''',
                "com/omegas/prohub/ecu/Mp48Protocol.kt": r'''
                    package com.omegas.prohub.ecu
                    object Mp48Protocol {
                        const val STATUS_ACK = 0x53
                        const val TELEMETRY_PAYLOAD_SIZE = 4
                        val CMD_INIT_1 = byteArrayOf(0x00, 0x02)
                        val CMD_INIT_2 = byteArrayOf(0x01, 0x00)
                        val CMD_IDENTIFY = byteArrayOf(0x00, 0x25)
                        val CMD_TELEMETRY = byteArrayOf(0x48, 0x01)
                        val CMD_DISCONNECT = byteArrayOf(0x00, 0x01)
                        fun decodeTelemetry(payload: ByteArray, capturedAt: Long) = Mp48Telemetry(capturedAt, true)
                    }
                    data class Mp48Telemetry(val capturedAt: Long, val plausible: Boolean)
                ''',
                "Harness.kt": r'''
                    import com.omegas.prohub.ecu.*
                    import com.omegas.prohub.usb.UsbSerialManager
                    import com.omegas.prohub.util.RingLog

                    fun waitUntil(timeoutMs: Long, block: () -> Boolean) {
                        val end = System.currentTimeMillis() + timeoutMs
                        while (!block() && System.currentTimeMillis() < end) Thread.sleep(2)
                        check(block()) { "timeout" }
                    }

                    fun main() {
                        val usb = UsbSerialManager()
                        val engine = ResponseDrivenEcuEngine(usb, RingLog(), { _, _, _ -> })
                        engine.beginUsbSession(7L)
                        check(engine.start())
                        waitUntil(1000) { engine.isSessionReady() }
                        usb.events.clear()

                        engine.transaction(byteArrayOf(0x71), "read-1", 100, false, 7L, Mp48WorkClass.READ_ONLY)
                        engine.transaction(byteArrayOf(0x72), "read-2", 100, false, 7L, Mp48WorkClass.READ_ONLY)
                        waitUntil(500) { usb.events.contains("72") && usb.events.lastOrNull() == "TEL" }
                        val readTrace = usb.events.toList()
                        val r1 = readTrace.indexOf("71")
                        val r2 = readTrace.indexOf("72")
                        check(r1 >= 0 && r2 > r1)
                        check(readTrace.subList(r1 + 1, r2).contains("TEL")) { "sem telemetria entre reads: $readTrace" }

                        usb.events.clear()
                        engine.unit(
                            reason = "write+readback",
                            expectedSessionId = 7L,
                            workClass = Mp48WorkClass.MANUAL_WRITE,
                            telemetryAfter = true,
                        ) { unit ->
                            check(unit.transaction(byteArrayOf(0x14), "write", 100).ok)
                            check(unit.transaction(byteArrayOf(0x2A), "readback", 100).ok)
                        }
                        waitUntil(500) { usb.events.contains("2A") && usb.events.lastOrNull() == "TEL" }
                        val writeTrace = usb.events.toList()
                        val w = writeTrace.indexOf("14")
                        val rb = writeTrace.indexOf("2A")
                        check(w >= 0 && rb == w + 1) { "write/readback separados: $writeTrace" }
                        check(writeTrace.drop(rb + 1).contains("TEL")) { "telemetria não voltou: $writeTrace" }

                        engine.close()
                        println("MP48_SERIAL_SCHEDULER_BEHAVIOR=PASS")
                    }
                ''',
            }
            files = [ENGINE, SCHEDULER]
            for rel, body in stubs.items():
                path = tmp / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(textwrap.dedent(body), encoding="utf-8")
                files.append(path)
            jar = tmp / "scheduler.jar"
            compile_proc = subprocess.run(
                [kotlinc, *map(str, files), "-include-runtime", "-d", str(jar)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                timeout=30,
            )
            self.assertEqual(0, compile_proc.returncode, compile_proc.stdout + compile_proc.stderr)
            run_proc = subprocess.run(
                [java, "-jar", str(jar)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                timeout=15,
            )
            self.assertEqual(0, run_proc.returncode, run_proc.stdout + run_proc.stderr)
            self.assertIn("MP48_SERIAL_SCHEDULER_BEHAVIOR=PASS", run_proc.stdout)


if __name__ == "__main__":
    unittest.main()
