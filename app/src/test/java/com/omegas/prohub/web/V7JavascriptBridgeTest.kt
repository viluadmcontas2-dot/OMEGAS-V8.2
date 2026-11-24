package com.omegas.prohub.web

import com.omegas.prohub.web.V7JavascriptBridge.WriteOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class V7JavascriptBridgeTest {
    @Test
    fun `ACK de mapa ou curva espera readback e liberação do writer`() {
        for (type in listOf("MAP_K", "K_FACTOR")) {
            val details = JSONObject().put("calibrationType", type).put("acked", true).put("readbackValid", false)
            val status = JSONObject().put("state", "BATCH_CONFIRMED").put("busy", true).put("details", details)
            assertEquals(WriteOutcome.PENDING, V7JavascriptBridge.writeOutcome(status))
            details.put("readbackValid", true)
            assertEquals(WriteOutcome.PENDING, V7JavascriptBridge.writeOutcome(status))
            status.put("busy", false)
            assertEquals(WriteOutcome.CONFIRMED, V7JavascriptBridge.writeOutcome(status))
            details.put("readbackValid", false)
            assertEquals(WriteOutcome.FAILED, V7JavascriptBridge.writeOutcome(status))
        }
    }

    @Test
    fun `reset neutro termina e erro da central encerra a espera`() {
        val status = JSONObject().put("state", "RESET_CONFIRMED").put("busy", false)
        assertEquals(WriteOutcome.CONFIRMED, V7JavascriptBridge.writeOutcome(status, allowNeutralReset = true))
        assertEquals(WriteOutcome.PENDING, V7JavascriptBridge.writeOutcome(status))
        status.put("state", "RESET_FAILED")
        assertEquals(WriteOutcome.FAILED, V7JavascriptBridge.writeOutcome(status, allowNeutralReset = true))
    }
}
