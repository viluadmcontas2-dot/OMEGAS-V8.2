package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class SessionPartPlannerTest {
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; out[e.name] = zip.readBytes() }
        }
        return out
    }

    private fun publish(dir: File, final: Boolean = false, commit: Boolean = true): Pair<Int, Map<String, ByteArray>>? {
        val plan = SessionPartPlanner.plan(dir, final) ?: return null
        val out = ByteArrayOutputStream()
        SessionPartPlanner.writeZip(plan, "s1", out)
        if (commit) SessionPartPlanner.commit(dir, plan)
        return plan.part to unzip(out.toByteArray())
    }

    private fun events(parts: List<Map<String, ByteArray>>): ByteArray =
        parts.flatMap { p -> p.filterKeys { it.contains("events_0001.from_") }.toSortedMap(compareBy { it.substringAfter("from_").substringBefore(".").toLong() }).values }
            .fold(ByteArray(0)) { acc, b -> acc + b }

    @Test
    fun `corte de energia no meio da direcao nao perde nem duplica eventos`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        File(dir, "manifest.json").writeText("{\"v\":1}")
        log.writeText("{\"seq\":1}\n{\"seq\":2}\n")
        val p1 = publish(dir)!!
        assertEquals(1, p1.first)
        assertTrue(p1.second.containsKey("s1/manifest.json"))

        // Mais eventos e uma linha cortada pela queda de energia.
        log.appendText("{\"seq\":3}\n{\"seq\":4}\n{\"se")
        // Queda ENTRE publicar e registrar: a mesma parte (mesmo nome) é refeita, sem "(1)".
        val lost = publish(dir, commit = false)!!
        assertEquals(2, lost.first)
        val p2 = publish(dir)!!
        assertEquals(2, p2.first)
        assertTrue("manifest não mudou, não repete", !p2.second.containsKey("s1/manifest.json"))

        // Nada novo: nenhuma parte.
        assertNull(publish(dir))

        // App reabre depois do corte; a linha cortada é completada e sai na parte final.
        log.appendText("q\":5}\n")
        val p3 = publish(dir, final = true)!!
        assertEquals(3, p3.first)
        assertTrue(JSONObject(String(p3.second.getValue("s1/parte.json"))).getBoolean("final"))

        // Juntando as partes publicadas = exatamente o arquivo local, byte a byte.
        assertArrayEquals(log.readBytes(), events(listOf(p1.second, p2.second, p3.second)))
        dir.deleteRecursively()
    }

    @Test
    fun `arquivo sem linha completa ainda nao publica nada`() {
        val dir = Files.createTempDirectory("sess").toFile()
        File(dir, "events_0001.jsonl").writeText("{\"seq\":1")
        assertEquals(0L, SessionPartPlanner.completeLength(File(dir, "events_0001.jsonl")))
        assertNull(publish(dir))
        assertEquals("s_1_parte_0007.zip", SessionPartPlanner.partName("s 1", 7))
        dir.deleteRecursively()
    }

    // Dono, 2026-10-13: 90 min de gravação saíram em 49 ZIPs de 100 KB. Sessão que fecha (ou é recuperada depois de queda) sem
    // nenhuma parte já publicada sai em UM ZIP só, com os eventos inteiros e o nome da sessão.
    @Test
    fun `sessao fechada sem parte anterior sai em um zip so`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        File(dir, "manifest.json").writeText("{\"v\":1}")
        log.writeText("{\"seq\":1}\n{\"seq\":2}\n{\"seq\":3}\n")
        val plan = SessionPartPlanner.plan(dir, final = true)!!
        assertTrue(plan.single)
        assertEquals("s_1.zip", plan.fileName("s 1"))
        val out = ByteArrayOutputStream()
        SessionPartPlanner.writeZip(plan, "s 1", out)
        val zip = unzip(out.toByteArray())
        assertArrayEquals(log.readBytes(), zip.getValue("s_1/events_0001.jsonl"))
        assertTrue(JSONObject(String(zip.getValue("s_1/parte.json"))).getBoolean("single"))
        dir.deleteRecursively()
    }

    @Test
    fun `sessao que ja publicou partes continua em partes mesmo ao fechar`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        log.writeText("{\"seq\":1}\n")
        publish(dir)!! // parte 1 (sessão antiga, de versão anterior)
        log.appendText("{\"seq\":2}\n")
        val plan = SessionPartPlanner.plan(dir, final = true)!!
        assertTrue(!plan.single)
        assertEquals("s_1_parte_0002.zip", plan.fileName("s 1"))
        dir.deleteRecursively()
    }

    // Dono, 2026-10-23: o download pelo Drive quebra acima de ~8 MB; parte bruta tem teto de bytes brutos (o JSON comprime 8x ou mais).
    @Test
    fun `parte bruta respeita o teto de bytes e nada se perde nem duplica`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        val lines = (1..40).joinToString("") { "{\"seq\":$it,\"pad\":\"xxxxxxxxxx\"}\n" }
        log.writeText(lines)
        val cap = 200L
        val parts = ArrayList<Map<String, ByteArray>>()
        val finals = ArrayList<Boolean>()
        while (true) {
            val plan = SessionPartPlanner.plan(dir, final = true, maxRawBytes = cap) ?: break
            assertTrue("parte com ${plan.slices.sumOf { it.to - it.from }} bytes", plan.slices.sumOf { it.to - it.from } <= cap)
            assertTrue(!plan.single || plan.final)
            val out = ByteArrayOutputStream()
            SessionPartPlanner.writeZip(plan, "s1", out)
            SessionPartPlanner.commit(dir, plan)
            parts += unzip(out.toByteArray())
            finals += plan.final
        }
        assertTrue(parts.size > 3)
        assertEquals(listOf(true), finals.takeLast(1))
        assertTrue(finals.dropLast(1).none { it })
        assertArrayEquals(log.readBytes(), events(parts))
        dir.deleteRecursively()
    }

    @Test
    fun `sessao pequena continua em um zip so mesmo com teto`() {
        val dir = Files.createTempDirectory("sess").toFile()
        File(dir, "events_0001.jsonl").writeText("{\"seq\":1}\n{\"seq\":2}\n")
        val plan = SessionPartPlanner.plan(dir, final = true, maxRawBytes = 1_000L)!!
        assertTrue(plan.single)
        assertTrue(plan.final)
        dir.deleteRecursively()
    }

    @Test
    fun `parte carrega identidade estavel uuid inicio indice ultima parte e hash do conteudo`() {
        val dir = Files.createTempDirectory("sess").toFile()
        File(dir, "manifest.json").writeText(JSONObject().put("sessionId", "s1").put("sessionUuid", "uuid-1").put("createdAtMs", 1_790_000_000_000L).toString())
        File(dir, "events_0001.jsonl").writeText("{\"seq\":1}\n{\"seq\":2}\n")
        val p1 = publish(dir)!!
        File(dir, "events_0001.jsonl").appendText("{\"seq\":3}\n")
        val p2 = publish(dir, final = true)!!
        val a = JSONObject(String(p1.second.getValue("s1/parte.json")))
        val b = JSONObject(String(p2.second.getValue("s1/parte.json")))
        assertEquals("uuid-1", a.getString("sessionUuid"))
        assertEquals(1_790_000_000_000L, a.getLong("startMs"))
        assertEquals(1, a.getInt("partIndex"))
        assertEquals(false, a.getBoolean("isLast"))
        assertTrue(a.isNull("totalParts"))
        assertEquals(2, b.getInt("partIndex"))
        assertEquals(true, b.getBoolean("isLast"))
        assertEquals(2, b.getInt("totalParts"))
        assertEquals(10L, b.getLong("rawBytes"))
        assertEquals(64, a.getString("contentSha256").length)
        assertTrue(a.getString("contentSha256") != b.getString("contentSha256"))
        dir.deleteRecursively()
    }
}
