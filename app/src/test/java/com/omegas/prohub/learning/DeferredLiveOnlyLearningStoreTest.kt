package com.omegas.prohub.learning

import com.omegas.prohub.util.RingLog
import org.json.JSONArray
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

    @Test
    fun `native snapshots during restore replay once in causal order around calibration adjustment`() {
        val root = temporaryFolder.newFolder("runtime-autocal-causal")
        val releaseRestore = CountDownLatch(1)
        val loaderEntered = CountDownLatch(1)
        val store = DeferredLiveOnlyLearningStore(root, RingLog()) { runtimeRoot, log ->
            loaderEntered.countDown()
            releaseRestore.await(3, TimeUnit.SECONDS)
            restoreNormally(runtimeRoot, log)
        }

        assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))
        store.startSession()

        val beforeAdjustment = nativeSnapshot(
            snapshotId = "AUTOCAL-BEFORE",
            snapshotHash = "snapshot-before",
            rpm = 2100,
            firstSequence = 100L,
            lastSequence = 108L,
            observedAt = 2_000L,
        )
        val deferredBefore = store.importNativeSnapshot(beforeAdjustment)
        val duplicateBefore = store.importNativeSnapshot(JSONObject(beforeAdjustment.toString()))
        assertTrue(deferredBefore.getBoolean("ok"))
        assertTrue(deferredBefore.getBoolean("deferred"))
        assertTrue(duplicateBefore.getBoolean("duplicate"))

        val adjustment = store.onCalibrationAdjustment(
            JSONObject()
                .put("adjustmentId", "restore-causal-boundary")
                .put("calibrationType", "K_FACTOR")
                .put("humanConfirmed", true)
                .put("readbackValid", true)
                .put("newHash", "curve-after"),
        )
        assertTrue(adjustment.getBoolean("deferred"))

        val afterAdjustment = nativeSnapshot(
            snapshotId = "AUTOCAL-AFTER",
            snapshotHash = "snapshot-after",
            rpm = 2600,
            firstSequence = 200L,
            lastSequence = 208L,
            observedAt = 4_000L,
        )
        val deferredAfter = store.importNativeSnapshot(afterAdjustment)
        assertTrue(deferredAfter.getBoolean("ok"))
        assertTrue(deferredAfter.getBoolean("deferred"))

        val restoring = store.migrationStatus().getJSONObject("restore")
        assertEquals(3, restoring.getInt("pendingDeferredOperations"))
        assertEquals(2L, restoring.getLong("deferredNativeSnapshots"))
        assertEquals(1L, restoring.getLong("duplicateNativeSnapshots"))

        releaseRestore.countDown()
        assertTrue(waitUntilReady(store))

        val exported = store.export("test")
        val restore = exported.getJSONObject("restore")
        assertEquals(0, restore.getInt("pendingDeferredOperations"))
        assertEquals(2L, restore.getLong("replayedNativeSnapshots"))
        assertEquals(1L, restore.getLong("duplicateNativeSnapshots"))
        assertEquals(0L, restore.getLong("failedDeferredOperations"))

        val anchors = exported.getJSONArray("nativeLearningAnchors")
        assertEquals("pre-adjustment anchor must be superseded while post-adjustment anchor survives", 1, anchors.length())
        val survivor = anchors.getJSONObject(0)
        assertEquals("AUTOCAL-AFTER", survivor.getString("snapshotId"))
        assertEquals("snapshot-after", survivor.getString("snapshotHash"))
        assertEquals(2600, survivor.getInt("rpm"))
        assertFalse(survivor.getBoolean("comparisonVote"))
        assertEquals(0.0, survivor.getDouble("effectiveComparisonWeight"), 0.0)
        store.close()
    }

    private fun nativeSnapshot(
        snapshotId: String,
        snapshotHash: String,
        rpm: Int,
        firstSequence: Long,
        lastSequence: Long,
        observedAt: Long,
    ): JSONObject {
        val counts = JSONArray()
        val petrol = JSONArray()
        val map = JSONArray()
        repeat(18) { index ->
            counts.put(if (index == 4) 8 else 0)
            petrol.put(2000 + index)
            map.put(500 + index)
        }
        val event = JSONObject()
            .put("eventType", "NATIVE_BAND_MATURED")
            .put("nativeValidity", true)
            .put("sessionId", 9L)
            .put("snapshotId", snapshotId)
            .put("snapshotHash", snapshotHash)
            .put("fuel", "GNV")
            .put("bandIndex", 4)
            .put("zone", "NORMAL")
            .put("counter", 8)
            .put("threshold", 8)
            .put("previousObservedAtElapsedMs", observedAt - 1_000L)
            .put("observedAtElapsedMs", observedAt)
            .put("correlationState", "CORRELATED")
            .put("correlationConfidence", 0.80)
            .put("rpmConfidence", 0.75)
            .put("rpm", rpm)
            .put("correlatedPetrolMs", 4.20)
            .put("correlatedGasMs", 7.2)
            .put("correlatedMapBar", 0.55)
            .put("correlatedFuel", "GNV")
            .put("correlatedFrameElapsedMs", observedAt - 500L)
            .put("correlationLagMs", 500L)
            .put("firstTelemetrySequence", firstSequence)
            .put("lastTelemetrySequence", lastSequence)
            .put("matchedTelemetryFrames", (lastSequence - firstSequence + 1L).toInt())
            .put("overlapKey", "9:$firstSequence-$lastSequence:GNV")

        return JSONObject()
            .put("sessionId", 9L)
            .put("snapshotId", snapshotId)
            .put("snapshotHash", snapshotHash)
            .put("autoCalEnabled", 1)
            .put(
                "fields",
                JSONArray()
                    .put(nativeField("NUM_BUF_UPD_GAS", counts))
                    .put(nativeField("PETR_INJ_TBUF_GAS", petrol))
                    .put(nativeField("MNFLD_PRESS_BUF_GAS", map)),
            )
            .put("nativeMaturityEvents", JSONArray().put(event))
    }

    private fun nativeField(key: String, values: JSONArray): JSONObject = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("rawValues", values)

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
