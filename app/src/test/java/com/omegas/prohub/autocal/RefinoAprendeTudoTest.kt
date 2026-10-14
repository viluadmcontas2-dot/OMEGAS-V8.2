package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/**
 * Dono, 2026-10-11: "A CURVA DEVE APRENDER TUDO! E SABER ONDE AJUSTAR O QUÊ!"
 *
 * A curva (global) aprende todas as rotações, lenta incluída. Um grupo (coluna de RPM do Mapa K × faixa de
 * t_gasolina) só vira ajuste LOCAL no Mapa K quando é separável e diferente: ≥ 8 pares e ≥ 3 visitas estáveis
 * no grupo, ≥ 8 pares de outras colunas no mesmo t (±0,5 ms) e diferença acima de 5% (sai só em 4%). Sem
 * separabilidade fica tudo na curva. Classe 2 (sintético), pelo mesmo caminho do serviço: acumulador real,
 * cola real (RefinementCycle), diário e piloto reais; o "motor" é sintético (nenhuma proposta de curva), exceto
 * onde o motor real roda sobre a leitura real da ECU (sessão de referência, seq 95).
 */
class RefinoAprendeTudoTest {
    private var now = 5_000_000L
    private val clock = { now }
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private val monitorDone = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)

    /** Lenta (coluna 850) e cruzeiro (coluna 1850) no MESMO t_gasolina; cruzeiro "longe" em outro t. */
    private val idle = Triple(860.0, 0.44, 3.9)
    private val cruise = Triple(2000.0, 0.40, 3.9)
    private val cruiseFar = Triple(2000.0, 0.70, 6.75)

    private inner class Rig {
        val ledger = EquivalenceLedger(null, clock)
        val journal = RefinementJournal(null, clock)
        val pilot = RefinementAutopilot(null, clock)
        var enginePairs: List<Pair<Double, Double>> = emptyList()
        val cycle = RefinementCycle(ledger, journal, pilot, monotonicMs = { now }) { _, pairs, _ -> enginePairs = pairs; heldAnalysis() }
        var last = JSONObject()
        var monitor: JSONObject = monitorDone

        fun frames(fuel: String, at: Triple<Double, Double, Double>, petrolMs: Double, n: Int) {
            now += 2_000 // quadros de chamadas diferentes nunca formam a mesma janela estável
            repeat(n) { ledger.accept(EquivalenceLedger.Frame(now, fuel, at.first, at.second, petrolMs)); now += 280 }
        }

        /** Gasolina própria quente e madura (8 leituras estáveis) em cada região. */
        fun petrol(vararg at: Triple<Double, Double, Double>) = at.forEach { frames("GASOLINA", it, it.third, 10) }

        /** Uma visita no GNV: [n] leituras estáveis por região com t_no_GNV = t_gasolina × razão; depois 2 min longe. */
        fun visit(vararg at: Pair<Triple<Double, Double, Double>, Double>, n: Int = 6) {
            at.forEach { (where, ratio) -> frames("GNV", where, where.third * ratio, n + 2) }
            now += 120_000
        }

        private fun heldAnalysis(): JSONObject = JSONObject().put("ok", true).put("available", true).put("refinementMode", "EQUIVALENCE")
            .put("snapshotHash", "S").put("points", JSONArray((0 until 30).map {
                JSONObject().put("index", it).put("referenceTimeMs", axisRaw[it] / 512.0).put("currentRaw", 16384)
                    .put("calculatedRaw", 16384).put("origin", "HELD")
            }))

        private fun snapshot(): JSONObject = JSONObject().put("snapshotHash", "S").put("capturedAtMs", 1L).put("fields", JSONArray()
            .put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", JSONArray(axisRaw.toList())).put("capturedAtMs", 1L))
            .put(JSONObject().put("key", "MUL_ACT").put("status", "VALID").put("rawValues", JSONArray(List(30) { 16384 })).put("capturedAtMs", 1L)))

        fun tick(online: Boolean = true): JSONObject {
            now += 3_000
            cycle.evaluate(online)
            last = cycle.observe(online, online, monitor, snapshot())
            return last
        }

        fun local(): JSONObject? = last.getJSONArray("secondary").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("kind") == "local" } }
        fun cells(): List<Triple<Int, Int, Double>> = local()?.getJSONArray("cells")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { c -> Triple(c.getInt("row"), c.getInt("column"), c.getDouble("deltaPercent")) } }
        } ?: emptyList()
        fun band0(): JSONObject = ledger.index().getJSONArray("bands").getJSONObject(0)
        fun idlePairs() = ledger.pairs().count { it.rpm < 1_000.0 }
    }

    private fun referenceSnapshot(): JSONObject = RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(RealSessionReplaySupport.REFERENCE), 95)

    private fun points(analysis: JSONObject, from: Double, to: Double): List<JSONObject> =
        analysis.getJSONArray("points").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .filter { it.getDouble("referenceTimeMs") in from..to }

    @Test
    fun `cruzeiro igual e lenta 20 por cento em 3 visitas vira ajuste local na coluna 850 e a curva nao muda de 3 a 4,5 ms`() {
        val withIdle = Rig()
        withIdle.petrol(idle, cruise)
        repeat(3) { withIdle.visit(idle to 1.20, cruise to 1.0) }
        withIdle.tick()
        val local = withIdle.local()
        assertNotNull("ajuste local na lenta", local)
        assertTrue(local!!.getBoolean("enabled"))
        val cells = withIdle.cells()
        assertEquals("Revisar ajuste local no Mapa K (${cells.size} células)", local.getString("label"))
        // Coluna 850 (índice 0); linhas que cobrem [t_gasolina 3,9; t_GNV 4,68] = 3,5 / 4,5 / 6,0 ms; no máximo +15% por gravação.
        assertEquals(listOf(3 to 0, 4 to 0, 5 to 0), cells.map { it.first to it.second })
        assertTrue(cells.all { abs(it.third - 15.0) < 1e-9 })
        // Os pares da lenta (grupo local) saem da evidência da curva: pares, índice e o que o motor recebe.
        assertEquals(0, withIdle.idlePairs())
        assertEquals(18, withIdle.band0().getInt("samples"))
        assertEquals(1.0, withIdle.band0().getDouble("ratio"), 1e-6)
        assertTrue(withIdle.enginePairs.none { abs(it.second - 3.9 * 1.20) < 1e-6 })

        // Motor real sobre a leitura real da ECU: com a lenta local, os pontos de 3 a 4,5 ms são os mesmos de sem a lenta.
        val onlyCruise = Rig()
        onlyCruise.petrol(idle, cruise)
        repeat(3) { onlyCruise.visit(cruise to 1.0) }
        val snapshot = referenceSnapshot()
        val a = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, withIdle.ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs })
        val b = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, onlyCruise.ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs })
        assertEquals(points(b, 3.0, 4.5).toString(), points(a, 3.0, 4.5).toString())
    }

    @Test
    fun `8 por cento igual na lenta e no cruzeiro vai para a curva sem ajuste local`() {
        val rig = Rig()
        rig.petrol(idle, cruise)
        repeat(3) { rig.visit(idle to 1.08, cruise to 1.08) }
        rig.tick()
        assertNull(rig.local())
        // (dono, 2026-10-11: a curva aprende tudo) a lenta entra na curva junto com o cruzeiro.
        assertEquals(18, rig.idlePairs())
        assertEquals(36, rig.band0().getInt("samples"))
        assertEquals(1.08, rig.band0().getDouble("ratio"), 1e-6)
        assertEquals(36, rig.enginePairs.count { it.first in 3.0..4.5 })
    }

    @Test
    fun `lenta 20 por cento sem conducao no mesmo t vai para a curva e a curva sobe nesses pontos`() {
        val rig = Rig()
        rig.petrol(idle, cruiseFar)
        repeat(3) { rig.visit(idle to 1.20, cruiseFar to 1.0) }
        rig.tick()
        assertNull("sem condução no mesmo t não dá para separar: fica na curva", rig.local())
        assertEquals(18, rig.idlePairs())
        assertEquals(18, rig.band0().getInt("samples"))
        assertEquals(1.20, rig.band0().getDouble("ratio"), 1e-6)
        assertEquals(18, rig.enginePairs.count { abs(it.second - 3.9 * 1.20) < 1e-6 })

        // Motor real sobre a leitura real da ECU: com a lenta na evidência, a curva sobe em 3,5–4,5 ms.
        val far = Rig()
        far.petrol(idle, cruiseFar)
        repeat(3) { far.visit(cruiseFar to 1.0) }
        val snapshot = referenceSnapshot()
        val up = points(AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, rig.ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs }), 3.5, 4.5)
        val base = points(AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, far.ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs }), 3.5, 4.5)
        assertTrue(up.isNotEmpty())
        up.zip(base).forEach { (u, b) -> assertTrue("ponto ${u.getInt("index")} subiu", u.getInt("calculatedRaw") > b.getInt("calculatedRaw")) }
    }

    @Test
    fun `margem do dono no ajuste local 4,2 sem botao 5,5 botao 4,5 segue e 3,9 some`() {
        val rig = Rig()
        rig.petrol(idle, cruise)
        repeat(3) { rig.visit(cruise to 1.0, n = 10) }
        // Cada passo: 3 visitas novas de 10 leituras na lenta (a região guarda as 30 mais recentes); entre uma visita e outra
        // o carro passa por outra região (visita = passagem; sem relógio).
        fun step(ratio: Double): JSONObject? { repeat(3) { rig.visit(idle to ratio, n = 10); rig.visit(cruise to 1.0, n = 2) }; rig.tick(); return rig.local() }
        assertNull("4,2% nunca vira botão", step(1.042))
        val entered = step(1.055)
        assertNotNull("5,5% entra", entered)
        assertEquals(5.5, rig.cells().first().third, 1e-6)
        assertNotNull("4,5% depois segue (histerese: só sai em 4%)", step(1.045))
        assertEquals(4.5, rig.cells().first().third, 1e-6)
        assertNull("3,9% some", step(1.039))
    }

    @Test
    fun `visitas instaveis ou uma visita so nao viram ajuste local`() {
        val unstable = Rig()
        unstable.petrol(idle, cruise)
        listOf(1.20, 1.20, 0.90).forEach { unstable.visit(idle to it, cruise to 1.0) }
        unstable.tick()
        assertNull(unstable.local())
        assertEquals(18, unstable.idlePairs())

        val once = Rig()
        once.petrol(idle, cruise)
        once.visit(idle to 1.20, n = 18)
        repeat(3) { once.visit(cruise to 1.0) }
        once.tick()
        assertNull(once.local())
        assertEquals(18, once.idlePairs())
    }

    @Test
    fun `ajuste local so com a ECU fora do automatico e sem ECU conectada fica desabilitado`() {
        val rig = Rig()
        rig.petrol(idle, cruise)
        repeat(3) { rig.visit(idle to 1.20, cruise to 1.0) }
        rig.tick()
        assertNotNull(rig.local())
        assertTrue(rig.local()!!.getBoolean("enabled"))
        rig.tick(online = false)
        assertFalse("sem ECU o botão fica, desabilitado", rig.local()!!.getBoolean("enabled"))

        val working = Rig()
        working.monitor = JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1)
        working.petrol(idle, cruise)
        repeat(3) { working.visit(idle to 1.20, cruise to 1.0) }
        assertEquals("ECU_TRABALHANDO", working.tick().getString("phase"))
        assertNull("a ECU ainda no automático: nada local", working.local())
        assertEquals(0, working.idlePairs())
    }

    @Test
    fun `lenta conta no prazo da verificacao e parado nao`() {
        // (dono, 2026-10-11: a curva aprende tudo) o prazo anda com o motor funcionando no GNV em qualquer rotação.
        assertTrue(RefinementCycle.driving(860.0, 3.9, "GNV"))
        assertTrue(RefinementCycle.driving(2_000.0, 5.0, "gnv"))
        assertFalse(RefinementCycle.driving(0.0, 3.9, "GNV"))
        assertFalse(RefinementCycle.driving(860.0, 3.9, "GASOLINA"))
        assertFalse(RefinementCycle.driving(860.0, 0.5, "GNV"))
    }

    @Test
    fun `refino nunca grava sozinho nem fala com a ECU`() {
        listOf("EquivalenceLedger.kt", "LocalMapPlanner.kt", "RefinementAutopilot.kt", "RefinementCycle.kt").forEach { name ->
            val source = listOf("src/main/java/com/omegas/prohub/autocal/$name", "app/src/main/java/com/omegas/prohub/autocal/$name")
                .map(::File).first { it.exists() }.readText()
            // O ciclo só LÊ o status JSON do KFactorManager (gravação já confirmada); ninguém aqui importa ou chama escritor/USB.
            listOf("KWriteManager", "import com.omegas.prohub.calibration.KFactorManager", "import com.omegas.prohub.usb", "UsbSerialManager",
                "startMapBatchWrite", "startBatchWrite", "writeCurve(", "writeMap(").forEach {
                assertFalse("$name cita $it", source.contains(it))
            }
        }
        assertTrue(ln(1.05) > ln(1.04))
    }
}
