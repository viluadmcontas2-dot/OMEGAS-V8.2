package com.omegas.prohub.autocal

import com.omegas.prohub.telemetry.RuntimeFreshness
import com.omegas.prohub.telemetry.RuntimeFuel
import com.omegas.prohub.telemetry.RuntimeTelemetryFrame
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationaryCalibrationProjectionTest {
    @Test
    fun rawNativeFlagNeverInventsStationaryActivitySemantics() {
        val frame = frame(RuntimeFuel.PETROL)
        listOf(0, 1, 127, 255).forEach { raw ->
            val projected = StationaryCalibrationProjection.project(
                monitorStatus = JSONObject()
                    .put("nativeFlag13", raw)
                    .put("calibrationIdentityReady", true),
                frame = frame,
            )

            assertEquals(
                NativeCalibrationFlow.AUTOMATIC_ECU_CALIBRATION_STATIONARY.name,
                projected.getString("flow"),
            )
            assertEquals("PRECONDITIONS_UNKNOWN", projected.getString("state"))
            assertEquals("NATIVE_ACTIVE_STATE_UNKNOWN", projected.getString("reason"))
            assertEquals("UNPROVEN", projected.getString("activeSignalEvidence"))
            assertTrue(projected.isNull("nativeActive"))
            assertTrue(projected.isNull("completionObserved"))
            assertTrue(projected.isNull("failureObserved"))
            assertFalse(projected.getBoolean("algorithmKnown"))
            assertFalse(projected.getBoolean("appAutomaticWrite"))
            assertFalse(projected.getBoolean("uiMaySimulateAlgorithm"))
        }
    }

    @Test
    fun missingRuntimePreconditionsRemainExplicitlyUnknown() {
        val projected = StationaryCalibrationProjection.project(
            monitorStatus = JSONObject(),
            frame = null,
        )

        assertEquals("PRECONDITIONS_UNKNOWN", projected.getString("state"))
        assertEquals("STATIONARY_PRECONDITIONS_INCOMPLETE", projected.getString("reason"))
        assertFalse(projected.getBoolean("inputsKnown"))
        assertEquals("UNKNOWN", projected.getString("mutationScope"))
    }

    @Test
    fun unknownFuelCannotPromoteStationaryState() {
        val projected = StationaryCalibrationProjection.project(
            monitorStatus = JSONObject()
                .put("nativeFlag13", 13)
                .put("calibrationIdentityReady", true),
            frame = frame(RuntimeFuel.UNKNOWN),
        )

        assertEquals("STATIONARY_PRECONDITIONS_INCOMPLETE", projected.getString("reason"))
        assertFalse(projected.getJSONObject("preconditions").getBoolean("fuelStateKnown"))
    }

    private fun frame(fuel: RuntimeFuel) = RuntimeTelemetryFrame(
        sequence = 10L,
        usbSessionId = 7L,
        capturedAtElapsedMs = 1_000L,
        rpm = 850,
        petrolMs = 4.2,
        gasMsDiagnostic = null,
        waterC = 85,
        gasTemperatureC = 60,
        gasPressureAbsBar = 2.0,
        mapBar = 0.40,
        pressureDiffBar = 1.60,
        fuel = fuel,
        plausible = true,
        freshness = RuntimeFreshness.CURRENT,
    )
}
