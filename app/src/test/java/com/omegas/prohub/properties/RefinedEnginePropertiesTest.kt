package com.omegas.prohub.properties

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.equivalence.EquivalenceEngine
import com.omegas.prohub.equivalence.EquivalenceInput
import com.omegas.prohub.equivalence.EquivalenceJson
import com.omegas.prohub.equivalence.ExperienceMeter
import com.omegas.prohub.equivalence.Fuel
import com.omegas.prohub.equivalence.OwnCurveFitter
import com.omegas.prohub.equivalence.RefPoint
import com.omegas.prohub.equivalence.Reference
import com.omegas.prohub.equivalence.UsageMeter
import com.omegas.prohub.properties.PropertySupport.DriveScript
import com.omegas.prohub.properties.PropertySupport.SEEDS
import com.omegas.prohub.properties.PropertySupport.assertFiniteJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/**
 * Propriedades dos motores puros com sementes fixas (Lote W, parte 3): determinismo, propostas dentro da faixa do
 * AutoMatch [0,75 ; 1,20] com passo ≤ ±15 %, T(MAP) monótono, nada de NaN/Infinity no que sai.
 */
class RefinedEnginePropertiesTest {
    private val axis30 = intArrayOf(
        256, 512, 768, 1024, 1280, 1536, 1792, 2048, 2304, 2560, 2816, 3072, 3328, 3584, 3840,
        4096, 4352, 4608, 4864, 5120, 5632, 6144, 6656, 7168, 7680, 8192, 8704, 9216, 10240, 11264,
    )

    private fun randomK(rnd: Random): IntArray {
        // curva sã: 0,85..1,25 com ondulação (pode ter degraus: é o que o refino existe para suavizar)
        val base = 0.95 + rnd.nextDouble() * 0.15
        return IntArray(30) { i -> (16384.0 * (base + 0.08 * Math.sin(i / 3.0 + rnd.nextDouble()) + rnd.nextGaussian() * 0.01)).toInt().coerceIn(8300, 32000) }
    }

    private fun bands(rnd: Random, scale: Double): Triple<IntArray, IntArray, IntArray> {
        val time = IntArray(18) { i -> (900 + i * (230 + rnd.nextInt(40)) + rnd.nextInt(30)) }
        val map = IntArray(18) { i -> (200 + i * (35 + rnd.nextInt(10)) + rnd.nextInt(15)) }
        val counts = IntArray(18) { rnd.nextInt(9) }
        val t = IntArray(18) { (time[it] * scale * (0.98 + rnd.nextDouble() * 0.04)).toInt() }
        return Triple(t, map, counts)
    }

    private fun randomInput(seed: Long): AutoMatchRefinedEngine.Input {
        val rnd = Random(seed)
        val (pt, pm, pc) = bands(rnd, 1.0)
        val (gt, gm, gc) = bands(rnd, 1.04 + rnd.nextDouble() * 0.12)
        val pairs = (0 until rnd.nextInt(80)).map { 3.0 + rnd.nextDouble() * 8.0 }.map { tp -> tp to tp * (0.95 + rnd.nextDouble() * 0.2) }
        return AutoMatchRefinedEngine.Input(
            axisRaw = axis30, mulActRaw = randomK(rnd),
            petrolTimeRaw = pt, petrolMapRaw = pm, petrolCounts = pc,
            gasTimeRaw = gt, gasMapRaw = gm, gasCounts = gc,
            telemetryPairs = pairs, telemetryEpisodes = pairs.indices.map { rnd.nextInt(5) },
            holdMinStepLog = AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG,
        )
    }

    @Test
    fun `refinar a mesma entrada e deterministico (resultado igual campo a campo)`() {
        for (seed in 1L..40L) {
            val input = randomInput(seed)
            val a = AutoMatchRefinedEngine.refine(input)
            val b = AutoMatchRefinedEngine.refine(input.copy())
            assertEquals("semente $seed", a, b)
        }
    }

    @Test
    fun `toda proposta fica na faixa do AutoMatch com passo de no maximo 15 por cento e sem numero invalido`() {
        for (seed in 1L..60L) {
            val input = randomInput(seed)
            val r = AutoMatchRefinedEngine.refine(input)
            assertEquals(30, r.refinedRaw.size)
            assertEquals(30, r.currentRaw.size)
            assertEquals(30, r.origins.size)
            for (j in 0 until 30) {
                val cur = r.currentRaw[j]
                val ref = r.refinedRaw[j]
                assertTrue("semente $seed ponto $j: $ref fora de 0..65535", ref in 0..AutoMatchRefinedEngine.MAX_RAW)
                if (ref != cur) {
                    assertTrue("semente $seed ponto $j: K $ref fora de [0,75 ; 1,20]", ref in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
                    assertTrue("semente $seed ponto $j: passo ${ref.toDouble() / cur} > ±15 %", abs(ref.toDouble() / cur - 1.0) <= 0.1501)
                } else {
                    assertTrue("ponto inalterado não pode ser MEASURED", r.origins[j] != AutoMatchRefinedEngine.Origin.MEASURED || ref == cur)
                }
            }
            r.gain.forEach { assertTrue("ganho inválido $it", it.isFinite() && it >= 0.0) }
            r.axisMs.forEach { assertTrue(it.isFinite() && it > 0.0) }
            assertTrue(r.elasticityLimit.isFinite())
            assertTrue(r.matureCommonPoints >= 0)
            r.targets.forEach { t -> assertTrue("alvo inválido", t.petrolMs.isFinite() && t.gasMs.isFinite() && t.weight.isFinite()) }
        }
    }

    @Test
    fun `sem nenhuma evidencia o motor nao inventa correcao`() {
        for (seed in 1L..30L) {
            val rnd = Random(seed)
            val k = randomK(rnd)
            val r = AutoMatchRefinedEngine.refine(AutoMatchRefinedEngine.Input(axis30, k, null, null, null, null, null, null))
            assertTrue("sem evidência não pode haver MEASURED", r.origins.none { it == AutoMatchRefinedEngine.Origin.MEASURED })
            assertEquals(AutoMatchRefinedEngine.Mode.POLISH.takeIf { r.available } ?: r.mode, r.mode)
            assertTrue(!r.equivalenceAvailable)
        }
    }

    @Test
    fun `K fora da faixa sa (lixo de leitura) e rejeitado e nunca vira proposta`() {
        val rnd = Random(77)
        val k = randomK(rnd).also { it[4] = 65535; it[11] = 0 }
        val r = AutoMatchRefinedEngine.refine(randomInput(77).copy(mulActRaw = k))
        assertEquals(AutoMatchRefinedEngine.Mode.UNAVAILABLE, r.mode)
        assertEquals(r.currentRaw, r.refinedRaw)
    }

    @Test
    fun `eixo nao crescente ou de tamanho errado e rejeitado`() {
        val input = randomInput(5)
        val broken = axis30.copyOf().also { it[10] = it[9] }
        assertEquals(AutoMatchRefinedEngine.Mode.UNAVAILABLE, AutoMatchRefinedEngine.refine(input.copy(axisRaw = broken)).mode)
        assertEquals(AutoMatchRefinedEngine.Mode.UNAVAILABLE, AutoMatchRefinedEngine.refine(input.copy(axisRaw = axis30.copyOf(29))).mode)
        assertEquals(AutoMatchRefinedEngine.Mode.UNAVAILABLE, AutoMatchRefinedEngine.refine(input.copy(mulActRaw = IntArray(30))).mode)
    }

    @Test
    fun `ajuste monotono - T(MAP) aceito nunca decresce com o MAP e a isotonica e monotona`() {
        for (seed in 1L..60L) {
            val rnd = Random(seed)
            val points = (0 until 18).map { i ->
                AutoMatchRefinedEngine.BandPoint(
                    band = i, mapBar = 0.2 + i * 0.04 + rnd.nextGaussian() * 0.01,
                    timeMs = 2.0 + i * 0.5 + rnd.nextGaussian() * 0.8, weight = 0.1 + rnd.nextDouble(), count = rnd.nextInt(9),
                )
            }.sortedBy { it.mapBar } // como bandPoints entrega: ordenado por MAP
            val (accepted, rejected) = AutoMatchRefinedEngine.monotoneFit(points)
            assertEquals(points.size, accepted.size + rejected.size)
            val ordered = accepted.sortedBy { it.mapBar }
            ordered.zipWithNext().forEach { (a, b) ->
                assertTrue("semente $seed: T(MAP) decresce ${a.fitTimeMs} → ${b.fitTimeMs}", b.fitTimeMs >= a.fitTimeMs - 1e-9)
            }
            val ys = (0 until 25).map { rnd.nextGaussian() }
            val fitted = AutoMatchRefinedEngine.pava(ys, ys.map { 0.5 + rnd.nextDouble() })
            assertEquals(ys.size, fitted.size)
            fitted.zipWithNext().forEach { (a, b) -> assertTrue(b >= a - 1e-9) }
        }
    }

    private fun evaluate(seed: Long, withReference: Boolean): Pair<String, com.omegas.prohub.equivalence.EquivalenceResult> {
        val rnd = Random(seed)
        val ledger = EquivalenceLedger(null)
        DriveScript(seed).frames(2_500).forEach { ledger.accept(it) }
        val reference = if (withReference) Reference("REF-$seed", 1L, "fp", (2..14).map { RefPoint(0.08 * it, 9.0 * 0.08 * it + 0.2, 6 + it % 5) }) else null
        val input = EquivalenceInput(
            axis30, randomK(rnd), reference, null,
            ledger.petrolObservations(), ledger.gasObservations(),
            ExperienceMeter(null).reading(), UsageMeter(null).reading(),
            null, null, AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG,
        )
        val result = EquivalenceEngine.evaluate(input)
        return EquivalenceJson.result(result, reference, null, null, nowMs = 1_000_000L).toString() to result
    }

    @Test
    fun `o cerebro e deterministico e seu JSON nunca tem numero invalido`() {
        for (seed in SEEDS) for (withReference in listOf(false, true)) {
            val (a, result) = evaluate(seed, withReference)
            val (b, _) = evaluate(seed, withReference)
            assertEquals("semente $seed ref=$withReference", a, b)
            assertFiniteJson(org.json.JSONObject(a))
            assertEquals(30, result.points.size)
            result.index?.let { assertTrue("índice fora de [0,∞): $it", it.isFinite() && it >= 0.0) }
            assertTrue("cobertura ${result.coverage}", result.coverage in 0..30)
            result.points.forEach { p ->
                assertTrue(p.kCurrent.isFinite() && p.kCurrent > 0.0)
                p.kTarget?.let { assertTrue("kTarget inválido $it", it.isFinite() && it > 0.0) }
                p.mixture?.let { assertTrue("mistura inválida $it", it.isFinite()) }
                assertTrue("tolerância ${p.tolerance}", p.tolerance >= 0.04 - 1e-12)
                assertTrue(p.samples >= 0 && p.usage in 0.0..1.0 + 1e-9)
            }
            result.nextAction.pointIndexes.forEach { assertTrue(it in 0..29) }
            result.proposal?.let { prop ->
                for (j in 0 until 30) {
                    val cur = prop.currentRaw[j]
                    val ref = prop.refinedRaw[j]
                    assertTrue("proposta $ref fora de [0,75 ; 1,20]", ref == cur || ref in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
                }
            }
        }
    }

    @Test
    fun `Curva Propria - T(MAP) monotona, celulas finitas e determinismo`() {
        for (seed in SEEDS) for (fuel in Fuel.values()) {
            val ledger = EquivalenceLedger(null)
            DriveScript(seed).frames(2_000).forEach { ledger.accept(it) }
            val observations = if (fuel == Fuel.GASOLINA) ledger.petrolObservations() else ledger.gasObservations()
            val reference = Reference("R", 1L, "fp", (2..14).map { RefPoint(0.08 * it, 9.0 * 0.08 * it + 0.2, 8) })
            for (prior in listOf<Reference?>(null, reference)) {
                val a = OwnCurveFitter.fit(observations, fuel, prior)
                val b = OwnCurveFitter.fit(observations, fuel, prior)
                assertEquals(a.cells, b.cells)
                val known = a.cells.mapNotNull { it.petrolMs }
                known.forEach { assertTrue("ms inválido $it", it.isFinite() && it > 0.0) }
                known.zipWithNext().forEach { (x, y) -> assertTrue("semente $seed $fuel: T(MAP) decresce $x → $y", y >= x - 1e-9) }
                a.cells.forEach { c -> assertTrue(c.samples >= 0 && c.dispersion.isFinite() && c.mapBar.isFinite()) }
            }
        }
    }

    @Test
    fun `eixo invalido no cerebro devolve resultado vazio e nenhuma acao de gravar`() {
        val input = EquivalenceInput(
            IntArray(30) { 500 }, IntArray(30) { 16384 }, null, null, emptyList(), emptyList(),
            ExperienceMeter(null).reading(), UsageMeter(null).reading(),
        )
        val r = EquivalenceEngine.evaluate(input)
        assertTrue(r.points.isEmpty())
        assertEquals(com.omegas.prohub.equivalence.NextActionKind.NOTHING, r.nextAction.kind)
        assertEquals(null, r.proposal)
    }
}
