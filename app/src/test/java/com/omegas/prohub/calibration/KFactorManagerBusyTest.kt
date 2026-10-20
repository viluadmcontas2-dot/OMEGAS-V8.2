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

/** "Ocupado" com dono na Curva K: a conferência em segundo plano não apaga o ocupado de outra operação. */
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

    private fun phase() = JSONObject(manager.writeVerifyJson()).optString("phase")

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "tempo esgotado esperando $what" }
            Thread.sleep(5)
        }
    }

    @Test fun finishingOperationDoesNotFreeAnotherOperationsBusy() {
        ecu.hold("conferência em segundo plano Curva K")
        val points = JSONArray().put(JSONObject().put("index", 3).put("currentRaw", 16_384).put("targetRaw", 18_000))
        assertTrue(manager.startBatchWrite(points).optBoolean("ok"))
        ecu.awaitEntered("conferência em segundo plano Curva K")
        waitFor("Gravado libera a tela") { !manager.isBusy() }
        ecu.hold("eixo Petrol Inj")
        val b = Thread { manager.readCurve() }.apply { start() }
        ecu.awaitEntered("eixo Petrol Inj")
        assertTrue(manager.isBusy())
        ecu.release("conferência em segundo plano Curva K")
        waitFor("conferência de A termina") { phase() == KBatchVerifyCore.Phase.OK }
        Thread.sleep(50)
        assertTrue("B continua ocupado depois que A terminou", manager.isBusy())
        ecu.release("eixo Petrol Inj")
        b.join(5_000)
        assertFalse(manager.isBusy())
    }
}
