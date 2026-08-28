package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStoreEquivalenceVisitTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `overlapping CNG windows remain one Advisor visit until physical separation`() {
        val state = temporary.root.resolve("equivalence-visit-${System.nanoTime()}.json")
        val store = SignalLearningStore(state, RingLog())
        try {
            store.startSession()
            store.ingest(telemetry(650L), accepted(sample("p1", 100L, 650L, Mp48Fuel.PETROL, 2_400.0, 0.50, 4.00)))
            store.ingest(telemetry(1_250L), accepted(sample("p2", 700L, 1_250L, Mp48Fuel.PETROL, 2_480.0, 0.52, 4.00)))
            store.ingest(telemetry(1_850L), accepted(sample("p3", 1_300L, 1_850L, Mp48Fuel.PETROL, 2_560.0, 0.54, 4.00)))

            store.ingest(telemetry(2_550L), accepted(sample("c1", 2_000L, 2_550L, Mp48Fuel.CNG, 2_400.0, 0.50, 4.20)))
            store.ingest(telemetry(2_700L), accepted(sample("c2", 2_150L, 2_700L, Mp48Fuel.CNG, 2_480.0, 0.52, 4.20)))
            store.ingest(telemetry(3_550L), accepted(sample("c3", 3_000L, 3_550L, Mp48Fuel.CNG, 2_560.0, 0.54, 4.20)))
        } finally {
            store.close()
        }

        val persisted = temporary.root.resolve("learning_equivalence_v1.json")
        assertTrue(persisted.isFile)
        val snapshot = EquivalenceSurfaceCodec.decode(persisted.readText(Charsets.UTF_8))
        val cngRevisions = snapshot.nodes
            .filter { it.lane == FuelLane.CNG_PETROL_OBSERVED }
            .map { it.materialRevision }
            .toSet()

        assertEquals(
            "The overlapping c1/c2 windows are one correlated physical visit; c3 is independent",
            2,
            cngRevisions.size,
        )

        val bounded = BoundedEquivalenceAdvisorSnapshot.build(snapshot, epoch = 1)
        val comparisons = bounded.getJSONArray("comparisons")
        val visitIds = (0 until comparisons.length())
            .map { comparisons.getJSONObject(it).getString("visit_id") }
            .toSet()

        assertTrue(comparisons.length() > 0)
        assertEquals(
            "Correlated rolling windows must not manufacture independent Advisor visits",
            2,
            visitIds.size,
        )
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
        fuel: Mp48Fuel,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = start,
        endedAtElapsedMs = end,
        fuel = fuel,
        rpm = rpm,
        mapBar = mapBar,
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

    private fun telemetry(at: Long) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.0,
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
