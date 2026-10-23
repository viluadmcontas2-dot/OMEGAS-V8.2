package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.EcuParameterSnapshot.Kind
import com.omegas.prohub.autocal.EcuParameterSnapshot.ReadOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Calibração do NÍVEL lida da ECU, SÓ LEITURA, ao conectar: tipo do sensor (SC 0x24), 4 referências (0x25), filtro FAST/SLOW
 * (0x114) e histerese + 4 limiares de LED (0x12C). Os pedidos são conferidos contra o FIO REAL do original (Portmon LOGNOVO,
 * `tests/ui/fixtures/ecu-estado-lognovo.json`: chave = pedido com checksum, valor = resposta da ECU do dono).
 */
class LevelCalibrationTest {
    private val fio: Map<String, String> by lazy {
        val j = JSONObject(File("../tests/ui/fixtures/ecu-estado-lognovo.json").readText()).getJSONObject("leituras")
        j.keys().asSequence().associateWith { j.getString(it) }
    }

    private fun group() = EcuParameterSnapshot.GROUPS.first { g -> g.any { it.name == "TIPO_SENSORE" } }
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    @Test
    fun `grupo NIVEL tem exatamente as leituras que o original faz e so de leitura`() {
        val names = group().map { it.name }
        assertEquals(listOf("TIPO_SENSORE", "RIF_SENSORE", "LO_PASS_FILT_CON_FAST", "LO_PASS_FILT_CON_SLOW", "ISTERESI_RIACCENSIONE",
            "SOGLIA_LED_1", "SOGLIA_LED_2", "SOGLIA_LED_3", "SOGLIA_LED_4"), names)
        assertTrue(group().all { it.kind in setOf(Kind.SCALAR, Kind.VECTOR, Kind.INDEXED) })
        group().forEach { e ->
            val first = EcuParameterSnapshot.request(e)[0].toInt() and 0xFF
            assertTrue("${e.name} usa opcode de LEITURA (0x09/0x29/0x0A), nunca de escrita", first in setOf(0x09, 0x29, 0x0A))
        }
    }

    @Test
    fun `cada pedido e identico ao que o original enviou no fio e a ECU respondeu`() {
        group().forEach { e ->
            val request = hex(EcuParameterSnapshot.request(e))
            assertTrue("pedido ${e.name} = $request tem de existir no fio real", fio.containsKey(request))
        }
    }

    @Test
    fun `lendo com as respostas reais da ECU do dono sai tipo 0x81, referencias 39 93 143 219, filtro e LEDs`() {
        val items = EcuParameterSnapshot.run(group()) { e ->
            val resp = fio.getValue(hex(EcuParameterSnapshot.request(e))).split(' ').map { it.toInt(16) }.toIntArray()
            ReadOutcome.Ok(resp)
        }
        val cal = LevelCalibration.fromParams(EcuParameterSnapshot.toJson(items, "AUTO_START").getJSONObject("params"))
        assertEquals(true, cal.getBoolean("complete"))
        assertEquals(129, cal.getInt("typeRaw"))
        assertEquals("0x81", cal.getString("typeHex"))
        assertEquals("[39,93,143,219]", cal.getJSONArray("refs").toString())
        assertEquals(32768, cal.getJSONObject("filter").getInt("fastRaw"))
        assertEquals(4915, cal.getJSONObject("filter").getInt("slowRaw"))
        assertEquals(1.0, cal.getJSONObject("filter").getDouble("fast"), 1e-9)
        assertEquals(0.15, cal.getJSONObject("filter").getDouble("slow"), 0.001)
        assertEquals(3, cal.getInt("hysteresis"))
        assertEquals("[10,37,62,90]", cal.getJSONArray("leds").toString())
        assertEquals("INVERTED_255_EMPTY", cal.getJSONObject("scale").getString("orientation"))
        assertEquals("[75,125,177,253]", cal.getJSONObject("scale").getJSONArray("ladderObserved").toString())
    }

    @Test
    fun `NACK ou falha viram null e complete false, nunca zero`() {
        val items = EcuParameterSnapshot.run(group()) { e ->
            if (e.name == "RIF_SENSORE") ReadOutcome.Nack(0xCA) else ReadOutcome.Ok(IntArray(e.width * e.count) { 5 })
        }
        val cal = LevelCalibration.fromParams(EcuParameterSnapshot.toJson(items, "t").getJSONObject("params"))
        assertFalse(cal.getBoolean("complete"))
        assertTrue(cal.isNull("refs"))
        assertEquals("NACK", cal.getJSONObject("states").getString("RIF_SENSORE"))
        assertEquals(5, cal.getInt("typeRaw"))
    }

    @Test
    fun `sem nenhuma leitura do grupo nada e inventado`() {
        val cal = LevelCalibration.fromParams(JSONObject())
        assertFalse(cal.getBoolean("complete"))
        assertNull(cal.opt("typeRaw")?.takeIf { it != JSONObject.NULL })
        assertTrue(cal.isNull("leds"))
    }
}
