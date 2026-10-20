package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Escritor da volta à gasolina com ECU falsa em BYTES: `09 3E 00` / `12 3E 00 v`, `29 7B 00` / `29 7C 00` / `14 7B|7C 00 i lo hi`. */
class VoltaGasolinaWriterTest {
    private class FakeSerial(var tempo: Int) : Mp48SerialScheduler {
        val rif = VoltaGasolinaCore.table(1250, 1500).rif.copyOf()
        val coeff = VoltaGasolinaCore.table(1250, 1500).coeff.copyOf()
        var nakWrites = false
        val frames = CopyOnWriteArrayList<String>()
        val classes = CopyOnWriteArrayList<Mp48WorkClass>()
        private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        override fun isConnected() = true
        override fun currentSessionId() = 7L
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean): UsbProtocolReply = handle(request)
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T {
            classes += workClass
            return block(object : Mp48SerialUnit {
                override val sessionId = 7L
                override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = handle(request)
            })
        }
        private fun ok(request: ByteArray, p: ByteArray) = UsbProtocolReply(ok = true, status = 0x53, payload = p, request = request, rawResponse = request + byteArrayOf(0x53, p.size.toByte()) + p)
        private fun words(v: IntArray) = ByteArray(v.size * 2) { i -> ((v[i / 2] shr (8 * (i % 2))) and 0xFF).toByte() }
        private fun handle(request: ByteArray): UsbProtocolReply {
            frames += hex(request)
            val r = IntArray(request.size) { request[it].toInt() and 0xFF }
            return when {
                r[0] == 0x09 && r[1] == 0x3E -> ok(request, byteArrayOf(tempo.toByte()))
                r[0] == 0x29 && r[1] == 0x7B -> ok(request, words(rif))
                r[0] == 0x29 && r[1] == 0x7C -> ok(request, words(coeff))
                nakWrites -> UsbProtocolReply(ok = false, status = 0xCA, request = request, rawResponse = request + byteArrayOf(0xCA.toByte(), 1, 0x10), error = "NACK")
                r[0] == 0x12 && r[1] == 0x3E -> { tempo = r[3]; UsbProtocolReply(ok = true, status = 0x53, request = request, rawResponse = request + byteArrayOf(0x53, 0, 0x53)) }
                r[0] == 0x14 -> { (if (r[1] == 0x7B) rif else coeff)[r[3]] = r[4] or (r[5] shl 8); UsbProtocolReply(ok = true, status = 0x53, request = request, rawResponse = request + byteArrayOf(0x53, 0, 0x53)) }
                else -> UsbProtocolReply(ok = false, status = -1, request = request, error = "inesperado")
            }
        }
    }

    private class Sink {
        val reads = CopyOnWriteArrayList<String>()
        val records = CopyOnWriteArrayList<JSONObject>()
    }

    private fun writer(serial: FakeSerial, sink: Sink) = VoltaGasolinaWriter(
        serial = serial,
        record = { _, d -> sink.records += d },
        onRead = { _, name, _, values, trigger -> sink.reads += "$name:${values.toList()}:$trigger" },
    )

    private fun waitDone(w: VoltaGasolinaWriter): JSONObject {
        repeat(5_000_000) {
            val s = JSONObject(w.statusJson())
            if (s.getString("state") == "DONE" && !s.getBoolean("busy")) return s
            Thread.yield()
        }
        throw AssertionError("não terminou: ${w.statusJson()}")
    }

    @Test fun `ler agora - tempo e tabela, sem nenhuma escrita`() {
        val serial = FakeSerial(0)
        val sink = Sink()
        val w = writer(serial, sink)
        assertTrue(w.readCurrent().getBoolean("ok"))
        repeat(5_000_000) { if (sink.reads.size >= 3) return@repeat; Thread.yield() }
        assertTrue(sink.reads.any { it.startsWith("TEMPO_RITORNO_BENZINA:[0]") })
        assertTrue(sink.reads.any { it.startsWith("RIF_PRESS_DIFF:[1250, 1274,") })
        assertTrue(sink.reads.any { it.startsWith("COEFF_PRESS_DIFF:[1089, 975,") }) // com sinal
        assertEquals(listOf("09 3E 00 47", "29 7B 00 A4", "29 7C 00 A5"), serial.frames.toList())
        assertEquals(listOf(Mp48WorkClass.READ_ONLY), serial.classes.toList())
    }

    @Test fun `desligar - um toque grava 255, relê, e Desfazer volta ao tempo anterior`() {
        val serial = FakeSerial(2)
        val w = writer(serial, Sink())
        assertTrue(w.apply("""{"acao":"desligar"}""").getBoolean("ok"))
        val s = waitDone(w)
        assertEquals("CONFIRMED", s.getString("outcome"))
        assertEquals(255, serial.tempo)
        assertTrue(serial.frames.contains("12 3E 00 FF 4F"))
        assertTrue(s.getJSONObject("undo").getBoolean("available"))
        serial.frames.clear()
        assertTrue(w.apply("""{"acao":"desfazer"}""").getBoolean("ok"))
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        assertEquals(2, serial.tempo)
        assertTrue(serial.frames.contains("12 3E 00 02 52"))
    }

    @Test fun `ligar depois de desligar restaura o tempo guardado como o original`() {
        val serial = FakeSerial(3)
        val w = writer(serial, Sink())
        w.apply("""{"acao":"desligar"}"""); waitDone(w)
        w.apply("""{"acao":"ligar"}"""); waitDone(w)
        assertEquals(3, serial.tempo)
        assertTrue(serial.frames.contains("12 3E 00 03 53"))
    }

    @Test fun `espera grava 0 a 3 segundos`() {
        val serial = FakeSerial(0)
        val w = writer(serial, Sink())
        w.apply("""{"acao":"espera","segundos":1}""")
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        assertEquals(1, serial.tempo)
        assertFalse(w.apply("""{"acao":"espera","segundos":9}""").getBoolean("ok"))
    }

    @Test fun `pressao - um toque grava os 30 quadros do original, rele e Desfazer volta a tabela de antes`() {
        val serial = FakeSerial(0)
        val antes = serial.rif.copyOf() to serial.coeff.copyOf()
        val w = writer(serial, Sink())
        assertTrue(JSONObject(w.statusJson()).getBoolean("pressureWritable"))
        assertTrue(w.apply("""{"acao":"pressao","mbar":1300}""").getBoolean("ok"))
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        val want = VoltaGasolinaCore.table(1300, 1500)
        assertEquals(VoltaGasolinaCore.tableFrames(want.rif, want.coeff).map { f -> f.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } },
            serial.frames.filter { it.startsWith("14") })
        assertTrue(w.apply("""{"acao":"desfazer"}""").getBoolean("ok"))
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        assertEquals(antes.first.toList(), serial.rif.toList())
        assertEquals(antes.second.toList(), serial.coeff.toList())
    }

    @Test fun `salvar - espera e pressao num toque, e um Desfazer volta as duas`() {
        val serial = FakeSerial(0)
        val antes = serial.rif.copyOf()
        val w = writer(serial, Sink())
        assertTrue(w.apply("""{"acao":"salvar","segundos":2,"mbar":1200}""").getBoolean("ok"))
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        assertEquals(2, serial.tempo)
        assertEquals(1199, serial.rif[0]) // o original trunca 1,2 × 1000 = 1199,999… → 1199; o app faz igual
        assertTrue(w.apply("""{"acao":"desfazer"}""").getBoolean("ok"))
        assertEquals("CONFIRMED", waitDone(w).getString("outcome"))
        assertEquals(0, serial.tempo)
        assertEquals(antes.toList(), serial.rif.toList())
        assertFalse(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
    }

    @Test fun `pressao fora da faixa ou fora do passo de 5 mbar e recusada sem ir a porta`() {
        val serial = FakeSerial(0)
        val w = writer(serial, Sink())
        for (bad in listOf(2100, 150, 1252)) assertFalse(w.apply("""{"acao":"pressao","mbar":$bad}""").getBoolean("ok"))
        repeat(2000) { Thread.yield() }
        assertTrue(serial.frames.isEmpty())
    }

    @Test fun `central recusa - nao diz gravado`() {
        val serial = FakeSerial(0).also { it.nakWrites = true }
        val w = writer(serial, Sink())
        w.apply("""{"acao":"desligar"}""")
        val s = waitDone(w)
        assertEquals("NOT_ACKED", s.getString("outcome"))
        assertFalse(s.getString("message").contains("Gravado"))
        assertEquals(0, serial.tempo)
    }
}
