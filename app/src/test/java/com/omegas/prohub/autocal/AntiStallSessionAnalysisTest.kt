package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AntiStallSessionAnalysisTest {
    private fun frame(rpm: Int) = JSONObject().put("rpm", rpm).put("fuel_byte", 144).put("sample_state", "SAMPLE_ACCEPTED")
    private fun params(value: Int) = JSONObject().put("params", JSONObject()
        .put("ANTI_STALLO", JSONObject().put("state", "OK").put("values", JSONArray(listOf(value,100,1500,2,20)))))

    @Test fun missingIsNotZeroAndStartIsNotStall() {
        val a = AntiStallSessionAnalysis()
        a.observe("telemetry", frame(0)); a.observe("telemetry", frame(200)); a.observe("telemetry", frame(850))
        a.observe("telemetry", JSONObject())
        assertEquals(0, a.finish("s").getInt("quedas"))
    }
    @Test fun episodeGroupsAndRecoversByReadings() {
        val a = AntiStallSessionAnalysis()
        a.observe("param_snapshot", params(1600))
        listOf(1800,900,200,0,0,850).forEach { a.observe("telemetry", frame(it)) }
        val s = a.finish("s")
        assertEquals(1, s.getInt("quedas"))
        val e = s.getJSONArray("eventos").getJSONObject(0)
        assertEquals("MOTOR_PAROU", e.getString("tipo"))
        assertEquals("DESCONHECIDO", e.getString("movimento"))
        assertEquals(2, e.getInt("leiturasAteRecuperar"))
        assertEquals(1600, s.getInt("maiorAjusteComQueda"))
        assertEquals(0, s.getJSONObject("changes").length())
    }
    @Test fun gapDoesNotInventRecovery() {
        val a = AntiStallSessionAnalysis()
        listOf(1800,0).forEach { a.observe("telemetry", frame(it)) }
        a.observe("telemetry", frame(0).put("sample_state", "TELEMETRY_GAP"))
        a.observe("telemetry", frame(900))
        val e = a.finish("s").getJSONArray("eventos").getJSONObject(0)
        assertEquals("LEITURA_INTERROMPIDA", e.getString("desfecho"))
        assertTrue(e.isNull("leiturasAteRecuperar"))
    }
    @Test fun failedWriteInvalidatesSetting() {
        val a = AntiStallSessionAnalysis()
        a.observe("param_snapshot", params(1600))
        a.observe("anti_stallo_params_write", JSONObject().put("outcome", "READBACK_FAILED"))
        listOf(1800,0,850).forEach { a.observe("telemetry", frame(it)) }
        assertTrue(a.finish("s").isNull("maiorAjusteComQueda"))
    }

    @Test fun replayAllRealSessions() {
        val file = listOf(java.io.File("tests/fixtures/sessoes-uteis/telemetria.csv.gz"),
            java.io.File("../tests/fixtures/sessoes-uteis/telemetria.csv.gz")).first { it.isFile }
        val sessions = LinkedHashMap<String, AntiStallSessionAnalysis>()
        var count = 0
        java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().use { reader ->
            val keys = reader.readLine().split(',')
            reader.forEachLine { line ->
                val cells = line.split(',')
                val fields = keys.zip(cells).toMap()
                val row = JSONObject()
                fields.forEach { (key, value) ->
                    row.put(key, if (value.isBlank()) JSONObject.NULL else value.toDoubleOrNull() ?: value)
                }
                sessions.getOrPut(fields.getValue("sessao")) { AntiStallSessionAnalysis() }.observe("telemetry", row)
                count++
            }
        }
        assertEquals(177457, count)
        assertEquals(89, sessions.size)
        assertEquals(31, sessions.entries.sumOf { it.value.finish(it.key).getInt("quedas") })
        assertEquals(28, sessions.entries.sumOf { it.value.finish(it.key).getInt("paradasDoMotor") })
        sessions.forEach { (id, a) -> assertEquals(a.finish(id).toString(), a.finish(id).toString()) }
    }
}
