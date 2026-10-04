package com.omegas.prohub.equivalence

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Paridade Kotlin ↔ oráculo Python (tools/equivalence_oracle, escrito do zero) em todas as sessões reais de
 * fixtures/autocal/real. A JVM chama o Python (que existe no runner do CI), então a paridade roda de verdade.
 */
class EquivalenceOracleParityTest {
    private val root = EquivalenceReplaySupport.repoRoot()
    private val script = File(root, "tools/equivalence_oracle/equivalence_oracle.py")

    private fun runOracle(fixture: File): JSONObject? {
        return try {
            val process = ProcessBuilder("python3", "-B", script.absolutePath, fixture.absolutePath)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) fail("oráculo Python falhou ($code): $output")
            JSONObject(output)
        } catch (e: IOException) {
            if (System.getenv("CI") == "true") throw AssertionError("python3 ausente no CI", e)
            assumeNoException(e)
            null
        }
    }

    private fun close(label: String, a: Double?, b: Double?, tolerance: Double) {
        if (a == null || b == null) {
            assertEquals("$label: um nulo e outro não ($a × $b)", a == null, b == null)
            return
        }
        assertTrue("$label: $a × $b", abs(a - b) <= tolerance * maxOf(1.0, abs(b)))
    }

    private fun optNumber(o: JSONObject, key: String): Double? = if (o.isNull(key)) null else o.getDouble(key)

    private fun compareCurve(label: String, py: JSONObject, kt: OwnCurve) {
        val cells = py.getJSONArray("cells")
        assertEquals("$label células", kt.cells.size, cells.length())
        for (i in kt.cells.indices) {
            val p = cells.getJSONObject(i)
            val k = kt.cells[i]
            assertEquals("$label[$i] samples", p.getInt("samples"), k.samples)
            assertEquals("$label[$i] source", p.getString("source"), k.source.name)
            close("$label[$i] petrolMs", optNumber(p, "petrolMs"), k.petrolMs, 1e-6)
            close("$label[$i] dispersion", optNumber(p, "dispersion"), k.dispersion, 1e-9)
            close("$label[$i] divergence", optNumber(p, "divergence"), k.divergence, 1e-6)
        }
    }

    @Test
    fun `Kotlin e oraculo Python concordam na curva propria nos pontos e no indice das sessoes reais`() {
        val files = File(root, "fixtures/autocal/real").listFiles { f -> f.name.endsWith(".json.gz") }!!.sortedBy { it.name }
        assertTrue("fixtures reais ausentes", files.size >= 3)
        for (file in files) {
            val name = file.name.removeSuffix(".json.gz")
            val py = runOracle(file) ?: return
            val refSeq = if (py.isNull("refSeq")) null else py.getInt("refSeq")
            val kSeq = py.getInt("curveSeq")

            val (axis, k) = EquivalenceReplaySupport.curve(name, kSeq)
            val ledger = EquivalenceReplaySupport.ledger(name)
            val usage = UsageMeter(null)
            EquivalenceReplaySupport.frames(name).forEach { usage.accept(it.t, it.fuel, it.rpm, it.map, 1L) }
            val reference = refSeq?.let { EquivalenceReplaySupport.reference(name, it) }
            val kt = EquivalenceEngine.evaluate(
                EquivalenceInput(
                    axis, k, reference, null, ledger.petrolObservations(), ledger.gasObservations(),
                    ExperienceMeter(null).reading(), usage.reading(),
                ),
            )

            assertEquals("$name pontos de Referência", py.getInt("referencePoints"), reference?.points?.size ?: 0)
            compareCurve("$name ownPetrol", JSONObject().put("cells", py.getJSONArray("ownPetrol")), kt.ownPetrol)
            compareCurve("$name ownGas", JSONObject().put("cells", py.getJSONArray("ownGas")), kt.ownGas)
            val points = py.getJSONArray("points")
            assertEquals("$name nº de pontos", 30, kt.points.size)
            for (i in 0 until 30) {
                val p = points.getJSONObject(i)
                val q = kt.points[i]
                assertEquals("$name ponto $i estado", p.getString("state"), q.state.name)
                close("$name ponto $i mixture", optNumber(p, "mixture"), q.mixture, 1e-6)
                close("$name ponto $i tolerance", p.getDouble("tolerance"), q.tolerance, 1e-6)
                close("$name ponto $i usage", p.getDouble("usage"), q.usage, 1e-9)
                assertEquals("$name ponto $i samples", p.getInt("samples"), q.samples)
                assertEquals("$name ponto $i episodes", p.getInt("episodes"), q.episodes)
            }
            close("$name index", optNumber(py, "index"), kt.index, 1e-9)
            close("$name judgedUsage", py.getDouble("judgedUsage"), kt.judgedUsage, 1e-9)
            assertEquals("$name coverage", py.getInt("coverage"), kt.coverage)
            println("PARITY $name ref=$refSeq k=$kSeq index=${kt.index} coverage=${kt.coverage} OK")
        }
    }
}
