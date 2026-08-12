package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AutoCalNativeActionManagerTest {
    @Test
    fun `quadros nativos conhecidos sao exatos`() {
        assertArrayEquals(hex("02 24 04 01 2B"), AutoCalNativeActionManager.Action.RESET_PETROL.request)
        assertArrayEquals(hex("02 24 04 02 2C"), AutoCalNativeActionManager.Action.RESET_GAS.request)
        assertArrayEquals(hex("02 24 04 04 2E"), AutoCalNativeActionManager.Action.RESET_ALL.request)
        assertArrayEquals(hex("02 24 04 08 32"), AutoCalNativeActionManager.Action.NATIVE_AUTOMATCH.request)
    }

    @Test
    fun `preparar nao envia nenhum byte e exige confirmacao separada`() {
        val calls = AtomicInteger(0)
        val manager = manager { request, _, _, _ ->
            calls.incrementAndGet()
            reply(request, byteArrayOf(1))
        }
        val prepared = manager.prepare("RESET_PETROL")
        assertTrue(prepared.getBoolean("prepared"))
        assertTrue(prepared.getBoolean("requiresCriticalConfirmation"))
        assertFalse(prepared.getBoolean("automatic"))
        assertEquals(0, calls.get())
        manager.clearPreparation()
        assertEquals(0, calls.get())
        manager.close()
    }

    @Test
    fun `acao confirmada gera snapshot antes depois e recibo persistente`() {
        val actionSent = AtomicBoolean(false)
        val confirmed = AtomicBoolean(false)
        val receiptFile = temporaryFile()
        val manager = manager(receiptFile = receiptFile, onConfirmed = { confirmed.set(true) }) { request, _, _, _ ->
            when (request[0].toInt() and 0xFF) {
                0x09, 0x29 -> reply(request, byteArrayOf(if (actionSent.get()) 2 else 1))
                else -> {
                    actionSent.set(true)
                    reply(request, byteArrayOf())
                }
            }
        }
        val prepared = manager.prepare("NATIVE_AUTOMATCH")
        val started = manager.execute(prepared.getString("preparationId"))
        assertTrue(started.getBoolean("humanConfirmed"))
        awaitIdle(manager)
        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertTrue(confirmed.get())
        val receipts = manager.receiptsJson()
        assertEquals(1, receipts.length())
        val receipt = receipts.getJSONObject(0)
        assertEquals("NATIVE_AUTOMATCH", receipt.getString("action"))
        assertEquals("02 24 04 08 32", receipt.getString("commandHex"))
        assertEquals(1, receipt.getJSONArray("changedFields").length())
        assertFalse(receipt.getBoolean("automatic"))
        assertFalse(receipt.getBoolean("automaticRollback"))
        assertTrue(receiptFile.isFile)
        manager.close()
    }

    @Test
    fun `confirmacao errada ou sessao alterada nao envia acao`() {
        val session = AtomicLong(7L)
        val actionCalls = AtomicInteger(0)
        val manager = manager(session = session) { request, _, _, _ ->
            if ((request[0].toInt() and 0xFF) == 0x02) actionCalls.incrementAndGet()
            reply(request, byteArrayOf(1))
        }
        val prepared = manager.prepare("RESET_ALL")
        assertFalse(manager.execute("outro-id").getBoolean("ok"))
        session.incrementAndGet()
        assertFalse(manager.execute(prepared.getString("preparationId")).getBoolean("ok"))
        assertEquals(0, actionCalls.get())
        manager.close()
    }

    @Test
    fun `conflito com outra calibracao impede preparar`() {
        val otherBusy = AtomicBoolean(true)
        val manager = manager(otherBusy = otherBusy) { request, _, _, _ -> reply(request, byteArrayOf(1)) }
        val result = manager.prepare("RESET_GAS")
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("error").contains("Outra operação"))
        manager.close()
    }

    private fun manager(
        receiptFile: File = temporaryFile(),
        session: AtomicLong = AtomicLong(1L),
        connected: AtomicBoolean = AtomicBoolean(true),
        otherBusy: AtomicBoolean = AtomicBoolean(false),
        onConfirmed: (org.json.JSONObject) -> Unit = {},
        transaction: (ByteArray, String, Int, Long) -> UsbProtocolReply,
    ) = AutoCalNativeActionManager(
        receiptFile = receiptFile,
        isConnected = connected::get,
        currentSessionId = session::get,
        otherCalibrationBusy = otherBusy::get,
        transaction = transaction,
        fieldsForReceipt = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
        onConfirmed = onConfirmed,
    )

    private fun reply(request: ByteArray, payload: ByteArray) = UsbProtocolReply(
        ok = true,
        status = Mp48Protocol.STATUS_ACK,
        payload = payload,
        request = request,
        echo = request,
    )

    private fun temporaryFile(): File = Files.createTempDirectory("autocal-action-test")
        .resolve("receipts.json")
        .toFile()

    private fun awaitIdle(manager: AutoCalNativeActionManager) {
        repeat(400) {
            if (!manager.isBusy()) return
            Thread.sleep(5L)
        }
        throw AssertionError("Ação AutoCal não finalizou")
    }

    private fun hex(value: String): ByteArray = value.split(' ')
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
