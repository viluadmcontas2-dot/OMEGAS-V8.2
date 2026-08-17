package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeCalibrationReaderTest {
    @Test
    fun `session start le calibracao inteira em uma unit read only e ordem fixa`() {
        val replies = mutableListOf<UsbProtocolReply>()
        replies += ack(byteArrayOf(4))
        replies += ack(u16Payload(IntArray(30) { (it + 1) * 256 }))
        replies += ack(u16Payload(IntArray(30) { 0x4000 + it }))
        replies += ack(u16Payload(IntArray(12) { 781 + it }))
        replies += ack(u16Payload(IntArray(12) { 1000 + it * 500 }))
        repeat(13) { row -> replies += ack(ByteArray(12) { column -> (row * 12 + column).toByte() }) }
        replies += ack(u16Payload(IntArray(30) { 0x4000 + it }))
        replies += ack(byteArrayOf(4))
        val scheduler = FakeScheduler(77L, replies)

        val result = CompositeCalibrationReader(scheduler).readAtSessionStart(77L)

        assertEquals(1, scheduler.unitCalls)
        assertEquals(0, scheduler.directTransactions)
        assertEquals(Mp48WorkClass.READ_ONLY, scheduler.workClass)
        assertTrue(scheduler.telemetryAfter)
        assertEquals(CompositeCalibrationReader.WAIT_TIMEOUT_MS, scheduler.waitTimeoutMs)
        assertEquals(20, scheduler.requests.size)
        assertTrue(scheduler.requests[0].contentEquals(AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)))
        assertTrue(scheduler.requests[1].contentEquals(KFactorProtocol.readPetrolAxis()))
        assertTrue(scheduler.requests[2].contentEquals(KFactorProtocol.readFactors()))
        assertTrue(scheduler.requests[3].contentEquals(Mp48Protocol.readKPetrolAxis()))
        assertTrue(scheduler.requests[4].contentEquals(Mp48Protocol.readKRpmAxis()))
        repeat(13) { row -> assertTrue(scheduler.requests[5 + row].contentEquals(Mp48Protocol.readKRow(row))) }
        assertTrue(scheduler.requests[18].contentEquals(KFactorProtocol.readFactors()))
        assertTrue(scheduler.requests[19].contentEquals(AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)))
        assertEquals(13, result.mapRowsRaw.size)
        assertEquals(12, result.mapRowsRaw[12].size)
        assertTrue(result.generationCheck.stable)
        assertEquals(77L, result.usbSessionId)
    }

    private fun ack(payload: ByteArray) = UsbProtocolReply(ok = true, status = Mp48Protocol.STATUS_ACK, payload = payload)

    private fun u16Payload(values: IntArray): ByteArray = ByteArray(values.size * 2).also { out ->
        values.forEachIndexed { index, value ->
            out[index * 2] = (value and 0xFF).toByte()
            out[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
    }

    private class FakeScheduler(
        private val session: Long,
        replies: List<UsbProtocolReply>,
    ) : Mp48SerialScheduler {
        private val pending = ArrayDeque(replies)
        var unitCalls = 0
        var directTransactions = 0
        var workClass: Mp48WorkClass? = null
        var telemetryAfter = false
        var waitTimeoutMs = -1L
        val requests = mutableListOf<ByteArray>()

        override fun isConnected() = true
        override fun currentSessionId() = session
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean): UsbProtocolReply {
            directTransactions += 1
            error("CompositeCalibrationReader não deve usar transaction direta")
        }
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T {
            unitCalls += 1
            assertEquals(session, expectedSessionId)
            this.workClass = workClass
            this.telemetryAfter = telemetryAfter
            this.waitTimeoutMs = waitTimeoutMs
            return block(object : Mp48SerialUnit {
                override val sessionId: Long = session
                override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean): UsbProtocolReply {
                    requests += request.copyOf()
                    return pending.removeFirst()
                }
            })
        }
    }
}
