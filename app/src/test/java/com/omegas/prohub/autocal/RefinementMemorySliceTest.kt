package com.omegas.prohub.autocal

import org.junit.Assert.*
import org.junit.Test

class RefinementMemorySliceTest {
    private var time = 1000L
    private fun drive(l: EquivalenceLedger, fuel: String, ms: Double, n: Int = 8) {
        repeat(n * 3) { l.accept(EquivalenceLedger.Frame(time, fuel, 2000.0, .6, ms, engineHot = true)); time += 100 }
    }
    @Test fun predictionIsNotDecisionEvidenceAndHistorySurvives() {
        val l = EquivalenceLedger()
        drive(l, "GASOLINA", 5.0); drive(l, "GNV", 5.5)
        val old = l.exportMemory().getJSONArray("gas").length()
        assertTrue(l.rescaleCurve(IntArray(30) { (it + 1) * 512 }, IntArray(30) { 16384 }, IntArray(30) { 18022 }, "TEST"))
        assertEquals(old, l.exportMemory().getJSONArray("gas").length())
        assertTrue(l.allPairs().isEmpty())
        assertTrue(l.progress(true).getJSONObject("metric").isNull("equivalencePct"))
        drive(l, "GNV", 5.0)
        assertEquals(8, l.allPairs().size)
        assertEquals(100.0, l.progress(true).getJSONObject("metric").getDouble("equivalencePct"), .001)
    }
    @Test fun gasolineSurvivesNativeAcquisitionAndResetKeepsDatedGas() {
        val l = EquivalenceLedger()
        drive(l, "GASOLINA", 5.0); drive(l, "GNV", 5.5)
        l.notePetrolAcquisition(18); l.notePetrolAcquisition(0)
        assertEquals(8, l.exportMemory().getJSONArray("petrol").length())
        l.resetGas("TEST")
        assertEquals(8, l.exportMemory().getJSONArray("gas").length())
        assertTrue(l.allPairs().isEmpty())
        val restored = EquivalenceLedger()
        assertTrue(restored.restoreMemory(l.exportMemory()))
        assertTrue(restored.allPairs().isEmpty())
    }
}
