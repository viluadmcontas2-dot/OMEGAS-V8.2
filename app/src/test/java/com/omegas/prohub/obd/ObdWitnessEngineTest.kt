package com.omegas.prohub.obd

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Test

class ObdWitnessEngineTest {
    @Test
    fun `GNV residual is relative to compatible gasoline STFT not zero`() {
        val engine = ObdWitnessEngine()
        repeat(5) { index ->
            engine.observe(
                sample(
                    fuel = "PETROL",
                    stft = 2.0 + index * 0.1,
                    rpm = 2000.0,
                    map = 0.55,
                    petrol = 4.5,
                    state = "curve=0;map=0",
                ),
            )
        }
        repeat(5) { index ->
            engine.observe(
                sample(
                    fuel = "GNV",
                    stft = 9.8 + index * 0.1,
                    rpm = 2010.0,
                    map = 0.56,
                    petrol = 4.5,
                    state = "curve=0;map=0",
                ),
            )
        }

        val result = engine.evaluate(
            rpm = 2000.0,
            mapBar = 0.55,
            petrolMs = 4.5,
            calibrationState = "curve=0;map=0",
        )

        assertEquals(2.2, result.gasolineReferencePct!!, 0.15)
        assertEquals(10.0, result.gnvStftPct!!, 0.15)
        assertEquals(7.8, result.residualPp!!, 0.25)
        val expectedRatio = 1.10 / 1.022
        assertEquals(expectedRatio, result.correctionRatio!!, 0.003)
        assertEquals(ln(expectedRatio), result.errorLog!!, 0.003)
        assertEquals((expectedRatio - 1.0) * 100.0, result.correctionPercent!!, 0.3)
    }

    @Test
    fun `zero gasoline and plus ten GNV means ten percent physical correction`() {
        val engine = ObdWitnessEngine()
        repeat(5) {
            engine.observe(sample("PETROL", 0.0, 1500.0, 0.45, 3.2, "curve=3;map=2"))
            engine.observe(sample("GNV", 10.0, 1505.0, 0.45, 3.2, "curve=3;map=2"))
        }

        val result = engine.evaluate(1500.0, 0.45, 3.2, "curve=3;map=2")

        assertEquals(1.10, result.correctionRatio!!, 1e-9)
        assertEquals(ln(1.10), result.errorLog!!, 1e-9)
        assertEquals(10.0, result.correctionPercent!!, 1e-9)
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
