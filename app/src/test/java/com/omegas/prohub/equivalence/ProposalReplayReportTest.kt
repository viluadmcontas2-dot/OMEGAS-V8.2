package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoCalAcquisition
import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.EvidencePairs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classe 3: replay das sessões reais de 06/10 (as jsonl.gz em fixtures/autocal/real) pelo MESMO caminho da produção
 * (EquivalenceRuntime.evaluate + EquivalencePhases.observe). Imprime, por snapshot amostrado, qual guarda do Refino
 * bloqueia a proposta. Análise pura: nada aqui toca a ECU. O relatório vai para o artefato do CI.
 */
class ProposalReplayReportTest {
    /** Eixo PETR_INJ_TBP de fábrica (idêntico em todas as sessões gravadas; as jsonl não o trazem). */
    private val axisRaw = intArrayOf(
        256, 512, 768, 1024, 1280, 1536, 1792, 2048, 2304, 2560, 2816, 3072, 3328, 3584, 3840, 4096, 4352, 4608, 4864, 5120,
        5632, 6144, 6656, 7168, 7680, 8192, 8704, 9216, 10240, 11264,
    )

    data class Row(
        val session: String, val seq: Int, val apply: Boolean, val line: String,
    )

    private fun load(name: String): List<JSONObject> {
        val file = listOf("../fixtures/autocal/real/$name.jsonl.gz", "fixtures/autocal/real/$name.jsonl.gz")
            .map(::File).first { it.exists() }
        return GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
        }.sortedBy { it.getLong("t") }
    }

    private fun fixSnapshot(data: JSONObject): JSONObject {
        val fields = JSONArray()
        val src = data.getJSONArray("fields")
        for (i in 0 until src.length()) fields.put(src.getJSONObject(i))
        fields.put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", JSONArray(axisRaw.toList())))
        return JSONObject().put("capturedAtMs", data.optLong("capturedAtMs")).put("fields", fields)
    }

    private fun guards(ledger: EquivalenceLedger, runtime: EquivalenceRuntime, acquisition: JSONObject?): String {
        val reference = runtime.references.current() ?: runtime.references.provisional(acquisition)
        val ecuRef = reference?.let { EvidencePairs.cleanReference(it.points.map { p -> p.mapBar to p.petrolMs }) } ?: emptyList()
        val pairs = EvidencePairs.build(ledger.petrolObservations(), ledger.gasObservations(), ecuRef)
            .filter { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }
        val cand = pairs.filter { it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS && it.gasPetrolMs > 0.0 }
        val candidates = cand.map { it.petrolRefMs to it.gasPetrolMs }
        val plausible = AutoMatchRefinedEngine.plausibleIndices(candidates, cand.map { it.episode })
        val keptSet = plausible.kept.toSet()
        val perBand = EquivalenceLedger.BANDS.map { (lo, hi) ->
            val before = candidates.count { (tp, _) -> tp >= lo && tp < hi }
            val after = candidates.indices.count { it in keptSet && candidates[it].first >= lo && candidates[it].first < hi }
            "${lo}-${hi}:${before}/${after}"
        }
        val before8 = EquivalenceLedger.BANDS.count { (lo, hi) -> candidates.count { (tp, _) -> tp >= lo && tp < hi } >= 8 }
        val after8 = EquivalenceLedger.BANDS.count { (lo, hi) -> candidates.indices.count { it in keptSet && candidates[it].first >= lo && candidates[it].first < hi } >= 8 }
        return "pares=${candidates.size} faixas>=8pares antes=$before8 depois=$after8 [antes/depois ${perBand.joinToString(" ")}] " +
            "foraRazao=${plausible.outliers} parRejeitado=${plausible.rejectedPairs}"
    }

    private fun replay(name: String): List<Row> {
        val events = load(name)
        var now = events.first().getLong("t")
        val ledger = EquivalenceLedger(null) { now }
        val runtime = EquivalenceRuntime(null) { now }
        val phasesReal = EquivalencePhases(null, { now }, { now })
        val phasesDone = EquivalencePhases(null, { now }, { now })
        val snaps = events.filter { it.getString("type") == "autocal_native_snapshot" }
        val sample = snaps.indices.filter { it % 10 == 9 || it == snaps.lastIndex }.toSet()
        val rows = ArrayList<Row>()
        var snapIndex = -1
        var fuelNow: String? = null
        for (event in events) {
            now = event.getLong("t")
            val data = event.getJSONObject("data")
            when (event.getString("type")) {
                "telemetry" -> {
                    fun fin(k: String) = data.optDouble(k, 0.0).takeIf { it.isFinite() } ?: 0.0
                    fuelNow = data.optString("fuel", "DESCONHECIDO")
                    ledger.accept(
                        EquivalenceLedger.Frame(now, fuelNow, fin("rpm"), fin("load_bar"), fin("petrol_ms"), fin("gas_ms_diagnostic")),
                    )
                    runtime.onFrame(now, fuelNow, fin("rpm"), fin("load_bar"), fin("petrol_ms"), 1L)
                }
                "autocal_native_snapshot" -> {
                    snapIndex++
                    val snapshot = fixSnapshot(data)
                    val acquisition = AutoCalAcquisition.fromSnapshot(snapshot)
                    // Como no serviço: toda leitura alinha o livro à Curva K (curva nova = GNV antigo sai do livro).
                    EquivalenceEngine.curveFromSnapshot(snapshot)?.let { runtime.alignCurve(ledger, phasesReal, it.second, now) }
                    // O dono salva a gasolina da ECU como referência (sem isso o cérebro só pede "Salvar a referência").
                    if (runtime.references.current() == null && ReferenceStore.pointsFrom(acquisition).isNotEmpty()) {
                        runtime.freeze(acquisition, phasesReal)
                    }
                    if (snapIndex !in sample) continue
                    val enabled = EquivalenceReplaySupportHex.first(data, "AUTO_CAL_ENABLE") ?: 1
                    val result = runtime.evaluate(ledger, phasesReal, snapshot, acquisition, true) { null } ?: continue
                    val index = ledger.index()
                    val monitorReal = JSONObject().put("autoCalEnabled", enabled)
                    val monitorDone = JSONObject().put("autoCalEnabled", enabled).put("autoMatchCount", 3).put("maxAutomatch", 3)
                    val real = phasesReal.observe(true, monitorReal, acquisition, index, JSONObject(), 0, fuelNow)
                    val done = phasesDone.observe(true, monitorDone, acquisition, index, JSONObject(), 0, fuelNow)
                    val p = result.proposal
                    val bands = index.optJSONArray("bands") ?: JSONArray()
                    val interior = (0 until bands.length()).count { bands.optJSONObject(it)?.optBoolean("interiorCovered", false) == true }
                    val judged = result.points.count { it.state != PointState.SEM_DADOS && it.state != PointState.APRENDENDO }
                    val line = "$name snap#$snapIndex refCongelada=${runtime.references.current() != null} nextAction=${result.nextAction.kind} proposal.mode=${p?.mode} " +
                        "proposal.reason=${p?.reason} telemetryOnly=${p?.telemetryOnly} regBlocked=${p?.regressionBlocked} " +
                        "deadBand=${p?.deadBandPoints} used=${p?.telemetryPairsUsed} " +
                        "reasonCode(sem contador)=${real.optString("reasonCode")} ecuDone(sem contador)=${real.optBoolean("ecuDone")} " +
                        "reasonCode(ECU concluida assumida)=${done.optString("reasonCode")} " +
                        "interiorCovered=$interior/${bands.length()} pontosJulgados=$judged | ${guards(ledger, runtime, acquisition)}"
                    result.nextAction.takeIf { it.kind == NextActionKind.APPLY }?.let { a ->
                        // Proposta nunca sai da caixa de K nem passa do passo máximo de 15% por ponto.
                        val cur = a.currentRaw!!; val ref = a.refinedRaw!!
                        for (i in cur.indices) if (ref[i] != cur[i]) {
                            assertTrue("$name snap#$snapIndex ponto $i fora da caixa: ${ref[i]}",
                                ref[i] in AutoMatchRefinedEngine.MIN_RAW_PROPOSAL..AutoMatchRefinedEngine.MAX_RAW_PROPOSAL)
                            assertTrue("$name snap#$snapIndex ponto $i passo ${ref[i].toDouble() / cur[i]}",
                                kotlin.math.abs(ref[i].toDouble() / cur[i] - 1.0) <= 0.15 + 1e-3)
                        }
                    }
                    if (name.startsWith("util")) {
                        // Carro quase todo parado (marcha lenta): nenhum ponto desses vira evidência nem proposta.
                        assertEquals("$name snap#$snapIndex", 0, p?.telemetryPairsUsed ?: 0)
                    }
                    rows += Row(name, snapIndex, result.nextAction.kind == NextActionKind.APPLY, line)
                }
            }
        }
        return rows
    }

    @Test
    fun `relatorio de qual guarda bloqueia a proposta nas sessoes reais`() {
        for (name in listOf("pista_2026-10-06_2030", "util_2026-10-06_1921")) {
            val rows = replay(name)
            rows.forEach { println("REPLAY_REFINO ${it.line}") }
            println("REPLAY_REFINO_RESUMO $name amostras=${rows.size} comProposta(APPLY)=${rows.count { it.apply }}")
            if (name.startsWith("pista")) assertTrue("sessão real da pista deve gerar >= 1 proposta", rows.any { it.apply })
            if (name.startsWith("util")) assertEquals("sessão parada não propõe", 0, rows.count { it.apply })
        }
    }
}

private object EquivalenceReplaySupportHex {
    fun first(data: JSONObject, key: String): Int? {
        val fields = data.optJSONArray("fields") ?: return null
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            if (f.optString("key") == key) return f.optJSONArray("rawValues")?.optInt(0)
        }
        return null
    }
}
