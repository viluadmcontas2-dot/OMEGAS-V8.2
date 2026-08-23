package com.omegas.prohub.physics

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KStarAggregateEvidenceTest {
    @Test fun aggregateObservationDoesNotInventSingularPhysicalEvidence() {
        val assessment = KStarEstimator.assess(
            petrolOnGas = ScientificMeasurement(
                valueMs = 4.4,
                evidence = evidence(
                    authority = ScientificAuthority.CLASSIC_ASSISTED,
                    role = ScientificEvidenceRole.OBSERVATION,
                    evidenceId = "CNG-SURFACE-REV-12",
                    support = 3.5,
                ),
            ),
            petrolReference = ScientificMeasurement(
                valueMs = 4.0,
                evidence = evidence(
                    authority = ScientificAuthority.CLASSIC_ASSISTED,
                    role = ScientificEvidenceRole.OBSERVATION,
                    evidenceId = "PETROL-SURFACE-REV-11",
                    support = 4.5,
                ),
            ),
        )

        assertTrue(assessment.eligible)
        assertEquals("OBSERVATION_ELIGIBLE", assessment.reason)
        assertEquals(ln(4.4 / 4.0), assessment.logError, 1e-12)
        assertTrue(assessment.scientificTrace.petrolOnGasPhysicalEvidenceIds.isEmpty())
        assertTrue(assessment.scientificTrace.petrolReferencePhysicalEvidenceIds.isEmpty())
        assertEquals(setOf("CNG-SURFACE-REV-12"), assessment.scientificTrace.petrolOnGasEvidenceIds)
        assertEquals(setOf("PETROL-SURFACE-REV-11"), assessment.scientificTrace.petrolReferenceEvidenceIds)
    }

    @Test fun predictionAggregateCannotMasqueradeAsObservation() {
        val assessment = KStarEstimator.assess(
            petrolOnGas = ScientificMeasurement(
                4.4,
                evidence(
                    ScientificAuthority.ADAPTIVE_SHADOW,
                    ScientificEvidenceRole.PREDICTION,
                    "ADAPTIVE-PRED-7",
                    support = 2.0,
                ),
            ),
            petrolReference = ScientificMeasurement(
                4.0,
                evidence(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "PETROL-SURFACE-11",
                    support = 5.0,
                ),
            ),
        )

        assertFalse(assessment.eligible)
        assertEquals("PREDICTION_IS_NOT_OBSERVATION", assessment.reason)
    }

    @Test fun zeroAggregateSupportCannotBecomeKStarObservation() {
        val assessment = KStarEstimator.assess(
            petrolOnGas = ScientificMeasurement(
                4.4,
                evidence(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "CNG-ZERO",
                    support = 0.0,
                ),
            ),
            petrolReference = ScientificMeasurement(
                4.0,
                evidence(
                    ScientificAuthority.CLASSIC_ASSISTED,
                    ScientificEvidenceRole.OBSERVATION,
                    "PETROL-OK",
                    support = 3.0,
                ),
            ),
        )

        assertFalse(assessment.eligible)
        assertEquals("NO_SCIENTIFIC_WEIGHT", assessment.reason)
    }

    @Test fun sameAggregateEvidenceCannotCompareAgainstItself() {
        val shared = evidence(
            ScientificAuthority.CLASSIC_ASSISTED,
            ScientificEvidenceRole.OBSERVATION,
            "SURFACE-SHARED-9",
            support = 2.0,
        )
        val assessment = KStarEstimator.assess(
            petrolOnGas = ScientificMeasurement(4.4, shared),
            petrolReference = ScientificMeasurement(4.0, shared),
        )

        assertFalse(assessment.eligible)
        assertEquals("SELF_COMPARISON_EVIDENCE", assessment.reason)
    }

    @Test fun producerRelabelingDoesNotChangeObservedLogError() {
        val results = ScientificAuthority.values().map { authority ->
            KStarEstimator.assess(
                petrolOnGas = ScientificMeasurement(
                    4.4,
                    evidence(authority, ScientificEvidenceRole.OBSERVATION, "CNG-${authority.name}", 1.0),
                ),
                petrolReference = ScientificMeasurement(
                    4.0,
                    evidence(authority, ScientificEvidenceRole.OBSERVATION, "PETROL-${authority.name}", 1.0),
                ),
            )
        }

        assertEquals(1, results.map { it.logError }.toSet().size)
        results.forEach { assertTrue(it.eligible) }
    }

    private fun evidence(
        authority: ScientificAuthority,
        role: ScientificEvidenceRole,
        evidenceId: String,
        support: Double,
    ): KStarScientificEvidence = KStarScientificEvidence(
        authorities = setOf(authority),
        role = role,
        evidenceIds = setOf(evidenceId),
        physicalEvidenceIds = emptySet(),
        effectiveSupport = support,
        provenance = setOf("aggregate-test"),
    )
}
