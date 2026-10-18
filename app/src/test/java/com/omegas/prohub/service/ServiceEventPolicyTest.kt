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
}
