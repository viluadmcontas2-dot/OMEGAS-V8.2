package com.omegas.prohub.service

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbSessionTransitionPolicyTest {
    @Test
    fun `first physical session is connected`() {
        assertEquals(
            UsbSessionTransition.CONNECTED,
            UsbSessionTransitionPolicy.classify(false, 0L, true, 4L),
        )
    }

    @Test
    fun `same connected generation is not a transition`() {
        assertEquals(
            UsbSessionTransition.NONE,
            UsbSessionTransitionPolicy.classify(true, 4L, true, 4L),
        )
    }

    @Test
    fun `true to true with a new session id is a physical generation change`() {
        assertEquals(
            UsbSessionTransition.GENERATION_CHANGED,
            UsbSessionTransitionPolicy.classify(true, 4L, true, 5L),
        )
    }

    @Test
    fun `disconnect closes the active generation`() {
        assertEquals(
            UsbSessionTransition.DISCONNECTED,
            UsbSessionTransitionPolicy.classify(true, 5L, false, 0L),
        )
    }
}
