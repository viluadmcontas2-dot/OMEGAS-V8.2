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
 * - [get]: devolve o valor guardado na hora. Só velho ([staleMs]): agenda UM recálculo em segundo plano
 *   ([background]) e devolve o último valor (ECU#2: a troca de zona não trava o JavaScript). Sem valor ou
 *   invalidado (depois de gravar/resetar): calcula dentro da chamada; um cálculo iniciado antes do
 *   [invalidate] não vira válido (contador de geração).
 * - [getFresh]: calcula dentro da chamada (pedido explícito de leitura fresca, depois de gravar/restaurar).
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
    /** Onde o recálculo atrasado roda; padrão: uma thread de fundo compartilhada. */
    private val background: (Runnable) -> Unit = { task -> SHARED_BACKGROUND.execute(task) },
    private val compute: () -> String,
) {
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val computeLock = Any()
    @Volatile private var value: String? = null
    @Volatile private var valid = false
    @Volatile private var computedAt = 0L
    @Volatile private var requestedAt = -1L
    /** Sobe a cada [invalidate]: um cálculo que começou antes dele não vira valor válido. */
    private val generation = java.util.concurrent.atomic.AtomicLong(0L)
    private val computations = AtomicInteger(0)

    /** Quantas vezes o cálculo pesado rodou (para provar que a chamada da tela não o dispara). */
    fun computations(): Int = computations.get()

    fun get(): String {
        val now = clock()
        requestedAt = now
        val cached = value
        if (valid && cached != null && now - computedAt <= staleMs) return cached
        if (valid && cached != null) {
            // Só velho: devolve já e recalcula em segundo plano (ECU#2).
            scheduleRefresh()
            return cached
        }
        synchronized(computeLock) {
            // Primeira chamada ou valor invalidado (depois de gravar): calcula na hora. Outra thread pode ter
            // acabado de recalcular enquanto esta esperava o cadeado.
            val again = value
            if (valid && again != null) return again
            return runCompute()
        }
    }

    /** Valor calculado agora, dentro da chamada. Só para pedido explícito de leitura fresca. */
    fun getFresh(): String {
        requestedAt = clock()
        synchronized(computeLock) { return runCompute() }
    }

    /**
     * Leitura para superfícies síncronas (addJavascriptInterface): nunca executa [compute] na thread
     * chamadora. Marca o memo como observado (o relógio de fundo passa a recalculá-lo) e devolve o último
     * valor, mesmo vencido ou invalidado; sem valor anterior devolve [fallback]. Só lê [value], ignora [valid].
     * Quando a correção exige valor recalculado na hora (depois de gravar), usar [getFresh].
     */
    fun getNonBlocking(fallback: String): String {
        requestedAt = clock()
        return value ?: fallback
    }

    private fun scheduleRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) return
        try {
            background(Runnable {
                try {
                    synchronized(computeLock) {
                        if (!(valid && clock() - computedAt <= staleMs)) runCompute()
                    }
                } finally {
                    refreshQueued.set(false)
                }
            })
        } catch (_: Exception) {
            refreshQueued.set(false)
        }
    }

    fun invalidate() {
        generation.incrementAndGet()
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

    private companion object {
        val SHARED_BACKGROUND: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "omegas-memo-refresh").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
            }
    }

    private fun runCompute(): String {
        val startedGeneration = generation.get()
        val result = try {
            compute()
        } catch (error: Exception) {
            onError(error)
        }
        value = result
        computedAt = clock()
        valid = generation.get() == startedGeneration
        computations.incrementAndGet()
        return result
    }
}
