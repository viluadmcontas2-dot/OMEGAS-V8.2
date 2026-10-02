#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WRAPPER = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt"
SCHEDULER = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/util/RuntimeBackpressurePolicy.kt"


class RedBackpressureBehaviorTest(unittest.TestCase):
    def test_read_only_saturates_without_blocking_critical_lane(self):
        kotlinc = shutil.which("kotlinc")
        java = shutil.which("java")
        if not kotlinc or not java:
            self.skipTest("kotlinc/java indisponível")

        with tempfile.TemporaryDirectory(prefix="omegas-red-backpressure-") as td:
            tmp = Path(td)
            stubs = {
                "org/json/JSONObject.kt": r'''
                    package org.json
                    class JSONObject {
                        fun put(key: String, value: Any?): JSONObject = this
                    }
                ''',
                "com/omegas/prohub/usb/UsbProtocolReply.kt": r'''
                    package com.omegas.prohub.usb
                    data class UsbProtocolReply(val ok: Boolean, val elapsedMs: Long = 0L)
                ''',
                "com/omegas/prohub/learning/NativeAnchorTelemetryWindow.kt": r'''
                    package com.omegas.prohub.learning
                    object NativeAnchorTelemetryWindow { data class Frame(val id: Long = 0L) }
                ''',
                "Harness.kt": r'''
                    import com.omegas.prohub.ecu.*
                    import com.omegas.prohub.usb.UsbProtocolReply
                    import java.util.concurrent.CountDownLatch
                    import java.util.concurrent.TimeUnit
                    import java.util.concurrent.atomic.AtomicReference

                    class FakeScheduler : Mp48SerialScheduler {
                        val entered = CountDownLatch(1)
                        val release = CountDownLatch(1)
                        override fun isConnected() = true
                        override fun currentSessionId() = 7L
                        override fun transaction(
                            request: ByteArray,
                            reason: String,
                            timeoutMs: Int,
                            purgeBefore: Boolean,
                            expectedSessionId: Long,
                            workClass: Mp48WorkClass,
                            telemetryAfter: Boolean,
                        ): UsbProtocolReply {
                            if (request.firstOrNull()?.toInt() == 1) {
                                entered.countDown()
                                check(release.await(2, TimeUnit.SECONDS)) { "blocking read was not released" }
                            }
                            return UsbProtocolReply(true)
                        }
                        override fun <T> unit(
                            reason: String,
                            expectedSessionId: Long,
                            workClass: Mp48WorkClass,
                            telemetryAfter: Boolean,
                            waitTimeoutMs: Long,
                            block: (Mp48SerialUnit) -> T,
                        ): T = error("unit not used by harness")
                    }

                    fun main() {
                        val delegate = FakeScheduler()
                        val scheduler = Mp48BackpressureScheduler(delegate, readOnlyCapacity = 1, criticalCapacity = 1)
                        val backgroundError = AtomicReference<Throwable?>(null)
                        val first = Thread {
                            try {
                                scheduler.transaction(byteArrayOf(1), "blocking-read", 1000, false, 7L, Mp48WorkClass.READ_ONLY, true)
                            } catch (error: Throwable) {
                                backgroundError.set(error)
                            }
                        }
                        first.start()
                        check(delegate.entered.await(1, TimeUnit.SECONDS)) { "first read never entered delegate" }

                        var rejected = false
                        try {
                            scheduler.transaction(byteArrayOf(2), "second-read", 1000, false, 7L, Mp48WorkClass.READ_ONLY, true)
                        } catch (_: Mp48BackpressureRejectedException) {
                            rejected = true
                        }
                        check(rejected) { "saturated READ_ONLY lane must reject immediately" }

                        val critical = scheduler.transaction(
                            byteArrayOf(3), "manual-write", 250, false, 7L, Mp48WorkClass.MANUAL_WRITE, true,
                        )
                        check(critical.ok) { "critical lane must remain available" }

                        delegate.release.countDown()
                        first.join(1500)
                        check(!first.isAlive) { "blocking read did not finish" }
                        backgroundError.get()?.let { throw it }

                        val metrics = scheduler.metricsSnapshot()
                        check(metrics.readOnlyAccepted == 1L)
                        check(metrics.readOnlyRejected == 1L)
                        check(metrics.criticalAccepted == 1L)
                        check(metrics.criticalRejected == 0L)
                        check(metrics.readOnlyInFlight == 0)
                        check(metrics.criticalInFlight == 0)
                        println("RED_BACKPRESSURE_BEHAVIOR=PASS")
                    }
                ''',
            }
            files = [WRAPPER, SCHEDULER, POLICY]
            for rel, body in stubs.items():
                path = tmp / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(textwrap.dedent(body), encoding="utf-8")
                files.append(path)

            jar = tmp / "red-backpressure.jar"
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
                timeout=10,
            )
            self.assertEqual(0, run_proc.returncode, run_proc.stdout + run_proc.stderr)
            self.assertIn("RED_BACKPRESSURE_BEHAVIOR=PASS", run_proc.stdout)


if __name__ == "__main__":
    unittest.main()
