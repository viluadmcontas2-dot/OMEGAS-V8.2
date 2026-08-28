package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchDraftReviewValidatorTest {
    @Test
    fun `curva fresca valida somente pontos escolhidos`() {
        val draft = selectedDraft()
        val result = AutoMatchDraftReviewValidator.validate(draft, freshCurve())
        assertTrue(result.getBoolean("ok"))
        assertEquals(1, result.getInt("pointCount"))
        assertFalse(result.getBoolean("automatic"))
        assertFalse(result.getBoolean("writesStarted"))
        assertTrue(result.getBoolean("requiresCriticalConfirmation"))
        val point = result.getJSONArray("points").getJSONObject(0)
        assertEquals(3, point.getInt("index"))
        assertEquals(KFactorProtocol.Q14_SCALE, point.getInt("currentRaw"))
    }

    @Test
    fun `curva alterada invalida handoff`() {
        val curve = freshCurve()
        curve.getJSONArray("factorsRaw").put(3, KFactorProtocol.Q14_SCALE + 7)
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchDraftReviewValidator.validate(selectedDraft(), curve)
        }
    }

    @Test
    fun `curva de outra sessao nao e aceita`() {
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchDraftReviewValidator.validate(
                selectedDraft(),
                freshCurve().put("sessionConfirmed", false),
            )
        }
    }

    @Test
    fun `rascunho sem selecao nao abre revisao`() {
        val analysis = draftAnalysis()
        val draft = AutoMatchKFactorDraftPlanner.create(analysis, 100L)
        assertThrows(IllegalArgumentException::class.java) {
            AutoMatchDraftReviewValidator.validate(draft, freshCurve())
        }
    }

    private fun selectedDraft(): AutoMatchKFactorDraft {
        val draft = AutoMatchKFactorDraftPlanner.create(draftAnalysis(), 100L)
        return AutoMatchKFactorDraftPlanner.select(draft, 3, true)
    }

    private fun draftAnalysis(): JSONObject {
        val points = JSONArray()
        repeat(30) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("referenceTimeMs", 0.5 + index * 0.5)
                .put("currentRaw", KFactorProtocol.Q14_SCALE)
                .put("calculatedRaw", KFactorProtocol.Q14_SCALE + index + 1)
                .put("origin", "CALCULATED"))
        }
        return JSONObject()
            .put("ok", true)
            .put("available", true)
            .put("nativeFirmwareExact", false)
            .put("snapshotHash", "snapshot")
            .put("points", points)
    }

    private fun freshCurve(): JSONObject = JSONObject()
        .put("ok", true)
        .put("complete", true)
        .put("sessionConfirmed", true)
        .put("sessionId", 88L)
        .put("hash", "curve-hash")
        .put("axisRaw", JSONArray(IntArray(30) { 256 + it * 256 }.toList()))
        .put("factorsRaw", JSONArray(IntArray(30) { KFactorProtocol.Q14_SCALE }.toList()))
}
