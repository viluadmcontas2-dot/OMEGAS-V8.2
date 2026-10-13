package com.omegas.prohub.autocal

import org.junit.Assert.*
import org.junit.Test

class NativeAutoCalAcquisitionEpochTest {
    private fun counts(total: Int) = IntArray(18) { if (it < total) 1 else 0 }
    private fun filled() = counts(6)

    // Carro, 2026-10-12: após "Reaprender GNV" a ECU ainda devolveu os contadores antigos por alguns ciclos e a tela
    // voltou a mostrar GNV 12/18 como vivo. O pendente de GNV só cai com contadores zerados; a gasolina não é afetada.
    @Test fun resetGasStaysPendingWhileEcuStillReportsOldGasCounters() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(9L)
        gate.nativeCounter(9L, 0)
        assertTrue(gate.acquisitionGroup(9L, 0, filled(), counts(12)))
        assertTrue(gate.manualAction(9L, "RESET_GAS"))
        assertTrue(gate.view().gasPending)
        gate.nativeCounter(9L, 0) // o monitor re-observa o contador antes do próximo grupo (baseline novo)
        // Mesma leitura antiga: não é a aquisição nova, a época continua pendente.
        assertFalse(gate.acquisitionGroup(9L, 0, filled(), counts(12)))
        assertTrue(gate.view().gasPending)
        assertEquals("GAS_RESET_NOT_APPLIED_YET", gate.view().reason)
        assertFalse(gate.view().comparisonAllowed)
        // Contadores de GNV zerados: aí sim a nova geração começa, com a gasolina preservada.
        assertTrue(gate.acquisitionGroup(9L, 0, filled(), counts(0)))
        assertFalse(gate.view().gasPending)
        assertFalse(gate.view().petrolPending)
        assertEquals(6, gate.view().petrolSamples)
        assertEquals(0, gate.view().gasSamples)
    }

    @Test fun resetPetrolStaysPendingWhileEcuStillReportsOldPetrolCounters() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(10L)
        gate.nativeCounter(10L, 0)
        assertTrue(gate.acquisitionGroup(10L, 0, filled(), filled()))
        assertTrue(gate.manualAction(10L, "RESET_PETROL"))
        gate.nativeCounter(10L, 0)
        assertFalse(gate.acquisitionGroup(10L, 0, filled(), filled()))
        assertTrue(gate.view().petrolPending)
        assertEquals("PETROL_RESET_NOT_APPLIED_YET", gate.view().reason)
        assertTrue(gate.acquisitionGroup(10L, 0, counts(0), filled()))
        assertFalse(gate.view().petrolPending)
    }

    // Dono, 2026-10-13: depois de "Reaprender GNV" + "Reaprender gasolina" a aquisição não podia depender de ordem. Um reset ainda
    // não aplicado na ECU para um combustível não segura o outro.
    @Test fun unappliedGasResetDoesNotHoldPetrolAcquisition() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(20L)
        gate.nativeCounter(20L, 0)
        assertTrue(gate.acquisitionGroup(20L, 0, filled(), counts(12)))
        assertTrue(gate.manualAction(20L, "RESET_GAS"))
        assertTrue(gate.manualAction(20L, "RESET_PETROL"))
        gate.nativeCounter(20L, 0)
        // Gasolina já recomeçou na ECU; o GNV ainda devolve os contadores antigos.
        assertFalse(gate.acquisitionGroup(20L, 0, counts(0), counts(12)))
        assertFalse("a gasolina que a ECU já reiniciou sai de pendente", gate.view().petrolPending)
        assertTrue(gate.view().gasPending)
        // Depois é a vez do GNV, quando o carro rodar nele: ordem qualquer.
        assertTrue(gate.acquisitionGroup(20L, 0, counts(2), counts(0)))
        assertFalse(gate.view().gasPending)
        assertEquals(2, gate.view().petrolSamples)
    }

    // Se o carro já voltou a adquirir antes da leitura seguinte, os contadores nem passam por zero; ficar abaixo do que havia
    // no reset prova que a ECU aplicou (antes: pendente para sempre).
    @Test fun resetCountsAsAppliedWhenCountersDropBelowTheResetBaselineEvenWithoutZero() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(21L)
        gate.nativeCounter(21L, 0)
        assertTrue(gate.acquisitionGroup(21L, 0, filled(), counts(12)))
        assertTrue(gate.manualAction(21L, "RESET_GAS"))
        gate.nativeCounter(21L, 0)
        assertFalse(gate.acquisitionGroup(21L, 0, filled(), counts(12)))
        assertTrue(gate.view().gasPending)
        assertTrue(gate.acquisitionGroup(21L, 0, filled(), counts(3)))
        assertFalse(gate.view().gasPending)
        assertEquals(3, gate.view().gasSamples)
    }

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

    @Test fun gasResetPreservesPetrolReferenceAndBlocksOnlyGasReference() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(31L)
        gate.nativeCounter(31L, 0)
        gate.acquisitionGroup(31L, 0, filled(), filled())
        gate.referenceGroup(31L, 0)
        assertFalse(gate.view().petrolReferencePending)
        assertFalse(gate.view().gasReferencePending)
        gate.nativeCounter(31L, 1)
        assertFalse(gate.view().petrolReferencePending)
        assertTrue(gate.view().gasReferencePending)
        assertTrue(gate.view().referencePending)
    }

    @Test fun petrolResetPreservesExistingGasReferenceButCannotCompare() {
        val gate = NativeAutoCalAcquisitionEpoch()
        gate.reset(32L)
        gate.nativeCounter(32L, 0)
        gate.acquisitionGroup(32L, 0, filled(), filled())
        gate.referenceGroup(32L, 0)
        assertTrue(gate.manualAction(32L, "RESET_PETROL"))
        assertTrue(gate.view().petrolReferencePending)
        assertFalse(gate.view().gasReferencePending)
        assertFalse(gate.view().comparisonAllowed)
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
