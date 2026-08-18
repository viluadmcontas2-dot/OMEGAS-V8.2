package com.omegas.prohub.learning

import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeferredLiveOnlyLearningStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun constructorDoesNotWaitForPersistedLearningRestore() {
        val root = temporaryFolder.newFolder("runtime")
        val releaseRestore = CountDownLatch(1)
        val loaderEntered = CountDownLatch(1)

        val startedAt = System.nanoTime()
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }
        val constructorMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue("restore thread should have started", loaderEntered.await(1, TimeUnit.SECONDS))
        assertTrue("constructor must stay outside heavy restore", constructorMs < 500L)
        assertTrue(store.statusJson().getBoolean("restoring"))
        assertFalse(store.export("test").getBoolean("ok"))

        releaseRestore.countDown()
        assertTrue(waitUntilReady(store))
        assertTrue(store.export("test").getBoolean("ok"))
        store.close()
    }

    @Test
    fun `small medium and large persisted payloads do not enter hot constructor path`() {
        val sizes = listOf(
            "small" to 4 * 1024,
            "medium" to 512 * 1024,
            "large" to 5 * 1024 * 1024,
        )
        sizes.forEach { (label, bytes) ->
            val root = temporaryFolder.newFolder("runtime-benchmark-$label")
            File(root, "historical-payload.bin").writeBytes(ByteArray(bytes) { 0x41 })
            val releaseRestore = CountDownLatch(1)
            val loaderEntered = CountDownLatch(1)

            val startedAt = System.nanoTime()
            val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
                loaderEntered.countDown()
                releaseRestore.await(3, TimeUnit.SECONDS)
                restoreNormally(runtimeRoot, log)
            }
            val constructorMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertTrue("$label restore worker should start", loaderEntered.await(1, TimeUnit.SECONDS))
            assertTrue("$label history must stay out of hot startup ($constructorMs ms)", constructorMs < 500L)
            val status = store.startSession()
            assertTrue("$label should expose a usable restoring state", status.getBoolean("restoring"))
            assertTrue(status.getBoolean("ok"))

            releaseRestore.countDown()
            assertTrue(waitUntilReady(store))
            store.close()
        }
    }

    @Test
    fun confirmedCalibrationDuringRestoreIsDeferredBeforeReady() {
        val root = temporaryFolder.newFolder("runtime-write")
        val releaseRestore = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }

        val deferred = store.onCalibrationAdjustment(
            JSONObject()
                .put("adjustmentId", "confirmed-during-restore")
                .put("humanConfirmed", true)
                .put("readbackValid", true)
                .put("newHash", "after-write"),
        )
        assertFalse(deferred.getBoolean("ok"))
        assertTrue(deferred.getBoolean("deferred"))
        assertFalse(deferred.getBoolean("resetPerformed"))

        releaseRestore.countDown()
        assertTrue(waitUntilReady(store))
        val exported = store.export("test")
        assertTrue(exported.getBoolean("ok"))
        assertTrue(exported.optJSONObject("restore")?.getString("state") == DeferredLiveOnlyLearningStore.STATE_READY)
        store.close()
    }

    private fun restoreNormally(root: File, log: RingLog): DeferredLiveOnlyLearningStore.RestoredLearning {
        val migration = LearningTelemetrySchemaMigration.prepare(root, log)
        val state = File(root, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
        return DeferredLiveOnlyLearningStore.RestoredLearning(
            migration = migration,
            store = LiveOnlyLearningStore(state, log),
        )
    }

    private fun waitUntilReady(store: DeferredLiveOnlyLearningStore): Boolean {
        repeat(100) {
            if (store.statusJson().optString("state") != DeferredLiveOnlyLearningStore.STATE_RESTORING) {
                return store.statusJson().optJSONObject("restore")?.optString("state") == DeferredLiveOnlyLearningStore.STATE_READY
            }
            Thread.sleep(10L)
        }
        return false
    }
}
