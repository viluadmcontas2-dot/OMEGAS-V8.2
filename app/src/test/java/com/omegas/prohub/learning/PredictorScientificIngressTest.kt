package com.omegas.prohub.learning

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorScientificIngressTest {
    @Test
    fun `only real observations and real post write outcomes enter scientific evidence`() {
        val accepted = setOf(
            PredictorScientificSourceType.DIRECT_OBSERVATION,
            PredictorScientificSourceType.POST_WRITE_OUTCOME,
        )
        PredictorScientificSourceType.entries.forEach { source ->
            val classification = PredictorScientificIngress.classify(source)
            assertEquals(source in accepted, classification.acceptedAsEvidence)
        }
        assertTrue(PredictorScientificIngress.classify(PredictorScientificSourceType.HISTORICAL_PRIOR).acceptedAsPrior)
        assertFalse(PredictorScientificIngress.classify(PredictorScientificSourceType.HISTORICAL_PRIOR).countsAsCurrentVisit)
    }

    @Test
    fun `prediction suggestion draft ui and predicted cache never add support`() {
        val ledger = PredictorScientificSupportLedger()
        val forbidden = listOf(
            PredictorScientificSourceType.PREDICTION,
            PredictorScientificSourceType.SUGGESTION,
            PredictorScientificSourceType.DRAFT,
            PredictorScientificSourceType.UI,
            PredictorScientificSourceType.PREDICTED_CACHE,
        )
        forbidden.forEachIndexed { index, source ->
            assertFalse(ledger.ingest(PredictorScientificIngressRecord("blocked-$index", source)).acceptedAsEvidence)
        }
        assertEquals(PredictorScientificSupportSnapshot.ZERO, ledger.snapshot())
    }

    @Test
    fun `one million recomputes without new evidence cannot create authority`() {
        val ledger = PredictorScientificSupportLedger()
        assertTrue(ledger.ingest(PredictorScientificIngressRecord("obs-1", PredictorScientificSourceType.DIRECT_OBSERVATION)).acceptedAsEvidence)
        val before = ledger.snapshot()
        repeat(1_000_000) { ledger.onRecompute() }
        assertEquals(before, ledger.snapshot())
    }

    @Test
    fun `restore cannot convert predicted cache into evidence and historical prior is not current visit`() {
        val ledger = PredictorScientificSupportLedger()
        ledger.restore(
            listOf(
                PredictorScientificIngressRecord("prediction", PredictorScientificSourceType.PREDICTION),
                PredictorScientificIngressRecord("cache", PredictorScientificSourceType.PREDICTED_CACHE),
                PredictorScientificIngressRecord("prior", PredictorScientificSourceType.HISTORICAL_PRIOR),
                PredictorScientificIngressRecord("real", PredictorScientificSourceType.DIRECT_OBSERVATION),
            ),
        )
        assertEquals(
            PredictorScientificSupportSnapshot(
                evidenceCount = 1,
                anchorCount = 1,
                currentVisitCount = 1,
                supportCount = 1,
                historicalPriorCount = 1,
            ),
            ledger.snapshot(),
        )
    }

    @Test
    fun `repeating the same real provenance does not manufacture support`() {
        val ledger = PredictorScientificSupportLedger()
        repeat(100_000) {
            ledger.ingest(PredictorScientificIngressRecord("same-observation", PredictorScientificSourceType.DIRECT_OBSERVATION))
        }
        assertEquals(1, ledger.snapshot().evidenceCount)
        assertEquals(1, ledger.snapshot().supportCount)
        assertEquals(1, ledger.snapshot().currentVisitCount)
    }

    @Test
    fun `source injection fuzz never promotes forbidden source`() {
        val random = Random(162_2026)
        val values = PredictorScientificSourceType.entries
        val ledger = PredictorScientificSupportLedger()
        var expectedEvidence = 0
        var expectedPriors = 0
        repeat(20_000) { index ->
            val source = values[random.nextInt(values.size)]
            val result = ledger.ingest(PredictorScientificIngressRecord("id-$index", source))
            when (source) {
                PredictorScientificSourceType.DIRECT_OBSERVATION,
                PredictorScientificSourceType.POST_WRITE_OUTCOME,
                -> {
                    assertTrue(result.acceptedAsEvidence)
                    expectedEvidence += 1
                }
                PredictorScientificSourceType.HISTORICAL_PRIOR -> {
                    assertFalse(result.acceptedAsEvidence)
                    assertTrue(result.acceptedAsPrior)
                    expectedPriors += 1
                }
                else -> {
                    assertFalse(result.acceptedAsEvidence)
                    assertFalse(result.acceptedAsPrior)
                }
            }
        }
        assertEquals(expectedEvidence, ledger.snapshot().evidenceCount)
        assertEquals(expectedEvidence, ledger.snapshot().supportCount)
        assertEquals(expectedPriors, ledger.snapshot().historicalPriorCount)
    }
}
