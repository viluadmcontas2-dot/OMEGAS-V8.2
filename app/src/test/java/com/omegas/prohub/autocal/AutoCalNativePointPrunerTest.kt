package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class AutoCalNativePointPrunerTest {
    private fun field(key: String, values: IntArray): JSONObject =
        JSONObject().put("key", key).put("status", "VALID").put("rawValues", JSONArray(values.toList()))

    private fun snapshot(
        hash: String,
        gasOutliers: Map<Int, Double> = emptyMap(),
        petrolOutliers: Map<Int, Double> = emptyMap(),
        autoMatchCount: Int = 0,
        correlatedRpm: Map<Int, Double> = emptyMap(),
    ): JSONObject {
        val maps = IntArray(18) { i -> ((0.20 + i * 0.045) * 1024.0).roundToInt() }
        fun times(outliers: Map<Int, Double>) = IntArray(18) { i ->
            val map = maps[i] / 1024.0
            val baseMs = 2.05 + 4.15 * map
            ((baseMs + (outliers[i] ?: 0.0)) * 512.0).roundToInt()
        }
        val calibration = IntArray(9).also { it[2] = 3; it[5] = 3; it[8] = 3 }
        val events = JSONArray()
        correlatedRpm.forEach { (band, rpm) ->
            events.put(
                JSONObject()
                    .put("fuel", "GNV")
                    .put("bandIndex", band)
                    .put("correlationState", "CORRELATED")
                    .put("correlationConfidence", 0.96)
                    .put("rpmConfidence", 0.95)
                    .put("rpm", rpm),
            )
        }
        return JSONObject()
            .put("available", true)
            .put("snapshotHash", hash)
            .put("sessionId", 77L)
            .put("autoMatchCount", autoMatchCount)
            .put(
                "fields",
                JSONArray()
                    .put(field("VECT_AUTOCAL_U8_1", intArrayOf(3)))
                    .put(field("CALIBRATION_VAL_1", calibration))
                    .put(field("ACQUIRED_ZONES_PETROL", intArrayOf(1, 1, 1, 1)))
                    .put(field("ACQUIRED_ZONES_GAS", intArrayOf(1, 1, 1, 1)))
                    .put(field("PETR_INJ_TBUF", times(petrolOutliers)))
                    .put(field("MNFLD_PRESS_BUF", maps))
                    .put(field("NUM_BUF_UPD_PETR", IntArray(18) { 6 }))
                    .put(field("PETR_INJ_TBUF_GAS", times(gasOutliers)))
                    .put(field("MNFLD_PRESS_BUF_GAS", maps))
                    .put(field("NUM_BUF_UPD_GAS", IntArray(18) { 6 })),
            )
            .put("nativeMaturityEvents", events)
    }

    @Test
    fun `curva coerente nao produz exclusao automatica`() {
        assertTrue(AutoCalNativePointPruner.candidates(snapshot("smooth")).isEmpty())
    }

    @Test
    fun `bolinha isolada muito abaixo da maioria vira candidata`() {
        val candidates = AutoCalNativePointPruner.candidates(
            snapshot("dip", gasOutliers = mapOf(8 to -1.05)),
        )
        assertEquals(1, candidates.size)
        assertEquals("GAS", candidates.single().target.fuel.wireName)
        assertEquals(8, candidates.single().target.index)
        assertTrue(candidates.single().robustZ >= AutoCalNativePointPruner.STRONG_Z)
    }

    @Test
    fun `anomalia moderada em lenta correlacionada pode ser podada mas nao pela porcentagem sozinha`() {
        val idle = AutoCalNativePointPruner.candidates(
            snapshot("idle", gasOutliers = mapOf(8 to -0.55), correlatedRpm = mapOf(8 to 890.0)),
        )
        assertEquals(1, idle.size)
        assertTrue(idle.single().idleContext)

        val sameShapeWithoutContext = AutoCalNativePointPruner.candidates(
            snapshot("not-idle", gasOutliers = mapOf(8 to -0.55)),
        )
        assertTrue(sameShapeWithoutContext.isEmpty())
    }

    @Test
    fun `dois pontos vizinhos deslocados sao comportamento local ambiguo e nao sao apagados`() {
        val candidates = AutoCalNativePointPruner.candidates(
            snapshot("cluster", gasOutliers = mapOf(8 to -0.95, 9 to -0.92)),
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `decisao automatica exige duas leituras distintas e nao entra em loop na mesma epoca`() {
        val pruner = AutoCalNativePointPruner()
        assertNull(pruner.observe(snapshot("a", gasOutliers = mapOf(8 to -1.05))))
        val decision = pruner.observe(snapshot("b", gasOutliers = mapOf(8 to -1.05)))
        assertEquals(8, decision!!.targets.single().index)
        pruner.accepted(decision)
        assertNull(pruner.observe(snapshot("c", gasOutliers = mapOf(8 to -1.05))))
    }

    @Test
    fun `nova epoca AutoMatch permite avaliar de novo o mesmo indice`() {
        val pruner = AutoCalNativePointPruner()
        assertNull(pruner.observe(snapshot("a", gasOutliers = mapOf(8 to -1.05), autoMatchCount = 0)))
        val first = pruner.observe(snapshot("b", gasOutliers = mapOf(8 to -1.05), autoMatchCount = 0))
        pruner.accepted(first!!)
        assertNull(pruner.observe(snapshot("c", gasOutliers = mapOf(8 to -1.05), autoMatchCount = 1)))
        assertEquals(8, pruner.observe(snapshot("d", gasOutliers = mapOf(8 to -1.05), autoMatchCount = 1))!!.targets.single().index)
    }
}
