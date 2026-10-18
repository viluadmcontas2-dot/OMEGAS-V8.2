package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class MapSuggestionPolicyTest {
    private val session = 7L
    private fun map(k: Int = 150, sessionId: Long = session) = JSONObject().put("sessionConfirmed", true).put("complete", true)
        .put("sessionId", sessionId).put("rows", JSONArray(List(12) { List(12) { k } }))
    private fun decide(groups: List<EquivalenceAttribution.Group>, map: JSONObject?, currentSession: Long = session) =
        MapSuggestionPolicy.decide(groups, map, currentSession)
    private fun group(error: Double = 0.12, local: Boolean = true, reason: String = EquivalenceAttribution.LOCAL) =
        EquivalenceAttribution.Group(EquivalenceAttribution.Key(0, 1), 9, 3, 5.0, 5.0 * (1 + error),
            ln(1 + error), 12, 0.0, ln(1 + error), local, reason)

    @Test fun suggestionUsesNativeLimitsAndIsRepeatable() {
        val groups = listOf(group())
        val before = map(250)
        val a = decide(groups, before)
        assertEquals("SUGERIR", a.getString("verdict"))
        val cells = a.getJSONArray("cells")
        assertTrue(cells.length() > 0)
        repeat(cells.length()) { assertEquals(255, cells.getJSONObject(it).getInt("target")) }
        assertTrue(a.similar(decide(groups, before)))
        assertEquals(250, before.getJSONArray("rows").getJSONArray(0).getInt(0))
        assertFalse(a.getBoolean("automatic"))
        assertTrue(a.getBoolean("manualOnly"))
    }

    @Test fun floorAndMissingMapNeverInventTargets() {
        val a = decide(listOf(group(-0.12)), map(101))
        assertEquals(100, a.getJSONArray("cells").getJSONObject(0).getInt("target"))
        assertEquals("SEM_EVIDENCIA", decide(listOf(group()), null).getString("verdict"))
        assertEquals("SEM_EVIDENCIA", decide(listOf(group()), map(-1)).getString("verdict"))
    }

    @Test fun insufficientEvidenceIsNotAlreadyOkAndHeatmapIsComplete() {
        val a = decide(listOf(group(local = false, reason = EquivalenceAttribution.FEW_VISITS)), map())
        assertEquals("SEM_EVIDENCIA", a.getString("verdict"))
        assertEquals(0, a.getJSONArray("cells").length())
        val heat = a.getJSONObject("equivalence").getJSONArray("cells")
        assertEquals(60, heat.length())
        assertTrue(heat.getJSONObject(0).isNull("errorPercent"))
        assertEquals(12.0, heat.getJSONObject(12).getDouble("errorPercent"), 1e-8)
        assertEquals("JA_OK", decide(listOf(group(0.02, false, EquivalenceAttribution.SAME_AS_CURVE)), map()).getString("verdict"))
    }

    @Test fun limitsBlockingCorrectionAreNotEvidenceOfEquivalence() {
        val a = decide(listOf(group(-0.12)), map(100))
        assertEquals("SEM_EVIDENCIA", a.getString("verdict"))
        assertEquals("LIMITE_DO_MAPA", a.getString("reason"))
        assertEquals(0, a.getJSONArray("cells").length())
    }

    @Test fun invalidNumbersAreRejected() {
        val pairs = listOf(EquivalenceLedger.EvidencePair(5.0, Double.POSITIVE_INFINITY, 850.0))
        assertEquals("SEM_EVIDENCIA", decide(EquivalenceAttribution.classify(pairs, emptySet()), map()).getString("verdict"))
        val nan = group().copy(gasMs = Double.NaN)
        assertEquals("SEM_PARES", decide(listOf(nan), map()).getString("reason"))
    }
    @Test fun previousSessionMapMustNeverProduceSuggestion() {
        val stale = map().put("sessionConfirmed", false)
        assertEquals("SEM_EVIDENCIA", decide(listOf(group()), stale).getString("verdict"))
        assertEquals(0, decide(listOf(group()), stale).getJSONArray("cells").length())
        // Cache ainda diz "confirmado" (só volta a false na próxima conexão), mas é de outra sessão ou o USB caiu.
        val otherSession = map(sessionId = session - 1)
        assertEquals("MAPA_NAO_CONFIRMADO", decide(listOf(group()), otherSession).getString("reason"))
        assertEquals(0, decide(listOf(group()), otherSession).getJSONArray("cells").length())
        val disconnected = decide(listOf(group()), map(), currentSession = 0L)
        assertEquals("MAPA_NAO_CONFIRMADO", disconnected.getString("reason"))
        assertEquals(0, disconnected.getJSONArray("cells").length())
        assertEquals("MAPA_NAO_CONFIRMADO", decide(listOf(group()), map().apply { remove("sessionId") }).getString("reason"))
    }

    @Test fun concreteVerdictsWithSmallFixedInputs() {
        // Retratos pequenos e fixos com o veredito esperado (substitui o replay de corpus dentro do JUnit).
        val sugerir = decide(listOf(group(0.12)), map(150))
        assertEquals("SUGERIR", sugerir.getString("verdict"))
        assertEquals("RESIDUAL_LOCAL", sugerir.getString("reason"))
        val first = sugerir.getJSONArray("cells").getJSONObject(0)
        assertEquals(150, first.getInt("current"))
        assertTrue(first.getInt("target") > 150)
        assertEquals("SEM_PARES", decide(emptyList(), map()).getString("reason"))
        assertEquals("SEM_EVIDENCIA", decide(emptyList(), map()).getString("verdict"))
        val ok = decide(listOf(group(0.01, false, EquivalenceAttribution.SAME_AS_CURVE)), map())
        assertEquals("JA_OK", ok.getString("verdict"))
        assertEquals("SEM_RESIDUAL_LOCAL", ok.getString("reason"))
        assertEquals(0, ok.getJSONArray("cells").length())
    }

    @Test fun cellOutsideTheMapNeverThrows() {
        val far = group().copy(key = EquivalenceAttribution.Key(11, EquivalenceLedger.BANDS.size - 1))
        val out = decide(listOf(far), map())
        assertTrue(out.getString("verdict") in setOf("SUGERIR", "SEM_EVIDENCIA"))
    }

    @Test fun overlappingGroupsHaveOneWeightedTargetPerPhysicalCell() {
        val groups = listOf(group(), group(0.08).copy(key = EquivalenceAttribution.Key(0, 2), pairs = 18))
        val a = decide(groups, map())
        val cells = a.getJSONArray("cells")
        val coordinates = (0 until cells.length()).map {
            val c = cells.getJSONObject(it)
            assertEquals(9.3, c.getDouble("deltaPercent"), 0.001)
            c.getInt("row") to c.getInt("column")
        }
        assertEquals(coordinates.size, coordinates.distinct().size)
        assertTrue(a.similar(decide(groups.reversed(), map())))
    }

}
