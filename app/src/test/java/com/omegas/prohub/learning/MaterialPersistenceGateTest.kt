package com.omegas.prohub.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialPersistenceGateTest {
    @Test
    fun `redundant request without material revision is skipped`() {
        val gate = MaterialPersistenceGate()
        assertFalse(gate.shouldRequest())
        assertFalse(gate.shouldRequest())
    }

    @Test
    fun `one material revision requests one snapshot only`() {
        val gate = MaterialPersistenceGate()
        gate.markMaterialChange()
        assertTrue(gate.shouldRequest())
        assertFalse(gate.shouldRequest())
    }

    @Test
    fun `later material revision becomes requestable again`() {
        val gate = MaterialPersistenceGate()
        gate.markMaterialChange()
        assertTrue(gate.shouldRequest())
        gate.markMaterialChange()
        assertTrue(gate.shouldRequest())
    }

    @Test
    fun `explicit boundary may force final snapshot without fabricating material revision`() {
        val gate = MaterialPersistenceGate()
        assertTrue(gate.shouldRequest(forceBoundary = true))
        assertFalse(gate.shouldRequest())
    }
}
