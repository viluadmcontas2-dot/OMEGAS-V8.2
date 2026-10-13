package com.omegas.prohub.autocal

import org.json.JSONObject
import kotlin.math.abs

/**
 * Medidor do "cabo de guerra" da ECU (dono, 2026-10-13): a cada AutoMatch a ECU reescreve a Curva K a partir dos buffers que
 * tinha quando o AutoMatch começou. Aqui só se MEDE (nada é gravado, nada decide a partir disto): por ponto, em que sentido
 * a ECU mexeu a cada AutoMatch e quantas vezes o sentido inverteu. Curva que vai e volta (+, −, +) é a ECU brigando com ela
 * mesma ou com a nossa correção; curva que anda sempre para o mesmo lado está convergindo.
 * Entrada: pares (antes, depois) de AutoMatch já confirmados pelo `NativeAutoMatchEvidenceBracket` (sem escrita do app entre
 * eles). Zera a cada sessão USB.
 */
class NativeKMovementTracker(private val pointCount: Int = 30) {
    data class Summary(
        val events: Int,
        val lastAutoMatchCount: Int?,
        val changedPoints: Int,
        val meanAbsPercent: Double,
        val maxAbsPercent: Double,
        val pointsWithTwoMoves: Int,
        val reversalPoints: Int,
        val reversalShare: Double,
        val oscillating: Boolean,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("events", events)
            .put("lastAutoMatchCount", lastAutoMatchCount ?: JSONObject.NULL)
            .put("changedPoints", changedPoints)
            .put("meanAbsPercent", meanAbsPercent)
            .put("maxAbsPercent", maxAbsPercent)
            .put("pointsWithTwoMoves", pointsWithTwoMoves)
            .put("reversalPoints", reversalPoints)
            .put("reversalShare", reversalShare)
            .put("oscillating", oscillating)
            .put("measurementOnly", true)
    }

    private var events = 0
    private var lastCount: Int? = null
    private var lastChanged = 0
    private var lastMeanAbs = 0.0
    private var lastMaxAbs = 0.0
    private val lastSign = IntArray(pointCount)
    private val moves = IntArray(pointCount)
    private val reversals = IntArray(pointCount)

    @Synchronized fun reset() {
        events = 0; lastCount = null; lastChanged = 0; lastMeanAbs = 0.0; lastMaxAbs = 0.0
        lastSign.fill(0); moves.fill(0); reversals.fill(0)
    }

    /** `before`/`after` = MUL_ACT bruto (Q14) antes e depois de um AutoMatch confirmado. */
    @Synchronized fun observe(autoMatchCount: Int, before: IntArray, after: IntArray): Summary {
        if (before.size == pointCount && after.size == pointCount) {
            var changed = 0
            var sumAbs = 0.0
            var maxAbs = 0.0
            for (i in 0 until pointCount) {
                val d = after[i] - before[i]
                if (d == 0) continue
                changed += 1
                val percent = if (before[i] > 0) abs(d).toDouble() / before[i] * 100.0 else 0.0
                sumAbs += percent
                if (percent > maxAbs) maxAbs = percent
                val sign = if (d > 0) 1 else -1
                if (lastSign[i] != 0 && lastSign[i] != sign) reversals[i] += 1
                lastSign[i] = sign
                moves[i] += 1
            }
            events += 1
            lastCount = autoMatchCount
            lastChanged = changed
            lastMeanAbs = if (changed > 0) sumAbs / changed else 0.0
            lastMaxAbs = maxAbs
        }
        return summary()
    }

    @Synchronized fun summary(): Summary {
        val two = (0 until pointCount).count { moves[it] >= 2 }
        val rev = (0 until pointCount).count { reversals[it] > 0 }
        val share = if (two > 0) rev.toDouble() / two else 0.0
        return Summary(
            events = events,
            lastAutoMatchCount = lastCount,
            changedPoints = lastChanged,
            meanAbsPercent = lastMeanAbs,
            maxAbsPercent = lastMaxAbs,
            pointsWithTwoMoves = two,
            reversalPoints = rev,
            reversalShare = share,
            // "Vai e volta": precisa de pelo menos 3 AutoMatch medidos, 5 pontos com 2+ movimentos e 40% deles invertendo.
            oscillating = events >= 3 && two >= MIN_POINTS && share >= OSCILLATION_SHARE,
        )
    }

    companion object {
        const val MIN_POINTS = 5
        const val OSCILLATION_SHARE = 0.4
    }
}
