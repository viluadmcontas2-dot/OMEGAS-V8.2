package com.omegas.prohub.autocal

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Corpus real pelo carregador canônico; Ledger e política de produção, sem cópia Python do algoritmo. */
class MapSuggestionPolicyReplayTest {
    @Test fun realCorpusThroughLedgerIsDeterministicAcrossTwoRuns() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "tools/sessoes/corpus.py").isFile }
        // Conservador: TODO evento do corpus corta a aquisição (inclusive logs/leituras sem mudança).
        // Não mistura sessões nem presume continuidade em calibração não observada.
        val script = """
from tools.sessoes.corpus import telemetria, eventos
import json
rows = []
for e in eventos():
    rows.append((e['sessao'], int(e['recordedAtMs']), 0, 'RESET'))
for r in telemetria():
    if all(r[k] is not None for k in ('t_ms', 'rpm', 'map_bar', 'petrol_ms')):
        rows.append((r['sessao'], int(r['t_ms']), 1, r['fuel'], r['rpm'], r['map_bar'], r['petrol_ms'], r['gas_ms'] or 0, r['water_c']))
for row in sorted(rows, key=lambda r: r[:3]):
    print(json.dumps(row))
""".trimIndent()
        val process = ProcessBuilder("python3", "-B", "-c", script).directory(root).start()
        val input = process.inputStream.bufferedReader().readLines()
        assertEquals(process.errorStream.bufferedReader().readText(), 0, process.waitFor())
        assertTrue("corpus completo carregado", input.size > 100_000)
        // Mapa neutro de teste: o corpus compacto não oferece mapa vivo coerente por par.
        val map = JSONObject().put("sessionConfirmed", true).put("complete", true).put("rows", JSONArray(List(12) { List(12) { 150 } }))
        fun replay(): List<String> {
            var session = ""
            var now = 0L
            var ledger = EquivalenceLedger(clock = { now })
            val decisions = mutableListOf<String>()
            var pairsSeen = 0
            fun observe() {
                val groups = ledger.attribution()
                pairsSeen += groups.sumOf { it.pairs }
                val decision = MapSuggestionPolicy.decide(groups, map)
                assertTrue(decision.similar(MapSuggestionPolicy.decide(groups, map)))
                assertFalse(decision.getBoolean("automatic"))
                decisions += decision.toString()
            }
            input.forEach { line ->
                val row = JSONArray(line)
                if (session != row.getString(0)) {
                    observe()
                    session = row.getString(0)
                    ledger = EquivalenceLedger(clock = { now })
                }
                now = row.getLong(1)
                if (row.getString(3) == "RESET") {
                    observe()
                    ledger = EquivalenceLedger(clock = { now }) // corta também a janela estável e referências ambíguas
                } else {
                    ledger.accept(EquivalenceLedger.Frame(now, row.getString(3), row.getDouble(4), row.getDouble(5),
                        row.getDouble(6), row.getDouble(7), if (row.isNull(8)) null else row.getDouble(8) >= 60.0))
                }
            }
            observe()
            assertTrue("o replay deve exercitar grupos reais, não apenas ausência", pairsSeen > 0)
            println("W2 replay: ${input.size} registros, ${decisions.size} decisões, $pairsSeen pares classificados")
            return decisions
        }
        assertEquals(replay(), replay())
    }
}
