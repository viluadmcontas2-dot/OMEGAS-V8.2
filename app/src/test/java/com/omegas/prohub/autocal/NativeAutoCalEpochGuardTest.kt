package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoCalEpochGuardTest {
    @Test
    fun `same counter and native flag stays in one epoch`() {
        assertTrue(NativeAutoCalEpochGuard.sameEpoch(status(flag = 1, count = 2), status(flag = 1, count = 2)))
    }

    @Test
    fun `counter transition rejects a torn grouped read`() {
        assertFalse(NativeAutoCalEpochGuard.sameEpoch(status(flag = 1, count = 1), status(flag = 1, count = 2)))
    }

    @Test
    fun `known native flag transition rejects a torn grouped read`() {
        assertFalse(NativeAutoCalEpochGuard.sameEpoch(status(flag = 0, count = 2), status(flag = 1, count = 2)))
    }

    @Test
    fun `fallback unknown flag still uses the native counter`() {
        assertTrue(NativeAutoCalEpochGuard.sameEpoch(status(flag = -1, count = 2), status(flag = 1, count = 2)))
        assertFalse(NativeAutoCalEpochGuard.sameEpoch(status(flag = -1, count = 2), status(flag = 1, count = 3)))
    }

    private fun status(flag: Int, count: Int) = AutoCalProtocol.NativeStatus(
        nativeFlag13 = flag,
        autoMatchCount = count,
        rawPayload = ByteArray(14),
    )
}
