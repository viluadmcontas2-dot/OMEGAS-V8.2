package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

class DocumentsSessionMirrorZipTest {
    @Test
    fun `sessao vira um zip com todos os arquivos e sem marcadores ocultos`() {
        val dir = Files.createTempDirectory("sess").toFile()
        dir.resolve("manifest.json").writeText("{}")
        dir.resolve("events_0001.jsonl").writeText("{\"a\":1}\n")
        dir.resolve(".documents_mirrored").writeText("1")
        val out = ByteArrayOutputStream()
        val count = DocumentsSessionMirror.writeZip(dir, "session 2026/10", out)
        assertEquals(2, count)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; names += e.name }
        }
        assertEquals(listOf("session_2026_10/events_0001.jsonl", "session_2026_10/manifest.json"), names)
        assertEquals("session_2026_10.zip", DocumentsSessionMirror.zipName("session 2026/10"))
        dir.deleteRecursively()
    }
}
