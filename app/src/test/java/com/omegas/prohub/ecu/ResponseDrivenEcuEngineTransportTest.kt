package com.omegas.prohub.ecu

import com.omegas.prohub.usb.Mp48Transport
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ResponseDrivenEcuEngineTransportTest {
    @Test
    fun deterministicTransportAndClockDriveRealDecoderAndAnalyzer() {
        val payload = ByteArray(Mp48Protocol.TELEMETRY_PAYLOAD_SIZE).apply {
            putU16(0, 870)
            putU16(8, 1758)
            this[11] = 0x80.toByte()
            this[12] = 100.toByte()
            putU16(14, 900)
            this[16] = 80.toByte()
            putU16(17, 400)
        }
        val transport = FakeTransport(payload)
        val clock = FakeClock()
        val seen = CountDownLatch(1)
        var decoded: Mp48Telemetry? = null

        val engine = ResponseDrivenEcuEngine(
            usb = transport,
            log = RingLog(),
            onTelemetry = { telemetry, _, _ ->
                if (decoded == null) {
                    decoded = telemetry
                    seen.countDown()
                }
            },
            clock = clock,
        )
        engine.beginUsbSession(7L)
        assertTrue(engine.start())
        assertTrue("telemetry not observed", seen.await(2, TimeUnit.SECONDS))

        val frame = requireNotNull(decoded)
        assertEquals(870, frame.rpm)
        assertEquals(0.400, frame.mapBar, 0.000001)
        assertEquals(4.50048, frame.petrolMs, 0.000001)
        assertTrue(transport.requests.take(3).contentEquals(
            listOf(Mp48Protocol.CMD_INIT_1, Mp48Protocol.CMD_INIT_2, Mp48Protocol.CMD_IDENTIFY),
        ))

        engine.stop(graceful = false)
        engine.close()
    }

    private class FakeTransport(private val telemetry: ByteArray) : Mp48Transport {
        override var connected: Boolean = true
        val requests = mutableListOf<ByteArray>()

        override fun purge(reason: String): Boolean = true

        override fun protocolTransaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
        ): UsbProtocolReply {
            requests += request.copyOf()
            val payload = if (request.contentEquals(Mp48Protocol.CMD_TELEMETRY)) telemetry else byteArrayOf()
            return UsbProtocolReply(
                ok = true,
                status = Mp48Protocol.STATUS_ACK,
                payload = payload,
                request = request,
                echo = request,
            )
        }
    }

    private class FakeClock : Mp48RuntimeClock {
        private var now = 10_000L
        override fun elapsedRealtime(): Long = now++
        override fun sleep(ms: Long) { now += ms.coerceAtLeast(1L) }
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
