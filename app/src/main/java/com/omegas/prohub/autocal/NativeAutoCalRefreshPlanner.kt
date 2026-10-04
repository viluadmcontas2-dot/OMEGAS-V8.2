package com.omegas.prohub.autocal

/**
 * Cadência pura do espelho operacional AutoCal.
 *
 * Não possui thread, timer, I/O ou autoridade serial. O serviço decide quando
 * chamar o monitor; este planner apenas informa quais famílias estão vencidas.
 * Um snapshot completo ancora ambas as famílias. Leitura de grupo que falha recua
 * de forma exponencial (com teto): uma ECU/cabo ruim não é reperguntada a cada tick.
 */
class NativeAutoCalRefreshPlanner {
    data class Due(
        val acquisition: Boolean,
        val reference: Boolean,
    )

    private var lastAcquisitionAtElapsedMs = 0L
    private var lastReferenceAtElapsedMs = 0L
    private var acquisitionFailures = 0
    private var referenceFailures = 0

    @Synchronized
    fun reset() {
        lastAcquisitionAtElapsedMs = 0L
        lastReferenceAtElapsedMs = 0L
        acquisitionFailures = 0
        referenceFailures = 0
    }

    @Synchronized
    fun markFullSnapshot(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
        lastReferenceAtElapsedMs = observedAtElapsedMs
        acquisitionFailures = 0
        referenceFailures = 0
    }

    @Synchronized
    fun markAcquisition(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
        acquisitionFailures = 0
    }

    @Synchronized
    fun markReference(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastReferenceAtElapsedMs = observedAtElapsedMs
        referenceFailures = 0
    }

    /** A leitura agrupada de aquisição falhou: próxima tentativa só depois do recuo (2 s, 4 s, 8 s, … até o teto de 30 s). */
    @Synchronized
    fun markAcquisitionFailure(attemptedAtElapsedMs: Long) {
        require(attemptedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = attemptedAtElapsedMs
        acquisitionFailures = (acquisitionFailures + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
    }

    /** A leitura agrupada de referência falhou: próxima tentativa só depois do recuo (8 s, 16 s, … até o teto de 30 s). */
    @Synchronized
    fun markReferenceFailure(attemptedAtElapsedMs: Long) {
        require(attemptedAtElapsedMs > 0L)
        lastReferenceAtElapsedMs = attemptedAtElapsedMs
        referenceFailures = (referenceFailures + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
    }

    @Synchronized
    fun due(nowElapsedMs: Long): Due {
        if (nowElapsedMs <= 0L || lastAcquisitionAtElapsedMs <= 0L || lastReferenceAtElapsedMs <= 0L) {
            return Due(acquisition = false, reference = false)
        }
        return Due(
            acquisition = nowElapsedMs - lastAcquisitionAtElapsedMs >= interval(ACQUISITION_INTERVAL_MS, acquisitionFailures),
            reference = nowElapsedMs - lastReferenceAtElapsedMs >= interval(REFERENCE_INTERVAL_MS, referenceFailures),
        )
    }

    private fun interval(base: Long, failures: Int): Long =
        if (failures <= 0) base else (base shl failures).coerceAtMost(BACKOFF_CAP_MS)

    companion object {
        const val ACQUISITION_INTERVAL_MS = 1_000L
        const val REFERENCE_INTERVAL_MS = 4_000L
        const val BACKOFF_CAP_MS = 30_000L
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
