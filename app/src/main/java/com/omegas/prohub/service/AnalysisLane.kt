package com.omegas.prohub.service

import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * Faixa única de trabalho pesado de análise (refino, diário, `full_snapshot` JSON, overlay, notificação).
 *
 * Roda em um executor PRÓPRIO, fora da thread do `autoCalTick`: nada aqui pode atrasar a aquisição
 * AutoCal. Coalescente: se uma rodada ainda está em curso, a seguinte é descartada (contada em
 * `skipped`) em vez de enfileirar. Não conhece ECU, serial nem comandos.
 */
class AnalysisLane(
    executor: Executor,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onFailure: (Throwable) -> Unit = {},
    /** Rodada que passa disto é dada como travada: avisa e deixa UMA seguinte entrar na fila atrás dela. */
    private val hangMs: Long = HANG_MS,
    private val onHang: (Long) -> Unit = {},
    /**
     * Recuperação real do travamento: recebe o executor preso (para encerrá-lo) e devolve um novo. A rodada
     * presa é ABANDONADA (se um dia terminar, não mexe mais no estado da faixa) e a nova roda no executor novo.
     * Sem isto, a rodada nova só esperaria atrás da travada (comportamento antigo).
     */
    private val replaceExecutor: ((Executor) -> Executor)? = null,
) {
    @Volatile private var executor: Executor = executor
    /** Cada troca de executor abre uma geração nova; rodada de geração antiga não atualiza mais nada. */
    private val generation = AtomicLong(0L)
    /** Rodadas aceitas e ainda não terminadas (em curso + no máximo uma na fila atrás de uma travada). */
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)
    private val hangs = AtomicLong(0L)

    companion object {
        const val HANG_MS = 30_000L
    }
    @Volatile private var startedAt = -1L
    private val submitted = AtomicLong(0L)
    private val skipped = AtomicLong(0L)
    private val completed = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    @Volatile private var lastDurationMs = 0L
    @Volatile private var maxDurationMs = 0L

    /** Tarefas que chegaram com a faixa ocupada e pediram "rodar de novo uma vez" (uma por chave). */
    private val rerun = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    /**
     * Devolve true se a rodada foi aceita; false se a anterior ainda roda (ou o executor recusou).
     * Com [rerunKey], a rodada recusada por faixa ocupada fica marcada como suja e roda UMA vez quando a faixa
     * liberar (várias recusas da mesma chave viram uma só): o tique da análise não se perde por azar de horário.
     */
    fun submit(rerunKey: String? = null, task: () -> Unit): Boolean {
        while (true) {
            val n = pending.get()
            val hungFor = if (n == 1 && startedAt >= 0L) clock() - startedAt else 0L
            val hungNow = hungFor > hangMs
            if (n != 0 && !hungNow) {
                skipped.incrementAndGet()
                if (rerunKey != null) rerun[rerunKey] = task
                return false
            }
            if (pending.compareAndSet(n, n + 1)) {
                // O executor é de uma thread só: a rodada nova espera a travada acabar; nunca rodam duas juntas.
                if (hungNow) {
                    hangs.incrementAndGet()
                    try { onHang(hungFor) } catch (_: Throwable) {}
                    val swap = replaceExecutor
                    if (swap != null) {
                        try {
                            executor = swap(executor)
                            generation.incrementAndGet()
                            // Só a rodada nova conta agora; a presa foi descartada.
                            pending.set(1)
                            startedAt = -1L
                        } catch (_: Throwable) {
                            // Não deu para trocar: mantém o comportamento antigo (a nova espera atrás da presa).
                        }
                    }
                }
                break
            }
        }
        submitted.incrementAndGet()
        val myGeneration = generation.get()
        return try {
            executor.execute {
                val started = clock()
                startedAt = started
                try {
                    task()
                    completed.incrementAndGet()
                } catch (error: Throwable) {
                    failed.incrementAndGet()
                    try { onFailure(error) } catch (_: Throwable) {}
                } finally {
                    // Rodada abandonada (executor trocado): termina sem tocar no estado da faixa nova.
                    if (generation.get() == myGeneration) {
                        val took = (clock() - started).coerceAtLeast(0L)
                        lastDurationMs = took
                        if (took > maxDurationMs) maxDurationMs = took
                        startedAt = -1L
                        pending.decrementAndGet()
                        runDirty()
                    }
                }
            }
            true
        } catch (_: Exception) {
            pending.decrementAndGet() // executor encerrado
            false
        }
    }

    /** Roda, numa única rodada, o que foi marcado sujo enquanto a faixa estava ocupada. */
    private fun runDirty() {
        if (rerun.isEmpty()) return
        val pending = rerun.keys.toList().mapNotNull { key -> rerun.remove(key)?.let { key to it } }
        if (pending.isEmpty()) return
        // Se outra rodada tomou a faixa nesse meio tempo, a pendência volta (e roda quando ela terminar).
        val accepted = submit { pending.forEach { (_, step) -> try { step() } catch (_: Throwable) {} } }
        if (!accepted) pending.forEach { (key, step) -> rerun.putIfAbsent(key, step) }
    }

    fun isBusy(): Boolean = pending.get() > 0

    fun json(): JSONObject = JSONObject()
        .put("busy", pending.get() > 0)
        .put("hangs", hangs.get())
        .put("submitted", submitted.get())
        .put("completed", completed.get())
        .put("skipped", skipped.get())
        .put("failed", failed.get())
        .put("lastMs", lastDurationMs)
        .put("maxMs", maxDurationMs)
}

/** Passos independentes: um que lança vira aviso e os demais continuam (nenhuma falha derruba a rodada). */
object GuardedSteps {
    fun run(steps: List<Pair<String, () -> Unit>>, warn: (String) -> Unit) {
        for ((name, step) in steps) {
            try {
                step()
            } catch (error: Throwable) {
                try { warn("Passo '$name' falhou: ${error.message}") } catch (_: Throwable) {}
            }
        }
    }
}
