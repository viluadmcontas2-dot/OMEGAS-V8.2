package com.omegas.prohub.calibration

import com.omegas.prohub.model.HubStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationWriteSafetyPolicyTest {
    private fun safeStatus() = HubStatus(
        serviceRunning = true,
        engineRunning = true,
        engineReady = true,
        engineStuck = false,
        usbConnected = true,
        usbPermissionPending = false,
        rpm = 900,
        directTelemetryAgeMs = 250,
    )

    @Test
    fun `estado seguro libera somente a etapa manual de escrita`() {
        val decision = CalibrationWriteSafetyPolicy.evaluate(safeStatus())
        assertTrue(decision.allowed)
        assertNull(decision.reason)
        assertEquals("SAFE_TO_REVIEW_WRITE", decision.code)
    }

    @Test
    fun `cada fronteira insegura bloqueia a mutacao`() {
        val cases = listOf(
            safeStatus().copy(serviceRunning = false) to "SERVICE_UNAVAILABLE",
            safeStatus().copy(usbConnected = false) to "USB_DISCONNECTED",
            safeStatus().copy(usbPermissionPending = true) to "USB_PERMISSION_PENDING",
            safeStatus().copy(engineRunning = false) to "ENGINE_UNSAFE",
            safeStatus().copy(engineReady = false) to "ENGINE_UNSAFE",
            safeStatus().copy(engineStuck = true) to "ENGINE_UNSAFE",
            safeStatus().copy(directTelemetryAgeMs = -1) to "TELEMETRY_STALE",
            safeStatus().copy(directTelemetryAgeMs = 2_501) to "TELEMETRY_STALE",
        )
        cases.forEach { (status, code) ->
            val decision = CalibrationWriteSafetyPolicy.evaluate(status)
            assertFalse(code, decision.allowed)
            assertEquals(code, decision.code)
            assertTrue(decision.reason?.isNotBlank() == true)
        }
    }

    @Test
    fun `limites seguros permanecem inclusivos onde definido`() {
        assertTrue(CalibrationWriteSafetyPolicy.evaluate(
            safeStatus().copy(rpm = 6_500, directTelemetryAgeMs = 2_500),
        ).allowed)
    }

    @Test
    fun `rpm nao bloqueia uma escrita manual quando comunicacao permanece segura`() {
        val decision = CalibrationWriteSafetyPolicy.evaluate(safeStatus().copy(rpm = 4_500))

        assertTrue(decision.allowed)
        assertEquals("SAFE_TO_REVIEW_WRITE", decision.code)
    }
}
