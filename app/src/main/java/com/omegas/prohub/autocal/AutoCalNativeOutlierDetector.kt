package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detecta bolinhas NATIVAS do AutoCal que contradizem de forma robusta a curva aprendida pela maioria.
 *
 * Não usa um corte percentual isolado. A decisão exige:
 *  1. ponto maduro pela própria ECU;
 *  2. rejeição pelo mesmo [AutoMatchRefinedEngine.monotoneFit] usado no Refino;
 *  3. posição abaixo da interpolação local por uma margem maior que o ruído robusto da vizinhança;
 *  4. repetição em leituras completas independentes;
 *  5. uma única submissão por combustível/ponto/geração para impedir laço de readquisição.
 *
 * A classe só decide. O envio do comando continua no AutoCalNativeActionManager canônico.
 */
class AutoCalNativeOutlierDetector(
    private val confirmationsRequired: Int = 2,
    private val independentGapMs: Long = EvidencePairs.OVERLAP_MS,
) {
    companion object {
        const val MIN_CURVE_POINTS = 5
        /** Duas janelas de casamento de MAP do cérebro: piso físico, não porcentagem da curva. */
        const val MIN_DOWN_GAP_BAR = 0.04
        /** Readquirir só depois que saiu da lenta para não apagar e reaprender a mesma bolinha parado. */
        const val REACQUIRE_MIN_RPM = 1_200.0
    }

    data class Decision(
        val fuel: AutoCalPointDeleteProtocol.Fuel,
        val index: Int,
        val expectedMapBar: Double,
        val actualMapBar: Double,
        val deltaBar: Double,
        val confirmations: Int,
        val usbSessionId: Long,
        val generation: Int,
        val capturedAtMs: Long,
        val reason: String,
    ) {
        val target: AutoCalPointDeleteProtocol.Target
            get() = AutoCalPointDeleteProtocol.Target(fuel, index)

        fun toJson(): JSONObject = JSONObject()
            .put("fuel", fuel.wireName)
            .put("index", index)
            .put("point", index + 1)
            .put("zone", target.zone)
            .put("expectedMapBar", expectedMapBar)
            .put("actualMapBar", actualMapBar)
            .put("deltaBar", deltaBar)
            .put("confirmations", confirmations)
            .put("usbSessionId", usbSessionId)
            .put("generation", generation)
            .put("capturedAtMs", capturedAtMs)
            .put("reason", reason)
    }

    private data class Point(
        val fuel: AutoCalPointDeleteProtocol.Fuel,
        val index: Int,
        val mapBar: Double,
        val timeMs: Double,
        val count: Int,
    )

    private data class Key(
        val usbSessionId: Long,
        val fuel: AutoCalPointDeleteProtocol.Fuel,
        val index: Int,
        val generation: Int,
    )

    private data class Trace(
        var confirmations: Int = 0,
        var lastCapturedAtMs: Long = Long.MIN_VALUE,
    )

    private val traces = LinkedHashMap<Key, Trace>()
    private val submitted = LinkedHashSet<Key>()
    private var usbSessionId: Long = 0L
    private var pending: Decision? = null

    @Synchronized
    fun observe(snapshot: JSONObject): Decision? {
        if (snapshot.optInt("autoCalEnabled", -1) != 1) {
            pending = null
            return null
        }
        val epoch = snapshot.optJSONObject("liveAcquisitionEpoch") ?: return pending
        val session = epoch.optLong("usbSessionId", 0L)
        if (session <= 0L) return pending
        if (session != usbSessionId) {
            traces.clear()
            submitted.clear()
            pending = null
            usbSessionId = session
        }

        val capturedAtMs = snapshot.optLong("capturedAtMs", 0L)
        if (capturedAtMs <= 0L) return pending
        val acquisition = snapshot.optJSONObject("acquisition")
            ?: if (snapshot.has("fields")) AutoCalAcquisition.fromSnapshot(snapshot) else return pending
        val candidates = candidates(acquisition, epoch, session, capturedAtMs)
        val activeKeys = candidates.map(::keyOf).toSet()

        pending?.let { if (keyOf(it) !in activeKeys) pending = null }
        traces.keys.removeAll { it.usbSessionId == session && it !in activeKeys && it !in submitted }

        for (base in candidates.sortedByDescending { it.deltaBar }) {
            val key = keyOf(base)
            if (key in submitted) continue
            val trace = traces.getOrPut(key) { Trace() }
            if (trace.lastCapturedAtMs == Long.MIN_VALUE ||
                capturedAtMs - trace.lastCapturedAtMs >= independentGapMs
            ) {
                trace.confirmations += 1
                trace.lastCapturedAtMs = capturedAtMs
            }
            if (trace.confirmations >= confirmationsRequired) {
                val ready = base.copy(confirmations = trace.confirmations)
                pending = ready
                return ready
            }
        }
        return pending
    }

    @Synchronized
    fun markSubmitted(decision: Decision) {
        submitted += keyOf(decision)
        traces.remove(keyOf(decision))
        if (pending?.let(::keyOf) == keyOf(decision)) pending = null
    }

    @Synchronized
    fun statusJson(): JSONObject = JSONObject()
        .put("automaticPointReacquisition", true)
        .put("pending", pending != null)
        .put("candidate", pending?.toJson() ?: JSONObject.NULL)
        .put("suppressedThisGeneration", submitted.size)

    private fun candidates(
        acquisition: JSONObject,
        epoch: JSONObject,
        session: Long,
        capturedAtMs: Long,
    ): List<Decision> {
        val array = acquisition.optJSONArray("points") ?: JSONArray()
        val points = ArrayList<Point>()
        repeat(array.length()) { i ->
            val p = array.optJSONObject(i) ?: return@repeat
            if (p.optBoolean("previous", false) || !p.optBoolean("draw", false)) return@repeat
            val fuel = when (p.optString("fuel").uppercase()) {
                "GNV", "GAS", "CNG" -> AutoCalPointDeleteProtocol.Fuel.GAS
                "GASOLINA", "PETROL" -> AutoCalPointDeleteProtocol.Fuel.PETROL
                else -> return@repeat
            }
            val index = p.optInt("index", -1)
            val timeMs = p.nullableDouble("timeMs") ?: return@repeat
            val mapBar = p.nullableDouble("mapBar") ?: return@repeat
            val counter = p.nullableInt("counter") ?: return@repeat
            val threshold = p.nullableInt("threshold") ?: return@repeat
            val mature = p.optString("state") == "ZONA_ADQUIRIDA" || (threshold > 0 && counter >= threshold)
            if (!mature || index !in 0 until AutoCalPointDeleteProtocol.POINT_COUNT ||
                timeMs <= 0.0 || mapBar <= 0.0
            ) return@repeat
            points += Point(fuel, index, mapBar, timeMs, counter)
        }

        val out = ArrayList<Decision>()
        points.groupBy { it.fuel }.forEach { (fuel, fuelPoints) ->
            if (fuelPoints.size < MIN_CURVE_POINTS) return@forEach
            val bandPoints = fuelPoints.map { p ->
                AutoMatchRefinedEngine.BandPoint(
                    band = p.index,
                    mapBar = p.mapBar,
                    timeMs = p.timeMs,
                    weight = min(p.count, AutoMatchRefinedEngine.BAND_FULL_COUNT).toDouble() /
                        AutoMatchRefinedEngine.BAND_FULL_COUNT.toDouble(),
                    count = p.count,
                )
            }.sortedBy { it.mapBar }
            val rejected = AutoMatchRefinedEngine.monotoneFit(bandPoints).second.map { it.band }.toSet()
            if (rejected.isEmpty()) return@forEach

            val byTime = fuelPoints.sortedBy { it.timeMs }
            val residualByBand = LinkedHashMap<Int, Double>()
            for (i in 1 until byTime.lastIndex) {
                val before = byTime[i - 1]
                val point = byTime[i]
                val after = byTime[i + 1]
                val span = after.timeMs - before.timeMs
                if (span <= 1e-9) continue
                val ratio = ((point.timeMs - before.timeMs) / span).coerceIn(0.0, 1.0)
                val expectedMap = before.mapBar + (after.mapBar - before.mapBar) * ratio
                residualByBand[point.index] = expectedMap - point.mapBar
            }
            if (residualByBand.isEmpty()) return@forEach
            // O ponto que o Refino já rejeitou não pode inflar a estimativa do próprio ruído.
            // Mede a vizinhança aceita e usa esse piso robusto para decidir se o desvio para baixo é local.
            val acceptedResiduals = residualByBand
                .filterKeys { it !in rejected }
                .values
                .map { abs(it) }
            val robustMapNoise = if (acceptedResiduals.isEmpty()) 0.0 else 1.4826 * median(acceptedResiduals)
            val limit = max(MIN_DOWN_GAP_BAR, AutoMatchRefinedEngine.OUTLIER_MAD_K * robustMapNoise)
            val generation = if (fuel == AutoCalPointDeleteProtocol.Fuel.GAS) {
                epoch.optInt("gasGeneration", 0)
            } else {
                epoch.optInt("petrolGeneration", 0)
            }

            byTime.forEach { point ->
                val delta = residualByBand[point.index] ?: return@forEach
                if (point.index !in rejected || delta < limit) return@forEach
                val expectedMap = point.mapBar + delta
                out += Decision(
                    fuel = fuel,
                    index = point.index,
                    expectedMapBar = expectedMap,
                    actualMapBar = point.mapBar,
                    deltaBar = delta,
                    confirmations = 0,
                    usbSessionId = session,
                    generation = generation,
                    capturedAtMs = capturedAtMs,
                    reason = "REFINO_ROBUST_OUTLIER_ABAIXO_CURVA",
                )
            }
        }
        return out
    }

    private fun keyOf(decision: Decision): Key =
        Key(decision.usbSessionId, decision.fuel, decision.index, decision.generation)

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val m = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[m - 1] + sorted[m]) / 2.0 else sorted[m]
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
