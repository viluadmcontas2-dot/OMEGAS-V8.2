package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.CalibrationWriteResultV7
import com.omegas.v7.runtime.CalibrationWriterV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONArray
import org.json.JSONObject

/**
 * Liga o runtime V7 aos dois writers Android já comprovados no aplicativo.
 *
 * A chamada deve ocorrer fora da main thread. O retorno positivo acontece
 * somente depois que o manager real conclui ACK, readback e publica
 * BATCH_CONFIRMED. BATCH_QUEUED nunca avança a revisão V7.
 */
class ExistingCalibrationWriterV7(
    private val mapWriter: KWriteManager,
    private val curveWriter: KFactorManager,
    private val timeoutMs: Long = 120_000L,
    private val mapMaxStep: Int = KWriteManager.MAX_SAFE_STEP,
    private val mapPauseMs: Int = 0,
) : CalibrationWriterV7 {

    override fun write(
        current: CalibrationStateV7,
        desired: CalibrationStateV7,
        suggestion: LocalSuggestionV7,
    ): CalibrationWriteResultV7 = when (suggestion.target) {
        SuggestionTargetV7.MAP_K -> writeMap(current, desired, suggestion)
        SuggestionTargetV7.CURVE_K -> writeCurve(current, desired, suggestion)
    }

    private fun writeMap(
        current: CalibrationStateV7,
        desired: CalibrationStateV7,
        suggestion: LocalSuggestionV7,
    ): CalibrationWriteResultV7 {
        val cells = JSONArray()
        suggestion.mapChanges.forEach { change ->
            require(current.mapK[change.row][change.column] == change.before)
            require(desired.mapK[change.row][change.column] == change.after)
            cells.put(
                JSONObject()
                    .put("row", change.row)
                    .put("column", change.column)
                    .put("current", change.before)
                    .put("target", change.after),
            )
        }
        val started = mapWriter.startBatchWrite(
            cells = cells,
            maxStep = mapMaxStep,
            pauseMs = mapPauseMs,
            reason = suggestion.rationale,
        )
        if (!started.optBoolean("ok")) {
            return CalibrationWriteResultV7(
                success = false,
                message = started.optString("error", "Mapa K não foi enfileirado"),
            )
        }
        return awaitManager(
            isBusy = mapWriter::isBusy,
            statusJson = mapWriter::statusJson,
            confirmedState = "BATCH_CONFIRMED",
            failureStates = setOf("BATCH_PARTIAL_FAILED", "FAILED", "SAFETY_LOCKED_INSERTION_UNKNOWN"),
            decodeReadBack = { status -> CalibrationWriterReadBackV7.map(desired, status) },
        )
    }

    private fun writeCurve(
        current: CalibrationStateV7,
        desired: CalibrationStateV7,
        suggestion: LocalSuggestionV7,
    ): CalibrationWriteResultV7 {
        val points = JSONArray()
        suggestion.curveChanges.forEach { change ->
            require(current.curveK[change.index] == change.before)
            require(desired.curveK[change.index] == change.after)
            points.put(
                JSONObject()
                    .put("index", change.index)
                    .put("currentRaw", KFactorProtocol.rawFromFactor(change.before))
                    .put("targetRaw", KFactorProtocol.rawFromFactor(change.after)),
            )
        }
        val started = curveWriter.startBatchWrite(points, suggestion.rationale)
        if (!started.optBoolean("ok")) {
            return CalibrationWriteResultV7(
                success = false,
                message = started.optString("error", "Curva K não foi enfileirada"),
            )
        }
        return awaitManager(
            isBusy = curveWriter::isBusy,
            statusJson = curveWriter::statusJson,
            confirmedState = "BATCH_CONFIRMED",
            failureStates = setOf("BATCH_FAILED", "FAILED"),
            decodeReadBack = { status -> CalibrationWriterReadBackV7.curve(desired, status) },
        )
    }

    private fun awaitManager(
        isBusy: () -> Boolean,
        statusJson: () -> String,
        confirmedState: String,
        failureStates: Set<String>,
        decodeReadBack: (JSONObject) -> CalibrationStateV7,
    ): CalibrationWriteResultV7 {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (isBusy()) {
            if (System.nanoTime() >= deadline) {
                return CalibrationWriteResultV7(
                    success = false,
                    message = "Tempo esgotado aguardando confirmação da ECU",
                )
            }
            try {
                Thread.sleep(25L)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return CalibrationWriteResultV7(false, message = "Escrita interrompida")
            }
        }
        val status = try {
            JSONObject(statusJson())
        } catch (error: Exception) {
            return CalibrationWriteResultV7(false, message = "Status do writer inválido: ${error.message}")
        }
        val state = status.optString("state")
        val message = status.optString("message", state)
        return when {
            state == confirmedState -> try {
                CalibrationWriteResultV7(
                    success = true,
                    readBack = decodeReadBack(status),
                    message = message,
                )
            } catch (error: Exception) {
                CalibrationWriteResultV7(
                    success = false,
                    message = "Confirmação sem readback utilizável: ${error.message}",
                )
            }
            state in failureStates -> CalibrationWriteResultV7(false, message = message)
            else -> CalibrationWriteResultV7(
                success = false,
                message = "Writer encerrou em estado inesperado: $state • $message",
            )
        }
    }
}
