package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TRAVA PERMANENTE (regra 17 do AGENTS.md, dono 2026-10-08): apagar ponto manualmente ("Reaprender N pontos")
 * nunca muda o armamento da limpeza automática: armada continua armada, desarmada pelo dono continua desarmada.
 */
class AutoCalManualDeleteKeepsAutoCleanupTest {
    private val coordinator = AutoIdleCleanupCoordinator(
        telemetry = object : Mp48TelemetryWindowSource {
            override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long) = emptyList<NativeAnchorTelemetryWindow.Frame>()
        },
        executeDelete = { _, _ -> JSONObject().put("ok", true) },
        autoCalEnabled = { 1 },
        sessionAgeMs = { 60_000L },
        canAct = { null },
        record = { _, _ -> },
        clock = { 100_000L },
        executor = { it.run() },
        currentSessionId = { 1L },
        onChanged = {},
    )

    private fun manualDelete() = JSONObject().put("action", "DELETE_POINT").put("outcome", "CONFIRMED").put("automatic", false)

    @Test
    fun `apagar ponto manual com a limpeza armada nao desarma`() {
        coordinator.onSessionChanged(1L)
        assertTrue(coordinator.setArmed(true, "teste").getBoolean("ok"))
        coordinator.onManualIntent("DELETE_POINT")
        assertTrue("intencao de apagar ponto nao desarma", coordinator.uiSummary().armed)
        coordinator.onManualMutation(manualDelete())
        assertTrue("recibo confirmado do apagamento manual nao desarma", coordinator.uiSummary().armed)
        coordinator.onManualMutation(manualDelete().put("outcome", "FAILED").put("mutationMayHaveStarted", true))
        assertTrue("falha com mutacao possivel do apagamento manual nao desarma", coordinator.uiSummary().armed)
        assertTrue(coordinator.automaticEnabled())
    }

    @Test
    fun `apagar ponto manual com a limpeza desligada pelo dono nao liga`() {
        coordinator.onSessionChanged(1L)
        coordinator.onManualIntent("DELETE_POINT")
        coordinator.onManualMutation(manualDelete())
        assertFalse(coordinator.uiSummary().armed)
    }

    @Test
    fun `outras acoes manuais continuam desarmando`() {
        coordinator.onSessionChanged(1L)
        coordinator.setArmed(true, "teste")
        coordinator.onManualMutation(JSONObject().put("action", "RESET_GAS").put("outcome", "CONFIRMED"))
        assertFalse(coordinator.uiSummary().armed)
    }

    @Test
    fun `a ponte nao chama a intencao manual para DELETE_POINT`() {
        val dir = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
        val text = File(dir, "com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").readText()
        val start = text.indexOf("fun executeNativeAction")
        val body = text.substring(start, text.indexOf("invalidateAnalysis()", start))
        assertTrue(
            "executeNativeAction so desarma quando a acao nao e DELETE_POINT",
            Regex("""!=\s*AutoCalNativeActionManager\.Action\.DELETE_POINT\s*\)\s*\{\s*activityRef[^\n]*onManualAutoCalIntent""").containsMatchIn(body),
        )
    }
}
