package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdWitnessGnvOnlyTest {
    @Test
    fun `GNV STFT is usable without gasoline OBD samples`() {
        val engine = ObdWitnessEngine()
        repeat(5) { index ->
            engine.observe(sample("GNV", 9.8 + index * 0.1, 2000.0, 0.55, 4.8, "map-2:curve-3"))
        }

        val result = engine.evaluate(2000.0, 0.55, 4.8, "map-2:curve-3")

        assertEquals(ObdWitnessState.INSUFFICIENT, result.state)
        assertNull(result.gasolineReferencePct)
        assertEquals(10.0, result.gnvStftPct!!, 0.15)
        assertNull(result.correctionRatio)
        assertNull(result.correctionPercent)
        assertEquals(0, result.gasolineSamples)
        assertEquals(5, result.gnvSamples)
    }

    private fun sample(
        fuel: String,
        stft: Double,
        rpm: Double,
        map: Double,
        petrol: Double,
        state: String,
    ) = ObdWitnessSample(
        observedAtMs = 1_000L,
        stftPct = stft,
        rpm = rpm,
        mapBar = map,
        petrolMs = petrol,
        fuel = fuel,
        calibrationState = state,
        skewMs = 20L,
    )
}
