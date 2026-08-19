package com.omegas.prohub.autocal

import com.omegas.prohub.telemetry.RuntimeFreshness
import com.omegas.prohub.telemetry.RuntimeFuel
import com.omegas.prohub.telemetry.RuntimeTelemetryFrame
import org.json.JSONObject

/**
 * Projeção observacional da Automatic ECU Calibration estacionária.
 *
 * A engenharia reversa confirma a existência do fluxo e suas pré-condições
 * gerais, mas ainda não identificou um bit/opcode confiável para ACTIVE,
 * COMPLETED ou FAILED. Por isso esses sinais permanecem null/UNKNOWN.
 * Esta classe não faz I/O, não agenda trabalho e não possui writer.
 */
object StationaryCalibrationProjection {
    const val ACTIVE_SIGNAL_EVIDENCE = "UNPROVEN"

    fun project(
        monitorStatus: JSONObject,
        frame: RuntimeTelemetryFrame?,
    ): JSONObject {
        val telemetryKnown = frame != null && frame.freshness != RuntimeFreshness.UNKNOWN
        val fuelKnown = telemetryKnown && frame?.fuel != RuntimeFuel.UNKNOWN
        val nativeStatusKnown = monitorStatus.has("nativeFlag13") && !monitorStatus.isNull("nativeFlag13")
        val identityKnown = monitorStatus.optBoolean("calibrationIdentityReady", false)

        val preconditions = StationaryCalibrationStateMachine.Preconditions(
            engineRunningKnown = telemetryKnown,
            fuelStateKnown = fuelKnown,
            nativeStatusKnown = nativeStatusKnown,
            calibrationIdentityKnown = identityKnown,
        )

        // Deliberadamente null: nativeFlag13 é raw e não possui semântica
        // estacionária comprovada. AutoCal acquisition e AutoMatch não são usados
        // como atalhos causais para este terceiro fluxo.
        val snapshot = StationaryCalibrationStateMachine.evaluate(
            preconditions = preconditions,
            nativeActive = null,
            completionObserved = null,
            failureObserved = null,
            outputObserved = false,
        )

        return JSONObject()
            .put("flow", NativeCalibrationFlow.AUTOMATIC_ECU_CALIBRATION_STATIONARY.name)
            .put("state", snapshot.state.name)
            .put("reason", snapshot.reason)
            .put("inputsKnown", snapshot.inputsKnown)
            .put("outputObserved", snapshot.outputObserved)
            .put("mutationScope", snapshot.mutationScope)
            .put("recovery", snapshot.recovery ?: JSONObject.NULL)
            .put("algorithmKnown", snapshot.algorithmKnown)
            .put("appAutomaticWrite", snapshot.appAutomaticWrite)
            .put("activeSignalEvidence", ACTIVE_SIGNAL_EVIDENCE)
            .put("nativeActive", JSONObject.NULL)
            .put("completionObserved", JSONObject.NULL)
            .put("failureObserved", JSONObject.NULL)
            .put("uiMaySimulateAlgorithm", false)
            .put(
                "preconditions",
                JSONObject()
                    .put("engineRunningKnown", preconditions.engineRunningKnown)
                    .put("fuelStateKnown", preconditions.fuelStateKnown)
                    .put("nativeStatusKnown", preconditions.nativeStatusKnown)
                    .put("calibrationIdentityKnown", preconditions.calibrationIdentityKnown),
            )
            .put(
                "observedContext",
                JSONObject()
                    .put("rpm", frame?.rpm ?: JSONObject.NULL)
                    .put("fuel", frame?.fuel?.name ?: JSONObject.NULL)
                    .put("telemetryFreshness", frame?.freshness?.name ?: "UNKNOWN")
                    .put("nativeFlag13Raw", monitorStatus.opt("nativeFlag13") ?: JSONObject.NULL),
            )
    }
}
