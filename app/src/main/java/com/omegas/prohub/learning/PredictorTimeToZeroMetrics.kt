package com.omegas.prohub.learning

import kotlin.math.abs

data class PredictorTimeToZeroMetrics(
    val timeToFirstKstarMs: Long?,
    val timeToConfirmedKstarMs: Long?,
    val timeToFirstActionableMapMs: Long?,
    val timeToZeroBandMs: Long?,
    val predictedCellsBeforeDirectObservation: Int,
    val heldoutPredictionError: Double?,
    val correctionsToConverge: Int,
    val abstentionRate: Double,
    val regressionRate: Double,
)

/** Primitive product metrics for Fast-to-Zero; no UI/frame clock owns them. */
class PredictorTimeToZeroTracker(
    private val startedAtMs: Long,
) {
    init {
        require(startedAtMs >= 0L)
    }

    private var firstKstarAtMs: Long? = null
    private var confirmedKstarAtMs: Long? = null
    private var firstActionableMapAtMs: Long? = null
    private var zeroBandAtMs: Long? = null
    private var predictedCellsBeforeDirectObservation = 0
    private var heldoutAbsoluteErrorSum = 0.0
    private var heldoutErrorCount = 0
    private var corrections = 0
    private var decisions = 0
    private var abstentions = 0
    private var revalidations = 0
    private var regressions = 0

    @Synchronized
    fun recordFirstKstar(atMs: Long) {
        firstKstarAtMs = firstKstarAtMs ?: validTime(atMs)
    }

    @Synchronized
    fun recordConfirmedKstar(atMs: Long) {
        confirmedKstarAtMs = confirmedKstarAtMs ?: validTime(atMs)
    }

    @Synchronized
    fun recordFirstActionableMap(atMs: Long) {
        firstActionableMapAtMs = firstActionableMapAtMs ?: validTime(atMs)
    }

    @Synchronized
    fun recordZeroBand(atMs: Long) {
        zeroBandAtMs = zeroBandAtMs ?: validTime(atMs)
    }

    @Synchronized
    fun recordPredictedCellsBeforeDirectObservation(count: Int) {
        require(count >= 0)
        predictedCellsBeforeDirectObservation = saturatingAdd(predictedCellsBeforeDirectObservation, count)
    }

    @Synchronized
    fun recordHeldoutPredictionError(error: Double) {
        require(error.isFinite())
        heldoutAbsoluteErrorSum += abs(error)
        heldoutErrorCount = increment(heldoutErrorCount)
    }

    @Synchronized
    fun recordCorrection() {
        corrections = increment(corrections)
    }

    @Synchronized
    fun recordDecision(abstained: Boolean) {
        decisions = increment(decisions)
        if (abstained) abstentions = increment(abstentions)
    }

    @Synchronized
    fun recordRevalidation(regressed: Boolean) {
        revalidations = increment(revalidations)
        if (regressed) regressions = increment(regressions)
    }

    @Synchronized
    fun snapshot(): PredictorTimeToZeroMetrics = PredictorTimeToZeroMetrics(
        timeToFirstKstarMs = elapsed(firstKstarAtMs),
        timeToConfirmedKstarMs = elapsed(confirmedKstarAtMs),
        timeToFirstActionableMapMs = elapsed(firstActionableMapAtMs),
        timeToZeroBandMs = elapsed(zeroBandAtMs),
        predictedCellsBeforeDirectObservation = predictedCellsBeforeDirectObservation,
        heldoutPredictionError = if (heldoutErrorCount == 0) null else heldoutAbsoluteErrorSum / heldoutErrorCount.toDouble(),
        correctionsToConverge = corrections,
        abstentionRate = if (decisions == 0) 0.0 else abstentions.toDouble() / decisions.toDouble(),
        regressionRate = if (revalidations == 0) 0.0 else regressions.toDouble() / revalidations.toDouble(),
    )

    private fun validTime(atMs: Long): Long {
        require(atMs >= startedAtMs)
        return atMs
    }

    private fun elapsed(atMs: Long?): Long? = atMs?.minus(startedAtMs)

    private fun increment(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

    private fun saturatingAdd(value: Int, add: Int): Int {
        val sum = value.toLong() + add.toLong()
        return sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
