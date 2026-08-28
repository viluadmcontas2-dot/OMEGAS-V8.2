package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCalProjectionMemoTest {
    @Test
    fun thousandReadsOfSameSnapshotComputeProjectionOnce() {
        val memo = AutoCalProjectionMemo()
        var computations = 0
        repeat(1_000) {
            val result = memo.resolve("snapshot-a") {
                computations += 1
                JSONObject().put("state", "READY")
            }
            assertEquals("READY", result.getString("state"))
        }
        assertEquals(1, computations)
        assertEquals(1L, memo.recomputeCount)
    }

    @Test
    fun newMaterialSnapshotRecomputesExactlyOnceAndClearDropsOldAuthority() {
        val memo = AutoCalProjectionMemo()
        var computations = 0
        repeat(10) { memo.resolve("snapshot-a") { computations += 1; JSONObject().put("revision", 1) } }
        repeat(10) { memo.resolve("snapshot-b") { computations += 1; JSONObject().put("revision", 2) } }
        assertEquals(2, computations)
        assertEquals(2L, memo.recomputeCount)

        memo.clear()
        val afterClear = memo.resolve("snapshot-b") { computations += 1; JSONObject().put("revision", 3) }
        assertEquals(3, computations)
        assertEquals(1L, memo.recomputeCount)
        assertEquals(3, afterClear.getInt("revision"))
    }
}
