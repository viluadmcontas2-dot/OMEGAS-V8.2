#!/usr/bin/env python3
"""Replay do corpus real (tests/fixtures/sessoes-uteis) pelo Ledger + MapSuggestionPolicy de produção (Kotlin).

Era um JUnit (MapSuggestionPolicyReplayTest) que dependia de python3 e do corpus grande dentro do Gradle; virou script
(não roda no `unittest discover`, o nome não começa com test_). Os vereditos concretos ficam em MapSuggestionPolicyTest.

Uso:  KOTLINC_HOME=build/jvm-tools/kotlinc JSON_JAR=/caminho/json-20240303.jar python3 -B tests/replay_sugestao_mapa.py
Sem as variáveis, só confere que o corpus carrega e gera a mesma entrada duas vezes.
Prova: duas passagens completas dão exatamente as mesmas decisões, e nenhuma é automática.
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.sessoes.corpus import eventos, telemetria  # noqa: E402

KOTLIN_MAIN = r'''
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.MapSuggestionPolicy
import org.json.JSONArray
import org.json.JSONObject

fun main(args: Array<String>) {
    val input = java.io.File(args[0]).readLines()
    val map = JSONObject().put("sessionConfirmed", true).put("complete", true).put("sessionId", 1L)
        .put("rows", JSONArray(List(12) { List(12) { 150 } }))
    var session = ""
    var ledger = EquivalenceLedger(null) { 0L }
    val out = StringBuilder()
    var pairs = 0
    fun observe() {
        val groups = ledger.attribution()
        pairs += groups.sumOf { it.pairs }
        val d = MapSuggestionPolicy.decide(groups, map, 1L)
        check(!d.getBoolean("automatic"))
        out.append(d.toString()).append('\n')
    }
    for (line in input) {
        val row = JSONArray(line)
        if (session != row.getString(0)) { observe(); session = row.getString(0); ledger = EquivalenceLedger(null) { 0L } }
        val t = row.getLong(1)
        if (row.getString(3) == "RESET") { observe(); ledger = EquivalenceLedger(null) { 0L } }
        else ledger.accept(EquivalenceLedger.Frame(t, row.getString(3), row.getDouble(4), row.getDouble(5), row.getDouble(6),
            row.getDouble(7), if (row.isNull(8)) null else row.getDouble(8) >= 60.0))
    }
    observe()
    check(pairs > 0) { "o replay deve exercitar grupos reais" }
    print(out)
    System.err.println("replay: ${input.size} registros, $pairs pares classificados")
}
'''


def corpus_lines():
    rows = [(e['sessao'], int(e['recordedAtMs']), 0, 'RESET') for e in eventos()]
    for r in telemetria():
        if all(r[k] is not None for k in ('t_ms', 'rpm', 'map_bar', 'petrol_ms')):
            rows.append((r['sessao'], int(r['t_ms']), 1, r['fuel'], r['rpm'], r['map_bar'], r['petrol_ms'], r['gas_ms'] or 0, r['water_c']))
    return [json.dumps(row) for row in sorted(rows, key=lambda r: r[:3])]


def main():
    first, second = corpus_lines(), corpus_lines()
    assert first == second and len(first) > 100_000, 'corpus incompleto ou não determinístico'
    home, jar = os.environ.get('KOTLINC_HOME'), os.environ.get('JSON_JAR')
    if not home or not jar:
        print(f'corpus: {len(first)} registros, entrada determinística; Kotlin não configurado (KOTLINC_HOME/JSON_JAR)')
        return 0
    m = ROOT / 'app/src/main/java/com/omegas/prohub'
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / 'KWriteManager.kt').write_text('package com.omegas.prohub.calibration\nobject KWriteManager { const val MIN_SAFE_K = 100; const val ROW_COUNT = 12; const val COLUMN_COUNT = 12 }\n')
        (tmp / 'Main.kt').write_text(KOTLIN_MAIN)
        (tmp / 'input.jsonl').write_text('\n'.join(first))
        sources = [tmp / 'KWriteManager.kt', tmp / 'Main.kt'] + [m / p for p in (
            'calibration/KMapPhysicalAxes.kt', 'calibration/MapKManualPlanner.kt', 'util/AtomicJsonFile.kt',
            'autocal/EquivalenceLedger.kt', 'autocal/TypicalInjectionBands.kt', 'autocal/PresentationMedian.kt',
            'autocal/AutoMatchRefinedEngine.kt', 'autocal/EquivalenceAttribution.kt', 'autocal/LocalMapPlanner.kt',
            'autocal/MapSuggestionPolicy.kt')]
        subprocess.run([f'{home}/bin/kotlinc', '-nowarn', '-cp', jar, *map(str, sources), '-d', str(tmp / 'r.jar')], check=True)
        cp = f"{tmp / 'r.jar'}:{jar}:{home}/lib/kotlin-stdlib.jar"
        runs = [subprocess.run(['java', '-cp', cp, 'MainKt', str(tmp / 'input.jsonl')], check=True, capture_output=True, text=True).stdout
                for _ in range(2)]
    assert runs[0] == runs[1], 'mesmo corpus deu decisões diferentes'
    print(f'replay determinístico: {len(first)} registros, {runs[0].count(chr(10))} decisões')
    return 0


if __name__ == '__main__':
    sys.exit(main())
