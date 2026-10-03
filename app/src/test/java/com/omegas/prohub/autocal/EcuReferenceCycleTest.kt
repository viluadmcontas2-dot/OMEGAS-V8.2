package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ciclo inteiro do Refino num app recém-instalado: a ECU já fez o AutoMatch (3 de 3) e já tem a curva de
 * gasolina; o app nunca mediu gasolina. Início (ler a ECU), meio (medir o GNV, propor, gravar, verificar)
 * e fim (estável), mais as variações: a curva de gasolina da ECU chega depois, ou some.
 */
class EcuReferenceCycleTest {
    private var now = 1_000_000L
    private val ledger = EquivalenceLedger(null) { now }
    private val journal = RefinementJournal(null) { now }
    private val pilot = RefinementAutopilot(null) { now }

    private val cells = listOf(
        Triple(2_000.0, 0.40, 3.6), Triple(2_200.0, 0.50, 5.0), Triple(2_500.0, 0.60, 6.5),
        Triple(2_800.0, 0.70, 8.0), Triple(3_200.0, 0.85, 10.0),
    )
    /** A curva de gasolina da ECU passa exatamente pelas células de condução. */
    private val ecuCurve = listOf(0.30 to 2.5, 0.40 to 3.6, 0.50 to 5.0, 0.60 to 6.5, 0.70 to 8.0, 0.85 to 10.0, 0.95 to 11.5)
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private val emptyAcquisition = JSONObject().put("points", JSONArray())
    private var t = 0L

    private fun gas(ratio: Double) = cells.forEach { (rpm, map, ms) ->
        repeat(10) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", rpm, map, ms * ratio)); t += 280 }
        t += 5_000
    }

    private fun observe(count: Int? = 3, online: Boolean = true, acquisition: JSONObject? = emptyAcquisition, stepMs: Long = 3_000L): JSONObject {
        now += stepMs
        journal.evaluate(ledger.index(), ecuOnline = online)
        val monitor = JSONObject().put("autoMatchCount", count ?: JSONObject.NULL).put("maxAutomatch", 3).put("autoCalEnabled", 1)
        return pilot.observe(online, monitor, acquisition, ledger.index(), journal.json(), journal.restorePoints().length())
    }

    private fun writeCurve() {
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { for (i in 5..23) it[i] = 17000 }
        journal.recordCurveWrite(before, after, axisRaw, ledger.index(), "teste")
        ledger.resetGas("CURVA_K_GRAVADA")
    }

    private fun status() = journal.json().getJSONObject("latest").getString("status")

    @Test
    fun `app novo com a ECU ja pronta percorre o ciclo inteiro sem medir gasolina`() {
        ledger.setEcuPetrolReference(ecuCurve)
        // Início: a ECU ainda não entregou nada.
        val reading = observe(count = null, acquisition = null)
        assertEquals("LENDO_ECU", reading.getString("phase"))
        // A ECU entregou: 3 de 3 já feito. Nada de esperar AutoMatch, nada de pedir gasolina.
        val read = observe()
        assertEquals("COLETANDO_NOSSOS", read.getString("phase"))
        assertTrue(read.getBoolean("ecuDone"))
        assertFalse(read.getString("next"), read.getString("next").contains("na gasolina para criar"))
        assertTrue(read.getString("headline"), read.getString("headline").contains("já tem a curva de gasolina"))
        // Meio: o carro roda no GNV pedindo 12% a mais que a gasolina.
        gas(1.12)
        val ready = observe()
        assertEquals("PROPOSTA_PRONTA", ready.getString("phase"))
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        // Gravação manual confirmada, depois verificação que anda sozinha.
        writeCurve()
        assertEquals("VERIFICANDO", observe().getString("phase"))
        gas(1.0)
        val done = observe()
        assertEquals("VERIFICADO", status())
        assertEquals("ESTAVEL", done.getString("phase"))
        assertTrue(done.getBoolean("canDisconnect"))
    }

    @Test
    fun `a curva de gasolina da ECU chega depois e destrava o refino`() {
        gas(1.12) // o motorista já rodou, mas ninguém tem gasolina de referência
        val waiting = observe()
        assertEquals("COLETANDO_NOSSOS", waiting.getString("phase"))
        assertTrue(waiting.getString("next"), waiting.getString("next").contains("Rode alguns minutos na gasolina"))
        // A ECU entrega a curva de gasolina: os GNV já medidos viram evidência, sem rodar mais nada.
        ledger.setEcuPetrolReference(ecuCurve)
        assertEquals("PROPOSTA_PRONTA", observe().getString("phase"))
    }

    @Test
    fun `se a curva de gasolina da ECU some, a proposta nao fica presa em evidencia que nao existe mais`() {
        ledger.setEcuPetrolReference(ecuCurve)
        gas(1.12)
        assertEquals("PROPOSTA_PRONTA", observe().getString("phase"))
        ledger.setEcuPetrolReference(emptyList()) // RESET_PETROL na ECU: a referência acabou
        val after = observe()
        assertNotEquals("PROPOSTA_PRONTA", after.getString("phase"))
        assertEquals("NENHUMA", after.getString("petrolReference"))
        assertEquals("COLETANDO_NOSSOS", after.getString("phase"))
    }

    @Test
    fun `a ECU entrega um AutoMatch novo e o piloto volta a acompanhar sem travar`() {
        ledger.setEcuPetrolReference(ecuCurve)
        gas(1.12)
        assertEquals("PROPOSTA_PRONTA", observe(count = 3).getString("phase"))
        // A ECU fez outro AutoMatch (contador voltou a 1): ela é quem manda de novo.
        val again = observe(count = 1)
        assertEquals("ECU_TRABALHANDO", again.getString("phase"))
        assertFalse(again.getBoolean("ecuDone"))
        // E quando chega ao máximo outra vez, o piloto retoma na hora.
        assertEquals("MAX_AUTOMATCH", observe(count = 3).getString("ecuDoneReason"))
    }

    @Test
    fun `sem cabo nada afirma estado novo e o que a ECU entregou antes continua valendo`() {
        ledger.setEcuPetrolReference(ecuCurve)
        gas(1.12)
        assertEquals("PROPOSTA_PRONTA", observe().getString("phase"))
        val offline = observe(count = null, online = false, acquisition = null)
        assertEquals("PROPOSTA_PRONTA", offline.getString("phase"))
        assertFalse(offline.getBoolean("ecuOnline"))
    }
}
