package com.omegas.prohub.model

/**
 * Contratos arquiteturais canônicos da V8.2.
 *
 * Estes tipos não substituem os motores existentes: eles definem as fronteiras
 * que os próximos blocos devem usar ao projetar estado e eventos para novas
 * superfícies. Nenhum contrato desta camada toca USB, WebView ou executa escrita.
 */
enum class AppStateDomain {
    SCIENTIFIC,
    OPERATIONAL,
    VISUAL,
}

enum class SnapshotAvailability {
    AVAILABLE,
    STALE,
    UNAVAILABLE,
}

data class Freshness(
    val producedAtMs: Long,
    val observedAtMs: Long,
    val maximumAgeMs: Long,
) {
    init {
        require(producedAtMs >= 0L)
        require(observedAtMs >= 0L)
        require(maximumAgeMs >= 0L)
    }

    val ageMs: Long get() = (observedAtMs - producedAtMs).coerceAtLeast(0L)
    val availability: SnapshotAvailability
        get() = if (producedAtMs == 0L) SnapshotAvailability.UNAVAILABLE
        else if (ageMs > maximumAgeMs) SnapshotAvailability.STALE
        else SnapshotAvailability.AVAILABLE
}

@JvmInline
value class ScientificRevision(val value: Long) {
    init { require(value >= 0L) }
}

sealed interface ProductEvent {
    val occurredAtMs: Long
    val scientificRevision: ScientificRevision?

    data class Telemetry(
        override val occurredAtMs: Long,
        override val scientificRevision: ScientificRevision? = null,
    ) : ProductEvent

    data class LearningEvidenceChanged(
        override val occurredAtMs: Long,
        override val scientificRevision: ScientificRevision,
    ) : ProductEvent

    data class AutoCalChanged(
        override val occurredAtMs: Long,
        override val scientificRevision: ScientificRevision,
    ) : ProductEvent

    data class PredictorChanged(
        override val occurredAtMs: Long,
        override val scientificRevision: ScientificRevision,
    ) : ProductEvent

    data class CalibrationChanged(
        override val occurredAtMs: Long,
        override val scientificRevision: ScientificRevision,
    ) : ProductEvent
}

enum class EcuMutationKind {
    MAP_K,
    CURVE_K,
    AUTOCAL,
    OTHER,
}

data class HumanIntent(
    val mutation: EcuMutationKind,
    val requestedAtMs: Long,
    val operatorConfirmed: Boolean,
    val source: String,
) {
    init {
        require(requestedAtMs >= 0L)
        require(source.isNotBlank())
    }

    fun requireConfirmed(): HumanIntent {
        check(operatorConfirmed) { "ECU mutation requires explicit operator confirmation" }
        return this
    }
}

data class HumanFacingError(
    val code: String,
    val summary: String,
    val technicalDetail: String,
) {
    init {
        require(code.isNotBlank())
        require(summary.isNotBlank())
        require(technicalDetail.isNotBlank())
    }
}

/** Capacidades permitidas à camada visual. Tudo que não está aqui pertence ao nativo. */
object UiBoundaryContract {
    const val MAY_RENDER_STATE = true
    const val MAY_EMIT_HUMAN_INTENT = true
    const val MAY_TOUCH_USB_DIRECTLY = false
    const val MAY_PARSE_MP48_DIRECTLY = false
    const val MAY_WRITE_ECU_DIRECTLY = false
    const val MAY_OWN_SCIENTIFIC_MATH = false
}
