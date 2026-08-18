package com.omegas.prohub.ecu

import com.omegas.prohub.learning.LearningMutationAuthority
import com.omegas.prohub.learning.LearningMutationState
import com.omegas.prohub.learning.NativeAnchorTelemetryWindow
import com.omegas.prohub.learning.SampleDecision
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PartialWriteLearningIsolationTest {
    @Test
    fun `step1 then telemetry then step2 failure never reaches active science`() {
        LearningMutationAuthority.endPhysicalSession()
        val scheduler = Mp48BackpressureScheduler(PartialFailureUnitScheduler())

        assertThrows(IllegalStateException::class.java) {
            scheduler.unit(
                reason = "partial batch write",
                expectedSessionId = 77L,
                workClass = Mp48WorkClass.MANUAL_WRITE,
                telemetryAfter = true,
            ) { unit ->
                assertEquals(
                    LearningMutationState.QUARANTINED_MUTATION_WINDOW,
                    LearningMutationAuthority.current().state,
                )

                unit.transaction(byteArrayOf(0x01), "step1")

                val telemetryBetweenSteps = LearningMutationAuthority.gate(
                    SampleDecision.transition(
                        state = "ELIGIBLE_BEFORE_MUTATION",
                        reason = "fixture that would otherwise be considered by science",
                        learningEligible = true,
                        reasonCode = "FIXTURE_ELIGIBLE",
                    ),
                )
                assertEquals(
                    LearningMutationState.QUARANTINED_MUTATION_WINDOW.name,
                    telemetryBetweenSteps.state,
                )
                assertFalse(telemetryBetweenSteps.learningEligible)
                assertNull(telemetryBetweenSteps.sample)

                unit.transaction(byteArrayOf(0x02), "step2-fails")
            }
        }

        assertEquals(LearningMutationState.UNKNOWN, LearningMutationAuthority.current().state)
        assertEquals(77L, LearningMutationAuthority.current().usbSessionId)
        LearningMutationAuthority.endPhysicalSession()
    }

    private class PartialFailureUnitScheduler : Mp48SerialScheduler {
        override fun isConnected() = true
        override fun currentSessionId() = 77L

        override fun transaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
        ): UsbProtocolReply = error("outer transaction not used")

        override fun <T> unit(
            reason: String,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
            waitTimeoutMs: Long,
            block: (Mp48SerialUnit) -> T,
        ): T {
            var step = 0
            return block(object : Mp48SerialUnit {
                override val sessionId: Long = expectedSessionId

                override fun transaction(
                    request: ByteArray,
                    reason: String,
                    timeoutMs: Int,
                    purgeBefore: Boolean,
                ): UsbProtocolReply {
                    step += 1
                    if (step == 2) throw IllegalStateException("simulated step2 failure")
                    return UsbProtocolReply(ok = true, status = 0x53, payload = byteArrayOf())
                }
            })
        }

        override fun recentTelemetryFrames(
            fromElapsedMs: Long,
            toElapsedMs: Long,
        ): List<NativeAnchorTelemetryWindow.Frame> = emptyList()
    }
}
