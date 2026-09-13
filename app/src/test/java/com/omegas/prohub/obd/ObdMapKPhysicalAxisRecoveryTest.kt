package com.omegas.prohub.obd

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdMapKPhysicalAxisRecoveryTest {
    @Test
    fun `map k address uses petrol injection as row and rpm as column`() {
        val rows = JSONArray()
        repeat(12) {
            val row = JSONArray()
            repeat(12) { row.put(120) }
            rows.put(row)
        }
        val result = ObdMapKSuggestion.prepare(
            learning = ObdLearningResult(
                state = ObdLearningState.READY,
                stftMedianPct = 10.0,
                correctionMultiplier = 1.10,
                quality = 0.95,
                sampleCount = 8,
                epoch = "e1",
            ),
            rpm = 2_500.0,
            resolvedPetrolMs = 10.0,
            mapRows = rows,
            addressConfidence = 0.95,
        )
        assertTrue(result.optBoolean("available"))
        assertEquals(7, result.getInt("row"))
        assertEquals(3, result.getInt("column"))
        assertEquals(132, result.getInt("target"))
    }
}
