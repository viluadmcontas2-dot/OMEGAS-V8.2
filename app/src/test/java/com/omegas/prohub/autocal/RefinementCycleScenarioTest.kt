package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classe 2 (comportamento sintético), mas CRUZADO: cada cenário percorre o ciclo inteiro
 * (aprender → propor → gravar → verificar → fechar) e o que acontece se algo der errado no meio.
 * Foi escrito a partir de bugs vistos no carro: "verificando" eterno, marcha lenta puxando a curva
 * em ~4,5 ms, e o refino dependendo de uma resposta do AutoMatch nativo.
 */
class RefinementCycleScenarioTest {
    private var now = 1_000_000L
    private val ledger = EquivalenceLedger(null) { now }
    private val journal = RefinementJournal(null) { now }
    private val pilot = EquivalencePhases(null) { now }

    /** Faixas de Petrol Inj. de condução: (rpm, MAP, ms de gasolina). Células distintas no RPM×MAP. */
    private val cells = listOf(
        Triple(2_000.0, 0.40, 3.6),
        Triple(2_200.0, 0.50, 5.0),
        Triple(2_500.0, 0.60, 6.5),
        Triple(2_800.0, 0.70, 8.0),
        Triple(3_200.0, 0.85, 10.0),
    )
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }

    private var t = 0L

    private fun drive(fuel: String, rpm: Double, map: Double, ms: Double, frames: Int = 10) {
        repeat(frames) { ledger.accept(EquivalenceLedger.Frame(t, fuel, rpm, map, ms)); t += 280 }
        t += 5_000
    }

    private fun petrolBaseline() = cells.forEach { (rpm, map, ms) -> drive("GASOLINA", rpm, map, ms) }

    private fun gas(ratio: Double, bands: List<Int> = cells.indices.toList()) =
        // Três passagens separadas por lacuna > 3 s: cada faixa precisa de 3 episódios (E3), não só de 8 pares.
        bands.forEach { i -> val (rpm, map, ms) = cells[i]; repeat(3) { drive("GNV", rpm, map, ms * ratio) } }

    private fun monitor(count: Int, max: Int = 3, enabled: Int = 1) = JSONObject()
        .put("autoMatchCount", count).put("maxAutomatch", max).put("autoCalEnabled", enabled)

    /** Um tick do serviço. [stepMs] ≤ 10 s: é o teto que o diário e o piloto contam por tick. */
    private fun observe(count: Int = 3, online: Boolean = true, stepMs: Long = 3_000L): JSONObject {
        now += stepMs
        journal.evaluate(ledger.index(), ecuOnline = online)
        return pilot.observe(online, monitor(count), null, ledger.index(), journal.json(), journal.restorePoints().length())
    }

    private fun writeCurve() {
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..23) it[i] = 17000 }
        journal.recordCurveWrite(before, after, axisRaw, ledger.index(), "teste")
        ledger.resetGas("CURVA_K_GRAVADA")
    }

    private fun status() = journal.json().getJSONObject("latest").getString("status")

    @Test
    fun `ciclo completo - curva pronta, gravacao, verificacao e estavel`() {
        petrolBaseline(); gas(1.08)
        val ready = observe()
        assertEquals("PROPOSTA_PRONTA", ready.getString("phase"))
        assertEquals("MAX_AUTOMATCH", ready.getString("ecuDoneReason"))
        assertTrue("avisa uma vez", pilot.takeAlert() != null)

        writeCurve()
        val verifying = observe()
        assertEquals("VERIFICANDO", verifying.getString("phase"))
        assertTrue(verifying.getString("headline").contains("0 de 15 min"))
        assertTrue("diz onde dirigir", verifying.getString("next").contains("ms"))

        gas(1.0)
        val done = observe()
        assertEquals("VERIFICADO", status())
        assertEquals("ESTAVEL", done.getString("phase"))
        assertTrue(done.getBoolean("canDisconnect"))
    }

    @Test
    fun `faixa que o motorista nunca visita nao segura a verificacao para sempre`() {
        petrolBaseline(); gas(1.08)
        observe()
        writeCurve()
        gas(1.0, bands = listOf(0, 1, 3, 4)) // a faixa 6,0–7,5 ms nunca é visitada
        var phase = observe().getString("phase")
        assertEquals("ainda dentro do prazo, espera", "VERIFICANDO", phase)
        assertTrue(pilot.json().getJSONObject("verification").getJSONArray("waitingBands").length() >= 1)
        // 16+ minutos de condução depois (100 ticks de 10 s): fecha com o que mediu.
        repeat(100) { phase = observe(stepMs = 10_000L).getString("phase") }
        assertNotEquals("VERIFICANDO", phase)
        assertEquals("VERIFICADO", status())
        val band = journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2)
        assertEquals("SEM_DADOS", band.getString("verdict"))
    }

    @Test
    fun `gravar sem medicao anterior fecha na hora e o refino segue sozinho`() {
        petrolBaseline() // só gasolina: o GNV de antes não existe
        writeCurve()
        val phase = observe().getString("phase")
        assertEquals("SEM_BASE", status())
        assertNotEquals("VERIFICANDO", phase)
        // O que o motorista roda depois já vira a base: sem esperar resposta nenhuma da ECU.
        gas(1.12)
        val next = observe()
        assertEquals("PROPOSTA_PRONTA", next.getString("phase"))
    }

    @Test
    fun `sem ECU online o relogio da verificacao nao anda`() {
        petrolBaseline(); gas(1.08)
        observe()
        writeCurve()
        repeat(200) { observe(online = false, stepMs = 10_000L) }
        assertEquals("VERIFICANDO", status())
        assertEquals(0L, journal.json().getJSONObject("latest").getLong("onlineMs"))
    }

    @Test
    fun `sem nenhuma faixa julgavel em 40 min fica INCONCLUSIVO e o refino volta a medir`() {
        petrolBaseline(); gas(1.08)
        observe()
        writeCurve()
        var phase = ""
        repeat(260) { phase = observe(stepMs = 10_000L).getString("phase") } // ~43 min de condução, nenhum GNV novo
        assertEquals("INCONCLUSIVO", status())
        assertNotEquals("VERIFICANDO", phase)
    }

    @Test
    fun `AutoMatch ou Mapa K no meio da verificacao interrompe e libera o piloto`() {
        petrolBaseline(); gas(1.08)
        observe()
        writeCurve()
        assertEquals("VERIFICANDO", observe().getString("phase"))
        journal.interrupt("AUTOMATCH_NATIVO")
        ledger.resetGas("AUTOMATCH_NATIVO")
        val phase = observe(count = 1).getString("phase")
        assertEquals("INTERROMPIDO", status())
        assertEquals("a ECU voltou ao automático", "ECU_TRABALHANDO", phase)
    }

    @Test
    fun `depois do terceiro AutoMatch a ECU esta fechada na hora, sem esperar silencio`() {
        petrolBaseline(); gas(1.08)
        val working = observe(count = 2)
        assertEquals("ECU_TRABALHANDO", working.getString("phase"))
        assertNull(pilot.takeAlert())
        val closed = observe(count = 3)
        assertEquals("MAX_AUTOMATCH", closed.getString("ecuDoneReason"))
        assertEquals("PROPOSTA_PRONTA", closed.getString("phase"))
        // Um reset da ECU zera o contador: volta a esperar e depois fecha de novo.
        assertEquals("ECU_TRABALHANDO", observe(count = 0).getString("phase"))
        assertEquals("MAX_AUTOMATCH", observe(count = 3).getString("ecuDoneReason"))
    }

    @Test
    fun `marcha lenta nunca vira evidencia para corrigir a curva`() {
        // Parado em neutro: gasolina e GNV a ~870 rpm com 20% de diferença.
        drive("GASOLINA", 870.0, 0.35, 4.0, frames = 20)
        drive("GNV", 870.0, 0.35, 4.8, frames = 20)
        assertTrue("os pares existem (aparecem no gráfico)", ledger.pairs().isNotEmpty())
        assertTrue("mas não entram na correção", ledger.drivingPairs().isEmpty())
        val index = ledger.index()
        assertEquals(0, index.getInt("samples"))
        val phase = observe().getString("phase")
        assertEquals("COLETANDO_NOSSOS", phase)
    }

    @Test
    fun `numero desconhecido da ECU aparece como null e nunca como zero`() {
        petrolBaseline(); gas(1.08)
        val out = observe()
        assertTrue(out.isNull("petrolValid"))
        assertTrue(out.isNull("gasValid"))
        assertTrue(out.optBoolean("ecuOnline"))
    }

    @Test
    fun `reinicio do app no meio da verificacao retoma de onde parou`() {
        val dir = java.nio.file.Files.createTempDirectory("journal").toFile()
        val file = java.io.File(dir, "refinement_journal.json")
        val first = RefinementJournal(file) { now }
        petrolBaseline(); gas(1.08)
        first.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { if (it in 5..23) 17000 else 16384 }, axisRaw, ledger.index(), "teste")
        ledger.resetGas("CURVA_K_GRAVADA")
        repeat(30) { now += 10_000; first.evaluate(ledger.index(), ecuOnline = true) }
        // O diário grava a cada ~1 min: uma avaliação depois disso força o estado mais novo no arquivo.
        now += 61_000
        first.evaluate(ledger.index(), ecuOnline = true)
        val counted = first.json().getJSONObject("latest").getLong("onlineMs")
        assertTrue(counted >= 200_000L)

        val second = RefinementJournal(file) { now }
        val latest = second.json().getJSONObject("latest")
        assertEquals("VERIFICANDO", latest.getString("status"))
        assertEquals("o tempo de condução já contado volta", counted, latest.getLong("onlineMs"))
        assertEquals(EquivalenceLedger.BANDS.size, latest.getJSONArray("bands").length())
    }
}
