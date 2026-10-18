package com.omegas.prohub.service

import org.junit.Assert.*
import org.junit.Test

class CalibrationReadEventsTest {
    @Test fun `primeiro quadro inicia e sucesso nao repete`() {
        val events = CalibrationReadEvents()
        val first = events.next(1, false)!!
        assertEquals("AUTO_START", first.trigger)
        assertTrue(first.parameters)
        assertNull(events.next(1, false))
        events.complete(first, true)
        repeat(100) { assertNull(events.next(1, false)) }
    }
    @Test fun `ocupacao preserva pedido e falha tenta no proximo quadro`() {
        val events = CalibrationReadEvents()
        assertNull(events.next(1, true))
        val first = events.next(1, false)!!
        events.complete(first, false)
        assertNotNull(events.next(1, false))
    }
    @Test fun `evento durante leitura nao se perde`() {
        val events = CalibrationReadEvents()
        val first = events.next(1, false)!!
        events.request(1, "AUTO_MATCH")
        events.complete(first, true)
        assertEquals("AUTO_MATCH", events.next(1, false)!!.trigger)
    }
    @Test fun `sessao antiga nao conclui nova e parametros uma vez por conexao`() {
        val events = CalibrationReadEvents()
        val old = events.next(1, false)!!
        assertNull(events.next(2, false)) // uma única I/O mesmo na troca de sessão
        events.complete(old, true)
        val fresh = events.next(2, false)!!
        assertTrue(fresh.parameters)
        events.complete(fresh, true)
        events.request(2, "WRITE_CONFIRMED")
        assertFalse(events.next(2, false)!!.parameters)
    }
    @Test fun `falhas limitadas por evento e novo evento libera novas tentativas`() {
        val events = CalibrationReadEvents()
        repeat(4) { events.complete(events.next(1, false)!!, false) }
        assertNull(events.next(1, false))
        events.request(1, "ECU_CALIBRATION_CHANGED")
        assertNotNull(events.next(1, false))
    }
}
