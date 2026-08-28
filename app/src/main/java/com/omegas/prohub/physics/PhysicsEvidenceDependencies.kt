package com.omegas.prohub.physics

enum class PhysicsRule {
    K1_COMPONENT,
    MUL_ACT_ACTIVE_GAIN,
    K2_INTERPRETATION,
    K3_UNKNOWNNESS,
    K4_INTERPRETATION,
    DEADTIME_ACTIVE_PULSE,
    EFFECTIVE_ACTUATION,
    ENVIRONMENTAL_EXPLANATION,
    KSTAR_WITH_ACTIVE_PULSE,
    RESIDUAL_MECHANISM_CLASSIFICATION,
}

/**
 * Explicit dependency graph used when reverse-engineering evidence changes.
 * Invalidation is selective and never erases history; callers mark returned
 * rules STALE_BY_EVIDENCE and preserve unaffected rules.
 */
object PhysicsEvidenceDependencies {
    private val dependencies: Map<PhysicsRule, Set<PhysicsEvidenceId>> = mapOf(
        PhysicsRule.K1_COMPONENT to setOf(PhysicsEvidenceId.K1_MAP),
        PhysicsRule.MUL_ACT_ACTIVE_GAIN to setOf(PhysicsEvidenceId.MUL_ACT, PhysicsEvidenceId.GAS_DEADTIME),
        PhysicsRule.K2_INTERPRETATION to setOf(PhysicsEvidenceId.K2_PRESSURE, PhysicsEvidenceId.PRESSURE),
        PhysicsRule.K3_UNKNOWNNESS to setOf(PhysicsEvidenceId.K3_ECU_SIDE, PhysicsEvidenceId.WATER_TEMP),
        PhysicsRule.K4_INTERPRETATION to setOf(PhysicsEvidenceId.K4_GAS_TEMP, PhysicsEvidenceId.GAS_TEMP),
        PhysicsRule.DEADTIME_ACTIVE_PULSE to setOf(PhysicsEvidenceId.GAS_DEADTIME),
        PhysicsRule.EFFECTIVE_ACTUATION to setOf(PhysicsEvidenceId.K1_MAP, PhysicsEvidenceId.MUL_ACT),
        PhysicsRule.ENVIRONMENTAL_EXPLANATION to setOf(
            PhysicsEvidenceId.K2_PRESSURE,
            PhysicsEvidenceId.K3_ECU_SIDE,
            PhysicsEvidenceId.K4_GAS_TEMP,
            PhysicsEvidenceId.PRESSURE,
            PhysicsEvidenceId.WATER_TEMP,
            PhysicsEvidenceId.GAS_TEMP,
            PhysicsEvidenceId.MAP,
            PhysicsEvidenceId.RPM,
        ),
        PhysicsRule.KSTAR_WITH_ACTIVE_PULSE to setOf(
            PhysicsEvidenceId.K1_MAP,
            PhysicsEvidenceId.MUL_ACT,
            PhysicsEvidenceId.GAS_DEADTIME,
        ),
        PhysicsRule.RESIDUAL_MECHANISM_CLASSIFICATION to setOf(
            PhysicsEvidenceId.RPM,
            PhysicsEvidenceId.MAP,
            PhysicsEvidenceId.PRESSURE,
            PhysicsEvidenceId.WATER_TEMP,
            PhysicsEvidenceId.GAS_TEMP,
        ),
    )

    fun dependenciesOf(rule: PhysicsRule): Set<PhysicsEvidenceId> = dependencies.getValue(rule)

    fun invalidate(changedEvidence: Set<PhysicsEvidenceId>): Set<PhysicsRule> {
        if (changedEvidence.isEmpty()) return emptySet()
        return dependencies.entries
            .filter { (_, evidence) -> evidence.any(changedEvidence::contains) }
            .mapTo(linkedSetOf()) { it.key }
    }
}
