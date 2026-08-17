package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7

data class V7CalibrationIdentityProjection(
    val functionFingerprint: String,
    val geometryFingerprint: String,
    val mapHash: String,
    val curveAxisFingerprint: String,
    val curveFactorsFingerprint: String,
    val usbSessionId: Long,
    val generation: Int,
    val revisionProjection: CalibrationRevisionV7,
    val provenance: CalibrationProvenance,
    val completeness: CalibrationCompleteness,
    val freshness: CalibrationFreshness,
)

/**
 * Orquestrador NEXT para consumidores V7.
 *
 * Não lê Mapa/Curva nem reconstrói identidade física. Recebe somente a
 * CalibrationIdentity já validada e mantém CalibrationRevisionV7 como projeção.
 */
class V7CalibrationIdentityCoordinator {
    private val lock = Any()
    private var currentIdentity: CalibrationIdentity? = null
    private var currentRevision = CalibrationRevisionV7(0, 0)

    fun accept(identity: CalibrationIdentity, revisionProjection: CalibrationRevisionV7) = synchronized(lock) {
        require(identity.materiallyUsable()) { "CalibrationIdentity não está pronta para uso material" }
        currentIdentity = identity
        currentRevision = revisionProjection
    }

    fun matchesMaterial(expectedFingerprint: String): Boolean = synchronized(lock) {
        val current = currentIdentity ?: return@synchronized false
        current.materiallyUsable() && current.functionFingerprint == expectedFingerprint
    }

    fun requireMaterial(expectedFingerprint: String): CalibrationIdentity = synchronized(lock) {
        val current = currentIdentity ?: error("CalibrationIdentity ainda não foi reconciliada")
        check(current.materiallyUsable()) { "CalibrationIdentity não está pronta para uso material" }
        check(current.functionFingerprint == expectedFingerprint) { "A calibração física mudou; revalide antes de continuar" }
        current
    }

    fun projection(): V7CalibrationIdentityProjection = synchronized(lock) {
        val current = currentIdentity ?: error("CalibrationIdentity ainda não foi reconciliada")
        V7CalibrationIdentityProjection(
            functionFingerprint = current.functionFingerprint,
            geometryFingerprint = current.geometryFingerprint,
            mapHash = current.mapHash,
            curveAxisFingerprint = current.curveAxisFingerprint,
            curveFactorsFingerprint = current.curveFactorsFingerprint,
            usbSessionId = current.usbSessionId,
            generation = current.generation,
            revisionProjection = currentRevision,
            provenance = current.provenance,
            completeness = current.completeness,
            freshness = current.freshness,
        )
    }

    fun clear() = synchronized(lock) {
        currentIdentity = null
        currentRevision = CalibrationRevisionV7(0, 0)
    }
}
