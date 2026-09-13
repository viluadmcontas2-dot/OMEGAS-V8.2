package com.omegas.prohub.blue

import com.omegas.prohub.ecu.KFactorProtocol

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
        fun mapCell(row: Int, column: Int) = BlueActuatorAddress(BlueActuatorKind.MAP_CELL, row = row, column = column)
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
 * State-free causal gate. Gain is accepted only when the confirmed actuator
 * actually participated in both measurements inside one continuous physical
 * RPM x MAP region. Quantization labels never substitute physical matching.
 */
class BlueCausalAttribution(
    private val engine: BlueCausalEngine = BlueCausalEngine(),
) {
    fun evaluate(
        intervention: BlueCausalIntervention,
        before: FuelComparison,
        after: FuelComparison,
    ): BlueAttributionResult {
        if (!intervention.ackConfirmed || !intervention.readbackConfirmed) return abstain("WRITE_NOT_CONFIRMED")
        if (intervention.changedActuators.size != 1 || intervention.changedActuators.single() != intervention.actuator) {
            return abstain("INTERVENTION_NOT_ISOLATED")
        }
        if (before.revision != intervention.beforeRevision || after.revision != intervention.afterRevision ||
            !revisionCloses(intervention)
        ) return abstain("REVISION_MISMATCH")
        if (!samePhysicalRegion(intervention, before, after)) return abstain("REGION_MISMATCH")
        if (before.createdAtMs > intervention.confirmedAtMs || after.createdAtMs < intervention.confirmedAtMs) {
            return abstain("EVIDENCE_TIME_MISMATCH")
        }
        when (intervention.actuator.kind) {
            BlueActuatorKind.MAP_CELL -> if (!mapCellParticipated(intervention.actuator, before, after)) {
                return abstain("MAP_CELL_NOT_PARTICIPATING")
            }
            BlueActuatorKind.CURVE_POINT -> if (!curvePointParticipated(intervention.actuator, before, after)) {
                return abstain("CURVE_POINT_NOT_PARTICIPATING")
            }
        }

        val beforeError = engine.cngErrorLog(before.petrolOnCngMs, before.petrolTargetMs)
        val afterError = engine.cngErrorLog(after.petrolOnCngMs, after.petrolTargetMs)
        val gain = engine.actuatorGain(beforeError, afterError, intervention.beforeK, intervention.afterK)
            ?: return abstain("INVALID_GAIN")

        return BlueAttributionResult(
            BlueAttributionState.ACCEPTED,
            "ISOLATED_CONFIRMED_RESPONSE",
            BlueGainObservation(
                intervention.id,
                intervention.actuator,
                intervention.beforeRevision,
                intervention.afterRevision,
                before.scientificRegionId,
                before.id,
                after.id,
                beforeError,
                afterError,
                intervention.beforeK,
                intervention.afterK,
                gain,
                after.createdAtMs,
            ),
        )
    }

    private fun samePhysicalRegion(
        intervention: BlueCausalIntervention,
        before: FuelComparison,
        after: FuelComparison,
    ): Boolean {
        if (!BlueScientificRegion.isVersionedId(intervention.scientificRegionId) ||
            !BlueScientificRegion.isVersionedId(before.scientificRegionId) ||
            !BlueScientificRegion.isVersionedId(after.scientificRegionId)
        ) return false
        val intended = BlueScientificRegion.parse(intervention.scientificRegionId) ?: return false
        return intended.physicallyMatches(before.rpm, before.mapBar) &&
            intended.physicallyMatches(after.rpm, after.mapBar) &&
            BlueScientificRegion.physicallyMatches(before.rpm, before.mapBar, after.rpm, after.mapBar)
    }

    private fun mapCellParticipated(
        actuator: BlueActuatorAddress,
        before: FuelComparison,
        after: FuelComparison,
    ): Boolean {
        val row = actuator.row ?: return false
        val column = actuator.column ?: return false
        val beforeCell = BlueMapKAddressing.cell(before)
        val afterCell = BlueMapKAddressing.cell(after)
        return beforeCell.optInt("row", -1) == row && beforeCell.optInt("column", -1) == column &&
            afterCell.optInt("row", -1) == row && afterCell.optInt("column", -1) == column
    }

    private fun curvePointParticipated(
        actuator: BlueActuatorAddress,
        before: FuelComparison,
        after: FuelComparison,
    ): Boolean {
        val index = actuator.index ?: return false
        fun participates(petrolMs: Double): Boolean {
            val (lower, upper, _) = KFactorProtocol.blendAxis(petrolMs)
            return index == lower || index == upper
        }
        return participates(before.petrolOnCngMs) && participates(after.petrolOnCngMs)
    }

    private fun revisionCloses(intervention: BlueCausalIntervention): Boolean = when (intervention.actuator.kind) {
        BlueActuatorKind.CURVE_POINT ->
            intervention.afterRevision.curveK == intervention.beforeRevision.curveK + 1 &&
                intervention.afterRevision.mapK == intervention.beforeRevision.mapK
        BlueActuatorKind.MAP_CELL ->
            intervention.afterRevision.mapK == intervention.beforeRevision.mapK + 1 &&
                intervention.afterRevision.curveK == intervention.beforeRevision.curveK
    }

    private fun abstain(reason: String) = BlueAttributionResult(BlueAttributionState.ABSTAIN, reason)
}
