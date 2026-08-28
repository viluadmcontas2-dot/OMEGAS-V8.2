package com.omegas.prohub.learning

data class PredictorWorkTicket(
    val revision: Long,
    val generation: Long,
    val enqueuedAtMs: Long,
)

data class PredictorPublishResult<T>(
    val published: Boolean,
    val revision: Long,
    val value: T?,
)

data class PredictorWorkerMetrics(
    val requested: Long,
    val computed: Long,
    val coalesced: Long,
    val superseded: Long,
    val maxComputeMs: Long,
    val maxQueueAgeMs: Long,
)

/**
 * Bounded latest-revision coordinator. At most one active computation and one
 * pending newest revision exist. Completion publication is generation-checked.
 * It owns no executor/thread pool, serial scheduler, Store, Router or writer.
 */
class PredictorLatestRevisionWorker<T> {
    private var requested = 0L
    private var computed = 0L
    private var coalesced = 0L
    private var superseded = 0L
    private var maxComputeMs = 0L
    private var maxQueueAgeMs = 0L
    private var latestRevision = Long.MIN_VALUE
    private var latestGeneration = 0L
    private var pending: PredictorWorkTicket? = null
    private var active: PredictorWorkTicket? = null

    @Synchronized
    fun request(revision: Long, nowMs: Long): PredictorWorkTicket? {
        require(revision >= 0L)
        require(nowMs >= 0L)
        requested = increment(requested)
        if (revision <= latestRevision) {
            coalesced = increment(coalesced)
            return pending ?: active
        }
        latestRevision = revision
        latestGeneration = increment(latestGeneration)
        val ticket = PredictorWorkTicket(revision, latestGeneration, nowMs)
        if (pending != null) coalesced = increment(coalesced)
        pending = ticket
        return ticket
    }

    @Synchronized
    fun claimNext(nowMs: Long): PredictorWorkTicket? {
        require(nowMs >= 0L)
        if (active != null) return null
        val ticket = pending ?: return null
        pending = null
        active = ticket
        maxQueueAgeMs = maxOf(maxQueueAgeMs, (nowMs - ticket.enqueuedAtMs).coerceAtLeast(0L))
        return ticket
    }

    @Synchronized
    fun isCancelled(ticket: PredictorWorkTicket): Boolean = ticket.generation != latestGeneration

    @Synchronized
    fun complete(
        ticket: PredictorWorkTicket,
        value: T,
        computeMs: Long,
        nowMs: Long,
    ): PredictorPublishResult<T> {
        require(computeMs >= 0L)
        require(nowMs >= 0L)
        val current = active
        require(current == ticket) { "completion does not match active Predictor work" }
        active = null
        computed = increment(computed)
        maxComputeMs = maxOf(maxComputeMs, computeMs)
        val stale = ticket.generation != latestGeneration || ticket.revision != latestRevision
        if (stale) {
            superseded = increment(superseded)
            return PredictorPublishResult(false, ticket.revision, null)
        }
        return PredictorPublishResult(true, ticket.revision, value)
    }

    @Synchronized
    fun pendingRevision(): Long? = pending?.revision

    @Synchronized
    fun metrics(): PredictorWorkerMetrics = PredictorWorkerMetrics(
        requested = requested,
        computed = computed,
        coalesced = coalesced,
        superseded = superseded,
        maxComputeMs = maxComputeMs,
        maxQueueAgeMs = maxQueueAgeMs,
    )

    private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1L
}
