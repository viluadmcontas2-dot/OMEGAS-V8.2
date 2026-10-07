package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Revisão 2026-10-07 (#5): o destroy da Activity antiga não desarma a ponte da Activity nova. */
class OwnedSlotTest {
    @Test
    fun `so o dono atual limpa o slot`() {
        val slot = OwnedSlot<String>()
        val oldBridge = Any()
        val newBridge = Any()
        slot.set(oldBridge, "antiga")
        slot.set(newBridge, "nova")
        assertFalse("a ponte antiga não é mais dona", slot.clearIf(oldBridge))
        assertEquals("nova", slot.get())
        assertTrue(slot.isOwner(newBridge))
        assertTrue(slot.clearIf(newBridge))
        assertNull(slot.get())
    }
}
