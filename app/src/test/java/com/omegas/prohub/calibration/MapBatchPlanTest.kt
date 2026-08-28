package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBatchPlanTest {
    private fun cells(count: Int): JSONArray = JSONArray().apply {
        repeat(count) { index ->
            put(JSONObject()
                .put("row", index / 12)
                .put("column", index % 12)
                .put("current", 120)
                .put("target", 125))
        }
    }

    @Test
    fun `uma intenção pode conter todas as 144 células graváveis`() {
        val plan = MapBatchPlan.build(cells(144))
        assertEquals(144, MapBatchPlan.MAX_USER_CELLS)
        assertEquals(144, plan.totalCells)
        assertEquals(9, plan.chunks.size)
        assertTrue(plan.chunks.all { it.length() in 1..16 })
        assertEquals(144, plan.chunks.sumOf { it.length() })
    }

    @Test
    fun `ordem e coordenadas são preservadas entre blocos internos`() {
        val plan = MapBatchPlan.build(cells(33))
        val flattened = mutableListOf<String>()
        plan.chunks.forEach { chunk ->
            repeat(chunk.length()) { index ->
                val item = chunk.getJSONObject(index)
                flattened += "${item.getInt("row")}:${item.getInt("column")}"
            }
        }
        assertEquals((0 until 33).map { "${it / 12}:${it % 12}" }, flattened)
        assertEquals(listOf(16, 16, 1), plan.chunks.map { it.length() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lote vazio é rejeitado`() {
        MapBatchPlan.build(JSONArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mais de 144 células é rejeitado`() {
        MapBatchPlan.build(cells(145))
    }
}
