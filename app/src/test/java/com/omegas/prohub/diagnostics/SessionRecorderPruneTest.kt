package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Trava da poda das sessões: sem cópia pública confirmada a pasta não sai pela contagem, mas sai pelo teto total
 * ([SessionRecorder.SESSIONS_TOTAL_LIMIT_BYTES]), das mais antigas para as mais novas; a ativa nunca.
 */
class SessionRecorderPruneTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun session(name: String, bytes: Int, ageMinutes: Long, mirrored: Boolean): File {
        val dir = File(temporary.root, name).apply { mkdirs() }
        File(dir, "manifest.json").writeText("{}")
        File(dir, "events_0001.jsonl").writeBytes(ByteArray(bytes))
        if (mirrored) File(dir, ".documents_mirrored").writeText("1")
        assertTrue(dir.setLastModified(System.currentTimeMillis() - ageMinutes * 60_000L))
        return dir
    }

    private fun prune(activeDir: File? = null, keep: Int = 25, limit: Long) = SessionRecorder.pruneSessionDirs(
        root = temporary.root,
        activeDir = activeDir,
        keep = keep,
        totalLimitBytes = limit,
        durable = { File(it, ".documents_mirrored").isFile },
    )

    @Test
    fun `fora da contagem so sai quem tem copia publica, abaixo do teto a sem copia fica`() {
        val old = session("s_old", 64 * 1024, ageMinutes = 300, mirrored = false)
        val oldMirrored = session("s_old_mirrored", 64 * 1024, ageMinutes = 200, mirrored = true)
        val recent = session("s_recent", 64 * 1024, ageMinutes = 10, mirrored = false)

        // keep = 2: a mais recente fica pela contagem; das outras duas só a espelhada sai.
        assertEquals(0, prune(keep = 2, limit = Long.MAX_VALUE))
        assertTrue(old.isDirectory)
        assertFalse(oldMirrored.isDirectory)
        assertTrue(recent.isDirectory)
    }

    @Test
    fun `acima do teto as mais antigas saem mesmo sem copia publica e a ativa nunca`() {
        val oldest = session("s1", 100 * 1024, ageMinutes = 400, mirrored = false)
        val second = session("s2", 100 * 1024, ageMinutes = 300, mirrored = true)
        val third = session("s3", 100 * 1024, ageMinutes = 200, mirrored = false)
        val active = session("s4", 100 * 1024, ageMinutes = 100, mirrored = false)

        // 400 KB gravados para um teto de 250 KB: saem as duas mais antigas, publicadas ou não.
        assertEquals(2, prune(activeDir = active, limit = 250L * 1024L))
        assertFalse(oldest.isDirectory)
        assertFalse(second.isDirectory)
        assertTrue(third.isDirectory)
        assertTrue(active.isDirectory)
    }

    @Test
    fun `a ativa nao sai nem quando e a unica acima do teto`() {
        val active = session("s_active", 300 * 1024, ageMinutes = 0, mirrored = false)
        assertEquals(0, prune(activeDir = active, limit = 100L * 1024L))
        assertTrue(active.isDirectory)
    }

    @Test
    fun `pasta vazia nao conta e nada sai abaixo do teto`() {
        session("s_only", 64 * 1024, ageMinutes = 5, mirrored = false)
        assertEquals(0, prune(limit = SessionRecorder.SESSIONS_TOTAL_LIMIT_BYTES))
        assertEquals(1, temporary.root.listFiles { f -> f.isDirectory }?.size)
    }
}
