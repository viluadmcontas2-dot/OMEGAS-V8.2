package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluePresentationContractTest {
    private fun comparison() = FuelComparison(
        id = "cmp",
        revision = CalibrationRevision(2, 3),
        petrolVisitId = "petrol",
        cngVisitId = "gnv",
        rpm = 1850.0,
        mapBar = 0.55,
        petrolTargetMs = 3.0,
        petrolOnCngMs = 6.0,
        errorPercent = 100.0,
        quality = 0.9,
        createdAtMs = 10_000L,
    )

    @Test
    fun `proposal availability means a causal correction target exists`() {
        val engine = BlueCausalEngine()
        val adapter = BlueAutoCalAdapter(engine)
        val withoutGain = adapter.proposalJson(comparison(), null)
        assertTrue(withoutGain.getBoolean("evidenceAvailable"))
        assertFalse(withoutGain.getBoolean("available"))
        assertFalse(withoutGain.has("correctionMultiplier"))
        assertEquals("MEASURE_ACTUATOR_GAIN", withoutGain.getString("state"))

        val gain = engine.actuatorGain(0.10, 0.05, 1.0, 1.05)
        assertNotNull(gain)
        val withGain = adapter.proposalJson(comparison(), gain)
        assertTrue(withGain.getBoolean("available"))
        assertEquals("PROPOSAL_READY", withGain.getString("state"))
        assertTrue(withGain.getDouble("correctionMultiplier").isFinite())
    }

    @Test
    fun `presentation cell is the current GNV Map K cell`() {
        val fields = BlueMapKAddressing.presentationFields(comparison())
        val cell = fields.getJSONObject("mapKCell")
        assertEquals(cell.getInt("row"), fields.getInt("row"))
        assertEquals(cell.getInt("column"), fields.getInt("column"))
        assertEquals(cell.getString("key"), fields.getString("cellKey"))
        assertEquals(6.0, cell.getDouble("petrolBin"), 0.0)
        assertEquals(1850, cell.getInt("rpmBin"))
    }
}
