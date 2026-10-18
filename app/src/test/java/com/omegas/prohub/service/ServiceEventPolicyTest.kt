package com.omegas.prohub.service

import org.junit.Assert.*
import org.junit.Test

/** Gatilhos do serviço por contagem de quadros e por borda de estado; nenhum relógio. */
class ServiceEventPolicyTest {
    @Test fun `tick do AutoCal a cada N quadros validos e nao a cada quadro`() {
        val trigger = FrameCountTrigger(ServiceEventPolicy.AUTOCAL_TICK_FRAMES)
        val fired = (1..40).count { trigger.onFrame() }
        assertEquals(40 / ServiceEventPolicy.AUTOCAL_TICK_FRAMES, fired)
        assertEquals(4, ServiceEventPolicy.AUTOCAL_TICK_FRAMES)
    }

    @Test fun `contagem recomeca na troca de sessao`() {
        val trigger = FrameCountTrigger(4)
        repeat(3) { assertFalse(trigger.onFrame()) }
        trigger.reset()
        repeat(3) { assertFalse(trigger.onFrame()) }
        assertTrue(trigger.onFrame())
    }

    @Test fun `reinicio automatico do nucleo limitado por contagem e liberado por quadro valido`() {
        val budget = EngineRestartBudget(3)
        repeat(3) { assertTrue(budget.tryConsume(7)) }
        assertFalse(budget.tryConsume(7))
        assertTrue(budget.exhausted(7))
        budget.onValidFrame(7)
        assertTrue(budget.tryConsume(7))
        // nova sessão USB começa do zero
        repeat(2) { budget.tryConsume(7) }
        assertFalse(budget.tryConsume(7))
        assertTrue(budget.tryConsume(8))
        assertFalse(budget.tryConsume(0))
    }

    @Test fun `borda ocupado para livre so uma vez`() {
        val edge = BusyEdge()
        assertFalse(edge.observe(false))
        assertFalse(edge.observe(true))
        assertFalse(edge.observe(true))
        assertTrue(edge.observe(false))
        assertFalse(edge.observe(false))
    }

    @Test fun `leitura da tela espera o fim da leitura automatica`() {
        val gate = AutoReadGate()
        gate.enter()
        val order = java.util.Collections.synchronizedList(ArrayList<String>())
        val waiter = Thread { gate.awaitIdle { false }; order += "tela" }
        waiter.start()
        waiter.join(200)
        assertTrue(waiter.isAlive)
        order += "auto"
        gate.exit()
        waiter.join(2_000)
        assertFalse(waiter.isAlive)
        assertEquals(listOf("auto", "tela"), order.toList())
        // sem leitura automática não espera nada
        gate.awaitIdle { false }
    }

    @Test fun `servico parando libera quem espera`() {
        val gate = AutoReadGate()
        gate.enter()
        var stopping = false
        val waiter = Thread { gate.awaitIdle { stopping } }
        waiter.start()
        waiter.join(100)
        stopping = true
        gate.wakeAll()
        waiter.join(2_000)
        assertFalse(waiter.isAlive)
    }
    // Re-revisão 2026-10-18 (A): depois de awaitIdle, um quadro válido abria nova leitura automática antes da leitura da
    // tela, que recebia "Outra operação K em andamento". A tela reserva a serial do pedido até o fim da própria leitura.
    @Test fun `leitura da tela reserva a serial contra nova leitura automatica`() {
        val gate = AutoReadGate()
        val reads = CalibrationReadEvents()
        assertFalse(gate.uiReadPending())
        val seen = gate.forUi({ false }) {
            assertTrue(gate.uiReadPending())
            // quadro válido chega aqui: a leitura automática não abre (fica pendente para o próximo quadro)
            reads.next(5, busy = false || gate.uiReadPending())
        }
        assertNull(seen)
        assertFalse(gate.uiReadPending())
        assertEquals("AUTO_START", reads.next(5, busy = gate.uiReadPending())?.trigger)
    }

    @Test fun `reserva da tela e devolvida mesmo com erro e conta pedidos simultaneos`() {
        val gate = AutoReadGate()
        try { gate.forUi({ false }) { throw IllegalStateException("x") } } catch (_: IllegalStateException) {}
        assertFalse(gate.uiReadPending())
        gate.forUi({ false }) {
            gate.forUi({ false }) { assertTrue(gate.uiReadPending()) }
            assertTrue(gate.uiReadPending())
        }
        assertFalse(gate.uiReadPending())
    }

    @Test fun `tela esperando a leitura automatica ja reserva a serial`() {
        val gate = AutoReadGate()
        gate.enter()
        val waiter = Thread { gate.forUi({ false }) { } }
        waiter.start()
        var spins = 0
        while (waiter.state != Thread.State.WAITING && spins++ < 200) waiter.join(10)
        assertTrue("tela ainda espera a leitura automática", waiter.isAlive)
        assertTrue("reserva feita ANTES de esperar", gate.uiReadPending())
        gate.exit()
        waiter.join(2_000)
        assertFalse(gate.uiReadPending())
    }

    // Re-revisão 2026-10-18 (B): o próprio readCurve do D1 gera a borda ocupado → livre ANTES de o pendente ser gravado;
    // sem consumir na hora, a nova tentativa só vinha com a próxima operação.
    @Test fun `D1 pendente com serial ja livre roda na hora`() {
        val retry = AutoRefineRetry<String>(3)
        val offer = retry.offer("d1", "7:2") { false }
        assertTrue(offer.accepted)
        assertEquals("d1", offer.runNow)
        assertNull("consumido uma vez só", retry.take())
    }

    @Test fun `D1 pendente com serial ocupada espera a borda e tem limite por AutoMatch`() {
        val retry = AutoRefineRetry<String>(3)
        val offer = retry.offer("d1", "7:2") { true }
        assertTrue(offer.accepted)
        assertNull(offer.runNow)
        assertEquals("d1", retry.take())
        assertNull(retry.take())
        assertTrue(retry.offer("d1", "7:2") { false }.accepted)
        assertTrue(retry.offer("d1", "7:2") { false }.accepted)
        val exhausted = retry.offer("d1", "7:2") { false }
        assertFalse("3 tentativas por época evitam laço", exhausted.accepted)
        assertNull(exhausted.runNow)
        assertTrue("AutoMatch novo recomeça", retry.offer("d1", "7:3") { true }.accepted)
    }

    @Test fun `borda livre entre gravar o pendente e conferir ocupado nao roda duas vezes`() {
        val retry = AutoRefineRetry<String>(3)
        var edgeTook: String? = null
        val offer = retry.offer("d1", "1:1") { edgeTook = retry.take(); false }
        assertEquals("d1", edgeTook)
        assertNull(offer.runNow)
    }

    // Re-revisão 2026-10-18 (G): MUL_ACT visto no snapshot durante a operação de curva do próprio app (lote entre o
    // ACK/readback e onConfirmedBatch) era registrado como ECU_NATIVE_OR_EXTERNAL.
    @Test fun `curva vista no snapshot durante operacao do app nao e atribuida a ECU`() {
        assertEquals(CurveSnapshotChange.SAME, CurveSnapshotChange.of("[1]", "[1]", curveBusy = false))
        assertEquals(CurveSnapshotChange.BASELINE, CurveSnapshotChange.of("[1]", null, curveBusy = false))
        assertEquals(CurveSnapshotChange.APP_OPERATION, CurveSnapshotChange.of("[2]", "[1]", curveBusy = true))
        assertEquals(CurveSnapshotChange.APP_OPERATION, CurveSnapshotChange.of("[2]", null, curveBusy = true))
        assertEquals(CurveSnapshotChange.ECU_CHANGE, CurveSnapshotChange.of("[2]", "[1]", curveBusy = false))
    }
}
