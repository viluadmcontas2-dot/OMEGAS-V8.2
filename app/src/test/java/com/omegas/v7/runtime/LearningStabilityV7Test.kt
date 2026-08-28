package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStabilityV7Test {
    private val revision = CalibrationRevisionV7(0, 0)

    @Test
    fun `visitas coerentes consolidam e um outlier isolado apenas revalida`() {
        val baseline = (0 until 8).map { index -> comparison(index, targetMs = 4.0, observedMs = 4.5) }
        val consolidated = LearningStabilityV7.mapCell(baseline, row = 4, column = 2)
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, consolidated.state)
        assertEquals(0, consolidated.generation)
        assertEquals(12.5, consolidated.consolidatedErrorPercent ?: Double.NaN, 0.000001)

        val withOutlier = baseline + comparison(20, targetMs = 4.4, observedMs = 4.5)
        val revalidating = LearningStabilityV7.mapCell(withOutlier, row = 4, column = 2)
        assertEquals(LearningStabilityStateV7.REVALIDATING, revalidating.state)
        assertEquals(12.5, revalidating.consolidatedErrorPercent ?: Double.NaN, 0.000001)
        assertEquals(1, revalidating.recentUniqueVisits)
        assertNotNull(revalidating.recentErrorPercent)
    }

    @Test
    fun `mudanca repetivel promove novo consolidado em nova geracao`() {
        val baseline = (0 until 8).map { index -> comparison(index, targetMs = 4.0, observedMs = 4.5) }
        val changed = (20 until 28).map { index -> comparison(index, targetMs = 4.4, observedMs = 4.5) }
        val result = LearningStabilityV7.mapCell(baseline + changed, row = 4, column = 2)

        assertEquals(LearningStabilityStateV7.CONSOLIDATED, result.state)
        assertEquals(1, result.generation)
        assertEquals((4.5 - 4.4) / 4.4 * 100.0, result.consolidatedErrorPercent ?: Double.NaN, 0.000001)
        assertEquals(0, result.recentUniqueVisits)
    }

    @Test
    fun `mesma evidencia em ordem diferente produz o mesmo consolidado`() {
        val evidence = (0 until 8).map { comparison(it, 4.0, 4.5) } +
            (20 until 23).map { comparison(it, 4.4, 4.5) }
        val ordered = LearningStabilityV7.mapCell(evidence, 4, 2)
        val shuffled = LearningStabilityV7.mapCell(evidence.shuffled(java.util.Random(42)), 4, 2)

        assertEquals(ordered.state, shuffled.state)
        assertEquals(ordered.generation, shuffled.generation)
        assertEquals(ordered.consolidatedErrorPercent, shuffled.consolidatedErrorPercent)
        assertEquals(ordered.recentErrorPercent, shuffled.recentErrorPercent)
        assertEquals(ordered.consolidatedUniqueVisits, shuffled.consolidatedUniqueVisits)
        assertEquals(ordered.recentUniqueVisits, shuffled.recentUniqueVisits)
    }

    @Test
    fun `peso bilinear nao transforma uma visita em quatro visitas independentes`() {
        val comparison = FuelComparisonV7(
            id = "blend",
            revision = revision,
            cngVisitId = "blend-visit",
            petrolEvidenceIds = listOf("p"),
            rpm = 1_600.0,
            mapBar = 0.55,
            waterC = 82.0,
            petrolTargetMs = 4.0,
            petrolOnCngMs = 4.0,
            differenceMs = 0.0,
            errorPercent = 0.0,
            direction = "EQUIVALENT",
            quality = 1.0,
            createdAtMs = 1000L,
        )
        val totalEffective = (0 until CalibrationShapeV7.MAP_K_EDITABLE_ROWS).sumOf { row ->
            (0 until CalibrationShapeV7.MAP_K_COLUMNS).sumOf { column ->
                LearningStabilityV7.mapCell(listOf(comparison), row, column).recentEffectiveVisits
            }
        }
        assertEquals(1.0, totalEffective, 0.000001)
    }

    @Test
    fun `mais de seiscentas visitas nao fazem a memoria consolidada depender da fila bruta`() {
        val evidence = (0 until 620).map { index ->
            comparison(index, targetMs = 4.0, observedMs = 4.5)
        }
        val result = LearningStabilityV7.mapCell(evidence, 4, 2)

        assertEquals(LearningStabilityStateV7.CONSOLIDATED, result.state)
        assertEquals(12.5, result.consolidatedErrorPercent ?: Double.NaN, 0.000001)
        assertEquals(620, result.consolidatedUniqueVisits)
        assertTrue(result.consolidatedEffectiveVisits >= 600.0)
    }

    @Test
    fun `cobertura global distingue condicao localizada de tendencia abrangente`() {
        val localized = (0 until 8).map { index ->
            comparison(index, 4.0, 4.5, rpm = 1_850.0, mapBar = 0.50)
        }
        val localizedState = LearningStabilityV7.curvePoint(localized, indexFor4ms())
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, localizedState.state)
        assertEquals(1, localizedState.rpmBandCount)
        assertEquals(1, localizedState.mapBandCount)

        val broad = (0 until 8).map { index ->
            comparison(
                index,
                4.0,
                4.5,
                rpm = if (index % 2 == 0) 1_850.0 else 2_500.0,
                mapBar = if (index % 3 == 0) 0.45 else 0.60,
            )
        }
        val broadState = LearningStabilityV7.curvePoint(broad, indexFor4ms())
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, broadState.state)
        assertTrue(broadState.rpmBandCount >= 2)
        assertTrue(broadState.mapBandCount >= 2)
    }

    private fun indexFor4ms(): Int {
        val axis = com.omegas.prohub.ecu.KFactorProtocol.OBSERVED_PETROL_AXIS_MS
        val index = axis.indexOfFirst { kotlin.math.abs(it - 4.0) < 0.000001 }
        require(index >= 0) { "Eixo K não possui ponto de 4 ms para o teste" }
        return index
    }

    private fun comparison(
        index: Int,
        targetMs: Double,
        observedMs: Double,
        rpm: Double = 1_850.0,
        mapBar: Double = 0.50,
    ): FuelComparisonV7 {
        val difference = observedMs - targetMs
        val error = difference / targetMs * 100.0
        return FuelComparisonV7(
            id = "cmp-$index",
            revision = revision,
            cngVisitId = "visit-$index",
            petrolEvidenceIds = listOf("petrol-$index"),
            rpm = rpm,
            mapBar = mapBar,
            waterC = 82.0,
            petrolTargetMs = targetMs,
            petrolOnCngMs = observedMs,
            differenceMs = difference,
            errorPercent = error,
            direction = if (kotlin.math.abs(error) <= 2.5) "EQUIVALENT" else if (error > 0) "INCREASE_CNG_DELIVERY" else "DECREASE_CNG_DELIVERY",
            quality = 1.0,
            createdAtMs = 1_000L + index * 100L,
        )
    }
}
