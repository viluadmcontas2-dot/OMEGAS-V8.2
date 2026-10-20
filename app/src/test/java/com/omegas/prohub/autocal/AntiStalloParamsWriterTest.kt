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

/** Escritor dos 5 números do anti-apagão com ECU falsa em BYTES (leitura 29 33 01 e SetVector estendido 37 33 0C 01 ...). */
class AntiStalloParamsWriterTest {
    private class FakeSerial(var values: IntArray, var acceptWrites: Boolean = true) : Mp48SerialScheduler {
        var storeInstead: IntArray? = null
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
            frames += hex(request)
            return if (request[0].toInt() == 0x29) {
                if (failReadsAfterWrite && frames.any { it.startsWith("37") }) return UsbProtocolReply(ok = false, status = -1, error = "timeout", request = request)
                val p = ByteArray(10) { i -> ((values[i / 2] shr (8 * (i % 2))) and 0xFF).toByte() }
                UsbProtocolReply(ok = true, status = 0x53, payload = p, request = request, rawResponse = request + byteArrayOf(0x53, 10) + p)
            } else {
                if (acceptWrites) values = storeInstead ?: IntArray(5) { (request[4 + it * 2].toInt() and 0xFF) or ((request[5 + it * 2].toInt() and 0xFF) shl 8) }
                UsbProtocolReply(ok = acceptWrites, status = if (acceptWrites) 0x53 else 0xCA, request = request, rawResponse = request + byteArrayOf(0x53, 0, 0x53))
            }
        }
    }

    private val factory = intArrayOf(1600, 100, 1500, 2, 20)

    private fun waitFor(writer: AntiStalloParamsWriter, vararg states: String): JSONObject {
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
    ) = AntiStalloParamsWriter(serial, { _, d -> records += d }, { s, v, t -> reads += "$s:${v.toList()}:$t" }, busy, { s, t -> unknowns += "$s:$t" })

    private fun changes(vararg pairs: Pair<Int, Int>) = JSONObject().also { o -> pairs.forEach { o.put(it.first.toString(), it.second) } }.toString()

    @Test fun `abrir valores sem snapshot le diretamente sem escrever ou alterar estados`() {
        val serial = FakeSerial(factory.copyOf())
        val reads = java.util.concurrent.CopyOnWriteArrayList<String>()
        val w = writer(serial, reads = reads)
        assertTrue(w.readCurrent().getBoolean("ok"))
        repeat(5_000_000) {
            if (reads.isNotEmpty()) return@repeat
            Thread.yield()
        }
        assertTrue("leitura direta deve alimentar snapshot", reads.any { it.endsWith("ABRIR_VALORES") })
        assertEquals(factory.toList(), serial.values.toList())
        assertEquals(listOf(Mp48WorkClass.READ_ONLY), serial.classes)
        assertEquals(listOf("29"), serial.frames.map { it.substring(0, 2) })
        assertEquals("IDLE", JSONObject(w.statusJson()).getString("state"))
    }

    @Test fun `prepare so le - nenhum quadro de escrita sai`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        assertTrue(w.prepare(changes(0 to 1700)).getBoolean("ok"))
        val s = waitFor(w, "READY")
        assertEquals("37 33 0C 01 A4 06 64 00 DC 05 02 00 14 00 " + "%02X".format((0x37 + 0x33 + 0x0C + 0x01 + 0xA4 + 0x06 + 0x64 + 0xDC + 0x05 + 0x02 + 0x14) and 0xFF), s.getString("frameHex"))
        assertEquals("[0]", s.getJSONArray("changed").toString())
        assertEquals(listOf(Mp48WorkClass.READ_ONLY), serial.classes)
        assertTrue(serial.frames.none { it.startsWith("37") })
    }

    @Test fun `valor fora da faixa e recusado ja no prepare e nada vai a porta`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        for (bad in listOf(changes(0 to 6001), changes(1 to 1001), changes(2 to 10001), changes(3 to -1), changes(4 to 99999), changes(7 to 1), "nao-json", "[]", "{\"0\":\"abc\"}", "{\"0\":1.5}")) {
            val reply = w.prepare(bad)
            assertFalse(bad, reply.getBoolean("ok"))
        }
        repeat(2000) { Thread.yield() }
        assertTrue(serial.frames.isEmpty())
        assertEquals("IDLE", JSONObject(w.statusJson()).getString("state"))
    }

    @Test fun `confirm com token certo grava UM quadro, le de volta e so entao CONFIRMED`() {
        val serial = FakeSerial(factory.copyOf())
        val records = ArrayList<JSONObject>()
        val reads = ArrayList<String>()
        val w = writer(serial, records, reads)
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        assertTrue(w.confirm(ready.getString("token")).getBoolean("ok"))
        val done = waitFor(w, "DONE")
        assertEquals("CONFIRMED", done.getString("outcome"))
        assertEquals("[1700,100,1500,2,20]", done.getJSONArray("readback").toString())
        assertEquals(1, serial.frames.count { it.startsWith("37") })
        assertEquals(Mp48WorkClass.MANUAL_WRITE, serial.classes.last())
        assertEquals(listOf("29", "29", "37", "29"), serial.frames.map { it.substring(0, 2) })
        assertEquals("CONFIRMED", records.last().getString("outcome"))
        assertTrue(reads.last().endsWith("APOS_GRAVAR"))
    }

    @Test fun `sem confirm nada e gravado, mesmo esperando`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        w.prepare(changes(0 to 1700)); waitFor(w, "READY")
        repeat(1000) { Thread.yield() }
        assertTrue(serial.frames.none { it.startsWith("37") })
    }

    @Test fun `token errado ou sem previa e recusado`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        assertFalse(w.confirm("x").getBoolean("ok"))
        w.prepare(changes(0 to 1700)); waitFor(w, "READY")
        assertFalse(w.confirm("outro").getBoolean("ok"))
        assertTrue(serial.frames.none { it.startsWith("37") })
    }

    @Test fun `cancel descarta a previa e confirm depois e recusado`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        w.cancel()
        assertEquals("IDLE", JSONObject(w.statusJson()).getString("state"))
        assertFalse(w.confirm(ready.getString("token")).getBoolean("ok"))
    }

    @Test fun `USB mudou de sessao entre a previa e a confirmacao - recusa`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        serial.session = 6L
        assertFalse(w.confirm(ready.getString("token")).getBoolean("ok"))
    }

    @Test fun `o modulo recusa a escrita - nao e sucesso`() {
        val serial = FakeSerial(factory.copyOf(), acceptWrites = false)
        val w = writer(serial)
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        w.confirm(ready.getString("token"))
        assertEquals("NOT_ACKED", waitFor(w, "DONE").getString("outcome"))
    }

    @Test fun `desfazer volta os valores anteriores pelo mesmo fluxo e so existe apos CONFIRMED`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        assertFalse(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
        w.prepare(changes(0 to 1700, 3 to 5)); w.confirm(waitFor(w, "READY").getString("token")); waitFor(w, "DONE")
        val undo = JSONObject(w.statusJson()).getJSONObject("undo")
        assertTrue(undo.getBoolean("available"))
        assertEquals("[1600,100,1500,2,20]", undo.getJSONArray("values").toString())
        // o Desfazer é um pedido de gravação como outro qualquer: prévia e 2º toque
        val obj = JSONObject(); for (i in 0 until 5) obj.put(i.toString(), undo.getJSONArray("values").getInt(i))
        w.prepare(obj.toString()); val ready = waitFor(w, "READY")
        w.confirm(ready.getString("token")); waitFor(w, "DONE")
        assertEquals(factory.toList(), serial.values.toList())
    }

    @Test fun `restaurar fabrica nao e atalho - passa por previa e confirmacao`() {
        val serial = FakeSerial(intArrayOf(1800, 100, 1500, 5, 20))
        val w = writer(serial)
        w.prepare(AntiStalloParamsCore.factoryChangesJson())
        val ready = waitFor(w, "READY")
        assertEquals("[0,3]", ready.getJSONArray("changed").toString())
        assertTrue(serial.frames.none { it.startsWith("37") })
        w.confirm(ready.getString("token")); assertEquals("CONFIRMED", waitFor(w, "DONE").getString("outcome"))
        assertEquals(factory.toList(), serial.values.toList())
    }

    @Test fun `USB desconectado nao prepara`() {
        val serial = FakeSerial(factory.copyOf()).also { it.connected = false }
        assertFalse(writer(serial).prepare(changes(0 to 1700)).getBoolean("ok"))
    }

    @Test fun `prepare com calibracao ocupada recusa e nao envia nada`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial, busy = { true })
        val reply = w.prepare(changes(0 to 1700))
        assertFalse(reply.getBoolean("ok"))
        assertTrue(reply.getString("error"), reply.getString("error").contains("calibração em andamento"))
        repeat(2000) { Thread.yield() }
        assertTrue(serial.frames.isEmpty())
    }

    @Test fun `confirm com calibracao ocupada recusa sem enviar e a previa continua valida`() {
        val serial = FakeSerial(factory.copyOf())
        var ocupado = false
        val w = writer(serial, busy = { ocupado })
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        ocupado = true
        assertFalse(w.confirm(ready.getString("token")).getBoolean("ok"))
        assertTrue(serial.frames.none { it.startsWith("37") })
        ocupado = false
        assertTrue(w.confirm(ready.getString("token")).getBoolean("ok"))
        assertEquals("CONFIRMED", waitFor(w, "DONE").getString("outcome"))
    }

    @Test fun `gravacao sem leitura de volta marca ANTI_STALLO como nao lido`() {
        val serial = FakeSerial(factory.copyOf())
        val unknowns = ArrayList<String>()
        val w = writer(serial, unknowns = unknowns)
        w.prepare(changes(0 to 1700)); val ready = waitFor(w, "READY")
        serial.failReadsAfterWrite = true
        w.confirm(ready.getString("token"))
        assertEquals("READBACK_FAILED", waitFor(w, "DONE").getString("outcome"))
        assertEquals(1, unknowns.size)
    }

    @Test fun `executor encerrado nao lanca e libera o ocupado`() {
        val w = writer(FakeSerial(factory.copyOf()))
        w.close()
        assertFalse(w.prepare(changes(0 to 1700)).getBoolean("ok"))
        assertFalse(JSONObject(w.statusJson()).getBoolean("busy"))
    }

    @Test fun `gravacao posterior que diverge invalida o desfazer`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        w.prepare(changes(0 to 1700)); w.confirm(waitFor(w, "READY").getString("token")); waitFor(w, "DONE")
        assertTrue(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
        serial.storeInstead = intArrayOf(1, 1, 1, 1, 1)
        w.prepare(changes(0 to 1800)); val ready = waitFor(w, "READY")
        w.confirm(ready.getString("token"))
        assertEquals("READBACK_DIVERGED", waitFor(w, "DONE").getString("outcome"))
        assertFalse(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
    }

    @Test fun `apply grava num unico passo MANUAL_WRITE - ler, gravar e reler sem previa nem token`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        assertTrue(w.apply(changes(0 to 1700)).getBoolean("ok"))
        val done = waitFor(w, "DONE")
        assertEquals("CONFIRMED", done.getString("outcome"))
        assertEquals(1700, done.getJSONArray("readback").getInt(0))
        assertEquals(listOf(Mp48WorkClass.MANUAL_WRITE), serial.classes)
        assertEquals(1, serial.frames.count { it.startsWith("37") })
        assertTrue(JSONObject(w.statusJson()).getJSONObject("undo").getBoolean("available"))
    }

    @Test fun `apply com os mesmos numeros nao envia nada e valor fora da faixa e recusado antes de tocar a central`() {
        val serial = FakeSerial(factory.copyOf())
        val w = writer(serial)
        w.apply(changes(0 to factory[0]))
        assertEquals("NOTHING_TO_DO", waitFor(w, "DONE").getString("outcome"))
        assertFalse(w.apply(changes(0 to 999999)).getBoolean("ok"))
        assertTrue(serial.frames.none { it.startsWith("37") })
    }

    @Test fun `apply com a central recusando nao e sucesso`() {
        val serial = FakeSerial(factory.copyOf(), acceptWrites = false)
        val w = writer(serial); w.apply(changes(0 to 1700))
        assertEquals("NOT_ACKED", waitFor(w, "DONE").getString("outcome"))
        assertEquals(factory.toList(), serial.values.toList())
    }
}
