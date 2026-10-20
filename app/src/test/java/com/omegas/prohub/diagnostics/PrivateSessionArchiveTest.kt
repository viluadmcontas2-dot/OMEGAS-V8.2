package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Arquivo das sessões no armazenamento PRIVADO do app (privacidade): escrita atômica, sem fuga de pasta, migração sem perda. */
class PrivateSessionArchiveTest {
    private fun tmp(): File = Files.createTempDirectory("omegas-private").toFile()

    @Test
    fun parte_nasce_dentro_da_pasta_privada_e_reescrever_nao_duplica() {
        val root = tmp()
        val archive = PrivateSessionArchive(root)
        val n1 = archive.writePart("s1", "s1_parte_0001.zip") { it.write(byteArrayOf(1, 2, 3)); 3 }
        val n2 = archive.writePart("s1", "s1_parte_0001.zip") { it.write(byteArrayOf(9, 9)); 2 }
        assertEquals(3, n1); assertEquals(2, n2)
        val target = File(root, "s1/s1_parte_0001.zip")
        assertEquals(listOf<Byte>(9, 9), target.readBytes().toList())
        assertEquals(listOf("s1_parte_0001.zip"), File(root, "s1").list()!!.sorted())
    }

    @Test
    fun falha_no_meio_da_escrita_preserva_a_parte_anterior_e_nao_deixa_lixo() {
        val root = tmp()
        val archive = PrivateSessionArchive(root)
        archive.writePart("s1", "p.zip") { it.write(byteArrayOf(7)); 1 }
        try {
            archive.writePart("s1", "p.zip") { it.write(byteArrayOf(1, 1)); throw IOException("disco cheio") }
        } catch (_: IOException) { /* esperado */ }
        assertEquals(listOf<Byte>(7), File(root, "s1/p.zip").readBytes().toList())
        assertEquals(listOf("p.zip"), File(root, "s1").list()!!.toList())
    }

    @Test
    fun nome_que_tenta_sair_da_pasta_e_recusado() {
        val root = tmp()
        val archive = PrivateSessionArchive(root)
        assertNull(archive.resolveInside("../fora.zip"))
        assertNull(archive.resolveInside("a/../../fora.zip"))
        assertNull(archive.resolveInside("/etc/passwd"))
        assertNull(archive.resolveInside(""))
        assertEquals(File(root, "s1/p.zip").canonicalFile, archive.resolveInside("s1/p.zip"))
        var refused = false
        try { archive.writePart("..", "x.zip") { 0 } } catch (_: IllegalArgumentException) { refused = true }
        assertTrue(refused)
        assertFalse(File(root.parentFile, "x.zip").exists())
    }

    @Test
    fun copia_de_arquivo_solto_acrescenta_eventos_e_regrava_o_resto() {
        val root = tmp(); val src = tmp()
        val archive = PrivateSessionArchive(root)
        val events = File(src, "events_0001.jsonl").apply { writeText("a\n") }
        archive.copyRootFile(events)
        events.appendText("b\n")
        archive.copyRootFile(events)
        assertEquals("a\nb\n", File(root, "events_0001.jsonl").readText())
        val backup = File(src, "MANUAL-1.json").apply { writeText("{\"v\":1}") }
        archive.copyRootFile(backup)
        backup.writeText("{\"v\":2}")
        archive.copyRootFile(backup)
        assertEquals("{\"v\":2}", File(root, "MANUAL-1.json").readText())
    }

    @Test
    fun importar_copia_antiga_confere_o_tamanho_antes_de_aceitar() {
        val root = tmp()
        val archive = PrivateSessionArchive(root)
        val bytes = "conteudo".toByteArray()
        assertTrue(archive.importVerified("s1/p.zip", bytes.size.toLong()) { ByteArrayInputStream(bytes) })
        assertEquals("conteudo", File(root, "s1/p.zip").readText())
        // Já importado com o mesmo tamanho: não reescreve, só confirma.
        assertTrue(archive.importVerified("s1/p.zip", bytes.size.toLong()) { error("não deveria abrir de novo") })
        // Cópia truncada: recusa e não deixa arquivo parcial.
        assertFalse(archive.importVerified("s2/q.zip", 100L) { ByteArrayInputStream(bytes) })
        assertFalse(File(root, "s2/q.zip").exists())
        assertFalse(archive.importVerified("../fora.zip", bytes.size.toLong()) { ByteArrayInputStream(bytes) })
    }

    @Test
    fun migrar_pasta_publica_move_tudo_sem_perda_e_apaga_so_o_que_foi_conferido() {
        val root = tmp(); val legacy = tmp()
        File(legacy, "s1").mkdirs()
        File(legacy, "s1/s1_parte_0001.zip").writeText("zip1")
        File(legacy, "MANUAL-1.json").writeText("{}")
        File(legacy, "s1/.tmp_meio.zip.tmp").writeText("x")
        val report = PrivateSessionArchive(root).migrateDirectory(legacy)
        assertEquals(2, report.moved)
        assertEquals(0, report.failed)
        assertEquals("zip1", File(root, "s1/s1_parte_0001.zip").readText())
        assertEquals("{}", File(root, "MANUAL-1.json").readText())
        assertFalse(File(legacy, "s1/s1_parte_0001.zip").exists())
        assertFalse(File(legacy, "MANUAL-1.json").exists())
        // Rodar de novo não faz nada nem perde nada.
        val again = PrivateSessionArchive(root).migrateDirectory(legacy)
        assertEquals(0, again.moved)
        assertEquals("zip1", File(root, "s1/s1_parte_0001.zip").readText())
    }

    @Test
    fun migrar_pasta_inexistente_nao_falha() {
        val report = PrivateSessionArchive(tmp()).migrateDirectory(File("/nao/existe/omegas"))
        assertEquals(0, report.moved); assertEquals(0, report.failed)
    }
}
