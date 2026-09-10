package com.omegas.prohub.obd

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdMapKSuggestionTest {
    @Test
    fun `ready OBD result prepares one manual cell when address is resolved`() {
        val rows = JSONArray()
        repeat(12) { rows.put(JSONArray(List(12) { 100 })) }
        val result = ObdLearningResult(
            state = ObdLearningState.READY,
            stftMedianPct = 10.0,
            correctionMultiplier = 1.10,
            quality = 0.90,
            sampleCount = 8,
            epoch = "obd-2",
        )

        val proposal = ObdMapKSuggestion.prepare(
            learning = result,
            rpm = 1_850.0,
            resolvedPetrolMs = 4.5,
            mapRows = rows,
            addressConfidence = 0.90,
        )

        assertTrue(proposal.getBoolean("available"))
        assertEquals("OBD_MAP_K_READY", proposal.getString("state"))
        assertEquals(2, proposal.getInt("row"))
        assertEquals(4, proposal.getInt("column"))
        assertEquals(100, proposal.getInt("current"))
        assertEquals(110, proposal.getInt("target"))
        assertTrue(proposal.getBoolean("manualOnly"))
        assertEquals(1, proposal.getJSONArray("cells").length())
    }

    @Test
    fun `missing physical address fails closed without erasing percentage`() {
        val result = ObdLearningResult(ObdLearningState.READY, 10.0, 1.10, 0.90, 8, "obd-2")
        val proposal = ObdMapKSuggestion.prepare(result, 1_850.0, null, null, 0.0)

        assertFalse(proposal.getBoolean("available"))
        assertEquals("ADDRESS_UNRESOLVED", proposal.getString("state"))
        assertEquals(10.0, proposal.getDouble("correctionPercent"), 0.001)
    }
}
