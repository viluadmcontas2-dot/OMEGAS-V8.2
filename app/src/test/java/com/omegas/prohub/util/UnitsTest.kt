package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitsTest {
    @Test fun `formatos pt-BR fixos mesmo com outro idioma no aparelho`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("3,45 ms", Units.msUnit(3.4496))
            assertEquals("0,412 bar", Units.mapUnit(0.4123))
            assertEquals("2.032", Units.rpm(2032))
            assertEquals("1,234", Units.k(1.2344))
            assertEquals("3,4", Units.msBand(3.44))
        } finally { Locale.setDefault(old) }
    }

    @Test fun `diferenca em percentual tem sinal e uma casa e desconhecido e traco`() {
        assertEquals("+2,1%", Units.gapPercent(2.1))
        assertEquals("-0,4%", Units.gapPercent(-0.4))
        assertEquals("0,0%", Units.gapPercent(-0.01))
        assertEquals("+5,0%", Units.gapFromRatio(1.05))
        assertEquals("—", Units.gapPercent(null))
        assertEquals("—", Units.msUnit(Double.NaN))
        assertEquals("—", Units.mapUnit(null))
        assertEquals("—", Units.rpm(null as Int?))
        assertEquals("3%", Units.percentWhole(3.0))
    }
}
