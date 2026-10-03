package com.omegas.prohub.properties

import com.omegas.prohub.autocal.AutoCalNativeActionManager
import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.properties.PropertySupport.SEEDS
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Trava serial e ação nativa do AutoCal sob falhas (Lote W, parte 3):
 * a trava e o "ocupado" são soltos depois de QUALQUER callback que lance (Exception ou Error) e o gerente segue utilizável.
 * (A ponte CalibrationOperationsBridge depende de MainActivity e não roda em JVM puro: o contrato dela
 * está em tests/test_calibration_bridge_busy_release.py.)
 */
class SerialGuardPropertiesTest {
    @Test
    fun `sequencia aleatoria de adquirir e soltar nunca tem dois donos e so o dono solta`() {
        val owners = listOf(SerialWriteGuard.OWNER_K_FACTOR, SerialWriteGuard.OWNER_K_MAP, SerialWriteGuard.OWNER_AUTOCAL, "OUTRO")
        for (seed in SEEDS) {
            val rnd = Random(seed)
            val guard = SerialWriteGuard()
            var model: String? = null
            repeat(500) {
                val owner = owners[rnd.nextInt(owners.size)]
                if (rnd.nextBoolean()) {
                    val ok = guard.tryAcquire(owner)
                    assertEquals("semente $seed: tryAcquire($owner) com dono=$model", model == null, ok)
                    if (ok) model = owner
                } else {
                    guard.release(owner)
                    if (model == owner) model = null
                }
                assertEquals(model, guard.holder())
                assertEquals(model != null, guard.isHeld())
            }
        }
    }

    /** Padrão usado em produção: adquire, roda, solta no finally. Falha em qualquer ponto não pode deixar a trava presa. */
    private fun guarded(guard: SerialWriteGuard, owner: String, body: () -> Unit): Boolean {
        if (!guard.tryAcquire(owner)) return false
        try {
            body()
        } finally {
            guard.release(owner)
        }
        return true
    }

    @Test
    fun `trava solta depois de callback que lanca Exception ou Error`() {
        val throwables = listOf<() -> Throwable>(
            { IllegalStateException("x") }, { IOException("cabo") }, { OutOfMemoryError("sem memória") }, { AssertionError("falha") },
            { StackOverflowError() }, { NumberFormatException("NaN") },
        )
        for (make in throwables) {
            val guard = SerialWriteGuard()
            try { guarded(guard, SerialWriteGuard.OWNER_K_FACTOR) { throw make() } } catch (_: Throwable) { }
            assertNull("trava presa depois de ${make().javaClass.simpleName}", guard.holder())
            assertTrue(guard.tryAcquire(SerialWriteGuard.OWNER_AUTOCAL))
        }
    }

    private fun reply(request: ByteArray, payload: ByteArray) = UsbProtocolReply(
        ok = true, status = Mp48Protocol.STATUS_ACK, payload = payload, request = request, echo = request,
    )

    private fun awaitIdle(manager: AutoCalNativeActionManager) {
        repeat(600) {
            if (!manager.isBusy()) return
            Thread.sleep(5L)
        }
        throw AssertionError("Ação AutoCal não finalizou")
    }

    private fun manager(guard: SerialWriteGuard, transaction: (ByteArray) -> UsbProtocolReply) = AutoCalNativeActionManager(
        receiptFile = Files.createTempDirectory("guard-prop").resolve("receipts.json").toFile(),
        isConnected = { true },
        currentSessionId = { 1L },
        otherCalibrationBusy = { false },
        unsafeMutationReason = { null },
        transaction = { request, _, _, _ -> transaction(request) },
        fieldsForReceipt = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
        onConfirmed = {},
        guard = guard,
    )

    @Test
    fun `acao do AutoCal com transacao que lanca em qualquer chamada solta trava e ocupado e o gerente segue utilizavel`() {
        for (failAt in 0..1) for (kind in 0..2) {
            val guard = SerialWriteGuard()
            val calls = AtomicInteger(0)
            val healthy = AtomicBoolean(false)
            val manager = manager(guard) { request ->
                val n = calls.getAndIncrement()
                if (!healthy.get() && n == failAt) {
                    when (kind) {
                        0 -> throw IllegalStateException("falha injetada")
                        1 -> throw IOException("cabo desconectado")
                        else -> throw AssertionError("erro fatal injetado")
                    }
                }
                val isRead = request.isNotEmpty() && (request[0].toInt() and 0xFF) == (AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE)[0].toInt() and 0xFF) &&
                    request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE))
                reply(request, if (isRead) byteArrayOf(1) else byteArrayOf())
            }
            val prepared = manager.prepare("ENABLE_AUTO_CAL")
            assertTrue(prepared.getBoolean("ok"))
            val started = manager.execute(prepared.getString("preparationId"))
            assertTrue(started.getBoolean("ok"))
            awaitIdle(manager)
            assertNull("falha=$failAt tipo=$kind: trava presa", guard.holder())
            assertFalse("falha=$failAt tipo=$kind: gerente ocupado para sempre", manager.isBusy())
            assertFalse(manager.statusJson().getBoolean("busy"))
            if (kind != 2) assertEquals("FAILED", manager.statusJson().getString("state"))
            // continua utilizável: nova ação com a ECU saudável
            healthy.set(true)
            val again = manager.prepare("ENABLE_AUTO_CAL")
            assertTrue("falha=$failAt tipo=$kind: não consegue preparar de novo: $again", again.getBoolean("ok"))
            assertTrue(manager.execute(again.getString("preparationId")).getBoolean("ok"))
            awaitIdle(manager)
            assertNull(guard.holder())
            assertEquals("CONFIRMED", manager.statusJson().getString("state"))
            manager.close()
        }
    }

    @Test
    fun `duas acoes ao mesmo tempo - a segunda e recusada sem tocar a ECU e nada fica preso`() {
        val guard = SerialWriteGuard()
        val gate = java.util.concurrent.CountDownLatch(1)
        val calls = AtomicInteger(0)
        val manager = manager(guard) { request ->
            calls.incrementAndGet()
            gate.await(2, java.util.concurrent.TimeUnit.SECONDS)
            reply(request, if (request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE))) byteArrayOf(1) else byteArrayOf())
        }
        val first = manager.prepare("ENABLE_AUTO_CAL")
        assertTrue(manager.execute(first.getString("preparationId")).getBoolean("ok"))
        val second = manager.prepare("DISABLE_AUTO_CAL")
        val refused = manager.execute(second.optString("preparationId", "x"))
        assertFalse("segunda ação durante a primeira precisa ser recusada", refused.getBoolean("ok"))
        gate.countDown()
        awaitIdle(manager)
        assertNull(guard.holder())
        manager.close()
        assertTrue(calls.get() >= 1)
    }

    @Test
    fun `sessao USB que muda ou desconexao entre preparar e confirmar nao envia nada e nao prende a trava`() {
        val guard = SerialWriteGuard()
        val session = AtomicLong(1L)
        val connected = AtomicBoolean(true)
        val sent = AtomicInteger(0)
        val manager = AutoCalNativeActionManager(
            receiptFile = Files.createTempDirectory("guard-prop2").resolve("receipts.json").toFile(),
            isConnected = connected::get,
            currentSessionId = session::get,
            otherCalibrationBusy = { false },
            unsafeMutationReason = { null },
            transaction = { request, _, _, _ -> sent.incrementAndGet(); reply(request, byteArrayOf()) },
            fieldsForReceipt = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
            onConfirmed = {},
            guard = guard,
        )
        for (breakIt in listOf({ session.incrementAndGet() }, { connected.set(false) })) {
            val prepared = manager.prepare("ENABLE_AUTO_CAL")
            if (!prepared.optBoolean("ok")) { connected.set(true); continue }
            breakIt()
            val result = manager.execute(prepared.getString("preparationId"))
            assertFalse(result.getBoolean("ok"))
            assertNull(guard.holder())
            assertFalse(manager.isBusy())
            connected.set(true)
        }
        assertEquals(0, sent.get())
        manager.close()
    }
}
