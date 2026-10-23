package com.omegas.prohub.carinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Captura crua SEM perda da multimídia: cada mudança de payload das 17 transações vai inteira, com hex completo; repetição não grava. */
class MmLinesTest {
    @Test
    fun `mesmo payload repetido nao e mudanca e payload novo ou outra transacao e`() {
        val f = MmChangeFilter()
        val a = byteArrayOf(1, 2, 3)
        assertTrue(f.changed(6, a))
        assertFalse(f.changed(6, a.copyOf()))
        assertTrue(f.changed(6, byteArrayOf(1, 2, 4)))
        assertTrue(f.changed(17, a))
        assertFalse(f.changed(17, a))
        f.reset()
        assertTrue(f.changed(6, byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `linha crua leva o hex INTEIRO sem truncar, mesmo acima de 48 bytes`() {
        val payload = ByteArray(300) { (it % 251).toByte() }
        val j = MmLines.tx(17, payload)
        assertEquals(17, j.getInt("tx"))
        assertEquals(300, j.getInt("bytes"))
        assertEquals(600, j.getString("hex").length)
    }

    @Test
    fun `propriedades da tx17 saem com propId areaId e valor de TODAS, sem o teto de 64 do cartao`() {
        val props = (0 until 100).map { CarProp(0x11600200 + it, 0, "float", "$it.5", it + 0.5) }
        val j = MmLines.props(CarPropBatch("svc", props, 100, true))
        assertEquals(100, j.getJSONArray("values").length())
        assertEquals("0x11600200/0=0.5", j.getJSONArray("values").getString(0))
        assertEquals(100, j.getInt("declared"))
        assertTrue(j.getBoolean("complete"))
    }
}
