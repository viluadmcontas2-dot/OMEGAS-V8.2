package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStoreEquivalenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun cng_is_retained_before_petrol_and_compared_when_reference_arrives() {
        val store = SignalLearningStore(
            temporary.root.resolve("equivalence-${System.nanoTime()}.json"),
            RingLog(),
        )
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng", 100L, 650L, Mp48Fuel.CNG, 3.30)),
            )
            val beforePetrol = store.export("test").getJSONObject("primaryEquivalence")
            assertTrue(beforePetrol.getDouble("cngWeight") > 0.0)
            assertEquals(0.0, beforePetrol.getDouble("petrolWeight"), 0.0)
            assertFalse(beforePetrol.getBoolean("comparable"))

            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol", 700L, 1_250L, Mp48Fuel.PETROL, 3.00)),
            )
            val afterPetrol = store.export("test").getJSONObject("primaryEquivalence")
            assertTrue(afterPetrol.getDouble("petrolWeight") > 0.0)
            assertTrue(afterPetrol.getBoolean("comparable"))
            assertEquals(3.00, afterPetrol.getDouble("referenceMs"), 1e-9)
            assertEquals(3.30, afterPetrol.getDouble("cngMs"), 1e-9)
            assertEquals(0.10, afterPetrol.getDouble("errorFraction"), 1e-9)
            assertTrue(afterPetrol.getDouble("uncertaintyFraction") > 0.0)
        } finally {
            store.close()
        }
    }

    @Test
    fun current_primary_equivalence_does_not_reuse_stale_pair_in_cng_only_territory() {
        val store = SignalLearningStore(temporary.root.resolve("local-comparability.json"), RingLog())
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol-a", 100L, 650L, Mp48Fuel.PETROL, 3.00)),
            )
            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng-a", 700L, 1_250L, Mp48Fuel.CNG, 3.30)),
            )
            assertTrue(store.export("test").getJSONObject("primaryEquivalence").getBoolean("comparable"))

            store.ingest(
                telemetry(at = 1_850L, fuel = Mp48Fuel.CNG, petrolMs = 4.00),
                SampleDecision.accepted(
                    sample(
                        id = "cng-only-b",
                        start = 1_300L,
                        end = 1_850L,
                        fuel = Mp48Fuel.CNG,
                        petrolMs = 4.00,
                        rpm = 3_800.0,
                        mapBar = 1.10,
                    ),
                ),
            )

            val local = store.export("test").getJSONObject("primaryEquivalence")
            assertFalse(
                "A previous gasoline/CNG pair must not make new CNG-only territory look locally comparable",
                local.getBoolean("comparable"),
            )
            assertFalse(local.has("referenceMs"))
            assertFalse(local.has("cngMs"))
            assertFalse(local.has("actionable"))
        } finally {
            store.close()
        }
    }

    @Test
    fun hard_zero_state_hides_cached_local_equivalence_without_erasing_surface() {
        val store = SignalLearningStore(temporary.root.resolve("hard-zero-local-status.json"), RingLog())
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol-a", 100L, 650L, Mp48Fuel.PETROL, 3.00)),
            )
            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng-a", 700L, 1_250L, Mp48Fuel.CNG, 3.30)),
            )
            assertTrue(store.export("test").getJSONObject("primaryEquivalence").getBoolean("comparable"))

            store.ingest(
                telemetry(at = 1_300L, fuel = Mp48Fuel.ENGINE_OFF, petrolMs = 0.0),
                SampleDecision.transition(state = "ENGINE_OFF", reason = "Motor parado"),
            )
            val stopped = store.export("test").getJSONObject("primaryEquivalence")
            assertFalse("Engine-off is a physical hard-zero and cannot expose cached action", stopped.getBoolean("comparable"))
            assertFalse(stopped.has("actionable"))

            store.ingest(
                telemetry(at = 1_900L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng-return", 1_350L, 1_900L, Mp48Fuel.CNG, 3.30)),
            )
            assertTrue(
                "Hard-zero hides current status but must not erase the bounded gasoline/CNG surface",
                store.export("test").getJSONObject("primaryEquivalence").getBoolean("comparable"),
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun weighted_fuel_lanes_survive_store_restart_without_raw_sample_replay() {
        val state = temporary.root.resolve("equivalence-restart.json")
        val first = SignalLearningStore(state, RingLog())
        try {
            first.startSession()
            first.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng", 100L, 650L, Mp48Fuel.CNG, 3.30)),
            )
            first.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol", 700L, 1_250L, Mp48Fuel.PETROL, 3.00)),
            )
        } finally {
            first.close()
        }

        val restored = SignalLearningStore(state, RingLog())
        try {
            val primary = restored.export("test").getJSONObject("primaryEquivalence")
            assertTrue(primary.getDouble("petrolWeight") > 0.0)
            assertTrue(primary.getDouble("cngWeight") > 0.0)
            assertEquals("SPARSE_MOMENTS_ONLY", primary.getString("persistenceRepresentation"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun advisor_is_driven_by_bounded_equivalence_surface_not_legacy_region_matching() {
        val store = SignalLearningStore(temporary.root.resolve("advisor.json"), RingLog())
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol", 100L, 650L, Mp48Fuel.PETROL, 3.00)),
            )
            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng", 700L, 1_250L, Mp48Fuel.CNG, 3.30)),
            )

            repeat(200) {
                val exported = store.export("test")
                val advice = exported.getJSONObject("assistedCalibration")
                if (exported.optBoolean("advisorFresh", false) &&
                    advice.optString("primaryAuthority") == BoundedEquivalenceAdvisorSnapshot.AUTHORITY &&
                    advice.optInt("comparisonCount", 0) > 0
                ) {
                    assertEquals("BOUNDED_EQUIVALENCE_SURFACE", advice.getString("inputSource"))
                    assertFalse(advice.optBoolean("environmentGates", true))
                    return
                }
                Thread.sleep(10L)
            }
            fail("Advisor never published the bounded RPM+MAP/Tinj authority")
        } finally {
            store.close()
        }
    }

    @Test
    fun legacy_petrol_regions_seed_new_surface_once_after_upgrade() {
        val stateFile = temporary.root.resolve("legacy-petrol.json")
        val first = SignalLearningStore(stateFile, RingLog())
        try {
            first.startSession()
            first.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("legacy-petrol", 100L, 650L, Mp48Fuel.PETROL, 3.00)),
            )
        } finally {
            first.close()
        }

        val surfaceFile = File(stateFile.parentFile, "learning_equivalence_v1.json")
        assertTrue(surfaceFile.isFile)
        assertTrue(surfaceFile.delete())

        val upgraded = SignalLearningStore(stateFile, RingLog())
        val migratedWeight: Double
        try {
            val equivalence = upgraded.export("test").getJSONObject("primaryEquivalence")
            migratedWeight = equivalence.getDouble("petrolWeight")
            assertTrue(migratedWeight > 0.0)
            assertEquals(1, equivalence.getInt("legacySeededRegions"))
            assertEquals(0.0, equivalence.getDouble("cngWeight"), 0.0)
        } finally {
            upgraded.close()
        }

        val reopened = SignalLearningStore(stateFile, RingLog())
        try {
            val equivalence = reopened.export("test").getJSONObject("primaryEquivalence")
            assertEquals(migratedWeight, equivalence.getDouble("petrolWeight"), 1e-12)
            assertEquals(1, equivalence.getInt("legacySeededRegions"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun pressure_and_temperature_do_not_participate_in_primary_equivalence_weight() {
        val a = EquivalenceEvidenceWeight.from(diagnostics(pressureShift = 0.0, water = 80.0)).stability
        val b = EquivalenceEvidenceWeight.from(diagnostics(pressureShift = 99.0, water = -20.0)).stability
        assertEquals(a, b, 1e-12)
    }

    private fun sample(
        id: String,
        start: Long,
        end: Long,
        fuel: Mp48Fuel,
        petrolMs: Double,
        rpm: Double = 2_500.0,
        mapBar: Double = 0.60,
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
        quality = 1.0,
        classification = SampleClassification.STRONG,
        frameCount = 8,
        diagnostics = diagnostics(),
    )

    private fun diagnostics(pressureShift: Double = 0.0, water: Double = 80.0) = SampleDiagnostics(
        frameCount = 8,
        durationMs = 550L,
        medianIntervalMs = 50L,
        waterCenterC = water,
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
        pressureCenterShift = pressureShift,
        pressureCenterLimit = 0.04,
        pressureOscillation = pressureShift,
        pressureOscillationLimit = 0.08,
    )

    private fun telemetry(at: Long, fuel: Mp48Fuel, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 100 else 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
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
