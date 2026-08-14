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

class NativeLearningAnchorIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `native maturity imports once persists and never creates comparison vote`() {
        val state = temporary.root.resolve("native-anchor-${System.nanoTime()}.json")
        val first = SignalLearningStore(state, RingLog())
        try {
            first.startSession()
            val snapshot = snapshot()
            val imported = first.importNativeSnapshot(snapshot)
            val repeated = first.importNativeSnapshot(JSONObject(snapshot.toString()))
            val exported = first.export("test")

            assertEquals(1, imported.getInt("importedNativeAnchors"))
            assertEquals(0, repeated.getInt("importedNativeAnchors"))
            assertEquals(1, exported.getJSONArray("nativeLearningAnchors").length())
            assertEquals(0, exported.getJSONArray("comparisons").length())
            val anchor = exported.getJSONArray("nativeLearningAnchors").getJSONObject(0)
            assertEquals(1, anchor.getInt("calibrationEpoch"))
            assertEquals(9L, anchor.getLong("sessionId"))
            assertEquals("GNV", anchor.getString("fuel"))
            assertEquals(2450, anchor.getInt("rpm"))
            assertEquals(7.2, anchor.getDouble("gasMsDiagnostic"), 1e-9)
            assertEquals(1500L, anchor.getLong("correlatedFrameElapsedMs"))
            assertEquals(500L, anchor.getLong("lagMs"))
            assertTrue(anchor.getBoolean("nativeValidity"))
            assertFalse(anchor.getBoolean("comparisonVote"))
            assertFalse(anchor.getBoolean("automaticWrite"))
            assertEquals("omegas-learning-evidence-v6-v3", exported.getString("evidenceStateSchema"))
        } finally {
            first.close()
        }

        val restored = SignalLearningStore(state, RingLog())
        try {
            val exported = restored.export("test")
            assertEquals(1, exported.getJSONArray("nativeLearningAnchors").length())
            assertTrue(exported.getJSONObject("evidenceBudget").getInt("nativeAnchors") <= LearningEvidenceBudget.MAX_NATIVE_ANCHORS)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `unreliable native maturity stays context only and creates no anchor`() {
        val state = temporary.root.resolve("native-anchor-unreliable-${System.nanoTime()}.json")
        val store = SignalLearningStore(state, RingLog())
        try {
            val snapshot = snapshot().apply {
                getJSONArray("nativeMaturityEvents").getJSONObject(0)
                    .put("correlationState", "NO_RELIABLE_CORRELATION")
            }
            val result = store.importNativeSnapshot(snapshot)
            val exported = store.export("test")

            assertEquals(0, result.getInt("importedNativeAnchors"))
            assertEquals(0, exported.getJSONArray("nativeLearningAnchors").length())
            assertTrue(exported.getJSONArray("nativeEcuEvidence").length() > 0)
            assertEquals(0, exported.getJSONArray("comparisons").length())
        } finally {
            store.close()
        }
    }

    @Test
    fun `calibration epoch change supersedes previous native anchors`() {
        val state = temporary.root.resolve("native-anchor-reset-${System.nanoTime()}.json")
        val store = SignalLearningStore(state, RingLog())
        try {
            store.startSession()
            assertEquals(1, store.importNativeSnapshot(snapshot()).getInt("nativeAnchorCount"))
            val adjustment = store.onCalibrationAdjustment(
                JSONObject()
                    .put("adjustmentId", "test-epoch")
                    .put("newHash", "map-v2"),
            )
            assertEquals(2, adjustment.getInt("epoch"))
            assertEquals(0, store.export("test").getJSONArray("nativeLearningAnchors").length())
        } finally {
            store.close()
        }
    }

    private fun snapshot(): JSONObject {
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
            .put("snapshotId", "AUTOCAL-TEST")
            .put("snapshotHash", "snapshot-hash")
            .put("fuel", "GNV")
            .put("bandIndex", 4)
            .put("zone", "NORMAL")
            .put("counter", 8)
            .put("threshold", 8)
            .put("previousObservedAtElapsedMs", 1000L)
            .put("observedAtElapsedMs", 2000L)
            .put("correlationState", "CORRELATED")
            .put("correlationConfidence", 0.80)
            .put("rpmConfidence", 0.75)
            .put("rpm", 2450)
            .put("correlatedPetrolMs", 4.20)
            .put("correlatedGasMs", 7.2)
            .put("correlatedMapBar", 0.55)
            .put("correlatedFuel", "GNV")
            .put("correlatedFrameElapsedMs", 1500L)
            .put("correlationLagMs", 500L)
            .put("firstTelemetrySequence", 100L)
            .put("lastTelemetrySequence", 108L)
            .put("matchedTelemetryFrames", 9)

        return JSONObject()
            .put("snapshotId", "AUTOCAL-TEST")
            .put(
                "fields",
                JSONArray()
                    .put(field("NUM_BUF_UPD_GAS", counts))
                    .put(field("PETR_INJ_TBUF_GAS", petrol))
                    .put(field("MNFLD_PRESS_BUF_GAS", map)),
            )
            .put("nativeMaturityEvents", JSONArray().put(event))
    }

    private fun field(key: String, values: JSONArray): JSONObject = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("rawValues", values)
}
