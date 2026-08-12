package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutoCalScaleTest {
    @Test
    fun `tempo AutoCal usa raw por 512 milissegundos`() {
        assertEquals(5.0, AutoCalScale.injectionMs(0x0A00), 0.000001)
        assertEquals(0.5, AutoCalScale.injectionMs(0x0100), 0.000001)
    }

    @Test
    fun `MAP AutoCal usa S16 por 1024 bar`() {
        assertEquals(1.0, AutoCalScale.mapBar(1024), 0.000001)
        assertEquals(-0.5, AutoCalScale.mapBar(-512), 0.000001)
    }

    @Test
    fun `multiplicador AutoCal usa Q14`() {
        assertEquals(1.0, AutoCalScale.multiplierFromRaw(0x4000), 0.000001)
        assertEquals(1.03802490234375, AutoCalScale.multiplierFromRaw(0x426F), 0.000001)
    }

    @Test
    fun `escalas rejeitam raws fora do contrato`() {
        assertThrows(IllegalArgumentException::class.java) { AutoCalScale.injectionMs(-1) }
        assertThrows(IllegalArgumentException::class.java) { AutoCalScale.mapBar(Short.MAX_VALUE.toInt() + 1) }
        assertThrows(IllegalArgumentException::class.java) { AutoCalScale.multiplierFromRaw(0x1_0000) }
    }
}
