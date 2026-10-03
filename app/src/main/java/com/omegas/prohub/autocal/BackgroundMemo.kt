package com.omegas.prohub.autocal

import java.util.concurrent.atomic.AtomicInteger

/**
 * Resposta pronta para a WebView.
 *
 * Uma chamada de bridge bloqueia o JavaScript até voltar. Calcular índices, análises e
 * projeções dentro dela congelava a tela da multimídia (bolinha atrasada, troca de tela
 * travando). Aqui o resultado é recalculado em segundo plano, só enquanto alguém está olhando
 * ([watchMs]), e a chamada da tela devolve o último valor na hora.
 *
 * - [get]: devolve o valor guardado se ainda for novo ([staleMs]); senão calcula na hora (primeira
 *   chamada, depois de [invalidate], ou se o segundo plano parou).
 * - [refreshIfWatched]: chamado pelo relógio de segundo plano; recalcula a cada [refreshMs] só se
 *   a tela pediu o valor nos últimos [watchMs].
 * - [invalidate]: depois de uma ação que muda o estado (gravação, reset) a próxima leitura é fresca.
 *
 * Puro e determinístico: o relógio é injetado, o teste não dorme.
 */
class BackgroundMemo(
    private val refreshMs: Long,
    private val staleMs: Long,
    private val watchMs: Long = 15_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (Exception) -> String = { """{"ok":false,"error":"${it.message?.replace('"', '\'') ?: "falha"}"}""" },
    private val compute: () -> String,
) {
    private val computeLock = Any()
    @Volatile private var value: String? = null
    @Volatile private var valid = false
    @Volatile private var computedAt = 0L
    @Volatile private var requestedAt = -1L
    private val computations = AtomicInteger(0)

    /** Quantas vezes o cálculo pesado rodou (para provar que a chamada da tela não o dispara). */
    fun computations(): Int = computations.get()

    fun get(): String {
        val now = clock()
        requestedAt = now
        val cached = value
        if (valid && cached != null && now - computedAt <= staleMs) return cached
        synchronized(computeLock) {
            // Outra thread pode ter acabado de recalcular enquanto esta esperava o cadeado.
            val again = value
            if (valid && again != null && clock() - computedAt <= staleMs) return again
            return runCompute()
        }
    }

    fun invalidate() {
        valid = false
    }

    /** true se recalculou. */
    fun refreshIfWatched(): Boolean {
        val now = clock()
        val asked = requestedAt
        if (asked < 0L || now - asked > watchMs) return false
        if (valid && now - computedAt < refreshMs) return false
        synchronized(computeLock) {
            if (valid && clock() - computedAt < refreshMs) return false
            runCompute()
        }
        return true
    }

    private fun runCompute(): String {
        val result = try {
            compute()
        } catch (error: Exception) {
            onError(error)
        }
        value = result
        computedAt = clock()
        valid = true
        computations.incrementAndGet()
        return result
    }
}
