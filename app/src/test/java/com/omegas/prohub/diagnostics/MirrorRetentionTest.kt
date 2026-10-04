package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRetentionTest {
    private fun entry(session: String, mb: Long, at: Long) =
        MirrorRetention.Entry(session, mb * 1024L * 1024L, at)

    @Test fun `abaixo do teto nada sai e sem aviso`() {
        val entries = listOf(entry("a", 100, 1), entry("b", 100, 2))
        assertTrue(MirrorRetention.sessionsToDelete(entries, maxBytes = 1024L * 1024L * 1024L).isEmpty())
        assertNull(MirrorRetention.warning(entries))
    }

    @Test fun `acima do aviso avisa com o tamanho`() {
        val entries = listOf(entry("a", 3_100, 1))
        val warning = MirrorRetention.warning(entries)
        assertTrue(warning != null && warning.contains("3100 MB"))
    }

    @Test fun `acima do teto apaga as mais antigas primeiro e para ao voltar ao teto`() {
        val entries = (1..6).map { entry("s$it", 100, it.toLong()) } // 600 MB
        val out = MirrorRetention.sessionsToDelete(entries, maxBytes = 350L * 1024L * 1024L, keepNewest = 1)
        assertEquals(listOf("s1", "s2", "s3"), out)
    }

    @Test fun `nunca apaga as mais novas nem a protegida`() {
        val entries = (1..4).map { entry("s$it", 100, it.toLong()) }
        val out = MirrorRetention.sessionsToDelete(
            entries, protect = setOf("s1"), maxBytes = 1L, keepNewest = 2,
        )
        assertEquals(listOf("s2"), out)
    }

    @Test fun `varios arquivos da mesma sessao contam juntos`() {
        val entries = listOf(entry("old", 200, 1), entry("old", 200, 2), entry("new", 10, 3))
        val out = MirrorRetention.sessionsToDelete(entries, maxBytes = 100L * 1024L * 1024L, keepNewest = 1)
        assertEquals(listOf("old"), out)
    }
}
