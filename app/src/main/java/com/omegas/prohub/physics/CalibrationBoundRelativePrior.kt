package com.omegas.prohub.physics

import kotlin.math.exp
import kotlin.math.ln

enum class CalibrationDependency {
    MAP,
    CURVE,
}

data class CalibrationDependencyIdentity(
    val mapHash: String,
    val curveFingerprint: String,
) {
    init {
        require(mapHash.isNotBlank())
        require(curveFingerprint.isNotBlank())
    }

    companion object {
        fun from(context: CalibrationPhysicsContext): CalibrationDependencyIdentity =
            CalibrationDependencyIdentity(
                mapHash = context.identity.mapHash,
                curveFingerprint = context.identity.curveFingerprint,
            )
    }
}

data class RelativePriorRebaseResult(
    val available: Boolean,
    val targetFactor: Double?,
    val reason: String,
)

data class CalibrationBoundRelativePrior private constructor(
    val sourceIdentity: CalibrationDependencyIdentity,
    val dependencies: Set<CalibrationDependency>,
    val sourceFactor: Double,
    val deltaStar: Double,
    val provenance: String,
) {
    init {
        require(dependencies.isNotEmpty())
        require(sourceFactor.isFinite() && sourceFactor > 0.0)
        require(deltaStar.isFinite())
        require(provenance.isNotBlank())
    }

    fun rebase(
        currentFactor: Double,
        currentIdentity: CalibrationDependencyIdentity,
    ): RelativePriorRebaseResult {
        if (!currentFactor.isFinite() || currentFactor <= 0.0) {
            return unavailable("CURRENT_FACTOR_INVALID")
        }

        val mapChanged = CalibrationDependency.MAP in dependencies &&
            sourceIdentity.mapHash != currentIdentity.mapHash
        val curveChanged = CalibrationDependency.CURVE in dependencies &&
            sourceIdentity.curveFingerprint != currentIdentity.curveFingerprint
        when {
            mapChanged && curveChanged -> return unavailable("MAP_AND_CURVE_DEPENDENCY_CHANGED")
            mapChanged -> return unavailable("MAP_DEPENDENCY_CHANGED")
            curveChanged -> return unavailable("CURVE_DEPENDENCY_CHANGED")
        }

        val target = currentFactor * exp(deltaStar)
        if (!target.isFinite() || target <= 0.0) return unavailable("REBASED_TARGET_INVALID")
        return RelativePriorRebaseResult(
            available = true,
            targetFactor = target,
            reason = "RELATIVE_PRIOR_REBASED",
        )
    }

    private fun unavailable(reason: String): RelativePriorRebaseResult = RelativePriorRebaseResult(
        available = false,
        targetFactor = null,
        reason = reason,
    )

    companion object {
        fun fromAbsolute(
            sourceIdentity: CalibrationDependencyIdentity,
            dependencies: Set<CalibrationDependency>,
            sourceFactor: Double,
            targetFactor: Double,
            provenance: String,
        ): CalibrationBoundRelativePrior {
            require(sourceFactor.isFinite() && sourceFactor > 0.0)
            require(targetFactor.isFinite() && targetFactor > 0.0)
            return CalibrationBoundRelativePrior(
                sourceIdentity = sourceIdentity,
                dependencies = dependencies,
                sourceFactor = sourceFactor,
                deltaStar = ln(targetFactor / sourceFactor),
                provenance = provenance,
            )
        }
    }
}
