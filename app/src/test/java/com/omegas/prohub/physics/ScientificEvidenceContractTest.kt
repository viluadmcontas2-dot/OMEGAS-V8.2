package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificEvidenceContractTest {
    @Test fun `same physical observation counts once and preserves every producer`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(
                    ScientificAuthority.OEM_NATIVE,
                    ScientificEvidenceRole.OBSERVATION,
                    "oem-frame-42",
                    "frame-42",
                    0.8,
                    "mp48-native",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "classic-frame-42",
                    "frame-42",
                    0.8,
                    "classic-forward",
                ),
            ),
        )

        assertTrue(resolution.conflicts.isEmpty())
        assertEquals(1, resolution.accepted.size)
        val evidence = resolution.accepted.single()
        assertEquals(
            setOf(ScientificAuthority.OEM_NATIVE, ScientificAuthority.CLASSIC_ASSISTED),
            evidence.authorities,
        )
        assertEquals(setOf("oem-frame-42", "classic-frame-42"), evidence.evidenceIds)
        assertEquals(0.8, evidence.effectiveWeight, 1e-12)
    }

    @Test fun `conflicting physical weights stay explicit`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(
                    ScientificAuthority.OEM_NATIVE,
                    ScientificEvidenceRole.OBSERVATION,
                    "a",
                    "frame-42",
                    1.0,
                    "oem",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "b",
                    "frame-42",
                    0.7,
                    "classic",
                ),
            ),
        )

        assertTrue(resolution.accepted.isEmpty())
        assertEquals("SCIENTIFIC_WEIGHT_CONFLICT", resolution.conflicts.single().reason)
    }

    @Test fun `observation prediction collision stays explicit`() {
        val resolution = PhysicsScientificInput.resolve(
            listOf(
                PhysicsScientificInput(
                    ScientificAuthority.OEM_NATIVE,
                    ScientificEvidenceRole.OBSERVATION,
                    "a",
                    "frame-42",
                    1.0,
                    "oem",
                ),
                PhysicsScientificInput(
                    ScientificAuthority.ADAPTIVE_SHADOW,
                    ScientificEvidenceRole.PREDICTION,
                    "pred-7",
                    "frame-42",
                    1.0,
                    "adaptive-model",
                ),
            ),
        )

        assertTrue(resolution.accepted.isEmpty())
        assertEquals("SCIENTIFIC_ROLE_CONFLICT", resolution.conflicts.single().reason)
    }

    @Test fun `prediction does not invent singular physical lineage`() {
        val prediction = PhysicsScientificInput(
            ScientificAuthority.ADAPTIVE_SHADOW,
            ScientificEvidenceRole.PREDICTION,
            "pred-aggregate-7",
            null,
            0.6,
            "adaptive-model",
        )

        assertNull(prediction.physicalEvidenceId)
        val resolution = PhysicsScientificInput.resolve(listOf(prediction))
        assertEquals(1, resolution.accepted.size)
        assertEquals(ScientificEvidenceRole.PREDICTION, resolution.accepted.single().role)
    }
}
