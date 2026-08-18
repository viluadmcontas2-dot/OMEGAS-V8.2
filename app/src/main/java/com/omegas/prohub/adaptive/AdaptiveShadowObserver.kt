package com.omegas.prohub.adaptive

import com.omegas.prohub.telemetry.CanonicalEvidence
import org.json.JSONObject

/**
 * Consumidor Adaptive read-only do Canonical Evidence Bus.
 *
 * Não mantém histórico, não faz polling e não produz proposta/escrita. O solver
 * futuro poderá substituir este observador no owner correto sem criar backbone
 * paralelo. Backpressure permanece no pipeline latest-only que o alimenta.
 */
class AdaptiveShadowObserver {
    private val lock = Any()
    private var activeUsbSessionId = 0L
    private var lastSequence = -1L
    private var observed = 0L
    private var rejectedStale = 0L
    private var lastFuel = "UNKNOWN"
    private var lastReasonCode = "UNKNOWN"
    private var lastLearningEligible = false
    private var lastCapturedAtElapsedMs = 0L

    fun beginSession(usbSessionId: Long) = synchronized(lock) {
        require(usbSessionId > 0L)
        activeUsbSessionId = usbSessionId
        lastSequence = -1L
        lastFuel = "UNKNOWN"
        lastReasonCode = "UNKNOWN"
        lastLearningEligible = false
        lastCapturedAtElapsedMs = 0L
    }

    fun endSession(usbSessionId: Long) = synchronized(lock) {
        if (activeUsbSessionId == usbSessionId) {
            activeUsbSessionId = 0L
            lastSequence = -1L
        }
    }

    fun observe(evidence: CanonicalEvidence): Boolean = synchronized(lock) {
        if (activeUsbSessionId <= 0L || evidence.usbSessionId != activeUsbSessionId || evidence.sequence <= lastSequence) {
            rejectedStale += 1L
            return@synchronized false
        }
        lastSequence = evidence.sequence
        observed += 1L
        lastFuel = evidence.frame.fuel.name
        lastReasonCode = evidence.sampleDecision.reasonCode
        lastLearningEligible = evidence.sampleDecision.learningEligible
        lastCapturedAtElapsedMs = evidence.provenance.capturedAtElapsedMs
        true
    }

    fun metricsJson(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("authority", "ADAPTIVE_SHADOW")
            .put("mode", "LIVE_SHADOW_READ_ONLY")
            .put("polling", false)
            .put("writer", false)
            .put("automatic_calibration", false)
            .put("canonical_schema", CanonicalEvidence.SCHEMA)
            .put("active_usb_session_id", activeUsbSessionId)
            .put("last_sequence", lastSequence)
            .put("observed", observed)
            .put("rejected_stale", rejectedStale)
            .put("last_fuel", lastFuel)
            .put("last_reason_code", lastReasonCode)
            .put("last_learning_eligible", lastLearningEligible)
            .put("last_captured_at_elapsed_ms", lastCapturedAtElapsedMs)
    }
}
