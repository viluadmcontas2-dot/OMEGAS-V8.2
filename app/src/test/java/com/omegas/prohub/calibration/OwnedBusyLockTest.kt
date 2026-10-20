package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedBusyLockTest {
    @Test fun onlyTheOwnerReleasesAndOnlyOnce() {
        val lock = OwnedBusyLock()
        val a = lock.tryAcquire()
        assertNotEquals(0L, a)
        assertEquals("ocupado recusa a segunda operação", 0L, lock.tryAcquire())
        assertTrue(lock.release(a))
        val b = lock.tryAcquire()
        assertNotEquals(0L, b)
        assertFalse("A liberando de novo não solta B", lock.release(a))
        assertFalse("token 0 nunca libera", lock.release(0L))
        assertTrue(lock.isBusy())
        assertTrue(lock.release(b))
        assertFalse(lock.isBusy())
    }
}
