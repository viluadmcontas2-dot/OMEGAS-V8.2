package com.omegas.prohub.ecu

import android.os.SystemClock

/**
 * Runtime clock seam for deterministic headless replay.
 *
 * Production keeps Android SystemClock semantics; tests/SIL may advance a
 * synthetic monotonic clock without wall-clock sleeps.
 */
interface Mp48RuntimeClock {
    fun elapsedRealtime(): Long
    fun sleep(ms: Long)
}

object AndroidMp48RuntimeClock : Mp48RuntimeClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun sleep(ms: Long) = SystemClock.sleep(ms)
}
