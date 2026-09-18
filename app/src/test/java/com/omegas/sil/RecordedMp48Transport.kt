package com.omegas.sil

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48RuntimeClock
import com.omegas.prohub.usb.Mp48Transport
import com.omegas.prohub.usb.UsbProtocolReply
import java.util.concurrent.atomic.AtomicLong

data class RecordedMp48Frame(
    val sequence: Long,
    val recordedAtMs: Long,
    val payload: ByteArray,
)

class SilRuntimeClock(initialMs: Long = 0L) : Mp48RuntimeClock {
    private val now = AtomicLong(initialMs)

    override fun elapsedRealtime(): Long = now.get()

    override fun sleep(ms: Long) {
        if (ms > 0L) now.addAndGet(ms)
    }

    fun advanceTo(elapsedMs: Long) {
        now.updateAndGet { current -> maxOf(current, elapsedMs) }
    }
}

class RecordedMp48Transport(
    frames: List<RecordedMp48Frame>,
    private val clock: SilRuntimeClock,
) : Mp48Transport {
    private val recorded = frames.sortedWith(compareBy<RecordedMp48Frame> { it.recordedAtMs }.thenBy { it.sequence })
    private var nextIndex = 0

    val consumedFrames: Int
        get() = synchronized(this) { nextIndex }

    override val connected: Boolean
        get() = true

    override fun purge(reason: String): Boolean = true

    override fun protocolTransaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        purgeBefore: Boolean,
        expectedSessionId: Long,
    ): UsbProtocolReply {
        return when {
            request.contentEquals(Mp48Protocol.CMD_TELEMETRY) -> nextTelemetry(request)
            request.contentEquals(Mp48Protocol.CMD_INIT_1) ||
                request.contentEquals(Mp48Protocol.CMD_INIT_2) ||
                request.contentEquals(Mp48Protocol.CMD_IDENTIFY) ||
                request.contentEquals(Mp48Protocol.CMD_DISCONNECT) -> ack(request)
            else -> UsbProtocolReply(
                ok = false,
                request = request.copyOf(),
                echo = request.copyOf(),
                error = "SIL unsupported MP48 request: " + request.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
            )
        }
    }

    @Synchronized
    private fun nextTelemetry(request: ByteArray): UsbProtocolReply {
        if (nextIndex >= recorded.size) {
            return UsbProtocolReply(
                ok = false,
                request = request.copyOf(),
                echo = request.copyOf(),
                error = "SIL corpus exhausted",
            )
        }
        val frame = recorded[nextIndex++]
        clock.advanceTo(frame.recordedAtMs)
        return UsbProtocolReply(
            ok = true,
            status = Mp48Protocol.STATUS_ACK,
            payload = frame.payload.copyOf(),
            request = request.copyOf(),
            echo = request.copyOf(),
            elapsedMs = 0L,
        )
    }

    private fun ack(request: ByteArray): UsbProtocolReply = UsbProtocolReply(
        ok = true,
        status = Mp48Protocol.STATUS_ACK,
        request = request.copyOf(),
        echo = request.copyOf(),
        elapsedMs = 0L,
    )
}
