package com.omegas.prohub.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFreshnessPolicyTest {
    @Test
    fun `idade dentro do limite e current e acima vira stale`() {
        assertEquals(RuntimeFreshness.CURRENT, RuntimeFreshnessPolicy.classify(1_000L, 1_150L, 150L))
        assertEquals(RuntimeFreshness.STALE, RuntimeFreshnessPolicy.classify(1_000L, 1_151L, 150L))
    }

    @Test
    fun `relogio incoerente vira unknown sem clamp silencioso`() {
        assertEquals(RuntimeFreshness.UNKNOWN, RuntimeFreshnessPolicy.classify(2_000L, 1_999L, 150L))
        assertEquals(RuntimeFreshness.UNKNOWN, RuntimeFreshnessPolicy.classify(0L, 2_000L, 150L))
    }

    @Test
    fun `consumer cientifico aceita somente current`() {
        assertTrue(RuntimeFreshnessPolicy.scientificallyCurrent(RuntimeFreshness.CURRENT))
        assertFalse(RuntimeFreshnessPolicy.scientificallyCurrent(RuntimeFreshness.STALE))
        assertFalse(RuntimeFreshnessPolicy.scientificallyCurrent(RuntimeFreshness.UNKNOWN))
    }
}
