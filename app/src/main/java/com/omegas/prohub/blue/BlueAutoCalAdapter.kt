package com.omegas.prohub.blue

import org.json.JSONObject
import kotlin.math.ln

/** Auto-Cal consumes the single causal engine and never owns correction math. */
class BlueAutoCalAdapter(
    private val engine: BlueCausalEngine,
) {
    fun proposal(
        comparison: FuelComparison,
        gain: BlueActuatorGain?,
    ): BlueCorrectionProposal {
        require(comparison.petrolTargetMs > 0.0)
        require(comparison.petrolOnCngMs > 0.0)
        val errorLog = engine.cngErrorLog(comparison.petrolOnCngMs, comparison.petrolTargetMs)
        return BlueCorrectionProposal(
            calibrationState = engine.calibrationState(comparison.revision),
            correctionMultiplier = engine.correctionMultiplier(errorLog, gain),
            errorLog = errorLog,
            errorPercent = engine.errorPercentFromLog(errorLog),
            actuatorGain = gain,
            automaticWrite = false,
        )
    }

    fun proposalJson(comparison: FuelComparison, gain: BlueActuatorGain?): JSONObject {
        val proposal = proposal(comparison, gain)
        return JSONObject()
            .put("ok", true)
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
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
            .put("state", if (proposal.correctionMultiplier == null) "MEASURE_ACTUATOR_GAIN" else "PROPOSAL_READY")
    }

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
