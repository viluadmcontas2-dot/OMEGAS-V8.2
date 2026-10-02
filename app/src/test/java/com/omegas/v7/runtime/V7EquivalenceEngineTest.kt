package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V7EquivalenceEngineTest {
    private val revision = CalibrationRevisionV7(0, 0)

    private fun runtime() = V7SessionRuntime(
        V7SessionState(
            sessionId = "equivalence",
            calibration = CalibrationStateV7(
                revision = revision,
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
                },
            ),
        ),
    )

    private fun petrol(
        id: String = "p1",
        visit: String = "petrol-visit",
        rpm: Double = 1_500.0,
        map: Double = 0.50,
        petrolMs: Double = 3.80,
    ) = EvidenceV7(
        id = id,
        fuel = FuelV7.PETROL,
        collectedAtMs = 20,
        visitId = visit,
        rpm = rpm,
        mapBar = map,
        petrolMs = petrolMs,
        quality = 0.90,
        cngRevision = null,
        waterC = 85.0,
    )

    private fun cng(
        id: String = "g1",
        visit: String = "cng-visit",
        rpm: Double = 1_510.0,
        map: Double = 0.51,
        petrolMs: Double = 4.20,
    ) = EvidenceV7(
        id = id,
        fuel = FuelV7.CNG,
        collectedAtMs = 10,
        visitId = visit,
        rpm = rpm,
        mapBar = map,
        petrolMs = petrolMs,
        quality = 0.90,
        cngRevision = revision,
        waterC = 86.0,
        gasC = 35.0,
        pressureDiffBar = 1.20,
    )

    @Test
    fun cng_first_is_preserved_and_reconciled_when_equivalent_petrol_arrives_later() {
        val runtime = runtime()
        runtime.addEvidence(cng())

        assertEquals(1, runtime.state.activeCngEvidence().size)
        assertEquals(0, runtime.state.petrolEvidence.size)

        runtime.addEvidence(petrol())

        val comparison = runtime.state.activeComparisons().single()
        assertEquals("cng-visit", comparison.cngVisitId)
        assertEquals(3.80, comparison.petrolTargetMs, 1e-9)
        assertEquals(4.20, comparison.petrolOnCngMs, 1e-9)
        assertEquals("INCREASE_CNG_DELIVERY", comparison.direction)
    }

    @Test
    fun petrol_first_and_cng_first_produce_the_same_physical_comparison() {
        val petrolFirst = runtime().also {
            it.addEvidence(petrol())
            it.addEvidence(cng())
        }.state.activeComparisons().single()
        val cngFirst = runtime().also {
            it.addEvidence(cng())
            it.addEvidence(petrol())
        }.state.activeComparisons().single()

        assertEquals(petrolFirst.petrolTargetMs, cngFirst.petrolTargetMs, 1e-9)
        assertEquals(petrolFirst.petrolOnCngMs, cngFirst.petrolOnCngMs, 1e-9)
        assertEquals(petrolFirst.direction, cngFirst.direction)
    }

    @Test
    fun injection_time_does_not_participate_in_reference_matching() {
        val runtime = runtime()
        runtime.addEvidence(petrol(petrolMs = 2.0))
        runtime.addEvidence(cng(petrolMs = 8.0))

        val comparison = runtime.state.activeComparisons().single()
        assertEquals(2.0, comparison.petrolTargetMs, 1e-9)
        assertEquals(8.0, comparison.petrolOnCngMs, 1e-9)
    }

    @Test
    fun practical_rpm_map_and_temperature_variation_still_compares() {
        val runtime = runtime()
        runtime.addEvidence(petrol(rpm = 1_500.0, map = 0.50).copy(waterC = 82.0))
        runtime.addEvidence(cng(rpm = 1_650.0, map = 0.56).copy(waterC = 88.0))

        assertEquals(1, runtime.state.activeComparisons().size)
    }

    @Test
    fun unknown_temperature_does_not_block_otherwise_equivalent_conditions() {
        val runtime = runtime()
        runtime.addEvidence(petrol().copy(waterC = EvidenceV7.UNKNOWN_TEMPERATURE_C))
        runtime.addEvidence(cng().copy(waterC = EvidenceV7.UNKNOWN_TEMPERATURE_C))

        assertEquals(1, runtime.state.activeComparisons().size)
    }

    @Test
    fun low_quality_evidence_is_collected_without_becoming_a_hard_gate() {
        val runtime = runtime()
        runtime.addEvidence(cng().copy(quality = 0.0))

        assertEquals(1, runtime.state.activeCngEvidence().size)
        assertTrue(runtime.state.activeComparisons().isEmpty())

        runtime.addEvidence(petrol().copy(quality = 0.0))

        val comparison = runtime.state.activeComparisons().single()
        assertEquals(0.0, comparison.quality, 1e-9)
    }

    @Test
    fun equal_injection_time_does_not_force_pair_when_rpm_and_map_are_far() {
        val runtime = runtime()
        runtime.addEvidence(petrol(rpm = 900.0, map = 0.20, petrolMs = 4.0))
        runtime.addEvidence(cng(rpm = 3_000.0, map = 0.90, petrolMs = 4.0))

        assertTrue(runtime.state.activeComparisons().isEmpty())
    }

    @Test
    fun one_cng_visit_creates_one_immutable_comparison_even_when_later_snapshot_looks_better() {
        val runtime = runtime()
        runtime.addEvidence(petrol())
        runtime.addEvidence(cng(id = "g-first", petrolMs = 4.1).copy(quality = 0.50))
        runtime.addEvidence(cng(id = "g-later", petrolMs = 4.3).copy(quality = 0.95, collectedAtMs = 30))

        val comparisons = runtime.state.activeComparisons()
        assertEquals(1, comparisons.size)
        assertEquals(4.1, comparisons.single().petrolOnCngMs, 1e-9)
        assertEquals(10L, comparisons.single().createdAtMs)
    }

    @Test
    fun later_petrol_reference_does_not_rewrite_an_existing_cng_comparison() {
        val runtime = runtime()
        runtime.addEvidence(petrol(id = "p-first", visit = "p-first", petrolMs = 3.80))
        runtime.addEvidence(cng(petrolMs = 4.20))
        val first = runtime.state.activeComparisons().single()
        assertEquals(3.80, first.petrolTargetMs, 1e-9)

        runtime.addEvidence(
            petrol(
                id = "p-later",
                visit = "p-later",
                petrolMs = 4.05,
            ).copy(collectedAtMs = 100),
        )

        val afterNewPetrol = runtime.state.activeComparisons().single()
        assertEquals(first, afterNewPetrol)
        assertEquals(3.80, afterNewPetrol.petrolTargetMs, 1e-9)
    }

    @Test
    fun pending_cng_visit_can_gain_its_first_comparison_when_petrol_reference_arrives() {
        val runtime = runtime()
        runtime.addEvidence(cng())
        assertTrue(runtime.state.activeComparisons().isEmpty())

        runtime.addEvidence(petrol())

        assertEquals(1, runtime.state.activeComparisons().size)
        assertEquals("cng-visit", runtime.state.activeComparisons().single().cngVisitId)
    }

    @Test
    fun comparison_and_physical_conditions_survive_snapshot_roundtrip() {
        val runtime = runtime()
        runtime.addEvidence(cng())
        runtime.addEvidence(petrol())

        val restored = V7SessionRuntime(
            V7SessionSnapshotCodec.decode(V7SessionSnapshotCodec.encode(runtime.state)),
        ).state

        assertEquals(1, restored.activeComparisons().size)
        assertEquals(85.0, restored.petrolEvidence.single().waterC, 1e-9)
        assertEquals(86.0, restored.activeCngEvidence().single().waterC, 1e-9)
        assertEquals(10L, restored.activeComparisons().single().createdAtMs)
    }
}
