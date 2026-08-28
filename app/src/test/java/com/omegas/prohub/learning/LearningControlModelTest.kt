package com.omegas.prohub.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningControlModelTest {
    @Test
    fun `perfil equilibrado reproduz a politica padrao`() {
        val payload = JSONObject()
            .put("rpm", 2)
            .put("map", 2)
            .put("petrol", 2)
            .put("pressure", 2)
            .put("collection", 2)
            .put("minimumWaterC", 55)

        val applied = LearningControlModel.apply(payload, LearningTolerancePolicy())

        assertEquals(LearningTolerancePolicy(), applied.policy)
        assertEquals(55, applied.minimumWaterC)
    }

    @Test
    fun `perfil rigoroso reduz tolerancias e aumenta observacao`() {
        val payload = JSONObject()
            .put("rpm", 0)
            .put("map", 0)
            .put("petrol", 0)
            .put("pressure", 0)
            .put("collection", 0)
            .put("minimumWaterC", 65)

        val defaults = LearningTolerancePolicy()
        val applied = LearningControlModel.apply(payload, defaults)

        assertTrue(applied.policy.rpmOscillationMinimum < defaults.rpmOscillationMinimum)
        assertTrue(applied.policy.mapOscillationBar < defaults.mapOscillationBar)
        assertTrue(applied.policy.petrolOscillationPercent < defaults.petrolOscillationPercent)
        assertTrue(applied.policy.pressureOscillationBar < defaults.pressureOscillationBar)
        assertTrue(applied.policy.requiredFrames > defaults.requiredFrames)
        assertEquals(65, applied.minimumWaterC)
    }

    @Test
    fun `descricao e somente local e nunca automatica`() {
        val model = LearningControlModel.describe(LearningTolerancePolicy(), 55)

        assertTrue(model.getBoolean("ok"))
        assertFalse(model.getBoolean("automaticCalibration"))
        assertEquals(5, model.getJSONArray("levels").length())
        assertEquals(5, model.getJSONArray("controls").length())
    }
}

