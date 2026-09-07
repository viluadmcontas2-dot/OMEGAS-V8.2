package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Test

class BlueMapKAddressingTest {
    @Test
    fun `Map K address uses current GNV petrol injection not gasoline reference`() {
        val comparison = FuelComparison(
            id = "cmp",
            revision = CalibrationRevision(2, 3),
            petrolVisitId = "petrol",
            cngVisitId = "gnv",
            rpm = 1850.0,
            mapBar = 0.55,
            petrolTargetMs = 3.0,
            petrolOnCngMs = 6.0,
            errorPercent = 8.0,
            quality = 0.9,
            createdAtMs = 10_000L,
        )

        val cell = BlueMapKAddressing.cell(comparison)

        assertEquals(6.0, cell.getDouble("petrolBin"), 0.0)
        assertEquals(1850, cell.getInt("rpmBin"))
    }
}
