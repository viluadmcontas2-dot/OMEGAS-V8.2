package com.omegas.prohub.learning

import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeferredLearningRestoreSafetyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unconfirmedAdjustmentCannotEvictDeferredNativeEvidence() {
        val root = temporaryFolder.newFolder("restore-safety")
        val releaseRestore = CountDownLatch(1)
        val loaderEntered = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }

        try {
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))
            repeat(64) { index ->
                val result = store.importNativeSnapshot(
                    JSONObject()
                        .put("sessionId", "session-A")
                        .put("snapshotId", "snapshot-$index"),
                )
                assertTrue(result.getBoolean("deferred"))
                assertFalse(result.optBoolean("duplicate", false))
            }

            val rejected = store.onCalibrationAdjustment(
                JSONObject()
                    .put("adjustmentId", "not-confirmed")
                    .put("humanConfirmed", false)
                    .put("readbackValid", false),
            )

            assertFalse(rejected.getBoolean("deferred"))
            assertEquals("UNCONFIRMED_CALIBRATION_UPDATE", rejected.getString("reasonCode"))

            val restore = store.statusJson().getJSONObject("restore")
            assertEquals(64, restore.getInt("pendingDeferredOperations"))
            assertEquals(0L, restore.getLong("pendingCalibrationAdjustments"))
            assertEquals(0L, restore.getLong("rejectedNativeSnapshots"))
        } finally {
            store.close()
            releaseRestore.countDown()
        }
    }

    @Test
    fun confirmedManualAdjustmentStillReceivesSafetyPriorityWhenQueueIsFull() {
        val root = temporaryFolder.newFolder("restore-confirmed-priority")
        val releaseRestore = CountDownLatch(1)
        val loaderEntered = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }

        try {
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))
            repeat(64) { index ->
                assertTrue(
                    store.importNativeSnapshot(
                        JSONObject()
                            .put("sessionId", "session-B")
                            .put("snapshotId", "snapshot-$index"),
                    ).getBoolean("deferred"),
                )
            }

            val accepted = store.onCalibrationAdjustment(
                JSONObject()
                    .put("adjustmentId", "confirmed-write")
                    .put("humanConfirmed", true)
                    .put("readbackValid", true)
                    .put("newHash", "map-after-write"),
            )

            assertTrue(accepted.getBoolean("deferred"))
            val restore = store.statusJson().getJSONObject("restore")
            assertEquals(64, restore.getInt("pendingDeferredOperations"))
            assertEquals(1L, restore.getLong("pendingCalibrationAdjustments"))
            assertEquals(1L, restore.getLong("rejectedNativeSnapshots"))
        } finally {
            store.close()
            releaseRestore.countDown()
        }
    }

    @Test
    fun failedRestoreNeverClaimsThatMaterialOperationsAreDeferred() {
        val root = temporaryFolder.newFolder("restore-failed")
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { _, _ ->
            throw IllegalStateException("forced restore failure")
        }

        repeat(100) {
            if (store.statusJson().optString("state") == DeferredLiveOnlyLearningStore.STATE_FAILED) return@repeat
            Thread.sleep(10L)
        }
        assertEquals(DeferredLiveOnlyLearningStore.STATE_FAILED, store.statusJson().getString("state"))

        val snapshot = store.importNativeSnapshot(
            JSONObject()
                .put("sessionId", "failed-session")
                .put("snapshotId", "never-replayed"),
        )
        assertFalse(snapshot.getBoolean("ok"))
        assertFalse(snapshot.getBoolean("deferred"))
        assertEquals(DeferredLiveOnlyLearningStore.STATE_FAILED, snapshot.getString("reasonCode"))

        val adjustment = store.onCalibrationAdjustment(
            JSONObject()
                .put("adjustmentId", "confirmed-after-failure")
                .put("humanConfirmed", true)
                .put("readbackValid", true),
        )
        assertFalse(adjustment.getBoolean("ok"))
        assertFalse(adjustment.getBoolean("deferred"))
        assertEquals(DeferredLiveOnlyLearningStore.STATE_FAILED, adjustment.getString("reasonCode"))

        val restore = store.statusJson().getJSONObject("restore")
        assertEquals(0, restore.getInt("pendingDeferredOperations"))
        assertEquals(0L, restore.getLong("pendingCalibrationAdjustments"))
        assertEquals(1L, restore.getLong("rejectedNativeSnapshots"))
        store.close()
    }

    @Test
    fun terminalRestoreFailureDrainsOperationsThatCanNoLongerReplay() {
        val root = temporaryFolder.newFolder("restore-terminal-drain")
        val loaderEntered = CountDownLatch(1)
        val releaseRestore = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { _, _ ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            throw IllegalStateException("forced failure after queueing")
        }

        try {
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))
            assertTrue(
                store.importNativeSnapshot(
                    JSONObject()
                        .put("sessionId", "terminal-session")
                        .put("snapshotId", "queued-before-failure"),
                ).getBoolean("deferred"),
            )
            assertTrue(
                store.onCalibrationAdjustment(
                    JSONObject()
                        .put("adjustmentId", "confirmed-before-failure")
                        .put("humanConfirmed", true)
                        .put("readbackValid", true),
                ).getBoolean("deferred"),
            )

            val pending = store.statusJson().getJSONObject("restore")
            assertEquals(2, pending.getInt("pendingDeferredOperations"))
            assertEquals(1L, pending.getLong("pendingCalibrationAdjustments"))

            releaseRestore.countDown()
            repeat(100) {
                if (store.statusJson().optString("state") == DeferredLiveOnlyLearningStore.STATE_FAILED) return@repeat
                Thread.sleep(10L)
            }

            val failed = store.statusJson()
            assertEquals(DeferredLiveOnlyLearningStore.STATE_FAILED, failed.getString("state"))
            val restore = failed.getJSONObject("restore")
            assertEquals(0, restore.getInt("pendingDeferredOperations"))
            assertEquals(0L, restore.getLong("pendingCalibrationAdjustments"))
            assertEquals(1L, restore.getLong("rejectedNativeSnapshots"))
            assertEquals(2L, restore.getLong("failedDeferredOperations"))
        } finally {
            releaseRestore.countDown()
            store.close()
        }
    }

    @Test
    fun closedStoreNeverAcceptsDeferredMaterialOperationsOrRegressesToFailed() {
        val root = temporaryFolder.newFolder("restore-closed")
        val loaderEntered = CountDownLatch(1)
        val releaseRestore = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }

        assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))
        store.close()
        releaseRestore.countDown()

        val snapshot = store.importNativeSnapshot(
            JSONObject()
                .put("sessionId", "closed-session")
                .put("snapshotId", "must-not-defer"),
        )
        assertFalse(snapshot.getBoolean("ok"))
        assertFalse(snapshot.getBoolean("deferred"))
        assertEquals(DeferredLiveOnlyLearningStore.STATE_CLOSED, snapshot.getString("reasonCode"))

        val adjustment = store.onCalibrationAdjustment(
            JSONObject()
                .put("adjustmentId", "confirmed-after-close")
                .put("humanConfirmed", true)
                .put("readbackValid", true),
        )
        assertFalse(adjustment.getBoolean("ok"))
        assertFalse(adjustment.getBoolean("deferred"))
        assertEquals(DeferredLiveOnlyLearningStore.STATE_CLOSED, adjustment.getString("reasonCode"))

        repeat(20) { Thread.sleep(5L) }
        val status = store.statusJson()
        assertEquals(DeferredLiveOnlyLearningStore.STATE_CLOSED, status.getString("state"))
        val restore = status.getJSONObject("restore")
        assertEquals(0, restore.getInt("pendingDeferredOperations"))
        assertEquals(0L, restore.getLong("pendingCalibrationAdjustments"))
    }

    private fun restoreNormally(root: File, log: RingLog): DeferredLiveOnlyLearningStore.RestoredLearning {
        val migration = LearningTelemetrySchemaMigration.prepare(root, log)
        val state = File(root, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
        return DeferredLiveOnlyLearningStore.RestoredLearning(
            migration = migration,
            store = LiveOnlyLearningStore(state, log),
        )
    }
}
