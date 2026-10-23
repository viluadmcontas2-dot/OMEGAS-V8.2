package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Sessão órfã (corte de energia ao desligar a chave): fechada na próxima abertura, marcada "sem stop", com a última prova de vida. */
class OrphanSessionsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun dir(recording: Boolean = true): File {
        val d = temporary.newFolder()
        File(d, "manifest.json").writeText(JSONObject().put("sessionId", d.name).put("recording", recording).put("createdAtMs", 1_000L)
            .put("stoppedAtMs", 0L).put("stopReason", "").toString())
        return d
    }

    private fun events(d: File, vararg lines: String) = File(d, "events_0001.jsonl").writeText(lines.joinToString("\n") + "\n")

    @Test
    fun `manifesto que ficou recording true vira sem stop com a hora do ultimo batimento`() {
        val d = dir()
        File(d, "raw").mkdirs()
        File(d, "raw/heartbeat.0001.tsv").writeText("#omegas-raw-v2 stream=heartbeat\n4884\t5000\t75\t0\t75\t0\n9884\t10000\t150\t0\t150\t0\n99")
        events(d, """{"sequence":1,"recordedAtMs":2000}""")
        assertTrue(OrphanSessions.closeIfOrphan(d))
        val m = JSONObject(File(d, "manifest.json").readText())
        assertFalse(m.getBoolean("recording"))
        assertEquals(10_000L, m.getLong("stoppedAtMs"))
        assertEquals(OrphanSessions.REASON, m.getString("stopReason"))
        assertTrue(m.getBoolean("orphan"))
        assertEquals("batimento", m.getString("lastLifeSign"))
    }

    @Test
    fun `sem batimento usa o ultimo evento inteiro e ignora linha cortada`() {
        val d = dir()
        File(d, "events_0001.jsonl").writeText("""{"sequence":1,"recordedAtMs":2000}""" + "\n" + """{"sequence":2,"recordedAtMs":3000}""" + "\n" + """{"sequence":3,"recor""")
        assertTrue(OrphanSessions.closeIfOrphan(d))
        val m = JSONObject(File(d, "manifest.json").readText())
        assertEquals(3_000L, m.getLong("stoppedAtMs"))
        assertEquals("evento", m.getString("lastLifeSign"))
    }

    @Test
    fun `sem nenhuma prova de vida a hora de parada e desconhecida e nao zero`() {
        val d = dir()
        assertTrue(OrphanSessions.closeIfOrphan(d))
        val m = JSONObject(File(d, "manifest.json").readText())
        assertTrue(m.isNull("stoppedAtMs"))
        assertEquals("nenhuma", m.getString("lastLifeSign"))
    }

    @Test
    fun `sessao que fechou direito nao e tocada e fechar duas vezes e idempotente`() {
        val ok = dir(recording = false)
        val before = File(ok, "manifest.json").readText()
        assertFalse(OrphanSessions.closeIfOrphan(ok))
        assertEquals(before, File(ok, "manifest.json").readText())
        val d = dir()
        events(d, """{"sequence":1,"recordedAtMs":2000}""")
        assertTrue(OrphanSessions.closeIfOrphan(d))
        val once = File(d, "manifest.json").readText()
        assertFalse(OrphanSessions.closeIfOrphan(d))
        assertEquals(once, File(d, "manifest.json").readText())
    }
}
