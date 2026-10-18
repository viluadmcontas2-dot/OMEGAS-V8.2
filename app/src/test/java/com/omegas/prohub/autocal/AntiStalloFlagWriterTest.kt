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
                val p = ByteArray(4) { i -> ((words[i / 2] shr (8 * (i % 2))) and 0xFF).toByte() }
                UsbProtocolReply(ok = true, status = 0x53, payload = p, request = request, rawResponse = request + byteArrayOf(0x53, 4) + p)
            } else {
                if (acceptWrites) words = intArrayOf((request[3].toInt() and 0xFF) or ((request[4].toInt() and 0xFF) shl 8), (request[5].toInt() and 0xFF) or ((request[6].toInt() and 0xFF) shl 8))
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

    private fun writer(serial: FakeSerial, records: MutableList<JSONObject> = ArrayList(), reads: MutableList<String> = ArrayList()) =
        AntiStalloFlagWriter(serial, { _, d -> records += d }, { s, w, t -> reads += "$s:${w.toList()}:$t" })

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
}
