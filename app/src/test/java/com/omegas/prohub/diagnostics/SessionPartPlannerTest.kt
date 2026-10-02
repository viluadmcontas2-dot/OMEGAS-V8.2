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
}
