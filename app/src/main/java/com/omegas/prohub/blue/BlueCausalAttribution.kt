package com.omegas.prohub.blue

enum class BlueActuatorKind { CURVE_POINT, MAP_CELL }

data class BlueActuatorAddress(
    val kind: BlueActuatorKind,
    val index: Int? = null,
    val row: Int? = null,
    val column: Int? = null,
) {
    init {
        when (kind) {
            BlueActuatorKind.CURVE_POINT -> require(index != null && index >= 0 && row == null && column == null)
            BlueActuatorKind.MAP_CELL -> require(index == null && row != null && row >= 0 && column != null && column >= 0)
        }
    }

    companion object {
        fun curvePoint(index: Int) = BlueActuatorAddress(BlueActuatorKind.CURVE_POINT, index = index)
        fun mapCell(row: Int, column: Int) =
            BlueActuatorAddress(BlueActuatorKind.MAP_CELL, row = row, column = column)
    }
}

data class BlueCausalIntervention(
    val id: String,
    val actuator: BlueActuatorAddress,
    val beforeRevision: CalibrationRevision,
    val afterRevision: CalibrationRevision,
    val beforeK: Double,
    val afterK: Double,
    val ackConfirmed: Boolean,
    val readbackConfirmed: Boolean,
    val changedActuators: List<BlueActuatorAddress>,
    val confirmedAtMs: Long,
    val beforeComparisonId: String = "",
    val scientificRegionId: String = "",
) {
    init {
        require(id.isNotBlank())
        require(confirmedAtMs >= 0L)
    }
}

data class BlueGainObservation(
    val interventionId: String,
    val actuator: BlueActuatorAddress,
    val beforeRevision: CalibrationRevision,
    val afterRevision: CalibrationRevision,
    val scientificRegionId: String,
    val beforeComparisonId: String,
    val afterComparisonId: String,
    val beforeErrorLog: Double,
    val afterErrorLog: Double,
    val beforeK: Double,
    val afterK: Double,
    val gain: BlueActuatorGain,
    val observedAtMs: Long,
)

enum class BlueAttributionState { ACCEPTED, ABSTAIN }

data class BlueAttributionResult(
    val state: BlueAttributionState,
    val reason: String,
    val observation: BlueGainObservation? = null,
)

/**
 * State-free causal gate. It accepts only one isolated actuator change closed by
 * ACK, complete readback, matching calibration lineage and the same scientific
 * operating region before and after the write.
 */
class BlueCausalAttribution(
    private val engine: BlueCausalEngine = BlueCausalEngine(),
) {
    fun evaluate(
        intervention: BlueCausalIntervention,
        before: FuelComparison,
        after: FuelComparison,
    ): BlueAttributionResult {
        if (!intervention.ackConfirmed || !intervention.readbackConfirmed) {
            return abstain("WRITE_NOT_CONFIRMED")
        }
        if (intervention.changedActuators.size != 1 ||
            intervention.changedActuators.single() != intervention.actuator
        ) {
            return abstain("INTERVENTION_NOT_ISOLATED")
        }
        if (before.revision != intervention.beforeRevision ||
            after.revision != intervention.afterRevision ||
            !revisionCloses(intervention)
        ) {
            return abstain("REVISION_MISMATCH")
        }
        if (before.scientificRegionId.isBlank() ||
            before.scientificRegionId != after.scientificRegionId
        ) {
            return abstain("REGION_MISMATCH")
        }
        if (before.createdAtMs > intervention.confirmedAtMs ||
            after.createdAtMs < intervention.confirmedAtMs
        ) {
            return abstain("EVIDENCE_TIME_MISMATCH")
        }

        val beforeError = engine.cngErrorLog(before.petrolOnCngMs, before.petrolTargetMs)
        val afterError = engine.cngErrorLog(after.petrolOnCngMs, after.petrolTargetMs)
        val gain = engine.actuatorGain(
            beforeErrorLog = beforeError,
            afterErrorLog = afterError,
            beforeK = intervention.beforeK,
            afterK = intervention.afterK,
        ) ?: return abstain("INVALID_GAIN")

        return BlueAttributionResult(
            state = BlueAttributionState.ACCEPTED,
            reason = "ISOLATED_CONFIRMED_RESPONSE",
            observation = BlueGainObservation(
                interventionId = intervention.id,
                actuator = intervention.actuator,
                beforeRevision = intervention.beforeRevision,
                afterRevision = intervention.afterRevision,
                scientificRegionId = before.scientificRegionId,
                beforeComparisonId = before.id,
                afterComparisonId = after.id,
                beforeErrorLog = beforeError,
                afterErrorLog = afterError,
                beforeK = intervention.beforeK,
                afterK = intervention.afterK,
                gain = gain,
                observedAtMs = after.createdAtMs,
            ),
        )
    }

    private fun revisionCloses(intervention: BlueCausalIntervention): Boolean = when (intervention.actuator.kind) {
        BlueActuatorKind.CURVE_POINT ->
            intervention.afterRevision.curveK == intervention.beforeRevision.curveK + 1 &&
                intervention.afterRevision.mapK == intervention.beforeRevision.mapK
        BlueActuatorKind.MAP_CELL ->
            intervention.afterRevision.mapK == intervention.beforeRevision.mapK + 1 &&
                intervention.afterRevision.curveK == intervention.beforeRevision.curveK
    }

    private fun abstain(reason: String) =
        BlueAttributionResult(BlueAttributionState.ABSTAIN, reason)
}
