package com.omegas.prohub.blue

import com.omegas.v7.runtime.FuelComparisonV7
import org.json.JSONObject
import kotlin.math.ln

/**
 * Auto-Cal is an orchestrator/consumer only. It receives observed Blue
 * comparisons and an actuator gain learned by [BlueCausalEngine]; it never owns
 * independent residual or K-target mathematics.
 */
class BlueAutoCalAdapter(
    private val engine: BlueCausalEngine,
) {
    fun proposal(
        comparison: FuelComparisonV7,
        gain: BlueActuatorGain?,
    ): BlueCorrectionProposal {
        require(comparison.petrolTargetMs > 0.0)
        require(comparison.petrolOnCngMs > 0.0)
        val errorLog = engine.cngErrorLog(
            petrolOnCngMs = comparison.petrolOnCngMs,
            petrolReferenceMs = comparison.petrolTargetMs,
        )
        return BlueCorrectionProposal(
            calibrationState = engine.calibrationState(comparison.revision),
            correctionMultiplier = engine.correctionMultiplier(errorLog, gain),
            errorLog = errorLog,
            errorPercent = engine.errorPercentFromLog(errorLog),
            actuatorGain = gain,
            automaticWrite = false,
        )
    }

    fun proposalJson(
        comparison: FuelComparisonV7,
        gain: BlueActuatorGain?,
    ): JSONObject {
        val proposal = proposal(comparison, gain)
        return JSONObject()
            .put("ok", true)
            .put("mode", "BLUE_CAUSAL_ENGINE")
            .put("automatic", false)
            .put("manualOnly", true)
            .put("curveRevision", proposal.calibrationState.curveK)
            .put("mapRevision", proposal.calibrationState.mapK)
            .put("petrolReferenceMs", comparison.petrolTargetMs)
            .put("petrolOnCngMs", comparison.petrolOnCngMs)
            .put("errorLog", proposal.errorLog)
            .put("errorPercent", proposal.errorPercent)
            .put("actuatorGain", proposal.actuatorGain?.gain ?: JSONObject.NULL)
            .put("correctionMultiplier", proposal.correctionMultiplier ?: JSONObject.NULL)
            .put(
                "state",
                if (proposal.correctionMultiplier == null) "MEASURE_GAIN_FIRST" else "PROPOSAL_READY",
            )
    }

    /**
     * Convenience helper for historical event identification. No default gain is
     * fabricated when before/after K does not produce a valid causal estimate.
     */
    fun learnGain(
        beforePetrolOnCngMs: Double,
        beforePetrolReferenceMs: Double,
        afterPetrolOnCngMs: Double,
        afterPetrolReferenceMs: Double,
        beforeK: Double,
        afterK: Double,
    ): BlueActuatorGain? = engine.actuatorGain(
        beforeErrorLog = ln(beforePetrolOnCngMs / beforePetrolReferenceMs),
        afterErrorLog = ln(afterPetrolOnCngMs / afterPetrolReferenceMs),
        beforeK = beforeK,
        afterK = afterK,
    )
}
