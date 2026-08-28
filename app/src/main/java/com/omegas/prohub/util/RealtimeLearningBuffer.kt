package com.omegas.prohub.util

import org.json.JSONObject
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Buffer quente do aprendizado para aparelhos lentos.
 *
 * O frame MP48 já foi adquirido quando chega aqui. Este buffer controla somente
 * trabalho científico downstream: evidências possuem valor semântico explícito,
 * diagnóstico mantém apenas o estado mais recente e o backlog quente nunca cresce
 * sem limite. Em saturação científica, uma tarefa nova só substitui uma pendente
 * de valor menor ou igual; aquisição/telemetria nunca passa por esta decisão.
 */
class RealtimeLearningBuffer(
    threadName: String,
    importantCapacity: Int = 3,
    threadPriority: Int = Thread.NORM_PRIORITY - 1,
    private val consumerName: String = threadName,
    private val onFailure: (sequence: Long, error: Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    companion object {
        /** Limite duro: o backlog quente deve representar segundos, nunca minutos. */
        const val MAX_HOT_EVIDENCE = 3
        /** Estimativa conservadora quando o producer ainda não fornece tamanho próprio. */
        const val DEFAULT_RETAINED_TASK_BYTES = 768
    }

    private data class Task(
        val generation: Long,
        val sequence: Long,
        val workClass: EvidenceWorkClass,
        val estimatedBytes: Int,
        val enqueuedAtNanos: Long,
        val work: () -> Unit,
    ) {
        val important: Boolean get() = !workClass.diagnosticOnly
    }

    /**
     * Custo observado por classe sem transformar tempo de CPU em confiança.
     * MarginalInformationClass é somente ordem qualitativa de utilidade para
     * backpressure; custo é telemetria operacional independente.
     */
    private data class CostStats(
        var observations: Long = 0L,
        var lastQueueDelayMs: Long = 0L,
        var totalQueueDelayMs: Long = 0L,
        var maxQueueDelayMs: Long = 0L,
        var lastProcessingMs: Long = 0L,
        var totalProcessingMs: Long = 0L,
        var maxProcessingMs: Long = 0L,
        var cpuObservations: Long = 0L,
        var lastThreadCpuMs: Long = -1L,
        var totalThreadCpuMs: Long = 0L,
        var maxThreadCpuMs: Long = -1L,
    ) {
        fun observe(queueDelayMs: Long, processingMs: Long, threadCpuMs: Long) {
            observations += 1L
            lastQueueDelayMs = queueDelayMs
            totalQueueDelayMs += queueDelayMs
            maxQueueDelayMs = maxOf(maxQueueDelayMs, queueDelayMs)
            lastProcessingMs = processingMs
            totalProcessingMs += processingMs
            maxProcessingMs = maxOf(maxProcessingMs, processingMs)
            if (threadCpuMs >= 0L) {
                cpuObservations += 1L
                lastThreadCpuMs = threadCpuMs
                totalThreadCpuMs += threadCpuMs
                maxThreadCpuMs = maxOf(maxThreadCpuMs, threadCpuMs)
            }
        }

        fun toJson(workClass: EvidenceWorkClass): JSONObject = JSONObject()
            .put("marginalInformationClass", workClass.marginalInformationClass.name)
            .put("marginalInformationRank", workClass.marginalInformationClass.rank)
            .put("informationInterpretation", "QUALITATIVE_ORDER_NOT_CONFIDENCE_OR_PROBABILITY")
            .put("observations", observations)
            .put("lastQueueDelayMs", lastQueueDelayMs)
            .put("avgQueueDelayMs", if (observations > 0L) totalQueueDelayMs.toDouble() / observations else 0.0)
            .put("maxQueueDelayMs", maxQueueDelayMs)
            .put("lastProcessingMs", lastProcessingMs)
            .put("avgProcessingMs", if (observations > 0L) totalProcessingMs.toDouble() / observations else 0.0)
            .put("maxProcessingMs", maxProcessingMs)
            .put("cpuObservations", cpuObservations)
            .put("lastThreadCpuMs", lastThreadCpuMs)
            .put("avgThreadCpuMs", if (cpuObservations > 0L) totalThreadCpuMs.toDouble() / cpuObservations else JSONObject.NULL)
            .put("maxThreadCpuMs", if (cpuObservations > 0L) maxThreadCpuMs else JSONObject.NULL)
    }

    private val capacityImportant = importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)
    private val monitor = Object()
    private val accepting = AtomicBoolean(true)
    private val importantQueue = ArrayDeque<Task>()
    private var latestTransient: Task? = null
    private var active: Task? = null
    private var activeGeneration = 0L
    private var currentGeneration = 0L
    private var importantSinceTransient = 0

    private val submittedFrames = AtomicLong(0L)
    private val acceptedImportant = AtomicLong(0L)
    private val acceptedTransient = AtomicLong(0L)
    private val executed = AtomicLong(0L)
    private val executedImportant = AtomicLong(0L)
    private val executedTransient = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    private val coalescedTransient = AtomicLong(0L)
    private val supersededImportant = AtomicLong(0L)
    private val rejectedLowValue = AtomicLong(0L)
    private val rejectedStale = AtomicLong(0L)
    private val purgedImportant = AtomicLong(0L)
    private val purgedTransient = AtomicLong(0L)
    private val lastQueueDelayMs = AtomicLong(0L)
    private val maxQueueDelayMs = AtomicLong(0L)
    private val lastImportantQueueDelayMs = AtomicLong(0L)
    private val maxImportantQueueDelayMs = AtomicLong(0L)
    private val lastProcessingMs = AtomicLong(0L)
    private val maxProcessingMs = AtomicLong(0L)
    private val lastImportantProcessingMs = AtomicLong(0L)
    private val maxImportantProcessingMs = AtomicLong(0L)
    private val lastThreadCpuMs = AtomicLong(-1L)
    private val maxThreadCpuMs = AtomicLong(-1L)
    private val lastImportantThreadCpuMs = AtomicLong(-1L)
    private val maxImportantThreadCpuMs = AtomicLong(-1L)
    private val lastCompletedSequence = AtomicLong(0L)
    private val acceptedByClass = EnumMap<EvidenceWorkClass, Long>(EvidenceWorkClass::class.java)
    private val executedByClass = EnumMap<EvidenceWorkClass, Long>(EvidenceWorkClass::class.java)
    private val supersededByClass = EnumMap<EvidenceWorkClass, Long>(EvidenceWorkClass::class.java)
    private val rejectedByClass = EnumMap<EvidenceWorkClass, Long>(EvidenceWorkClass::class.java)
    private val costByClass = EnumMap<EvidenceWorkClass, CostStats>(EvidenceWorkClass::class.java)

    private val worker = Thread({ runLoop() }, threadName).apply {
        isDaemon = true
        priority = threadPriority.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
        start()
    }

    /** Troca a geração ativa e elimina trabalho pendente de qualquer sessão anterior. */
    fun beginGeneration(generation: Long) {
        synchronized(monitor) {
            currentGeneration = generation
            purgeQueuedLocked()
            importantSinceTransient = 0
            monitor.notifyAll()
        }
    }

    /**
     * Tenta drenar apenas o pequeno buffer quente. Ao fim, invalida a geração e
     * remove o que restou; a sessão gravada permanece como evidência durável.
     */
    fun endGeneration(generation: Long, drainMs: Long = 750L) {
        flush(drainMs)
        synchronized(monitor) {
            if (currentGeneration == generation) currentGeneration = 0L
            purgeQueuedLocked()
            importantSinceTransient = 0
            monitor.notifyAll()
        }
    }

    /** Compatibilidade temporária para produtores ainda não migrados ao Router semântico. */
    fun submit(
        generation: Long,
        sequence: Long,
        important: Boolean,
        estimatedBytes: Int = DEFAULT_RETAINED_TASK_BYTES,
        work: () -> Unit,
    ): Boolean = submit(
        generation = generation,
        sequence = sequence,
        workClass = EvidenceBackpressurePolicy.fromLegacyImportant(important),
        estimatedBytes = estimatedBytes,
        work = work,
    )

    fun submit(
        generation: Long,
        sequence: Long,
        workClass: EvidenceWorkClass,
        estimatedBytes: Int = DEFAULT_RETAINED_TASK_BYTES,
        work: () -> Unit,
    ): Boolean {
        submittedFrames.incrementAndGet()
        synchronized(monitor) {
            if (!accepting.get() || generation <= 0L || generation != currentGeneration) {
                rejectedStale.incrementAndGet()
                increment(rejectedByClass, workClass)
                return false
            }
            val task = Task(
                generation = generation,
                sequence = sequence,
                workClass = workClass,
                estimatedBytes = estimatedBytes.coerceAtLeast(0),
                enqueuedAtNanos = System.nanoTime(),
                work = work,
            )
            val accepted = if (task.important) {
                admitImportantLocked(task)
            } else {
                if (latestTransient != null) coalescedTransient.incrementAndGet()
                latestTransient = task
                acceptedTransient.incrementAndGet()
                increment(acceptedByClass, workClass)
                true
            }
            if (accepted) monitor.notifyAll()
            return accepted
        }
    }

    /** Aguarda o buffer atual ficar vazio, sem criar uma fila adicional. */
    fun flush(timeoutMs: Long = 2_000L): Boolean {
        if (Thread.currentThread() === worker) return true
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
        synchronized(monitor) {
            while (importantQueue.isNotEmpty() || latestTransient != null || active != null) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                val waitMs = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L)
                try {
                    monitor.wait(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return true
        }
    }

    fun metricsJson(): JSONObject = synchronized(monitor) {
        val queuedEstimatedBytes = importantQueue.sumOf { it.estimatedBytes.toLong() } +
            (latestTransient?.estimatedBytes?.toLong() ?: 0L)
        JSONObject()
            .put("consumer", consumerName)
            .put("trigger", "EVENT_DRIVEN_SCIENCE_DECISION")
            .put("cadence", "EVENT_DRIVEN_NO_TIMER")
            .put("mode", "SEMANTIC_EVIDENCE_ROUTER_BOUNDED_SESSION_DURABLE")
            .put("durableBacklog", "SESSION_RECORDER")
            .put("queueBoundImportant", capacityImportant)
            .put("queueBoundDiagnostic", 1)
            .put("overloadPolicy", "SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING")
            .put("acquisitionDropAllowed", false)
            .put("pendingBytesKind", "DECLARED_ESTIMATE_NOT_HEAP_MEASUREMENT")
            .put("defaultRetainedTaskBytes", DEFAULT_RETAINED_TASK_BYTES)
            .put("cpuAccounting", "ANDROID_THREAD_CPU_TIME_WHEN_AVAILABLE")
            .put("marginalInformationModel", "QUALITATIVE_ORDER_ONLY_NOT_CONFIDENCE_OR_PROBABILITY")
            .put("accepting", accepting.get())
            .put("generation", currentGeneration)
            .put("capacityImportant", capacityImportant)
            .put("pendingImportant", importantQueue.size)
            .put("pendingTransient", if (latestTransient == null) 0 else 1)
            .put("active", if (active == null) 0 else 1)
            .put("activeGeneration", activeGeneration)
            .put("queuedEstimatedBytes", queuedEstimatedBytes)
            .put("activeEstimatedBytes", active?.estimatedBytes ?: 0)
            .put("pending", importantQueue.size + (if (latestTransient == null) 0 else 1) + (if (active == null) 0 else 1))
            .put("submittedFrames", submittedFrames.get())
            .put("acceptedImportant", acceptedImportant.get())
            .put("acceptedTransient", acceptedTransient.get())
            .put("executed", executed.get())
            .put("executedImportant", executedImportant.get())
            .put("executedTransient", executedTransient.get())
            .put("coalescedTransient", coalescedTransient.get())
            .put("supersededImportant", supersededImportant.get())
            .put("rejectedLowValue", rejectedLowValue.get())
            .put("rejectedStale", rejectedStale.get())
            .put("purgedImportant", purgedImportant.get())
            .put("purgedTransient", purgedTransient.get())
            .put("failed", failed.get())
            .put("lastCompletedSequence", lastCompletedSequence.get())
            .put("lastQueueDelayMs", lastQueueDelayMs.get())
            .put("maxQueueDelayMs", maxQueueDelayMs.get())
            .put("lastImportantQueueDelayMs", lastImportantQueueDelayMs.get())
            .put("maxImportantQueueDelayMs", maxImportantQueueDelayMs.get())
            .put("lastProcessingMs", lastProcessingMs.get())
            .put("maxProcessingMs", maxProcessingMs.get())
            .put("lastImportantProcessingMs", lastImportantProcessingMs.get())
            .put("maxImportantProcessingMs", maxImportantProcessingMs.get())
            .put("lastThreadCpuMs", lastThreadCpuMs.get())
            .put("maxThreadCpuMs", maxThreadCpuMs.get())
            .put("lastImportantThreadCpuMs", lastImportantThreadCpuMs.get())
            .put("maxImportantThreadCpuMs", maxImportantThreadCpuMs.get())
            .put("pendingByClass", countPendingByClassLocked())
            .put("acceptedByClass", enumMapJson(acceptedByClass))
            .put("executedByClass", enumMapJson(executedByClass))
            .put("supersededByClass", enumMapJson(supersededByClass))
            .put("rejectedByClass", enumMapJson(rejectedByClass))
            .put("costByClass", costByClassJsonLocked())
    }

    override fun close() {
        if (!accepting.compareAndSet(true, false)) return
        synchronized(monitor) { monitor.notifyAll() }
        try {
            worker.join(2_000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (worker.isAlive) worker.interrupt()
        synchronized(monitor) {
            purgeQueuedLocked()
            monitor.notifyAll()
        }
    }

    private fun admitImportantLocked(task: Task): Boolean {
        if (importantQueue.size >= capacityImportant) {
            val snapshot = importantQueue.toList()
            val lowestIndex = snapshot.indices.minWithOrNull(
                compareBy<Int> { snapshot[it].workClass.valueRank }
                    .thenBy { snapshot[it].sequence },
            ) ?: 0
            val lowest = snapshot[lowestIndex]
            if (!EvidenceBackpressurePolicy.incomingMaySupersede(task.workClass, lowest.workClass)) {
                rejectedLowValue.incrementAndGet()
                increment(rejectedByClass, task.workClass)
                return false
            }
            importantQueue.clear()
            snapshot.forEachIndexed { index, pending ->
                if (index != lowestIndex) importantQueue.addLast(pending)
            }
            supersededImportant.incrementAndGet()
            increment(supersededByClass, lowest.workClass)
        }
        importantQueue.addLast(task)
        acceptedImportant.incrementAndGet()
        increment(acceptedByClass, task.workClass)
        return true
    }

    private fun runLoop() {
        while (accepting.get() || hasPending()) {
            val task = synchronized(monitor) {
                while (accepting.get() && importantQueue.isEmpty() && latestTransient == null) {
                    try {
                        monitor.wait(250L)
                    } catch (_: InterruptedException) {
                        if (!accepting.get()) return
                    }
                }
                val next = chooseNextLocked()
                active = next
                activeGeneration = next?.generation ?: 0L
                next
            } ?: continue

            val startedAt = System.nanoTime()
            val cpuStartedAt = ThreadCpuClock.nowNanos()
            val queueDelayMs = nanosToMillis(startedAt - task.enqueuedAtNanos)
            lastQueueDelayMs.set(queueDelayMs)
            updateMaximum(maxQueueDelayMs, queueDelayMs)
            if (task.important) {
                lastImportantQueueDelayMs.set(queueDelayMs)
                updateMaximum(maxImportantQueueDelayMs, queueDelayMs)
            }
            try {
                val generationStillCurrent = synchronized(monitor) { task.generation == currentGeneration }
                if (generationStillCurrent) {
                    task.work()
                    executed.incrementAndGet()
                    synchronized(monitor) { increment(executedByClass, task.workClass) }
                    if (task.important) executedImportant.incrementAndGet() else executedTransient.incrementAndGet()
                    lastCompletedSequence.set(task.sequence)
                } else {
                    if (task.important) purgedImportant.incrementAndGet() else purgedTransient.incrementAndGet()
                }
            } catch (error: Throwable) {
                failed.incrementAndGet()
                onFailure(task.sequence, error)
            } finally {
                val processingMs = nanosToMillis(System.nanoTime() - startedAt)
                lastProcessingMs.set(processingMs)
                updateMaximum(maxProcessingMs, processingMs)
                if (task.important) {
                    lastImportantProcessingMs.set(processingMs)
                    updateMaximum(maxImportantProcessingMs, processingMs)
                }
                val cpuMs = ThreadCpuClock.elapsedMillis(cpuStartedAt, ThreadCpuClock.nowNanos())
                if (cpuMs >= 0L) {
                    lastThreadCpuMs.set(cpuMs)
                    updateMaximum(maxThreadCpuMs, cpuMs)
                    if (task.important) {
                        lastImportantThreadCpuMs.set(cpuMs)
                        updateMaximum(maxImportantThreadCpuMs, cpuMs)
                    }
                }
                synchronized(monitor) {
                    costByClass.getOrPut(task.workClass) { CostStats() }
                        .observe(queueDelayMs, processingMs, cpuMs)
                    active = null
                    activeGeneration = 0L
                    monitor.notifyAll()
                }
            }
        }
    }

    private fun chooseNextLocked(): Task? {
        val transient = latestTransient
        if (transient != null && (importantQueue.isEmpty() || importantSinceTransient >= 2)) {
            latestTransient = null
            importantSinceTransient = 0
            return transient
        }
        if (importantQueue.isNotEmpty()) {
            importantSinceTransient += 1
            val best = importantQueue.maxWithOrNull(
                compareBy<Task> { it.workClass.valueRank }
                    .thenByDescending { it.sequence },
            ) ?: return null
            importantQueue.remove(best)
            return best
        }
        if (transient != null) {
            latestTransient = null
            importantSinceTransient = 0
            return transient
        }
        return null
    }

    private fun purgeQueuedLocked() {
        if (importantQueue.isNotEmpty()) {
            purgedImportant.addAndGet(importantQueue.size.toLong())
            importantQueue.clear()
        }
        if (latestTransient != null) {
            purgedTransient.incrementAndGet()
            latestTransient = null
        }
    }

    private fun countPendingByClassLocked(): JSONObject = JSONObject().also { root ->
        EvidenceWorkClass.entries.forEach { workClass ->
            val pendingImportant = importantQueue.count { it.workClass == workClass }
            val pendingTransient = if (latestTransient?.workClass == workClass) 1 else 0
            val activeCount = if (active?.workClass == workClass) 1 else 0
            root.put(workClass.name, pendingImportant + pendingTransient + activeCount)
        }
    }

    private fun costByClassJsonLocked(): JSONObject = JSONObject().also { root ->
        EvidenceWorkClass.entries.forEach { workClass ->
            root.put(workClass.name, (costByClass[workClass] ?: CostStats()).toJson(workClass))
        }
    }

    private fun enumMapJson(source: EnumMap<EvidenceWorkClass, Long>): JSONObject = JSONObject().also { root ->
        EvidenceWorkClass.entries.forEach { workClass -> root.put(workClass.name, source[workClass] ?: 0L) }
    }

    private fun increment(target: EnumMap<EvidenceWorkClass, Long>, workClass: EvidenceWorkClass) {
        target[workClass] = (target[workClass] ?: 0L) + 1L
    }

    private fun hasPending(): Boolean = synchronized(monitor) {
        importantQueue.isNotEmpty() || latestTransient != null || active != null
    }

    private fun nanosToMillis(value: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(value.coerceAtLeast(0L))

    private fun updateMaximum(target: AtomicLong, candidate: Long) {
        var current = target.get()
        while (candidate > current && !target.compareAndSet(current, candidate)) {
            current = target.get()
        }
    }
}
