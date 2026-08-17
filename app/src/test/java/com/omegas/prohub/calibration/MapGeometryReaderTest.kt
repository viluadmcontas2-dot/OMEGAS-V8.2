package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapGeometryReaderTest {
    @Test
    fun `dois eixos usam uma unica unit read only pinada a sessao`() {
        val time = intArrayOf(781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031)
        val rpm = intArrayOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)
        val scheduler = FakeScheduler(77L, listOf(reply(time), reply(rpm)))

        val result = MapGeometryReader(scheduler).readRaw(expectedSessionId = 77L)

        assertEquals(1, scheduler.unitCalls)
        assertEquals(0, scheduler.directTransactions)
        assertEquals(Mp48WorkClass.READ_ONLY, scheduler.observedWorkClass)
        assertEquals(77L, scheduler.observedExpectedSessionId)
        assertEquals(2, scheduler.unitRequests.size)
        assertArrayEquals(Mp48Protocol.readKPetrolAxis(), scheduler.unitRequests[0])
        assertArrayEquals(Mp48Protocol.readKRpmAxis(), scheduler.unitRequests[1])
        assertArrayEquals(time, result.timeAxisRaw)
        assertArrayEquals(rpm, result.rpmAxisRaw)
        assertEquals(77L, result.unitSessionId)
    }

    @Test
    fun `sessao diferente no inicio bloqueia antes da unit`() {
        val scheduler = FakeScheduler(78L, emptyList())

        assertThrows(IllegalStateException::class.java) {
            MapGeometryReader(scheduler).readRaw(expectedSessionId = 77L)
        }
        assertEquals(0, scheduler.unitCalls)
    }

    @Test
    fun `reconnect depois da unit descarta o resultado`() {
        val values = IntArray(12) { 1000 + it * 100 }
        val scheduler = FakeScheduler(
            session = 77L,
            replies = listOf(reply(values), reply(values)),
            sessionAfterUnit = 78L,
        )

        assertThrows(IllegalStateException::class.java) {
            MapGeometryReader(scheduler).readRaw(expectedSessionId = 77L)
        }
        assertEquals(1, scheduler.unitCalls)
    }

    private fun reply(raw: IntArray): UsbProtocolReply {
        val payload = ByteArray(raw.size * 2)
        raw.forEachIndexed { index, value ->
            payload[index * 2] = (value and 0xFF).toByte()
            payload[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return UsbProtocolReply(ok = true, status = Mp48Protocol.STATUS_ACK, payload = payload)
    }

    private class FakeScheduler(
        session: Long,
        replies: List<UsbProtocolReply>,
        private val sessionAfterUnit: Long? = null,
    ) : Mp48SerialScheduler {
        private val pending = ArrayDeque(replies)
        private var session = session
        var unitCalls = 0
        var directTransactions = 0
        var observedExpectedSessionId = -1L
        var observedWorkClass: Mp48WorkClass? = null
        val unitRequests = mutableListOf<ByteArray>()

        override fun isConnected(): Boolean = session > 0L
        override fun currentSessionId(): Long = session

        override fun transaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
        ): UsbProtocolReply {
            directTransactions += 1
            error("MapGeometryReader não deve usar transaction direta")
        }

        override fun <T> unit(
            reason: String,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
            waitTimeoutMs: Long,
            block: (Mp48SerialUnit) -> T,
        ): T {
            unitCalls += 1
            observedExpectedSessionId = expectedSessionId
            observedWorkClass = workClass
            val unitSession = session
            val result = block(object : Mp48SerialUnit {
                override val sessionId: Long = unitSession

                override fun transaction(
                    request: ByteArray,
                    reason: String,
                    timeoutMs: Int,
                    purgeBefore: Boolean,
                ): UsbProtocolReply {
                    unitRequests += request.copyOf()
                    return pending.removeFirst()
                }
            })
            sessionAfterUnit?.let { session = it }
            return result
        }
    }
}
