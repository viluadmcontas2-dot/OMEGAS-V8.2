package com.omegas.prohub.storage

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class WriteJournalTest {
    private class Mem(var text: String? = null) : JournalStore {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    private class Unreadable : JournalStore {
        var writes = 0
        override fun read(): String? = throw IOException("journal indisponível")
        override fun write(text: String) { writes += 1 }
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

    @Test fun `diario vazio significa nenhuma pendencia`() {
        assertNull(WriteJournal(Mem("")).pending())
        assertNull(WriteJournal(Mem(null)).pending())
    }

    @Test fun `nova abertura nao sobrescreve gravacao interrompida`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "plano original")
        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))
        val saved = store.text

        try {
            journal.open("g2", listOf("OUTRA_ETAPA"), "novo plano")
            fail("uma nova abertura nao pode substituir a gravacao interrompida")
        } catch (_: IllegalStateException) {
            // Falha fechada: a entrada original precisa continuar intacta.
        }

        assertEquals(saved, store.text)
        assertEquals("g1", journal.pending()!!.id)
        assertEquals(listOf("CURVA_K"), journal.pending()!!.missing)
    }

    @Test fun `fechar nao descarta etapas ainda nao conferidas`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "plano original")
        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))

        try {
            journal.close("g1")
            fail("uma gravacao incompleta nao pode ser fechada")
        } catch (_: IllegalStateException) {
            // A etapa ainda nao conferida deve permanecer recuperavel.
        }

        assertEquals("g1", journal.pending()!!.id)
        assertEquals(listOf("CURVA_K"), journal.pending()!!.missing)
    }

    @Test fun `falha de leitura nao vira ausencia de pendencia`() {
        val store = Unreadable()
        try {
            WriteJournal(store).pending()
            fail("falha de I/O nao pode ser interpretada como diario vazio")
        } catch (_: IOException) {
            // Fail-closed: o estado desconhecido nao pode liberar nova gravacao.
        }
    }

    @Test fun `falha de leitura impede abrir e nao sobrescreve armazenamento`() {
        val store = Unreadable()
        try {
            WriteJournal(store).open("g2", listOf("MAPA_K"), "novo plano")
            fail("nao pode abrir quando nao consegue ler a intencao anterior")
        } catch (_: IOException) {
            // A intencao anterior pode existir; nao sobrescrever.
        }
        assertEquals(0, store.writes)
    }

    @Test fun `diario nao vazio corrompido nao vira ausencia de pendencia`() {
        for (corrupt in listOf("lixo", "J1|g1|A", "J1|g1|A|||x")) {
            try {
                WriteJournal(Mem(corrupt)).pending()
                fail("conteudo corrompido nao pode liberar nova gravacao: $corrupt")
            } catch (_: IllegalStateException) {
                // Estado desconhecido: bloquear e preservar o arquivo.
            }
        }
    }

    @Test fun `arquivo que nao pode ser lido nao e tratado como ausente`() {
        val root = Files.createTempDirectory("omegas-journal-read").toFile()
        try {
            val directoryAtJournalPath = File(root, "journal")
            assertTrue(directoryAtJournalPath.mkdir())
            try {
                FileJournalStore(directoryAtJournalPath).read()
                fail("um caminho existente que nao pode ser lido nao e um diario ausente")
            } catch (_: IOException) {
                // Erro de I/O deve chegar ao chamador.
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `falha no rename atomico preserva diario anterior`() {
        val root = Files.createTempDirectory("omegas-journal-move").toFile()
        try {
            val journalFile = File(root, "journal")
            journalFile.writeText("diario anterior")
            var temporary: java.nio.file.Path? = null
            val store = FileJournalStore(journalFile) { source, target ->
                temporary = source
                assertTrue(Files.exists(source))
                assertEquals(journalFile.toPath().toAbsolutePath(), target)
                throw IOException("falha simulada no rename atomico")
            }

            try {
                store.write("diario novo")
                fail("a falha injetada no rename atomico precisa ser propagada")
            } catch (error: IOException) {
                assertEquals("falha simulada no rename atomico", error.message)
            }

            assertEquals("diario anterior", journalFile.readText())
            assertNotNull(temporary)
            assertFalse(Files.exists(temporary!!))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `nova abertura apos todas as etapas conferidas nao fica bloqueada por diario concluido`() {
        val store = Mem()
        val first = WriteJournal(store)
        first.open("g1", listOf("MAPA_K", "CURVA_K"), "plano anterior")
        assertTrue(first.mark("g1", "MAPA_K", readbackOk = true))
        assertTrue(first.mark("g1", "CURVA_K", readbackOk = true))

        // Simula reinício depois do último readback e antes de close().
        val restarted = WriteJournal(store)
        assertNull(restarted.pending())
        restarted.open("g2", listOf("OUTRA_ETAPA"), "novo plano")

        assertEquals("g2", restarted.pending()!!.id)
        assertEquals(listOf("OUTRA_ETAPA"), restarted.pending()!!.missing)
    }
}
