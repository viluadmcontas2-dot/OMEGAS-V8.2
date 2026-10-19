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

/** Escritor com ECU falsa em nível de BYTES (leitura 29 FA 00 23 e SetVector 35 FA ...): prévia, confirmação, readback, desfazer. */
class AntiStalloFlagWriterTest {
    private class FakeSerial(var words: IntArray, var acceptWrites: Boolean = true) : Mp48SerialScheduler {
        /** Se definido, a ECU guarda este valor em vez do pedido (leitura de volta divergente). */
        var storeInstead: IntArray? = null
        /** Depois de qualquer SetVector, as leituras 29 deixam de responder. */
        var failReadsAfterWrite = false
        @Volatile var connected = true
        var session = 5L
        val frames = ArrayList<String>()
        val classes = ArrayList<Mp48WorkClass>()
        private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        override fun isConnected() = connected
        override fun currentSessionId() = session
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean): UsbProtocolReply =
            handle(request)
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T {
            classes += workClass
            return block(object : Mp48SerialUnit {
                override val sessionId = session
                override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = handle(request)
            })
        }
        private fun handle(request: ByteArray): UsbProtocolReply {
            val h = hex(request)
            frames += h
            return if (request[0].toInt() == 0x29) {
                if (failReadsAfterWrite && frames.any { it.startsWith("35") }) return UsbProtocolReply(ok = false, status = -1, error = "timeout", request = request)
                val p = ByteArray(4) { i -> ((words[i / 2] shr (8 * (i % 2))) and 0xFF).toByte() }
                UsbProtocolReply(ok = true, status = 0x53, payload = p, request = request, rawResponse = request + byteArrayOf(0x53, 4) + p)
            } else {
                if (acceptWrites) words = storeInstead ?: intArrayOf((request[3].toInt() and 0xFF) or ((request[4].toInt() and 0xFF) shl 8), (request[5].toInt() and 0xFF) or ((request[6].toInt() and 0xFF) shl 8))
                UsbProtocolReply(ok = acceptWrites, status = if (acceptWrites) 0x53 else 0xCA, request = request, rawResponse = request + byteArrayOf(0x53, 0, 0x53))
            }
        }
    }

    private fun waitFor(writer: AntiStalloFlagWriter, vararg states: String): JSONObject {
        repeat(5_000_000) {
            val s = JSONObject(writer.statusJson())
            if (s.getString("state") in states) return s
            Thread.yield()
        }
        throw AssertionError("estado ${states.toList()} não chegou: ${writer.statusJson()}")
    }

    private fun writer(
        serial: FakeSerial, records: MutableList<JSONObject> = ArrayList(), reads: MutableList<String> = ArrayList(),
        unknowns: MutableList<String> = ArrayList(), busy: () -> Boolean = { false },
    ) = AntiStalloFlagWriter(serial, { _, d -> records += d }, { s, w, t -> reads += "$s:${w.toList()}:$t" }, busy, { s, t -> unknowns += "$s:$t" })

    @Test fun `prepare so le - nenhum quadro de escrita sai`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        assertTrue(w.prepare(true).getBoolean("ok"))
        val s = waitFor(w, "READY")
        assertEquals("35 FA 00 20 00 00 00 4F", s.getString("frameHex"))
        assertEquals(listOf(Mp48WorkClass.READ_ONLY), serial.classes)
        assertTrue(serial.frames.none { it.startsWith("35") })
    }

    @Test fun `confirm com token certo grava UM quadro, le de volta e so entao CONFIRMED`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val records = ArrayList<JSONObject>()
        val reads = ArrayList<String>()
        val w = writer(serial, records, reads)
        w.prepare(true); val ready = waitFor(w, "READY")
        assertTrue(w.confirm(ready.getString("token")).getBoolean("ok"))
        val done = waitFor(w, "DONE")
        assertEquals("CONFIRMED", done.getString("outcome"))
        assertEquals(listOf(0x20, 0), done.getJSONArray("readback").let { listOf(it.getInt(0), it.getInt(1)) })
        assertEquals(1, serial.frames.count { it.startsWith("35") })
        assertEquals(Mp48WorkClass.MANUAL_WRITE, serial.classes.last())
        val order = serial.frames.map { it.substring(0, 2) }
        assertEquals(listOf("29", "29", "35", "29"), order) // prévia, releitura, gravação, leitura de volta
        assertEquals("CONFIRMED", records.last().getString("outcome"))
        assertTrue(reads.last().endsWith("APOS_GRAVAR"))
    }

    @Test fun `sem confirm nada e gravado, mesmo esperando`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        w.prepare(true); waitFor(w, "READY")
        repeat(1000) { Thread.yield() }
        assertTrue(serial.frames.none { it.startsWith("35") })
    }

    @Test fun `token errado ou sem previa e recusado`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        assertFalse(w.confirm("x").getBoolean("ok"))
        w.prepare(true); waitFor(w, "READY")
        assertFalse(w.confirm("outro").getBoolean("ok"))
        assertTrue(serial.frames.none { it.startsWith("35") })
    }

    @Test fun `cancel descarta a previa e confirm depois e recusado`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        w.prepare(true); val ready = waitFor(w, "READY")
        w.cancel()
        assertEquals("IDLE", JSONObject(w.statusJson()).getString("state"))
        assertFalse(w.confirm(ready.getString("token")).getBoolean("ok"))
    }

    @Test fun `USB mudou de sessao entre a previa e a confirmacao - recusa`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        w.prepare(true); val ready = waitFor(w, "READY")
        serial.session = 6L
        assertFalse(w.confirm(ready.getString("token")).getBoolean("ok"))
    }

    @Test fun `ECU recusa a escrita - nao e sucesso`() {
        val serial = FakeSerial(intArrayOf(0, 0), acceptWrites = false)
        val w = writer(serial)
        w.prepare(true); val ready = waitFor(w, "READY")
        w.confirm(ready.getString("token"))
        val done = waitFor(w, "DONE")
        assertEquals("NOT_ACKED", done.getString("outcome"))
    }

    @Test fun `desfazer fica disponivel so apos CONFIRMED e restaura o bit anterior pelo mesmo fluxo`() {
        val serial = FakeSerial(intArrayOf(0x4000, 3))
        val w = writer(serial)
        assertFalse(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
        w.prepare(true); w.confirm(waitFor(w, "READY").getString("token")); val done = waitFor(w, "DONE")
        assertEquals("CONFIRMED", done.getString("outcome"))
        val undo = JSONObject(w.statusJson()).getJSONObject("undo")
        assertTrue(undo.getBoolean("available"))
        assertFalse(undo.getBoolean("enable")) // antes estava desligado
        w.prepare(undo.getBoolean("enable")); val ready = waitFor(w, "READY")
        assertEquals("[16384,3]", ready.getJSONArray("after").toString())
        w.confirm(ready.getString("token")); waitFor(w, "DONE")
        assertEquals(listOf(0x4000, 3), serial.words.toList())
    }

    @Test fun `USB desconectado nao prepara`() {
        val serial = FakeSerial(intArrayOf(0, 0)).also { it.connected = false }
        assertFalse(writer(serial).prepare(true).getBoolean("ok"))
    }

    // ---- revisão independente, item 2: ocupação da calibração ----
    @Test fun `prepare com calibracao ocupada recusa, nao envia nada e nao muda o estado`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial, busy = { true })
        val reply = w.prepare(true)
        assertFalse(reply.getBoolean("ok"))
        assertTrue(reply.getString("error"), reply.getString("error").contains("calibração em andamento, tente de novo"))
        repeat(2000) { Thread.yield() }
        assertTrue(serial.frames.isEmpty())
        assertTrue(serial.classes.isEmpty())
        val status = JSONObject(w.statusJson())
        assertEquals("IDLE", status.getString("state"))
        assertFalse(status.getBoolean("busy"))
    }

    @Test fun `confirm com calibracao ocupada recusa sem enviar e a previa continua valida`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        var ocupado = false
        val w = writer(serial, busy = { ocupado })
        w.prepare(true); val ready = waitFor(w, "READY")
        ocupado = true
        val reply = w.confirm(ready.getString("token"))
        assertFalse(reply.getBoolean("ok"))
        assertTrue(reply.getString("error").contains("calibração em andamento, tente de novo"))
        assertTrue(serial.frames.none { it.startsWith("35") })
        assertEquals("READY", JSONObject(w.statusJson()).getString("state"))
        ocupado = false
        assertTrue(w.confirm(ready.getString("token")).getBoolean("ok"))
        assertEquals("CONFIRMED", waitFor(w, "DONE").getString("outcome"))
    }

    // ---- item 3: leitura de volta falhou -> a entrada deixa de aparecer como lida ----
    @Test fun `gravacao sem leitura de volta marca FLAG_CONF3 como nao lida`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val unknowns = ArrayList<String>()
        val w = writer(serial, unknowns = unknowns)
        w.prepare(true); val ready = waitFor(w, "READY")
        serial.failReadsAfterWrite = true
        w.confirm(ready.getString("token"))
        assertEquals("READBACK_FAILED", waitFor(w, "DONE").getString("outcome"))
        assertEquals(1, unknowns.size)
        assertTrue(unknowns[0], unknowns[0].startsWith("5:"))
    }

    // ---- item 5 ----
    @Test fun `executor encerrado nao lanca e libera o ocupado`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        w.close()
        val reply = w.prepare(true)
        assertFalse(reply.getBoolean("ok"))
        assertFalse(JSONObject(w.statusJson()).getBoolean("busy"))
        assertEquals("ERROR", JSONObject(w.statusJson()).getString("state"))
    }

    @Test fun `ocupado ja esta livre quando READY aparece - prepare logo em seguida nunca e recusado`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        repeat(300) {
            assertTrue("rodada $it", w.prepare(true).getBoolean("ok"))
            waitFor(w, "READY")
            assertFalse("busy ainda true com READY visível (rodada $it)", JSONObject(w.statusJson()).getBoolean("busy"))
        }
    }

    @Test fun `ocupado ja esta livre quando DONE aparece`() {
        val serial = FakeSerial(intArrayOf(0, 0))
        val w = writer(serial)
        repeat(100) {
            w.prepare(it % 2 == 0)
            val ready = waitFor(w, "READY")
            if (ready.getBoolean("noop")) return@repeat
            w.confirm(ready.getString("token")); waitFor(w, "DONE")
            assertFalse("busy ainda true com DONE visível (rodada $it)", JSONObject(w.statusJson()).getBoolean("busy"))
        }
    }

    @Test fun `gravacao posterior que diverge invalida o desfazer`() {
        val serial = FakeSerial(intArrayOf(0x4000, 3))
        val w = writer(serial)
        w.prepare(true); w.confirm(waitFor(w, "READY").getString("token")); waitFor(w, "DONE")
        assertTrue(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
        serial.storeInstead = intArrayOf(0x4010, 3) // a ECU guarda outro valor que o pedido
        w.prepare(false); val ready = waitFor(w, "READY")
        w.confirm(ready.getString("token"))
        assertEquals("READBACK_DIVERGED", waitFor(w, "DONE").getString("outcome"))
        assertFalse(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
    }

    @Test fun `leitura previa espera a vez quando a serial recusa por estar ocupada e nao vira erro`() {
        var recusas = 0
        val resultado = esperandoAVez {
            if (recusas < 3) { recusas += 1; throw com.omegas.prohub.ecu.Mp48BackpressureRejectedException("lane READ_ONLY saturada") }
            "leu"
        }
        assertEquals("leu", resultado)
        assertEquals(3, recusas)
    }
}
