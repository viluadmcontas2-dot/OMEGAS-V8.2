package com.omegas.prohub.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defeito P2-6: a Activity limpa o ouvinte no onDestroy e o serviço não empurra mais nada para ela. */
class RevisionListenerSlotTest {
    @Test
    fun `ouvinte registrado recebe e limpo (null) deixa de receber`() {
        val slot = RevisionListenerSlot()
        val got = ArrayList<Pair<RuntimeSnapshotBus.Kind, Long>>()
        slot.set { kind, revision -> got += kind to revision }
        slot.publish(RuntimeSnapshotBus.Kind.EVIDENCE, 7L)
        assertEquals(1, got.size)
        slot.set(null) // Activity.onDestroy
        assertFalse(slot.isSet())
        slot.publish(RuntimeSnapshotBus.Kind.EVIDENCE, 8L)
        assertEquals("nada empurrado depois de limpar", 1, got.size)
    }

    @Test
    fun `ouvinte que lanca nao derruba o servico`() {
        val slot = RevisionListenerSlot()
        slot.set { _, _ -> throw IllegalStateException("WebView destruído") }
        slot.publish(RuntimeSnapshotBus.Kind.TABLES, 1L)
        assertTrue(slot.isSet())
    }
}
