package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achado 8: mistura E suavidade piores depois do ajuste = CONTESTADO (não INCONCLUSIVO). */
class ProofContestedTest {
    private var now = 0L

    private fun point(index: Int, mixture: Double?, samples: Int, rough: Double? = null) = EquivalencePoint(
        index, 6.0, 1.0, null, mixture, 0.04, rough, null, null, 0.1, samples, emptySet(), PointState.POBRE, 3,
    )

    @Test
    fun `mistura piorou e suavidade piorou vira CONTESTADO`() {
        val p = EquivalencePhases(null) { now }
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20, rough = 1.0)))
        assertEquals(PointState.CONTESTADO, p.judgePoints(listOf(point(12, 0.09, 8, rough = 1.10)), true).states[12])
    }
}
