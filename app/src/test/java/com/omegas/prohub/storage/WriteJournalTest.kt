package com.omegas.prohub.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
}
