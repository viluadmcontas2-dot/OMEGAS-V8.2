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
    fun `rpm nao bloqueia escrita quando a comunicacao esta saudavel`() {
        for (rpm in listOf(0, 900, 1_200, 2_500, 6_500)) {
            assertTrue(
                "RPM $rpm não pode ser usado como proxy de veículo em movimento",
                CalibrationWriteSafetyPolicy.evaluate(
                    safeStatus().copy(rpm = rpm, directTelemetryAgeMs = 2_500),
                ).allowed,
            )
        }
    }
}
