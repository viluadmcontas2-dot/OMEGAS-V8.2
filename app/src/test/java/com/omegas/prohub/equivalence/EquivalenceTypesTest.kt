package com.omegas.prohub.equivalence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquivalenceTypesTest {
    @Test
    fun `tolerancia e o maior entre 4 por cento e duas dispersoes`() {
        assertEquals(0.04, EquivalenceTolerances.tolerance(0.01), 1e-12)
        assertEquals(0.05, EquivalenceTolerances.tolerance(0.03), 1e-12) // teto de ±5%
        assertEquals(0.05, EquivalenceTolerances.tolerance(0.50), 1e-12)
        assertEquals(0.04, EquivalenceTolerances.tolerance(0.0), 1e-12)
        assertEquals(0.02, EquivalenceTolerances.CELL_BAR, 0.0)
        assertEquals(10, EquivalenceTolerances.USAGE_SESSIONS)
    }

    @Test
    fun `curva propria interpola so entre celulas conhecidas`() {
        val c = OwnCurve(
            Fuel.GASOLINA,
            listOf(
                OwnCell(0.31, null, 0, 0.0, CellSource.REFERENCE, null),
                OwnCell(0.33, 4.0, 5, 0.01, CellSource.OWN, 0.0),
                OwnCell(0.35, 5.0, 5, 0.01, CellSource.OWN, 0.0),
            ),
        )
        assertEquals(4.5, c.at(0.34)!!, 1e-12)
        assertNull(c.at(0.32))
        assertNull(c.at(0.36))
    }
}
