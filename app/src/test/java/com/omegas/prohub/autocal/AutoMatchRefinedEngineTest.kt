package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Equivalência Refinada contra sessões reais do proprietário
 * (fixtures/autocal/real, extraídas por tools/autocal_refine/extract_session.py).
 */
class AutoMatchRefinedEngineTest {
    private fun fixture(name: String): JSONObject {
        val file = listOf("../fixtures/autocal/real/$name.json.gz", "fixtures/autocal/real/$name.json.gz")
            .map(::File).first { it.exists() }
        return JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() })
    }

    private fun snapshot(name: String, sequence: Int): JSONObject {
        val snapshots = fixture(name).getJSONArray("snapshots")
        for (index in 0 until snapshots.length()) {
            val snap = snapshots.getJSONObject(index)
            if (snap.getInt("sequence") == sequence) return snap
        }
        error("snapshot $sequence ausente em $name")
    }

    private fun factors(analysis: JSONObject, key: String): List<Double> {
        val points = analysis.getJSONArray("points")
        return (0 until points.length()).map { points.getJSONObject(it).getInt(key) / 16384.0 }
    }

    @Test
    fun `sessao de referencia remove buraco e corcova preservando o nivel`() {
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot("ref_2026-10-01_1719", 95))

        assertTrue(analysis.getBoolean("available"))
        assertEquals("EQUIVALENCE", analysis.getString("refinementMode"))
        assertFalse(analysis.getBoolean("automatic"))
        assertTrue(analysis.getBoolean("manualOnly"))
        val old = factors(analysis, "currentRaw")
        val new = factors(analysis, "calculatedRaw")
        val after = analysis.getJSONObject("metricsAfter")
        val before = analysis.getJSONObject("metricsBefore")
        assertTrue(after.getDouble("maxElasticity") <= AutoMatchRefinedEngine.E_MAX + 0.01)
        assertTrue(before.getDouble("maxElasticity") > 1.5)
        assertEquals("HIGH", analysis.getString("joltRiskBefore"))
        assertEquals("LOW", analysis.getString("joltRiskAfter"))
        assertTrue(analysis.getDouble("evidenceErrorAfter") < analysis.getDouble("evidenceErrorBefore") * 0.5)
        assertTrue(new[9] > old[9] + 0.03)
        assertTrue(new[17] < old[17] - 0.04)
        assertTrue(new.zip(old).all { (n, o) -> abs(n / o - 1.0) <= 0.1501 })
        val deviation = (4..18).sumOf { abs(new[it] / old[it] - 1.0) } / 15.0
        assertTrue("desvio médio $deviation", deviation < 0.04)
    }

    @Test
    fun `outlier repetido nao pode fabricar cobertura de uma faixa`() {
        // Quatro leituras coerentes ocupam só o primeiro terço de 3,0–4,5 ms.
        // Duas leituras incoerentes repetidas no segundo terço não podem ser usadas
        // para fingir que a faixa ganhou cobertura real.
        val pairs = listOf(
            3.10 to 3.41,
            3.20 to 3.52,
            3.28 to 3.608,
            3.34 to 3.674,
            3.60 to 2.34,
            3.70 to 2.405,
        )
        val sameEpisode = List(pairs.size) { 100_001 }

        val plausible = AutoMatchRefinedEngine.plausibleIndices(pairs, sameEpisode)

        assertTrue(
            "dois pontos incoerentes repetidos não podem completar sozinhos o segundo terço da faixa",
            plausible.kept.isEmpty(),
        )
    }

    @Test
    fun `sem bandas comuns maduras o motor nao inventa correcao`() {
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot("automatch_2026-10-01_1301", 1401))

        assertEquals("POLISH", analysis.getString("refinementMode"))
        assertEquals(0, analysis.getInt("changedCount"))
    }

    @Test
    fun `buffers incoerentes no tempo caem para polimento sem usar evidencia`() {
        val snap = snapshot("ref_2026-10-01_1719", 95)
        snap.put("coherenceGroups", org.json.JSONArray().put(
            JSONObject().put("key", "ACQUISITION_CURRENT").put("coherent", false),
        ))
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snap)

        assertEquals("POLISH", analysis.getString("refinementMode"))
        assertFalse(analysis.getBoolean("buffersCoherent"))
    }

    @Test
    fun `refino nunca empobrece a baixa onde o motor apaga`() {
        for ((name, seq) in listOf("ref_2026-10-01_1719" to 95)) {
            val points = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot(name, seq)).getJSONArray("points")
            for (i in 0 until points.length()) {
                val p = points.getJSONObject(i)
                if (p.getDouble("referenceTimeMs") < AutoMatchSnapshotAnalysis.LOW_GUARD_MS) {
                    assertTrue("ponto ${p.getDouble("referenceTimeMs")} ms empobreceu",
                        p.getInt("calculatedRaw") >= p.getInt("currentRaw"))
                }
            }
        }
    }
}

