package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedControlSurfaceScienceTest {
    @Test
    fun `surface exposes exactly 144 immutable control nodes`() {
        val nodes = FixedControlSurfaceScience.allNodes()
        assertEquals(144, nodes.size)
        val node45 = FixedControlSurfaceScience.controlNode(row = 4, column = 2)
        val node60 = FixedControlSurfaceScience.controlNode(row = 5, column = 2)
        assertEquals(1_850.0, node45.controlRpm, 0.0)
        assertEquals(4.5, node45.controlPetrolMs, 0.0)
        assertEquals(6.0, node60.controlPetrolMs, 0.0)
    }

    @Test
    fun `exact node receives all visit weight`() {
        val visit = FixedControlSurfaceScience.dedupeVisits(
            listOf(obs("v1", rpm = 1_850.0, petrolMs = 4.5, quality = 0.8))
        ).single()
        val projected = FixedControlSurfaceScience.projectVisit(visit)

        assertEquals(1, projected.size)
        assertEquals(4, projected.single().row)
        assertEquals(2, projected.single().column)
        assertEquals(0.8, projected.single().weight, 1e-12)
    }

    @Test
    fun `interior evidence uses only four bounding nodes and never renames them`() {
        val visit = FixedControlSurfaceScience.dedupeVisits(
            listOf(obs("v1", rpm = 2_100.0, petrolMs = 5.3, quality = 1.0))
        ).single()
        val projected = FixedControlSurfaceScience.projectVisit(visit)

        assertEquals(
            setOf(4 to 2, 4 to 3, 5 to 2, 5 to 3),
            projected.map { it.row to it.column }.toSet(),
        )
        assertEquals(1.0, projected.sumOf { it.weight }, 1e-12)
        assertTrue(projected.all { it.weight > 0.0 })

        assertEquals(4.5, FixedControlSurfaceScience.controlNode(4, 2).controlPetrolMs, 0.0)
        assertEquals(6.0, FixedControlSurfaceScience.controlNode(5, 2).controlPetrolMs, 0.0)
    }

    @Test
    fun `out of domain evidence clamps to declared physical endpoints`() {
        val low = FixedControlSurfaceScience.dedupeVisits(
            listOf(obs("low", rpm = 100.0, petrolMs = 0.5))
        ).single()
        val high = FixedControlSurfaceScience.dedupeVisits(
            listOf(obs("high", rpm = 9_000.0, petrolMs = 40.0))
        ).single()

        val lowProjected = FixedControlSurfaceScience.projectVisit(low)
        val highProjected = FixedControlSurfaceScience.projectVisit(high)

        assertEquals(setOf(0 to 0), lowProjected.map { it.row to it.column }.toSet())
        assertEquals(setOf(11 to 11), highProjected.map { it.row to it.column }.toSet())
        assertEquals(1.0, lowProjected.sumOf { it.weight }, 1e-12)
        assertEquals(1.0, highProjected.sumOf { it.weight }, 1e-12)
    }

    @Test
    fun `splitting identical physical evidence across USB sessions changes no science`() {
        val oneSession = listOf(
            obs("v1", rpm = 1_850.0, petrolMs = 4.5, residual = 1.2, session = "USB-A", at = 1_000L),
            obs("v2", rpm = 2_100.0, petrolMs = 5.3, residual = -0.8, quality = 0.8, session = "USB-A", at = 2_000L),
        )
        val manySessions = listOf(
            oneSession[0].copy(sessionId = "USB-1"),
            oneSession[1].copy(sessionId = "USB-99"),
        )

        assertEquals(
            signature(FixedControlSurfaceScience.projectObservations(oneSession)),
            signature(FixedControlSurfaceScience.projectObservations(manySessions)),
        )
    }

    @Test
    fun `same physical visit across reconnect is one vote and keeps sessions only as provenance`() {
        val visits = FixedControlSurfaceScience.dedupeVisits(
            listOf(
                obs("physical-v1", rpm = 1_850.0, petrolMs = 4.5, residual = 2.0, session = "USB-A", at = 1_000L),
                obs("physical-v1", rpm = 1_850.0, petrolMs = 4.5, residual = 2.0, session = "USB-B", at = 1_001L),
            )
        )

        assertEquals(1, visits.size)
        assertEquals(setOf("USB-A", "USB-B"), visits.single().provenanceSessions)
        assertEquals(1, FixedControlSurfaceScience.projectVisit(visits.single()).size)
    }

    @Test
    fun `support mass and Kish ESS stay separate`() {
        val observations = listOf(
            obs("v1", quality = 0.5, residual = 1.0),
            obs("v2", quality = 0.5, residual = 1.2, at = 2_000L),
            obs("v3", quality = 0.5, residual = 0.8, at = 3_000L),
        )
        val evidence = FixedControlSurfaceScience.evidenceFor(
            row = 4,
            column = 2,
            contributions = FixedControlSurfaceScience.projectObservations(observations),
        )

        assertEquals(1.5, evidence.supportMass, 1e-12)
        assertEquals(3.0, evidence.kishEss, 1e-12)
        assertEquals(3, evidence.uniqueVisits)
        assertEquals(1_850.0, evidence.controlNode.controlRpm, 0.0)
        assertEquals(4.5, evidence.controlNode.controlPetrolMs, 0.0)
        assertEquals(1_850.0, evidence.observedRpmCenter ?: Double.NaN, 1e-12)
        assertEquals(4.5, evidence.observedPetrolMsCenter ?: Double.NaN, 1e-12)
        assertEquals(1.0, evidence.residualMedianPercent ?: Double.NaN, 1e-12)
        assertEquals(0.2, evidence.residualMadPercent ?: Double.NaN, 1e-12)
    }

    private fun obs(
        key: String,
        rpm: Double = 1_850.0,
        petrolMs: Double = 4.5,
        residual: Double = 1.0,
        quality: Double = 1.0,
        session: String = "USB-A",
        at: Long = 1_000L,
    ) = FixedControlSurfaceScience.VisitObservation(
        visitKey = key,
        revision = 7,
        rpm = rpm,
        petrolMs = petrolMs,
        residualPercent = residual,
        quality = quality,
        collectedAtMs = at,
        sessionId = session,
    )

    private fun signature(
        values: List<FixedControlSurfaceScience.NodeContribution>,
    ): List<String> = values.map {
        listOf(
            it.row,
            it.column,
            it.revision,
            it.visitKey,
            "%.9f".format(java.util.Locale.US, it.residualPercent),
            "%.9f".format(java.util.Locale.US, it.weight),
            "%.9f".format(java.util.Locale.US, it.observedRpm),
            "%.9f".format(java.util.Locale.US, it.observedPetrolMs),
        ).joinToString("|")
    }.sorted()
}
