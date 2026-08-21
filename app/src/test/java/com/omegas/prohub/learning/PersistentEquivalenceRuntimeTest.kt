package com.omegas.prohub.learning

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
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
}
