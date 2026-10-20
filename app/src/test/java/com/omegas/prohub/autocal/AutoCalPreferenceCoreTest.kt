package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quadros iguais aos do oráculo de docs/progbase/protocolo/autocal-preferencias.md (OBS-COD) e regras de recusa. */
class AutoCalPreferenceCoreTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    private fun frame(id: String, raw: Int) = hex(AutoCalPreferenceCore.writeFrame(AutoCalPreferenceCore.spec(id)!!, raw))

    @Test fun `quadros de gravacao batem com o oraculo do original`() {
        assertEquals("13 65 01 02 03 7E", frame("VECT_AUTOCAL_U8_2", 3))
        assertEquals("13 7A 01 B8 0B 51", frame("MAX_RPM_FOR_AUTOCAL", 3000))
        assertEquals("12 8B 01 01 9F", frame("DISABLE_ACQ_BAND", 1))
        assertEquals("12 4A 01 01 5E", frame("AUTO_CAL_ENABLE", 1))
        assertEquals("13 69 01 33 00 B0", frame("LIMIT_PRESSURE_MIN", 51))
        assertEquals("13 6A 01 CC 03 4D", frame("LIMIT_PRESSURE_MAX", 972))
        assertEquals("14 4C 01 05 00 02 68", frame("MNFLD_PRESS_THD:5", 512))
        assertEquals("14 4B 01 05 00 04 69", frame("PETR_INJ_TBP:5", 1024))
    }

    @Test fun `catalogo so tem itens comprovados e nenhum toca a curva K`() {
        assertTrue(AutoCalPreferenceCore.SPECS.none { it.sc == AutoCalProtocol.MUL_ACT.address })
        assertEquals(AutoCalPreferenceCore.SPECS.size, AutoCalPreferenceCore.SPECS.map { it.id }.toSet().size)
    }

    @Test fun `decodifica com sinal e devolve nulo para resposta curta`() {
        val s = AutoCalPreferenceCore.spec("LIMIT_PRESSURE_MIN")!!
        assertEquals(-2, AutoCalPreferenceCore.decode(s, intArrayOf(0xFE, 0xFF)))
        assertNull(AutoCalPreferenceCore.decode(s, intArrayOf(0xFE)))
        val v = AutoCalPreferenceCore.spec("MNFLD_PRESS_THD:1")!!
        assertEquals(0x0201, AutoCalPreferenceCore.decode(v, intArrayOf(0, 0, 1, 2)))
        assertNull(AutoCalPreferenceCore.decode(v, intArrayOf(0, 0)))
    }

    @Test fun `recusas - faixa, desligar aprendizado, ordem dos pontos`() {
        val none: (String) -> Int? = { null }
        assertNotNull(AutoCalPreferenceCore.refusal(AutoCalPreferenceCore.spec("MAX_RPM_FOR_AUTOCAL")!!, 100, none))
        assertNotNull(AutoCalPreferenceCore.refusal(AutoCalPreferenceCore.spec("AUTO_CAL_ENABLE")!!, 0, none))
        assertNull(AutoCalPreferenceCore.refusal(AutoCalPreferenceCore.spec("AUTO_CAL_ENABLE")!!, 1, none))
        val refs = { id: String -> id.substringAfter(':').toIntOrNull()?.let { 100 + it * 10 } }
        val p5 = AutoCalPreferenceCore.spec("MNFLD_PRESS_THD:5")!!
        assertNull(AutoCalPreferenceCore.refusal(p5, 155, refs))
        assertNotNull(AutoCalPreferenceCore.refusal(p5, 150, refs))
        assertNotNull(AutoCalPreferenceCore.refusal(p5, 160, refs))
        assertNotNull(AutoCalPreferenceCore.refusal(p5, 155, none)) // vizinho ilegível: não envia
        assertNotNull(AutoCalPreferenceCore.refusal(AutoCalPreferenceCore.spec("LIMIT_PRESSURE_MIN")!!, 60) { 50 })
        assertNull(AutoCalPreferenceCore.refusal(AutoCalPreferenceCore.spec("LIMIT_PRESSURE_MIN")!!, 40) { 50 })
        assertFalse(AutoCalPreferenceCore.spec("NAO_EXISTE") != null)
    }
}
