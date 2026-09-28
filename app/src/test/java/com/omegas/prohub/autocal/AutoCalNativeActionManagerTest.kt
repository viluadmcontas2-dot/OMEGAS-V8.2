package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
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
        assertArrayEquals(hex("02 24 04 08 32"), AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH.request)
        assertEquals(0, AutoCalNativeActionManager.Action.RESET_K_FACTOR.request.size)
        assertEquals(30, AutoCalProtocol.resetKFactorMulActFrames().size)
        assertArrayEquals(hex("02 24 04 01 2B"), AutoCalNativeActionManager.Action.RESET_PETROL.request)
        assertArrayEquals(hex("02 24 04 02 2C"), AutoCalNativeActionManager.Action.RESET_GAS.request)
        assertArrayEquals(hex("02 24 04 04 2E"), AutoCalNativeActionManager.Action.RESET_ALL.request)
        assertArrayEquals(hex("12 4A 01 01 5E"), AutoCalNativeActionManager.Action.ENABLE_AUTO_CAL.request)
        assertArrayEquals(hex("12 4A 01 00 5D"), AutoCalNativeActionManager.Action.DISABLE_AUTO_CAL.request)
    }

    @Test
    fun `automatch manual existe mas nunca executa sem confirmacao humana`() {
        val calls = AtomicInteger(0)
        val manager = manager { request, _, _, _ ->
            calls.incrementAndGet()
            reply(request, byteArrayOf(1))
        }
        val prepared = manager.prepare("MANUAL_AUTOMATCH")
        assertTrue(prepared.getBoolean("prepared"))
        assertTrue(prepared.getBoolean("requiresCriticalConfirmation"))
        assertEquals("02 24 04 08 32", prepared.getString("commandHex"))
        assertEquals(0, calls.get())
        manager.clearPreparation()
        assertEquals(0, calls.get())
        manager.close()
    }

    @Test
    fun `reset k progbase escreve trinta pontos mul act e exige readback exato`() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        val readMul = AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)
        val neutralPayload = ByteArray(60).also { bytes ->
            repeat(30) { index ->
                bytes[index * 2] = 0x00
                bytes[index * 2 + 1] = 0x40
            }
        }
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.MUL_ACT),
            transaction = { request, _, _, _ ->
                requests += request.copyOf()
                if (request.contentEquals(readMul)) reply(request, neutralPayload)
                else reply(request, byteArrayOf())
            },
        )
        val prepared = manager.prepare("RESET_K_FACTOR")
        assertTrue(prepared.getBoolean("prepared"))
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        val resetFrames = AutoCalProtocol.resetKFactorMulActFrames()
        assertEquals(31, requests.size)
        resetFrames.forEachIndexed { index, frame -> assertArrayEquals(frame, requests[index]) }
        assertArrayEquals(readMul, requests.last())
        manager.close()
    }

    @Test
    fun `reset k progbase falha se mul act nao voltar neutro`() {
        val readMul = AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)
        val badPayload = ByteArray(60).also { bytes ->
            repeat(30) { index ->
                bytes[index * 2] = 0x00
                bytes[index * 2 + 1] = 0x40
            }
            bytes[0] = 0x01
        }
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.MUL_ACT),
            transaction = { request, _, _, _ ->
                if (request.contentEquals(readMul)) reply(request, badPayload)
                else reply(request, byteArrayOf())
            },
        )
        val prepared = manager.prepare("RESET_K_FACTOR")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        manager.close()
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
    fun `reset confirmado envia primeiro e so depois faz readback sem backup automatico`() {
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val confirmed = AtomicBoolean(false)
        val receiptFile = temporaryFile()
        val manager = manager(receiptFile = receiptFile, onConfirmed = { confirmed.set(true) }) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request)) {
                calls += "RESET_GAS"
                reply(request, byteArrayOf())
            } else {
                calls += "READBACK"
                reply(request, byteArrayOf(1))
            }
        }

        val prepared = manager.prepare("RESET_GAS")
        val started = manager.execute(prepared.getString("preparationId"))
        assertTrue(started.getBoolean("humanConfirmed"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertTrue(confirmed.get())
        assertEquals(listOf("RESET_GAS", "READBACK"), calls.toList())

        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("RESET_GAS", receipt.getString("action"))
        assertEquals("02 24 04 02 2C", receipt.getString("commandHex"))
        assertTrue(receipt.getBoolean("readbackValid"))
        assertFalse(receipt.getBoolean("automaticBackup"))
        assertTrue(receipt.isNull("preMutationBackup"))
        assertFalse(receipt.has("before"))
        assertTrue(receipt.has("after"))
        assertFalse(File(receiptFile.parentFile, "backups/autocal_pre_reset").exists())
        manager.close()
    }

    @Test
    fun `pasta de backup indisponivel nao bloqueia reset`() {
        val receiptFile = temporaryFile()
        File(receiptFile.parentFile, "backups").writeText("bloqueia qualquer backup automatico")
        val resetCalls = AtomicInteger(0)
        val manager = manager(receiptFile = receiptFile) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_PETROL.request)) {
                resetCalls.incrementAndGet()
                reply(request, byteArrayOf())
            } else {
                reply(request, byteArrayOf(1))
            }
        }

        val prepared = manager.prepare("RESET_PETROL")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals(1, resetCalls.get())
        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        manager.close()
    }

    @Test
    fun `finish autocal copia max automatch para contador e confirma readback`() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        val maxRead = AutoCalProtocol.read(AutoCalProtocol.MAX_AUTOMATCH)
        val counterRead = AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)
        val commit = AutoCalProtocol.finishAutoCalCommit(3, 1)
        var counterReads = 0
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            transaction = { request, _, _, _ ->
                requests += request.copyOf()
                when {
                    request.contentEquals(maxRead) -> reply(request, byteArrayOf(3))
                    request.contentEquals(counterRead) -> {
                        counterReads += 1
                        reply(request, byteArrayOf(if (counterReads == 1) 1 else 3))
                    }
                    request.contentEquals(commit) -> reply(request, byteArrayOf())
                    else -> throw AssertionError("Frame inesperado: " + request.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
                }
            },
        )

        val prepared = manager.prepare("FINISH_AUTOCAL")
        assertTrue(prepared.getBoolean("prepared"))
        assertEquals(
            "READ MAX_AUTOMATCH 0x0165:2 → WRITE NUM_AUTOMATCH_EXECUTED 0x0174 → READBACK",
            prepared.getString("commandHex"),
        )
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertArrayEquals(maxRead, requests[0])
        assertArrayEquals(counterRead, requests[1])
        assertArrayEquals(commit, requests[2])
        assertArrayEquals(counterRead, requests[3])

        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("FINISH_AUTOCAL", receipt.getString("action"))
        val details = receipt.getJSONObject("details")
        assertEquals("MAX_AUTOMATCH", details.getString("finishSource"))
        assertEquals("NUM_AUTOMATCH_EXECUTED", details.getString("finishTarget"))
        assertEquals(1, details.getInt("beforeCounter"))
        assertEquals(3, details.getInt("maxAutomatch"))
        assertEquals(3, details.getInt("committedValue"))
        assertEquals(100, details.getInt("settleMs"))
        manager.close()
    }

    @Test
    fun `readquirir um ponto usa dois vetores completos commit e so depois readback`() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        val manager = manager { request, _, _, _ ->
            requests += request.copyOf()
            val opcode = request.firstOrNull()?.toInt()?.and(0xFF)
            reply(request, if (opcode == AutoCalProtocol.READ_SCALAR || opcode == AutoCalProtocol.READ_VECTOR) byteArrayOf(1) else byteArrayOf())
        }
        val prepared = manager.preparePointDelete("GAS", 14)
        assertTrue(prepared.getBoolean("prepared"))
        assertEquals("DELETE_POINT", prepared.getString("action"))
        assertEquals(4, prepared.getJSONObject("details").getInt("zone"))
        assertFalse(prepared.getBoolean("automaticBackup"))
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        val expected = AutoCalPointDeleteProtocol.singlePointPlan(
            AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 14),
        )
        assertEquals(expected.size + 1, requests.size)
        expected.forEachIndexed { index, frame -> assertArrayEquals(frame, requests[index]) }
        assertArrayEquals(AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE), requests.last())
        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("DELETE_POINT", receipt.getString("action"))
        assertEquals("GAS", receipt.getJSONObject("pointDelete").getString("fuel"))
        assertEquals(14, receipt.getJSONObject("pointDelete").getInt("index"))
        assertFalse(receipt.getBoolean("automaticBackup"))
        manager.close()
    }

    @Test
    fun `falha em mask pontual impede commit`() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        val manager = manager { request, _, _, _ ->
            requests += request.copyOf()
            if (requests.size == 2) {
                UsbProtocolReply(
                    ok = false,
                    status = -1,
                    payload = byteArrayOf(),
                    request = request,
                    echo = byteArrayOf(),
                    error = "mask rejeitado",
                )
            } else {
                reply(request, byteArrayOf())
            }
        }
        val prepared = manager.preparePointDelete("PETROL", 5)
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        assertEquals(2, requests.size)
        assertFalse(requests.any { it.contentEquals(AutoCalPointDeleteProtocol.commit()) })
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
