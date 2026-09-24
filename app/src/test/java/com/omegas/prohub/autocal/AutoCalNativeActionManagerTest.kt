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
        assertArrayEquals(hex("12 4A 01 01 5E"), AutoCalNativeActionManager.Action.ENABLE_AUTO_CAL.request)
        assertArrayEquals(hex("12 4A 01 00 5D"), AutoCalNativeActionManager.Action.DISABLE_AUTO_CAL.request)
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
                0x09, 0x29 -> reply(request, byteArrayOf(if (actionSent.get()) 1 else 0))
                else -> {
                    actionSent.set(true)
                    reply(request, byteArrayOf())
                }
            }
        }
        val prepared = manager.prepare("ENABLE_AUTO_CAL")
        val started = manager.execute(prepared.getString("preparationId"))
        assertTrue(started.getBoolean("humanConfirmed"))
        awaitIdle(manager)
        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertTrue(confirmed.get())
        val receipts = manager.receiptsJson()
        assertEquals(1, receipts.length())
        val receipt = receipts.getJSONObject(0)
        assertEquals("ENABLE_AUTO_CAL", receipt.getString("action"))
        assertEquals("12 4A 01 01 5E", receipt.getString("commandHex"))
        assertTrue(receipt.getBoolean("readbackValid"))
        assertEquals(1, receipt.getJSONArray("changedFields").length())
        assertFalse(receipt.getBoolean("automatic"))
        assertFalse(receipt.getBoolean("automaticRollback"))
        assertTrue(receiptFile.isFile)
        manager.close()
    }

    @Test
    fun `reset persiste snapshot antes de enviar comando destrutivo`() {
        val receiptFile = temporaryFile()
        val backupExistedBeforeWrite = AtomicBoolean(false)
        val manager = manager(receiptFile = receiptFile) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request)) {
                val backupDir = File(receiptFile.parentFile, "backups/autocal_pre_reset")
                val backup = backupDir.listFiles()?.singleOrNull { it.extension == "json" }
                backupExistedBeforeWrite.set(
                    backup != null &&
                        org.json.JSONObject(backup.readText(Charsets.UTF_8))
                            .getJSONObject("before")
                            .getString("snapshotHash")
                            .isNotBlank(),
                )
            }
            reply(request, byteArrayOf(1))
        }

        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertTrue("Reset só pode sair depois de backup durável", backupExistedBeforeWrite.get())
        manager.close()
    }

    @Test
    fun `falha ao persistir backup bloqueia reset antes da usb`() {
        val receiptFile = temporaryFile()
        File(receiptFile.parentFile, "backups").writeText("bloqueia diretório de backup")
        val resetCalls = AtomicInteger(0)
        val manager = manager(receiptFile = receiptFile) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request)) {
                resetCalls.incrementAndGet()
            }
            reply(request, byteArrayOf(1))
        }

        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals(0, resetCalls.get())
        assertEquals("FAILED", manager.statusJson().getString("state"))
        manager.close()
    }

    @Test
    fun `reset gas registra escopo dedicado quando gasolina e curva k ficam intactas`() {
        val actionSent = AtomicBoolean(false)
        val fields = listOf(
            AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            AutoCalProtocol.ACQUIRED_ZONES_GAS,
            AutoCalProtocol.MUL_ACT,
        )
        val manager = manager(fieldsForReceipt = fields) { request, _, _, _ ->
            when {
                request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request) -> {
                    actionSent.set(true)
                    reply(request, byteArrayOf())
                }
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.ACQUIRED_ZONES_PETROL)) ->
                    reply(request, byteArrayOf(1, 1, 1, 1))
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.ACQUIRED_ZONES_GAS)) ->
                    reply(request, if (actionSent.get()) byteArrayOf(0, 0, 0, 0) else byteArrayOf(1, 1, 1, 1))
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)) ->
                    reply(request, q14Payload(16384))
                else -> error("request inesperado")
            }
        }

        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        val scope = manager.receiptsJson().getJSONObject(0).getJSONObject("scopeAssessment")
        assertEquals("GNV_REACQUISITION", scope.getString("intendedScope"))
        assertTrue(scope.getBoolean("scopeConclusive"))
        assertTrue(scope.getBoolean("gasAcquisitionChanged"))
        assertFalse(scope.getBoolean("petrolAcquisitionChanged"))
        assertFalse(scope.getBoolean("mulActChanged"))
        assertFalse(scope.getBoolean("broaderThanIntended"))
        assertTrue(scope.getBoolean("dedicatedScopeObserved"))
        assertFalse(scope.getBoolean("requiresAttention"))
        manager.close()
    }

    @Test
    fun `reset gas alerta quando readback mostra gasolina ou curva k alteradas`() {
        val actionSent = AtomicBoolean(false)
        val fields = listOf(
            AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            AutoCalProtocol.ACQUIRED_ZONES_GAS,
            AutoCalProtocol.MUL_ACT,
        )
        val manager = manager(fieldsForReceipt = fields) { request, _, _, _ ->
            when {
                request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request) -> {
                    actionSent.set(true)
                    reply(request, byteArrayOf())
                }
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.ACQUIRED_ZONES_PETROL)) ->
                    reply(request, if (actionSent.get()) byteArrayOf(0, 0, 0, 0) else byteArrayOf(1, 1, 1, 1))
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.ACQUIRED_ZONES_GAS)) ->
                    reply(request, if (actionSent.get()) byteArrayOf(0, 0, 0, 0) else byteArrayOf(1, 1, 1, 1))
                request.contentEquals(AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)) ->
                    reply(request, q14Payload(if (actionSent.get()) 16000 else 16384))
                else -> error("request inesperado")
            }
        }

        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("CONFIRMED_WITH_SCOPE_WARNING", manager.statusJson().getString("state"))
        val scope = manager.receiptsJson().getJSONObject(0).getJSONObject("scopeAssessment")
        assertTrue(scope.getBoolean("petrolAcquisitionChanged"))
        assertTrue(scope.getBoolean("mulActChanged"))
        assertTrue(scope.getBoolean("broaderThanIntended"))
        assertFalse(scope.getBoolean("dedicatedScopeObserved"))
        assertTrue(scope.getBoolean("requiresAttention"))
        manager.close()
    }

    @Test
    fun `enable exige readback do AUTO_CAL_ENABLE`() {
        val actionSent = AtomicBoolean(false)
        val manager = manager { request, _, _, _ ->
            when (request[0].toInt() and 0xFF) {
                0x09, 0x29 -> reply(request, byteArrayOf(0))
                else -> {
                    actionSent.set(true)
                    reply(request, byteArrayOf())
                }
            }
        }
        val prepared = manager.prepare("ENABLE_AUTO_CAL")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertTrue(actionSent.get())
        assertEquals("FAILED", manager.statusJson().getString("state"))
        assertTrue(manager.statusJson().getString("message").contains("Readback"))
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
        val prepared = manager.prepare("RESET_GAS")
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
        fieldsForReceipt: List<AutoCalProtocol.Field> = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
        transaction: (ByteArray, String, Int, Long) -> UsbProtocolReply,
    ) = AutoCalNativeActionManager(
        receiptFile = receiptFile,
        isConnected = connected::get,
        currentSessionId = session::get,
        otherCalibrationBusy = otherBusy::get,
        transaction = transaction,
        fieldsForReceipt = fieldsForReceipt,
        onConfirmed = onConfirmed,
    )

    private fun q14Payload(value: Int): ByteArray = ByteArray(60).also { bytes ->
        repeat(30) { index ->
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
    }

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
