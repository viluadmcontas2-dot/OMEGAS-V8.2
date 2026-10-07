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

    /** Simula o espelho: reserva o número (nunca reaproveitado), escreve o ZIP e só então registra. */
    private fun publish(
        dir: File,
        final: Boolean = false,
        commit: Boolean = true,
        published: MutableSet<String> = mutableSetOf(),
    ): Pair<Int, Map<String, ByteArray>>? {
        val plan = SessionPartPlanner.plan(dir, final) ?: return null
        SessionPartPlanner.reserve(plan) { SessionPartPlanner.fileName("s1", it) in published }
        val name = SessionPartPlanner.fileName("s1", plan)
        assertTrue("nome já publicado nunca é reescrito: $name", published.add(name))
        val out = ByteArrayOutputStream()
        SessionPartPlanner.writeZip(plan, "s1", out)
        if (commit) SessionPartPlanner.commit(dir, plan)
        return plan.part to unzip(out.toByteArray())
    }

    /** Junta as partes como o leitor faz: cada trecho é escrito na posição fromByte do arquivo de origem. */
    private fun events(parts: List<Map<String, ByteArray>>): ByteArray {
        var image = ByteArray(0)
        parts.forEach { part ->
            part.filterKeys { it.contains("events_0001.from_") }.forEach { (key, bytes) ->
                val from = key.substringAfter("from_").substringBefore(".").toInt()
                if (image.size < from + bytes.size) image = image.copyOf(from + bytes.size)
                System.arraycopy(bytes, 0, image, from, bytes.size)
            }
        }
        return image
    }

    @Test
    fun `corte de energia no meio da direcao nao perde eventos nem reescreve parte publicada`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val published = mutableSetOf<String>()
        val log = File(dir, "events_0001.jsonl")
        File(dir, "manifest.json").writeText("{\"v\":1}")
        log.writeText("{\"seq\":1}\n{\"seq\":2}\n")
        val p1 = publish(dir, published = published)!!
        assertEquals(1, p1.first)
        assertTrue(p1.second.containsKey("s1/manifest.json"))

        // Mais eventos e uma linha cortada pela queda de energia.
        log.appendText("{\"seq\":3}\n{\"seq\":4}\n{\"se")
        // Queda ENTRE publicar e registrar: a parte 2 já saiu; a nova tentativa usa o PRÓXIMO número
        // (antes regravava a mesma _parte_0002 com faixa maior via "rwt").
        val lost = publish(dir, commit = false, published = published)!!
        assertEquals(2, lost.first)
        val p3 = publish(dir, published = published)!!
        assertEquals(3, p3.first)
        val info3 = JSONObject(String(p3.second.getValue("s1/parte.json")))
        assertEquals("[2]", info3.getJSONArray("supersededParts").toString())
        assertEquals("[1]", info3.getJSONArray("committedPartsBefore").toString())

        // Nada novo: nenhuma parte.
        assertNull(publish(dir, published = published))

        // App reabre depois do corte; a linha cortada é completada e sai na parte final.
        log.appendText("q\":5}\n")
        val p4 = publish(dir, final = true, published = published)!!
        assertEquals(4, p4.first)
        val info4 = JSONObject(String(p4.second.getValue("s1/parte.json")))
        assertTrue(info4.getBoolean("final"))
        assertEquals("[1,3,4]", info4.getJSONArray("expectedParts").toString())
        assertEquals(log.length(), info4.getJSONObject("cumulativeToByte").getLong("events_0001.jsonl"))

        // Juntando as partes necessárias (sem a 2) = exatamente o arquivo local, byte a byte.
        assertArrayEquals(log.readBytes(), events(listOf(p1.second, p3.second, p4.second)))
        // E com a 2 (publicada antes da queda) também: as faixas sobrepostas têm bytes idênticos.
        assertArrayEquals(log.readBytes(), events(listOf(p1.second, lost.second, p3.second, p4.second)))
        dir.deleteRecursively()
    }

    @Test
    fun `estado corrompido nao volta para a parte 1 e reenvia tudo do inicio`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val published = mutableSetOf<String>()
        val log = File(dir, "events_0001.jsonl")
        log.writeText("{\"seq\":1}\n")
        repeat(3) { index ->
            log.appendText("{\"seq\":${index + 2}}\n")
            publish(dir, published = published)
        }
        File(dir, SessionPartPlanner.STATE_FILE).writeText("{\"nextPart\": 4, \"offs")
        log.appendText("{\"seq\":9}\n")
        val recovered = publish(dir, published = published)!!
        assertEquals(4, recovered.first)
        val info = JSONObject(String(recovered.second.getValue("s1/parte.json")))
        assertTrue(info.getBoolean("stateRecovered"))
        // Sem saber o que já saiu, a parte recuperada leva o arquivo inteiro: duplica, nunca perde.
        assertArrayEquals(log.readBytes(), events(listOf(recovered.second)))
        dir.deleteRecursively()
    }

    @Test
    fun `estado corrompido sem reservas pula nomes ja publicados`() {
        val dir = Files.createTempDirectory("sess").toFile()
        File(dir, "events_0001.jsonl").writeText("{\"seq\":1}\n")
        File(dir, SessionPartPlanner.STATE_FILE).writeText("lixo")
        val plan = SessionPartPlanner.plan(dir, final = false)!!
        val existing = setOf("s1_parte_0001.zip", "s1_parte_0002.zip", "s1_parte_0003.zip")
        SessionPartPlanner.reserve(plan) { SessionPartPlanner.fileName("s1", it) in existing }
        assertEquals(4, plan.part)
        assertEquals("s1_parte_0004.zip", SessionPartPlanner.fileName("s1", plan))
        dir.deleteRecursively()
    }

    @Test
    fun `registro do estado e atomico e sobrevive a temporario completo sem rename`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        log.writeText("{\"seq\":1}\n")
        val first = SessionPartPlanner.plan(dir, final = false)!!
        SessionPartPlanner.reserve(first) { false }
        SessionPartPlanner.commit(dir, first)
        assertTrue(File(dir, SessionPartPlanner.STATE_FILE).isFile)
        assertTrue("sem temporário sobrando", !File(dir, SessionPartPlanner.STATE_FILE + ".tmp").exists())

        // Queda entre gravar o temporário (completo, com fsync) e o rename: o temporário vale.
        log.appendText("{\"seq\":2}\n")
        val second = SessionPartPlanner.plan(dir, final = false)!!
        SessionPartPlanner.reserve(second) { false }
        File(dir, SessionPartPlanner.STATE_FILE + ".tmp").writeText(SessionPartPlanner.stateAfter(second).toString())
        File(dir, SessionPartPlanner.STATE_FILE).writeText("{corrompido")
        assertNull("nada novo depois da parte 2", SessionPartPlanner.plan(dir, final = false))
        log.appendText("{\"seq\":3}\n")
        assertEquals(3, SessionPartPlanner.plan(dir, final = false)!!.part)
        dir.deleteRecursively()
    }

    @Test
    fun `reserva repetida do mesmo numero e recusada`() {
        val dir = Files.createTempDirectory("sess").toFile()
        File(dir, "events_0001.jsonl").writeText("{\"seq\":1}\n")
        val a = SessionPartPlanner.plan(dir, final = false)!!
        val b = SessionPartPlanner.plan(dir, final = false)!!
        SessionPartPlanner.reserve(a) { false }
        SessionPartPlanner.reserve(b) { false }
        assertEquals(1, a.part)
        assertEquals("dois planos concorrentes nunca recebem o mesmo nome", 2, b.part)
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

    @Test
    fun `sessao fechada sem parte antes vira um ZIP so com a sessao inteira`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        val log2 = File(dir, "events_0002.jsonl")
        File(dir, "manifest.json").writeText("{\"v\":1}")
        File(dir, "RESUMO.md").writeText("# resumo")
        log.writeText("{\"seq\":1}\n{\"seq\":2}\n")
        log2.writeText("{\"seq\":3}\n")

        val plan = SessionPartPlanner.plan(dir, final = true)!!
        assertTrue(plan.single)
        assertEquals("s_1.zip", SessionPartPlanner.fileName("s 1", plan))
        val out = ByteArrayOutputStream()
        SessionPartPlanner.writeZip(plan, "s 1", out)
        val zip = unzip(out.toByteArray())
        assertArrayEquals(log.readBytes(), zip.getValue("s_1/events_0001.jsonl"))
        assertArrayEquals(log2.readBytes(), zip.getValue("s_1/events_0002.jsonl"))
        assertTrue(zip.containsKey("s_1/RESUMO.md"))
        assertTrue(zip.containsKey("s_1/manifest.json"))
        val info = JSONObject(String(zip.getValue("s_1/parte.json")))
        assertTrue(info.getBoolean("single"))
        assertTrue(info.getBoolean("final"))
        assertTrue("nenhum nome 'from_' no ZIP único", zip.keys.none { it.contains(".from_") })

        // Depois de publicado e registrado não sobra nada para publicar.
        SessionPartPlanner.commit(dir, plan)
        assertNull(SessionPartPlanner.plan(dir, final = true))
        dir.deleteRecursively()
    }

    @Test
    fun `sessao antiga que ja tem parte publicada continua em partes`() {
        val dir = Files.createTempDirectory("sess").toFile()
        val log = File(dir, "events_0001.jsonl")
        log.writeText("{\"seq\":1}\n")
        val first = SessionPartPlanner.plan(dir, final = false)!!
        assertEquals(false, first.single)
        assertEquals("s_1_parte_0001.zip", SessionPartPlanner.fileName("s 1", first))
        SessionPartPlanner.commit(dir, first)
        log.appendText("{\"seq\":2}\n")
        val last = SessionPartPlanner.plan(dir, final = true)!!
        assertEquals("a última parte de sessão antiga não vira ZIP único", false, last.single)
        assertEquals("s_1_parte_0002.zip", SessionPartPlanner.fileName("s 1", last))
        dir.deleteRecursively()
    }
}
