package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Fecha, de forma somente-leitura, o bracket causal de um AutoMatch nativo:
 * contador N -> N+Δ e MUL_ACT estável imediatamente antes/depois da mesma época.
 *
 * Não tenta reproduzir a fórmula interna da ECU. O objetivo é registrar o que
 * a ECU realmente fez e falhar fechado quando o "antes" não pertence ao
 * contador anterior observado.
 */
object NativeAutoMatchEvidenceBracket {
    private const val POINT_COUNT = 30
    private const val Q14_SCALE = 16384.0

    enum class State {
        FACTOR_CHANGE_CONFIRMED,
        NO_FACTOR_CHANGE_OBSERVED,
        INCONCLUSIVE,
    }

    data class StableVector(
        val sessionId: Long,
        val autoMatchCount: Int,
        val capturedAtElapsedMs: Long,
        val rawValues: IntArray,
        val rawPayloadHex: String = "",
    ) {
        init {
            require(sessionId > 0L)
            require(autoMatchCount >= 0)
            require(capturedAtElapsedMs >= 0L)
            require(rawValues.size == POINT_COUNT)
        }
    }

    data class PointDelta(
        val index: Int,
        val beforeRaw: Int,
        val afterRaw: Int,
        val beforeFactor: Double,
        val afterFactor: Double,
        val deltaRaw: Int,
        val deltaFactor: Double,
        val deltaPercentFromBefore: Double?,
    )

    data class Result(
        val state: State,
        val reason: String,
        val sessionId: Long,
        val beforeCount: Int,
        val afterCount: Int,
        val counterDelta: Int,
        val beforeCapturedAtElapsedMs: Long?,
        val eventObservedAtElapsedMs: Long,
        val afterCapturedAtElapsedMs: Long?,
        val beforeRaw: IntArray?,
        val afterRaw: IntArray?,
        val pointDeltas: List<PointDelta>,
        val beforePayloadHex: String?,
        val afterPayloadHex: String?,
    ) {
        val changedPointCount: Int get() = pointDeltas.size
        val physicalChangeKnown: Boolean
            get() = state != State.INCONCLUSIVE

        fun toJson(): JSONObject = JSONObject()
            .put("state", state.name)
            .put("reason", reason)
            .put("sessionId", sessionId)
            .put("beforeCount", beforeCount)
            .put("afterCount", afterCount)
            .put("counterDelta", counterDelta)
            .put("beforeCapturedAtElapsedMs", beforeCapturedAtElapsedMs ?: JSONObject.NULL)
            .put("eventObservedAtElapsedMs", eventObservedAtElapsedMs)
            .put("afterCapturedAtElapsedMs", afterCapturedAtElapsedMs ?: JSONObject.NULL)
            .put("changedPointCount", changedPointCount)
            .put("physicalChangeKnown", physicalChangeKnown)
            .put("beforeRaw", beforeRaw?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("afterRaw", afterRaw?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("beforePayloadHex", beforePayloadHex ?: JSONObject.NULL)
            .put("afterPayloadHex", afterPayloadHex ?: JSONObject.NULL)
            .put("pointDeltas", JSONArray(pointDeltas.map { it.toJson() }))
            .put("appWritePerformed", false)
            .put("appAutomaticWrite", false)
            .put("nativeFirmwareFormulaInferred", false)
    }

    fun stable(
        sessionId: Long,
        autoMatchCount: Int,
        capturedAtElapsedMs: Long,
        rawValues: IntArray?,
        rawPayloadHex: String = "",
    ): StableVector? {
        if (sessionId <= 0L || autoMatchCount < 0 || capturedAtElapsedMs < 0L) return null
        if (rawValues == null || rawValues.size != POINT_COUNT) return null
        return StableVector(
            sessionId = sessionId,
            autoMatchCount = autoMatchCount,
            capturedAtElapsedMs = capturedAtElapsedMs,
            rawValues = rawValues.copyOf(),
            rawPayloadHex = rawPayloadHex,
        )
    }

    fun evaluate(
        event: NativeAutoMatchCounterTracker.Event,
        before: StableVector?,
        afterSessionId: Long,
        afterAutoMatchCount: Int,
        afterCapturedAtElapsedMs: Long,
        afterRaw: IntArray?,
        afterPayloadHex: String = "",
    ): Result {
        fun inconclusive(reason: String): Result = Result(
            state = State.INCONCLUSIVE,
            reason = reason,
            sessionId = event.sessionId,
            beforeCount = event.beforeCount,
            afterCount = event.afterCount,
            counterDelta = event.delta,
            beforeCapturedAtElapsedMs = before?.capturedAtElapsedMs,
            eventObservedAtElapsedMs = event.observedAtElapsedMs,
            afterCapturedAtElapsedMs = afterCapturedAtElapsedMs.takeIf { it >= 0L },
            beforeRaw = before?.rawValues?.copyOf(),
            afterRaw = afterRaw?.copyOf(),
            pointDeltas = emptyList(),
            beforePayloadHex = before?.rawPayloadHex?.takeIf(String::isNotBlank),
            afterPayloadHex = afterPayloadHex.takeIf(String::isNotBlank),
        )

        if (before == null) return inconclusive("NO_STABLE_BEFORE_VECTOR")
        if (event.sessionId != before.sessionId || event.sessionId != afterSessionId) {
            return inconclusive("SESSION_MISMATCH")
        }
        if (before.autoMatchCount != event.beforeCount) {
            return inconclusive("BEFORE_COUNT_MISMATCH")
        }
        if (afterAutoMatchCount != event.afterCount) {
            return inconclusive("AFTER_COUNT_MISMATCH")
        }
        if (before.capturedAtElapsedMs > event.observedAtElapsedMs) {
            return inconclusive("BEFORE_CAPTURE_AFTER_EVENT")
        }
        if (afterCapturedAtElapsedMs < event.observedAtElapsedMs) {
            return inconclusive("AFTER_CAPTURE_BEFORE_EVENT")
        }
        if (afterRaw == null || afterRaw.size != POINT_COUNT || before.rawValues.size != POINT_COUNT) {
            return inconclusive("MUL_ACT_SHAPE_INVALID")
        }

        val deltas = buildList {
            repeat(POINT_COUNT) { index ->
                val oldRaw = before.rawValues[index]
                val newRaw = afterRaw[index]
                if (oldRaw == newRaw) return@repeat
                val oldFactor = oldRaw / Q14_SCALE
                val newFactor = newRaw / Q14_SCALE
                add(
                    PointDelta(
                        index = index,
                        beforeRaw = oldRaw,
                        afterRaw = newRaw,
                        beforeFactor = oldFactor,
                        afterFactor = newFactor,
                        deltaRaw = newRaw - oldRaw,
                        deltaFactor = newFactor - oldFactor,
                        deltaPercentFromBefore = oldRaw.takeIf { it != 0 }?.let {
                            ((newRaw - oldRaw) / oldRaw.toDouble()) * 100.0
                        },
                    ),
                )
            }
        }

        return Result(
            state = if (deltas.isEmpty()) State.NO_FACTOR_CHANGE_OBSERVED else State.FACTOR_CHANGE_CONFIRMED,
            reason = if (deltas.isEmpty()) "COUNTER_ADVANCED_MUL_ACT_UNCHANGED" else "COUNTER_ADVANCED_MUL_ACT_CHANGED",
            sessionId = event.sessionId,
            beforeCount = event.beforeCount,
            afterCount = event.afterCount,
            counterDelta = event.delta,
            beforeCapturedAtElapsedMs = before.capturedAtElapsedMs,
            eventObservedAtElapsedMs = event.observedAtElapsedMs,
            afterCapturedAtElapsedMs = afterCapturedAtElapsedMs,
            beforeRaw = before.rawValues.copyOf(),
            afterRaw = afterRaw.copyOf(),
            pointDeltas = deltas,
            beforePayloadHex = before.rawPayloadHex.takeIf(String::isNotBlank),
            afterPayloadHex = afterPayloadHex.takeIf(String::isNotBlank),
        )
    }

    private fun PointDelta.toJson(): JSONObject = JSONObject()
        .put("index", index)
        .put("point", index + 1)
        .put("beforeRaw", beforeRaw)
        .put("afterRaw", afterRaw)
        .put("beforeFactor", beforeFactor)
        .put("afterFactor", afterFactor)
        .put("deltaRaw", deltaRaw)
        .put("deltaFactor", deltaFactor)
        .put("deltaPercentFromBefore", deltaPercentFromBefore ?: JSONObject.NULL)
        .put("absoluteDeltaFactor", abs(deltaFactor))
}
