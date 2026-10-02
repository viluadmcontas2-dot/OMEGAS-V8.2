package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol

/**
 * Accepts a grouped AutoCal read only when the ECU stayed in the same native
 * AutoMatch epoch for the whole bracket.
 *
 * The compact status exposes the native AutoMatch counter. Some fallback
 * firmware paths cannot expose nativeFlag13 and use -1; in that case the
 * counter remains the authoritative epoch discriminator.
 */
internal object NativeAutoCalEpochGuard {
    fun sameEpoch(
        before: AutoCalProtocol.NativeStatus,
        after: AutoCalProtocol.NativeStatus,
    ): Boolean {
        if (before.autoMatchCount != after.autoMatchCount) return false
        if (before.nativeFlag13 >= 0 && after.nativeFlag13 >= 0 &&
            before.nativeFlag13 != after.nativeFlag13
        ) return false
        return true
    }
}
