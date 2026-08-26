package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActuatorTargetIdentificationTest {
    @Test
    fun `curve frozen and map free yields only conditioned Map target`() {
        val ctx = context()
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FREE, ActuatorFreedom.FROZEN, localResidualRemoved = false),
        )

        assertEquals(1.12, result.fStar!!, 1e-12)
        assertEquals(1.12 / 0.98, result.mapTarget!!.factor!!, 1e-12)
        assertNull(result.curveTarget)
        assertEquals(1.12, result.mapTarget!!.factor!! * ctx.cEff!!, 1e-12)
        assertEquals("MAP_CONDITIONED_ON_FROZEN_CURVE", result.reason)
    }

    @Test
    fun `map frozen and curve free yields only conditioned Curve target after local residual removal`() {
        val ctx = context()
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FROZEN, ActuatorFreedom.FREE, localResidualRemoved = true),
        )

        assertEquals(1.12, result.fStar!!, 1e-12)
        assertNull(result.mapTarget)
        assertEquals(1.12 / 1.05, result.curveTarget!!.factor!!, 1e-12)
        assertEquals(1.12, ctx.mEff!! * result.curveTarget!!.factor!!, 1e-12)
        assertEquals("CURVE_CONDITIONED_ON_FROZEN_MAP", result.reason)
    }

    @Test
    fun `curve target stays unavailable until local residual is removed`() {
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FROZEN, ActuatorFreedom.FREE, localResidualRemoved = false),
        )

        assertEquals(1.12, result.fStar!!, 1e-12)
        assertNull(result.mapTarget)
        assertNull(result.curveTarget)
        assertEquals("LOCAL_RESIDUAL_NOT_REMOVED", result.reason)
    }

    @Test
    fun `both free keeps F star primary and emits no simultaneous actuator targets`() {
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FREE, ActuatorFreedom.FREE, localResidualRemoved = true),
        )

        assertEquals(1.12, result.fStar!!, 1e-12)
        assertNull(result.mapTarget)
        assertNull(result.curveTarget)
        assertEquals("FSTAR_PRIMARY_BOTH_ACTUATORS_FREE", result.reason)
    }

    @Test
    fun `both frozen emits no actuator target`() {
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FROZEN, ActuatorFreedom.FROZEN, localResidualRemoved = true),
        )

        assertEquals(1.12, result.fStar!!, 1e-12)
        assertNull(result.mapTarget)
        assertNull(result.curveTarget)
        assertEquals("NO_FREE_ACTUATOR", result.reason)
    }

    @Test
    fun `abstained K star cannot create actuator target`() {
        val result = ActuatorTargetIdentification.resolve(
            input(ActuatorFreedom.FREE, ActuatorFreedom.FROZEN, localResidualRemoved = true)
                .copy(kStar = kstar(abstained = true, targetFactor = null)),
        )

        assertNull(result.fStar)
        assertNull(result.mapTarget)
        assertNull(result.curveTarget)
        assertEquals(MagnitudeAuthority.UNKNOWN, result.authority)
    }

    @Test
    fun `identification invariant never permits simultaneous Map and Curve targets`() {
        ActuatorFreedom.entries.forEach { mapFreedom ->
            ActuatorFreedom.entries.forEach { curveFreedom ->
                listOf(false, true).forEach { localRemoved ->
                    val result = ActuatorTargetIdentification.resolve(input(mapFreedom, curveFreedom, localRemoved))
                    assertTrue(result.mapTarget == null || result.curveTarget == null)
                }
            }
        }
    }

    private fun input(
        mapFreedom: ActuatorFreedom,
        curveFreedom: ActuatorFreedom,
        localResidualRemoved: Boolean,
    ) = ActuatorIdentificationInput(
        kStar = kstar(),
        context = context(),
        mapFreedom = mapFreedom,
        curveFreedom = curveFreedom,
        localResidualRemoved = localResidualRemoved,
    )

    private fun context(): CalibrationPhysicsContext = CalibrationPhysicsContext.create(
        identity = CalibrationIdentityRef("fn", "geo", "map-A", "curve-A", 9L, 3, "FULL_ECU_READ"),
        microState = ContextSlice(2400.0, 0.55, 1.1, 38.0, 88.0, 100L),
        deadtime = DeadtimeEvidence.known(1.0, "readback"),
        mapEffective = FactorEvidence.liveEffective("M_eff", 1.05, "map", 9L, 100L),
        curveEffective = FactorEvidence.liveEffective("C_eff", 0.98, "curve", 9L, 100L),
        uncertainty = 0.04,
    )

    private fun kstar(
        abstained: Boolean = false,
        targetFactor: Double? = 1.12,
    ): KStarEstimate = KStarEstimate(
        logError = 0.0,
        currentTheta = 0.0,
        targetTheta = targetFactor?.let { kotlin.math.ln(it) },
        targetFactor = targetFactor,
        gain = PlantGain.empiricallyBounded(1.0, 0.8, 1.2),
        authority = if (abstained) MagnitudeAuthority.UNKNOWN else MagnitudeAuthority.EMPIRICALLY_BOUNDED,
        abstained = abstained,
        reason = if (abstained) "fixture-abstain" else "fixture",
        scientificTrace = KStarScientificTrace(
            petrolOnGasAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
            petrolReferenceAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
            petrolOnGasEvidenceIds = setOf("cng"),
            petrolReferenceEvidenceIds = setOf("gas"),
            petrolOnGasPhysicalEvidenceIds = setOf("cng-frame"),
            petrolReferencePhysicalEvidenceIds = setOf("gas-frame"),
            provenance = setOf("step153-test"),
        ),
    )
}
