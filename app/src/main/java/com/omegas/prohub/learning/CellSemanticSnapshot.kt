package com.omegas.prohub.learning

import org.json.JSONObject
import kotlin.math.abs

/**
 * Contrato de produto entre a ciência Kotlin e a UI NEXT.
 *
 * Uma célula é uma região física da grade; ela nunca "é" um tempo de injeção.
 * Cada número exposto carrega papel, origem, unidade e confiança para impedir
 * que condição atual, referência equivalente, observação GNV, previsão e K
 * sejam apresentados como se fossem o mesmo objeto.
 */
object CellSemanticProjection {
    enum class EvidenceRole {
        CURRENT_CONDITION,
        GASOLINE_EQUIVALENT_REFERENCE,
        CNG_OBSERVATION,
        INFERENCE,
        CALIBRATION_K,
        OBD_WITNESS,
    }

    enum class EvidenceState {
        OBSERVED,
        CONSOLIDATED,
        REVALIDATING,
        PREDICTED,
        UNKNOWN,
    }

    data class Value(
        val role: EvidenceRole,
        val label: String,
        val value: Double?,
        val unit: String,
        val state: EvidenceState,
        val confidence: Double?,
        val source: String,
        val explanation: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("role", role.name)
            .put("label", label)
            .put("value", value ?: JSONObject.NULL)
            .put("unit", unit)
            .put("state", state.name)
            .put("confidence", confidence?.coerceIn(0.0, 1.0) ?: JSONObject.NULL)
            .put("source", source)
            .put("explanation", explanation)
    }

    data class Input(
        val row: Int,
        val column: Int,
        val currentRpm: Double,
        val currentPetrolMs: Double,
        val currentMapBar: Double,
        val currentFuel: String,
        val gasolineReferenceMs: Double?,
        val gasolineReferenceState: EvidenceState = EvidenceState.UNKNOWN,
        val gasolineReferenceConfidence: Double? = null,
        val observedPetrolOnCngMs: Double?,
        val comparisonQuality: Double? = null,
        val comparisonStage: EvidenceState = EvidenceState.UNKNOWN,
        val comparisonReason: String = "",
        val inferredPetrolMs: Double? = null,
        val inferenceConfidence: Double? = null,
        val currentK: Double? = null,
        val targetK: Double? = null,
        val proposedK: Double? = null,
        val obdTrimPct: Double? = null,
        val obdFresh: Boolean = false,
    )

    fun build(input: Input): JSONObject {
        val current = Value(
            role = EvidenceRole.CURRENT_CONDITION,
            label = "Petrol Inj. agora",
            value = input.currentPetrolMs.takeIf { it.isFinite() && it > 0.0 },
            unit = "ms",
            state = if (input.currentPetrolMs > 0.0) EvidenceState.OBSERVED else EvidenceState.UNKNOWN,
            confidence = null,
            source = "MP48_TELEMETRY_NOW",
            explanation = "Tempo de injeção de gasolina observado neste instante; não é a referência equivalente aprendida.",
        )
        val reference = Value(
            role = EvidenceRole.GASOLINE_EQUIVALENT_REFERENCE,
            label = "Referência gasolina equivalente",
            value = input.gasolineReferenceMs,
            unit = "ms",
            state = input.gasolineReferenceState,
            confidence = input.gasolineReferenceConfidence,
            source = "CONTINUOUS_GASOLINE_REFERENCE_SURFACE",
            explanation = "Estimativa da referência de gasolina para condição física comparável de RPM/MAP/temperatura; não é o valor atual da célula.",
        )
        val cngObserved = Value(
            role = EvidenceRole.CNG_OBSERVATION,
            label = "Petrol Inj. observado no GNV",
            value = input.observedPetrolOnCngMs,
            unit = "ms",
            state = input.comparisonStage,
            confidence = input.comparisonQuality,
            source = "MP48_PETROL_INJECTION_WHILE_CNG",
            explanation = "Tempo que a ECU de gasolina comandou enquanto o motor rodava no GNV. Não é Gas Inj. e não é K.",
        )
        val inference = Value(
            role = EvidenceRole.INFERENCE,
            label = "Valor inferido",
            value = input.inferredPetrolMs,
            unit = "ms",
            state = if (input.inferredPetrolMs != null) EvidenceState.PREDICTED else EvidenceState.UNKNOWN,
            confidence = input.inferenceConfidence,
            source = "PREDICTOR",
            explanation = "Estimativa derivada; nunca conta como observação e nunca aumenta a própria confiança.",
        )

        val comparable = input.gasolineReferenceMs != null && input.observedPetrolOnCngMs != null &&
            input.gasolineReferenceMs > 0.05 && input.gasolineReferenceMs.isFinite() && input.observedPetrolOnCngMs.isFinite()
        val differenceMs = if (comparable) input.observedPetrolOnCngMs!! - input.gasolineReferenceMs!! else null
        val differencePct = if (comparable) differenceMs!! / input.gasolineReferenceMs!! * 100.0 else null
        val direction = when {
            differenceMs == null || differencePct == null -> "UNKNOWN"
            abs(differenceMs) <= LearningToleranceSettings.current.equivalenceDeadbandMs ||
                abs(differencePct) <= LearningToleranceSettings.current.equivalenceDeadbandPercent -> "EQUIVALENT"
            differenceMs > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }

        return JSONObject()
            .put("schema", "omegas-next-cell-semantics-v1")
            .put("cell", JSONObject()
                .put("row", input.row)
                .put("column", input.column)
                .put("meaning", "PHYSICAL_REGION_RPM_X_PETROL_MS")
                .put("isMeasurement", false))
            .put("currentCondition", JSONObject()
                .put("rpm", input.currentRpm)
                .put("mapBar", input.currentMapBar)
                .put("fuel", input.currentFuel)
                .put("petrolInjection", current.toJson()))
            .put("gasolineEquivalentReference", reference.toJson())
            .put("cngObservation", cngObserved.toJson())
            .put("inference", inference.toJson())
            .put("comparison", JSONObject()
                .put("comparable", comparable)
                .put("differenceMs", differenceMs ?: JSONObject.NULL)
                .put("differencePct", differencePct ?: JSONObject.NULL)
                .put("direction", direction)
                .put("quality", input.comparisonQuality ?: JSONObject.NULL)
                .put("reason", input.comparisonReason)
                .put("rule", "CNG_PETROL_OBSERVATION_MINUS_EQUIVALENT_GASOLINE_REFERENCE"))
            .put("calibration", JSONObject()
                .put("role", EvidenceRole.CALIBRATION_K.name)
                .put("currentK", input.currentK ?: JSONObject.NULL)
                .put("targetK", input.targetK ?: JSONObject.NULL)
                .put("proposedK", input.proposedK ?: JSONObject.NULL)
                .put("unit", "K_FACTOR")
                .put("automaticWrite", false)
                .put("humanConfirmationRequired", true))
            .put("obdWitness", JSONObject()
                .put("role", EvidenceRole.OBD_WITNESS.name)
                .put("trimPct", if (input.obdFresh) input.obdTrimPct ?: JSONObject.NULL else JSONObject.NULL)
                .put("fresh", input.obdFresh)
                .put("observationalOnly", true))
    }
}
