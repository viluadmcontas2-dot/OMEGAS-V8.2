package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdFuelStateTest {
    @Test
    fun `transition is still gasoline and cut off is excluded`() {
        assertEquals(ObdScientificFuel.PETROL, ObdFuelState.normalize("GASOLINA"))
        assertEquals(ObdScientificFuel.PETROL, ObdFuelState.normalize("PETROL"))
        assertEquals(ObdScientificFuel.PETROL, ObdFuelState.normalize("TRANSITION"))
        assertEquals(ObdScientificFuel.PETROL, ObdFuelState.normalize("TRANSICAO"))
        assertEquals(ObdScientificFuel.PETROL, ObdFuelState.normalize("TRANSIÇÃO"))
        assertEquals(ObdScientificFuel.CNG, ObdFuelState.normalize("GNV"))
        assertEquals(ObdScientificFuel.CNG, ObdFuelState.normalize("CNG"))
        assertEquals(ObdScientificFuel.CNG, ObdFuelState.normalize("GAS"))
        assertNull(ObdFuelState.normalize("CUT_OFF"))
        assertNull(ObdFuelState.normalize("CUTOFF"))
        assertNull(ObdFuelState.normalize("UNKNOWN"))
        assertNull(ObdFuelState.normalize(""))
    }
}
