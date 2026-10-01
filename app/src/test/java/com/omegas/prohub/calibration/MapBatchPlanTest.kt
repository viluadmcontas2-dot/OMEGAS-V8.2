package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun `uma intenção de mapa inteiro permanece um único lote serial`() {
        val plan = MapBatchPlan.build(cells(144))
        assertEquals(144, MapBatchPlan.MAX_USER_CELLS)
        assertEquals(144, plan.totalCells)
        assertEquals(1, plan.chunks.size)
        assertEquals(144, plan.chunks.single().length())
    }

    @Test
    fun `ordem e coordenadas são preservadas no lote direto`() {
        val plan = MapBatchPlan.build(cells(33))
        val flattened = mutableListOf<String>()
        plan.chunks.forEach { chunk ->
            repeat(chunk.length()) { index ->
                val item = chunk.getJSONObject(index)
                flattened += "${item.getInt("row")}:${item.getInt("column")}"
            }
        }
        assertEquals((0 until 33).map { "${it / 12}:${it % 12}" }, flattened)
        assertEquals(listOf(33), plan.chunks.map { it.length() })
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
