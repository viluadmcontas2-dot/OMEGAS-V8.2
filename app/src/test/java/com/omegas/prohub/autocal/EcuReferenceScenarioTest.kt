package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * O estado do AutoMatch e a curva de gasolina moram na ECU, que os entrega ao conectar (app recém-instalado,
 * outra versão, outra sessão). O Refino não pode pedir gasolina que a ECU já tem, nem ficar "aguardando"
 * um AutoMatch que já terminou.
 */
class EcuReferenceScenarioTest {
    // ---------------------------------------------------------------- ledger, sintético (classe 2)

    private var t = 1_000_000L
    private fun drive(ledger: EquivalenceLedger, fuel: String, rpm: Double, map: Double, ms: Double, times: Int = 3) {
        repeat(times) { ledger.accept(EquivalenceLedger.Frame(t, fuel, rpm, map, ms)); t += 280 }
        t += 5_000
    }

    /** Curva de gasolina linear: t = 8 * MAP, de 0,30 a 0,95 bar. */
    private fun linearRef(points: Int = 8) = (0 until points).map { i -> val m = 0.30 + 0.65 * i / (points - 1); m to 8.0 * m }

    @Test
    fun `GNV sem nenhuma gasolina do app usa a curva de gasolina da ECU como referencia`() {
        val ledger = EquivalenceLedger(null)
        ledger.setEcuPetrolReference(linearRef())
        // GNV pedindo 8% a mais que a curva da ECU em MAP 0,60 (referência 4,8 ms).
        drive(ledger, "GNV", 2_200.0, 0.60, 4.8 * 1.08)
        val pairs = ledger.drivingPairs()
        assertEquals(1, pairs.size)
        assertTrue(pairs[0].ecuRef)
        assertEquals(4.8, pairs[0].petrolRefMs, 1e-9)
        val index = ledger.index()
        assertEquals("ECU", index.getString("petrolReference"))
        assertEquals(1, index.getInt("ecuReferencePairs"))
        assertEquals(0, index.getInt("ownReferencePairs"))
        assertEquals(8, index.getInt("ecuPetrolPoints"))
        val band = index.getJSONArray("bands").getJSONObject(1) // 4,5–6,0 ms
        assertEquals(1.0, band.getDouble("ecuShare"), 1e-9)
        assertEquals(1.08, band.getDouble("ratio"), 1e-6)
    }

    @Test
    fun `gasolina medida pelo app tem precedencia sobre a curva da ECU`() {
        val ledger = EquivalenceLedger(null)
        ledger.setEcuPetrolReference(linearRef())
        repeat(3) { drive(ledger, "GASOLINA", 2_200.0, 0.60, 5.0) } // o app mediu 5,0 ms (a ECU diz 4,8)
        drive(ledger, "GNV", 2_200.0, 0.60, 5.0)
        val pair = ledger.drivingPairs().single()
        assertFalse("há gasolina própria casando: não usa a da ECU", pair.ecuRef)
        assertEquals(5.0, pair.petrolRefMs, 1e-9)
        assertEquals("PROPRIA", ledger.index().getString("petrolReference"))
    }

    @Test
    fun `curva da ECU curta, estreita, fora do fisico ou fora da faixa de MAP nao vira referencia`() {
        val ledger = EquivalenceLedger(null)
        ledger.setEcuPetrolReference(linearRef(5)) // poucos pontos
        drive(ledger, "GNV", 2_200.0, 0.60, 5.0)
        assertEquals(0, ledger.drivingPairs().size)
        ledger.setEcuPetrolReference((0 until 8).map { 0.50 + it * 0.01 to 4.0 + it * 0.1 }) // estreita: 0,07 bar
        assertEquals(0, ledger.drivingPairs().size)
        ledger.setEcuPetrolReference((0 until 8).map { 0.30 + it * 0.09 to Double.NaN }) // inválida
        assertEquals(0, ledger.drivingPairs().size)
        ledger.setEcuPetrolReference(linearRef())
        drive(ledger, "GNV", 2_200.0, 1.40, 9.0) // MAP bem acima da curva (0,95 bar)
        assertEquals("só o ponto dentro da faixa da curva", 1, ledger.drivingPairs().size)
        assertEquals("NENHUMA", EquivalenceLedger(null).index().getString("petrolReference"))
    }

    @Test
    fun `a revisao so muda quando a curva da ECU realmente muda`() {
        val ledger = EquivalenceLedger(null)
        val r0 = ledger.revision()
        ledger.setEcuPetrolReference(linearRef())
        val r1 = ledger.revision()
        assertNotEquals(r0, r1)
        ledger.setEcuPetrolReference(linearRef()) // mesma curva lida de novo a cada segundo
        assertEquals("sem mudança, sem recálculo na multimídia", r1, ledger.revision())
        ledger.setEcuPetrolReference(emptyList())
        assertNotEquals(r1, ledger.revision())
    }

    // ---------------------------------------------------------------- sessão real (classe 3)

    private val root = RealSessionReplaySupport.fixture(REFERENCE)
    private val frames = RealSessionReplaySupport.telemetry(root)
    private val ecuAcquisition = RealSessionReplaySupport.acquisition(RealSessionReplaySupport.snapshot(root, 2183))

    private fun ledger(onlyGnv: Boolean, withEcuReference: Boolean): EquivalenceLedger {
        val ledger = EquivalenceLedger(null)
        if (withEcuReference) ledger.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(ecuAcquisition))
        frames.filter { !onlyGnv || it.fuel == "GNV" }.forEach { ledger.accept(RealSessionReplaySupport.ledgerFrame(it)) }
        return ledger
    }

    @Test
    fun `sessao real, a curva de gasolina da ECU concorda com a gasolina medida na mesma sessao`() {
        val ecuPoints = EcuPetrolReference.fromAcquisition(ecuAcquisition)
        assertTrue("a ECU entregou a curva de gasolina madura: ${ecuPoints.size} pontos", ecuPoints.size >= 10)
        val own = ledger(onlyGnv = false, withEcuReference = false).index()
        val ecu = ledger(onlyGnv = true, withEcuReference = true).index()
        assertEquals("sem nenhuma gasolina do app, a referência é a da ECU", "ECU", ecu.getString("petrolReference"))
        assertTrue(ecu.getInt("ecuReferencePairs") > 50)
        // Índice global: a mesma pergunta respondida pelos dois caminhos.
        val gap = abs(ln(own.getDouble("ratio") / ecu.getDouble("ratio")))
        assertTrue("índice global própria=%.3f ECU=%.3f".format(own.getDouble("ratio"), ecu.getDouble("ratio")), gap < 0.03)
        // Faixa por faixa onde as duas têm leituras de sobra. (Faixas com poucos pares próprios não entram.)
        var compared = 0
        for (i in 0 until own.getJSONArray("bands").length()) {
            val a = own.getJSONArray("bands").getJSONObject(i)
            val b = ecu.getJSONArray("bands").getJSONObject(i)
            if (a.getInt("samples") >= 12 && b.getInt("samples") >= 12 && !a.isNull("ratio") && !b.isNull("ratio")) {
                compared++
                val diff = abs(ln(a.getDouble("ratio") / b.getDouble("ratio")))
                assertTrue("faixa ${a.getDouble("fromMs")}–${a.getDouble("toMs")} própria=%.3f ECU=%.3f".format(a.getDouble("ratio"), b.getDouble("ratio")), diff < 0.06)
            }
        }
        assertTrue("pelo menos 3 faixas comparáveis (foram $compared)", compared >= 3)
    }

    private fun monitor(count: Int?, max: Int? = 3) = JSONObject()
        .put("autoMatchCount", count ?: JSONObject.NULL).put("maxAutomatch", max ?: JSONObject.NULL).put("autoCalEnabled", 1)

    @Test
    fun `app recem-instalado com AutoMatch 3 de 3 na ECU nao espera AutoMatch nem pede gasolina`() {
        val ledger = ledger(onlyGnv = true, withEcuReference = true) // nenhuma gasolina medida por este app
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        val pilot = EquivalencePhases(null)
        val out = pilot.observe(true, monitor(3), ecuAcquisition, ledger.index(), RefinementJournal(null).json(), 0)
        assertTrue("a ECU já tinha terminado: sem esperar", out.getBoolean("ecuDone"))
        assertEquals("MAX_AUTOMATCH", out.getString("ecuDoneReason"))
        assertNotEquals("ECU_TRABALHANDO", out.getString("phase"))
        assertEquals("ECU", out.getString("petrolReference"))
        val text = out.getString("headline") + " " + out.getString("next")
        assertFalse("não pede gasolina que a ECU já tem: $text", text.contains("na gasolina para criar"))
        assertFalse("não manda esperar AutoMatch: $text", text.contains("automático"))
    }

    @Test
    fun `contador 3 de 3 sai so da aquisicao quando o monitor ainda nao trouxe o maximo`() {
        val ledger = ledger(onlyGnv = true, withEcuReference = true)
        val acquisitionWithMax = JSONObject(ecuAcquisition.toString())
        acquisitionWithMax.getJSONObject("thresholds").put("maxAutomatch", 3)
        val out = EquivalencePhases(null).observe(true, monitor(3, max = null), acquisitionWithMax, ledger.index(), RefinementJournal(null).json(), 0)
        assertEquals("MAX_AUTOMATCH", out.getString("ecuDoneReason"))
    }

    @Test
    fun `conectou e a ECU ainda nao entregou nada, o piloto diz que esta lendo`() {
        val pilot = EquivalencePhases(null)
        val reading = pilot.observe(true, monitor(null, null), null, EquivalenceLedger(null).index(), RefinementJournal(null).json(), 0)
        assertEquals("LENDO_ECU", reading.getString("phase"))
        assertFalse(reading.getBoolean("ecuRead"))
        // Depois que a ECU entrega, o estado vem dela.
        val read = pilot.observe(true, monitor(1), ecuAcquisition, EquivalenceLedger(null).index(), RefinementJournal(null).json(), 0)
        assertEquals("ECU_TRABALHANDO", read.getString("phase"))
        assertTrue(read.getString("headline").contains("automático 1 de 3"))
        // Sem cabo e sem nada lido: sem ECU.
        val offline = EquivalencePhases(null).observe(false, monitor(null, null), null, EquivalenceLedger(null).index(), RefinementJournal(null).json(), 0)
        assertEquals("SEM_ECU", offline.getString("phase"))
    }

    @Test
    fun `sem gasolina do app e sem curva de gasolina na ECU, so ai pede gasolina`() {
        val ledger = EquivalenceLedger(null)
        drive(ledger, "GNV", 2_200.0, 0.60, 5.0)
        val out = EquivalencePhases(null).observe(true, monitor(3), JSONObject().put("points", org.json.JSONArray()), ledger.index(), RefinementJournal(null).json(), 0)
        assertEquals("COLETANDO_NOSSOS", out.getString("phase"))
        assertTrue(out.getString("next"), out.getString("next").contains("Rode alguns minutos na gasolina"))
    }
}
