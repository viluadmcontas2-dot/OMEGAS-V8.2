package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 1: política pura do apagamento automático de pontos fora da curva, GNV e gasolina (spec rev2). */
class AutoIdlePointCleanerTest {
    private val cleaner = AutoIdlePointCleaner()
    private val settled = AutoIdlePointCleaner.SESSION_SETTLE_MS + 1

    @Test
    fun `GNV fora da curva com carro rodando em GNV apaga as bandas do GNV`() {
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(4, 6)))
        assertTrue(decision is AutoIdlePointCleaner.Decision.Delete)
        decision as AutoIdlePointCleaner.Decision.Delete
        assertEquals(Fuel.GAS, decision.fuel)
        assertEquals(listOf(4, 6), decision.bands)
    }

    @Test
    fun `carro parado em marcha lenta nao apaga`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), rpm = 850)), "rodando")
    }

    @Test
    fun `sem ponto fora da curva nao apaga`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = emptyList())), "Nenhum")
    }

    @Test
    fun `GNV fora da curva com carro em gasolina espera`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), fuel = "GASOLINA")), "rodando")
    }

    @Test
    fun `gasolina fora da curva com carro em GNV espera`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = emptyList(), petrol = listOf(5), fuel = "GNV")), "gasolina")
    }

    @Test
    fun `gasolina fora da curva com carro em gasolina apaga so a gasolina`() {
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(4), petrol = listOf(5), fuel = "GASOLINA"))
        decision as AutoIdlePointCleaner.Decision.Delete
        assertEquals(Fuel.PETROL, decision.fuel)
        assertEquals(listOf(5), decision.bands)
    }

    @Test
    fun `os dois fora da curva com carro em GNV apaga so o GNV`() {
        val decision = cleaner.decide(input(now = 20_000, marks = listOf(4), petrol = listOf(5), fuel = "GNV"))
        decision as AutoIdlePointCleaner.Decision.Delete
        assertEquals(Fuel.GAS, decision.fuel)
        assertEquals(listOf(4), decision.bands)
    }

    @Test
    fun `quadro velho de telemetria nao conta como andando`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), frameAt = 17_000)), "rodando")
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
    fun `banda fora da curva mas ja vazia na ultima leitura nao vira alvo`() {
        assertWait(cleaner.decide(input(now = 20_000, marks = listOf(4), counters = counters(4 to 0))), "Nenhum")
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
    fun `falha sem mutacao tambem consome o intervalo minimo`() {
        // Revisão 2026-10-07: falha na releitura de antes não pode virar Delete a cada 500 ms para sempre.
        cleaner.onFailed(20_000, mutationMayHaveStarted = false)
        assertWait(cleaner.decide(input(now = 20_500, marks = listOf(4))), "bloqueado")
        assertTrue(cleaner.decide(input(now = 25_000, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
    }

    @Test
    fun `cinco falhas seguidas desligam o automatico na sessao`() {
        repeat(4) { cleaner.onFailed(20_000L + it * 6_000L, mutationMayHaveStarted = false) }
        assertEquals(null, cleaner.disabledReason())
        cleaner.onFailed(50_000, mutationMayHaveStarted = false)
        assertTrue(cleaner.disabledReason()!!.contains("falhas"))
    }

    @Test
    fun `sucesso zera a contagem de falhas seguidas`() {
        repeat(4) { cleaner.onFailed(20_000L + it * 6_000L, mutationMayHaveStarted = false) }
        cleaner.onSucceeded(60_000)
        repeat(4) { cleaner.onFailed(70_000L + it * 6_000L, mutationMayHaveStarted = false) }
        assertEquals(null, cleaner.disabledReason())
    }

    @Test
    fun `readback ambiguo consome o intervalo e exige releitura sem desligar`() {
        cleaner.onSucceeded(20_000, rereadRequired = true)
        assertWait(cleaner.decide(input(now = 30_000, marks = listOf(4))), "releitura")
        cleaner.onReread()
        assertTrue(cleaner.decide(input(now = 30_000, marks = listOf(4))) is AutoIdlePointCleaner.Decision.Delete)
        assertEquals(null, cleaner.disabledReason())
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
    fun `reset nao libera o bloqueio de falha com mutacao possivel`() {
        cleaner.onFailed(20_000, mutationMayHaveStarted = true)
        cleaner.reset()
        cleaner.onReread()
        assertWait(cleaner.decide(input(now = 21_000, marks = listOf(4))), "bloqueado")
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
        petrol: List<Int> = emptyList(),
        counters: IntArray = IntArray(18) { 3 },
        rpm: Int = 2_000,
        fuel: String = "GNV",
        frameAt: Long = now - 200,
        enabled: Int? = 1,
        sessionAge: Long = settled,
    ) = AutoIdlePointCleaner.Input(
        nowElapsedMs = now,
        candidates = mapOf(
            Fuel.GAS to marks.map { outlier(Fuel.GAS, it, counters[it]) },
            Fuel.PETROL to petrol.map { outlier(Fuel.PETROL, it, counters[it]) },
        ),
        latestFrame = NativeAnchorTelemetryWindow.Frame(1, frameAt, rpm, 0.6, 4.0, fuel),
        autoCalEnabled = enabled,
        sessionAgeMs = sessionAge,
    )

    private fun outlier(fuel: Fuel, band: Int, counter: Int) =
        OutlierCurveTracker.Outlier(fuel, band, counter, 1_500, 410, 1_500 / 512.0, 410 / 1024.0)
}
