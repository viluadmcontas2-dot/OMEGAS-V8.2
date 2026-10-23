package com.omegas.prohub.diagnostics

import org.json.JSONArray
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
import java.util.zip.ZipInputStream

/** Pacote autoexplicado: o LEIA-ME cita todo arquivo e todo campo; um agente "cego" acha tudo só pelo índice. */
class SessionGuideTest {
    @get:Rule val temporary = TemporaryFolder()
    private val wall0 = 1_790_000_000_000L
    private val elapsed0 = 4_000_000L

    private fun rows() = JSONArray().also { r -> repeat(12) { i -> r.put(JSONArray().also { c -> repeat(12) { j -> c.put(130 + i + j) } }) } }

    /** Sessão sintética completa em disco: telemetria, mapa lido, gravação do dono, fim. Devolve a pasta da sessão. */
    private fun makeSession(root: File, id: String, startOffset: Long, level0: Int, level1: Int, write: Boolean, ledger: File?): File {
        val dir = File(root, id).apply { mkdirs() }
        File(dir, "manifest.json").writeText(JSONObject().put("format", "omegas-session-log-v1").put("sessionId", id).toString())
        File(dir, "events_0001.jsonl").writeText("{\"a\":1}\n")
        File(File(dir, "raw").apply { mkdirs() }, "tx.0001.tsv").writeText("#omegas-raw-v2\n")
        val ctx = SummaryContext(sessionId = id, sessionUuid = "uuid-$id", startMs = wall0 + startOffset, appVersion = "8.2", commit = "abc")
        val b = SessionSummaryBuilder(ctx)
        var seq = 0L
        fun ev(type: String, d: JSONObject, t: Long) = b.observe(++seq, type, "t", d, ctx.startMs + t)
        ev("session_started", JSONObject(), 0)
        ev("lifecycle", JSONObject().put("kind", "usb_connected"), 50)
        for (i in 0..60) {
            val lvl = level0 + (level1 - level0) * i / 60
            ev("telemetry", JSONObject().put("captured_elapsed_ms", elapsed0 + i * 1000L).put("rpm", 1500).put("level_raw", lvl).put("fuel", "GNV")
                .put("gas_ms_diagnostic", 8.0).put("gas_pulse_present", true).put("load_bar", 0.4), i * 1000L)
            ev("car_info", JSONObject().put("speed", 36).put("complete", true), i * 1000L)
        }
        ev("k_read_map", JSONObject().put("rows", rows()).put("hash", "H1"), 2000)
        if (write) ev("k_batch_confirmed", JSONObject().put("adjustmentId", "ADJ").put("oldHash", "H1").put("newHash", "H2").put("confirmedEvents",
            JSONArray().put(JSONObject().put("row", 2).put("column", 3).put("before", 135).put("after", 160).put("reason", "Ajuste manual confirmado na UI clean-slate"))), 3000)
        ev("session_stopped", JSONObject().put("reason", "manual"), 61_000)
        val summary = b.summary(true)
        if (ledger != null) TankLedger(ledger).record(summary)
        SessionSummaryFiles.write(dir, b, final = true, ledgerFile = ledger)
        return dir
    }

    private fun packageNames(dir: File, ledger: File?): List<String> {
        val out = ByteArrayOutputStream()
        SessionSummaryFiles.packageZip(dir, ledger, out)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> generateSequence { z.nextEntry }.forEach { names += it.name } }
        return names
    }

    private fun resolve(json: JSONObject, path: String): Boolean {
        var cur: Any? = json
        for (k in path.split('.')) { cur = (cur as? JSONObject)?.opt(k) ?: return false }
        return true
    }

    @Test
    fun `o LEIA-ME cita todos os arquivos do pacote e todos os campos do manifesto`() {
        val ledger = File(temporary.root, "tank_ledger.json")
        val dir = makeSession(temporary.newFolder("logs"), "s1", 0, 60, 100, write = true, ledger = ledger)
        val readme = File(dir, SessionGuide.README).readText()
        // 1) todo arquivo do pacote (pasta inteira + zip leve + nomes de pacotes completos) tem descrição e é citado
        val present = dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.map { it.relativeTo(dir).invariantSeparatorsPath }.toList() +
            packageNames(dir, ledger).map { it.substringAfter('/', it) } + listOf("parte.json", "export_summary.json", "events_0001.from_100.jsonl")
        present.forEach { path ->
            val doc = SessionGuide.describeFile(path)
            assertNotNull("arquivo sem descrição no catálogo: $path", doc)
            assertTrue("o LEIA-ME não cita ${doc!!.name} ($path)", readme.contains("`${doc.name}`"))
        }
        // 2) todo campo dos manifestos e documentos tem descrição e aparece no LEIA-ME
        val manifest = JSONObject(File(dir, "resumo/manifest.json").readText())
        val known = SessionGuide.FIELDS.getValue("resumo/manifest.json")
        manifest.keys().forEach { k -> assertTrue("campo do manifesto sem descrição: $k", known.containsKey(k)); assertTrue(readme.contains("`$k`")) }
        listOf(SessionGuide.MAP_JSON, SessionGuide.CMP_JSON).forEach { f ->
            JSONObject(File(dir, f).readText()).keys().forEach { k -> assertTrue("campo de $f sem descrição: $k", SessionGuide.FIELDS.getValue(f).containsKey(k)) }
        }
        SessionGuide.FIELDS.forEach { (file, fields) -> fields.keys.forEach { assertTrue("$file → $it", readme.contains("`$it`")) } }
        // 3) o manifesto lista os documentos do guia
        val guide = manifest.getJSONObject("guide")
        assertEquals("LEIA-ME.md", guide.getString("readFirst"))
        assertEquals(SessionGuide.DOCS.toSet(), (0 until guide.getJSONArray("files").length()).map { guide.getJSONArray("files").getJSONObject(it).getString("name") }.toSet())
    }

    @Test
    fun `um agente cego acha mapa ativo mudancas e comparativo so pelo indice`() {
        val logs = temporary.newFolder("logs2")
        val ledger = File(temporary.root, "tank_ledger2.json")
        makeSession(logs, "s0", 0, 40, 90, write = false, ledger = ledger)
        val dir = makeSession(logs, "s1", 10_000_000, 90, 140, write = true, ledger = ledger)
        val names = packageNames(dir, ledger).map { it.substringAfter('/') }
        val readme = File(dir, SessionGuide.README).readText()
        val index = readme.substringAfter("## 1. Comece por aqui").substringBefore("## 2.").lines().filter { it.startsWith("- ") && it.count { c -> c == '|' } == 2 }
        assertTrue(index.size >= 8)
        fun lineFor(q: String) = index.first { it.contains(q) }.removePrefix("- ").split('|').map { it.trim() }
        val (_, mapFile, mapWhere) = lineFor("mapa K e a curva K em uso")
        val (_, changeFile, changeWhere) = lineFor("O que mudou no mapa")
        val (_, cmpFile, cmpWhere) = lineFor("Melhorou ou piorou")
        listOf(mapFile, changeFile, cmpFile).forEach { assertTrue("$it não está no pacote", names.contains(it)) }
        val mapJson = JSONObject(File(dir, mapFile).readText())
        mapWhere.split(',').map { it.trim() }.forEach { assertTrue("caminho $it não existe", resolve(mapJson, it)) }
        changeWhere.split(',').map { it.trim() }.forEach { assertTrue("caminho $it não existe", resolve(JSONObject(File(dir, changeFile).readText()), it)) }
        val cmp = JSONObject(File(dir, cmpFile).readText())
        cmpWhere.split(',').map { it.trim() }.forEach { assertTrue("caminho $it não existe", resolve(cmp, it)) }
        // conteúdo: mapa ativo completo, mudança por célula com causa e tanque, comparativo com veredito
        assertEquals(12, mapJson.getJSONObject("active").getJSONObject("map").getJSONArray("rows").length())
        val change = mapJson.getJSONObject("history").getJSONArray("changes").getJSONObject(0)
        assertEquals("DONO_MANUAL", change.getString("cause"))
        assertEquals(135, change.getJSONArray("changes").getJSONObject(0).getInt("before"))
        assertEquals(160, change.getJSONArray("changes").getJSONObject(0).getInt("after"))
        assertEquals("s1", change.getString("sessionId"))
        assertTrue(change.getJSONObject("tank").has("cycleIndex"))
        assertTrue(cmp.getString("verdict") in setOf("MELHOROU", "PIOROU", "IGUAL", "INCONCLUSIVO"))
        // cada pergunta do índice aponta para arquivo que existe
        index.forEach { line -> line.removePrefix("- ").split('|')[1].split(',').map { it.trim() }.forEach { f -> assertTrue("índice aponta para $f", names.contains(f) || names.contains(f.substringAfter('/')) || f.contains("NNNN") || f == "events_NNNN.jsonl") } }
    }

    @Test
    fun `sessao curta sem mapa lido sem nivel e sem abastecimento continua valida e nunca vira zero`() {
        val root = temporary.newFolder("logs3")
        val dir = File(root, "curta").apply { mkdirs() }
        val ctx = SummaryContext(sessionId = "curta", sessionUuid = "u-curta", startMs = wall0)
        val b = SessionSummaryBuilder(ctx)
        b.observe(1, "telemetry", "t", JSONObject().put("captured_elapsed_ms", elapsed0).put("rpm", 900).put("fuel", "GNV"), wall0)
        SessionSummaryFiles.write(dir, b, final = false)
        DOCS_ALL.forEach { assertTrue("falta $it", File(dir, it).isFile) }
        val map = JSONObject(File(dir, SessionGuide.MAP_JSON).readText())
        assertFalse(map.getJSONObject("active").getJSONObject("map").getBoolean("known"))
        assertTrue(map.getJSONObject("active").getJSONObject("map").isNull("rows"))
        assertEquals("INCONCLUSIVO", JSONObject(File(dir, SessionGuide.CMP_JSON).readText()).getString("verdict"))
        val qa = File(dir, SessionGuide.QA).readText()
        assertTrue(qa.contains("Não sei"))
        val lines = File(dir, SessionGuide.TIMELINE).readText().lines().filter { it.startsWith("- ") }
        assertTrue(lines.size <= 30)
    }

    @Test
    fun `linha do tempo tem no maximo 30 linhas e traz os marcos`() {
        val dir = makeSession(temporary.newFolder("logs4"), "s", 0, 60, 100, write = true, ledger = null)
        val lines = File(dir, SessionGuide.TIMELINE).readText().lines().filter { it.startsWith("- ") }
        assertTrue(lines.size in 3..30)
        val text = lines.joinToString("\n")
        assertTrue(text.contains("Cabo da central conectado"))
        assertTrue(text.contains("Mapa K: 1 célula"))
        assertTrue(text.contains("A sessão terminou"))
    }

    private val DOCS_ALL = SessionGuide.DOCS
}
