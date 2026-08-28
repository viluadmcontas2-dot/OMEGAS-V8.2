package com.omegas.prohub.adaptive

import com.omegas.prohub.calibration.CalibrationIdentity

/**
 * Identidade do modelo Adaptive separada da identidade física da ECU.
 *
 * O modelo é sempre consumidor de uma CalibrationIdentity KNOWN. Ele não lê a
 * ECU, não cria uma segunda identidade física e fica inválido assim que qualquer
 * componente material ao qual foi treinado/validado divergir.
 */
data class AdaptiveModelBinding(
    val modelSchema: String,
    val modelVersion: String,
    val modelHash: String,
    val domainId: String,
    val calibrationFingerprint: String,
    val geometryFingerprint: String,
    val mapHash: String,
    val curveAxisFingerprint: String,
    val curveFactorsFingerprint: String,
    val calibrationGeneration: Int,
    val boundAtElapsedMs: Long,
) {
    init {
        require(modelSchema.isNotBlank())
        require(modelVersion.isNotBlank())
        require(modelHash.isNotBlank())
        require(domainId.isNotBlank())
        require(calibrationFingerprint.isNotBlank())
        require(geometryFingerprint.isNotBlank())
        require(mapHash.isNotBlank())
        require(curveAxisFingerprint.isNotBlank())
        require(curveFactorsFingerprint.isNotBlank())
        require(calibrationGeneration >= 0)
        require(boundAtElapsedMs >= 0L)
    }

    fun key(): String = listOf(
        modelSchema,
        modelVersion,
        modelHash,
        domainId,
        calibrationFingerprint,
        geometryFingerprint,
        mapHash,
        curveAxisFingerprint,
        curveFactorsFingerprint,
        calibrationGeneration.toString(),
    ).joinToString(":")

    fun validityAgainst(identity: CalibrationIdentity): AdaptiveModelValidity {
        if (!identity.materiallyUsable()) return AdaptiveModelValidity.PHYSICAL_IDENTITY_NOT_USABLE
        if (calibrationFingerprint != identity.functionFingerprint) return AdaptiveModelValidity.CALIBRATION_CHANGED
        if (geometryFingerprint != identity.geometryFingerprint) return AdaptiveModelValidity.GEOMETRY_CHANGED
        if (mapHash != identity.mapHash) return AdaptiveModelValidity.MAP_CHANGED
        if (curveAxisFingerprint != identity.curveAxisFingerprint) return AdaptiveModelValidity.CURVE_AXIS_CHANGED
        if (curveFactorsFingerprint != identity.curveFactorsFingerprint) return AdaptiveModelValidity.CURVE_FACTORS_CHANGED
        if (calibrationGeneration != identity.generation) return AdaptiveModelValidity.GENERATION_CHANGED
        return AdaptiveModelValidity.CURRENT
    }

    fun isCurrentFor(identity: CalibrationIdentity): Boolean =
        validityAgainst(identity) == AdaptiveModelValidity.CURRENT

    companion object {
        fun bind(
            identity: CalibrationIdentity,
            modelSchema: String,
            modelVersion: String,
            modelHash: String,
            domainId: String,
            boundAtElapsedMs: Long,
        ): AdaptiveModelBinding {
            require(identity.materiallyUsable()) { "Adaptive exige CalibrationIdentity física KNOWN/current" }
            return AdaptiveModelBinding(
                modelSchema = modelSchema,
                modelVersion = modelVersion,
                modelHash = modelHash,
                domainId = domainId,
                calibrationFingerprint = identity.functionFingerprint,
                geometryFingerprint = identity.geometryFingerprint,
                mapHash = identity.mapHash,
                curveAxisFingerprint = identity.curveAxisFingerprint,
                curveFactorsFingerprint = identity.curveFactorsFingerprint,
                calibrationGeneration = identity.generation,
                boundAtElapsedMs = boundAtElapsedMs,
            )
        }
    }
}

enum class AdaptiveModelValidity {
    CURRENT,
    PHYSICAL_IDENTITY_NOT_USABLE,
    CALIBRATION_CHANGED,
    GEOMETRY_CHANGED,
    MAP_CHANGED,
    CURVE_AXIS_CHANGED,
    CURVE_FACTORS_CHANGED,
    GENERATION_CHANGED,
}
