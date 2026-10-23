package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Arquivos do resumo (`resumo/`): atômicos, com SHA-256, reconstruíveis do bruto depois de queda de energia. */
class SessionSummaryFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val wall0 = 1_790_000_000_000L
    private val elapsed0 = 9_000L

    private fun ctx(id: String = "session_2026-10-23_10-00-00_abcd1234") = SummaryContext(
        sessionId = id, sessionUuid = UUID.nameUUIDFromBytes(id.toByteArray()).toString(), startMs = wall0,
        appVersion = "8.2.test", commit = "abc123",
    )

    private fun telLine(seq: Long, t: Long, level: Int): String = JSONObject()
        .put("format", "omegas-session-log-v1").put("sequence", seq).put("recordedAtMs", wall0 + t)
        .put("type", "telemetry").put("source", "mp48")
        .put("data", JSONObject().put("captured_elapsed_ms", elapsed0 + t).put("rpm", 1500).put("level_raw", level)
            .put("fuel", "GNV").put("gas_ms_diagnostic", 8.0).put("gas_pulse_present", true).put("load_bar", 0.4))
        .toString()

    private fun builderWith(seconds: Int): SessionSummaryBuilder {
        val b = SessionSummaryBuilder(ctx())
        for (i in 0 until seconds) b.observe(i + 1L, "telemetry", "mp48",
            JSONObject().put("captured_elapsed_ms", elapsed0 + i * 1000L).put("rpm", 1500).put("level_raw", 60)
                .put("fuel", "GNV").put("gas_ms_diagnostic", 8.0).put("gas_pulse_present", true), wall0 + i * 1000L)
        return b
    }

    private fun sha(f: File) = Totals.sha256(f.readBytes())

    @Test
    fun `escreve os tres arquivos e o manifesto confere com os bytes`() {
        val dir = temporary.newFolder("s1")
        File(dir, "events_0001.jsonl").writeText("{\"a\":1}\n")
        SessionSummaryFiles.write(dir, builderWith(30), final = false)
        val out = File(dir, SessionSummaryFiles.DIR)
        assertEquals(setOf("session_summary.json", "series_1hz.csv", "manifest.json"), out.list()!!.toSet())
        val manifest = JSONObject(File(out, "manifest.json").readText())
        assertEquals("omegas-session-summary-manifest-v1", manifest.getString("schema"))
        assertEquals(false, manifest.getBoolean("final"))
        val files = manifest.getJSONArray("files")
        assertEquals(2, files.length())
        for (i in 0 until files.length()) {
            val e = files.getJSONObject(i)
            val f = File(out, e.getString("name"))
            assertEquals(f.length(), e.getLong("bytes"))
            assertEquals(sha(f), e.getString("sha256"))
        }
        assertFalse(out.list()!!.any { it.endsWith(".tmp") })
    }

    @Test
    fun `escrita final troca a periodica e inclui sha do bruto so quando pedido`() {
        val dir = temporary.newFolder("s2")
        val raw = File(dir, "events_0001.jsonl").apply { writeText("{\"a\":1}\n{\"b\":2}\n") }
        SessionSummaryFiles.write(dir, builderWith(10), final = false)
        val m1 = JSONObject(File(dir, "resumo/manifest.json").readText())
        assertTrue(m1.getJSONArray("rawFiles").getJSONObject(0).isNull("sha256"))
        SessionSummaryFiles.write(dir, builderWith(70), final = true, hashRaw = true)
        val m2 = JSONObject(File(dir, "resumo/manifest.json").readText())
        assertEquals(true, m2.getBoolean("final"))
        assertEquals(sha(raw), m2.getJSONArray("rawFiles").getJSONObject(0).getString("sha256"))
        assertEquals(raw.length(), m2.getJSONArray("rawFiles").getJSONObject(0).getLong("bytes"))
        val summary = JSONObject(File(dir, "resumo/session_summary.json").readText())
        assertEquals(true, summary.getBoolean("final"))
        assertNotNull(m2.getString("packageSha256"))
    }

    @Test
    fun `reconstroi do bruto depois de queda de energia com linha cortada e lixo`() {
        val dir = temporary.newFolder("s3")
        val id = "session_2026-10-23_10-00-00_abcd1234"
        File(dir, "manifest.json").writeText(
            JSONObject().put("format", "omegas-session-log-v1").put("sessionId", id).put("createdAtMs", wall0)
                .put("sessionUuid", "uuid-fixo").put("metadata", JSONObject().put("appVersion", "8.2.x")).toString())
        val lines = StringBuilder()
        for (i in 0 until 90) lines.append(telLine(i + 1L, i * 1000L, 60)).append('\n')
        lines.append("isto nao e json\n")
        lines.append(telLine(91, 90_000, 60).take(40)) // cortada pela queda de energia
        File(dir, "events_0001.jsonl").writeText(lines.toString())
        val b = SessionSummaryFiles.rebuild(dir)
        SessionSummaryFiles.write(dir, b, final = true, hashRaw = true)
        val summary = JSONObject(File(dir, "resumo/session_summary.json").readText())
        assertEquals("uuid-fixo", summary.getJSONObject("identity").getString("sessionUuid"))
        assertEquals(false, summary.getBoolean("closedCleanly"))
        assertEquals(89_000L, summary.getJSONObject("identity").getLong("elapsedMs"))
        assertEquals(90L, summary.getJSONObject("durations").getLong("telemetrySamples"))
        assertEquals("8.2.x", summary.getJSONObject("build").getString("appVersion"))
    }

    @Test
    fun `sem uuid no manifesto a identidade e derivada do nome da sessao e estavel`() {
        val dir = temporary.newFolder("s4")
        File(dir, "manifest.json").writeText(JSONObject().put("sessionId", "session_x").put("createdAtMs", wall0).toString())
        File(dir, "events_0001.jsonl").writeText(telLine(1, 0, 60) + "\n")
        val a = SessionSummaryFiles.rebuild(dir).summary(true).getJSONObject("identity").getString("sessionUuid")
        val b = SessionSummaryFiles.rebuild(dir).summary(true).getJSONObject("identity").getString("sessionUuid")
        assertEquals(a, b)
        assertEquals(UUID.nameUUIDFromBytes("session_x".toByteArray()).toString(), a)
    }

    @Test
    fun `pacote leve leva resumo serie manifesto e livro caixa e e bem menor que o bruto`() {
        val dir = temporary.newFolder("session_pkg")
        val rawBytes = StringBuilder()
        for (i in 0 until 600) rawBytes.append(telLine(i + 1L, i * 250L, 60)).append('\n')
        File(dir, "events_0001.jsonl").writeText(rawBytes.toString())
        SessionSummaryFiles.write(dir, builderWith(150), final = true)
        val ledger = File(temporary.root, "tank_ledger.json").apply { writeText("{\"schema\":\"omegas-tank-ledger-v1\"}") }
        val out = ByteArrayOutputStream()
        val size = SessionSummaryFiles.packageZip(dir, ledger, out)
        assertEquals(out.size().toLong(), size)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> while (true) { names += (z.nextEntry ?: break).name } }
        assertEquals(
            setOf("session_pkg/session_summary.json", "session_pkg/series_1hz.csv", "session_pkg/manifest.json", "tank_ledger.json"),
            names.toSet(),
        )
        assertTrue(size < File(dir, "events_0001.jsonl").length())
        assertEquals(size, SessionSummaryFiles.packageSize(dir, ledger))
    }

    @Test
    fun `sem resumo gravado nao ha pacote`() {
        val dir = temporary.newFolder("vazio")
        assertEquals(null, SessionSummaryFiles.packageSize(dir, null))
    }

    @Test
    fun `contexto le identidade estavel e build do manifesto do log`() {
        val dir = temporary.newFolder("s5")
        File(dir, "manifest.json").writeText(
            JSONObject().put("sessionId", "session_x").put("sessionKey", "S_abcd1234_9f8e7d6c_5000").put("bootId", "9f8e7d6c")
                .put("startElapsedMs", 5_000_000L).put("createdAtMs", wall0).put("sessionUuid", "u")
                .put("metadata", JSONObject().put("appVersion", "8.2").put("commit", "abc123def456").put("buildType", "release")).toString())
        val c = SessionSummaryFiles.contextFrom(dir)
        assertEquals("S_abcd1234_9f8e7d6c_5000", c.sessionKey)
        assertEquals("9f8e7d6c", c.bootId)
        assertEquals(5_000_000L, c.startElapsedMs)
        assertEquals("abc123def456", c.commit)
        assertEquals("release", c.buildType)
    }

    @Test
    fun `manifesto lista os fluxos brutos com colunas constantes, arquivos, tamanhos e sha so quando pedido`() {
        val dir = temporary.newFolder("s6")
        File(dir, "raw").mkdirs()
        File(dir, "raw/frames_48_01.0001.tsv").writeText("#omegas-raw-v2 stream=frames_48_01\n0\taa\n")
        File(dir, "raw/frames_48_01.0002.tsv").writeText("#omegas-raw-v2 stream=frames_48_01\n5\tbb\n")
        File(dir, "raw/tx.0001.tsv").writeText("#omegas-raw-v2 stream=tx\n0\tx\n")
        File(dir, "events_0001.jsonl").writeText("{}\n")
        SessionSummaryFiles.write(dir, builderWith(5), final = false)
        val m = JSONObject(File(dir, "resumo/manifest.json").readText())
        val streams = m.getJSONObject("streams")
        assertEquals(setOf("frames_48_01", "tx"), streams.keys().asSequence().toSet())
        assertEquals("e_ms,hex", streams.getJSONObject("frames_48_01").getString("columns"))
        assertEquals(2, streams.getJSONObject("frames_48_01").getJSONArray("files").length())
        assertTrue(streams.getJSONObject("tx").getJSONArray("files").getJSONObject(0).isNull("sha256"))
        assertEquals("omegas-raw-v2", m.getString("rawFormat"))
        SessionSummaryFiles.write(dir, builderWith(5), final = true, hashRaw = true)
        val m2 = JSONObject(File(dir, "resumo/manifest.json").readText())
        val f0 = m2.getJSONObject("streams").getJSONObject("frames_48_01").getJSONArray("files").getJSONObject(0)
        assertEquals(sha(File(dir, "raw/frames_48_01.0001.tsv")), f0.getString("sha256"))
        assertEquals(File(dir, "raw/frames_48_01.0001.tsv").length(), f0.getLong("bytes"))
    }

    @Test
    fun `nome do pacote leve para o espelho e estavel por sessao`() {
        assertEquals("session_x_resumo.zip", SessionSummaryFiles.packageName("session x"))
        assertEquals(SessionSummaryFiles.packageName("a"), SessionSummaryFiles.packageName("a"))
    }
}
