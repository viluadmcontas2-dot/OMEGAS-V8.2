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

/** Escritor das preferências com central falsa em BYTES: SC 378 (RPM máx.), SC 330 (liga), SC 331 (injeção, vetor). */
class AutoCalPreferenceWriterTest {
    private class FakeSerial : Mp48SerialScheduler {
        var rpm = 3000
        var enable = 1
        val inj = IntArray(18) { 100 + it * 50 }
        var nak = false
        var divergent = false
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
        private fun ack(request: ByteArray) = UsbProtocolReply(ok = true, status = 0x53, request = request, rawResponse = request + byteArrayOf(0x53, 0, 0x53))
        private fun words(v: IntArray) = ByteArray(v.size * 2) { i -> ((v[i / 2] shr (8 * (i % 2))) and 0xFF).toByte() }
        private fun handle(request: ByteArray): UsbProtocolReply {
            frames += hex(request)
            val r = IntArray(request.size) { request[it].toInt() and 0xFF }
            return when {
                r[0] == 0x09 && r[1] == 0x7A -> ok(request, byteArrayOf(rpm.toByte(), (rpm shr 8).toByte()))
                r[0] == 0x09 && r[1] == 0x4A -> ok(request, byteArrayOf(enable.toByte()))
                r[0] == 0x29 && r[1] == 0x4B -> ok(request, words(inj))
                r[0] in 0x12..0x14 && nak -> UsbProtocolReply(ok = false, status = 0xCA, request = request, rawResponse = request + byteArrayOf(0xCA.toByte(), 1, 0x10), error = "NACK")
                r[0] == 0x13 && r[1] == 0x7A -> { if (!divergent) rpm = r[3] or (r[4] shl 8); ack(request) }
                r[0] == 0x12 && r[1] == 0x4A -> { enable = r[3]; ack(request) }
                r[0] == 0x14 && r[1] == 0x4B -> { inj[r[3]] = r[4] or (r[5] shl 8); ack(request) }
                else -> UsbProtocolReply(ok = false, status = -1, request = request, error = "inesperado")
            }
        }
    }

    private fun writer(s: FakeSerial, records: MutableList<JSONObject> = CopyOnWriteArrayList()) =
        AutoCalPreferenceWriter(s, { _, d -> records += d })

    @Test fun `ler - vetor inteiro fatiado, desconhecido vira nulo`() {
        val s = FakeSerial()
        val out = writer(s).read("""{"ids":["MAX_RPM_FOR_AUTOCAL","PETR_INJ_TBP:2","DISABLE_ACQ_BAND"]}""")
        assertTrue(out.getBoolean("ok"))
        val v = out.getJSONObject("values")
        assertEquals(3000, v.getInt("MAX_RPM_FOR_AUTOCAL"))
        assertEquals(200, v.getInt("PETR_INJ_TBP:2"))
        assertTrue(v.isNull("DISABLE_ACQ_BAND")) // a central falsa não responde: "—", nunca 0
        assertEquals(listOf(Mp48WorkClass.READ_ONLY), s.classes.toList())
    }

    @Test fun `gravar - ACK e releitura igual dao CONFIRMED, e Desfazer volta`() {
        val s = FakeSerial()
        val w = writer(s)
        val r = w.apply("""{"id":"MAX_RPM_FOR_AUTOCAL","raw":3500}""")
        assertEquals("CONFIRMED", r.getString("outcome"))
        assertEquals(3000, r.getInt("before"))
        assertEquals(3500, s.rpm)
        assertTrue(s.frames.contains("13 7A 01 AC 0D 47"))
        assertEquals(Mp48WorkClass.MANUAL_WRITE, s.classes.last())
        assertEquals("CONFIRMED", w.apply("""{"id":"MAX_RPM_FOR_AUTOCAL","raw":3000}""").getString("outcome"))
        assertEquals(3000, s.rpm)
    }

    @Test fun `NAK nao vira Gravado`() {
        val s = FakeSerial().apply { nak = true }
        val r = writer(s).apply("""{"id":"MAX_RPM_FOR_AUTOCAL","raw":3500}""")
        assertEquals("NOT_ACKED", r.getString("outcome"))
        assertFalse(r.getBoolean("ok"))
        assertEquals(3000, s.rpm)
    }

    @Test fun `releitura diferente nao vira Gravado`() {
        val s = FakeSerial().apply { divergent = true }
        val r = writer(s).apply("""{"id":"MAX_RPM_FOR_AUTOCAL","raw":3500}""")
        assertEquals("READBACK_DIVERGED", r.getString("outcome"))
        assertFalse(r.getBoolean("ok"))
    }

    @Test fun `pedidos invalidos e recusas nao enviam nada`() {
        val s = FakeSerial()
        val w = writer(s)
        assertFalse(w.apply("""{"id":"MUL_ACT","raw":1}""").getBoolean("ok"))
        assertFalse(w.apply("""{"id":"MAX_RPM_FOR_AUTOCAL","raw":"x"}""").getBoolean("ok"))
        assertEquals("INVALID", w.apply("""{"id":"AUTO_CAL_ENABLE","raw":0}""").getString("outcome"))
        assertEquals("INVALID", w.apply("""{"id":"PETR_INJ_TBP:3","raw":100}""").getString("outcome")) // menor que o ponto anterior
        assertEquals(1, s.enable)
        assertTrue(s.frames.none { it.startsWith("12 ") || it.startsWith("13 ") || it.startsWith("14 ") })
    }

    @Test fun `ponto de injecao em ordem e gravado no vetor`() {
        val s = FakeSerial()
        val r = writer(s).apply("""{"id":"PETR_INJ_TBP:3","raw":275}""")
        assertEquals("CONFIRMED", r.getString("outcome"))
        assertEquals(275, s.inj[3])
        assertTrue(s.frames.contains("14 4B 01 03 13 01 ${"%02X".format((0x14 + 0x4B + 0x01 + 0x03 + 0x13 + 0x01) and 0xFF)}"))
    }
}
