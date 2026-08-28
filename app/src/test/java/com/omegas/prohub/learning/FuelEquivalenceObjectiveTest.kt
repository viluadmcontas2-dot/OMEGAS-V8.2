package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEquivalenceObjectiveTest {
    private val policy = LearningTolerancePolicy(
        equivalenceDeadbandMs = 0.12,
        equivalenceDeadbandPercent = 2.5,
    )

    @Test
    fun signConventionIsPetrolOnCngMinusReferenceOverReference() {
        val positive = FuelEquivalenceObjective.evaluate(4.0, 4.4, 0.05, policy)
        assertTrue(positive.valid)
        assertEquals(FuelEquivalenceState.PETROL_ON_CNG_ABOVE_REFERENCE, positive.state)
        assertEquals(0.4, positive.differenceMs!!, 1e-9)
        assertEquals(10.0, positive.errorPercent!!, 1e-9)

        val negative = FuelEquivalenceObjective.evaluate(4.0, 3.6, 0.05, policy)
        assertTrue(negative.valid)
        assertEquals(FuelEquivalenceState.PETROL_ON_CNG_BELOW_REFERENCE, negative.state)
        assertEquals(-10.0, negative.errorPercent!!, 1e-9)
    }

    @Test
    fun exactZeroAndPolicyDeadbandStayDistinctFromInvalid() {
        val zero = FuelEquivalenceObjective.evaluate(4.0, 4.0, 0.05, policy)
        assertTrue(zero.valid)
        assertEquals(FuelEquivalenceState.WITHIN_POLICY_DEADBAND, zero.state)
        assertEquals(0.0, zero.errorPercent!!, 0.0)

        val small = FuelEquivalenceObjective.evaluate(4.0, 4.08, 0.05, policy)
        assertEquals(FuelEquivalenceState.WITHIN_POLICY_DEADBAND, small.state)
        assertTrue(small.withinMsDeadband)
        assertTrue(small.withinPercentDeadband)
    }

    @Test
    fun unavailableNonFiniteAndSmallDenominatorAreInvalidWithoutFakeNumber() {
        val unavailable = FuelEquivalenceObjective.evaluate(null, 4.0, 0.05, policy)
        assertFalse(unavailable.valid)
        assertEquals("SIGNAL_UNAVAILABLE", unavailable.reasonCode)
        assertEquals(null, unavailable.errorPercent)

        val nonFinite = FuelEquivalenceObjective.evaluate(Double.NaN, 4.0, 0.05, policy)
        assertFalse(nonFinite.valid)
        assertEquals("SIGNAL_IMPLAUSIBLE", nonFinite.reasonCode)

        val denominator = FuelEquivalenceObjective.evaluate(0.04, 0.05, 0.05, policy)
        assertFalse(denominator.valid)
        assertEquals("REFERENCE_DENOMINATOR_TOO_SMALL", denominator.reasonCode)
        assertEquals(null, denominator.errorPercent)
    }
}
