package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.learning.SampleDecision
import org.json.JSONObject

/** Proveniência imutável do único frame físico que originou este envelope. */
data class CanonicalEvidenceProvenance(
    val schema: String,
    val acquisitionSource: String,
    val sequence: Long,
    val usbSessionId: Long,
    val capturedAtElapsedMs: Long,
) {
    init {
        require(schema.isNotBlank())
        require(acquisitionSource.isNotBlank())
        require(sequence >= 0L)
        require(usbSessionId > 0L)
        require(capturedAtElapsedMs >= 0L)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("acquisition_source", acquisitionSource)
        .put("sequence", sequence)
        .put("usb_session_id", usbSessionId)
        .put("captured_at_elapsed_ms", capturedAtElapsedMs)
}

/**
 * Envelope canônico publicado uma única vez depois da aquisição MP48.
 *
 * Não é Store, polling, predictor nem writer. Apenas mantém a mesma identidade
 * física/proveniência enquanto State, Classic Science, Adaptive Shadow, UI e
 * Recorder aplicam políticas de backpressure independentes.
 */
data class CanonicalEvidence(
    val frame: RuntimeTelemetryFrame,
    val rawTelemetry: Mp48Telemetry,
    val sampleDecision: SampleDecision,
    val provenance: CanonicalEvidenceProvenance,
) {
    init {
        require(frame.sequence == provenance.sequence)
        require(frame.usbSessionId == provenance.usbSessionId)
        require(frame.capturedAtElapsedMs == provenance.capturedAtElapsedMs)
        require(rawTelemetry.capturedAtElapsedMs == provenance.capturedAtElapsedMs)
    }

    val sequence: Long get() = provenance.sequence
    val usbSessionId: Long get() = provenance.usbSessionId

    /** Payload do recorder derivado diretamente do envelope, sem depender da UI. */
    fun toRecorderJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("sequence", sequence)
        .put("session_id", usbSessionId)
        .put("captured_at_elapsed_ms", provenance.capturedAtElapsedMs)
        .put("rpm", frame.rpm)
        .put("petrol_ms", frame.petrolMs)
        .put("gas_ms_diagnostic", frame.gasMsDiagnostic ?: JSONObject.NULL)
        .put("water_c", frame.waterC)
        .put("gas_c", frame.gasTemperatureC)
        .put("gas_pressure_abs_bar", frame.gasPressureAbsBar)
        .put("map_bar", frame.mapBar)
        .put("load_bar", frame.mapBar)
        .put("pressure_diff_bar", frame.pressureDiffBar)
        .put("fuel", rawTelemetry.fuel.wireName)
        .put("plausible", frame.plausible)
        .put("freshness", frame.freshness.name)
        .put("sample_state", sampleDecision.state)
        .put("sample_reason_code", sampleDecision.reasonCode)
        .put("sample_classification", sampleDecision.classification.name)
        .put("learning_eligible", sampleDecision.learningEligible)
        .put("fuel_confirmed", sampleDecision.fuelConfirmed ?: JSONObject.NULL)
        .put("transition_target", sampleDecision.transitionTarget ?: JSONObject.NULL)
        .put("canonical_provenance", provenance.toJson())

    companion object {
        const val SCHEMA = "omegas-canonical-evidence-v1"
        const val ACQUISITION_SOURCE = "MP48_RESPONSE_DRIVEN"

        fun from(
            telemetry: Mp48Telemetry,
            decision: SampleDecision,
            sequence: Long,
            usbSessionId: Long,
        ): CanonicalEvidence {
            val frame = RuntimeTelemetryFrame.from(telemetry, sequence, usbSessionId)
            val provenance = CanonicalEvidenceProvenance(
                schema = SCHEMA,
                acquisitionSource = ACQUISITION_SOURCE,
                sequence = sequence,
                usbSessionId = usbSessionId,
                capturedAtElapsedMs = telemetry.capturedAtElapsedMs,
            )
            return CanonicalEvidence(
                frame = frame,
                rawTelemetry = telemetry,
                sampleDecision = decision,
                provenance = provenance,
            )
        }
    }
}
