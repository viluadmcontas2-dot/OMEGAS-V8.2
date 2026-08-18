package com.omegas.prohub.learning

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningSelectionReasonTest {
    @After
    fun cleanup() {
        LearningCalibrationAuthority.endPhysicalSession()
    }

    @Test
    fun detailedReferenceOutcomesMapToStableCanonicalReasons() {
        val cases = listOf(
            Triple(true, "LOCAL_REFERENCE_AVAILABLE", LearningSelectionReason.REFERENCE_FOUND),
            Triple(true, "NEAREST_LOCAL_REFERENCE", LearningSelectionReason.REFERENCE_FOUND),
            Triple(true, "BOUNDED_EXTRAPOLATION", LearningSelectionReason.REFERENCE_FOUND),
            Triple(false, "NO_PETROL_REGIONS", LearningSelectionReason.NO_REGION),
            Triple(false, "NO_LOCAL_PETROL_REFERENCE", LearningSelectionReason.NO_REGION),
            Triple(false, "REFERENCE_SPREAD_EXCEEDED", LearningSelectionReason.INSUFFICIENT_SUPPORT),
            Triple(false, "REFERENCE_WEIGHT_INVALID", LearningSelectionReason.NUMERIC_INVALID),
            Triple(false, "INVALID_CNG_CONDITION", LearningSelectionReason.INVALID_CONDITION),
            Triple(false, "ENV_MISMATCH", LearningSelectionReason.ENV_MISMATCH),
            Triple(false, "STALE", LearningSelectionReason.STALE),
            Triple(false, "MAP_GEOMETRY_UNKNOWN", LearningSelectionReason.GEOMETRY_UNKNOWN),
            Triple(false, "UNRECOGNIZED", LearningSelectionReason.UNKNOWN),
        )
        cases.forEach { (available, detail, expected) ->
            assertEquals(
                expected,
                LearningSelectionReason.fromReference(available, detail, geometryKnown = true),
            )
        }
    }

    @Test
    fun geometryUnknownOnlyOverridesAnOtherwiseFoundReference() {
        assertEquals(
            LearningSelectionReason.GEOMETRY_UNKNOWN,
            LearningSelectionReason.fromReference(
                available = true,
                detailReasonCode = "LOCAL_REFERENCE_AVAILABLE",
                geometryKnown = false,
            ),
        )
        assertEquals(
            LearningSelectionReason.NO_REGION,
            LearningSelectionReason.fromReference(
                available = false,
                detailReasonCode = "NO_PETROL_REGIONS",
                geometryKnown = false,
            ),
        )
    }

    @Test
    fun selectorSerializationPublishesCanonicalAndDetailedReasons() {
        LearningCalibrationAuthority.beginPhysicalSession()
        val result = PetrolReferenceSelector.Result(
            available = true,
            reasonCode = "LOCAL_REFERENCE_AVAILABLE",
            message = "ok",
            petrolTargetMs = 4.0,
        )
        val json = result.toJson()
        assertEquals("LOCAL_REFERENCE_AVAILABLE", json.getString("reason_code"))
        assertEquals("LOCAL_REFERENCE_AVAILABLE", json.getString("detail_reason_code"))
        assertEquals("GEOMETRY_UNKNOWN", json.getString("selection_reason_code"))

        LearningCalibrationAuthority.endPhysicalSession()
        val outsideManagedSession = result.toJson()
        assertEquals("REFERENCE_FOUND", outsideManagedSession.getString("selection_reason_code"))
    }

    @Test
    fun enumContainsAllContractedConsumerStates() {
        assertEquals(
            setOf(
                "REFERENCE_FOUND",
                "NO_REGION",
                "INSUFFICIENT_SUPPORT",
                "ENV_MISMATCH",
                "STALE",
                "GEOMETRY_UNKNOWN",
                "INVALID_CONDITION",
                "NUMERIC_INVALID",
                "UNKNOWN",
            ),
            LearningSelectionReason.entries.map { it.name }.toSet(),
        )
    }
}
