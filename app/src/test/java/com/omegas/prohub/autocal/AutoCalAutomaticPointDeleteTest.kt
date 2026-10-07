package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Target
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Classe 2: caminho AUTOMÁTICO do DELETE_POINT (spec 2026-10-07-autocal-apagar-lenta) e as regras de
 * readback que valem também para o manual (A4, A4b, ECU#3, ECU#4).
 */
class AutoCalAutomaticPointDeleteTest {

    /** ECU falsa: responde leituras com o estado "antes" até ver o commit 01 24 05; depois, com o estado "depois". */
    private class FakeEcu {
        val before = mutableMapOf<String, IntArray>()
        val after = mutableMapOf<String, IntArray>()
        val requests = CopyOnWriteArrayList<ByteArray>()
        var committed = false
        var failOn: ((ByteArray) -> Boolean)? = null

        init {
            listOf(before, after).forEach { state ->
                ALL_FIELDS.forEach { field -> state[field.key] = IntArray(field.expectedElementsHint ?: 1) }
            }
        }

        fun set(field: AutoCalProtocol.Field, vararg values: Pair<Int, Int>, phase: String = "both") {
            if (phase != "after") values.forEach { (i, v) -> before.getValue(field.key)[i] = v }
            if (phase != "before") values.forEach { (i, v) -> after.getValue(field.key)[i] = v }
        }

        fun transaction(request: ByteArray): UsbProtocolReply {
            requests += request.copyOf()
            if (failOn?.invoke(request) == true) {
                return UsbProtocolReply(ok = false, status = -1, request = request, error = "sem resposta")
            }
            if (request.contentEquals(AutoCalPointDeleteProtocol.commit())) committed = true
            val field = ALL_FIELDS.firstOrNull { AutoCalProtocol.read(it).contentEquals(request) }
                ?: return ack(request, byteArrayOf())
            val values = (if (committed) after else before).getValue(field.key)
            return ack(request, encode(field, values))
        }

        fun writes(): List<ByteArray> = requests.filter { req -> ALL_FIELDS.none { AutoCalProtocol.read(it).contentEquals(req) } }

        private fun ack(request: ByteArray, payload: ByteArray) =
            UsbProtocolReply(true, Mp48Protocol.STATUS_ACK, payload, request, request)

        private fun encode(field: AutoCalProtocol.Field, values: IntArray): ByteArray =
            if (field.encoding == AutoCalProtocol.Encoding.U8) ByteArray(values.size) { values[it].toByte() }
            else ByteArray(values.size * 2).also { bytes ->
                values.forEachIndexed { i, v ->
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
                }
            }
    }

    private val receipts = CopyOnWriteArrayList<JSONObject>()
    private val failures = CopyOnWriteArrayList<JSONObject>()

    @Test
    fun `apagamento automatico so GNV envia mascara de gasolina toda KEEP e recibo automatico`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3, 6 to 2, phase = "before")
        val manager = manager(ecu)
        val started = manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4), Target(Fuel.GAS, 6)), evidence())
        assertTrue(started.toString(), started.getBoolean("ok"))
        assertFalse(started.getBoolean("humanConfirmed"))
        awaitIdle(manager)

        // Contrato dos bytes: com só alvos GNV, a máscara 0x016D vai inteira 18×1.
        val petrolMask = AutoCalProtocol.writeVectorU8(AutoCalPointDeleteProtocol.PETROL_DELETE_ADDRESS, IntArray(18) { 1 })
        val gasMask = AutoCalProtocol.writeVectorU8(
            AutoCalPointDeleteProtocol.GAS_DELETE_ADDRESS,
            IntArray(18) { if (it == 4 || it == 6) 0 else 1 },
        )
        val writes = ecu.writes()
        assertEquals(3, writes.size)
        assertArrayEquals(gasMask, writes[0])
        assertArrayEquals(petrolMask, writes[1])
        assertArrayEquals(AutoCalPointDeleteProtocol.commit(), writes[2])

        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
        assertTrue(manager.statusJson().getBoolean("automatic"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("CONFIRMED", receipt.getString("outcome"))
        assertTrue(receipt.getBoolean("automatic"))
        assertFalse(receipt.getBoolean("humanConfirmed"))
        assertFalse(receipt.getBoolean("manualOnly"))
        assertEquals("lenta", receipt.getJSONObject("automationEvidence").getString("reason"))
        val details = receipt.getJSONObject("details")
        assertTrue(details.getBoolean("effective"))
        assertFalse(details.getJSONObject("petrolGuard").getBoolean("abnormal"))
        assertEquals(1, receipts.size)
        assertTrue(receipts.single().getBoolean("automatic"))
    }

    @Test
    fun `automatico recusa alvo de gasolina sem tocar a ECU`() {
        val ecu = FakeEcu()
        val manager = manager(ecu)
        val result = manager.executeAutomaticPointDelete(listOf(Target(Fuel.PETROL, 4)), evidence())
        assertFalse(result.getBoolean("ok"))
        assertFalse(result.getBoolean("retryLater"))
        assertTrue(ecu.requests.isEmpty())
    }

    @Test
    fun `colisao com preparacao manual guarda ocupada ou busy vira tentar depois sem bytes`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3)
        val guard = SerialWriteGuard()
        val manager = manager(ecu, guard = guard)

        // Preparação manual pendente: o automático espera o dono.
        assertTrue(manager.prepare("RESET_GAS").getBoolean("prepared"))
        val waitingOwner = manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        assertFalse(waitingOwner.getBoolean("ok"))
        assertTrue(waitingOwner.getBoolean("retryLater"))
        manager.clearPreparation()

        // Trava serial com a escrita K.
        assertTrue(guard.tryAcquire(SerialWriteGuard.OWNER_K_FACTOR))
        val guardBusy = manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        assertFalse(guardBusy.getBoolean("ok"))
        assertTrue(guardBusy.getBoolean("retryLater"))
        assertFalse(manager.isBusy())
        guard.release(SerialWriteGuard.OWNER_K_FACTOR)

        // Outra calibração ocupada.
        val busyManager = manager(ecu, otherBusy = { true })
        val otherBusy = busyManager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        assertTrue(otherBusy.getBoolean("retryLater"))

        assertTrue(ecu.requests.isEmpty())
        assertTrue(manager.receiptsJson().length() == 0)
        busyManager.close()
        manager.close()
    }

    @Test
    fun `falha no meio com mascara GAS aceita e commit sem resposta registra mutacao possivel`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3)
        ecu.failOn = { it.contentEquals(AutoCalPointDeleteProtocol.commit()) }
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertEquals("FAILED", receipt.getString("outcome"))
        assertTrue(receipt.getBoolean("automatic"))
        assertFalse(receipt.getBoolean("humanConfirmed"))
        assertTrue(receipt.getBoolean("mutationMayHaveStarted"))
        assertEquals(1, failures.size)
        assertTrue(failures.single().getBoolean("mutationMayHaveStarted"))
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `readback ineficaz no automatico falha com effective false`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3) // depois do commit continua 3
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertFalse(receipt.getBoolean("effective"))
        assertEquals("READBACK_MISMATCH", receipt.getString("reasonCode"))
        assertFalse(failures.single().getBoolean("effective"))
    }

    @Test
    fun `readback que mostra contador menor que o anterior e eficaz`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 5, phase = "before")
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 1, phase = "after") // já readquiriu uma vez
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        assertEquals("CONFIRMED", manager.statusJson().getString("state"))
    }

    @Test
    fun `alvo ja vazio na releitura antes nao gera escrita`() {
        val ecu = FakeEcu() // contador GNV 0 em todas as bandas
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        assertTrue(ecu.writes().isEmpty())
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertFalse(receipt.getBoolean("mutationMayHaveStarted"))
        assertEquals("POINT_ALREADY_EMPTY", receipt.getString("reasonCode"))
    }

    @Test
    fun `gasolina aprendendo normalmente durante o apagamento nao e anormal`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3, phase = "before")
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_PETR, 7 to 2, phase = "before")
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_PETR, 7 to 3, phase = "after")
        ecu.set(AutoCalProtocol.PETR_INJ_TBUF, 7 to 1_500, phase = "before")
        ecu.set(AutoCalProtocol.PETR_INJ_TBUF, 7 to 1_520, phase = "after")
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        val guard = manager.receiptsJson().getJSONObject(0).getJSONObject("details").getJSONObject("petrolGuard")
        assertTrue(guard.getBoolean("changed"))
        assertFalse(guard.getBoolean("abnormal"))
    }

    @Test
    fun `gasolina zerada durante o apagamento e anormal`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3, phase = "before")
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_PETR, 7 to 4, phase = "before")
        ecu.set(AutoCalProtocol.PETR_INJ_TBUF, 7 to 1_500, phase = "before")
        val manager = manager(ecu)
        manager.executeAutomaticPointDelete(listOf(Target(Fuel.GAS, 4)), evidence())
        awaitIdle(manager)
        val receipt = manager.receiptsJson().getJSONObject(0)
        val guard = receipt.getJSONObject("details").getJSONObject("petrolGuard")
        assertTrue(guard.getBoolean("changed"))
        assertTrue(guard.getBoolean("abnormal"))
        assertEquals(7, guard.getJSONArray("bands").getJSONObject(0).getInt("band"))
    }

    @Test
    fun `readback manual ineficaz falha e preparacao recusa ponto ja vazio`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 4 to 3)
        val known = mapOf(AutoCalProtocol.NUM_BUF_UPD_GAS.key to IntArray(18).also { it[4] = 3 })
        val manager = manager(ecu, lastKnown = { known[it.key] })

        val empty = manager.preparePointDelete("GAS", 5)
        assertFalse(empty.getBoolean("ok"))
        assertTrue(empty.getString("error").contains("já está vazio"))

        val prepared = manager.preparePointDelete("GAS", 4)
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        val receipt = manager.receiptsJson().getJSONObject(0)
        assertFalse(receipt.getBoolean("effective"))
        assertTrue(receipt.getBoolean("humanConfirmed"))
        assertFalse(receipt.getBoolean("automatic"))
    }

    @Test
    fun `lote manual descarta ponto vazio e segue com os demais`() {
        val ecu = FakeEcu()
        val known = mapOf(AutoCalProtocol.NUM_BUF_UPD_GAS.key to IntArray(18).also { it[4] = 3 })
        val manager = manager(ecu, lastKnown = { known[it.key] })
        val prepared = manager.preparePointDeletes(listOf(Target(Fuel.GAS, 4), Target(Fuel.GAS, 5)))
        assertTrue(prepared.getBoolean("prepared"))
        assertEquals(1, prepared.getJSONArray("skippedEmpty").length())
        assertEquals(4, prepared.getJSONObject("details").getInt("index"))
        manager.close()
    }

    @Test
    fun `reset GNV tolera uma banda readquirindo e compara com o antes`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 3 to 1, phase = "after") // banda 3 voltou a aprender
        ecu.committed = true // reset não usa o commit de pontos: responde já o estado "depois"
        val known = mapOf(AutoCalProtocol.NUM_BUF_UPD_GAS.key to IntArray(18) { 4 })
        val manager = manager(ecu, lastKnown = { known[it.key] })
        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertEquals(manager.statusJson().toString(), "CONFIRMED", manager.statusJson().getString("state"))
    }

    @Test
    fun `reset GNV com duas bandas cheias continua falhando`() {
        val ecu = FakeEcu()
        ecu.set(AutoCalProtocol.NUM_BUF_UPD_GAS, 3 to 1, 8 to 1, phase = "after")
        ecu.committed = true
        val manager = manager(ecu)
        val prepared = manager.prepare("RESET_GAS")
        manager.execute(prepared.getString("preparationId"))
        awaitIdle(manager)
        assertEquals("FAILED", manager.statusJson().getString("state"))
        assertTrue(manager.statusJson().getString("message").contains("não zerados"))
    }

    // ---- apoio ----

    private fun evidence() = JSONObject()
        .put("reason", "lenta")
        .put("bands", org.json.JSONArray().put(JSONObject().put("band", 4).put("frames", 5).put("idleFraction", 1.0)))

    private fun manager(
        ecu: FakeEcu,
        guard: SerialWriteGuard = SerialWriteGuard(),
        otherBusy: () -> Boolean = { false },
        lastKnown: (AutoCalProtocol.Field) -> IntArray? = { null },
    ) = AutoCalNativeActionManager(
        receiptFile = temporaryFile(),
        isConnected = { true },
        currentSessionId = { 1L },
        otherCalibrationBusy = otherBusy,
        transaction = { request, _, _, _ -> ecu.transaction(request) },
        onConfirmed = { receipts += it },
        onFailed = { failures += it },
        guard = guard,
        lastKnownVector = lastKnown,
    )

    private fun temporaryFile(): File = Files.createTempDirectory("autocal-auto-delete").resolve("receipts.json").toFile()

    private fun awaitIdle(manager: AutoCalNativeActionManager) {
        repeat(600) {
            if (!manager.isBusy()) return
            Thread.sleep(5L)
        }
        throw AssertionError("Ação AutoCal não finalizou")
    }

    companion object {
        private val ALL_FIELDS = listOf(
            AutoCalProtocol.NUM_BUF_UPD_GAS, AutoCalProtocol.PETR_INJ_TBUF_GAS, AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
            AutoCalProtocol.ACQUIRED_ZONES_GAS, AutoCalProtocol.NUM_BUF_UPD_PETR, AutoCalProtocol.PETR_INJ_TBUF,
            AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalProtocol.ACQUIRED_ZONES_PETROL,
        )
    }
}
