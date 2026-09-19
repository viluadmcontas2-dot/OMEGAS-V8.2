package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.json.JSONObject
import kotlin.math.abs

/**
 * Observador rápido e causal do erro GNV.
 *
 * Opera no fluxo bruto antes da coalescência científica. Nunca escreve na ECU,
 * nunca cria uma comparação persistida e nunca exige comutação PETROL<->CNG.
 *
 * Política promovida pelo torneio AgentRed #736/#754/#765:
 * - memória limitada aos 300 frames GNV anteriores;
 * - vizinhança local de ±80 RPM e ±0,01 bar;
 * - mediana dos erros anteriores, portanto sem usar o frame atual para prevê-lo.
 */
internal class FastCngObserver {
    companion object {
        const val MAX_HISTORY = 300
        const val RPM_RADIUS = 80.0
        const val MAP_RADIUS_BAR = 0.01
        const val MIN_PETROL_MS = 0.70
    }

    private data class Observation(
        val rpm: Double,
        val mapBar: Double,
        val errorPercent: Double,
    )

    data class Snapshot(
        val state: String,
        val rpm: Double? = null,
        val mapBar: Double? = null,
        val petrolObservedMs: Double? = null,
        val petrolTargetMs: Double? = null,
        val rawErrorPercent: Double? = null,
        val localEstimatePercent: Double? = null,
        val localResidualPercent: Double? = null,
        val localSupport: Int = 0,
        val historySize: Int = 0,
        val observations: Long = 0L,
        val predictions: Long = 0L,
        val referenceQuality: Double? = null,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("state", state)
            .put("source", "RAW_CNG_FRAME")
            .put("reference", "LEARNED_PETROL_SURFACE")
            .put("policy", "PRIOR_300_LOCAL_MEDIAN")
            .put("rpmRadius", RPM_RADIUS)
            .put("mapRadiusBar", MAP_RADIUS_BAR)
            .put("maxHistory", MAX_HISTORY)
            .put("rpm", rpm ?: JSONObject.NULL)
            .put("mapBar", mapBar ?: JSONObject.NULL)
            .put("petrolObservedMs", petrolObservedMs ?: JSONObject.NULL)
            .put("petrolTargetMs", petrolTargetMs ?: JSONObject.NULL)
            .put("rawErrorPercent", rawErrorPercent ?: JSONObject.NULL)
            .put("localEstimatePercent", localEstimatePercent ?: JSONObject.NULL)
            .put("localResidualPercent", localResidualPercent ?: JSONObject.NULL)
            .put("localSupport", localSupport)
            .put("historySize", historySize)
            .put("observations", observations)
            .put("predictions", predictions)
            .put(
                "predictionCoveragePercent",
                if (observations > 0L) predictions.toDouble() / observations.toDouble() * 100.0 else 0.0,
            )
            .put("referenceQuality", referenceQuality ?: JSONObject.NULL)
            .put("runtimeFuelSwitchingRequired", false)
            .put("automaticWrite", false)
            .put("humanConfirmationRequired", true)

        companion object {
            fun empty() = Snapshot(state = "WAITING_FOR_CNG")
        }
    }

    private val history = ArrayDeque<Observation>()
    private var observations = 0L
    private var predictions = 0L
    private var last = Snapshot.empty()

    @Synchronized
    fun reset() {
        history.clear()
        observations = 0L
        predictions = 0L
        last = Snapshot.empty()
    }

    @Synchronized
    fun observe(
        telemetry: Mp48Telemetry,
        reference: FastPetrolReferenceEstimate?,
    ): Snapshot {
        if (telemetry.fuel != Mp48Fuel.CNG || !telemetry.plausible || telemetry.petrolMs < MIN_PETROL_MS) {
            last = last.copy(
                state = if (telemetry.fuel == Mp48Fuel.CNG) "INVALID_CNG_FRAME" else "WAITING_FOR_CNG",
                historySize = history.size,
                observations = observations,
                predictions = predictions,
            )
            return last
        }
        if (reference == null || reference.petrolTargetMs <= 0.05) {
            last = Snapshot(
                state = "WAITING_FOR_PETROL_REFERENCE",
                rpm = telemetry.rpm.toDouble(),
                mapBar = telemetry.mapBar,
                petrolObservedMs = telemetry.petrolMs,
                historySize = history.size,
                observations = observations,
                predictions = predictions,
            )
            return last
        }

        val rawError = (telemetry.petrolMs - reference.petrolTargetMs) / reference.petrolTargetMs * 100.0
        if (!rawError.isFinite()) {
            last = Snapshot(
                state = "INVALID_ERROR",
                rpm = telemetry.rpm.toDouble(),
                mapBar = telemetry.mapBar,
                petrolObservedMs = telemetry.petrolMs,
                petrolTargetMs = reference.petrolTargetMs,
                historySize = history.size,
                observations = observations,
                predictions = predictions,
                referenceQuality = reference.quality,
            )
            return last
        }

        val local = history.asSequence()
            .filter {
                abs(it.rpm - telemetry.rpm.toDouble()) <= RPM_RADIUS &&
                    abs(it.mapBar - telemetry.mapBar) <= MAP_RADIUS_BAR
            }
            .map { it.errorPercent }
            .toList()
        val estimate = local.medianOrNull()

        observations += 1L
        if (estimate != null) predictions += 1L

        // O frame atual só entra depois da estimativa: previsão estritamente causal.
        history.addLast(
            Observation(
                rpm = telemetry.rpm.toDouble(),
                mapBar = telemetry.mapBar,
                errorPercent = rawError,
            ),
        )
        while (history.size > MAX_HISTORY) history.removeFirst()

        last = Snapshot(
            state = if (estimate == null) "LEARNING_LOCAL_NEIGHBORHOOD" else "LOCAL_ESTIMATE_READY",
            rpm = telemetry.rpm.toDouble(),
            mapBar = telemetry.mapBar,
            petrolObservedMs = telemetry.petrolMs,
            petrolTargetMs = reference.petrolTargetMs,
            rawErrorPercent = rawError,
            localEstimatePercent = estimate,
            localResidualPercent = estimate?.let { rawError - it },
            localSupport = local.size,
            historySize = history.size,
            observations = observations,
            predictions = predictions,
            referenceQuality = reference.quality,
        )
        return last
    }

    @Synchronized
    fun snapshot(): Snapshot = last

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val ordered = sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) {
            ordered[middle]
        } else {
            (ordered[middle - 1] + ordered[middle]) / 2.0
        }
    }
}

internal data class FastPetrolReferenceEstimate(
    val petrolTargetMs: Double,
    val quality: Double,
    val stage: String,
)
