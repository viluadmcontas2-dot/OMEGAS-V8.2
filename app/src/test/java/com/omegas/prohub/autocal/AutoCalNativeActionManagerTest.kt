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
    fun `ações com efeito K provado ou possível declaram mayChangeMulAct`() {
        assertTrue(AutoCalNativeActionManager.Action.MANUAL_AUTOMATCH.mayChangeMulAct)
        assertTrue(AutoCalNativeActionManager.Action.RESET_K_FACTOR.mayChangeMulAct)
        assertTrue(AutoCalNativeActionManager.Action.RESET_ALL.mayChangeMulAct)
        assertFalse(AutoCalNativeActionManager.Action.ENABLE_AUTO_CAL.mayChangeMulAct)
        assertFalse(AutoCalNativeActionManager.Action.DISABLE_AUTO_CAL.mayChangeMulAct)
        assertFalse(AutoCalNativeActionManager.Action.RESET_PETROL.mayChangeMulAct)
        assertFalse(AutoCalNativeActionManager.Action.RESET_GAS.mayChangeMulAct)
        assertFalse(AutoCalNativeActionManager.Action.DELETE_POINT.mayChangeMulAct)
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
    fun `reset k progbase escreve trinta pontos mul act e preserva before after no recibo`() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
        val readMul = AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)
        val beforePayload = ByteArray(60).also { bytes ->
            repeat(30) { index ->
                val raw = if (index == 10) 0x4CCC else 0x4000
                bytes[index * 2] = (raw and 0xFF).toByte()
                bytes[index * 2 + 1] = ((raw ushr 8) and 0xFF).toByte()
            }
        }
        val neutralPayload = ByteArray(60).also { bytes ->
            repeat(30) { index ->
                bytes[index * 2] = 0x00
                bytes[index * 2 + 1] = 0x40
            }
        }
        var mulReads = 0
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.MUL_ACT),
            transaction = { request, _, _, _ ->
                requests += request.copyOf()
                if (request.contentEquals(readMul)) {
                    mulReads += 1
                    reply(request, if (mulReads == 1) beforePayload else neutralPayload)
                } else reply(request, byteArrayOf())
            },
        )
        val prepared = manager.prepare("RESET_K_FACTOR")
        assertTrue(prepared.getBoolean("prepared"))
        assertTrue(prepared.getBoolean("mayChangeMulAct"))
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        val resetFrames = AutoCalProtocol.resetKFactorMulActFrames()
        assertEquals(32, requests.size)
        assertArrayEquals(readMul, requests.first())
        resetFrames.forEachIndexed { index, frame -> assertArrayEquals(frame, requests[index + 1]) }
        assertArrayEquals(readMul, requests.last())

        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("RESET_K_FACTOR", receipt.getString("action"))
        assertFalse(receipt.isNull("before"))
        assertTrue(receipt.getJSONObject("before").getJSONArray("fields").length() >= 1)
        assertTrue(receipt.has("after"))
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
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("FAILED", receipt.getString("outcome"))
        assertEquals("RESET_K_FACTOR", receipt.getString("action"))
        assertEquals("VERIFYING_K_RESET", receipt.getString("failedFromState"))
        assertTrue(receipt.getBoolean("mutationMayHaveStarted"))
        assertFalse(receipt.getBoolean("automaticRetry"))
        assertEquals("READBACK_MISMATCH", receipt.getString("reasonCode"))
        assertFalse(receipt.isNull("before"))
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
        val witnesses = gasReadbackFields()
        val manager = manager(
            receiptFile = receiptFile,
            onConfirmed = { confirmed.set(true) },
            fieldsForReceipt = witnesses,
        ) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_GAS.request)) {
                calls += "RESET_GAS"
                reply(request, byteArrayOf())
            } else {
                calls += "READBACK"
                reply(request, validReadPayload(request, witnesses))
            }
        }

        val prepared = manager.prepare("RESET_GAS")
        val started = manager.execute(prepared.getString("preparationId"))
        assertTrue(started.getBoolean("humanConfirmed"))
        awaitIdle(manager)

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertTrue(confirmed.get())
        assertEquals("RESET_GAS", calls.first())
        assertEquals(witnesses.size, calls.drop(1).count { it == "READBACK" })

        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("CONFIRMED", receipt.getString("outcome"))
        assertEquals("RESET_GAS", receipt.getString("action"))
        assertEquals("02 24 04 02 2C", receipt.getString("commandHex"))
        assertTrue(receipt.getBoolean("readbackValid"))
        assertEquals(
            witnesses.map { it.key },
            receipt.getJSONArray("readbackWitnesses").let { array ->
                List(array.length()) { index -> array.getString(index) }
            },
        )
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
        val witnesses = petrolReadbackFields()
        val manager = manager(receiptFile = receiptFile, fieldsForReceipt = witnesses) { request, _, _, _ ->
            if (request.contentEquals(AutoCalNativeActionManager.Action.RESET_PETROL.request)) {
                resetCalls.incrementAndGet()
                reply(request, byteArrayOf())
            } else {
                reply(request, validReadPayload(request, witnesses))
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
        val witnesses = gasReadbackFields()
        val manager = manager(fieldsForReceipt = witnesses) { request, _, _, _ ->
            requests += request.copyOf()
            val readPayload = validReadPayloadOrNull(request, witnesses)
            reply(request, readPayload ?: byteArrayOf())
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
        assertEquals(expected.size + witnesses.size, requests.size)
        expected.forEachIndexed { index, frame -> assertArrayEquals(frame, requests[index]) }
        witnesses.forEachIndexed { index, field ->
            assertArrayEquals(AutoCalProtocol.read(field), requests[expected.size + index])
        }
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

        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("FAILED", receipt.getString("outcome"))
        assertEquals("DELETE_POINT", receipt.getString("action"))
        assertEquals("SENDING_ACTION", receipt.getString("failedFromState"))
        assertTrue(receipt.getBoolean("mutationMayHaveStarted"))
        assertFalse(receipt.getBoolean("automaticRetry"))
        assertEquals("ECU_ACK_MISSING", receipt.getString("reasonCode"))
        assertEquals("PETROL", receipt.getJSONObject("pointDelete").getString("fuel"))
        assertEquals(5, receipt.getJSONObject("pointDelete").getInt("index"))
        manager.close()
    }

    @Test
    fun `falha antes da primeira escrita fica auditada sem afirmar mutacao`() {
        val readMul = AutoCalProtocol.read(AutoCalProtocol.MUL_ACT)
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.MUL_ACT),
            transaction = { request, _, _, _ ->
                if (request.contentEquals(readMul)) {
                    UsbProtocolReply(
                        ok = false,
                        status = -1,
                        payload = byteArrayOf(),
                        request = request,
                        echo = byteArrayOf(),
                        error = "A ECU não confirmou MUL_ACT antes da ação",
                    )
                } else reply(request, byteArrayOf())
            },
        )
        val prepared = manager.prepare("RESET_K_FACTOR")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals("FAILED", manager.statusJson().getString("state"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("FAILED", receipt.getString("outcome"))
        assertEquals("READING_BEFORE", receipt.getString("failedFromState"))
        assertFalse(receipt.getBoolean("mutationMayHaveStarted"))
        assertFalse(receipt.getBoolean("automaticRetry"))
        assertEquals("ECU_ACK_MISSING", receipt.getString("reasonCode"))
        assertFalse(receipt.has("before"))
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
    fun `toggle operacional nao escreve durante comunicacao insegura`() {
        val calls = AtomicInteger(0)
        val manager = AutoCalNativeActionManager(
            receiptFile = temporaryFile(),
            isConnected = { true },
            currentSessionId = { 1L },
            otherCalibrationBusy = { false },
            unsafeMutationReason = { "Telemetria não está atual; aguarde novos quadros antes de gravar" },
            transaction = { request, _, _, _ ->
                calls.incrementAndGet()
                reply(request, byteArrayOf())
            },
            fieldsForReceipt = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
        )

        val result = manager.prepare("DISABLE_AUTO_CAL")
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("error").contains("Telemetria não está atual"))
        assertEquals(0, calls.get())
        manager.close()
    }

    @Test
    fun `ack e readback generico nao confirmam reset gas`() {
        val writes = AtomicInteger(0)
        val manager = manager(
            fieldsForReceipt = listOf(AutoCalProtocol.AUTO_CAL_ENABLE),
        ) { request, _, _, _ ->
            if ((request[0].toInt() and 0xFF) == 0x02) {
                writes.incrementAndGet()
                reply(request, byteArrayOf())
            } else {
                // AUTO_CAL_ENABLE válido não é testemunha do efeito RESET_GAS.
                reply(request, byteArrayOf(1))
            }
        }

        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)

        assertEquals(1, writes.get())
        assertEquals("FAILED", manager.statusJson().getString("state"))
        assertTrue(manager.statusJson().getString("message").contains("NUM_BUF_UPD_GAS"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertTrue(receipt.getBoolean("mutationMayHaveStarted"))
        assertFalse(receipt.getBoolean("automaticRetry"))
        manager.close()
    }

    @Test
    fun `preparacao publica testemunhas especificas por comando`() {
        val manager = manager { request, _, _, _ -> reply(request, byteArrayOf()) }

        fun witnesses(action: String): List<String> {
            val prepared = manager.prepare(action)
            val values = prepared.getJSONArray("readbackWitnesses")
            manager.clearPreparation()
            return List(values.length()) { index -> values.getString(index) }
        }

        assertEquals(gasReadbackFields().map { it.key }, witnesses("RESET_GAS"))
        assertEquals(petrolReadbackFields().map { it.key }, witnesses("RESET_PETROL"))
        assertEquals(
            (petrolReadbackFields() + gasReadbackFields() + AutoCalProtocol.MUL_ACT)
                .distinctBy { it.identity }
                .map { it.key },
            witnesses("RESET_ALL"),
        )
        assertEquals(listOf("MUL_ACT"), witnesses("MANUAL_AUTOMATCH"))
        assertEquals(listOf("AUTO_CAL_ENABLE"), witnesses("ENABLE_AUTO_CAL"))
        assertEquals(listOf("NUM_AUTOMATCH_EXECUTED"), witnesses("FINISH_AUTOCAL"))
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

    private fun petrolReadbackFields(): List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.NUM_BUF_UPD_PETR,
        AutoCalProtocol.PETR_INJ_TBUF,
        AutoCalProtocol.MNFLD_PRESS_BUF,
        AutoCalProtocol.ACQUIRED_ZONES_PETROL,
    )

    private fun gasReadbackFields(): List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.NUM_BUF_UPD_GAS,
        AutoCalProtocol.PETR_INJ_TBUF_GAS,
        AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
        AutoCalProtocol.ACQUIRED_ZONES_GAS,
    )

    private fun validReadPayloadOrNull(
        request: ByteArray,
        fields: List<AutoCalProtocol.Field>,
    ): ByteArray? {
        val field = fields.firstOrNull { AutoCalProtocol.read(it).contentEquals(request) } ?: return null
        val count = field.expectedElementsHint ?: 1
        val width = if (field.encoding == AutoCalProtocol.Encoding.U8_OR_U16_LE) 1 else field.encoding.bytesPerElement
        return ByteArray(count * width)
    }

    private fun validReadPayload(request: ByteArray, fields: List<AutoCalProtocol.Field>): ByteArray =
        requireNotNull(validReadPayloadOrNull(request, fields)) {
            "Leitura inesperada: " + request.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
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
