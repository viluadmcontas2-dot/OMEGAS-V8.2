package com.omegas.prohub.autocal

/**
 * Cadência pura do espelho operacional AutoCal.
 *
 * Não possui thread, timer, I/O ou autoridade serial. O serviço decide quando
 * chamar o monitor; este planner apenas informa quais famílias estão vencidas.
 * Um snapshot completo ancora ambas as famílias.
 */
class NativeAutoCalRefreshPlanner {
    data class Due(
        val acquisition: Boolean,
        val reference: Boolean,
    )

    private var lastAcquisitionAtElapsedMs = 0L
    private var lastReferenceAtElapsedMs = 0L

    @Synchronized
    fun reset() {
        lastAcquisitionAtElapsedMs = 0L
        lastReferenceAtElapsedMs = 0L
    }

    @Synchronized
    fun markFullSnapshot(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
        lastReferenceAtElapsedMs = observedAtElapsedMs
    }

    @Synchronized
    fun markAcquisition(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
    }

    @Synchronized
    fun markReference(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastReferenceAtElapsedMs = observedAtElapsedMs
    }

    @Synchronized
    fun due(nowElapsedMs: Long): Due {
        if (nowElapsedMs <= 0L || lastAcquisitionAtElapsedMs <= 0L || lastReferenceAtElapsedMs <= 0L) {
            return Due(acquisition = false, reference = false)
        }
        return Due(
            acquisition = nowElapsedMs - lastAcquisitionAtElapsedMs >= ACQUISITION_INTERVAL_MS,
            reference = nowElapsedMs - lastReferenceAtElapsedMs >= REFERENCE_INTERVAL_MS,
        )
    }

    companion object {
        const val ACQUISITION_INTERVAL_MS = 2_000L
        const val REFERENCE_INTERVAL_MS = 4_000L
    }
}
