package com.omegas.prohub.storage

import org.junit.Assert.*
import org.junit.Test

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

    @Test fun `arquivo truncado ou corrompido vira sem pendencia`() {
        for (lixo in listOf("", "J1|g1|A", "lixo", "J1|g1|A|||x")) assertNull(WriteJournal(Mem(lixo)).pending())
    }

    @Test fun `nova abertura não sobrescreve gravação interrompida`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "plano original")
        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))
        val saved = store.text

        try {
            journal.open("g2", listOf("OUTRA_ETAPA"), "novo plano")
            fail("uma nova abertura não pode substituir a gravação interrompida")
        } catch (_: IllegalStateException) {
            // Falha fechada: a entrada original precisa continuar intacta.
        }

        assertEquals(saved, store.text)
        assertEquals("g1", journal.pending()!!.id)
        assertEquals(listOf("CURVA_K"), journal.pending()!!.missing)
    }

    @Test fun `fechar não descarta etapas ainda não conferidas`() {
        val store = Mem()
        val journal = WriteJournal(store)
        journal.open("g1", listOf("MAPA_K", "CURVA_K"), "plano original")
        assertTrue(journal.mark("g1", "MAPA_K", readbackOk = true))

        try {
            journal.close("g1")
            fail("uma gravação incompleta não pode ser fechada")
        } catch (_: IllegalStateException) {
            // A etapa ainda não conferida deve permanecer recuperável.
        }

        assertEquals("g1", journal.pending()!!.id)
        assertEquals(listOf("CURVA_K"), journal.pending()!!.missing)
    }
}
