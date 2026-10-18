package com.omegas.prohub.service

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Gatilhos do serviço por evento (dono, 2026-10-18: nada por tempo). Contagem de quadros válidos aceitos e bordas de
 * estado substituem os antigos laços de 1 s / 3 s. Tudo puro, testável na JVM.
 */
internal object ServiceEventPolicy {
    /**
     * Sonda do AutoCal (CMD_NATIVE_STATUS) a cada N quadros válidos. Antes: 1 por segundo; a telemetria anda a ~4
     * quadros/s, então N = 4 mantém o ritmo antigo sem relógio (e não rouba a serial a cada quadro).
     */
    const val AUTOCAL_TICK_FRAMES = 4

    /** `full_snapshot` da gravação a cada N quadros válidos (antes: a cada 3 s ≈ 12 quadros a ~4 quadros/s). */
    const val FULL_SNAPSHOT_FRAMES = 12

    /** Reinícios automáticos do núcleo sem nenhum quadro válido entre eles, por sessão USB. */
    const val MAX_ENGINE_RESTARTS_WITHOUT_FRAME = 3
}

/** Dispara a cada [every] quadros contados. Sem relógio. */
internal class FrameCountTrigger(private val every: Int) {
    init { require(every >= 1) { "every deve ser >= 1" } }
    private var count = 0

    @Synchronized fun onFrame(): Boolean {
        count += 1
        if (count < every) return false
        count = 0
        return true
    }

    @Synchronized fun reset() { count = 0 }
}

/**
 * Limita o laço núcleo cai → healthTick → reinicia por CONTAGEM: no máximo [max] reinícios por sessão USB sem quadro
 * válido entre eles; o primeiro quadro válido devolve o saldo. Sessão nova começa do zero.
 */
internal class EngineRestartBudget(private val max: Int = ServiceEventPolicy.MAX_ENGINE_RESTARTS_WITHOUT_FRAME) {
    private var session = 0L
    private var used = 0

    @Synchronized fun tryConsume(sessionId: Long): Boolean {
        if (sessionId <= 0L) return false
        if (sessionId != session) {
            session = sessionId
            used = 0
        }
        if (used >= max) return false
        used += 1
        return true
    }

    @Synchronized fun onValidFrame(sessionId: Long) {
        if (sessionId == session) used = 0
    }

    @Synchronized fun exhausted(sessionId: Long): Boolean = sessionId == session && used >= max
}

/** Borda ocupado → livre (KWriteManager/KFactorManager): o evento que destrava o que ficou esperando a calibração. */
internal class BusyEdge {
    private var wasBusy = false

    @Synchronized fun observe(busy: Boolean): Boolean {
        val edge = wasBusy && !busy
        wasBusy = busy
        return edge
    }
}

/**
 * Leitura automática (AUTO_START, AutoMatch, D1) em curso: a leitura pedida pela tela espera ela TERMINAR (evento) em
 * vez de bater no "ocupado" do KWriteManager/KFactorManager. Nenhum prazo; o serviço parando acorda quem espera.
 */
internal class AutoReadGate {
    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var active = 0

    fun enter() = lock.withLock { active += 1 }

    fun exit() = lock.withLock {
        active = (active - 1).coerceAtLeast(0)
        idle.signalAll()
    }

    fun active(): Boolean = lock.withLock { active > 0 }

    fun awaitIdle(cancelled: () -> Boolean) = lock.withLock {
        while (active > 0 && !cancelled()) idle.await()
    }

    /** Acorda todos para conferirem `cancelled` (serviço parando). */
    fun wakeAll() = lock.withLock { idle.signalAll() }
}
