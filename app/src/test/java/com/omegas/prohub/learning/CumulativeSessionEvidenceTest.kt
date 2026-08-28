package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CumulativeSessionEvidenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `evidence database accumulates continuous memory across multiple session boundaries`() {
        val stateFile = temporary.root.resolve("cumulative-session-test.json")
        val store = SignalLearningStore(stateFile, RingLog())

        // --- SESSION 1 ---
        store.startSession()
        val decisionS1 = accepted(sample("s1-sample-1", 100L, 650L))
        store.ingest(telemetry(650L), decisionS1)
        val exportS1 = store.export("device-1")
        val regionsS1 = exportS1.getJSONArray("regions")
        assertEquals(1, regionsS1.length())
        assertEquals(1, regionsS1.getJSONObject(0).getInt("samples"))
        assertEquals(1, regionsS1.getJSONObject(0).getInt("session_count"))
        store.endSession("PHYSICAL_DISCONNECT_USB")

        // --- SESSION 2 ---
        store.startSession()
        val decisionS2 = accepted(sample("s2-sample-2", 700L, 1_250L))
        store.ingest(telemetry(1_250L), decisionS2)
        val exportS2 = store.export("device-1")
        val regionsS2 = exportS2.getJSONArray("regions")
        // Memory accumulates into region R1 rather than resetting or wiping
        assertEquals(1, regionsS2.length())
        assertEquals(2, regionsS2.getJSONObject(0).getInt("samples"))
        assertEquals(2, regionsS2.getJSONObject(0).getInt("session_count"))
        store.endSession("USER_NAVIGATION_STOP")

        // --- SESSION 3 ---
        store.startSession()
        val decisionS3 = accepted(sample("s3-sample-3", 1_300L, 1_850L))
        store.ingest(telemetry(1_850L), decisionS3)
        val exportS3 = store.export("device-1")

        // Verify lifetime continuous evidence accumulation across all 3 sessions
        val regionsS3 = exportS3.getJSONArray("regions")
        assertEquals(1, regionsS3.length())
        assertEquals(3, regionsS3.getJSONObject(0).getInt("samples"))
        assertEquals(3, regionsS3.getJSONObject(0).getInt("session_count"))

        // Verify sessions list acts as date/timestamp organization metadata
        val sessionsArray = exportS3.getJSONArray("sessions")
        assertTrue("Sessions array contains physical session history", sessionsArray.length() >= 3)
        for (i in 0 until sessionsArray.length()) {
            val sessionObj = sessionsArray.getJSONObject(i)
            assertNotNull(sessionObj.getString("id"))
            assertTrue(sessionObj.getLong("started_at") > 0L)
            assertNotNull(sessionObj.getString("end_reason"))
        }

        // Verify lifetime sample statistics persist and accumulate cumulatively
        assertTrue(exportS3.getBoolean("cumulativeEvidencePreserved"))
        assertEquals(3L, exportS3.getLong("lifetimeIndependentSamples"))
    }

    @Test
    fun `session metadata records start end timestamps and reason while preserving region evidence`() {
        val stateFile = temporary.root.resolve("session-metadata-test.json")
        val store = SignalLearningStore(stateFile, RingLog())

        val startStatus = store.startSession()
        val sessionId1 = startStatus.getJSONObject("session_summary").getString("session_id")
        assertTrue(sessionId1.isNotBlank())

        store.ingest(telemetry(650L), accepted(sample("s1", 100L, 650L)))
        val endStatus = store.endSession("POWER_OFF")

        val endedSessionSummary = endStatus.getJSONObject("session_summary")
        assertEquals("POWER_OFF", endedSessionSummary.getString("end_reason"))
        assertTrue(endedSessionSummary.getLong("ended_at") > 0L)

        // Starting session 2 creates new session metadata entry while preserving existing memory
        store.startSession()
        val exported = store.export("device-1")
        val memory = exported.getJSONObject("memory")
        assertTrue(memory.getBoolean("available"))
        assertEquals(1, exported.getJSONArray("regions").length())
    }

    private fun accepted(sample: MotorSample): SampleDecision {
        val cell = LearningGridProjection.cellFor(sample.rpm, sample.petrolMs)
        return SampleDecision.accepted(sample).copy(
            cellKey = cell.getString("key"),
            cellRow = cell.getInt("row"),
            cellColumn = cell.getInt("column"),
        )
    }

    private fun sample(
        id: String,
        start: Long,
        end: Long,
        petrolMs: Double = 4.0,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = start,
        endedAtElapsedMs = end,
        fuel = fuel,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = 30.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
    )

    private fun diagnostics() = SampleDiagnostics(
        frameCount = LearningTolerancePolicy().requiredFrames,
        durationMs = 550L,
        medianIntervalMs = 50L,
        waterCenterC = 80.0,
        minimumWaterC = 55,
        rpmCenterShift = 0.0,
        rpmCenterLimit = 62.5,
        rpmOscillation = 0.0,
        rpmOscillationLimit = 125.0,
        mapCenterShift = 0.0,
        mapCenterLimit = 0.025,
        mapOscillation = 0.0,
        mapOscillationLimit = 0.05,
        petrolCenterShift = 0.0,
        petrolCenterLimit = 0.24,
        petrolOscillationRatio = 0.0,
        petrolOscillationLimit = 0.15,
        pressureCenterShift = 0.0,
        pressureCenterLimit = 0.04,
        pressureOscillation = 0.0,
        pressureOscillationLimit = 0.08,
    )

    private fun telemetry(at: Long, petrolMs: Double = 4.0) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = Mp48Fuel.PETROL,
        state = Mp48Fuel.PETROL.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}

