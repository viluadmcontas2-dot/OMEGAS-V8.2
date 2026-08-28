package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdEvidenceLedgerTest {
    @Test fun `epoca so abre apos confirmacao manual e readback valido`() {
        val ledger = ObdEvidenceLedger()
        assertFalse(ledger.openAfterConfirmedReadback("MAP_K", false, true, "map-a", 100).opened)
        assertFalse(ledger.openAfterConfirmedReadback("MAP_K", true, false, "map-a", 100).opened)
        val result = ledger.openAfterConfirmedReadback("MAP_K", true, true, "map-a", 100)
        assertTrue(result.opened)
        assertEquals("map-1", result.epoch!!.mapEpochId)
        assertEquals("curve-0", result.epoch.curveEpochId)
    }

    @Test fun `mapa e curva produzem fronteiras independentes`() {
        val ledger = ObdEvidenceLedger()
        ledger.openAfterConfirmedReadback("MAP_K", true, true, "map-a", 100)
        val result = ledger.openAfterConfirmedReadback("K_FACTOR", true, true, "curve-a", 200)
        assertTrue(result.opened)
        assertEquals("map-1", result.epoch!!.mapEpochId)
        assertEquals("curve-1", result.epoch.curveEpochId)
        assertEquals("map-a", result.epoch.mapReadbackHash)
    }

    @Test fun `recibo repetido nao abre epoca adicional e rejeicoes persistem`() {
        val ledger = ObdEvidenceLedger()
        ledger.openAfterConfirmedReadback("MAP_K", true, true, "map-a", 100)
        assertFalse(ledger.openAfterConfirmedReadback("MAP_K", true, true, "map-a", 200).opened)
        ledger.recordRejection("malha aberta")
        ledger.recordRejection("malha aberta")
        val restored = ObdEvidenceLedger().also { it.load(ledger.toJson()) }
        assertEquals(2L, restored.metricsJson().getLong("malha aberta"))
        assertEquals(2L, restored.metricsJson().getLong("rejected"))
        assertEquals("malha aberta", restored.metricsJson().getString("lastReason"))
        assertEquals("map-1", restored.current().mapEpochId)
    }
}
