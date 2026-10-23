package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato Kotlin <-> Python: a sessão sintética abaixo gera SEMPRE o mesmo resumo, guardado em
 * `tests/fixtures/resumos/` e lido pelo analisador (`tools/sessoes/analisar_resumos.py`). Mudou o formato de propósito?
 * Rode este teste com OMEGAS_REGEN_FIXTURES=1 (ele regrava os dois arquivos) e ajuste o analisador e `docs/EXPORT-SESSOES.md`.
 */
class SessionSummaryContractTest {
    private val dir = File("../tests/fixtures/resumos")

    private fun synthetic(): Pair<JSONObject, String> {
        val wall0 = 1_790_000_000_000L
        val ctx = SummaryContext(
            sessionId = "session_2026-10-23_10-00-00_abcd1234", sessionUuid = "00000000-0000-4000-8000-000000000001",
            startMs = wall0, appVersion = "8.2.0", commit = "0123456789ab", deviceId = "abcd1234",
            mapHashAtStart = "mapa-cache-0001", curveHashAtStart = "curva-cache-0001", previousGnvEndRaw = 230,
        )
        val b = SessionSummaryBuilder(ctx)
        var seq = 0L
        fun ev(type: String, data: JSONObject, t: Long, src: String = "t") = b.observe(++seq, type, src, data, wall0 + t)
        ev("session_started", JSONObject().put("reason", "teste"), 0, "native")
        ev("k_read_map", JSONObject().put("hash", "mapa-lido-0001"), 100)
        ev("k_read_curve", JSONObject().put("hash", "curva-lida-0001"), 150)
        for (i in 0..180) {
            val t = i * 1000L
            val moving = i in 20..160
            ev("telemetry", JSONObject().put("captured_elapsed_ms", 7_000_000L + t).put("rpm", if (moving) 2200 else 900)
                .put("level_raw", 60 + i / 30).put("fuel", "GNV").put("petrol_ms", 3.2).put("load_bar", if (moving) 0.62 else 0.41)
                .put("gas_ms_diagnostic", if (moving) 9.5 else 5.0).put("gas_pulse_present", true).put("plausible", true), t, "mp48")
            ev("car_info", JSONObject().put("complete", true).put("speed", if (moving) 54 else 0).put("odo", 45_000 + i / 70)
                .put("cruisingRange", 300 - i / 20).put("avgFuelCont", 11.7), t, "multimidia")
        }
        ev("car_info_raw", JSONObject().put("channelsActive", 2).put("channelsTotal", 17)
            .put("tx", JSONObject().put("6", JSONObject().put("n", 181).put("bytes", 72))), 5_000, "multimidia")
        ev("session_stopped", JSONObject().put("reason", "manual"), 181_000, "native")
        return b.summary(final = true) to b.seriesCsv()
    }

    @Test
    fun `resumo sintetico bate com o fixture que o analisador Python le`() {
        val (summary, csv) = synthetic()
        val json = File(dir, "sessao_sintetica_final.json")
        val series = File(dir, "sessao_sintetica_series_1hz.csv")
        if (!json.isFile || !series.isFile || System.getenv("OMEGAS_REGEN_FIXTURES") == "1") {
            dir.mkdirs()
            json.writeText(summary.toString(2))
            series.writeText(csv)
        }
        val expected = JSONObject(json.readText())
        assertTrue("o resumo mudou; veja o comentario da classe\n" + summary.toString(2), expected.similar(JSONObject(summary.toString())))
        assertTrue("a serie mudou", series.readText() == csv)
        assertTrue(JSONArray(summary.getJSONObject("series").getJSONArray("columns").toString()).length() == SessionSummaryBuilder.COLUMNS.size)
    }
}
