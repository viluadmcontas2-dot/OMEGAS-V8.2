package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionalActuatorTargetTest {
    private fun context(): CalibrationPhysicsContext = CalibrationPhysicsContext.create(
        identity = CalibrationIdentityRef("fn", "geo", "map", "curve", 9L, 3, "FULL_ECU_READ"),
        microState = ContextSlice(2400.0, 0.55, 1.1, 38.0, 88.0, 100L),
        deadtime = DeadtimeEvidence.known(1.0, "readback"),
        mapEffective = FactorEvidence.liveEffective("M_eff", 1.05, "map", 9L, 100L),
        curveEffective = FactorEvidence.liveEffective("C_eff", 0.98, "curve", 9L, 100L),
        uncertainty = 0.04,
    )

    @Test fun `map local target derives from ideal F and current curve not step policy`() {
        val ctx = context()
        val kstar = KStarEstimate(
            logError = 0.0,
            currentTheta = 0.0,
            targetTheta = kotlin.math.ln(1.12),
            targetFactor = 1.12,
            gain = PlantGain.empiricallyBounded(1.0, 0.8, 1.2),
            authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            abstained = false,
            reason = "fixture",
            scientificTrace = trace("fixture-cng", "fixture-gas"),
        )
        val target = ConditionalActuatorTargets.mapLocal(kstar, ctx)
        assertEquals(1.12 / 0.98, requireNotNull(target.factor), 1e-12)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, target.authority)
        assertEquals(ctx.microState, target.context)
    }

    @Test fun `curve target requires local residual removed`() {
        val ctx = context()
        val kstar = KStarEstimator.estimate(
            typedInput(
                petrolOnGasMs = 5.5,
                petrolReferenceMs = 5.0,
                currentFactor = requireNotNull(ctx.fCurrent),
                gain = PlantGain.empiricallyBounded(1.0, 0.9, 1.1),
                suffix = "curve",
            ),
        )
        val blocked = ConditionalActuatorTargets.curveGlobal(kstar, ctx, localResidualRemoved = false)
        assertNull(blocked.factor)
        assertEquals(MagnitudeAuthority.UNKNOWN, blocked.authority)

        val target = ConditionalActuatorTargets.curveGlobal(kstar, ctx, localResidualRemoved = true)
        assertEquals(requireNotNull(kstar.targetFactor) / 1.05, requireNotNull(target.factor), 1e-12)
        assertTrue(target.factor!! > 0.0)
    }

    @Test fun `abstained K star cannot become actuator target`() {
        val ctx = context()
        val kstar = KStarEstimator.estimate(
            typedInput(
                petrolOnGasMs = 5.5,
                petrolReferenceMs = 5.0,
                currentFactor = requireNotNull(ctx.fCurrent),
                gain = PlantGain.unknown(),
                suffix = "unknown",
            ),
        )
        assertNull(ConditionalActuatorTargets.mapLocal(kstar, ctx).factor)
        assertNull(ConditionalActuatorTargets.curveGlobal(kstar, ctx, true).factor)
    }

    private fun typedInput(
        petrolOnGasMs: Double,
        petrolReferenceMs: Double,
        currentFactor: Double,
        gain: PlantGain,
        suffix: String,
    ): KStarScientificInput {
        fun evidence(id: String): ResolvedScientificEvidence = ResolvedScientificEvidence(
            authorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
            role = ScientificEvidenceRole.OBSERVATION,
            evidenceIds = setOf(id),
            physicalEvidenceId = id,
            effectiveWeight = 1.0,
            provenance = setOf("conditional-actuator-test"),
        )
        return KStarScientificInput(
            petrolOnGas = ScientificMeasurement(petrolOnGasMs, evidence("cng-$suffix")),
            petrolReference = ScientificMeasurement(petrolReferenceMs, evidence("gas-$suffix")),
            currentFactor = currentFactor,
            gain = gain,
        )
    }

    private fun trace(cngId: String, gasId: String): KStarScientificTrace = KStarScientificTrace(
        petrolOnGasAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
        petrolReferenceAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
        petrolOnGasEvidenceIds = setOf(cngId),
        petrolReferenceEvidenceIds = setOf(gasId),
        petrolOnGasPhysicalEvidenceIds = setOf(cngId),
        petrolReferencePhysicalEvidenceIds = setOf(gasId),
        provenance = setOf("conditional-actuator-test"),
    )
}
