package com.omegas.prohub.physics

import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import com.omegas.prohub.learning.ContinuousLearningMath
import org.json.JSONObject

enum class ScientificAuthority {
    OEM_NATIVE,
    CLASSIC_ASSISTED,
    ADAPTIVE_SHADOW,
}

data class PhysicsScientificInput(
    val authority: ScientificAuthority,
    val physicalEvidenceId: String,
    val weight: Double,
) {
    init {
        require(physicalEvidenceId.isNotBlank()) { "physicalEvidenceId is required" }
        require(weight in 0.0..1.0) { "weight must be within 0..1" }
    }

    companion object {
        /**
         * One physical evidence id contributes at most once. When multiple
         * scientific authorities expose the same physical evidence, preserve
         * the highest-order authority rather than multiplying its vote.
         */
        fun deduplicateByPhysicalEvidence(inputs: List<PhysicsScientificInput>): List<PhysicsScientificInput> {
            val priority = mapOf(
                ScientificAuthority.OEM_NATIVE to 3,
                ScientificAuthority.CLASSIC_ASSISTED to 2,
                ScientificAuthority.ADAPTIVE_SHADOW to 1,
            )
            return inputs
                .groupBy { it.physicalEvidenceId }
                .values
                .map { group -> group.maxBy { priority.getValue(it.authority) } }
        }
    }
}

/** Explicitly binds the existing educational interpolation to LOCAL_MODEL. */
fun ContinuousLearningMath.physicsModelAuthority(): PhysicsModelAuthority =
    PhysicsModelContract.BILINEAR_AUTHORITY

/**
 * Exposes the legacy 45–90% correction fraction as operational StepPolicy
 * metadata. It is intentionally not an ideal target and not an ECU plant gain.
 */
fun AssistedCalibrationAdvisor.correctionPolicyMetadata(): JSONObject = JSONObject()
    .put("minimumFraction", 0.45)
    .put("maximumFraction", 0.90)
    .put("magnitudeAuthority", MagnitudeAuthority.POLICY_ONLY.name)
    .put("idealTarget", false)
    .put("role", "STEP_POLICY_BASELINE")
