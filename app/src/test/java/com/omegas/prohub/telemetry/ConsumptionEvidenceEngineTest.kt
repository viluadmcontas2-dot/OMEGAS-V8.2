package com.omegas.prohub.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumptionEvidenceEngineTest {
    @Test
    fun `confirmed refuel computes economy from distance and added volume only`() {
        val engine = ConsumptionEvidenceEngine()
        val evidence = engine.confirmRefuel(timestampMs = 2_000L, distanceKm = 120.0, addedM3 = 10.0)
        assertTrue(evidence.accepted)
        assertTrue(evidence.confirmed)
        assertEquals(12.0, evidence.kmPerM3 ?: 0.0, 0.0001)
        assertEquals(ConsumptionEvidenceOrigin.REFUEL_CONFIRMED, evidence.origin)
    }

    @Test
    fun `pressure is estimate and timestamp regression is rejected`() {
        val engine = ConsumptionEvidenceEngine()
        val estimate = engine.estimateFromPressure(timestampMs = 1_000L, normalizedPressure = 128.0, capacityM3 = 15.0)
        assertTrue(estimate.accepted)
        assertFalse(estimate.confirmed)
        assertEquals(ConsumptionEvidenceOrigin.PRESSURE_ESTIMATE, estimate.origin)
        assertTrue(estimate.uncertaintyM3 > 0.0)

        val stale = engine.confirmRefuel(timestampMs = 999L, distanceKm = 50.0, addedM3 = 5.0)
        assertFalse(stale.accepted)
        assertEquals("OUT_OF_ORDER_TIMESTAMP", stale.reason)
    }
}
