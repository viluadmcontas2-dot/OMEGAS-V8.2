package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastCngObserverTest {
    @Test
    fun `prediction is prior only and uses local median`() {
        val observer = FastCngObserver()
        val reference = FastPetrolReferenceEstimate(
            petrolTargetMs = 4.0,
            quality = 0.9,
            stage = "ACCEPTED",
        )

        val first = observer.observe(telemetry(rpm = 2_000, mapBar = 0.50, petrolMs = 4.4), reference)
        assertEquals("LEARNING_LOCAL_NEIGHBORHOOD", first.state)
        assertNull(first.localEstimatePercent)
        assertEquals(1L, first.observations)
        assertEquals(0L, first.predictions)

        val second = observer.observe(telemetry(rpm = 2_050, mapBar = 0.505, petrolMs = 4.5), reference)
        assertEquals("LOCAL_ESTIMATE_READY", second.state)
        assertEquals(10.0, second.localEstimatePercent!!, 1e-9)
        assertEquals(12.5, second.rawErrorPercent!!, 1e-9)
        assertEquals(2.5, second.localResidualPercent!!, 1e-9)
        assertEquals(1, second.localSupport)
        assertEquals(2L, second.observations)
        assertEquals(1L, second.predictions)
        assertFalse(second.toJson().getBoolean("runtimeFuelSwitchingRequired"))
    }

    @Test
    fun `outside local radius does not borrow unrelated error`() {
        val observer = FastCngObserver()
        val reference = FastPetrolReferenceEstimate(4.0, 1.0, "CONFIRMED")

        observer.observe(telemetry(rpm = 2_000, mapBar = 0.50, petrolMs = 4.4), reference)
        val far = observer.observe(telemetry(rpm = 2_200, mapBar = 0.55, petrolMs = 4.4), reference)

        assertEquals("LEARNING_LOCAL_NEIGHBORHOOD", far.state)
        assertNull(far.localEstimatePercent)
        assertEquals(0, far.localSupport)
    }

    @Test
    fun `history is bounded and reset clears session memory`() {
        val observer = FastCngObserver()
        val reference = FastPetrolReferenceEstimate(4.0, 1.0, "CONFIRMED")

        repeat(FastCngObserver.MAX_HISTORY + 20) { index ->
            observer.observe(
                telemetry(
                    rpm = 2_000 + (index % 20),
                    mapBar = 0.50,
                    petrolMs = 4.2,
                    at = index.toLong(),
                ),
                reference,
            )
        }

        val full = observer.snapshot()
        assertEquals(FastCngObserver.MAX_HISTORY, full.historySize)
        assertTrue(full.predictions > 0L)

        observer.reset()
        val reset = observer.snapshot()
        assertEquals("WAITING_FOR_CNG", reset.state)
        assertEquals(0, reset.historySize)
        assertEquals(0L, reset.observations)
        assertEquals(0L, reset.predictions)
    }

    private fun telemetry(
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
        at: Long = 1L,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = 120,
        gasMsDiagnostic = 6.0,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 1,
        fuel = Mp48Fuel.CNG,
        state = Mp48Fuel.CNG.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
