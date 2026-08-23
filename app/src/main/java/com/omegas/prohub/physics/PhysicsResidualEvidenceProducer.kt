package com.omegas.prohub.physics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Produces causal-mechanism evidence from statistics already emitted by the
 * real AssistedCalibrationAdvisor. It introduces no new sample-count target or
 * scientific score threshold: positive useful margin comes from the Advisor's
 * uncertainty/deadband decision, while structural breadth is topological
 * (another supported curve point with the same direction).
 *
 * Environmental context is never invented. `environmentGates=false` means the
 * bounded RPM+MAP+Tinj path does not use environment as a primary gate; the
 * flag is retained only to qualify optional environmental diagnostics.
 */
object PhysicsResidualEvidenceProducer {
    const val SOURCE = "ADVISOR_STATISTICAL_PROJECTION_V1"

    fun populate(advice: JSONObject): JSONObject {
        val mapItems = advice.optJSONArray("mapResidualSuggestions") ?: JSONArray()
        val curveItems = advice.optJSONArray("kFactorSuggestions") ?: JSONArray()
        val environmentalContextVerified = advice.optBoolean("environmentGates", false)

        val localStructurePresent = (0 until mapItems.length()).any { index ->
            mapItems.optJSONObject(index)?.let(::supportsLocalStructure) == true
        }

        repeat(mapItems.length()) { index ->
            val item = mapItems.optJSONObject(index) ?: return@repeat
            if (!item.has("physicsResidualEvidence")) {
                item.put(
                    "physicsResidualEvidence",
                    mapEvidence(item, environmentalContextVerified),
                )
            }
        }

        repeat(curveItems.length()) { index ->
            val item = curveItems.optJSONObject(index) ?: return@repeat
            if (!item.has("physicsResidualEvidence")) {
                item.put(
                    "physicsResidualEvidence",
                    curveEvidence(
                        item = item,
                        siblings = curveItems,
                        index = index,
                        environmentalContextVerified = environmentalContextVerified,
                        localResidualCleared = !localStructurePresent,
                    ),
                )
            }
        }
        return advice
    }

    private fun mapEvidence(item: JSONObject, environmentalContextVerified: Boolean): JSONObject {
        val localSupported = supportsLocalStructure(item)
        val visits = item.optInt("uniqueVisits", 0).coerceAtLeast(0)
        return baseEvidence(
            comparableSamples = visits,
            localizedRepeatability = finite01(item, "confidence"),
            broadCoherence = 0.0,
            environmentalContextVerified = environmentalContextVerified,
            environmentalExplanationSupported = item.optBoolean("environmentalExplanationSupported", false),
            contradictionObserved = item.optBoolean("contradictionObserved", false),
            mapMechanismSupported = item.optBoolean("globalTrendRemoved", false),
            curveMechanismSupported = false,
            localizedStructureSupported = localSupported,
            broadStructureSupported = false,
            localResidualCleared = false,
            direction = effectDirection(item.optString("direction")),
        ).put("source", SOURCE)
            .put("structuralReason", if (localSupported) {
                "ACTIONABLE_LOCAL_RESIDUAL_AFTER_GLOBAL_REMOVAL"
            } else {
                "LOCAL_STRUCTURE_NOT_SUPPORTED"
            })
    }

    private fun curveEvidence(
        item: JSONObject,
        siblings: JSONArray,
        index: Int,
        environmentalContextVerified: Boolean,
        localResidualCleared: Boolean,
    ): JSONObject {
        val direction = item.optString("direction")
        val usable = supportsActionableStatisticalSignal(item)
        val coherentPeer = usable && direction in causalDirections && (0 until siblings.length()).any { peerIndex ->
            if (peerIndex == index) return@any false
            val peer = siblings.optJSONObject(peerIndex) ?: return@any false
            peer.optString("direction") == direction && supportsActionableStatisticalSignal(peer)
        }
        return baseEvidence(
            comparableSamples = item.optInt("uniqueVisits", 0).coerceAtLeast(0),
            localizedRepeatability = 0.0,
            broadCoherence = finite01(item, "confidence"),
            environmentalContextVerified = environmentalContextVerified,
            environmentalExplanationSupported = item.optBoolean("environmentalExplanationSupported", false),
            contradictionObserved = item.optBoolean("contradictionObserved", false),
            mapMechanismSupported = false,
            curveMechanismSupported = coherentPeer,
            localizedStructureSupported = false,
            broadStructureSupported = coherentPeer,
            localResidualCleared = localResidualCleared,
            direction = effectDirection(direction),
        ).put("source", SOURCE)
            .put("structuralReason", when {
                !usable -> "GLOBAL_POINT_NOT_STATISTICALLY_ACTIONABLE"
                !coherentPeer -> "NO_COHERENT_DISTINCT_CURVE_PEER"
                !localResidualCleared -> "ACTIONABLE_LOCAL_RESIDUAL_REMAINS"
                else -> "COHERENT_GLOBAL_STRUCTURE_WITH_LOCAL_RESIDUAL_CLEARED"
            })
    }

    private fun supportsLocalStructure(item: JSONObject): Boolean =
        item.optBoolean("globalTrendRemoved", false) && supportsActionableStatisticalSignal(item)

    private fun supportsActionableStatisticalSignal(item: JSONObject): Boolean {
        val effectiveSamples = finite(item, "effectiveSamples") ?: return false
        val usefulMargin = finite(item, "usefulMarginPercent") ?: return false
        val visits = item.optInt("uniqueVisits", 0)
        return item.optBoolean("actionable", false) &&
            effectiveSamples > 0.0 &&
            visits > 0 &&
            usefulMargin > 0.0 &&
            item.optString("direction") in causalDirections
    }

    private fun baseEvidence(
        comparableSamples: Int,
        localizedRepeatability: Double,
        broadCoherence: Double,
        environmentalContextVerified: Boolean,
        environmentalExplanationSupported: Boolean,
        contradictionObserved: Boolean,
        mapMechanismSupported: Boolean,
        curveMechanismSupported: Boolean,
        localizedStructureSupported: Boolean,
        broadStructureSupported: Boolean,
        localResidualCleared: Boolean,
        direction: EffectDirection,
    ): JSONObject = JSONObject()
        .put("comparableSamples", comparableSamples)
        .put("localizedRepeatability", localizedRepeatability)
        .put("broadCoherence", broadCoherence)
        .put("environmentalCorrelation", 0.0)
        .put("contradiction", if (contradictionObserved) 1.0 else 0.0)
        .put("mapMechanismSupported", mapMechanismSupported)
        .put("curveMechanismSupported", curveMechanismSupported)
        .put("direction", direction.name)
        .put("localizedStructureSupported", localizedStructureSupported)
        .put("broadStructureSupported", broadStructureSupported)
        .put("environmentalContextVerified", environmentalContextVerified)
        .put("environmentalExplanationSupported", environmentalExplanationSupported && environmentalContextVerified)
        .put("contradictionObserved", contradictionObserved)
        .put("localResidualCleared", localResidualCleared)

    private fun finite(source: JSONObject, key: String): Double? =
        source.optDouble(key, Double.NaN).takeIf(Double::isFinite)

    private fun finite01(source: JSONObject, key: String): Double =
        (finite(source, key) ?: 0.0).coerceIn(0.0, 1.0)

    private fun effectDirection(value: String): EffectDirection = when (value) {
        "INCREASE_CNG_DELIVERY", EffectDirection.INCREASE.name -> EffectDirection.INCREASE
        "DECREASE_CNG_DELIVERY", EffectDirection.DECREASE.name -> EffectDirection.DECREASE
        "EQUIVALENT", EffectDirection.NEUTRAL.name -> EffectDirection.NEUTRAL
        else -> EffectDirection.UNKNOWN
    }

    private val causalDirections = setOf("INCREASE_CNG_DELIVERY", "DECREASE_CNG_DELIVERY")
}
