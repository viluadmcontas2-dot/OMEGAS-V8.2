package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoCalAcquisition
import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EquivalencePhases
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Classe 2 (sintético, determinístico): SESSÃO FICTÍCIA completa passada pelo MESMO caminho do replay real
 * (EquivalenceRuntime.evaluate + EquivalencePhases.observe) e julgada por um ORÁCULO independente.
 *
 * Verdade plantada: curva de gasolina T(MAP) realista; curva K do GNV (a "aprendida pela ECU") com erro conhecido por
 * faixa nativa da ECU: faixa 3 com K 9% BAIXO, faixa 11 com K 12% ALTO, o resto dentro de 3%. K ideal(ms) = K / erro(ms).
 * O GNV mede t_gnv = t_gas · K_ideal / K (mesma relação que o motor usa: K_alvo = K·t_gnv/t_gas).
 * Contaminação plantada: carro parado/lenta (rpm < 1000, injeção +25..40%) e outliers de condução (×1,5 / ×0,6).
 *
 * Só análise: nada aqui toca a ECU. O JSON dos 5 momentos sai em build/sessao-ficticia (artefato do CI) para a prova visual.
 */
class SessaoFicticiaTest {
    private val axisRaw = intArrayOf(
        256, 512, 768, 1024, 1280, 1536, 1792, 2048, 2304, 2560, 2816, 3072, 3328, 3584, 3840, 4096, 4352, 4608, 4864, 5120,
        5632, 6144, 6656, 7168, 7680, 8192, 8704, 9216, 10240, 11264,
    )
    private val thd = intArrayOf(154, 256, 307, 358, 410, 461, 512, 563, 614, 666, 717, 768, 819, 870, 922, 973, 1024, 1126)

    // ---------------- verdade plantada ----------------
    private fun tpOf(map: Double) = 10.7 * map - 0.6                      // gasolina: ms × MAP
    private fun mapMid(b: Int) = (thd[b] + thd[b + 1]) / 2.0 / 1024.0
    private val usefulBands = 0 until 16
    /** erro do K por faixa nativa (K / K ideal): 0,91 = K 9% baixo, 1,12 = K 12% alto. */
    private val bandError = DoubleArray(16) { 1.0 + 0.025 * Math.sin(it * 2.1) }.also { it[3] = 0.91; it[11] = 1.12 }
    private val plantedBands = setOf(3, 11)
    private fun kCur(x: Double) = 1.06 - 0.08 * (1 - exp(-x / 7.0))
    private val nodeMs = DoubleArray(16) { tpOf(mapMid(it)) }
    private fun errAt(ms: Double): Double {
        if (ms <= nodeMs[0]) return bandError[0]
        if (ms >= nodeMs[15]) return bandError[15]
        for (i in 0 until 15) if (ms in nodeMs[i]..nodeMs[i + 1]) {
            val w = (ms - nodeMs[i]) / (nodeMs[i + 1] - nodeMs[i])
            return exp(ln(bandError[i]) * (1 - w) + ln(bandError[i + 1]) * w)
        }
        return 1.0
    }
    private fun curveRaw(errScale: Double = 1.0) = IntArray(30) { Math.round(kCur(axisRaw[it] / 512.0) * 16384.0).toInt() }
    private fun idealRaw(curve: IntArray) = DoubleArray(30) { curve[it] / errAt(axisRaw[it] / 512.0) }

    // ---------------- simulador (mesmo caminho do replay) ----------------
    private class Sim {
        var now = 1_800_000_000_000L
        val journal = com.omegas.prohub.autocal.RefinementJournal(null) { now }
        val stalls = com.omegas.prohub.autocal.StallWatch(null) { now }
        val ledger = EquivalenceLedger(null) { now }
        val runtime = EquivalenceRuntime(null) { now }
        val phases = EquivalencePhases(null, { now }, { now })
        var fuel: String? = null
        fun frame(fuel: String, rpm: Double, map: Double, ms: Double) {
            this.fuel = fuel
            ledger.accept(EquivalenceLedger.Frame(now, fuel, rpm, map, ms, ms * 2.0))
            runtime.onFrame(now, fuel, rpm, map, ms, 1L)
        }
        fun snap(snapshot: JSONObject, enable: Int, autoMatch: Int = 0): Pair<EquivalenceResult?, JSONObject> {
            val acquisition = AutoCalAcquisition.fromSnapshot(snapshot)
            EquivalenceEngine.curveFromSnapshot(snapshot)?.let { runtime.alignCurve(ledger, phases, it.second, now) }
            if (runtime.references.current() == null && ReferenceStore.pointsFrom(acquisition).isNotEmpty()) runtime.freeze(acquisition, phases)
            val result = runtime.evaluate(ledger, phases, snapshot, acquisition, true) { null }
            val monitor = JSONObject().put("autoCalEnabled", enable).put("autoMatchCount", autoMatch).put("maxAutomatch", 3)
            val obs = phases.observe(true, monitor, acquisition, ledger.index(), JSONObject(), 0, fuel)
            val ui = JSONObject().put("snapshot", snapshot).put("equivalence", runtime.json(acquisition)).put("phases", obs)
                // exatamente o que a ponte devolve em getEquivalence (mesma montagem da produção)
                .put("bridge", com.omegas.prohub.autocal.EquivalenceView.build(ledger, journal, phases, stalls, runtime.json(acquisition)))
            return result to ui
        }
    }

    private fun field(key: String, v: IntArray) =
        JSONObject().put("key", key).put("status", "VALID").put("rawValues", JSONArray(v.toList()))

    /** Snapshot nativo da ECU. petrolCount/gasCount = contagem por faixa (0 = buffer zerado). */
    private fun snapshot(t: Long, curve: IntArray, enable: Int, petrolCount: Int, gasCount: Int, rnd: Random): JSONObject {
        val n = 18
        val pT = IntArray(n); val pM = IntArray(n); val pC = IntArray(n)
        val gT = IntArray(n); val gM = IntArray(n); val gC = IntArray(n)
        // contador cresce com o ID da faixa em fase (aprendendo): faixas altas ainda com menos
        for (b in usefulBands) {
            val map = mapMid(b)
            val tp = tpOf(map)
            val c = { total: Int -> if (total == 0) 0 else (total - (b % 3)).coerceIn(1, 10) }
            if (petrolCount > 0) { pC[b] = c(petrolCount); pM[b] = Math.round(map * 1024).toInt(); pT[b] = Math.round(tp * (1 + 0.01 * (rnd.nextDouble() - .5)) * 512).toInt() }
            if (gasCount > 0) { gC[b] = c(gasCount); gM[b] = pM.let { Math.round(map * 1024).toInt() }; gT[b] = Math.round(tp / bandError[b] * (1 + 0.01 * (rnd.nextDouble() - .5)) * 512).toInt() }
        }
        val fields = JSONArray()
            .put(field("AUTO_CAL_ENABLE", intArrayOf(enable)))
            .put(field("MNFLD_PRESS_THD", thd))
            .put(field("NUM_BUF_UPD_PETR", pC)).put(field("NUM_BUF_UPD_GAS", gC))
            .put(field("PETR_INJ_TBUF", pT)).put(field("MNFLD_PRESS_BUF", pM))
            .put(field("PETR_INJ_TBUF_GAS", gT)).put(field("MNFLD_PRESS_BUF_GAS", gM))
            .put(field("MUL_ACT", curve)).put(field("PETR_INJ_TBP", axisRaw))
            .put(field("ACQUIRED_ZONES_PETROL", IntArray(4) { if (petrolCount > 0) 1 else 0 }))
            .put(field("ACQUIRED_ZONES_GAS", IntArray(4) { if (gasCount > 0) 1 else 0 }))
        return JSONObject().put("capturedAtMs", t).put("fields", fields)
    }

    // ---------------- gerador de telemetria ----------------
    private fun stretch(sim: Sim, rnd: Random, fuel: String, rpm: Double, map: Double, ms: Double) {
        repeat(3) {
            sim.frame(fuel, rpm, map, ms * (1 + 0.015 * (2 * rnd.nextDouble() - 1)))
            sim.now += 285
        }
        sim.now += 4_000
    }

    private fun bandMap(rnd: Random, b: Int) = (thd[b] + 0.1 * (thd[b + 1] - thd[b]) + rnd.nextDouble() * 0.8 * (thd[b + 1] - thd[b])) / 1024.0

    /** n pontos próprios por faixa (b3..b15: abaixo de 3 ms a telemetria não vale). */
    private fun petrolPhase(sim: Sim, rnd: Random, n: Int, from: Int = 0) {
        for (i in from until n) for (b in 3..15) {
            val m = bandMap(rnd, b)
            stretch(sim, rnd, "GASOLINA", 1500 + rnd.nextInt(2000).toDouble(), m, tpOf(m))
        }
    }

    private fun gasPhase(sim: Sim, rnd: Random, n: Int, curve: IntArray, errNow: (Double) -> Double, from: Int = 0) {
        for (i in from until n) for (b in 3..15) {
            val m = bandMap(rnd, b)
            val tp = tpOf(m)
            stretch(sim, rnd, "GNV", 1500 + rnd.nextInt(2000).toDouble(), m, tp / errNow(tp))
        }
    }

    /** Carro parado/lenta (rpm < 1000, injeção maior) e outliers de condução. Determinístico por seed própria. */
    private fun contaminate(sim: Sim, errNow: (Double) -> Double, parked: Boolean, outliers: Boolean) {
        val rnd = Random(777)
        if (parked) repeat(60) {
            val m = 0.30 + 0.5 * rnd.nextDouble()
            val rpm = 650 + rnd.nextInt(300).toDouble()                       // < 1000
            val fuel = if (it % 2 == 0) "GNV" else "GASOLINA"
            stretch(sim, rnd, fuel, rpm, m, tpOf(m) * (1.25 + 0.15 * rnd.nextDouble()))
        }
        if (outliers) repeat(26) {                                           // ~4% dos pares de condução
            val b = 3 + rnd.nextInt(13)
            val m = bandMap(rnd, b)
            stretch(sim, rnd, "GNV", 1700.0 + rnd.nextInt(1500), m, tpOf(m) / errNow(tpOf(m)) * (if (it % 2 == 0) 1.5 else 0.6))
        }
    }

    // ---------------- cenário completo ----------------
    private class Outcome(val result: EquivalenceResult?, val ui: JSONObject, val sim: Sim)

    private fun scenario(n: Int, native: Boolean, parked: Boolean, outliers: Boolean, seed: Long = 42): Outcome {
        val rnd = Random(seed)
        val sim = Sim()
        val curve = curveRaw()
        sim.snap(snapshot(sim.now, curve, 1, 0, 0, Random(1)), 1)   // o serviço já viu a Curva K antes de o GNV chegar
        petrolPhase(sim, rnd, n)
        gasPhase(sim, rnd, n, curve, ::errAt)
        contaminate(sim, ::errAt, parked, outliers)
        val nat = if (native) minOf(n, 10) else 0
        val (r, ui) = sim.snap(snapshot(sim.now, curve, 1, if (native) nat else 10, nat, Random(seed)), 1)
        // pareando mesma semântica do app: só a gasolina nativa existe quando a ECU não tem GNV maduro
        return Outcome(r, ui, sim)
    }

    private data class Proposal(val applies: Boolean, val raw: IntArray)

    private fun proposalOf(o: Outcome): Proposal {
        val a = o.result?.nextAction
        return if (a != null && a.kind == NextActionKind.APPLY) Proposal(true, a.refinedRaw!!.toIntArray()) else Proposal(false, curveRaw())
    }

    /** erro médio |ln(K/K_ideal)| nos pontos do eixo que ficam nas faixas plantadas (± meia faixa). */
    private fun plantedErr(raw: IntArray, curve: IntArray): Double {
        val ideal = idealRaw(curve)
        val idx = (0 until 30).filter { i -> plantedBands.any { abs(axisRaw[i] / 512.0 - nodeMs[it]) <= 1.0 } }
        return idx.map { abs(ln(raw[it] / ideal[it])) }.average()
    }

    private fun describe(o: Outcome): String {
        val a = o.result?.nextAction
        val p = o.result?.proposal
        return "kind=${a?.kind} mode=${p?.mode} telOnly=${p?.telemetryOnly} used=${p?.telemetryPairsUsed} reg=${p?.regressionBlocked} reason=${p?.reason} mature=${p?.matureCommonPoints} targets=${p?.targets?.size} telT=${p?.telemetryTargetCount} outB=${p?.telemetryOutlierBands} outP=${p?.telemetryOutlierPairs} errB=${p?.evidenceErrorBefore} errA=${p?.evidenceErrorAfter} rej=${p?.rejectedBands?.size} thin=${p?.thinBandsIgnored} inv=${p?.invalidEvidenceBands} petrolObs=${o.sim.ledger.petrolObservations().size} gasObs=${o.sim.ledger.gasObservations().size} chg=${p?.let { r -> (0 until 30).count { r.refinedRaw[it] != r.currentRaw[it] } }} text=${a?.text}"
    }

    @Test
    fun `oraculo da sessao ficticia`() {
        val curve = curveRaw()
        val ideal = idealRaw(curve)
        val out = File("build/sessao-ficticia").also { it.mkdirs() }
        println("SESSAO_FICTICIA verdade: erroPorFaixa=${bandError.joinToString(",") { "%.3f".format(it) }} nodeMs=${nodeMs.joinToString(",") { "%.1f".format(it) }}")

        // (1)(2) proposta com 50 pontos, ECU + próprios, SEM contaminação
        val clean = scenario(50, native = true, parked = false, outliers = false)
        println("SESSAO_FICTICIA limpa50: ${describe(clean)}")
        clean.result?.proposal?.targets?.forEach { println("SESSAO_FICTICIA   alvo map=${"%.3f".format(it.mapBar)} tp=${"%.2f".format(it.petrolMs)} tg=${"%.2f".format(it.gasMs)} w=${"%.2f".format(it.weight)} ratio=${"%.3f".format(it.ratio)} robust=${"%.2f".format(it.robustWeight)}") }
        println("SESSAO_FICTICIA origens=${clean.result?.proposal?.origins} gain=${clean.result?.proposal?.gain?.joinToString(",") { "%.2f".format(it) }} elast=${clean.result?.proposal?.elasticityLimit}")
        val prop = proposalOf(clean)
        for (i in 0 until 30) println("SESSAO_FICTICIA ponto#$i ms=${"%.1f".format(axisRaw[i] / 512.0)} K=${curve[i]} ideal=${ideal[i].toInt()} prop=${prop.raw[i]} erroReal=${"%.3f".format(errAt(axisRaw[i] / 512.0))}")
        assertTrue("(1) deve haver proposta: ${describe(clean)}", prop.applies)
        for (i in 0 until 30) {
            val e = errAt(axisRaw[i] / 512.0)
            val changed = prop.raw[i] != curve[i]
            if (changed) {
                assertTrue("(1) ponto $i fora da caixa ${prop.raw[i]}", prop.raw[i] in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
                assertTrue("(1) ponto $i passo ${prop.raw[i].toDouble() / curve[i]}", abs(prop.raw[i].toDouble() / curve[i] - 1) <= 0.15 + 1e-3)
                // sentido certo: se o K atual está alto (e>1) a proposta desce; baixo (e<1) sobe
                if (abs(e - 1) > 0.04) assertTrue("(1) ponto $i erro=$e sentido errado ${curve[i]}->${prop.raw[i]}", (e > 1) == (prop.raw[i] < curve[i]))
            }
        }
        for (b in plantedBands) {
            val i = (0 until 30).minByOrNull { abs(axisRaw[it] / 512.0 - nodeMs[b]) }!!
            assertTrue("(1) faixa plantada $b (ponto $i) deve mudar", prop.raw[i] != curve[i])
        }
        // (2) pontos com erro real <= 3,5% e longe das faixas plantadas (> 1,5 ms) não mudam
        for (i in 0 until 30) {
            val ms = axisRaw[i] / 512.0
            if (abs(errAt(ms) - 1) <= 0.035 && plantedBands.all { abs(ms - nodeMs[it]) > 1.5 })
                assertEquals("(2) ponto $i dentro da margem não pode mudar", curve[i], prop.raw[i])
        }

        // (3) com e sem contaminação a proposta é igual
        val dirty = scenario(50, true, parked = true, outliers = true)
        val parkedOnly = scenario(50, true, parked = true, outliers = false)
        println("SESSAO_FICTICIA suja50: ${describe(dirty)}")
        assertTrue("(3) parado: proposta idêntica", proposalOf(parkedOnly).raw.contentEquals(prop.raw))
        assertTrue("(3) parado+outliers: proposta idêntica", proposalOf(dirty).raw.contentEquals(prop.raw))

        // (4) mais pontos, mais perto do ideal (ECU + próprios e só próprios)
        println("SESSAO_FICTICIA TABELA erro_nas_faixas_plantadas antes=${"%.4f".format(plantedErr(curve, curve))}")
        for (native in listOf(true, false)) {
            val errs = listOf(10, 25, 50).map { n ->
                // média de 3 sementes: o erro de UMA realização de ruído não é monótono, a tendência é
                val es = listOf(42L, 43L, 44L).map { seed ->
                    val o = scenario(n, native, parked = true, outliers = true, seed = seed)
                    plantedErr(proposalOf(o).raw, curve).also {
                        if (seed == 42L) println("SESSAO_FICTICIA TABELA native=$native n=$n ${describe(o)}")
                    }
                }
                es.average().also { println("SESSAO_FICTICIA TABELA native=$native n=$n erroMedio3sementes=${"%.4f".format(it)} (antes ${"%.4f".format(plantedErr(curve, curve))})") }
            }
            assertTrue("(4) native=$native erro com 50 pontos <= com 10: $errs", errs[2] <= errs[0] + 0.002)
            assertTrue("(4) native=$native n=50 melhor que sem proposta", errs[2] < plantedErr(curve, curve) - 0.005)
        }

        // timeline para a prova visual + (5) RESET gasolina
        timeline(out, curve)
    }

    @Test
    fun `comparacao dos motores e erro nao crescente a cada aumento de pontos`() {
        val curve = curveRaw()
        val table = StringBuilder("native\tn\tplatina\tdiamante\n")
        for (native in listOf(true, false)) {
            val errors = listOf(10, 25, 50).map { n ->
                val outcomes = listOf(42L, 43L, 44L).map { scenario(n, native, true, true, it) }
                val current = outcomes.map { plantedErr(proposalOf(it).raw, curve) }.average()
                val legacy = outcomes.map {
                    val r = com.omegas.prohub.equivalence.comparison.MotorComparison.platina(it.ui.getJSONObject("snapshot"), it.sim.ledger, it.sim.runtime)
                    plantedErr(r.refinedRaw.toIntArray(), curve)
                }.average()
                println("COMPARACAO_REFINO native=$native n=$n platina=$legacy diamante=$current")
                table.append("$native\t$n\t$legacy\t$current\n")
                current
            }
            // Não confundir aumento bruto de frames com confiança: o gate pode conservar K
            // inalterado quando a amostra adicional fica incoerente. Nunca piorar a base.
            assertTrue("native=$native nenhuma proposta piora a base: $errors",
                errors.all { it <= errors.first() + 1e-8 })
            assertTrue("native=$native ao menos uma densidade de evidência melhora a base: $errors",
                errors.minOrNull()!! < errors.first() - 0.001)
        }
        File("build/sessao-ficticia/comparacao.tsv").also { it.parentFile.mkdirs() }.writeText(table.toString())
    }

    @Test
    fun `mesmos criterios de seguranca e contaminacao para a Platina`() {
        val clean = scenario(50, true, false, false)
        val parked = scenario(50, true, true, false)
        val dirty = scenario(50, true, true, true)
        fun legacy(o: Outcome) = com.omegas.prohub.equivalence.comparison.MotorComparison.platina(o.ui.getJSONObject("snapshot"), o.sim.ledger, o.sim.runtime).refinedRaw.toIntArray()
        val raw = legacy(clean)
        val curve = curveRaw()
        val changed = raw.indices.filter { raw[it] != curve[it] }
        val outside = changed.count { raw[it] !in 12288..19661 }
        val step = changed.count { abs(raw[it].toDouble() / curve[it] - 1) > 0.151 }
        val wrongWay = changed.count { abs(errAt(axisRaw[it] / 512.0) - 1) > .04 && ((errAt(axisRaw[it] / 512.0) > 1) != (raw[it] < curve[it])) }
        val unnecessary = changed.count { i ->
            val ms = axisRaw[i] / 512.0
            abs(errAt(ms) - 1) <= .035 && plantedBands.all { abs(ms - nodeMs[it]) > 1.5 }
        }
        println("ORACULO_PLATINA altered=${changed.size} outside=$outside step=$step wrongWay=$wrongWay unnecessary=$unnecessary parkedInvariant=${raw.contentEquals(legacy(parked))} outlierInvariant=${raw.contentEquals(legacy(dirty))}")
        // A cópia histórica é avaliada, não 'consertada' para passar; os critérios da produção estão no teste completo.
        assertTrue("comparação produziu 30 K", raw.size == 30)
    }

    @Test
    fun `pontos proprios corrigem uma base nativa ainda enviesada`() {
        val curve = IntArray(30) { 16384 }
        val snap = snapshot(1L, curve, 1, 10, 10, Random(1))
        val n = NativeBands.fromSnapshot(snap)!!
        // A nativa estima +8%; condução independente pede +10%, com verdade constante conhecida.
        val input = AutoMatchRefinedEngine.Input(axisRaw, curve, n.petrolTimeRaw, n.petrolMapRaw, n.petrolCounts,
            IntArray(18) { Math.round(n.petrolTimeRaw[it] * 1.08).toInt() }, n.petrolMapRaw, n.gasCounts,
            holdMinStepLog = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG)
        fun error(r: AutoMatchRefinedEngine.Result) = (9..17).map { abs(ln(r.refinedRaw[it] / (16384.0 * 1.1))) }.average()
        val baseline = error(AutoMatchRefinedEngine.refine(input))
        val errors = listOf(10, 25, 50).map { count ->
            val pairs = List(count) { i -> val tp = 4.5 + 4.5 * i / (count - 1.0); tp to tp * 1.1 }
            val r = AutoMatchRefinedEngine.refine(input.copy(telemetryPairs = pairs, telemetryEpisodes = pairs.indices.toList()))
            error(r).also { println("BASE_NATIVA_ENVIESADA n=$count error=$it baseline=$baseline") }
        }
        assertTrue("50 pontos próprios melhoram a base", errors.last() < baseline)
        for (i in 1 until errors.size) assertTrue("10→25→50 pontos totais: $errors", errors[i] <= errors[i - 1])
    }

    @Test
    fun `reset nativo nao apaga evidencias proprias quando Curva K nao mudou`() {
        for ((pc, gc) in listOf(0 to 10, 10 to 0, 0 to 0)) {
            val o = scenario(50, true, false, false)
            val before = proposalOf(o)
            assertTrue(before.applies)
            repeat(2) {
                val (r, _) = o.sim.snap(snapshot(o.sim.now, curveRaw(), 1, pc, gc, Random(42)), 1)
                assertEquals("reset de buffers nativos não invalida pares próprios", NextActionKind.APPLY, r?.nextAction?.kind)
                assertTrue("mesma Curva K produz mesma proposta própria",
                    before.raw.contentEquals(r!!.nextAction.refinedRaw!!.toIntArray()))
            }
            val (restored, _) = o.sim.snap(snapshot(o.sim.now, curveRaw(), 1, 10, 10, Random(42)), 1)
            assertEquals(NextActionKind.APPLY, restored?.nextAction?.kind)
            assertTrue(before.raw.contentEquals(restored!!.nextAction.refinedRaw!!.toIntArray()))
        }
    }

    private fun timeline(out: File, curve: IntArray) {
        val rnd = Random(42)
        val sim = Sim()
        val moments = JSONObject()
        val proposals = mutableMapOf<String, Proposal>()
        fun take(name: String, enable: Int, pc: Int, gc: Int) {
            val (r, ui) = sim.snap(snapshot(sim.now, curve, enable, pc, gc, Random(5)), enable)
            val p = proposalOf(Outcome(r, ui, sim))
            moments.put(name, ui)
            proposals[name] = p
            println("SESSAO_FICTICIA MOMENTO $name apply=${p.applies} petrolObs=${sim.ledger.petrolObservations().size} gasObs=${sim.ledger.gasObservations().size} ${describe(Outcome(r, ui, sim))}")
            File(out, "$name.json").writeText(ui.toString())
        }
        take("1-antes-de-aprender", 0, 0, 0)
        // AutoCal liga: ECU aprende pouco; poucos pontos próprios
        petrolPhase(sim, rnd, 4); gasPhase(sim, rnd, 4, curve, ::errAt)
        take("2-aprendendo", 1, 3, 2)
        petrolPhase(sim, rnd, 50, 4); gasPhase(sim, rnd, 50, curve, ::errAt, 4)
        take("3-proposta-pronta", 1, 10, 10)
        // RESET gasolina: contadores e buffers da gasolina zeram; o resto fica.
        take("4-depois-do-reset-gasolina", 1, 0, 10)
        // repovoando: a gasolina volta a aprender (contador sobe)
        take("5-repovoando", 1, 6, 10)
        take("6-repovoado", 1, 10, 10)
        // (5) RESET gasolina: a proposta ancorada na nativa some (nada velho em cache) e volta ao repovoar, igual à anterior.
        val m = { n: String -> moments.getJSONObject(n).getJSONObject("equivalence") }
        assertTrue("(5) antes do reset há proposta", proposals.getValue("3-proposta-pronta").applies)
        assertTrue("(5) depois do reset a proposta some", !proposals.getValue("4-depois-do-reset-gasolina").applies)
        assertTrue("(5) repovoado volta a proposta", proposals.getValue("6-repovoado").applies)
        assertTrue("(5) repovoado volta aos mesmos K", proposals.getValue("3-proposta-pronta").raw.contentEquals(proposals.getValue("6-repovoado").raw))
        assertTrue("(5) depois do reset a proposta mudou (nao ficou a velha em cache)", m("4-depois-do-reset-gasolina").toString() != m("3-proposta-pronta").toString())
        assertEquals("(5) repovoado volta a proposta da nativa", m("3-proposta-pronta").toString().length / 50, m("6-repovoado").toString().length / 50)
        File(out, "momentos.json").writeText(moments.toString())
    }
}
