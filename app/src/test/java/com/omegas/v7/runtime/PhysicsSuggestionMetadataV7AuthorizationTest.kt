package com.omegas.v7.runtime

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsSuggestionMetadataV7AuthorizationTest {
    @Test
    fun unknown_expected_effect_authority_is_not_actionable() {
        assertFalse(authority(MagnitudeAuthority.UNKNOWN).authorizes(SuggestionTargetV7.MAP_K))
    }

    @Test
    fun policy_only_expected_effect_authority_is_not_actionable() {
        assertFalse(authority(MagnitudeAuthority.POLICY_ONLY).authorizes(SuggestionTargetV7.MAP_K))
    }

    @Test
    fun empirical_expected_effect_authority_can_authorize_matching_map_target() {
        assertTrue(authority(MagnitudeAuthority.EMPIRICALLY_BOUNDED).authorizes(SuggestionTargetV7.MAP_K))
    }

    private fun authority(effectAuthority: MagnitudeAuthority): PhysicsSuggestionMetadataV7 =
        PhysicsSuggestionMetadataV7(
            magnitudeAuthority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            correctionMechanism = CorrectionMechanism.MAP_LOCAL,
            effectDirection = EffectDirection.INCREASE,
            effectAuthority = effectAuthority,
            falsifier = "post-write residual must improve",
            evidencePath = listOf("localized residual", "gain posterior"),
            idealTarget = true,
        )
}
