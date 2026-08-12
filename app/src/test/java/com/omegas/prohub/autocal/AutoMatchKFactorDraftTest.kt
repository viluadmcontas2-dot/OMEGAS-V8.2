package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchKFactorDraftTest {
    @Test
    fun `rascunho nasce com trinta pontos e nenhum selecionado`() {
        val draft = AutoMatchKFactorDraftPlanner.create(analysis(), nowMs = 100L)
        assertEquals(30, draft.points.size)
        assertEquals(0, draft.selectedCount)
        assertTrue(draft.points.none { it.selected })
        assertFalse(draft.toJson().getBoolean("automatic"))
        assertTrue(draft.toJson().getBoolean("requiresFreshCurveRead"))
        assertEquals(0.60, draft.toJson().getDouble("minimumFactor"), 0.0)
        assertEquals(KFactorProtocol.MAX_FACTOR, draft.toJson().getDouble("maximumFactor"), 0.0)
    }

    @Test
    fun `selecao e sempre explicita e revisavel`() {
        val original = AutoMatchKFactorDraftPlanner.create(analysis(), nowMs = 100L)
        val selected = AutoMatchKFactorDraftPlanner.select(original, 5, true)
        assertEquals(0, original.selectedCount)
        assertEquals(1, selected.selectedCount)
        assertTrue(selected.points[5].selected)
        assertEquals(1, selected.selectedPointsForReview().getJSONArray("points").length())
        assertTrue(selected.selectedPointsForReview().getBoolean("requiresReview"))
    }

    @Test
    fun `edicao local respeita minimo e limite natural Q14`() {
        val draft = AutoMatchKFactorDraftPlanner.create(analysis(), nowMs = 100L)
        val edited = AutoMatchKFactorDraftPlanner.setTargetFactor(draft, 3, 1.23456)
        assertEquals(KFactorProtocol.rawFromFactor(1.23456), edited.points[3].targetRaw)

        val high = AutoMatchKFactorDraftPlanner.setTargetFactor(draft, 3, 3.90)
        assertEquals(KFactorProtocol.rawFromFactor(3.90), high.points[3].targetRaw)

        val maximum = AutoMatchKFactorDraftPlanner.setTargetFactor(draft, 3, KFactorProtocol.MAX_FACTOR)
        assertEquals(KFactorProtocol.MAX_RAW, maximum.points[3].targetRaw)

        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchKFactorDraftPlanner.setTargetFactor(draft, 3, 0.59)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchKFactorDraftPlanner.setTargetFactor(draft, 3, 4.0)
        }
    }

    @Test
    fun `hash muda com selecao ou alvo`() {
        val draft = AutoMatchKFactorDraftPlanner.create(analysis(), nowMs = 100L)
        val selected = AutoMatchKFactorDraftPlanner.select(draft, 2, true)
        val edited = AutoMatchKFactorDraftPlanner.setTargetFactor(selected, 2, 1.1)
        assertNotEquals(draft.draftHash, selected.draftHash)
        assertNotEquals(selected.draftHash, edited.draftHash)
    }

    @Test
    fun `analise sem mul atual nao cria rascunho`() {
        val analysis = analysis()
        analysis.getJSONArray("points").getJSONObject(0).put("currentRaw", JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchKFactorDraftPlanner.create(analysis)
        }
    }

    @Test
    fun `analise marcada como firmware exato e rejeitada`() {
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchKFactorDraftPlanner.create(analysis().put("nativeFirmwareExact", true))
        }
    }

    private fun analysis(): JSONObject {
        val points = JSONArray()
        repeat(30) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("referenceTimeMs", 0.5 + index * 0.5)
                .put("currentRaw", KFactorProtocol.Q14_SCALE)
                .put("calculatedRaw", KFactorProtocol.Q14_SCALE + index)
                .put("origin", "CALCULATED"))
        }
        return JSONObject()
            .put("ok", true)
            .put("available", true)
            .put("nativeFirmwareExact", false)
            .put("snapshotHash", "snapshot-fixture")
            .put("points", points)
    }
}
