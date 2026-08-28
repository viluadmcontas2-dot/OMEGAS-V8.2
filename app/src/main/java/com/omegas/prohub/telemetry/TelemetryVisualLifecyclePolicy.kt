package com.omegas.prohub.telemetry

/**
 * Contrato de entrega visual para telemetria viva.
 *
 * O dado científico não depende desta política. Ela governa somente quanto
 * trabalho visual pode ser produzido quando a superfície consumidora muda de
 * estado ou fica mais lenta que a telemetria nativa.
 */
enum class TelemetryVisualSurfaceMode {
    FOREGROUND,
    SPLIT_SCREEN,
    HIDDEN,
}

enum class TelemetryVisualWorkClass {
    PRIMARY_TELEMETRY,
    SECONDARY_VISUAL,
    CRITICAL_EVENT,
}

object TelemetryVisualLifecyclePolicy {
    const val MAX_ACTIVE_VISUAL_FRAMES = 1
    const val MAX_PENDING_VISUAL_FRAMES = 1
    const val MAX_BUFFERED_VISUAL_FRAMES = MAX_ACTIVE_VISUAL_FRAMES + MAX_PENDING_VISUAL_FRAMES

    /** Learning científico e eventos críticos nunca entram na política de descarte visual. */
    const val SCIENTIFIC_LEARNING_USES_VISUAL_QUEUE = false
    const val CRITICAL_EVENTS_USE_VISUAL_QUEUE = false

    /**
     * Campos operacionais que devem permanecer no caminho visual prioritário.
     * Gráficos, superfícies e Predictor são trabalho secundário.
     */
    val primaryTelemetryFields: Set<String> = setOf(
        "rpm",
        "petrol_ms",
        "fuel",
        "fuel_mode",
    )

    fun shouldRender(workClass: TelemetryVisualWorkClass, mode: TelemetryVisualSurfaceMode): Boolean =
        when (workClass) {
            TelemetryVisualWorkClass.CRITICAL_EVENT -> true
            TelemetryVisualWorkClass.PRIMARY_TELEMETRY -> mode != TelemetryVisualSurfaceMode.HIDDEN
            TelemetryVisualWorkClass.SECONDARY_VISUAL -> mode == TelemetryVisualSurfaceMode.FOREGROUND
        }

    /**
     * Ao ocultar o app, a superfície para de renderizar, mas o Store continua
     * recebendo o estado mais recente. Na volta ao foreground/split-screen a UI
     * consome somente a fotografia atual, sem replay de frames visuais antigos.
     */
    fun retainLatestSnapshotWhileHidden(): Boolean = true

    fun requiresHistoricalReplayOnForeground(): Boolean = false

    fun normalizedAgeMs(updatedAtMs: Long, nowMs: Long): Long =
        if (updatedAtMs <= 0L) -1L else (nowMs - updatedAtMs).coerceAtLeast(0L)
}
