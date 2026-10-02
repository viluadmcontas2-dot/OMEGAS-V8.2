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
