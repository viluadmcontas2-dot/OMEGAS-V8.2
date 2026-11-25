package com.omegas.prohub.calibration

import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KFactorManagerBusyTest {
    private lateinit var dir: File
    private lateinit var ecu: FakeKEcuScheduler
    private lateinit var manager: KFactorManager

    @Before fun setUp() {
        dir = Files.createTempDirectory("kfactor").toFile()
        ecu = FakeKEcuScheduler()
        manager = KFactorManager(dir, ecu, RingLog(), {})
        assertTrue(manager.readCurve().optBoolean("ok"))
    }

    @After fun tearDown() { manager.close(); dir.deleteRecursively() }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "tempo esgotado esperando $what" }
            Thread.sleep(5)
        }
    }

    @Test fun readCurveCannotReplaceCacheDuringBatchVerification() {
        val verify = "conferência em segundo plano Curva K"
        ecu.hold(verify)
        val points = JSONArray().put(JSONObject().put("index", 3).put("currentRaw", 16_384).put("targetRaw", 18_000))
        assertTrue(manager.startBatchWrite(points).optBoolean("ok"))
        ecu.awaitEntered(verify)
        try {
            assertTrue(manager.isBusy())
            assertFalse(manager.readCurve().optBoolean("ok"))
            assertFalse(JSONObject(manager.statusJson()).getJSONObject("details").getBoolean("readbackValid"))
        } finally {
            ecu.release(verify)
        }
        waitFor("fim do lote") { !manager.isBusy() }
        assertTrue(JSONObject(manager.statusJson()).getJSONObject("details").getBoolean("readbackValid"))
        assertTrue(manager.readCurve().optBoolean("ok"))
    }
}
