package com.omegas.prohub.physics

import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsResidualEvidenceProductionTest {
    @Test
    fun primaryRpmMapTinjProjectionDoesNotRequireEnvironmentalContext() {
        val advice = JSONObject()
            .put("environmentGates", false)
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(localMapItem()))

        val item = AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice)
            .getJSONArray("mapResidualSuggestions")
            .getJSONObject(0)
        val evidence = item.getJSONObject("physicsResidualEvidence")

        assertEquals(3, evidence.getInt("comparableSamples"))
        assertTrue(evidence.getBoolean("localizedStructureSupported"))
        assertFalse(evidence.getBoolean("environmentalContextVerified"))
        assertEquals("MAP_LOCAL", item.getString("correctionMechanism"))
        assertEquals("LOCALIZED_REPEATABLE", item.getString("mechanismReasonCode"))
        assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
        assertFalse(item.getBoolean("idealTarget"))
    }

    @Test
    fun verifiedLocalizedProjectionPromotesMapMechanismWithoutPromotingMagnitude() {
        val advice = JSONObject()
            .put("environmentGates", true)
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(localMapItem()))

        val item = AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice)
            .getJSONArray("mapResidualSuggestions")
            .getJSONObject(0)

        assertEquals("MAP_LOCAL", item.getString("correctionMechanism"))
        assertEquals("LOCALIZED_REPEATABLE", item.getString("mechanismReasonCode"))
        assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
        assertFalse(item.getBoolean("idealTarget"))
    }

    @Test
    fun verifiedBroadProjectionNeedsCoherentPeerAndClearedLocalResidual() {
        val curve = JSONArray()
            .put(globalCurveItem(index = 3, direction = "INCREASE_CNG_DELIVERY"))
            .put(globalCurveItem(index = 4, direction = "INCREASE_CNG_DELIVERY"))
        val advice = JSONObject()
            .put("environmentGates", true)
            .put("kFactorSuggestions", curve)
            .put("mapResidualSuggestions", JSONArray().put(nonActionableMapItem()))

        val decorated = AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice)
        repeat(2) { index ->
            val item = decorated.getJSONArray("kFactorSuggestions").getJSONObject(index)
            val evidence = item.getJSONObject("physicsResidualEvidence")
            assertTrue(evidence.getBoolean("broadStructureSupported"))
            assertTrue(evidence.getBoolean("localResidualCleared"))
            assertEquals("CURVE_MUL_ACT", item.getString("correctionMechanism"))
            assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
            assertFalse(item.getBoolean("idealTarget"))
        }
    }

    @Test
    fun actionableLocalResidualBlocksCurvePromotion() {
        val curve = JSONArray()
            .put(globalCurveItem(index = 3, direction = "DECREASE_CNG_DELIVERY"))
            .put(globalCurveItem(index = 4, direction = "DECREASE_CNG_DELIVERY"))
        val advice = JSONObject()
            .put("environmentGates", true)
            .put("kFactorSuggestions", curve)
            .put("mapResidualSuggestions", JSONArray().put(localMapItem()))

        val item = AssistedCalibrationAdvisor.decoratePhysicsAuthority(advice)
            .getJSONArray("kFactorSuggestions")
            .getJSONObject(0)

        assertEquals("UNKNOWN", item.getString("correctionMechanism"))
        assertEquals("LOCAL_RESIDUAL_NOT_CLEARED", item.getString("mechanismReasonCode"))
    }

    private fun localMapItem(): JSONObject = JSONObject()
        .put("row", 2)
        .put("column", 4)
        .put("actionable", true)
        .put("usefulMarginPercent", 2.5)
        .put("confidence", 0.72)
        .put("effectiveSamples", 3.4)
        .put("uniqueVisits", 3)
        .put("globalTrendRemoved", true)
        .put("direction", "INCREASE_CNG_DELIVERY")

    private fun nonActionableMapItem(): JSONObject = JSONObject()
        .put("row", 2)
        .put("column", 4)
        .put("actionable", false)
        .put("usefulMarginPercent", 0.0)
        .put("confidence", 0.50)
        .put("effectiveSamples", 2.0)
        .put("uniqueVisits", 2)
        .put("globalTrendRemoved", true)
        .put("direction", "EQUIVALENT")

    private fun globalCurveItem(index: Int, direction: String): JSONObject = JSONObject()
        .put("index", index)
        .put("actionable", true)
        .put("usefulMarginPercent", 2.0)
        .put("confidence", 0.75)
        .put("effectiveSamples", 3.0)
        .put("uniqueVisits", 3)
        .put("rpmCoverage", 500.0)
        .put("direction", direction)
}
