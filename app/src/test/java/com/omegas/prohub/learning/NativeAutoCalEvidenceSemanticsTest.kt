package com.omegas.prohub.learning

import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONArray
import org.json.JSONObject

class NativeAutoCalEvidenceSemanticsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `native AutoCal evidence preserves typed band semantics without cross-field overwrite`() {
        val store = SignalLearningStore(
            temporary.root.resolve("native-semantics-${System.nanoTime()}.json"),
            RingLog(),
        )
        try {
            val snapshot = JSONObject()
                .put("snapshotId", "semantic-snapshot")
                .put(
                    "fields",
                    JSONArray()
                        .put(vector("NUM_BUF_UPD_GAS", 10))
                        .put(vector("PETR_INJ_TBUF_GAS", 1_000))
                        .put(vector("MNFLD_PRESS_BUF_GAS", 2_000))
                        .put(vector("MUL_ACT", 9_000)),
                )

            val imported = store.importNativeSnapshot(snapshot)
            assertTrue(imported.getBoolean("ok"))
            assertEquals(18, imported.getInt("importedBands"))

            val evidence = store.export("test").getJSONArray("nativeEcuEvidence")
            val band = (0 until evidence.length())
                .map { evidence.getJSONObject(it) }
                .single { it.getString("snapshotId") == "semantic-snapshot" && it.getInt("bandIndex") == 3 }

            assertEquals(13, band.getInt("count"))
            assertEquals(1_003, band.getInt("petrolTimeRaw"))
            assertTrue(band.isNull("cngTimeRaw"))
            assertEquals(2_003, band.getInt("mapRaw"))
            assertEquals(1.0, band.getDouble("coverageQuality"), 0.0001)
        } finally {
            store.close()
        }
    }

    private fun vector(key: String, base: Int): JSONObject {
        val raw = JSONArray()
        repeat(18) { band -> raw.put(base + band) }
        return JSONObject()
            .put("key", key)
            .put("status", "VALID")
            .put("rawValues", raw)
    }
}
