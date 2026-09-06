package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmConnectionStateTest {
    @Test
    fun `stalled rfcomm becomes retryable timeout`() {
        val state = ElmConnectionState(connectTimeoutMs = 12_000L)
        state.enter(ElmStage.RFCOMM, nowMs = 1_000L, detail = "Abrindo Bluetooth")

        val timed = state.onClock(nowMs = 13_001L)

        assertEquals(ElmStage.ERROR, timed.stage)
        assertEquals("RFCOMM_TIMEOUT", timed.errorCode)
        assertTrue(timed.retryable)
    }

    @Test
    fun `successful stages retain explicit diagnostic progress`() {
        val state = ElmConnectionState(connectTimeoutMs = 12_000L)
        state.enter(ElmStage.RFCOMM, 1_000L, "Abrindo Bluetooth")
        state.enter(ElmStage.ELM_INIT, 1_300L, "ELM respondeu")
        state.enter(ElmStage.PROTOCOL, 1_500L, "Negociando protocolo")
        val ready = state.enter(ElmStage.STFT_READY, 1_800L, "PID 0106 disponível")
        val live = state.enter(ElmStage.LIVE, 1_900L, "STFT ao vivo")

        assertEquals(ElmStage.STFT_READY, ready.stage)
        assertEquals(ElmStage.LIVE, live.stage)
        assertEquals("STFT ao vivo", live.detail)
        assertFalse(live.retryable)
    }

    @Test
    fun `explicit failure keeps stable error code and can retry`() {
        val state = ElmConnectionState()
        state.enter(ElmStage.ELM_INIT, 100L, "Inicializando")

        val failed = state.fail("ELM_NO_RESPONSE", "ELM não respondeu ao ATI", 500L)

        assertEquals(ElmStage.ERROR, failed.stage)
        assertEquals("ELM_NO_RESPONSE", failed.errorCode)
        assertEquals("ELM não respondeu ao ATI", failed.detail)
        assertTrue(failed.retryable)

        val retry = state.enter(ElmStage.RFCOMM, 700L, "Tentando novamente")
        assertEquals(ElmStage.RFCOMM, retry.stage)
        assertEquals("", retry.errorCode)
    }
}
