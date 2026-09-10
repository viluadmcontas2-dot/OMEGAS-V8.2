package com.omegas.prohub.blue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evidence-authority battery derived from the documented gasoline -> CNG
 * paired-injection method. Petrol Inj. delta is primary; GNV STFT is an
 * optional witness; LTFT never votes on correction math.
 */
class BluePairedEvidenceAuthorityTest {
    private val revision = CalibrationRevision(0, 0)

    @Test
    fun `two-second gasoline to gnv pair produces eight percent primary deviation`() {
        val engine = BlueCausalEngine()
        val petrol = evidence(FuelKind.PETROL, 100_000L, 870.0, 0.45, 4.50)
        val cng = evidence(FuelKind.CNG, 102_000L, 872.0, 0.45, 4.86)

        val comparison = engine.reconcile(state(petrol = listOf(petrol), cng = listOf(cng))).single()

        assertEquals(4.50, comparison.petrolTargetMs, 1e-9)
        assertEquals(4.86, comparison.petrolOnCngMs, 1e-9)
        assertEquals(8.0, comparison.errorPercent, 1e-9)
    }

    @Test
    fun `gasoline history outside temporal pair window cannot become primary comparison`() {
        val engine = BlueCausalEngine()
        val oldPetrol = evidence(FuelKind.PETROL, 1_000L, 870.0, 0.45, 4.50)
        val cng = evidence(FuelKind.CNG, 120_000L, 870.0, 0.45, 4.86)

        assertTrue(engine.reconcile(state(petrol = listOf(oldPetrol), cng = listOf(cng))).isEmpty())
    }

    @Test
    fun `gasoline observed after gnv cannot be used as before-switch reference`() {
        val engine = BlueCausalEngine()
        val futurePetrol = evidence(FuelKind.PETROL, 103_000L, 870.0, 0.45, 4.50)
        val cng = evidence(FuelKind.CNG, 102_000L, 870.0, 0.45, 4.86)

        assertNull(engine.petrolReference(cng, listOf(futurePetrol)))
    }

    @Test
    fun `recent pre-switch gasoline wins over older same-region history`() {
        val engine = BlueCausalEngine()
        val oldPetrol = evidence(FuelKind.PETROL, 10_000L, 870.0, 0.45, 4.00)
        val recentPetrol = evidence(FuelKind.PETROL, 100_000L, 870.0, 0.45, 4.50)
        val cng = evidence(FuelKind.CNG, 102_000L, 870.0, 0.45, 4.86)

        val reference = engine.petrolReference(cng, listOf(oldPetrol, recentPetrol))!!

        assertEquals(4.50, reference.petrolMs, 1e-9)
        assertEquals(listOf(recentPetrol.id), reference.evidenceIds)
    }

    @Test
    fun `current gnv injection determines physical map address not gasoline denominator`() {
        val engine = BlueCausalEngine()
        val petrol = evidence(FuelKind.PETROL, 100_000L, 870.0, 0.45, 4.50)
        val cng = evidence(FuelKind.CNG, 102_000L, 870.0, 0.45, 4.86)
        val comparison = engine.reconcile(state(petrol = listOf(petrol), cng = listOf(cng))).single()

        val cell = BlueMapKAddressing.cell(comparison)

        assertEquals(4.50, comparison.petrolTargetMs, 1e-9)
        assertEquals(4.86, comparison.petrolOnCngMs, 1e-9)
        assertEquals(4.50, cell.getDouble("petrolBin"), 1e-9)
    }

    @Test
    fun `supporting gnv stft increases confidence without changing primary error or target math`() {
        val base = JSONObject()
            .put("available", true)
            .put("state", "PROPOSAL_READY")
            .put("errorPercent", 8.0)
            .put("correctionMultiplier", 1.08)
        val witness = witness(stft = 7.0, ltft = 18.0)

        val projected = BlueWitnessConfidence.project(
            baseJson = base,
            blueErrorPercent = 8.0,
            baseQuality = 0.70,
            witness = witness,
            expectedCalibrationState = "map-0:curve-0",
            expectedRpm = 870.0,
            expectedMapBar = 0.45,
            expectedPetrolOnCngMs = 4.86,
        )

        assertEquals("SUPPORTS", projected.getJSONObject("obdWitness").getString("state"))
        assertTrue(projected.getDouble("effectiveConfidence") > projected.getDouble("baseConfidence"))
        assertEquals(8.0, projected.getDouble("errorPercent"), 1e-9)
        assertEquals(1.08, projected.getDouble("correctionMultiplier"), 1e-9)
    }

    @Test
    fun `ltft value cannot change blue result when paired injection and stft are identical`() {
        val first = project(stft = 7.0, ltft = -20.0)
        val second = project(stft = 7.0, ltft = 20.0)

        assertEquals(first.getDouble("errorPercent"), second.getDouble("errorPercent"), 1e-9)
        assertEquals(first.getDouble("correctionMultiplier"), second.getDouble("correctionMultiplier"), 1e-9)
        assertEquals(first.getDouble("effectiveConfidence"), second.getDouble("effectiveConfidence"), 1e-9)
    }

    @Test
    fun `conflicting gnv stft keeps measurement but gates action for more evidence`() {
        val projected = project(stft = -7.0, ltft = 10.0)

        assertEquals("CONFLICTS", projected.getJSONObject("obdWitness").getString("state"))
        assertEquals(8.0, projected.getDouble("errorPercent"), 1e-9)
        assertEquals(1.08, projected.getDouble("correctionMultiplier"), 1e-9)
        assertFalse(projected.getBoolean("available"))
        assertEquals("OBD_CONFLICT_COLLECT_MORE", projected.getString("state"))
    }

    @Test
    fun `missing obd witness never erases a valid primary paired measurement`() {
        val base = JSONObject()
            .put("available", true)
            .put("state", "PROPOSAL_READY")
            .put("errorPercent", 8.0)
            .put("correctionMultiplier", 1.08)

        val projected = BlueWitnessConfidence.project(
            baseJson = base,
            blueErrorPercent = 8.0,
            baseQuality = 0.70,
            witness = null,
            expectedCalibrationState = "map-0:curve-0",
            expectedRpm = 870.0,
            expectedMapBar = 0.45,
            expectedPetrolOnCngMs = 4.86,
        )

        assertEquals("UNAVAILABLE", projected.getJSONObject("obdWitness").getString("state"))
        assertEquals(8.0, projected.getDouble("errorPercent"), 1e-9)
        assertTrue(projected.getBoolean("available"))
    }

    @Test
    fun `gnv stft cannot fabricate a fuel comparison without gasoline evidence`() {
        val engine = BlueCausalEngine()
        val cng = evidence(FuelKind.CNG, 102_000L, 870.0, 0.45, 4.86)

        assertTrue(engine.reconcile(state(petrol = emptyList(), cng = listOf(cng))).isEmpty())
    }

    private fun project(stft: Double, ltft: Double): JSONObject {
        val base = JSONObject()
            .put("available", true)
            .put("state", "PROPOSAL_READY")
            .put("errorPercent", 8.0)
            .put("correctionMultiplier", 1.08)
        return BlueWitnessConfidence.project(
            baseJson = base,
            blueErrorPercent = 8.0,
            baseQuality = 0.70,
            witness = witness(stft, ltft),
            expectedCalibrationState = "map-0:curve-0",
            expectedRpm = 870.0,
            expectedMapBar = 0.45,
            expectedPetrolOnCngMs = 4.86,
        )
    }

    private fun witness(stft: Double, ltft: Double): JSONObject = JSONObject()
        .put("state", "INSUFFICIENT")
        .put("calibrationState", "map-0:curve-0")
        .put("rpm", 870.0)
        .put("map_bar", 0.45)
        .put("petrol_ms", 4.86)
        .put("gnvStftPct", stft)
        .put("ltftPct", ltft)
        .put("quality", 0.90)

    private fun evidence(
        fuel: FuelKind,
        at: Long,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
    ) = FuelEvidence(
        id = "${fuel.name}-$at-$petrolMs",
        fuel = fuel,
        collectedAtMs = at,
        visitId = "${fuel.name}-$at",
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        quality = 0.95,
        cngRevision = if (fuel == FuelKind.CNG) revision else null,
    )

    private fun state(petrol: List<FuelEvidence>, cng: List<FuelEvidence>): BlueLearningState {
        val calibration = CalibrationState(
            revision = revision,
            curveK = List(30) { 1.0 },
            mapK = List(13) { List(12) { 100 } },
        )
        return BlueLearningState(
            sessionId = "paired-evidence-test",
            calibration = calibration,
            petrolEvidence = petrol,
            cngEvidenceByRevision = mapOf(revision to cng),
        )
    }
}
