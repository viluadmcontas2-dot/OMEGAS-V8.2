package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 1: política pura do apagamento automático de pontos GNV aprendidos na lenta. */
class AutoIdlePointCleanerTest {
    private val cleaner = AutoIdlePointCleaner()
    private val settled = AutoIdlePointCleaner.SESSION_SETTLE_MS + 1

    @Test
    fun `lenta depois andou apaga todas as bandas marcadas com contador`() {
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(4, 6), counters = counters(4 to 3, 6 to 2)))
        assertTrue(decision is AutoIdlePointCleaner.Decision.Delete)
        assertEquals(listOf(4, 6), (decision as AutoIdlePointCleaner.Decision.Delete).bands)
    }

    @Test
    fun `carro parado na lenta nao apaga`() {
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(4), rpm = 850))
        assertWait(decision, "andando")
    }

    @Test
    fun `sem banda marcada nao apaga mesmo andando`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = emptyList())), "Nenhuma")
    }

    @Test
    fun `combustivel gasolina nao apaga`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), fuel = "GASOLINA")), "andando")
    }

    @Test
    fun `quadro velho de telemetria nao conta como andando`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), frameAt = 17_000)), "andando")
    }

    @Test
    fun `segundo disparo antes de 5 s espera`() {
        cleaner.onSucceeded(20_000)
        assertWait(cleaner.decide(input(now = 24_000, marks = listOf(5), counters = counters(5 to 1))), "Intervalo")
        assertTrue(cleaner.decide(input(now = 25_000, marks = listOf(5), counters = counters(5 to 1))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `tentar depois nao consome o intervalo`() {
        cleaner.onRetryLater()
        assertTrue(cleaner.decide(input(now = 20_000, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `AUTO_CAL_ENABLE diferente de 1 nao age`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), enabled = 0)), "AUTO_CAL_ENABLE")
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), enabled = null)), "AUTO_CAL_ENABLE")
    }

    @Test
    fun `sessao ainda estabilizando nao age`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), sessionAge = 3_000)), "estabiliz")
    }

    @Test
    fun `banda marcada mas ja vazia na ultima leitura nao vira alvo`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), counters = counters(4 to 0))), "vazia")
    }

    @Test
    fun `falha com mutacao possivel bloqueia 10 s e exige releitura`() {
        cleaner.onFailed(20_000, mutationMayHaveStarted = true)
        assertWait(cleaner.decide(input(now = 31_000, marks = listOf(4))), "releitura")
        cleaner.onReread()
        assertWait(cleaner.decide(input(now = 29_000, marks = listOf(4))), "bloqueado")
        assertTrue(cleaner.decide(input(now = 31_000, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `falha sem mutacao nao bloqueia nem consome intervalo`() {
        cleaner.onFailed(20_000, mutationMayHaveStarted = false)
        assertTrue(cleaner.decide(input(now = 20_001, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `desligado na sessao nao age ate sessao nova`() {
        cleaner.disable("gasolina mudou")
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4))), "desligado")
        cleaner.reset() // invalidateRound/ação manual: continua desligado e o intervalo continua valendo
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4))), "desligado")
        cleaner.resetSession()
        assertTrue(cleaner.decide(input(now = 20_000, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `reset preserva o intervalo de 5 s do ultimo apagamento`() {
        cleaner.onSucceeded(20_000)
        cleaner.reset()
        assertWait(cleaner.decide(input(now = 22_000, marks = listOf(4))), "Intervalo")
    }

    @Test
    fun `apagamento na vespera do automatch apaga normalmente`() {
        // Não existe guarda de AutoMatch: a política nem recebe o contador/MAX do AutoMatch.
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(7), counters = counters(7 to 9)))
        assertTrue(decision is AutoIdlePointCleaner.Decision.Delete)
    }

    private fun assertWait(decision: AutoIdlePointCleaner.Decision, reasonPart: String) {
        assertTrue(decision.toString(), decision is AutoIdlePointCleaner.Decision.Wait)
        val reason = (decision as AutoIdlePointCleaner.Decision.Wait).reason
        assertTrue(reason, reason.contains(reasonPart, ignoreCase = true))
    }

    private fun counters(vararg values: Pair<Int, Int>) = IntArray(18).also { a -> values.forEach { (i, v) -> a[i] = v } }

    private fun input(
        now: Long,
        marks: List<Int>,
        counters: IntArray = IntArray(18) { 3 },
        rpm: Int = 2_000,
        fuel: String = "GNV",
        frameAt: Long = now - 200,
        enabled: Int? = 1,
        sessionAge: Long = settled,
    ) = AutoIdlePointCleaner.Input(
        nowElapsedMs = now,
        idleBands = marks.associateWith { evidence(it) },
        lastCounters = counters,
        latestFrame = NativeAnchorTelemetryWindow.Frame(1, frameAt, rpm, 0.6, 4.0, fuel),
        autoCalEnabled = enabled,
        sessionAgeMs = sessionAge,
    )

    private fun evidence(band: Int) = IdleAcquisitionTracker.Evidence(
        band = band, frames = 5, idleFrames = 5, idleFraction = 1.0, mapBar = 0.4, mapRaw = 410,
        counterBefore = 1, counterAfter = 2, valueChangedAtSameCounter = false, fromElapsedMs = 0, toElapsedMs = 1,
    )
}
