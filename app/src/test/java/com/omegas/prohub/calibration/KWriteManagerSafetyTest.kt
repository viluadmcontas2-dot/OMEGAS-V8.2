package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Saída segura do K insertion e "ocupado" com dono no escritor do Mapa K (central falsa em bytes). */
class KWriteManagerSafetyTest {
    private lateinit var dir: File
    private lateinit var ecu: FakeKEcuScheduler
    private lateinit var writer: KWriteManager
    private val exit = Mp48Protocol.kInsertionMode(false)

    @Before fun setUp() {
        dir = Files.createTempDirectory("kwrite").toFile()
        ecu = FakeKEcuScheduler()
        writer = KWriteManager(dir, ecu, RingLog(), { false }, { true }, { true }, {})
        assertTrue(writer.readFullMap().optBoolean("ok"))
    }

    @After fun tearDown() { writer.close(); dir.deleteRecursively() }

    private fun status() = JSONObject(writer.statusJson())
    private fun phase() = JSONObject(writer.writeVerifyJson()).optString("phase")

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "tempo esgotado esperando $what" }
            Thread.sleep(5)
        }
    }

    @Test fun activationWithoutReplyStillSendsSafeExitOnce() {
        ecu.forced[FakeKEcuScheduler.key(Mp48Protocol.kInsertionMode(true))] = FakeKEcuScheduler.TIMEOUT
        assertTrue(writer.startWrite(0, 0, 120, 130).optBoolean("ok"))
        waitFor("fim do lote") { !writer.isBusy() }
        assertEquals("saída segura enviada uma vez", 1, ecu.count(exit))
        assertEquals("BATCH_PARTIAL_FAILED", status().getString("state"))
        assertTrue(status().getString("message").isNotBlank())
        assertTrue("saída confirmada: sem trava", writer.readLine(0).optBoolean("ok"))
    }

    @Test fun activationAndExitWithoutReplyTryExitOnceThenLock() {
        ecu.forced[FakeKEcuScheduler.key(Mp48Protocol.kInsertionMode(true))] = FakeKEcuScheduler.TIMEOUT
        ecu.forced[FakeKEcuScheduler.key(exit)] = FakeKEcuScheduler.TIMEOUT
        assertTrue(writer.startWrite(0, 0, 120, 130).optBoolean("ok"))
        waitFor("fim do lote") { !writer.isBusy() }
        assertEquals("uma tentativa, sem laço", 1, ecu.count(exit))
        assertEquals("SAFETY_LOCKED_INSERTION_UNKNOWN", status().getString("state"))
        assertFalse(status().optBoolean("busy"))
        assertTrue(writer.readLine(0).optBoolean("safetyLocked"))
    }

    @Test fun correctionActivationWithoutReplyStillSendsSafeExit() {
        // A central dá ACK na escrita mas não guarda: a conferência diverge e a correção tenta entrar de novo.
        ecu.forced[FakeKEcuScheduler.key(Mp48Protocol.writeKCell(0, 0, 130))] =
            UsbProtocolReply(ok = true, status = Mp48Protocol.STATUS_ACK)
        ecu.forcedByReason["ativar K insertion (correção)"] = FakeKEcuScheduler.TIMEOUT
        assertTrue(writer.startWrite(0, 0, 120, 130).optBoolean("ok"))
        waitFor("fim da conferência") { phase() == KBatchVerifyCore.Phase.FAILED }
        waitFor("saída segura") { ecu.count(exit) >= 2 }
        Thread.sleep(50)
        assertEquals("saída normal + uma saída segura", 2, ecu.count(exit))
        assertTrue("saída confirmada: sem trava", writer.readLine(0).optBoolean("ok"))
    }

    @Test fun finishingOperationDoesNotFreeAnotherOperationsBusy() {
        ecu.hold("conferência em segundo plano Mapa K")
        assertTrue(writer.startWrite(0, 0, 120, 130).optBoolean("ok"))
        ecu.awaitEntered("conferência em segundo plano Mapa K")
        waitFor("Gravado libera a tela") { !writer.isBusy() }
        ecu.hold("leitura da linha K")
        val b = Thread { writer.readLine(1) }.apply { start() }
        ecu.awaitEntered("leitura da linha K")
        assertTrue(writer.isBusy())
        ecu.release("conferência em segundo plano Mapa K")
        waitFor("conferência de A termina") { phase() == KBatchVerifyCore.Phase.OK }
        Thread.sleep(50)
        assertTrue("B continua ocupado depois que A terminou", writer.isBusy())
        ecu.release("leitura da linha K")
        b.join(5_000)
        assertFalse(writer.isBusy())
    }
}
