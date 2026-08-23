package com.omegas.prohub.physics

import kotlin.math.exp
import kotlin.math.ln

/**
 * Typed evidence vocabulary for Phase 06 calibration physics.
 *
 * The model deliberately separates observed/configured/static-oracle values
 * from values that are validated as effective at runtime. UNKNOWN is a real
 * state: no factory in this file silently substitutes zero or invents live
 * provenance.
 */
enum class PhysicsKnownness { KNOWN, UNKNOWN }

enum class PhysicsEvidenceAuthority {
    PHYSICAL_PROTOCOL,
    LIVE_VALIDATED,
    STATIC_ORACLE_CANDIDATE,
    OBSERVED_CONTEXT,
    CONFIGURED_ONLY,
    POLICY_ONLY,
    UNKNOWN,
}

enum class PhysicsEvidenceId {
    K1_MAP,
    MUL_ACT,
    K2_PRESSURE,
    K3_ECU_SIDE,
    K4_GAS_TEMP,
    GAS_DEADTIME,
    RPM,
    MAP,
    WATER_TEMP,
    GAS_TEMP,
    PRESSURE,
}

enum class MagnitudeAuthority {
    PHYSICALLY_ANCHORED,
    EMPIRICALLY_BOUNDED,
    POLICY_ONLY,
    UNKNOWN,
}

enum class EffectDirection { INCREASE, DECREASE, NEUTRAL, UNKNOWN }

enum class CorrectionMechanism {
    MAP_LOCAL,
    CURVE_MUL_ACT,
    ENVIRONMENTAL_DIAGNOSTIC,
    NO_ACTION,
    UNKNOWN,
}

enum class PhysicsModelAuthority { LOCAL_MODEL, ECU_INTERPOLATOR }

enum class FactorRole { CONFIGURED, LIVE_EFFECTIVE, STATIC_ORACLE_CANDIDATE, UNKNOWN }

data class PhysicsEvidenceRow(
    val id: PhysicsEvidenceId,
    val rawScale: String,
    val unit: String,
    val knownEffect: String,
    val evidenceLevel: String,
    val authority: PhysicsEvidenceAuthority,
    val knownness: PhysicsKnownness,
    val oracle: String,
    val unknowns: Set<String>,
    val consumers: Set<String>,
)

object PhysicsEvidenceMatrix {
    val rows: List<PhysicsEvidenceRow> = listOf(
        PhysicsEvidenceRow(
            PhysicsEvidenceId.K1_MAP, "raw/128", "factor", "local K1 component; 128 is neutral",
            "E4", PhysicsEvidenceAuthority.PHYSICAL_PROTOCOL, PhysicsKnownness.KNOWN,
            "Map K read/readback", setOf("not-total-Tgas-model"), setOf("physics", "map", "predictor"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.MUL_ACT, "Q14 raw/16384", "factor", "multiplicative curve component on active gas parcel",
            "E4", PhysicsEvidenceAuthority.PHYSICAL_PROTOCOL, PhysicsKnownness.KNOWN,
            "Curve K read/readback", setOf("plant-gain-not-universal"), setOf("physics", "curve", "predictor"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.K2_PRESSURE, "raw/8192", "factor", "host-side pressure-related candidate",
            "E3", PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsKnownness.KNOWN,
            "FREST Packet 0x0A static oracle", setOf("no-live-capture", "ecu-side-generation"), setOf("physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.K3_ECU_SIDE, "UNKNOWN", "factor", "UNKNOWN",
            "UNKNOWN", PhysicsEvidenceAuthority.UNKNOWN, PhysicsKnownness.UNKNOWN,
            "none", setOf("role", "scale", "generation", "live-value"), setOf("physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.K4_GAS_TEMP, "raw/32768", "factor", "host-side gas-temperature-related candidate",
            "E3", PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsKnownness.KNOWN,
            "FREST Packet 0x0A static oracle", setOf("no-live-capture", "ecu-side-generation"), setOf("physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.GAS_DEADTIME, "source-specific", "ms", "subtract only when current/fresh",
            "EVIDENCE_REQUIRED", PhysicsEvidenceAuthority.UNKNOWN, PhysicsKnownness.UNKNOWN,
            "current-session provenance", setOf("value-unless-read"), setOf("active-gas-pulse", "expected-effect"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.RPM, "protocol scale", "rpm", "operating context",
            "E4", PhysicsEvidenceAuthority.OBSERVED_CONTEXT, PhysicsKnownness.KNOWN,
            "MP48 telemetry", emptySet(), setOf("context", "learning", "physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.MAP, "protocol scale", "bar", "operating/load context",
            "E4", PhysicsEvidenceAuthority.OBSERVED_CONTEXT, PhysicsKnownness.KNOWN,
            "MP48 telemetry", emptySet(), setOf("context", "learning", "physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.WATER_TEMP, "protocol scale", "C", "environmental context",
            "OBSERVED", PhysicsEvidenceAuthority.OBSERVED_CONTEXT, PhysicsKnownness.KNOWN,
            "MP48 telemetry", setOf("not-a-K3-live-factor"), setOf("context", "physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.GAS_TEMP, "protocol/static candidate", "C", "environmental/confounder context",
            "OBSERVED_OR_STATIC", PhysicsEvidenceAuthority.OBSERVED_CONTEXT, PhysicsKnownness.KNOWN,
            "telemetry when observed", setOf("K4-live-generation"), setOf("context", "physics"),
        ),
        PhysicsEvidenceRow(
            PhysicsEvidenceId.PRESSURE, "protocol scale", "bar", "environmental/actuation context",
            "E4", PhysicsEvidenceAuthority.OBSERVED_CONTEXT, PhysicsKnownness.KNOWN,
            "MP48 telemetry", setOf("K2-live-generation"), setOf("context", "physics"),
        ),
    )

    private val byId = rows.associateBy { it.id }
    fun require(id: PhysicsEvidenceId): PhysicsEvidenceRow = requireNotNull(byId[id]) { "Missing physics evidence row: $id" }
}

data class DeadtimeEvidence(
    val valueMs: Double?,
    val knownness: PhysicsKnownness,
    val fresh: Boolean,
    val provenance: String,
) {
    init {
        require(valueMs == null || valueMs >= 0.0) { "deadtime must be non-negative" }
        if (knownness == PhysicsKnownness.KNOWN) require(valueMs != null && fresh) { "known deadtime must be fresh and valued" }
    }

    companion object {
        fun known(valueMs: Double, provenance: String): DeadtimeEvidence =
            DeadtimeEvidence(valueMs, PhysicsKnownness.KNOWN, true, provenance)

        fun unknown(reason: String): DeadtimeEvidence =
            DeadtimeEvidence(null, PhysicsKnownness.UNKNOWN, false, reason)
    }
}

data class ActiveGasPulse(
    val gasPulseMs: Double,
    val deadtimeMs: Double?,
    val activePulseMs: Double?,
    val authority: MagnitudeAuthority,
    val reason: String,
)

object GasPulsePhysics {
    fun activePulse(gasPulseMs: Double, deadtime: DeadtimeEvidence): ActiveGasPulse {
        require(gasPulseMs >= 0.0) { "gas pulse must be non-negative" }
        if (deadtime.knownness != PhysicsKnownness.KNOWN || !deadtime.fresh || deadtime.valueMs == null) {
            return ActiveGasPulse(
                gasPulseMs = gasPulseMs,
                deadtimeMs = null,
                activePulseMs = null,
                authority = MagnitudeAuthority.UNKNOWN,
                reason = "DEADTIME_UNKNOWN:${deadtime.provenance}",
            )
        }
        return ActiveGasPulse(
            gasPulseMs = gasPulseMs,
            deadtimeMs = deadtime.valueMs,
            activePulseMs = (gasPulseMs - deadtime.valueMs).coerceAtLeast(0.0),
            authority = MagnitudeAuthority.PHYSICALLY_ANCHORED,
            reason = "KNOWN_FRESH_DEADTIME",
        )
    }
}

data class FactorEvidence(
    val name: String,
    val value: Double?,
    val raw: Int?,
    val role: FactorRole,
    val authority: PhysicsEvidenceAuthority,
    val knownness: PhysicsKnownness,
    val provenance: String,
    val sessionId: Long?,
    val capturedAtMs: Long?,
) {
    fun isLiveEffective(): Boolean = role == FactorRole.LIVE_EFFECTIVE &&
        authority == PhysicsEvidenceAuthority.LIVE_VALIDATED &&
        knownness == PhysicsKnownness.KNOWN && value != null

    fun asUnknownLive(reason: String): FactorEvidence = copy(
        value = null,
        raw = null,
        role = FactorRole.LIVE_EFFECTIVE,
        authority = PhysicsEvidenceAuthority.UNKNOWN,
        knownness = PhysicsKnownness.UNKNOWN,
        provenance = reason,
        sessionId = null,
        capturedAtMs = null,
    )

    companion object {
        fun configured(name: String, value: Double, provenance: String): FactorEvidence = FactorEvidence(
            name, value, null, FactorRole.CONFIGURED, PhysicsEvidenceAuthority.CONFIGURED_ONLY,
            PhysicsKnownness.KNOWN, provenance, null, null,
        )

        fun liveEffective(
            name: String,
            value: Double,
            provenance: String,
            sessionId: Long,
            capturedAtMs: Long,
        ): FactorEvidence = FactorEvidence(
            name, value, null, FactorRole.LIVE_EFFECTIVE, PhysicsEvidenceAuthority.LIVE_VALIDATED,
            PhysicsKnownness.KNOWN, provenance, sessionId, capturedAtMs,
        )
    }
}

object PhysicsFactors {
    const val K1_IS_TOTAL_GAS_MODEL: Boolean = false

    fun k1FromMapRaw(raw: Int): Double {
        PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K1_MAP)
        require(raw in 0..255) { "K1 Map raw must be U8" }
        return raw / 128.0
    }

    fun k1Direction(raw: Int): EffectDirection = when {
        raw > 128 -> EffectDirection.INCREASE
        raw < 128 -> EffectDirection.DECREASE
        else -> EffectDirection.NEUTRAL
    }

    fun mulActFromQ14(raw: Int): Double {
        PhysicsEvidenceMatrix.require(PhysicsEvidenceId.MUL_ACT)
        require(raw >= 0) { "MUL_ACT raw must be non-negative" }
        return raw / 16384.0
    }

    fun mulActDirection(beforeRaw: Int, afterRaw: Int): EffectDirection = when {
        afterRaw > beforeRaw -> EffectDirection.INCREASE
        afterRaw < beforeRaw -> EffectDirection.DECREASE
        else -> EffectDirection.NEUTRAL
    }

    fun k2StaticCandidate(raw: Int): FactorEvidence {
        PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K2_PRESSURE)
        return FactorEvidence(
            "K2_PRESSURE", raw / 8192.0, raw, FactorRole.STATIC_ORACLE_CANDIDATE,
            PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsKnownness.KNOWN,
            "FREST_0A_STATIC_ORACLE", null, null,
        )
    }

    fun k4StaticCandidate(raw: Int): FactorEvidence {
        PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K4_GAS_TEMP)
        return FactorEvidence(
            "K4_GAS_TEMP", raw / 32768.0, raw, FactorRole.STATIC_ORACLE_CANDIDATE,
            PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsKnownness.KNOWN,
            "FREST_0A_STATIC_ORACLE", null, null,
        )
    }

    fun k3Unknown(): FactorEvidence {
        PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K3_ECU_SIDE)
        return FactorEvidence(
            "K3_ECU_SIDE", null, null, FactorRole.UNKNOWN,
            PhysicsEvidenceAuthority.UNKNOWN, PhysicsKnownness.UNKNOWN,
            "ECU_SIDE_GENERATION_UNKNOWN", null, null,
        )
    }
}

data class CalibrationIdentityRef(
    val functionFingerprint: String,
    val geometryFingerprint: String,
    val mapHash: String,
    val curveFingerprint: String,
    val usbSessionId: Long,
    val generation: Int,
    val provenance: String,
) {
    init {
        require(functionFingerprint.isNotBlank())
        require(geometryFingerprint.isNotBlank())
        require(mapHash.isNotBlank())
        require(curveFingerprint.isNotBlank())
        require(usbSessionId > 0L)
        require(generation >= 0)
        require(provenance.isNotBlank())
    }
}

data class ContextSlice(
    val rpm: Double,
    val mapBar: Double,
    val pressureDeltaBar: Double?,
    val gasTempC: Double?,
    val waterC: Double?,
    val capturedAtMs: Long,
)

data class CalibrationPhysicsContext(
    val identity: CalibrationIdentityRef,
    val microState: ContextSlice,
    val deadtime: DeadtimeEvidence,
    val mapEffective: FactorEvidence,
    val curveEffective: FactorEvidence,
    val mEff: Double?,
    val cEff: Double?,
    val fCurrent: Double?,
    val uncertainty: Double,
) {
    companion object {
        fun create(
            identity: CalibrationIdentityRef,
            microState: ContextSlice,
            deadtime: DeadtimeEvidence,
            mapEffective: FactorEvidence,
            curveEffective: FactorEvidence,
            uncertainty: Double,
        ): CalibrationPhysicsContext {
            require(uncertainty >= 0.0)
            val m = mapEffective.value.takeIf { mapEffective.isLiveEffective() }
            val c = curveEffective.value.takeIf { curveEffective.isLiveEffective() }
            return CalibrationPhysicsContext(
                identity = identity,
                microState = microState,
                deadtime = deadtime,
                mapEffective = mapEffective,
                curveEffective = curveEffective,
                mEff = m,
                cEff = c,
                fCurrent = if (m != null && c != null) m * c else null,
                uncertainty = uncertainty,
            )
        }
    }
}

data class IdealTarget(
    val factor: Double,
    val authority: MagnitudeAuthority,
)

data class AppliedStep(
    val factor: Double,
    val fraction: Double,
    val authority: MagnitudeAuthority,
)

interface TargetEstimator
interface ActuatorAllocator
interface StepPolicy {
    fun selectStep(currentFactor: Double, target: IdealTarget, uncertainty: Double): AppliedStep
}

class LegacyAdvisorStepPolicy : StepPolicy {
    val minimumFraction: Double = 0.45
    val maximumFraction: Double = 0.90
    val authority: MagnitudeAuthority = MagnitudeAuthority.POLICY_ONLY

    override fun selectStep(currentFactor: Double, target: IdealTarget, uncertainty: Double): AppliedStep {
        require(currentFactor > 0.0 && currentFactor.isFinite())
        require(target.factor > 0.0 && target.factor.isFinite())
        val normalizedRisk = uncertainty.coerceIn(0.0, 1.0)
        val fraction = (maximumFraction - (maximumFraction - minimumFraction) * normalizedRisk)
            .coerceIn(minimumFraction, maximumFraction)
        val factor = currentFactor + (target.factor - currentFactor) * fraction
        return AppliedStep(factor, fraction, MagnitudeAuthority.POLICY_ONLY)
    }
}

data class ExpectedEffect(
    val direction: EffectDirection,
    val lowerBound: Double?,
    val upperBound: Double?,
    val assumptions: List<String>,
    val authority: MagnitudeAuthority,
    val falsifier: String,
) {
    init {
        require(lowerBound == null || upperBound == null || lowerBound <= upperBound)
        require(falsifier.isNotBlank())
    }
}

data class CorrectionDecision(
    val mechanism: CorrectionMechanism,
    val effect: ExpectedEffect,
    val target: IdealTarget?,
    val evidencePath: List<String>,
) {
    init { require(evidencePath.isNotEmpty()) { "Every mechanism decision requires an evidence path" } }

    companion object {
        fun inconclusive(reason: String): CorrectionDecision = CorrectionDecision(
            mechanism = CorrectionMechanism.UNKNOWN,
            effect = ExpectedEffect(
                direction = EffectDirection.UNKNOWN,
                lowerBound = null,
                upperBound = null,
                assumptions = listOf("insufficient model/support/context"),
                authority = MagnitudeAuthority.UNKNOWN,
                falsifier = "additional comparable evidence resolves mechanism",
            ),
            target = null,
            evidencePath = listOf(reason),
        )
    }
}

data class PlantGain(
    val mean: Double?,
    val lower: Double?,
    val upper: Double?,
    val authority: MagnitudeAuthority,
) {
    companion object {
        fun unknown(): PlantGain = PlantGain(null, null, null, MagnitudeAuthority.UNKNOWN)

        fun empiricallyBounded(mean: Double, lower: Double, upper: Double): PlantGain {
            require(mean > 0.0 && lower > 0.0 && upper >= lower)
            return PlantGain(mean, lower, upper, MagnitudeAuthority.EMPIRICALLY_BOUNDED)
        }
    }
}

data class KStarScientificTrace(
    val authorities: Set<ScientificAuthority>,
    val evidenceIds: Set<String>,
    val petrolOnGasPhysicalEvidenceId: String?,
    val petrolReferencePhysicalEvidenceId: String?,
    val provenance: Set<String>,
)

data class KStarEstimate(
    val logError: Double,
    val currentTheta: Double,
    val targetTheta: Double?,
    val targetFactor: Double?,
    val gain: PlantGain,
    val authority: MagnitudeAuthority,
    val abstained: Boolean,
    val reason: String,
    val scientificTrace: KStarScientificTrace,
)

object KStarEstimator : TargetEstimator {
    fun estimate(input: KStarScientificInput): KStarEstimate {
        val petrolOnGas = input.petrolOnGas
        val petrolReference = input.petrolReference
        val error = ln(petrolOnGas.valueMs / petrolReference.valueMs)
        val theta = ln(input.currentFactor)
        val trace = KStarScientificTrace(
            authorities = petrolOnGas.evidence.authorities + petrolReference.evidence.authorities,
            evidenceIds = petrolOnGas.evidence.evidenceIds + petrolReference.evidence.evidenceIds,
            petrolOnGasPhysicalEvidenceId = petrolOnGas.evidence.physicalEvidenceId,
            petrolReferencePhysicalEvidenceId = petrolReference.evidence.physicalEvidenceId,
            provenance = petrolOnGas.evidence.provenance + petrolReference.evidence.provenance,
        )

        fun abstain(reason: String): KStarEstimate = KStarEstimate(
            logError = error,
            currentTheta = theta,
            targetTheta = null,
            targetFactor = null,
            gain = input.gain,
            authority = MagnitudeAuthority.UNKNOWN,
            abstained = true,
            reason = reason,
            scientificTrace = trace,
        )

        if (
            petrolOnGas.evidence.role == ScientificEvidenceRole.PREDICTION ||
            petrolReference.evidence.role == ScientificEvidenceRole.PREDICTION
        ) {
            return abstain("PREDICTION_IS_NOT_OBSERVATION")
        }

        if (
            petrolOnGas.evidence.effectiveWeight <= 0.0 ||
            petrolReference.evidence.effectiveWeight <= 0.0
        ) {
            return abstain("NO_SCIENTIFIC_WEIGHT")
        }

        val onGasPhysicalId = petrolOnGas.evidence.physicalEvidenceId
        val referencePhysicalId = petrolReference.evidence.physicalEvidenceId
        if (onGasPhysicalId != null && onGasPhysicalId == referencePhysicalId) {
            return abstain("SELF_COMPARISON_EVIDENCE")
        }

        val g = input.gain.mean
        if (g == null || !g.isFinite() || g <= 0.0) {
            return abstain("PLANT_GAIN_UNKNOWN")
        }

        val targetTheta = theta + error / g
        return KStarEstimate(
            logError = error,
            currentTheta = theta,
            targetTheta = targetTheta,
            targetFactor = exp(targetTheta),
            gain = input.gain,
            authority = input.gain.authority,
            abstained = false,
            reason = "GAIN_SUPPORTED",
            scientificTrace = trace,
        )
    }
}

object PhysicsModelContract {
    val BILINEAR_AUTHORITY: PhysicsModelAuthority = PhysicsModelAuthority.LOCAL_MODEL
}
