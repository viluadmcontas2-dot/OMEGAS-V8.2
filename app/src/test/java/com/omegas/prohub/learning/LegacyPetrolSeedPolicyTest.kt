package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPetrolSeedPolicyTest {
    @Test
    fun seed_authority_is_monotone_in_persisted_support_but_strictly_capped() {
        val low = LegacyPetrolSeedPolicy.weight(quality = 0.8, persistedSupport = 1.0)
        val medium = LegacyPetrolSeedPolicy.weight(quality = 0.8, persistedSupport = 4.0)
        val high = LegacyPetrolSeedPolicy.weight(quality = 0.8, persistedSupport = 10_000.0)

        assertTrue(low > 0.0)
        assertTrue(low < medium)
        assertTrue(medium < high)
        assertTrue(high <= LegacyPetrolSeedPolicy.MAX_SEED_WEIGHT)
    }

    @Test
    fun quality_is_monotone_and_invalid_input_never_manufactures_authority() {
        val weak = LegacyPetrolSeedPolicy.weight(quality = 0.2, persistedSupport = 10.0)
        val strong = LegacyPetrolSeedPolicy.weight(quality = 0.9, persistedSupport = 10.0)
        assertTrue(weak < strong)
        assertEquals(0.0, LegacyPetrolSeedPolicy.weight(Double.NaN, 10.0), 0.0)
        assertEquals(0.0, LegacyPetrolSeedPolicy.weight(0.8, Double.NaN), 0.0)
        assertEquals(0.0, LegacyPetrolSeedPolicy.weight(0.8, 0.0), 0.0)
    }

    @Test
    fun one_legacy_region_never_claims_more_than_one_effective_observation() {
        assertEquals(1.0, LegacyPetrolSeedPolicy.MAX_EFFECTIVE_SUPPORT, 0.0)
    }
}
