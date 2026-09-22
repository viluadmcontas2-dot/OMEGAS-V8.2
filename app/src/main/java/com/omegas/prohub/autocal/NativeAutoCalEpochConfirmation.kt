package com.omegas.prohub.autocal

/**
 * Confirma um novo epoch nativo somente quando a mudança do contador AutoMatch
 * recebe readback posterior de MUL_ACT diferente do baseline anterior.
 *
 * O limite de snapshots é operacional: evita polling pesado infinito.
 * Não representa regra interna da ECU.
 */
internal class NativeAutoCalEpochConfirmation(
    private val maxSnapshots: Int = 3,
) {
    init {
        require(maxSnapshots >= 1)
    }

    data class Confirmation(
        val transition: AutoCalEpochTransition,
        val oldMulHash: String,
        val newMulHash: String,
        val snapshotsObserved: Int,
    )

    enum class StartResult {
        STARTED,
        DUPLICATE,
        IGNORED_NO_BASELINE,
        OVERLAP_DROPPED,
    }

    sealed class Observation {
        data object None : Observation()
        data class Awaiting(
            val transition: AutoCalEpochTransition,
            val snapshotsObserved: Int,
        ) : Observation()
        data class Confirmed(val value: Confirmation) : Observation()
        data class Expired(
            val transition: AutoCalEpochTransition,
            val snapshotsObserved: Int,
        ) : Observation()
    }

    private data class Pending(
        val transition: AutoCalEpochTransition,
        val oldMulHash: String,
        var snapshotsObserved: Int = 0,
    )

    private var pending: Pending? = null

    fun start(transition: AutoCalEpochTransition, oldMulHash: String): StartResult {
        if (oldMulHash.isBlank()) return StartResult.IGNORED_NO_BASELINE
        val current = pending
        if (current != null) {
            if (current.transition == transition) return StartResult.DUPLICATE
            pending = null
            return StartResult.OVERLAP_DROPPED
        }
        pending = Pending(transition = transition, oldMulHash = oldMulHash)
        return StartResult.STARTED
    }

    fun observeMul(currentMulHash: String): Observation {
        val current = pending ?: return Observation.None
        current.snapshotsObserved += 1
        if (currentMulHash.isNotBlank() && currentMulHash != current.oldMulHash) {
            val confirmation = Confirmation(
                transition = current.transition,
                oldMulHash = current.oldMulHash,
                newMulHash = currentMulHash,
                snapshotsObserved = current.snapshotsObserved,
            )
            pending = null
            return Observation.Confirmed(confirmation)
        }
        if (current.snapshotsObserved >= maxSnapshots) {
            val expired = Observation.Expired(
                transition = current.transition,
                snapshotsObserved = current.snapshotsObserved,
            )
            pending = null
            return expired
        }
        return Observation.Awaiting(
            transition = current.transition,
            snapshotsObserved = current.snapshotsObserved,
        )
    }

    fun hasPending(): Boolean = pending != null

    fun pendingTransition(): AutoCalEpochTransition? = pending?.transition

    fun reset() {
        pending = null
    }
}
