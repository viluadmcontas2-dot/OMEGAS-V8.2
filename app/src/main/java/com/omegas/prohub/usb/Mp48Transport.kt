package com.omegas.prohub.usb

/**
 * Minimal MP48 transport seam shared by the physical USB implementation and
 * deterministic headless SIL replay.
 *
 * This interface deliberately exposes only what the response-driven ECU engine
 * already consumes. It does not add scheduling, retries, learning or writer
 * behavior.
 */
interface Mp48Transport {
    val connected: Boolean

    fun purge(reason: String = "sincronização serial"): Boolean

    fun protocolTransaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1_800,
        purgeBefore: Boolean = true,
        expectedSessionId: Long = 0L,
    ): UsbProtocolReply
}
