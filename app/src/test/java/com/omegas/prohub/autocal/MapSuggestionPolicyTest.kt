package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class MapSuggestionPolicyTest {
    private fun map(k: Int = 150) = JSONObject().put("sessionConfirmed", true).put("complete", true).put("rows", JSONArray(List(12) { List(12) { k } }))
    private fun group(error: Double = 0.12, local: Boolean = true, reason: String = EquivalenceAttribution.LOCAL) =
        EquivalenceAttribution.Group(EquivalenceAttribution.Key(0, 1), 9, 3, 5.0, 5.0 * (1 + error),
            ln(1 + error), 12, 0.0, ln(1 + error), local, reason)

    @Test fun suggestionUsesNativeLimitsAndIsRepeatable() {
        val groups = listOf(group())
        val before = map(250)
        val a = MapSuggestionPolicy.decide(groups, before)
        assertEquals("SUGERIR", a.getString("verdict"))
        val cells = a.getJSONArray("cells")
        assertTrue(cells.length() > 0)
        repeat(cells.length()) { assertEquals(255, cells.getJSONObject(it).getInt("target")) }
        assertTrue(a.similar(MapSuggestionPolicy.decide(groups, before)))
        assertEquals(250, before.getJSONArray("rows").getJSONArray(0).getInt(0))
        assertFalse(a.getBoolean("automatic"))
        assertTrue(a.getBoolean("manualOnly"))
    }

    @Test fun floorAndMissingMapNeverInventTargets() {
        val a = MapSuggestionPolicy.decide(listOf(group(-0.12)), map(101))
        assertEquals(100, a.getJSONArray("cells").getJSONObject(0).getInt("target"))
        assertEquals("SEM_EVIDENCIA", MapSuggestionPolicy.decide(listOf(group()), null).getString("verdict"))
        assertEquals("SEM_EVIDENCIA", MapSuggestionPolicy.decide(listOf(group()), map(-1)).getString("verdict"))
    }

    @Test fun insufficientEvidenceIsNotAlreadyOkAndHeatmapIsComplete() {
        val a = MapSuggestionPolicy.decide(listOf(group(local = false, reason = EquivalenceAttribution.FEW_VISITS)), map())
        assertEquals("SEM_EVIDENCIA", a.getString("verdict"))
        assertEquals(0, a.getJSONArray("cells").length())
        val heat = a.getJSONObject("equivalence").getJSONArray("cells")
        assertEquals(60, heat.length())
        assertTrue(heat.getJSONObject(0).isNull("errorPercent"))
        assertEquals(12.0, heat.getJSONObject(12).getDouble("errorPercent"), 1e-8)
        assertEquals("JA_OK", MapSuggestionPolicy.decide(listOf(group(0.02, false, EquivalenceAttribution.SAME_AS_CURVE)), map()).getString("verdict"))
    }

    @Test fun limitsBlockingCorrectionAreNotEvidenceOfEquivalence() {
        val a = MapSuggestionPolicy.decide(listOf(group(-0.12)), map(100))
        assertEquals("SEM_EVIDENCIA", a.getString("verdict"))
        assertEquals("LIMITE_DO_MAPA", a.getString("reason"))
        assertEquals(0, a.getJSONArray("cells").length())
    }

    @Test fun ecuReferenceWithoutRpmAndInvalidNumbersAreRejected() {
        val pairs = listOf(EquivalenceLedger.EvidencePair(5.0, 5.6, 850.0, ecuRef = true),
            EquivalenceLedger.EvidencePair(5.0, Double.POSITIVE_INFINITY, 850.0))
        assertEquals("SEM_EVIDENCIA", MapSuggestionPolicy.fromPairs(pairs, map()).getString("verdict"))
    }
    @Test fun previousSessionMapMustNeverProduceSuggestion() {
        val stale = map().put("sessionConfirmed", false)
        assertEquals("SEM_EVIDENCIA", MapSuggestionPolicy.decide(listOf(group()), stale).getString("verdict"))
        assertEquals(0, MapSuggestionPolicy.decide(listOf(group()), stale).getJSONArray("cells").length())
    }

    @Test fun overlappingGroupsHaveOneWeightedTargetPerPhysicalCell() {
        val groups = listOf(group(), group(0.08).copy(key = EquivalenceAttribution.Key(0, 2), pairs = 18))
        val a = MapSuggestionPolicy.decide(groups, map())
        val cells = a.getJSONArray("cells")
        val coordinates = (0 until cells.length()).map {
            val c = cells.getJSONObject(it)
            assertEquals(9.3, c.getDouble("deltaPercent"), 0.001)
            c.getInt("row") to c.getInt("column")
        }
        assertEquals(coordinates.size, coordinates.distinct().size)
        assertTrue(a.similar(MapSuggestionPolicy.decide(groups.reversed(), map())))
    }

}
