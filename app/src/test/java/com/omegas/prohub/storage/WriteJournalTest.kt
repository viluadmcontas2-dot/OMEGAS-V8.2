package com.omegas.prohub.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException

class WriteJournalTest {
    private class Mem(var text: String? = null) : JournalStore {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    @Test fun `abrir, marcar e fechar`() {
        val store = Mem(); val j = WriteJournal(store)
        j.open("g1", listOf("MAPA_K", "CURVA_K"), "plano 1")
        assertEquals(listOf("MAPA_K", "CURVA_K"), j.pending()!!.missing)
        assertTrue(j.mark("g1", "MAPA_K", readbackOk = true))
        assertEquals(listOf("CURVA_K"), j.pending()!!.missing)
        assertTrue(j.mark("g1", "CURVA_K", readbackOk = true))
        assertNull(j.pending())
        j.close("g1"); assertNull(WriteJournal(store).pending())
    }

    @Test fun `sem leitura de volta igual nao marca`() {
        val j = WriteJournal(Mem()); j.open("g1", listOf("A", "B"), "")
        assertFalse(j.mark("g1", "A", readbackOk = false))
        assertEquals(listOf("A", "B"), j.pending()!!.missing)
    }

    @Test fun `queda entre etapa 1 e 2 deixa a etapa 2 com o plano ao reabrir`() {
        val store = Mem(); val j = WriteJournal(store)
        j.open("g1", listOf("MAPA_K", "CURVA_K"), "nós|com,separadores")
        j.mark("g1", "MAPA_K", true)
        val depois = WriteJournal(store).pending()!!
        assertEquals(listOf("CURVA_K"), depois.missing)
        assertEquals("nós|com,separadores", depois.plan)
    }

    @Test fun `arquivo vazio significa nenhuma gravacao pendente`() {
        assertNull(WriteJournal(Mem("")).pending())
        assertNull(WriteJournal(Mem(null)).pending())
    }

    @Test fun `arquivo truncado ou corrompido sinaliza estado desconhecido`() {
        for (lixo in listOf("J1|g1|A", "lixo", "J1|g1|A|||x", "J1|%ZZ|A|||END")) {
            val error = runCatching { WriteJournal(Mem(lixo)).pending() }.exceptionOrNull()
            assertTrue("Conteúdo corrompido foi tratado como ausência: $lixo", error is IllegalStateException)
        }
    }

    @Test fun `arquivo existente e substituido por inteiro`() {
        val directory = Files.createTempDirectory("omegas-journal-replace").toFile()
        try {
            val destination = File(directory, "journal")
            destination.writeText("anterior")
            FileJournalStore(destination).write("novo")
            assertEquals("novo", destination.readText())
            assertFalse(File(directory, "journal.tmp").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `erro de leitura em disco nao vira ausencia de pendencia`() {
        val directory = Files.createTempDirectory("omegas-journal-read").toFile()
        try {
            val error = runCatching { FileJournalStore(directory).read() }.exceptionOrNull()
            assertNotNull("Falha de leitura foi convertida em diário vazio", error)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `falha ao substituir destino propaga erro e preserva conteudo anterior`() {
        val directory = Files.createTempDirectory("omegas-journal-write").toFile()
        try {
            val destination = File(directory, "journal")
            assertTrue(destination.mkdir())
            File(destination, "previous").writeText("intacto")

            val error = runCatching { FileJournalStore(destination).write("novo") }.exceptionOrNull()
            assertNotNull("Falha de substituição foi reportada como sucesso", error)
            assertEquals("intacto", File(destination, "previous").readText())
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test fun `caminho sem diretorio pai nao vira diario ausente`() {
        val directory = Files.createTempDirectory("omegas-journal-parent").toFile()
        try {
            val parent = File(directory, "not-a-directory")
            parent.writeText("sou um arquivo, nao uma pasta")
            val error = runCatching { FileJournalStore(File(parent, "journal")).read() }.exceptionOrNull()
            assertNotNull("Caminho inacessível foi convertido em diário ausente", error)
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test fun `nova abertura nao sobrescreve gravacao interrompida`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "plano anterior")

        val error = runCatching {
            journal.open("g2", listOf("RESET"), "plano novo")
        }.exceptionOrNull()

        assertNotNull("Nova gravação apagou uma pendência anterior", error)
        val pending = journal.pending()
        assertEquals("g1", pending?.id)
        assertEquals(listOf("MAPA_K", "CURVA_K"), pending?.missing)
        assertEquals("plano anterior", pending?.plan)
    }

    @Test fun `diario corrompido impede nova abertura sem alterar conteudo`() {
        val store = Mem("J1|incompleto")
        val error = runCatching {
            WriteJournal(store).open("g2", listOf("RESET"), "plano novo")
        }.exceptionOrNull()

        assertTrue("Diário corrompido foi sobrescrito", error is CorruptWriteJournalException)
        assertEquals("J1|incompleto", store.text)
    }

    @Test fun `nova abertura e permitida depois que todas as etapas anteriores foram conferidas`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K"), "plano anterior")
        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))

        journal.open("g2", listOf("RESET"), "plano novo")

        assertEquals("g2", journal.pending()?.id)
        assertEquals(listOf("RESET"), journal.pending()?.missing)
    }


    @Test fun `NoSuchFileException com pai que e arquivo nao vira diario ausente`() {
        val directory = Files.createTempDirectory("omegas-journal-android-parent").toFile()
        try {
            val parent = File(directory, "not-a-directory")
            parent.writeText("sou um arquivo")
            val store = FileJournalStore(File(parent, "journal")) { path ->
                throw NoSuchFileException(path.toString())
            }

            val error = runCatching { store.read() }.exceptionOrNull()

            assertTrue(
                "NoSuchFileException de um pai que e arquivo virou diario ausente",
                error is NotDirectoryException,
            )
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test fun `arquivo realmente ausente com pai valido continua sem pendencia`() {
        val directory = Files.createTempDirectory("omegas-journal-missing-file").toFile()
        try {
            assertNull(FileJournalStore(File(directory, "missing-journal")).read())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `NoSuchFileException com ancestral que e arquivo nao vira diario ausente`() {
        val directory = Files.createTempDirectory("omegas-journal-ancestor").toFile()
        try {
            val ancestor = File(directory, "not-a-directory")
            ancestor.writeText("sou um arquivo")
            val nestedPath = File(File(ancestor, "nested"), "journal")
            val store = FileJournalStore(nestedPath) { path ->
                throw NoSuchFileException(path.toString())
            }

            val error = runCatching { store.read() }.exceptionOrNull()

            assertNotNull(
                "NoSuchFileException com ancestral arquivo foi confundida com ausência",
                error,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `fechar diario com etapa pendente preserva a intencao`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "mapa ja conferido")

        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))
        val error = runCatching { journal.close("g1") }.exceptionOrNull()

        assertTrue("Fechar apagou uma etapa ainda pendente", error is IllegalStateException)
        assertEquals("g1", journal.pending()?.id)
        assertEquals(listOf("CURVA_K"), journal.pending()?.missing)
    }

}
