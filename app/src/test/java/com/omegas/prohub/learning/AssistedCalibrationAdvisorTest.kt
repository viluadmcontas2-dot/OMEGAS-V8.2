package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistedCalibrationAdvisorTest {
    @Test
    fun `sugestoes usam somente comparacoes da epoca atual`() {
        val comparisons = JSONArray()
            .put(comparison("old-1", 5.0, 6.0, 2_000.0, 0.45, 5, 4).put("epoch", 1))
            .put(comparison("old-2", 5.0, 6.0, 2_200.0, 0.45, 5, 4).put("epoch", 1))
            .put(comparison("current", 5.0, 4.5, 2_400.0, 0.45, 5, 4).put("epoch", 2))

        val result = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("epoch", 2).put("comparisons", comparisons),
        )

        assertEquals(1, result.getInt("comparisonCount"))
        assertEquals("DECREASE_CNG_DELIVERY", pointAt(result, 5.0).getString("direction"))
    }

    @Test
    fun `curvas e propostas permanecem exclusivamente manuais`() {
        val result = analyze(
            comparisons = buildComparisons(
                count = 12,
                targetMs = 5.0,
                observedMs = 5.5,
                row = 5,
                column = 4,
            ),
        )

        assertEquals("CONTINUOUS_ADAPTIVE_MANUAL", result.getString("mode"))
        assertFalse(result.getBoolean("automatic"))
        assertTrue(result.getBoolean("humanConfirmationRequired"))
        assertEquals(18, result.getJSONArray("petrolCurve").length())
        assertEquals(18, result.getJSONArray("cngCurve").length())
        assertEquals(30, result.getJSONArray("kFactorAxisMs").length())
        assertEquals(22.0, result.getJSONArray("kFactorAxisMs").getDouble(29), 0.000001)

        val point = pointAt(result, 5.0)
        assertEquals(10.0, point.getDouble("errorPercent"), 0.000001)
        assertTrue(point.getBoolean("actionable"))
        assertEquals("AVAILABLE", point.getString("readiness"))
        assertEquals("INCREASE_CNG_DELIVERY", point.getString("direction"))
        assertFalse(point.getBoolean("automatic"))
    }

    @Test
    fun `correcao consistente nao fica limitada a cinco por cento`() {
        val result = analyze(
            comparisons = buildComparisons(
                count = 18,
                targetMs = 5.0,
                observedMs = 5.6,
                row = 5,
                column = 4,
            ),
        )

        val point = pointAt(result, 5.0)
        val suggestion = point.getDouble("suggestedDeltaPercent")
        assertTrue("A proposta deve atacar parte relevante do erro", suggestion > 5.0)
        assertTrue("A proposta não deve ultrapassar o erro observado", suggestion < 12.0)
        assertTrue(point.getDouble("estimatedResidualAfterPercent") < 7.0)
    }

    @Test
    fun `uma unica amostra com sinal muito forte pode gerar proposta`() {
        val comparisons = JSONArray().put(comparison(
            id = "single-strong",
            targetMs = 5.0,
            observedMs = 6.0,
            rpm = 2_200.0,
            mapBar = 0.50,
            row = 5,
            column = 4,
        ))

        val point = pointAt(analyze(comparisons), 5.0)
        assertEquals(1, point.getInt("uniqueVisits"))
        assertTrue(point.getBoolean("actionable"))
        assertEquals("AVAILABLE", point.getString("readiness"))
        assertTrue(point.getDouble("uncertaintyPercent") < point.getDouble("errorPercent"))
    }

    @Test
    fun `erro pequeno coberto pela incerteza continua apenas observado`() {
        val comparisons = JSONArray().put(comparison(
            id = "single-small",
            targetMs = 5.0,
            observedMs = 5.2,
            rpm = 2_200.0,
            mapBar = 0.50,
            row = 5,
            column = 4,
        ))

        val point = pointAt(analyze(comparisons), 5.0)
        assertFalse(point.getBoolean("actionable"))
        assertEquals("OBSERVING", point.getString("readiness"))
        assertTrue(point.isNull("suggestedDeltaPercent"))
        assertTrue(point.getDouble("uncertaintyPercent") > point.getDouble("usefulMarginPercent"))
    }

    @Test
    fun `dados contraditorios nao viram correcao por quantidade`() {
        val comparisons = JSONArray()
        repeat(20) { index ->
            comparisons.put(comparison(
                id = "conflict-$index",
                targetMs = 5.0,
                observedMs = if (index % 2 == 0) 4.5 else 5.5,
                rpm = 1_500.0 + index * 80.0,
                mapBar = 0.45,
                row = 5,
                column = 4,
            ))
        }

        val point = pointAt(analyze(comparisons), 5.0)
        assertFalse(point.getBoolean("actionable"))
        assertEquals("EQUIVALENT", point.getString("readiness"))
        assertTrue(point.isNull("suggestedDeltaPercent"))
    }

    @Test
    fun `erro global e removido antes do mapa residual`() {
        val result = analyze(
            comparisons = buildComparisons(
                count = 16,
                targetMs = 5.0,
                observedMs = 5.5,
                row = 5,
                column = 4,
            ),
        )

        val residual = result.getJSONArray("mapResidualSuggestions").getJSONObject(0)
        assertEquals(0.0, residual.getDouble("residualErrorPercent"), 0.000001)
        assertFalse(residual.getBoolean("actionable"))
        assertTrue(residual.getBoolean("globalTrendRemoved"))
        assertTrue(residual.isNull("suggestedDeltaPercent"))
        val predictions = result.getJSONArray("mapResidualPredictions")
        assertEquals(144, predictions.length())
        val supported = (0 until predictions.length())
            .map { predictions.getJSONObject(it) }
            .filter { it.getString("supportType") in setOf("DIRECT", "NEAR") }
        assertTrue(supported.isNotEmpty())
        assertTrue(supported.all { !it.getBoolean("actionable") })
    }

    @Test
    fun `residual local transfere para vizinhos mas nao atravessa area sem suporte`() {
        val comparisons = JSONArray()
        repeat(6) { index ->
            comparisons.put(comparison("low-$index", 5.0, 4.5, 1_700.0 + index * 20, 0.38, 4, 2))
            comparisons.put(comparison("high-$index", 5.0, 5.5, 1_700.0 + index * 20, 0.58, 4, 2))
        }
        val predictions = analyze(comparisons).getJSONArray("mapResidualPredictions")
        assertEquals(144, predictions.length())
        val local = (0 until predictions.length()).map { predictions.getJSONObject(it) }
            .filter { it.getString("supportType") in setOf("DIRECT", "NEAR") }
        assertTrue(local.isNotEmpty())
        assertTrue(local.any { !it.isNull("localResidualPercent") })
        val globalOnly = (0 until predictions.length()).map { predictions.getJSONObject(it) }
            .filter { it.getString("supportType") == "GLOBAL_ONLY" }
        assertTrue(globalOnly.isNotEmpty())
        assertTrue(globalOnly.all { !it.getBoolean("actionable") && it.isNull("suggestedDeltaPercent") })
    }

    @Test
    fun `regioes locais nascem dos dados e nao de faixas fixas`() {
        val comparisons = JSONArray()
        repeat(12) { index ->
            comparisons.put(comparison(
                id = "low-$index",
                targetMs = 5.0,
                observedMs = 5.0,
                rpm = 1_700.0 + index * 20.0,
                mapBar = 0.42,
                row = 5,
                column = 4,
            ))
            comparisons.put(comparison(
                id = "high-$index",
                targetMs = 5.0,
                observedMs = 6.0,
                rpm = 2_300.0 + index * 20.0,
                mapBar = 0.50,
                row = 5,
                column = 5,
            ))
        }

        val result = analyze(comparisons)
        val residual = result.getJSONArray("mapResidualSuggestions")
        val regions = result.getJSONArray("mapCorrectionRegions")
        assertEquals(2, residual.length())
        assertTrue(residual.getJSONObject(0).getBoolean("actionable"))
        assertTrue(residual.getJSONObject(1).getBoolean("actionable"))
        assertTrue("Direções opostas devem gerar regiões independentes", regions.length() >= 2)
        repeat(regions.length()) { index ->
            val region = regions.getJSONObject(index)
            assertEquals("AVAILABLE", region.getString("readiness"))
            assertTrue(region.getInt("cellCount") >= 1)
            assertNotNull(region.getJSONArray("cells"))
            assertFalse(region.getBoolean("automatic"))
        }
    }

    @Test
    fun `alvo ideal fica separado do passo seguro limitado na primeira visita`() {
        val comparisons = JSONArray().put(comparison(
            id = "first-independent-visit",
            targetMs = 5.0,
            observedMs = 6.0,
            rpm = 2_200.0,
            mapBar = 0.50,
            row = 5,
            column = 4,
        ))

        val point = pointAt(analyze(comparisons), 5.0)

        assertEquals(20.0, point.getDouble("idealDeltaPercent"), 0.000001)
        assertEquals("INDEPENDENCE_BOUNDED", point.getString("stepPolicy"))
        assertTrue(point.getDouble("suggestedDeltaPercent") <= 11.0)
        assertTrue(point.getDouble("suggestedDeltaPercent") < point.getDouble("idealDeltaPercent"))
        assertEquals(
            point.getDouble("idealDeltaPercent") - point.getDouble("suggestedDeltaPercent"),
            point.getDouble("estimatedResidualAfterPercent"),
            0.000001,
        )
    }

    @Test
    fun `visitas independentes coerentes liberam passo maior sem ultrapassar o alvo`() {
        val first = JSONArray().put(comparison(
            id = "visit-1",
            targetMs = 5.0,
            observedMs = 6.0,
            rpm = 2_000.0,
            mapBar = 0.50,
            row = 5,
            column = 4,
        ))
        val repeated = buildComparisons(
            count = 4,
            targetMs = 5.0,
            observedMs = 6.0,
            row = 5,
            column = 4,
        )

        val firstPoint = pointAt(analyze(first), 5.0)
        val repeatedPoint = pointAt(analyze(repeated), 5.0)

        assertTrue(repeatedPoint.getDouble("correctionFraction") > firstPoint.getDouble("correctionFraction"))
        assertTrue(repeatedPoint.getDouble("correctionFraction") <= 0.90)
        assertTrue(repeatedPoint.getDouble("suggestedDeltaPercent") <= repeatedPoint.getDouble("idealDeltaPercent"))
    }

    private fun analyze(comparisons: JSONArray): JSONObject =
        AssistedCalibrationAdvisor.analyze(JSONObject().put("comparisons", comparisons))

    private fun pointAt(result: JSONObject, petrolMs: Double): JSONObject {
        val points = result.getJSONArray("kFactorSuggestions")
        repeat(points.length()) { index ->
            val point = points.getJSONObject(index)
            if (kotlin.math.abs(point.getDouble("petrolMs") - petrolMs) < 0.000001) return point
        }
        error("Ponto $petrolMs ms não encontrado")
    }

    private fun buildComparisons(
        count: Int,
        targetMs: Double,
        observedMs: Double,
        row: Int,
        column: Int,
    ): JSONArray = JSONArray().also { comparisons ->
        repeat(count) { index ->
            comparisons.put(comparison(
                id = "sample-$index",
                targetMs = targetMs,
                observedMs = observedMs,
                rpm = 1_200.0 + index * 140.0,
                mapBar = 0.45,
                row = row,
                column = column,
            ))
        }
    }

    private fun comparison(
        id: String,
        targetMs: Double,
        observedMs: Double,
        rpm: Double,
        mapBar: Double,
        row: Int,
        column: Int,
    ): JSONObject = JSONObject()
        .put("visit_id", id)
        .put("petrol_target_ms", targetMs)
        .put("petrol_on_cng_ms", observedMs)
        .put("rpm", rpm)
        .put("map_bar", mapBar)
        .put("quality", 1.0)
        .put("continuous_cell_weights", JSONArray()
            .put(JSONObject().put("row", row).put("column", column).put("weight", 1.0)))
}
