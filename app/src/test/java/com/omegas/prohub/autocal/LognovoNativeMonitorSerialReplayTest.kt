package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Classe 3, com fronteira de teste explícita:
 * - requests e replies encontrados na fixture ORIGINAL passam por Mp48SerialScheduler → monitor.tick()
 * - campos NÃO capturados retornam UNCAPTURED_TEST_ONLY e NÃO viram supostas leituras reais.
 * - relógio, frames de oportunidade e etapas são SINTÉTICOS, nunca latência comprovada.
 * - Nenhum writer é instanciado; todo acesso pertence à Mp48WorkClass.READ_ONLY.
 */
class LognovoNativeMonitorSerialReplayTest {
    private val fixture: JSONObject by lazy {
        val file = listOf(
            File("tests/fixtures/portmon-lognovo-autocal-epochs-v1.json"),
            File("../tests/fixtures/portmon-lognovo-autocal-epochs-v1.json"),
        ).first { it.isFile }
        JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun key(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 0xff) }

    private class ReplayedReply(val request: ByteArray, val reply: UsbProtocolReply)

    /** Apenas responde com bytes ORIGINAL_DERIVED, conferindo checksum, tamanho e eco. */
    private fun verifiedOriginalRow(row: JSONObject): ReplayedReply {
        val request = hex(row.getString("request"))
        val original = hex(row.getString("response"))
        assertTrue("response eco", original.size >= request.size + 3)
        assertArrayEquals(request, original.copyOfRange(0, request.size))
        val status = original[request.size].toInt() and 255
        val size = original[request.size + 1].toInt() and 255
        assertEquals("wire body size", request.size + size + 3, original.size)
        assertEquals("wire checksum", original.last().toInt() and 255,
            original.copyOfRange(request.size, original.lastIndex).sumOf { it.toInt() and 255 } and 255)
        val payload = original.copyOfRange(request.size + 2, original.lastIndex)
        return ReplayedReply(request, UsbProtocolReply(
            ok = status == Mp48Protocol.STATUS_ACK,
            status = status, payload = payload, request = request, echo = request,
            rawResponse = original,
        ))
    }

    private inner class FakeSerial : Mp48SerialScheduler {
        var connected = true
        val session = 42L
        var nowMs = 10_000L
        var stage = 0
        var reads = 0
        var unsupportedReads = 0
        val requests = mutableListOf<String>()
        val originals = mutableListOf<String>()
        val epochRows get() = fixture.getJSONArray("epochs")
        val originalResponses = linkedMapOf<String, ReplayedReply>()

        init { configure(0) }

        fun configure(next: Int) {
            require(next in 0..3)
            stage = next
            originalResponses.clear()
            val cfg = fixture.getJSONObject("staticConfigurationBeforeEpochs")
            listOf("AXIS", "PRESS", "MAX", "ENABLE", "CAL").forEach {
                add(cfg.getJSONObject(it))
            }
            val e = epochRows.getJSONObject(if (stage == 0) 0 else stage - 1)
            add(e.getJSONObject(if (stage == 0) "statusBefore" else "statusAfter"))
            if (stage == 0) add(cfg.getJSONObject("COUNT")) else add(e.getJSONObject("explicitCountAfter"))
            val petrol = e.getJSONObject("petrolContext")
            add(petrol.getJSONObject("time"))
            add(petrol.getJSONObject("map"))
            add(petrol.getJSONObject("counter"))
            val gas = e.getJSONObject(if (stage == 0) "gasCurrentBefore" else "gasCurrentAfterRollover")
            add(gas.getJSONObject("time"))
            add(gas.getJSONObject("map"))
            add(gas.getJSONObject("counter"))
            if (stage > 0) {
                val prev = e.getJSONObject("gasPreviousAfterRollover")
                add(prev.getJSONObject("time"))
                add(prev.getJSONObject("map"))
            }
            add(e.getJSONObject(if (stage == 0) "mulBefore" else "mulAfter"))
        }

        fun add(row: JSONObject) {
            val x = verifiedOriginalRow(row)
            originalResponses[key(x.request)] = x
        }

        override fun isConnected() = connected
        override fun currentSessionId() = if (connected) session else 0L

        /** 3 "oportunidades" artificiais por consulta do arbitro; não são leituras LOGNOVO. */
        private var offeredFrames = 0L
        override fun liveFrameCount(): Long = (++offeredFrames) * 3L
        override fun liveFrameAgeMs(): Long = 0L

        override fun transaction(
            request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean,
            expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean,
        ): UsbProtocolReply {
            assertEquals("Não permite writes no fake", Mp48WorkClass.READ_ONLY, workClass)
            assertTrue("sessão vinculada", expectedSessionId == 0L || expectedSessionId == session)
            reads++
            val k = key(request)
            requests += k
            val original = originalResponses[k]
            if (original != null) {
                originals += k
                return original.reply
            }
            unsupportedReads++
            // Ausente no recorte: erro apenas da bancada, nunca bytes falsos nem sucesso inventado.
            // Status sintético 0xCA evita confundir recorte incompleto com silêncio de transporte;
            // sem rawResponse porque nenhum quadro foi registrado.
            return UsbProtocolReply(false, status = 0xCA, error = "UNCAPTURED_TEST_ONLY", request = request)
        }

        override fun <T> unit(
            reason: String, expectedSessionId: Long, workClass: Mp48WorkClass,
            telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T,
        ): T {
            assertEquals(Mp48WorkClass.READ_ONLY, workClass)
            return block(object : Mp48SerialUnit {
                override val sessionId: Long = session
                override fun transaction(
                    request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean,
                ) = this@FakeSerial.transaction(
                    request, reason, timeoutMs, purgeBefore, session, Mp48WorkClass.READ_ONLY, true,
                )
            })
        }
    }

    @Test
    fun completeMonitorTickReadsOriginalNativeEpochsAndNeverAcceptsOldGeneration() {
        val bus = FakeSerial()
        val tableRevisions = mutableListOf<Long>()
        val monitor = NativeAutoCalMonitor(
            serial = bus,
            calibrationBusy = { false },
            onTablesChanged = { tableRevisions += monitorRevisionHolder[0]?.invoke() ?: 0L },
            clockMs = { bus.nowMs },
        )
        monitorRevisionHolder[0] = { monitor.tablesRevision() }
        // Tempo simulado só para agendamento: nunca uma métrica de latência real do LOGNOVO.
        monitor.beginUsbSession(bus.session)
        bus.nowMs += NativeAutoCalMonitor.SESSION_SETTLE_MS + 1
        monitor.tick()
        var st = monitor.statusJson()
        assertEquals("AutoMatch inicial capturado", 0, st.getInt("autoMatchCount"))
        assertTrue("full bootstrap contém buffers reais", monitor.latestSnapshotJson().optBoolean("available"))
        assertTrue("leitura original de status foi feita", bus.originals.contains(key(AutoCalProtocol.CMD_NATIVE_STATUS)))
        assertTrue("os campos sem origem NÃO foram inventados", bus.unsupportedReads > 0)

        val epochCounts = mutableListOf<Int>()
        val snapshots = mutableListOf<Int>()
        for (step in 1..3) {
            bus.configure(step)
            bus.nowMs += 2_000L
            monitor.tick()
            st = monitor.statusJson()
            val epoch = st.getJSONObject("liveAcquisitionEpoch")
            assertEquals("contador nativo da época $step", step, epoch.getInt("nativeAutoMatchCount"))
            assertEquals("geração GNV foi isolada", step, epoch.getInt("gasGeneration"))
            assertEquals("gasolina mantém geração", 0, epoch.getInt("petrolGeneration"))
            assertTrue("comparação bloqueada sem prova RV", !epoch.getBoolean("comparisonAllowed"))
            assertEquals("sem escrita de ECU", false, st.optBoolean("appAutomaticWrite", false))
            epochCounts += epoch.getInt("nativeAutoMatchCount")
            snapshots += epoch.getInt("gasSamples")
            val projected = AutoCalUiProjection.project(
                nativeStatus = st,
                nativeSnapshot = monitor.latestSnapshotJson(),
                manualStatus = JSONObject(),
                manualSnapshot = JSONObject().put("available", false),
            )
            val visible = projected.getJSONObject("snapshot")
            val gas = AutoCalAcquisition.fromSnapshot(visible).getJSONArray("points")
            // Os 13/10/9 pontos da geração anterior não podem permanecer visíveis
            // enquanto a aquisição pós-época tem 0/1/0 contadores positivos.
            val drawn = (0 until gas.length()).count { i ->
                val item = gas.getJSONObject(i)
                item.optString("fuel") == "GNV" && item.optBoolean("draw")
            }
            assertTrue("UI nunca contém os pontos da geração GNV anterior", drawn <= snapshots.last())
        }
        assertEquals(listOf(1,2,3), epochCounts)
        assertEquals(listOf(0,1,0), snapshots)
        assertTrue("alguma tabela mudou", tableRevisions.isNotEmpty())
        assertTrue("só bytes de leitura capturados ou recusa declarada", bus.requests.isNotEmpty())
        monitor.endUsbSession()
        assertFalse(monitor.statusJson().optBoolean("appAutomaticWrite", false))
    }

    private val monitorRevisionHolder =
        arrayOfNulls<(() -> Long)>(1)
}
