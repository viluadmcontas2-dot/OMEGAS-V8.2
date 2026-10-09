package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prova classe 3: respostas originais do LOGNOVO alimentam o decoder MP48,
 * AutoCalSnapshotBuilder e a guarda de epocas Kotlin usada pelo monitor.
 *
 * Sem USB, sem gravacao e sem relogio fabricado atribuido ao ProgBase.
 * Os campos antes/depois provem de brackets reais, mas NAO sao um snapshot
 * unico instantaneo. Nenhuma inferencia de latencia fisica e permitida.
 */
class LognovoNativeEpochKotlinReplayTest {
    private val session = 408L
    private val source: JSONObject by lazy {
        val file = listOf(
            File("tests/fixtures/portmon-lognovo-autocal-epochs-v1.json"),
            File("../tests/fixtures/portmon-lognovo-autocal-epochs-v1.json"),
        ).first { it.isFile }
        JSONObject(file.readText(Charsets.UTF_8))
    }

    /** Verifica o envelope ORIGINAL, sem construir request novo no simulador. */
    private fun originalPayload(row: JSONObject, expectedRequest: ByteArray): ByteArray {
        fun hex(text: String): ByteArray =
            text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val request = hex(row.getString("request"))
        val response = hex(row.getString("response"))
        assertArrayEquals("O request da fixture nao coincide com o descritor existente", expectedRequest, request)
        assertTrue("Eco ausente", response.take(request.size).toByteArray().contentEquals(request))
        val suffix = response.copyOfRange(request.size, response.size)
        assertTrue("Resposta curta", suffix.size >= 3)
        assertEquals("ACK da ECU", Mp48Protocol.STATUS_ACK, suffix[0].toInt() and 0xff)
        val size = suffix[1].toInt() and 0xff
        assertEquals("Payload original", size + 3, suffix.size)
        assertEquals("Checksum original", suffix.last().toInt() and 0xff,
            suffix.dropLast(1).sumOf { it.toInt() and 0xff } and 0xff)
        return suffix.copyOfRange(2, 2 + size)
    }

    private fun snapshot(vararg readings: Pair<AutoCalProtocol.Field, JSONObject>): AutoCalSnapshot {
        // capturedAtMs e artificial APENAS para a instancia do builder; sem medir cadencia.
        val observations = readings.mapIndexed { i, (field, row) ->
            AutoCalReadObservation(
                field = field,
                status = Mp48Protocol.STATUS_ACK,
                payload = originalPayload(row, AutoCalProtocol.read(field)),
                capturedAtMs = 10_000L + i,
            )
        }
        return AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = readings.map { it.first },
            source = AutoCalSnapshotSource.REPLAY,
            sessionId = "LOGNOVO-ORIGINAL-READ-ONLY",
        )
    }

    private fun raw(snap: AutoCalSnapshot, field: AutoCalProtocol.Field): IntArray {
        val value = snap.field(field)!!
        assertEquals("${field.key} nao foi decodificado", AutoCalFieldStatus.VALID, value.status)
        return value.rawValues
    }

    private fun gasGroup(records: JSONObject): AutoCalSnapshot = snapshot(
        AutoCalProtocol.PETR_INJ_TBUF_GAS to records.getJSONObject("time"),
        AutoCalProtocol.MNFLD_PRESS_BUF_GAS to records.getJSONObject("map"),
        AutoCalProtocol.NUM_BUF_UPD_GAS to records.getJSONObject("counter"),
    )

    @Test
    fun threeOriginalNativeAutoMatchesRejectOldGasAndAcceptOnlyCurrentEpochCounters() {
        val epochs = source.getJSONArray("epochs")
        assertEquals("A origem so documenta tres AutoMatch", 3, epochs.length())
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(session)
        val baseline = AutoCalProtocol.decodeNativeStatus(
            Mp48Protocol.STATUS_ACK,
            originalPayload(epochs.getJSONObject(0).getJSONObject("statusBefore"), AutoCalProtocol.CMD_NATIVE_STATUS),
        ).autoMatchCount
        assertEquals(0, baseline)
        assertFalse(gate.nativeCounter(session, baseline))

        for (i in 0 until epochs.length()) {
            val epoch = epochs.getJSONObject(i)
            val beforeCount = AutoCalProtocol.decodeNativeStatus(
                Mp48Protocol.STATUS_ACK,
                originalPayload(epoch.getJSONObject("statusBefore"), AutoCalProtocol.CMD_NATIVE_STATUS),
            ).autoMatchCount
            val afterCount = AutoCalProtocol.decodeNativeStatus(
                Mp48Protocol.STATUS_ACK,
                originalPayload(epoch.getJSONObject("statusAfter"), AutoCalProtocol.CMD_NATIVE_STATUS),
            ).autoMatchCount
            assertEquals("contador antes da epoca ${i + 1}", i, beforeCount)
            assertEquals("contador depois da epoca ${i + 1}", i + 1, afterCount)

            val before = gasGroup(epoch.getJSONObject("gasCurrentBefore"))
            val after = gasGroup(epoch.getJSONObject("gasCurrentAfterRollover"))
            val petrolRows = epoch.getJSONObject("petrolContext")
            val petrol = snapshot(
                AutoCalProtocol.PETR_INJ_TBUF to petrolRows.getJSONObject("time"),
                AutoCalProtocol.MNFLD_PRESS_BUF to petrolRows.getJSONObject("map"),
                AutoCalProtocol.NUM_BUF_UPD_PETR to petrolRows.getJSONObject("counter"),
            )
            val petrolCounters = raw(petrol, AutoCalProtocol.NUM_BUF_UPD_PETR)
            val gasBefore = raw(before, AutoCalProtocol.NUM_BUF_UPD_GAS)
            val gasAfter = raw(after, AutoCalProtocol.NUM_BUF_UPD_GAS)
            assertEquals(18, petrolCounters.size)
            assertEquals(18, gasBefore.size)
            assertEquals(18, gasAfter.size)
            assertEquals(18, raw(after, AutoCalProtocol.PETR_INJ_TBUF_GAS).size)
            assertEquals(18, raw(after, AutoCalProtocol.MNFLD_PRESS_BUF_GAS).size)
            assertTrue("AutoMatch deve inaugurar nova geracao", gate.nativeCounter(session, afterCount))
            val view = gate.view()
            assertEquals(afterCount, view.nativeAutoMatchCount)
            assertEquals(afterCount, view.gasGeneration)
            assertEquals(0, view.petrolGeneration)
            assertTrue(view.gasPending)
            assertFalse(view.comparisonAllowed)

            // Dados GNV da geracao anterior NAO podem reaparecer com contador anterior.
            assertFalse(gate.acquisitionGroup(session, beforeCount, petrolCounters, gasBefore))
            assertFalse(gate.comparisonAllowedForTest())

            // Aqui ambos os vetores sao do bracket POS-epoca, mas nao do mesmo instante:
            // teste de autoridade de contador/forma, nao equivalencia temporal.
            assertTrue(gate.acquisitionGroup(session, afterCount, petrolCounters, gasAfter))
            assertEquals(gasAfter.count { it > 0 }, gate.view().gasSamples)
            assertEquals(petrolCounters.count { it > 0 }, gate.view().petrolSamples)
            assertFalse("Nao comparar sem referencia nativa renovada", gate.view().comparisonAllowed)
            assertFalse(gate.referenceGroup(session, beforeCount))
        }
        assertEquals(3, gate.view().gasGeneration)
    }

    @Test
    fun originalPreviousGasMustDisappearFromUiProjectionImmediatelyAfterNativeCounter() {
        val first = source.getJSONArray("epochs").getJSONObject(0)
        val before = gasGroup(first.getJSONObject("gasCurrentBefore"))
        val previousGas = raw(before, AutoCalProtocol.NUM_BUF_UPD_GAS)
        assertEquals(13, previousGas.count { it > 0 })
        val uiBefore = AutoCalAcquisition.fromSnapshot(before.toJson())
        val pointsBefore = uiBefore.getJSONArray("points")
        assertTrue((0 until pointsBefore.length()).any { i ->
            val point = pointsBefore.getJSONObject(i)
            point.optString("fuel") == "GNV" && point.optBoolean("draw")
        })

        // A ECU ainda nao entregou os novos buffers quando o contador mudou.
        // A projecao nao pode reutilizar a geracao GNV anterior enquanto espera.
        val pending = JSONObject()
            .put("usbSessionId", session)
            .put("nativeAutoMatchCount", 1)
            .put("petrolPending", false)
            .put("gasPending", true)
            .put("petrolReferencePending", false)
            .put("gasReferencePending", true)
            .put("comparisonAllowed", false)
        val masked = AutoCalUiProjection.maskedAcquisition(before.toJson(), pending, false)
        for (key in listOf("PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")) {
            val fields = masked.getJSONArray("fields")
            val field = (0 until fields.length()).map { fields.getJSONObject(it) }
                .single { it.optString("key") == key }
            assertEquals("A leitura antiga deve ficar STALE_EPOCH", "STALE_EPOCH", field.getString("status"))
            assertEquals(0, field.getJSONArray("rawValues").length())
        }
        val after = AutoCalAcquisition.fromSnapshot(masked).getJSONArray("points")
        assertFalse("Ponto fantasma da epoca GNV anterior", (0 until after.length()).any { i ->
            val point = after.getJSONObject(i)
            point.optString("fuel") == "GNV" && point.optBoolean("draw")
        })
        // A guarda do motor pode limpar a projeção de modo imediato, mas o tempo
        // real para receber o evento depende do probe USB e e medido separadamente.
    }

    @Test
    fun corruptOriginalFrameCannotBeDecodedAsRealMeasurement() {
        val first = source.getJSONArray("epochs").getJSONObject(0)
            .getJSONObject("gasCurrentAfterRollover").getJSONObject("counter")
        val incorrect = first.getString("request").replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = originalPayload(first, incorrect)
        assertEquals(36, payload.size)
        val shortened = payload.copyOf(35)
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(AutoCalReadObservation(AutoCalProtocol.NUM_BUF_UPD_GAS,
                Mp48Protocol.STATUS_ACK, shortened, 100L)),
            expectedFields = listOf(AutoCalProtocol.NUM_BUF_UPD_GAS),
            source = AutoCalSnapshotSource.REPLAY,
        )
        assertEquals(AutoCalFieldStatus.INVALID,
            snapshot.field(AutoCalProtocol.NUM_BUF_UPD_GAS)!!.status)
    }

    /** Explicita que o gate so libera comparacoes apos referencia nova. */
    private fun NativeAutoCalAcquisitionEpoch.comparisonAllowedForTest(): Boolean = view().comparisonAllowed
}
