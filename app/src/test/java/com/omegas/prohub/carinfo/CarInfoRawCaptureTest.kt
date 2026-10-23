package com.omegas.prohub.carinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Payloads simulados no formato do Parcel (ordem de ESCRITA do contrato ICanBus). */
class W {
    private val out = ByteArrayOutputStream()
    private fun le(n: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()
    fun int(v: Int) = apply { out.write(le(v)) }
    fun float(v: Float) = apply { out.write(le(java.lang.Float.floatToIntBits(v))) }
    fun long(v: Long) = apply { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()) }
    fun string(s: String?) = apply {
        if (s == null) { int(-1); return@apply }
        int(s.length)
        val body = s.toByteArray(Charsets.UTF_16LE)
        out.write(body)
        val pad = ((body.size + 2 + 3) and 3.inv()) - body.size
        out.write(ByteArray(pad))
    }
    fun bytes(b: ByteArray) = apply { int(b.size); out.write(b); out.write(ByteArray(((b.size + 3) and 3.inv()) - b.size)) }
    fun toBytes(): ByteArray = out.toByteArray()
}

class CarInfoRawCaptureTest {
    private var now = 1_000L
    private fun capture() = CarInfoRawCapture { now }

    private fun dashPayload(speed: Int = Int.MIN_VALUE, range: Int = 35, avg: Float = 11.7f, inst: Float = -1.0f): ByteArray {
        val w = W().int(1)
        w.int(Int.MIN_VALUE).int(Int.MIN_VALUE).int(range).int(Int.MIN_VALUE).int(speed).int(Int.MIN_VALUE).int(Int.MIN_VALUE)
        val u = -2.14748365E9f
        w.float(u).float(u).float(avg).float(inst).float(u).float(u).float(u).float(u)
        w.int(Int.MIN_VALUE).int(Int.MIN_VALUE).int(Int.MIN_VALUE)
        return w.toBytes()
    }

    private fun propsPayload(service: String, vararg p: Triple<Int, String, Any>): ByteArray {
        val w = W().string(service).int(p.size)
        for ((id, cls, v) in p) {
            w.int(1).int(id).int(0).string(cls)
            when (v) {
                is String -> w.string(v)
                is Float -> w.int(7).float(v)
                is Int -> w.int(1).int(v)
            }
        }
        return w.toBytes()
    }

    @Test
    fun `conta chamadas por transacao e guarda o ultimo tamanho`() {
        val c = capture()
        c.onTransaction(6, dashPayload()); c.onTransaction(6, dashPayload()); c.onTransaction(2, W().int(0).toBytes())
        assertEquals(2, c.activeChannels())
        val json = c.json()
        assertEquals(2, json.getJSONObject("tx").getJSONObject("6").getInt("n"))
        assertEquals(76, json.getJSONObject("tx").getJSONObject("6").getInt("bytes"))
        assertEquals(17, json.getInt("channelsTotal"))
        assertFalse(json.getJSONObject("tx").has("5"))
    }

    @Test
    fun `campos crus do painel saem antes da sentinela`() {
        val c = capture()
        c.onTransaction(6, dashPayload(speed = 0, inst = -1.0f))
        val raw = c.json().getJSONObject("dashboard")
        assertEquals("35", raw.getString("cruisingRange"))
        assertEquals("-1.0", raw.getString("instFuelCont"))        // o -1,0 do campo, cru
        assertEquals((Int.MIN_VALUE).toString(), raw.getString("odo")) // sentinela aparece como chegou
        assertEquals("11.7", raw.getString("avgFuelCont"))
    }

    @Test
    fun `tx17 decodifica propId areaId e valor`() {
        val c = capture()
        c.onTransaction(17, propsPayload("maintenance", Triple(0x11600207, "java.lang.Float", 13.9f), Triple(0x4002, "java.lang.Integer", 7), Triple(0x99, "java.lang.String", "ola")))
        val props = c.json().getJSONObject("props")
        assertEquals("maintenance", props.getString("service"))
        assertTrue(props.getBoolean("complete"))
        val v = props.getJSONArray("values")
        assertEquals("0x11600207/0=13.9", v.getString(0))
        assertEquals("0x4002/0=7", v.getString(1))
        assertEquals("0x99/0=ola", v.getString(2))
    }

    @Test
    fun `payload truncado ou lixo nao estoura e marca incompleto`() {
        val c = capture()
        val ok = propsPayload("x", Triple(1, "java.lang.Integer", 5))
        c.onTransaction(17, ok.copyOf(ok.size - 6))
        c.onTransaction(2, byteArrayOf(1, 2, 3))
        c.onTransaction(14, ByteArray(0))
        c.onTransaction(6, ByteArray(10) { 0x7f })
        c.onTransaction(99, byteArrayOf(1))   // fora de 1..17: ignorado
        assertEquals(4, c.activeChannels())
        assertFalse(c.json().getJSONObject("props").getBoolean("complete"))
    }

    @Test
    fun `cabininfo usa a ordem de escrita com 22 campos e omite desconhecido`() {
        val w = W().int(1)
        repeat(22) { i -> w.int(if (i == 8) 1 else Int.MIN_VALUE) }
        val c = capture()
        c.onTransaction(2, w.toBytes())
        val cabin = c.json().getJSONObject("cabin")
        assertEquals(1, cabin.getInt("leftSafetyBelt"))
        assertFalse(cabin.has("bonnetStatus"))
        assertTrue(cabin.getBoolean("complete"))
    }

    @Test
    fun `lista de configuracao tx14 devolve os propIds`() {
        val w = W().string("maintenance").int(1)
        w.int(1).int(0x7001).string("java.lang.Integer").int(0).int(1)
        w.int(0).string("com.autoai.canbus.CarPropertyConfig\$AreaConfig").int(-1).int(-1).int(-1).int(-1).int(0).int(1)
        val c = capture()
        c.onTransaction(14, w.toBytes())
        val ids = c.json().getJSONObject("propertyList").getJSONArray("propIds")
        assertEquals("0x7001", ids.getString(0))
    }

    @Test
    fun `evento cru sai no maximo a cada 5 s e so quando muda`() {
        val c = capture()
        c.onTransaction(6, dashPayload())
        assertNotNull(c.rawEventIfDue())                 // primeiro sai
        c.onTransaction(6, dashPayload(speed = 50))
        now += 1_000; assertNull(c.rawEventIfDue())      // mudou, mas faz 1 s
        now += 5_000; assertNotNull(c.rawEventIfDue())   // 6 s e mudou
        c.onTransaction(6, dashPayload(speed = 50))
        now += 10_000; assertNull(c.rawEventIfDue())     // igual: não repete (contador não conta)
    }

    @Test
    fun `evento cru tem teto por ligacao e reset zera`() {
        val c = capture()
        var emitted = 0
        for (i in 0 until CarInfoRawCapture.RAW_EVENT_MAX + 50) {
            c.onTransaction(6, dashPayload(speed = i))
            now += CarInfoRawCapture.RAW_EVENT_EVERY_MS
            if (c.rawEventIfDue() != null) emitted++
        }
        assertEquals(CarInfoRawCapture.RAW_EVENT_MAX, emitted)
        c.reset()
        assertEquals(0, c.activeChannels())
        c.onTransaction(6, dashPayload())
        assertNotNull(c.rawEventIfDue())
    }

    @Test
    fun `hex de cada transacao tem tamanho limitado`() {
        val c = capture()
        c.onTransaction(5, ByteArray(500) { 1 })
        val hex = c.json().getJSONObject("hex").getString("5")
        assertEquals(CarInfoRawCapture.HEX_BYTES * 2 + 1, hex.length)   // + marca de corte
    }
}
