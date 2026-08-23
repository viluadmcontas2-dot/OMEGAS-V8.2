package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationPhysicsFoundationTest {
    @Test fun evidenceMatrixCoversEveryPhysicalInputAndPreservesUnknowns() {
        val ids = PhysicsEvidenceMatrix.rows.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf(
            PhysicsEvidenceId.K1_MAP,
            PhysicsEvidenceId.MUL_ACT,
            PhysicsEvidenceId.K2_PRESSURE,
            PhysicsEvidenceId.K3_ECU_SIDE,
            PhysicsEvidenceId.K4_GAS_TEMP,
            PhysicsEvidenceId.GAS_DEADTIME,
            PhysicsEvidenceId.RPM,
            PhysicsEvidenceId.MAP,
            PhysicsEvidenceId.WATER_TEMP,
            PhysicsEvidenceId.GAS_TEMP,
            PhysicsEvidenceId.PRESSURE,
        )))
        assertEquals(PhysicsKnownness.UNKNOWN, PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K3_ECU_SIDE).knownness)
        assertEquals(PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K2_PRESSURE).authority)
        assertEquals(PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, PhysicsEvidenceMatrix.require(PhysicsEvidenceId.K4_GAS_TEMP).authority)
    }

    @Test fun deadtimeUnknownNeverBecomesZero() {
        val unknown = GasPulsePhysics.activePulse(
            gasPulseMs = 5.20,
            deadtime = DeadtimeEvidence.unknown("no-current-session-deadtime"),
        )
        assertNull(unknown.activePulseMs)
        assertEquals(MagnitudeAuthority.UNKNOWN, unknown.authority)
        assertTrue(unknown.reason.contains("UNKNOWN"))

        val known = GasPulsePhysics.activePulse(
            gasPulseMs = 5.20,
            deadtime = DeadtimeEvidence.known(1.10, "current-session-readback"),
        )
        assertEquals(4.10, known.activePulseMs!!, 1e-9)
        assertEquals(MagnitudeAuthority.PHYSICALLY_ANCHORED, known.authority)
    }

    @Test fun k1IsAComponentNotATotalGasEquation() {
        assertEquals(1.0, PhysicsFactors.k1FromMapRaw(128), 1e-12)
        assertEquals(EffectDirection.NEUTRAL, PhysicsFactors.k1Direction(128))
        assertEquals(EffectDirection.INCREASE, PhysicsFactors.k1Direction(160))
        assertEquals(EffectDirection.DECREASE, PhysicsFactors.k1Direction(96))
        assertFalse(PhysicsFactors.K1_IS_TOTAL_GAS_MODEL)
    }

    @Test fun k2AndK4RemainStaticCandidatesAndK3RemainsUnknown() {
        val k2 = PhysicsFactors.k2StaticCandidate(raw = 8192)
        assertEquals(1.0, k2.value!!, 1e-12)
        assertEquals(PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, k2.authority)
        assertNull(k2.sessionId)
        assertNull(k2.capturedAtMs)

        val k4 = PhysicsFactors.k4StaticCandidate(raw = 32768)
        assertEquals(1.0, k4.value!!, 1e-12)
        assertEquals(PhysicsEvidenceAuthority.STATIC_ORACLE_CANDIDATE, k4.authority)
        assertNull(k4.sessionId)
        assertNull(k4.capturedAtMs)

        val k3 = PhysicsFactors.k3Unknown()
        assertNull(k3.value)
        assertEquals(PhysicsKnownness.UNKNOWN, k3.knownness)
    }

    @Test fun configuredFactorIsNotPromotedToLiveEffectiveFactor() {
        val configured = FactorEvidence.configured("K2_TABLE", 1.08, "ecu-config-read")
        assertEquals(FactorRole.CONFIGURED, configured.role)
        assertFalse(configured.isLiveEffective())
        val unknownLive = configured.asUnknownLive("ecu-side-generation-not-proven")
        assertNull(unknownLive.value)
        assertEquals(FactorRole.LIVE_EFFECTIVE, unknownLive.role)
        assertEquals(PhysicsKnownness.UNKNOWN, unknownLive.knownness)
    }

    @Test fun calibrationPhysicsContextPreservesEnvironmentalMicrostate() {
        val identity = CalibrationIdentityRef(
            functionFingerprint = "fn-a",
            geometryFingerprint = "geo-a",
            mapHash = "map-a",
            curveFingerprint = "curve-a",
            usbSessionId = 7L,
            generation = 2,
            provenance = "FULL_ECU_READ",
        )
        val map = FactorEvidence.liveEffective("M_eff", 1.05, "map-readback", 7L, 1000L)
        val curve = FactorEvidence.liveEffective("C_eff", 0.98, "curve-readback", 7L, 1000L)
        val first = CalibrationPhysicsContext.create(
            identity = identity,
            microState = ContextSlice(2200.0, 0.55, 0.9, 34.0, 86.0, 1000L),
            deadtime = DeadtimeEvidence.known(1.0, "deadtime-readback"),
            mapEffective = map,
            curveEffective = curve,
            uncertainty = 0.03,
        )
        val second = first.copy(microState = first.microState.copy(pressureDeltaBar = 1.4, gasTempC = 49.0))
        assertTrue(first != second)
        assertEquals(1.05, first.mEff!!, 1e-12)
        assertEquals(0.98, first.cEff!!, 1e-12)
        assertEquals(1.05 * 0.98, first.fCurrent!!, 1e-12)
    }

    @Test fun policyFractionMovesFromCurrentTowardIdealAndCannotMasqueradeAsTarget() {
        val policy = LegacyAdvisorStepPolicy()
        assertEquals(MagnitudeAuthority.POLICY_ONLY, policy.authority)
        assertEquals(0.45, policy.minimumFraction, 1e-12)
        assertEquals(0.90, policy.maximumFraction, 1e-12)

        val current = 1.20
        val target = IdealTarget(factor = 1.05, authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED)
        val step = policy.selectStep(currentFactor = current, target = target, uncertainty = 0.20)
        assertTrue(step.factor < current)
        assertTrue(step.factor > target.factor)
        assertTrue(step.factor != target.factor)
        assertEquals(MagnitudeAuthority.POLICY_ONLY, step.authority)
    }

    @Test fun targetEstimatorAllocatorAndStepPolicyAreSeparateAuthorities() {
        assertTrue(TargetEstimator::class != ActuatorAllocator::class)
        assertTrue(ActuatorAllocator::class != StepPolicy::class)
        assertTrue(TargetEstimator::class != StepPolicy::class)
    }

    @Test fun expectedEffectCanKnowDirectionWhileMagnitudeIsUnknown() {
        val effect = ExpectedEffect(
            direction = EffectDirection.INCREASE,
            lowerBound = null,
            upperBound = null,
            assumptions = listOf("deadtime unresolved"),
            authority = MagnitudeAuthority.UNKNOWN,
            falsifier = "validated deadtime reverses direction",
        )
        assertEquals(EffectDirection.INCREASE, effect.direction)
        assertNull(effect.lowerBound)
        assertEquals(MagnitudeAuthority.UNKNOWN, effect.authority)
    }

    @Test fun correctionMechanismNeverDefaultsToKWithoutEvidencePath() {
        val decision = CorrectionDecision.inconclusive("insufficient-locality")
        assertEquals(CorrectionMechanism.UNKNOWN, decision.mechanism)
        assertNull(decision.target)
        assertTrue(decision.evidencePath.isNotEmpty())
    }

    @Test fun typedKStarReproducesCurrentNumericTarget() {
        val estimate = KStarEstimator.estimate(kStarInput())
        assertEquals(1.1, estimate.targetFactor!!, 1e-12)
        assertFalse(estimate.abstained)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, estimate.authority)
    }

    @Test fun producerLabelNeverChangesKStarMathematics() {
        val targets = ScientificAuthority.values().map { authority ->
            KStarEstimator.estimate(kStarInput(authority = authority)).targetFactor
        }
        assertEquals(1, targets.toSet().size)
    }

    @Test fun adaptivePredictionCannotMasqueradeAsObservation() {
        val estimate = KStarEstimator.estimate(
            kStarInput(
                authority = ScientificAuthority.ADAPTIVE_SHADOW,
                role = ScientificEvidenceRole.PREDICTION,
                petrolOnGasPhysicalId = null,
                petrolReferencePhysicalId = null,
            ),
        )
        assertTrue(estimate.abstained)
        assertNull(estimate.targetFactor)
        assertEquals(MagnitudeAuthority.UNKNOWN, estimate.authority)
        assertEquals("PREDICTION_IS_NOT_OBSERVATION", estimate.reason)
    }

    @Test fun selfComparisonCannotCreateATarget() {
        val estimate = KStarEstimator.estimate(
            kStarInput(
                petrolOnGasPhysicalId = "same-frame",
                petrolReferencePhysicalId = "same-frame",
            ),
        )
        assertTrue(estimate.abstained)
        assertNull(estimate.targetFactor)
        assertEquals("SELF_COMPARISON_EVIDENCE", estimate.reason)
    }

    @Test fun zeroScientificWeightCannotCreateATarget() {
        val estimate = KStarEstimator.estimate(kStarInput(weight = 0.0))
        assertTrue(estimate.abstained)
        assertNull(estimate.targetFactor)
        assertEquals("NO_SCIENTIFIC_WEIGHT", estimate.reason)
    }

    @Test fun unknownGainStillAbstainsAndKeepsScientificTrace() {
        val estimate = KStarEstimator.estimate(kStarInput(gain = PlantGain.unknown()))
        assertNull(estimate.targetFactor)
        assertEquals(MagnitudeAuthority.UNKNOWN, estimate.authority)
        assertTrue(estimate.abstained)
        assertEquals("PLANT_GAIN_UNKNOWN", estimate.reason)
        assertEquals(
            setOf(ScientificAuthority.CLASSIC_ASSISTED),
            estimate.scientificTrace.authorities,
        )
        assertEquals("cng-frame", estimate.scientificTrace.petrolOnGasPhysicalEvidenceId)
        assertEquals("gas-frame", estimate.scientificTrace.petrolReferencePhysicalEvidenceId)
    }

    @Test fun publicKStarApiHasNoRawDoubleBypass() {
        val estimates = KStarEstimator::class.java.methods.filter { it.name == "estimate" }
        assertEquals(1, estimates.size)
        assertEquals(
            listOf(KStarScientificInput::class.java),
            estimates.single().parameterTypes.toList(),
        )
    }

    @Test fun bilinearProjectionIsExplicitlyALocalModel() {
        assertEquals(PhysicsModelAuthority.LOCAL_MODEL, PhysicsModelContract.BILINEAR_AUTHORITY)
        assertFalse(PhysicsModelContract.BILINEAR_AUTHORITY == PhysicsModelAuthority.ECU_INTERPOLATOR)
    }

    private fun evidence(
        authority: ScientificAuthority,
        role: ScientificEvidenceRole,
        evidenceId: String,
        physicalId: String?,
        weight: Double = 1.0,
    ): ResolvedScientificEvidence = ResolvedScientificEvidence(
        authorities = setOf(authority),
        role = role,
        evidenceIds = setOf(evidenceId),
        physicalEvidenceId = physicalId,
        effectiveWeight = weight,
        provenance = setOf("test-provenance"),
    )

    private fun kStarInput(
        authority: ScientificAuthority = ScientificAuthority.CLASSIC_ASSISTED,
        role: ScientificEvidenceRole = ScientificEvidenceRole.OBSERVATION,
        petrolOnGasPhysicalId: String? = "cng-frame",
        petrolReferencePhysicalId: String? = "gas-frame",
        weight: Double = 1.0,
        gain: PlantGain = PlantGain.empiricallyBounded(mean = 1.0, lower = 0.8, upper = 1.2),
    ): KStarScientificInput = KStarScientificInput(
        petrolOnGas = ScientificMeasurement(
            valueMs = 4.4,
            evidence = evidence(authority, role, "cng-evidence", petrolOnGasPhysicalId, weight),
        ),
        petrolReference = ScientificMeasurement(
            valueMs = 4.0,
            evidence = evidence(authority, role, "gas-evidence", petrolReferencePhysicalId, weight),
        ),
        currentFactor = 1.0,
        gain = gain,
    )
}
