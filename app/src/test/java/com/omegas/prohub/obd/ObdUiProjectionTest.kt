package com.omegas.prohub.obd

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdUiProjectionTest {
    @Test
    fun `zero valido nao vira ausencia de PID`() {
        val projected = ObdUiProjection.project(
            JSONObject()
                .put("mode", "local")
                .put("state", "CONECTADO")
                .put("connected", true)
                .put("updatedAt", 10_000L)
                .put("stft", 0.0)
                .put("ltft", JSONObject.NULL)
                .put("rpm", 900.0),
            nowMs = 10_500L,
        )
        assertEquals("VALIDO", projected.getString("state"))
        assertEquals(0.0, projected.getDouble("stftPct"), 0.0001)
        assertTrue(projected.getJSONObject("pidAvailability").getBoolean("stft"))
        assertFalse(projected.getJSONObject("pidAvailability").getBoolean("ltft"))
    }

    @Test
    fun `dado stale some em vez de parecer atual`() {
        val projected = ObdUiProjection.project(
            JSONObject()
                .put("mode", "local")
                .put("state", "CONECTADO")
                .put("connected", true)
                .put("updatedAt", 1_000L)
                .put("stft", 12.5)
                .put("rpm", 1_800.0),
            nowMs = 7_000L,
        )
        assertEquals("STALE", projected.getString("state"))
        assertTrue(projected.isNull("stftPct"))
        assertTrue(projected.isNull("rpm"))
    }

    @Test
    fun `conectado sem PID nao vira zero`() {
        val projected = ObdUiProjection.project(
            JSONObject()
                .put("mode", "local")
                .put("state", "CONECTADO")
                .put("connected", true)
                .put("updatedAt", 10_000L)
                .put("stft", JSONObject.NULL)
                .put("rpm", JSONObject.NULL),
            nowMs = 10_100L,
        )
        assertEquals("SEM_PID", projected.getString("state"))
        assertTrue(projected.isNull("stftPct"))
    }

    @Test
    fun `OBD nunca recebe autoridade de ECU ou Learning`() {
        val projected = ObdUiProjection.project(JSONObject().put("mode", "off"))
        assertTrue(projected.getBoolean("observationalOnly"))
        assertFalse(projected.getBoolean("ecuAuthority"))
        assertFalse(projected.getBoolean("learningAuthority"))
        assertFalse(projected.getBoolean("automaticCalibration"))
    }
}
