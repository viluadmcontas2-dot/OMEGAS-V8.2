package com.omegas.prohub.learning

/**
 * Conservative one-time bridge from accepted legacy gasoline-region summaries.
 *
 * It deliberately preserves the legacy mean/spread without pretending the old
 * region still contains raw frames. One migrated region can never claim more
 * than one effective independent observation and one live strong observation
 * has substantially more weight than even a highly supported legacy seed.
 */
internal object LegacyPetrolSeedPolicy {
    const val PROVENANCE = "LEGACY_ACCEPTED_PETROL_REGION"
    const val MAX_SEED_WEIGHT = 0.25
    const val MAX_EFFECTIVE_SUPPORT = 1.0

    fun weight(quality: Double, persistedSupport: Double): Double {
        if (!quality.isFinite() || !persistedSupport.isFinite() || persistedSupport <= 0.0) return 0.0
        val q = quality.coerceIn(0.0, 1.0)
        if (q <= 0.0) return 0.0
        // Monotone saturation: support informs the seed, but can never turn a
        // historical region into stronger authority than fresh live evidence.
        val supportFraction = persistedSupport / (persistedSupport + 1.0)
        return (MAX_SEED_WEIGHT * q * supportFraction).coerceIn(0.0, MAX_SEED_WEIGHT)
    }
}
