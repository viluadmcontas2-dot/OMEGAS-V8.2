package com.omegas.sil

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.ResponseDrivenEcuEngine
import com.omegas.prohub.learning.MotorLearningMemory
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OmegasSilReplayTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun stableRecordedPetrolFramesTraverseRealEngineAnalyzerAndLearningMemory() {
        val frames = List(12) { index ->
            RecordedMp48Frame(
                sequence = index + 1L,
                recordedAtMs = 1_000L + index * 300L,
                payload = petrolPayload(rpm = 870, mapRaw = 400, petrolRaw = 1758),
            )
        }
        val clock = SilRuntimeClock()
        val transport = RecordedMp48Transport(frames, clock)
        val memory = MotorLearningMemory(temporary.newFile("sil-learning.json"), RingLog())
        memory.startSession()
        val latch = CountDownLatch(frames.size)
        var callbacks = 0

        val engine = ResponseDrivenEcuEngine(
            usb = transport,
            log = RingLog(),
            onTelemetry = { telemetry, decision, _ ->
                memory.ingest(telemetry, decision)
                callbacks += 1
                latch.countDown()
            },
            clock = clock,
        )
        engine.beginUsbSession(77L)
        assertTrue(engine.start())
        assertTrue("SIL did not consume all recorded frames", latch.await(3, TimeUnit.SECONDS))
        engine.stop(graceful = false)
        engine.close()
        memory.awaitPersistence()

        assertEquals(frames.size, callbacks)
        val exported = memory.export("sil-test")
        assertTrue("real learning memory did not create petrol evidence", exported.getJSONArray("regions").length() > 0)
        assertEquals(0, exported.getJSONArray("comparisons").length())
        memory.close()
    }

    private fun petrolPayload(rpm: Int, mapRaw: Int, petrolRaw: Int): ByteArray =
        ByteArray(Mp48Protocol.TELEMETRY_PAYLOAD_SIZE).apply {
            putU16(0, rpm)
            putU16(6, 0)
            putU16(8, petrolRaw)
            this[11] = 0x80.toByte()
            this[12] = 20.toByte()
            this[13] = 100.toByte()
            putU16(14, 1_500)
            this[16] = 80.toByte()
            putU16(17, mapRaw)
        }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
