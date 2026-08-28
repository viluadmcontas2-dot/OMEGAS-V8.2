package com.omegas.prohub.learning

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedEquivalenceKStarBridgeTest {
    @Test fun boundedAdvisorProjectionCarriesTypedKStarObservationAssessment() {
        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 1_000.0,
                maxRpm = 4_000.0,
                rpmStep = 80.0,
                minMapBar = 0.20,
                maxMapBar = 1.20,
                mapStepBar = 0.02,
            ),
        )
        surface.observe(FuelLane.PETROL_REFERENCE, 2_400.0, 0.60, 3.00, 0.8, 11L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_400.0, 0.60, 3.30, 0.6, 12L)

        val snapshot = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 7)
        val row = snapshot.getJSONArray("comparisons").getJSONObject(0)

        assertTrue(row.getBoolean("kstar_observation_eligible"))
        assertEquals("OBSERVATION_ELIGIBLE", row.getString("kstar_observation_reason"))
        assertEquals(ln(3.30 / 3.00), row.getDouble("kstar_log_error"), 1e-12)
        assertTrue(row.getDouble("kstar_petrol_reference_support") > 0.0)
        assertTrue(row.getDouble("kstar_petrol_on_gas_support") > 0.0)

        val authorities = row.getJSONArray("kstar_authorities")
        assertEquals(1, authorities.length())
        assertEquals("CLASSIC_ASSISTED", authorities.getString(0))

        val referenceEvidence = row.getJSONArray("kstar_petrol_reference_evidence_ids")
        val cngEvidence = row.getJSONArray("kstar_petrol_on_gas_evidence_ids")
        assertTrue(referenceEvidence.length() > 0)
        assertTrue(cngEvidence.length() > 0)
        assertTrue(referenceEvidence.getString(0) != cngEvidence.getString(0))

        assertEquals(0, row.getJSONArray("kstar_petrol_reference_physical_evidence_ids").length())
        assertEquals(0, row.getJSONArray("kstar_petrol_on_gas_physical_evidence_ids").length())
    }
}
