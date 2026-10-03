package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleTelemetryContractTest {
    private val keys = setOf("state", "reason", "classification", "frame_count", "minimum_frames", "desired_frames",
        "duration_ms", "median_interval_ms", "gap_ms", "learning_eligible", "fuel_confirmed", "reason_code",
        "window_age_ms", "window_budget_ms", "frames_evicted", "plausibility_reasons", "cell_key", "cell_row",
        "cell_column", "quality")

    @Test fun `evento de telemetria mantem as 20 chaves de sample`() {
        val json = SampleDecision.invalid("Leitura fisicamente implausível: teste").toTelemetryJson()
        assertEquals(keys, json.keys().asSequence().toSet())
        assertEquals("INVALID", json.getString("state"))
        assertEquals("PLAUSIBILITY_REJECTED", json.getString("reason_code"))
        assertEquals("INVALID", json.getString("classification"))
        assertEquals(-1, json.getInt("cell_row"))
        assertEquals(0.0, json.getDouble("quality"), 0.0)
        assertTrue(json.isNull("fuel_confirmed"))
    }

    @Test fun `snapshot completo do cerebro 2 nao existe mais`() {
        for (type in listOf(SampleDecision::class.java, MotorSample::class.java, SampleDiagnostics::class.java)) {
            assertTrue(type.simpleName, type.methods.none { it.name == "toJson" })
        }
    }
}
