package com.omegas.v7.runtime

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.ExpectedEffect
import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SuggestionPhysicsMetadataTest {
    @Test
    fun `session snapshot preserves magnitude authority mechanism and expected effect`() {
        val revision = CalibrationRevisionV7(1, 2)
        val suggestion = LocalSuggestionV7(
            id = "physics-map",
            createdAtMs = 10L,
            expectedRevision = revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(0, 0, 128, 132)),
            rationale = "local residual",
            magnitudeAuthority = MagnitudeAuthority.POLICY_ONLY,
            correctionMechanism = CorrectionMechanism.MAP_LOCAL,
            expectedEffect = ExpectedEffect(
                direction = EffectDirection.INCREASE,
                lowerBound = null,
                upperBound = null,
                assumptions = listOf("legacy advisor step; not ideal target"),
                authority = MagnitudeAuthority.POLICY_ONLY,
                falsifier = "post-write residual does not improve",
            ),
        )
        val state = V7SessionState(
            sessionId = "s",
            calibration = CalibrationStateV7(
                revision = revision,
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) { List(CalibrationShapeV7.MAP_K_COLUMNS) { 128 } },
            ),
            suggestions = listOf(suggestion),
        )

        val restored = V7SessionSnapshotCodec.decode(V7SessionSnapshotCodec.encode(state)).suggestions.single()
        assertEquals(MagnitudeAuthority.POLICY_ONLY, restored.magnitudeAuthority)
        assertEquals(CorrectionMechanism.MAP_LOCAL, restored.correctionMechanism)
        assertNotNull(restored.expectedEffect)
        assertEquals(EffectDirection.INCREASE, restored.expectedEffect!!.direction)
        assertEquals(MagnitudeAuthority.POLICY_ONLY, restored.expectedEffect!!.authority)
    }
}
