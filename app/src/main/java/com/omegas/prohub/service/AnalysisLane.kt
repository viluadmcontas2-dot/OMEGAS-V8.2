package com.omegas.prohub.service

import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Faixa única de trabalho pesado de análise (refino, diário, `full_snapshot` JSON, overlay, notificação).
 *
 * Roda em um executor PRÓPRIO, fora da thread do `autoCalTick`: nada aqui pode atrasar a aquisição
 * AutoCal. Coalescente: se uma rodada ainda está em curso, a seguinte é descartada (contada em
 * `skipped`) em vez de enfileirar. Não conhece ECU, serial nem comandos.
 */
class AnalysisLane(
    private val executor: Executor,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onFailure: (Throwable) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val submitted = AtomicLong(0L)
    private val skipped = AtomicLong(0L)
    private val completed = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    @Volatile private var lastDurationMs = 0L
    @Volatile private var maxDurationMs = 0L

    /** Devolve true se a rodada foi aceita; false se a anterior ainda roda (ou o executor recusou). */
    fun submit(task: () -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) {
            skipped.incrementAndGet()
            return false
        }
        submitted.incrementAndGet()
        return try {
            executor.execute {
                val started = clock()
                try {
                    task()
                    completed.incrementAndGet()
                } catch (error: Throwable) {
                    failed.incrementAndGet()
                    try { onFailure(error) } catch (_: Throwable) {}
                } finally {
                    val took = (clock() - started).coerceAtLeast(0L)
                    lastDurationMs = took
                    if (took > maxDurationMs) maxDurationMs = took
                    running.set(false)
                }
            }
            true
        } catch (_: Exception) {
            running.set(false) // executor encerrado
            false
        }
    }

    fun isBusy(): Boolean = running.get()

    fun json(): JSONObject = JSONObject()
        .put("busy", running.get())
        .put("submitted", submitted.get())
        .put("completed", completed.get())
        .put("skipped", skipped.get())
        .put("failed", failed.get())
        .put("lastMs", lastDurationMs)
        .put("maxMs", maxDurationMs)
}
