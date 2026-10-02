package com.omegas.prohub.autocal

import org.junit.Assert.*
import org.junit.Test

class NativeAutoCalAcquisitionEpochTest {
    private fun counts(total: Int) = IntArray(18) { if (it < total) 1 else 0 }
    private fun filled() = counts(6)

    @Test fun autoMatchStartsNewGasGenerationWithoutDiscardingPetrol() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(4L)
        gate.nativeCounter(4L, 0)
        assertTrue(gate.acquisitionGroup(4L, 0, filled(), filled()))
        assertTrue(gate.referenceGroup(4L, 0))
        val previous = gate.view()
        assertTrue(previous.comparisonAllowed)
        assertTrue(gate.nativeCounter(4L, 1))
        assertEquals(previous.petrolGeneration, gate.view().petrolGeneration)
        assertEquals(previous.gasGeneration + 1, gate.view().gasGeneration)
        assertFalse(gate.view().comparisonAllowed)
        assertTrue(gate.view().gasPending)
        assertFalse(gate.acquisitionGroup(4L, 0, filled(), filled()))
        assertTrue(gate.acquisitionGroup(4L, 1, filled(), counts(0)))
        assertFalse(gate.view().gasPending)
        assertFalse(gate.referenceGroup(4L, 1))
        assertTrue(gate.acquisitionGroup(4L, 1, filled(), filled()))
        assertTrue(gate.referenceGroup(4L, 1))
        assertTrue(gate.view().comparisonAllowed)
    }

    @Test fun allThreeMatchesPermitAcquisitionEvenWithoutObservedZero() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(8L)
        gate.nativeCounter(8L, 0)
        gate.acquisitionGroup(8L, 0, filled(), filled())
        gate.referenceGroup(8L, 0)
        for (step in 1..3) {
            assertTrue(gate.nativeCounter(8L, step))
            assertFalse(gate.view().comparisonAllowed)
            assertTrue(gate.acquisitionGroup(8L, step, filled(), counts(2)))
            assertFalse(gate.referenceGroup(8L, step))
            assertTrue(gate.acquisitionGroup(8L, step, filled(), filled()))
            assertTrue(gate.referenceGroup(8L, step))
        }
        assertEquals(3, gate.view().gasGeneration)
        assertEquals(0, gate.view().petrolGeneration)
        assertTrue(gate.view().comparisonAllowed)
    }

    @Test fun manualResetsNeverMasqueradeAsKReset() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(7L)
        gate.nativeCounter(7L, 3)
        gate.acquisitionGroup(7L, 3, filled(), filled())
        gate.referenceGroup(7L, 3)
        val baseline = gate.view()
        assertFalse(gate.manualAction(7L, "RESET_K_FACTOR"))
        assertFalse(gate.manualAction(7L, "DISABLE_AUTO_CAL"))
        assertEquals(baseline, gate.view())
        assertTrue(gate.manualAction(7L, "RESET_GAS"))
        assertFalse(gate.view().petrolPending)
        assertTrue(gate.view().gasPending)
        assertEquals(baseline.petrolGeneration, gate.view().petrolGeneration)
        assertEquals(baseline.gasGeneration + 1, gate.view().gasGeneration)
        gate.nativeCounter(7L, 0)
        assertEquals(baseline.gasGeneration + 1, gate.view().gasGeneration)
        assertTrue(gate.acquisitionGroup(7L, 0, filled(), counts(0)))
        assertTrue(gate.manualAction(7L, "RESET_PETROL"))
        assertTrue(gate.view().petrolPending)
        assertFalse(gate.view().gasPending)
    }

    @Test fun usbGenerationsAndCorruptedGroupsFailClosed() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(11L)
        gate.nativeCounter(11L, 0)
        assertFalse(gate.acquisitionGroup(11L, 0, IntArray(17), filled()))
        assertFalse(gate.acquisitionGroup(12L, 0, filled(), filled()))
        assertFalse(gate.view().comparisonAllowed)
        assertTrue(gate.acquisitionGroup(11L, 0, filled(), filled()))
        assertTrue(gate.referenceGroup(11L, 0))
        gate.reset(12L)
        assertFalse(gate.view().comparisonAllowed)
        assertEquals(0, gate.view().gasSamples)
        assertFalse(gate.referenceGroup(11L, 0))
    }

    @Test fun missedReceiptIsCaughtByTotalCounterLoss() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(11L)
        gate.nativeCounter(11L, 0)
        gate.acquisitionGroup(11L, 0, filled(), filled())
        gate.referenceGroup(11L, 0)
        assertTrue(gate.acquisitionGroup(11L, 0, filled(), counts(0)))
        assertEquals(1, gate.view().gasGeneration)
        assertEquals("GAS_COUNTER_RESTART", gate.view().reason)
        assertFalse(gate.view().comparisonAllowed)
    }
}
