package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorG4bSoftwareGateTest {
    private val anchorSweep = listOf(0, 1, 2, 3, 4, 6, 8)
    private val sessions = listOf("S0", "S1", "S2")
    private val epochs = listOf("E0", "E1", "E2")
    private val hiddenCells = listOf(0 to 0, 0 to 3, 3 to 0, 3 to 3, 1 to 2)

    @Test
    fun `nested LOSO LOEO freezes training-only threshold across 0 1 2 3 4 6 8 anchors and hidden cells`() {
        val dataset = syntheticDataset()
        var predictedHoldoutCount = 0

        anchorSweep.forEach { anchorCount ->
            sessions.forEach { heldOutSession ->
                hiddenCells.forEach { hiddenCell ->
                    val train = dataset.filter {
                        it.sessionId != heldOutSession && (it.row to it.column) != hiddenCell
                    }
                    val holdout = dataset.filter {
                        it.sessionId == heldOutSession && (it.row to it.column) == hiddenCell
                    }
                    val poisonedHoldout = holdout.map { it.copy(kStar = it.kStar * 1.70) }
                    val baselineFold = evaluateOuterFold(train, holdout, anchorCount)
                    val poisonedFold = evaluateOuterFold(train, poisonedHoldout, anchorCount)

                    // The outer labels change materially, but the training-only threshold cannot move.
                    assertEquals(baselineFold.cutoff, poisonedFold.cutoff, 0.0)
                    assertEquals(holdout.size, poisonedHoldout.size)
                    assertTrue(train.none { it.sessionId == heldOutSession })
                    assertTrue(train.none { (it.row to it.column) == hiddenCell })

                    assertEquals(anchorCount.coerceAtMost(15), baselineFold.anchors.size)
                    val forecasts = baselineFold.forecasts
                    if (anchorCount < 3) {
                        assertTrue(forecasts.isEmpty())
                        assertTrue(poisonedFold.forecasts.isEmpty())
                    } else {
                        assertEquals(holdout.size, forecasts.size)
                        assertTrue(forecasts.all { it.absoluteLogError < 0.05 })
                        assertTrue(poisonedFold.forecasts.zip(forecasts).any { (poisoned, baseline) ->
                            abs(poisoned.absoluteLogError - baseline.absoluteLogError) > 0.10
                        })
                        predictedHoldoutCount += forecasts.size

                        val risk = PredictorRiskCoverageValidator.leaveOneEpoch(
                            outcomes = forecasts.map {
                                PredictorRiskOutcome(
                                    epochId = it.epochId,
                                    diagnosticConfidence = it.confidence,
                                    absoluteLogError = it.absoluteLogError,
                                )
                            },
                            highConfidenceCutoff = baselineFold.cutoff,
                        )
                        assertEquals(baselineFold.cutoff, risk.highConfidenceCutoff, 0.0)
                        assertEquals(3, risk.epochs.size)
                    }
                }
            }
        }
        assertEquals(180, predictedHoldoutCount)
    }

    @Test
    fun `synthetic E2E keeps draft prediction non-evidence and adapts only after six real post-write frames`() {
        val calibration = LearningCalibrationBinding(
            calibrationFingerprint = "cal-A",
            calibrationGeneration = 7,
            geometryFingerprint = "geom-A",
            usbSessionId = 42L,
            mapHash = "map-A",
            petrolAxisMs = List(12) { 1.0 + it * 0.2 },
            rpmAxis = List(12) { 900 + it * 250 },
        )
        val revisions = PredictorSourceRevisions(
            mapRevision = 11L,
            curveRevision = 12L,
            evidenceRevision = 13L,
            referenceRevision = 14L,
            physicsRevision = 15L,
        )
        val observation = PredictorObservation(
            cell = PredictorCell(row = 2, column = 4),
            kStar = 105.0,
            currentK = 100,
            uncertaintyPercent = 2.0,
            support = 0.95,
            knownness = PredictorKnownness.KNOWN,
            operatingPoint = PredictorOperatingPoint(
                rpm = 1900.0,
                petrolInjectionMs = 1.8,
                mapBar = 0.62,
                waterTemperatureC = 88.0,
                gasTemperatureC = 34.0,
                effectiveMass = 1.0,
                effectiveCapacity = 1.0,
            ),
            stamp = PredictorEvidenceStamp(
                calibrationFingerprint = "cal-A",
                calibrationGeneration = 7,
                geometryFingerprint = "geom-A",
                mapHash = "map-A",
                curveHash = "curve-A",
                sourceRevisions = revisions,
                epoch = 3,
                sessionId = "session-A",
                freshness = PredictorSourceFreshness.CURRENT,
            ),
            provenance = "kstar-direct-1",
            magnitudeAuthority = MagnitudeAuthority.PHYSICALLY_ANCHORED,
            evidenceRefs = listOf("ref-1", "kstar-direct-1"),
        )
        val ledger = PredictorScientificSupportLedger()
        assertTrue(ledger.ingest(PredictorScientificIngressRecord("kstar-direct-1", PredictorScientificSourceType.DIRECT_OBSERVATION)).acceptedAsEvidence)

        val predictor = PredictorContract.evaluate(
            PredictorInputSnapshot(
                calibration = calibration,
                curveHash = "curve-A",
                sourceRevisions = revisions,
                epoch = 3,
                sessionId = "session-A",
                observations = listOf(observation),
            ),
        )
        assertEquals(PredictorSnapshotState.READY, predictor.state)
        val target = predictor.candidates.single()
        assertEquals(105.0, target.estimateK, 0.0)
        assertTrue(target.industrialIdealAuthorityEligible())
        val humanState = PredictorHumanStateProjector.project(
            PredictorHumanProjectionInput(
                currentK = observation.currentK,
                targetEstimateK = target.estimateK,
                targetRange = target.range,
                authority = target.authority,
                scientificState = PredictorHumanScientificState.DIRECT_CONFIRMED,
                riskState = PredictorHumanRiskState.CALIBRATED_ACTIONABLE,
                confidence = 0.90,
                reasonCode = "SYNTHETIC_G4B_RISK_GATE",
            ),
        )
        assertEquals(PredictorHumanActionState.ACTIONABLE, humanState.actionState)

        // Predictor/Draft exist, but neither is allowed to become scientific evidence.
        ledger.ingest(PredictorScientificIngressRecord("prediction-1", PredictorScientificSourceType.PREDICTION))
        ledger.ingest(PredictorScientificIngressRecord("draft-1", PredictorScientificSourceType.DRAFT))
        assertEquals(1, ledger.snapshot().evidenceCount)

        var writerCalls = 0
        var suggestion = PredictorSuggestionRecord("suggestion-1", predictor.revisionToken)
        suggestion = PredictorSuggestionLifecycle.reduce(suggestion, PredictorSuggestionEvent.PROMOTE_REVIEWABLE)
        assertEquals(PredictorHumanActionState.ACTIONABLE, humanState.actionState)
        suggestion = PredictorSuggestionLifecycle.reduce(suggestion, PredictorSuggestionEvent.PROMOTE_ACTIONABLE)
        suggestion = PredictorSuggestionLifecycle.reduce(suggestion, PredictorSuggestionEvent.HUMAN_ACCEPT)
        assertEquals(0, writerCalls)
        assertEquals(PredictorSuggestionState.ACCEPTED, suggestion.state)

        // Explicit fake writer boundary: only here is a mutation acknowledged.
        writerCalls += 1
        suggestion = PredictorSuggestionLifecycle.reduce(suggestion, PredictorSuggestionEvent.MUTATION_CONFIRMED)
        assertEquals(PredictorSuggestionState.APPLIED, suggestion.state)
        assertNotEquals(PredictorSuggestionState.IMPROVED, suggestion.state)
        suggestion = PredictorSuggestionLifecycle.reduce(suggestion, PredictorSuggestionEvent.BEGIN_REVALIDATION)
        assertEquals(PredictorSuggestionState.REVALIDATING, suggestion.state)

        (1..4).forEach { index ->
            ledger.ingest(PredictorScientificIngressRecord("after-$index", PredictorScientificSourceType.POST_WRITE_OUTCOME))
        }
        val prior = PredictorSensitivityPosterior(gMean = 1.0, gVariance = 0.25, modelErrorVariance = 0.01)
        val provisional = PredictorRevalidation.evaluate(
            revalidationInput(
                frames = 4,
                state = suggestion.state,
                prior = prior,
                beforeError = 0.06,
                afterError = 0.02,
            ),
        )
        assertEquals(PredictorRevalidationEvidenceState.DIRECT_PROVISIONAL, provisional.evidenceState)
        assertFalse(provisional.adaptationAllowed)
        assertEquals(PredictorSuggestionState.REVALIDATING, provisional.lifecycleState)

        (5..6).forEach { index ->
            ledger.ingest(PredictorScientificIngressRecord("after-$index", PredictorScientificSourceType.POST_WRITE_OUTCOME))
        }
        val confirmed = PredictorRevalidation.evaluate(
            revalidationInput(
                frames = 6,
                state = suggestion.state,
                prior = prior,
                beforeError = 0.06,
                afterError = 0.02,
            ),
        )
        assertEquals(PredictorRevalidationEvidenceState.DIRECT_CONFIRMED, confirmed.evidenceState)
        assertEquals(PredictorSuggestionState.IMPROVED, confirmed.lifecycleState)
        assertTrue(confirmed.adaptationAllowed)
        assertNotNull(confirmed.sensitivityResult)
        val sensitivity = requireNotNull(confirmed.sensitivityResult)
        assertTrue(sensitivity.accepted)
        assertNotEquals(prior.gMean, sensitivity.posterior.gMean, 1e-12)
        assertFalse(confirmed.modelDowngraded)

        val support = ledger.snapshot()
        assertEquals(7, support.evidenceCount)
        assertEquals(1, support.anchorCount)
        assertEquals(7, support.currentVisitCount)
        assertEquals(7, support.supportCount)
        assertEquals(1, writerCalls)
    }

    private fun revalidationInput(
        frames: Int,
        state: PredictorSuggestionState,
        prior: PredictorSensitivityPosterior,
        beforeError: Double,
        afterError: Double,
    ): PredictorRevalidationInput = PredictorRevalidationInput(
        suggestionState = state,
        referenceStrong = true,
        contextComparable = true,
        afterSourceType = PredictorScientificSourceType.POST_WRITE_OUTCOME,
        afterFrameCount = frames,
        beforeError = beforeError,
        afterError = afterError,
        zeroBand = 0.005,
        noChangeTolerance = 0.002,
        sensitivityInput = PredictorSensitivityInput(
            sameIdentity = true,
            contextComparable = true,
            beforeError = beforeError,
            afterError = afterError,
            beforeFactor = 1.0,
            afterFactor = 1.05,
            measurementVariance = 0.0004,
            processVariance = 0.001,
            predictedAfterError = afterError,
            prior = prior,
            provenance = listOf("fake-writer-1", "after-$frames"),
        ),
    )

    private fun evaluateOuterFold(
        train: List<SyntheticPoint>,
        holdout: List<SyntheticPoint>,
        anchorCount: Int,
    ): OuterFoldResult {
        val cutoff = chooseActionabilityCutoffInnerLoeo(train, anchorCount)
        val anchors = selectAnchors(train, anchorCount)
        return OuterFoldResult(
            cutoff = cutoff,
            anchors = anchors,
            forecasts = holdout.mapNotNull { predict(it, anchors) },
        )
    }

    private fun chooseActionabilityCutoffInnerLoeo(
        train: List<SyntheticPoint>,
        anchorCount: Int,
    ): Double {
        if (anchorCount < 3) return 0.75
        val candidates = listOf(0.35, 0.45, 0.55, 0.65, 0.75)
        val innerForecasts = epochs.flatMap { heldOutEpoch ->
            val innerTrain = train.filter { it.epochId != heldOutEpoch }
            val innerValidation = train.filter { it.epochId == heldOutEpoch }
            val anchors = selectAnchors(innerTrain, anchorCount)
            innerValidation.mapNotNull { predict(it, anchors) }
        }
        require(innerForecasts.isNotEmpty())
        return candidates.mapNotNull { cutoff ->
            val high = innerForecasts.filter { it.confidence >= cutoff }
            val coverage = high.size.toDouble() / innerForecasts.size.toDouble()
            if (coverage < 0.20 || high.isEmpty()) return@mapNotNull null
            val mae = high.map { it.absoluteLogError }.average()
            Triple(cutoff, mae + 0.002 * (1.0 - coverage), coverage)
        }.minWithOrNull(compareBy<Triple<Double, Double, Double>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
            ?.first ?: 0.75
    }

    private fun selectAnchors(
        train: List<SyntheticPoint>,
        anchorCount: Int,
    ): List<Anchor> {
        if (anchorCount <= 0) return emptyList()
        val byCell = train.groupBy { it.row to it.column }
        val regions = byCell.map { (cell, points) ->
            val row = cell.first
            val column = cell.second
            val distanceFromCenter = abs(row - 1.5) + abs(column - 1.5)
            val meanDelta = points.map { abs(ln(it.kStar / it.currentK)) }.average()
            PredictorLearningRegion(
                regionId = "$row:$column",
                naturallyEligible = true,
                usage = (0.95 - 0.08 * distanceFromCenter).coerceIn(0.0, 1.0),
                geometricNovelty = (0.40 + 0.12 * distanceFromCenter).coerceIn(0.0, 1.0),
                modelUncertainty = (0.45 + 0.08 * distanceFromCenter).coerceIn(0.0, 1.0),
                referenceQuality = 0.95,
                calibrationFreshness = 0.98,
                independence = 0.90,
                expectedErrorImpact = (0.20 + meanDelta * 20.0).coerceIn(0.0, 1.0),
                acquisitionCost = 1.0,
            )
        }
        return PredictorActiveLearning.rank(regions)
            .take(anchorCount)
            .map { diagnostic ->
                val parts = diagnostic.regionId.split(':')
                val row = parts[0].toInt()
                val column = parts[1].toInt()
                val points = requireNotNull(byCell[row to column])
                Anchor(row, column, points.map { it.kStar }.average())
            }
    }

    private fun predict(point: SyntheticPoint, anchors: List<Anchor>): Forecast? {
        if (anchors.size < 3) return null
        val weighted = anchors.map { anchor ->
            val distance = hypot((point.row - anchor.row) / 3.0, (point.column - anchor.column) / 3.0)
            Triple(1.0 / (1.0 + distance), ln(anchor.kStar / point.currentK), distance)
        }
        val totalWeight = weighted.sumOf { it.first }
        val rawDelta = weighted.sumOf { it.first * it.second } / totalWeight
        val nearestDistance = weighted.minOf { it.third }
        val shrunkDelta = rawDelta / (1.0 + 0.25 * nearestDistance)
        val estimate = point.currentK * exp(shrunkDelta)
        val confidence = ((0.35 + 0.08 * anchors.size).coerceAtMost(0.97) / (1.0 + 0.30 * nearestDistance))
            .coerceIn(0.0, 1.0)
        return Forecast(
            epochId = point.epochId,
            estimateK = estimate,
            confidence = confidence,
            absoluteLogError = abs(ln(point.kStar / estimate)),
        )
    }

    private fun syntheticDataset(): List<SyntheticPoint> = sessions.flatMap { session ->
        epochs.flatMap { epoch ->
            (0 until 4).flatMap { row ->
                (0 until 4).map { column ->
                    val sessionOffset = when (session) {
                        "S0" -> -0.0025
                        "S1" -> 0.0
                        else -> 0.0025
                    }
                    val epochOffset = when (epoch) {
                        "E0" -> -0.0012
                        "E1" -> 0.0
                        else -> 0.0012
                    }
                    val delta = 0.012 * (row - 1.5) + 0.009 * (column - 1.5) + sessionOffset + epochOffset
                    SyntheticPoint(
                        sessionId = session,
                        epochId = epoch,
                        row = row,
                        column = column,
                        currentK = 100.0,
                        kStar = 100.0 * exp(delta),
                    )
                }
            }
        }
    }

    private data class SyntheticPoint(
        val sessionId: String,
        val epochId: String,
        val row: Int,
        val column: Int,
        val currentK: Double,
        val kStar: Double,
    )

    private data class Anchor(val row: Int, val column: Int, val kStar: Double)

    private data class OuterFoldResult(
        val cutoff: Double,
        val anchors: List<Anchor>,
        val forecasts: List<Forecast>,
    )

    private data class Forecast(
        val epochId: String,
        val estimateK: Double,
        val confidence: Double,
        val absoluteLogError: Double,
    )
}
