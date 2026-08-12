package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitConfidenceTest {
    private fun evaluate(
        visits: Int,
        effective: Double = visits.toDouble(),
        spread: Double = 0.01,
        consensus: Double = 1.0,
    ) = VisitConfidence.evaluate(
        uniqueVisits = visits,
        effectiveVisits = effective,
        spread = spread,
        spreadLimit = 0.06,
        consensus = consensus,
        provisionalVisits = 2,
        acceptedVisits = 4,
        confirmedVisits = 6,
    )

    @Test
    fun `muitas janelas da mesma visita continuam uma visita`() {
        val result = evaluate(visits = 1, effective = 1.0)
        assertEquals("OBSERVED", result.stage)
        assertEquals(1, result.uniqueVisits)
        assertTrue(result.confidence < 0.60)
    }

    @Test
    fun `duas visitas criam estado provisório`() {
        assertEquals("PROVISIONAL", evaluate(2).stage)
    }

    @Test
    fun `quatro visitas repetiveis criam estado aceito`() {
        assertEquals("ACCEPTED", evaluate(4).stage)
    }

    @Test
    fun `seis visitas repetiveis confirmam`() {
        val result = evaluate(6)
        assertEquals("CONFIRMED", result.stage)
        assertTrue(result.confidence > 0.80)
    }

    @Test
    fun `dispersao alta impede confirmacao mesmo com seis visitas`() {
        val result = evaluate(visits = 6, spread = 0.20)
        assertEquals("PROVISIONAL", result.stage)
        assertTrue(result.repeatability < 0.10)
    }

    @Test
    fun `consenso baixo impede promover evidencia`() {
        val result = evaluate(visits = 6, consensus = 0.50)
        assertEquals("PROVISIONAL", result.stage)
    }
}
