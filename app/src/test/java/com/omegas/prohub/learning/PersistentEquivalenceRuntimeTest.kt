package com.omegas.prohub.learning

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentEquivalenceRuntimeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun json_encoding_never_holds_runtime_science_lock() {
        val encodingStarted = CountDownLatch(1)
        val releaseEncoding = CountDownLatch(1)
        val secondObserveFinished = CountDownLatch(1)
        val stateFile = File(temporary.root, "state.json")
        val runtime = PersistentEquivalenceRuntime(
            stateFile = stateFile,
            snapshotEncoder = { snapshot ->
                encodingStarted.countDown()
                releaseEncoding.await(2, TimeUnit.SECONDS)
                EquivalenceSurfaceCodec.encode(snapshot)
            },
        )
        try {
            runtime.observe(
                FuelLane.PETROL_REFERENCE,
                2_400.0,
                0.50,
                3.0,
                1.0,
                1.0,
                1L,
            )
            assertTrue("writer never started encoding", encodingStarted.await(2, TimeUnit.SECONDS))

            val worker = thread(start = true) {
                runtime.observe(
                    FuelLane.PETROL_REFERENCE,
                    2_480.0,
                    0.52,
                    3.1,
                    1.0,
                    1.0,
                    2L,
                )
                secondObserveFinished.countDown()
            }

            assertTrue(
                "telemetry science path blocked behind JSON encoding",
                secondObserveFinished.await(500, TimeUnit.MILLISECONDS),
            )
            releaseEncoding.countDown()
            worker.join(2_000)
        } finally {
            releaseEncoding.countDown()
            runtime.close()
        }
    }

    @Test
    fun legacy_petrol_seed_batch_is_atomic_idempotent_and_never_seeds_cng() {
        val stateFile = File(temporary.root, "seed-state.json")
        val seeds = listOf(
            PersistentEquivalenceRuntime.LegacyPetrolSeed(
                rpm = 2_400.0,
                mapBar = 0.50,
                meanTinjMs = 3.0,
                varianceMs2 = 0.01,
                quality = 0.8,
                persistedSupport = 8.0,
            ),
            PersistentEquivalenceRuntime.LegacyPetrolSeed(
                rpm = 3_000.0,
                mapBar = 0.70,
                meanTinjMs = 4.2,
                varianceMs2 = 0.04,
                quality = 0.7,
                persistedSupport = 5.0,
            ),
        )

        val first = PersistentEquivalenceRuntime(stateFile)
        val firstPetrolWeight: Double
        try {
            assertEquals(2, first.seedLegacyPetrolIfEmpty(seeds))
            firstPetrolWeight = first.totalWeight(FuelLane.PETROL_REFERENCE)
            assertTrue(firstPetrolWeight > 0.0)
            assertEquals(0.0, first.totalWeight(FuelLane.CNG_PETROL_OBSERVED), 0.0)
            assertEquals(0, first.seedLegacyPetrolIfEmpty(seeds))
            assertEquals(2, first.legacySeededRegions())
            assertTrue(first.flush(2_000L))
        } finally {
            first.close()
        }

        val reopened = PersistentEquivalenceRuntime(stateFile)
        try {
            assertEquals(0, reopened.seedLegacyPetrolIfEmpty(seeds))
            assertEquals(firstPetrolWeight, reopened.totalWeight(FuelLane.PETROL_REFERENCE), 1e-12)
            assertEquals(0.0, reopened.totalWeight(FuelLane.CNG_PETROL_OBSERVED), 0.0)
            assertEquals(2, reopened.legacySeededRegions())
        } finally {
            reopened.close()
        }
    }
}
