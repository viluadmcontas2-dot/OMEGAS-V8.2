package com.omegas.prohub.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakEvidenceUncertaintyTest {
    @Test
    fun `fractional CNG authority increases runtime uncertainty without discarding evidence`() {
        val strong = runtimeWithCngStability(1.0)
        val weak = runtimeWithCngStability(0.20)

        val strongEstimate = strong.estimate(2_480.0, 0.50)!!
        val weakEstimate = weak.estimate(2_480.0, 0.50)!!

        assertTrue(weakEstimate.cngEffectiveSupport > 0.0)
        assertTrue(weakEstimate.cngEffectiveSupport < strongEstimate.cngEffectiveSupport)
        assertTrue(
            "A fractional observation must carry more mean uncertainty than a full observation",
            weakEstimate.uncertaintyFraction > strongEstimate.uncertaintyFraction,
        )
        assertFalse(
            "A lone 0.20-weight CNG observation must not inherit full-observation certainty",
            weakEstimate.actionable,
        )
    }

    @Test
    fun `bounded Advisor propagates fractional support into upstream uncertainty`() {
        val strong = boundedInput(cngWeight = 1.0)
        val weak = boundedInput(cngWeight = 0.20)

        val strongRow = strong.getJSONArray("comparisons").getJSONObject(0)
        val weakRow = weak.getJSONArray("comparisons").getJSONObject(0)

        assertTrue(weakRow.getDouble("quality") > 0.0)
        assertTrue(weakRow.getDouble("cng_effective_support") < strongRow.getDouble("cng_effective_support"))
        assertTrue(
            "Upstream uncertainty must reflect fractional CNG scientific support",
            weakRow.getDouble("upstream_uncertainty_fraction") >
                strongRow.getDouble("upstream_uncertainty_fraction"),
        )
        assertTrue(
            weakRow.getDouble("useful_margin_fraction") <
                strongRow.getDouble("useful_margin_fraction"),
        )
    }

    private fun runtimeWithCngStability(stability: Double): EquivalenceRuntime {
        val runtime = EquivalenceRuntime(
            surface = surface(),
            deadbandFraction = 0.02,
            empiricalSingleObservationNoiseFraction = 0.019,
        )
        runtime.observe(
            FuelLane.PETROL_REFERENCE,
            rpm = 2_480.0,
            mapBar = 0.50,
            petrolTinjMs = 3.00,
            stability = 1.0,
            novelty = 1.0,
            materialRevision = 1L,
        )
        runtime.observe(
            FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2_480.0,
            mapBar = 0.50,
            petrolTinjMs = 3.15,
            stability = stability,
            novelty = 1.0,
            materialRevision = 2L,
        )
        return runtime
    }

    private fun boundedInput(cngWeight: Double) = surface().also { surface ->
        surface.observe(FuelLane.PETROL_REFERENCE, 2_480.0, 0.50, 3.00, 1.0, 10L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_480.0, 0.50, 3.15, cngWeight, 11L)
    }.let { surface ->
        BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 1)
    }

    private fun surface() = EquivalenceSurface(
        EquivalenceSurface.Config(
            minRpm = 0.0,
            maxRpm = 9_000.0,
            rpmStep = 80.0,
            minMapBar = 0.0,
            maxMapBar = 2.5,
            mapStepBar = 0.02,
        ),
    )
}
