package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePetrolContextualPriorTest {
    private val policy = LearningTolerancePolicy(
        historicalRpmMinimum = 100.0,
        historicalRpmPercent = 5.0,
        historicalMapBar = 0.05,
        historicalTemperatureC = 6.0,
        referenceMaximumSpreadMs = 0.35,
    )

    @Test
    fun petrolAnchorWarmStartsOracleWithoutBecomingConventionalRegionOrVote() {
        val registry = NativeLearningAnchorRegistry()
        val anchor = anchor(fingerprint = "petrol-a", fuel = "PETROL", overlap = "7:10-14:PETROL")
        assertTrue(registry.upsert(anchor))

        val result = estimate(registry = registry)

        assertTrue(result.available)
        assertTrue(result.nativePriorUsed)
        assertEquals(1, result.nativePriorCount)
        assertEquals("CONTEXTUAL_PRIOR", result.stage)
        assertEquals("ECU_NATIVE_AUTOCAL_CONTEXTUAL_PRIOR", result.referenceSource)
        assertEquals(4.2, result.petrolTargetMs!!, 0.0)
        assertEquals(listOf("NATIVE_AUTOCAL:petrol-a"), result.regionIds)
        assertEquals(0, result.totalPetrolRegions)
        assertTrue(result.quality > 0.0)
        assertEquals(policy.referenceMaximumSpreadMs, result.spreadMs!!, 0.0)

        val context = result.selectedRegionContexts.single()
        assertEquals("ECU_NATIVE_AUTOCAL_CONTEXTUAL_PRIOR", context.source)
        val provenance = requireNotNull(context.provenance)
        assertEquals("7:10-14:PETROL", provenance.getString("overlapKey"))
        assertFalse(provenance.getBoolean("comparisonVote"))
        assertEquals(0.0, provenance.getDouble("effectiveComparisonWeight"), 0.0)
        assertFalse(provenance.getBoolean("directKTarget"))
        assertEquals(0.0, anchor.effectiveComparisonWeight, 0.0)
    }

    @Test
    fun cngAnchorNeverBecomesGasolineReference() {
        val registry = NativeLearningAnchorRegistry()
        assertTrue(registry.upsert(anchor(fingerprint = "cng-a", fuel = "GNV", overlap = "7:20-24:GNV")))

        val result = estimate(registry = registry)

        assertFalse(result.available)
        assertFalse(result.nativePriorUsed)
        assertEquals("NO_PETROL_REGIONS", result.reasonCode)
    }

    @Test
    fun conventionalLocalPetrolReferenceAlwaysWinsOverNativePrior() {
        val registry = NativeLearningAnchorRegistry()
        assertTrue(registry.upsert(anchor(fingerprint = "petrol-prior", fuel = "PETROL", overlap = "7:30-34:PETROL", petrolMs = 5.5)))
        val conventional = PetrolReferenceSelector.Region(
            id = "region-physical",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 80.0,
            petrolMs = 4.0,
            confidence = 0.95,
            sampleCount = 12,
        )

        val result = estimate(regions = listOf(conventional), registry = registry)

        assertTrue(result.available)
        assertFalse(result.nativePriorUsed)
        assertEquals("LEARNING_REGION", result.referenceSource)
        assertEquals(4.0, result.petrolTargetMs!!, 0.0)
        assertEquals(listOf("region-physical"), result.regionIds)
    }

    @Test
    fun duplicateOverlapCannotIncreasePriorCountOrScientificRevision() {
        val registry = NativeLearningAnchorRegistry()
        val first = anchor(fingerprint = "first", fuel = "PETROL", overlap = "7:40-44:PETROL")
        val duplicateWindow = anchor(fingerprint = "different-fingerprint", fuel = "PETROL", overlap = "7:40-44:PETROL")

        assertTrue(registry.upsert(first))
        assertFalse(registry.upsert(duplicateWindow))
        assertEquals(1, registry.snapshot().size)
        assertEquals(1L, registry.currentRevision())

        val result = estimate(registry = registry)
        assertTrue(result.available)
        assertEquals(1, result.nativePriorCount)
    }

    @Test
    fun clearingRegistryRemovesContextualPriorImmediately() {
        val registry = NativeLearningAnchorRegistry()
        assertTrue(registry.upsert(anchor(fingerprint = "petrol-clear", fuel = "PETROL", overlap = "7:50-54:PETROL")))
        assertTrue(estimate(registry = registry).nativePriorUsed)

        registry.clear()
        val after = estimate(registry = registry)

        assertFalse(after.available)
        assertFalse(after.nativePriorUsed)
        assertEquals("NO_PETROL_REGIONS", after.reasonCode)
    }

    @Test
    fun distantAnchorDoesNotFabricateLocalReference() {
        val registry = NativeLearningAnchorRegistry()
        assertTrue(
            registry.upsert(
                anchor(
                    fingerprint = "petrol-far",
                    fuel = "PETROL",
                    overlap = "7:60-64:PETROL",
                    rpm = 5_000,
                    mapBar = 1.20,
                ),
            ),
        )

        val result = estimate(registry = registry)
        assertFalse(result.available)
        assertFalse(result.nativePriorUsed)
        assertEquals("NO_PETROL_REGIONS", result.reasonCode)
    }

    @Test
    fun unrelatedRegistryCannotHijackScopedGasolineOracleAuthority() {
        val owner = NativeLearningAnchorRegistry()
        val unrelated = NativeLearningAnchorRegistry()
        assertTrue(owner.upsert(anchor("owner-anchor", "PETROL", "7:70-74:PETROL", petrolMs = 4.15)))
        assertTrue(unrelated.upsert(anchor("foreign-anchor", "PETROL", "8:80-84:PETROL", petrolMs = 7.90)))

        val ownerResult = NativePetrolPriorScope.withAnchors(owner.snapshot()) {
            // Criar/ler outro registry dentro do mesmo processo não pode mudar o input scoped.
            assertEquals(1, unrelated.snapshot().size)
            PetrolReferenceSelector.estimate(emptyList(), request(), policy)
        }

        assertTrue(ownerResult.nativePriorUsed)
        assertEquals(4.15, ownerResult.petrolTargetMs!!, 0.0)
        assertEquals(listOf("NATIVE_AUTOCAL:owner-anchor"), ownerResult.regionIds)

        val outsideScope = PetrolReferenceSelector.estimate(emptyList(), request(), policy)
        assertFalse(outsideScope.available)
        assertFalse(outsideScope.nativePriorUsed)
    }

    private fun estimate(
        regions: List<PetrolReferenceSelector.Region> = emptyList(),
        registry: NativeLearningAnchorRegistry,
    ): PetrolReferenceSelector.Result = NativePetrolPriorScope.withAnchors(registry.snapshot()) {
        PetrolReferenceSelector.estimate(regions, request(), policy)
    }

    private fun request() = PetrolReferenceSelector.Request(
        rpm = 2_000.0,
        mapBar = 0.50,
        waterC = 80.0,
    )

    private fun anchor(
        fingerprint: String,
        fuel: String,
        overlap: String,
        petrolMs: Double = 4.2,
        rpm: Int = 2_000,
        mapBar: Double = 0.50,
    ) = NativeLearningAnchor(
        fingerprint = fingerprint,
        calibrationEpoch = 1,
        sessionId = if (overlap.startsWith("8:")) 8L else 7L,
        snapshotId = "snapshot-$fingerprint",
        snapshotHash = "hash-$fingerprint",
        bandIndex = 4,
        zone = "NORMAL",
        counter = 8,
        threshold = 8,
        nativeValidity = true,
        correlationState = "CORRELATED",
        correlationConfidence = 0.90,
        rpmConfidence = 0.85,
        rpm = rpm,
        petrolOnCngMs = petrolMs,
        gasMsDiagnostic = null,
        mapBar = mapBar,
        fuel = fuel,
        firstTelemetrySequence = 10L,
        lastTelemetrySequence = 14L,
        matchedTelemetryFrames = 5,
        eventElapsedMs = 2_000L,
        correlatedFrameElapsedMs = 1_900L,
        lagMs = 100L,
        overlapKey = overlap,
    )
}
