package com.omegas.prohub.learning

import org.json.JSONObject

/**
 * Estado científico durante qualquer janela em que a calibração física possa mudar.
 *
 * Esta autoridade não escreve ECU nem cria transporte. O árbitro serial abre a
 * quarentena antes do primeiro MANUAL_WRITE. Somente uma CalibrationIdentity
 * novamente conhecida libera a fase POST_WRITE_REVALIDATING.
 */
enum class LearningMutationState {
    STABLE,
    QUARANTINED_MUTATION_WINDOW,
    UNKNOWN,
    POST_WRITE_REVALIDATING,
}

data class LearningMutationSnapshot(
    val state: LearningMutationState,
    val usbSessionId: Long,
    val revision: Long,
    val reason: String,
    val openedAtMs: Long,
    val calibrationFingerprint: String?,
    val calibrationGeneration: Int?,
) {
    val blocksActiveScience: Boolean
        get() = state == LearningMutationState.QUARANTINED_MUTATION_WINDOW ||
            state == LearningMutationState.UNKNOWN

    fun toJson(): JSONObject = JSONObject()
        .put("state", state.name)
        .put("usb_session_id", usbSessionId)
        .put("revision", revision)
        .put("reason", reason)
        .put("opened_at_ms", openedAtMs)
        .put("blocks_active_science", blocksActiveScience)
        .put("telemetry_continues", true)
        .put("calibration_fingerprint", calibrationFingerprint ?: JSONObject.NULL)
        .put("calibration_generation", calibrationGeneration ?: JSONObject.NULL)
}

object LearningMutationAuthority {
    private val lock = Any()
    private var snapshot = LearningMutationSnapshot(
        state = LearningMutationState.STABLE,
        usbSessionId = 0L,
        revision = 0L,
        reason = "NO_ACTIVE_MUTATION",
        openedAtMs = 0L,
        calibrationFingerprint = null,
        calibrationGeneration = null,
    )

    fun beginPhysicalSession(usbSessionId: Long) = synchronized(lock) {
        require(usbSessionId > 0L)
        snapshot = snapshot.copy(
            state = LearningMutationState.STABLE,
            usbSessionId = usbSessionId,
            revision = snapshot.revision + 1L,
            reason = "PHYSICAL_SESSION_STARTED",
            openedAtMs = 0L,
            calibrationFingerprint = null,
            calibrationGeneration = null,
        )
    }

    /** Chamado pelo árbitro serial imediatamente antes de admitir MANUAL_WRITE. */
    fun beginManualWrite(usbSessionId: Long, reason: String) = synchronized(lock) {
        require(usbSessionId > 0L)
        if (snapshot.state == LearningMutationState.QUARANTINED_MUTATION_WINDOW &&
            snapshot.usbSessionId == usbSessionId
        ) return@synchronized snapshot
        LearningCalibrationAuthority.clear()
        snapshot = LearningMutationSnapshot(
            state = LearningMutationState.QUARANTINED_MUTATION_WINDOW,
            usbSessionId = usbSessionId,
            revision = snapshot.revision + 1L,
            reason = reason.ifBlank { "MANUAL_WRITE" }.take(240),
            openedAtMs = System.currentTimeMillis(),
            calibrationFingerprint = null,
            calibrationGeneration = null,
        )
        snapshot
    }

    /** Qualquer falha depois da abertura deixa a função física desconhecida até releitura válida. */
    fun markUnknown(usbSessionId: Long, reason: String) = synchronized(lock) {
        if (snapshot.usbSessionId != usbSessionId || snapshot.state == LearningMutationState.STABLE) {
            return@synchronized snapshot
        }
        LearningCalibrationAuthority.clear()
        snapshot = snapshot.copy(
            state = LearningMutationState.UNKNOWN,
            revision = snapshot.revision + 1L,
            reason = reason.ifBlank { "MUTATION_RECOVERY_UNKNOWN" }.take(240),
            calibrationFingerprint = null,
            calibrationGeneration = null,
        )
        snapshot
    }

    /** CalibrationIdentity fresca/readback é a única liberação pós-mutação. */
    fun onCalibrationIdentityKnown(binding: LearningCalibrationBinding) = synchronized(lock) {
        if (snapshot.usbSessionId != binding.usbSessionId) return@synchronized snapshot
        if (snapshot.state == LearningMutationState.STABLE) return@synchronized snapshot
        snapshot = snapshot.copy(
            state = LearningMutationState.POST_WRITE_REVALIDATING,
            revision = snapshot.revision + 1L,
            reason = "CALIBRATION_IDENTITY_RECONCILED",
            calibrationFingerprint = binding.calibrationFingerprint,
            calibrationGeneration = binding.calibrationGeneration,
        )
        snapshot
    }

    fun endPhysicalSession() = synchronized(lock) {
        snapshot = snapshot.copy(
            state = LearningMutationState.STABLE,
            usbSessionId = 0L,
            revision = snapshot.revision + 1L,
            reason = "PHYSICAL_SESSION_ENDED",
            openedAtMs = 0L,
            calibrationFingerprint = null,
            calibrationGeneration = null,
        )
    }

    fun current(): LearningMutationSnapshot = synchronized(lock) { snapshot.copy() }

    /**
     * Durante QUARANTINE/UNKNOWN a observação permanece no Canonical Evidence Bus,
     * mas é transformada em decisão diagnóstica antes de alcançar a memória ativa.
     */
    fun gate(decision: SampleDecision): SampleDecision {
        val current = current()
        if (!current.blocksActiveScience) return decision
        return SampleDecision.transition(
            state = current.state.name,
            reason = if (current.state == LearningMutationState.UNKNOWN) {
                "Calibração física ainda não reconciliada; telemetria continua, ciência material pausada"
            } else {
                "Calibração em mutação; telemetria continua apenas como diagnóstico"
            },
            frameCount = decision.frameCount,
            gapMs = decision.gapMs,
            diagnostics = decision.diagnostics,
            learningEligible = false,
            fuelConfirmed = decision.fuelConfirmed,
            transitionTarget = decision.transitionTarget,
            largestGapMs = decision.largestGapMs,
            toleratedGapCount = decision.toleratedGapCount,
            plannedOperation = true,
            continuityLost = decision.continuityLost,
            reasonCode = current.state.name,
            windowAgeMs = decision.windowAgeMs,
            windowBudgetMs = decision.windowBudgetMs,
            framesEvicted = decision.framesEvicted,
        )
    }
}
