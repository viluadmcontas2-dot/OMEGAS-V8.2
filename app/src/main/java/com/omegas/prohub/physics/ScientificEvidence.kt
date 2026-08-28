package com.omegas.prohub.physics

enum class ScientificAuthority {
    OEM_NATIVE,
    CLASSIC_ASSISTED,
    ADAPTIVE_SHADOW,
}

enum class ScientificEvidenceRole {
    OBSERVATION,
    PREDICTION,
}

/**
 * Raw producer exposure of one scientific claim. OBSERVATION requires a real
 * physical evidence id because this layer is where duplicate physical evidence
 * is detected before any statistical aggregation.
 */
data class PhysicsScientificInput(
    val authority: ScientificAuthority,
    val role: ScientificEvidenceRole,
    val evidenceId: String,
    val physicalEvidenceId: String?,
    val weight: Double,
    val provenance: String,
) {
    init {
        require(evidenceId.isNotBlank()) { "evidenceId is required" }
        require(provenance.isNotBlank()) { "provenance is required" }
        require(weight.isFinite() && weight in 0.0..1.0) { "weight must be finite and within 0..1" }
        if (role == ScientificEvidenceRole.OBSERVATION) {
            require(!physicalEvidenceId.isNullOrBlank()) { "OBSERVATION requires physicalEvidenceId" }
        }
    }

    companion object {
        fun resolve(inputs: List<PhysicsScientificInput>): ScientificEvidenceResolution {
            val accepted = mutableListOf<ResolvedScientificEvidence>()
            val conflicts = mutableListOf<ScientificEvidenceConflict>()
            val consumed = mutableSetOf<PhysicsScientificInput>()

            inputs
                .filter { it.physicalEvidenceId != null }
                .groupBy { it.physicalEvidenceId!! }
                .toSortedMap()
                .values
                .forEach { group ->
                    val roles = group.map { it.role }.toSet()
                    if (roles.size > 1) {
                        conflicts += group.toConflict("SCIENTIFIC_ROLE_CONFLICT")
                        consumed.addAll(group)
                        return@forEach
                    }

                    if (roles.single() == ScientificEvidenceRole.OBSERVATION) {
                        val weights = group.map { it.weight }.toSet()
                        if (weights.size > 1) {
                            conflicts += group.toConflict("SCIENTIFIC_WEIGHT_CONFLICT")
                        } else {
                            accepted += group.toResolved(weights.single())
                        }
                        consumed.addAll(group)
                    }
                }

            inputs.filterNot { it in consumed }.forEach { input ->
                accepted += ResolvedScientificEvidence(
                    authorities = setOf(input.authority),
                    role = input.role,
                    evidenceIds = setOf(input.evidenceId),
                    physicalEvidenceId = input.physicalEvidenceId,
                    effectiveWeight = input.weight,
                    provenance = setOf(input.provenance),
                )
            }

            return ScientificEvidenceResolution(
                accepted = accepted,
                conflicts = conflicts,
            )
        }
    }
}

/** One resolved raw physical observation (or one raw prediction claim). */
data class ResolvedScientificEvidence(
    val authorities: Set<ScientificAuthority>,
    val role: ScientificEvidenceRole,
    val evidenceIds: Set<String>,
    val physicalEvidenceId: String?,
    val effectiveWeight: Double,
    val provenance: Set<String>,
) {
    init {
        require(authorities.isNotEmpty()) { "at least one scientific authority is required" }
        require(evidenceIds.isNotEmpty() && evidenceIds.none { it.isBlank() }) { "evidenceIds must be non-blank" }
        require(provenance.isNotEmpty() && provenance.none { it.isBlank() }) { "provenance must be non-blank" }
        require(effectiveWeight.isFinite() && effectiveWeight in 0.0..1.0) {
            "effectiveWeight must be finite and within 0..1"
        }
        if (role == ScientificEvidenceRole.OBSERVATION) {
            require(!physicalEvidenceId.isNullOrBlank()) { "resolved OBSERVATION requires physicalEvidenceId" }
        }
    }
}

data class ScientificEvidenceConflict(
    val evidenceIds: Set<String>,
    val physicalEvidenceId: String?,
    val authorities: Set<ScientificAuthority>,
    val reason: String,
) {
    init {
        require(evidenceIds.isNotEmpty())
        require(authorities.isNotEmpty())
        require(reason.isNotBlank())
    }
}

data class ScientificEvidenceResolution(
    val accepted: List<ResolvedScientificEvidence>,
    val conflicts: List<ScientificEvidenceConflict>,
)

/**
 * Evidence carried by a K* statistical measurement.
 *
 * Unlike raw PhysicsScientificInput, this object may represent a bounded
 * aggregate over many physical observations. It therefore carries scientific
 * support separately and never invents one physicalEvidenceId for an aggregate.
 * Raw evidence converts losslessly into this representation.
 */
data class KStarScientificEvidence(
    val authorities: Set<ScientificAuthority>,
    val role: ScientificEvidenceRole,
    val evidenceIds: Set<String>,
    val physicalEvidenceIds: Set<String>,
    val effectiveSupport: Double,
    val provenance: Set<String>,
) {
    init {
        require(authorities.isNotEmpty()) { "at least one scientific authority is required" }
        require(evidenceIds.isNotEmpty() && evidenceIds.none { it.isBlank() }) { "evidenceIds must be non-blank" }
        require(physicalEvidenceIds.none { it.isBlank() }) { "physicalEvidenceIds must be non-blank when present" }
        require(effectiveSupport.isFinite() && effectiveSupport >= 0.0) {
            "effectiveSupport must be finite and non-negative"
        }
        require(provenance.isNotEmpty() && provenance.none { it.isBlank() }) { "provenance must be non-blank" }
    }

    companion object {
        fun fromResolved(evidence: ResolvedScientificEvidence): KStarScientificEvidence =
            KStarScientificEvidence(
                authorities = evidence.authorities,
                role = evidence.role,
                evidenceIds = evidence.evidenceIds,
                physicalEvidenceIds = evidence.physicalEvidenceId?.let(::setOf) ?: emptySet(),
                effectiveSupport = evidence.effectiveWeight,
                provenance = evidence.provenance,
            )
    }
}

data class ScientificMeasurement(
    val valueMs: Double,
    val evidence: KStarScientificEvidence,
) {
    init {
        require(valueMs.isFinite() && valueMs > 0.0) { "scientific measurement must be finite and positive" }
    }

    constructor(valueMs: Double, evidence: ResolvedScientificEvidence) : this(
        valueMs = valueMs,
        evidence = KStarScientificEvidence.fromResolved(evidence),
    )
}

data class KStarScientificInput(
    val petrolOnGas: ScientificMeasurement,
    val petrolReference: ScientificMeasurement,
    val currentFactor: Double,
    val gain: PlantGain,
) {
    init {
        require(currentFactor.isFinite() && currentFactor > 0.0) { "currentFactor must be finite and positive" }
    }
}

private fun List<PhysicsScientificInput>.toResolved(weight: Double): ResolvedScientificEvidence =
    ResolvedScientificEvidence(
        authorities = map { it.authority }.toSet(),
        role = ScientificEvidenceRole.OBSERVATION,
        evidenceIds = map { it.evidenceId }.toSet(),
        physicalEvidenceId = first().physicalEvidenceId,
        effectiveWeight = weight,
        provenance = map { it.provenance }.toSet(),
    )

private fun List<PhysicsScientificInput>.toConflict(reason: String): ScientificEvidenceConflict =
    ScientificEvidenceConflict(
        evidenceIds = map { it.evidenceId }.toSet(),
        physicalEvidenceId = first().physicalEvidenceId,
        authorities = map { it.authority }.toSet(),
        reason = reason,
    )
