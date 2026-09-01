package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Cenários de sistema para provar consequências do aprendizado, não apenas funções isoladas.
 * Tudo permanece determinístico e roda no JVM, sem USB, ECU ou escrita real.
 */
class LearningScenarioMatrixTest {
    @Test
    fun `grade fisica possui 144 celulas e interpolacao conserva todo o peso`() {
        val grid = LearningGridProjection.gridJson()
        assertEquals(12, grid.getInt("rows"))
        assertEquals(12, grid.getInt("columns"))
        assertEquals(144, grid.getInt("physicalCells"))
        assertTrue(grid.getBoolean("immutablePhysicalContract"))

        val random = Random(48048)
        repeat(500) {
            val rpm = random.nextDouble(0.0, 7_500.0)
            val petrol = random.nextDouble(0.0, 25.0)
            val map = random.nextDouble(0.0, 1.30)

            val bilinear = ContinuousLearningMath.bilinearWeights(rpm, petrol)
            assertTrue(bilinear.isNotEmpty())
            assertEquals(1.0, bilinear.sumOf { it.weight }, 1e-9)
            assertTrue(bilinear.all { it.row in 0..11 && it.column in 0..11 && it.weight in 0.0..1.0 })

            val trilinear = ContinuousLearningMath.trilinearWeights(rpm, petrol, map)
            assertTrue(trilinear.isNotEmpty())
            assertEquals(1.0, trilinear.sumOf { it.weight }, 1e-9)
            assertTrue(trilinear.all {
                it.row in 0..11 && it.column in 0..11 &&
                    it.mapIndex in LearningGridProjection.mapBins.indices && it.weight in 0.0..1.0
            })
        }
    }

    @Test
    fun `amostra entre eixos preenche quatro celulas vizinhas sem perder evidencia`() {
        val rpmBins = LearningGridProjection.rpmBins
        val petrolBins = LearningGridProjection.petrolBins
        val rpm = (rpmBins[3] + rpmBins[4]) / 2.0
        val petrol = (petrolBins[5] + petrolBins[6]) / 2.0
        val regions = JSONArray().put(region("middle", "PETROL", rpm, petrol, 0.55, 100.0, 100))

        val projected = LearningGridProjection.project(regions, currentEpoch = 3)
        assertEquals(4, projected.length())
        val keys = mutableSetOf<String>()
        var totalSamples = 0
        repeat(projected.length()) { index ->
            val cell = projected.getJSONObject(index)
            keys += cell.getString("key")
            totalSamples += cell.getInt("samples")
            assertTrue(cell.getInt("samples") > 0)
        }
        assertEquals(setOf("5:3", "5:4", "6:3", "6:4"), keys)
        assertEquals(100, totalSamples)
    }

    @Test
    fun `integridade detecta celula ausente e aceita projecao canonica`() {
        val regions = JSONArray()
            .put(region("p", "PETROL", 2_200.0, 5.0, 0.45, 20.0, 20))
            .put(region("g", "CNG", 2_200.0, 5.0, 0.45, 20.0, 20).put("epoch", 4))
        val projected = LearningGridProjection.project(regions, currentEpoch = 4)

        val valid = LearningGridProjection.integrity(regions, projected, JSONArray(), 4, "map-hash")
        assertTrue(valid.getBoolean("ok"))
        assertTrue(valid.getBoolean("memoryEqualsInterface"))
        assertTrue(valid.getBoolean("interfaceEqualsExport"))
        assertTrue(valid.getInt("comparableCells") > 0)

        val missing = JSONArray()
        repeat(projected.length() - 1) { missing.put(projected.getJSONObject(it)) }
        val invalid = LearningGridProjection.integrity(regions, missing, JSONArray(), 4, "map-hash")
        assertFalse(invalid.getBoolean("ok"))
        assertFalse(invalid.getBoolean("memoryEqualsInterface"))
    }

    @Test
    fun `erro global gera curva K mas nao inventa erro residual local`() {
        val comparisons = JSONArray()
        repeat(8) { row ->
            repeat(8) { column ->
                comparisons.put(comparison(
                    id = "global-$row-$column",
                    target = 5.0,
                    observed = 5.5,
                    rpm = 1_000.0 + column * 350.0,
                    map = 0.30 + row * 0.05,
                    row = row,
                    column = column,
                ))
            }
        }

        val result = AssistedCalibrationAdvisor.analyze(JSONObject().put("comparisons", comparisons))
        val globalPoint = pointAt(result, 5.0)
        assertTrue(globalPoint.getBoolean("actionable"))
        assertEquals("INCREASE_CNG_DELIVERY", globalPoint.getString("direction"))

        val residuals = result.getJSONArray("mapResidualSuggestions")
        repeat(residuals.length()) { index ->
            val residual = residuals.getJSONObject(index)
            assertTrue(abs(residual.getDouble("residualErrorPercent")) < 1e-6)
            assertFalse(residual.getBoolean("actionable"))
        }
    }

    @Test
    fun `anomalia local gera sugestao local coerente depois de remover tendencia global`() {
        val comparisons = JSONArray()
        repeat(30) { index ->
            comparisons.put(comparison(
                id = "baseline-$index",
                target = 5.0,
                observed = 5.0,
                rpm = 1_200.0 + (index % 10) * 250.0,
                map = 0.40,
                row = 4,
                column = index % 10,
            ))
        }
        repeat(18) { index ->
            comparisons.put(comparison(
                id = "local-$index",
                target = 5.0,
                observed = 6.0,
                rpm = 2_400.0 + index,
                map = 0.55,
                row = 6,
                column = 7,
            ))
        }

        val result = AssistedCalibrationAdvisor.analyze(JSONObject().put("comparisons", comparisons))
        val residuals = result.getJSONArray("mapResidualSuggestions")
        val local = (0 until residuals.length())
            .map { residuals.getJSONObject(it) }
            .first { it.getInt("row") == 6 && it.getInt("column") == 7 }

        assertTrue(local.getBoolean("actionable"))
        assertEquals("INCREASE_CNG_DELIVERY", local.getString("direction"))
        assertTrue(local.getDouble("suggestedDeltaPercent") > 0.0)
        assertTrue(local.getDouble("suggestedDeltaPercent") < local.getDouble("residualErrorPercent"))
        assertFalse(local.getBoolean("automatic"))
        assertTrue(local.getBoolean("humanConfirmationRequired"))
        assertTrue(result.getJSONArray("mapCorrectionRegions").length() >= 1)
    }

    @Test
    fun `ordem das comparacoes nao muda decisoes`() {
        val items = MutableList(40) { index ->
            comparison(
                id = "order-$index",
                target = 4.0 + (index % 5) * 0.5,
                observed = (4.0 + (index % 5) * 0.5) * if (index % 3 == 0) 1.10 else 1.06,
                rpm = 1_100.0 + index * 75.0,
                map = 0.35 + (index % 6) * 0.05,
                row = index % 10,
                column = (index * 3) % 10,
            )
        }
        val direct = AssistedCalibrationAdvisor.analyze(JSONObject().put("comparisons", JSONArray(items)))
        val shuffled = AssistedCalibrationAdvisor.analyze(
            JSONObject().put("comparisons", JSONArray(items.shuffled(Random(8128)))),
        )

        assertEquals(direct.getInt("comparisonCount"), shuffled.getInt("comparisonCount"))
        assertDecisionArraysEquivalent(
            direct.getJSONArray("kFactorSuggestions"),
            shuffled.getJSONArray("kFactorSuggestions"),
            keyFields = listOf("petrolMs"),
        )
        assertDecisionArraysEquivalent(
            direct.getJSONArray("mapResidualSuggestions"),
            shuffled.getJSONArray("mapResidualSuggestions"),
            keyFields = listOf("row", "column"),
        )
    }

    @Test
    fun `dados invalidos sao ignorados e toda proposta continua manual e limitada`() {
        val comparisons = JSONArray()
            .put(comparison("valid-a", 5.0, 6.0, 2_000.0, 0.45, 5, 5))
            .put(comparison("valid-b", 5.0, 6.0, 2_700.0, 0.65, 5, 7))
            .put(comparison("zero", 0.0, 6.0, 2_000.0, 0.45, 5, 5))
            .put(comparison("negative-rpm", 5.0, 6.0, -1.0, 0.45, 5, 5))
            .put(comparison("negative-map", 5.0, 6.0, 2_000.0, -1.0, 5, 5))

        val result = AssistedCalibrationAdvisor.analyze(JSONObject().put("comparisons", comparisons))
        assertEquals(2, result.getInt("comparisonCount"))
        assertFalse(result.getBoolean("automatic"))
        assertTrue(result.getBoolean("humanConfirmationRequired"))

        val point = pointAt(result, 5.0)
        assertTrue(point.getBoolean("actionable"))
        val observedError = abs(point.getDouble("errorPercent"))
        val suggested = abs(point.getDouble("suggestedDeltaPercent"))
        assertTrue(suggested > 0.0)
        assertTrue(suggested <= observedError)
        assertFalse(point.getBoolean("automatic"))
        assertTrue(point.getBoolean("humanConfirmationRequired"))
    }

    private fun region(
        id: String,
        fuel: String,
        rpm: Double,
        petrol: Double,
        map: Double,
        weight: Double,
        samples: Int,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("fuel", fuel)
        .put("rpm", rpm)
        .put("petrol_ms", petrol)
        .put("map_bar", map)
        .put("weight", weight)
        .put("samples", samples)
        .put("confidence", 0.9)
        .put("stage", "CONFIRMED")
        .put("visits", JSONArray().put("visit-$id"))
        .put("sessions", JSONArray().put("session-$id"))

    private fun comparison(
        id: String,
        target: Double,
        observed: Double,
        rpm: Double,
        map: Double,
        row: Int,
        column: Int,
    ): JSONObject = JSONObject()
        .put("visit_id", id)
        .put("petrol_target_ms", target)
        .put("petrol_on_cng_ms", observed)
        .put("rpm", rpm)
        .put("map_bar", map)
        .put("quality", 1.0)
        .put("continuous_cell_weights", JSONArray().put(
            JSONObject().put("row", row).put("column", column).put("weight", 1.0),
        ))

    private fun pointAt(result: JSONObject, petrolMs: Double): JSONObject {
        val points = result.getJSONArray("kFactorSuggestions")
        repeat(points.length()) { index ->
            val point = points.getJSONObject(index)
            if (abs(point.getDouble("petrolMs") - petrolMs) < 1e-9) return point
        }
        error("Ponto $petrolMs ms não encontrado")
    }

    private fun assertDecisionArraysEquivalent(
        expected: JSONArray,
        actual: JSONArray,
        keyFields: List<String>,
    ) {
        assertEquals(expected.length(), actual.length())
        val expectedByKey = expected.toObjects().associateBy { decisionKey(it, keyFields) }
        val actualByKey = actual.toObjects().associateBy { decisionKey(it, keyFields) }
        assertEquals(expectedByKey.keys, actualByKey.keys)
        expectedByKey.forEach { (key, expectedItem) ->
            val actualItem = requireNotNull(actualByKey[key])
            assertEquals(expectedItem.getString("direction"), actualItem.getString("direction"))
            assertEquals(expectedItem.getBoolean("actionable"), actualItem.getBoolean("actionable"))
            assertEquals(expectedItem.getBoolean("automatic"), actualItem.getBoolean("automatic"))
            assertEquals(
                expectedItem.getBoolean("humanConfirmationRequired"),
                actualItem.getBoolean("humanConfirmationRequired"),
            )
            listOf("errorPercent", "residualErrorPercent", "suggestedDeltaPercent", "confidence")
                .filter { expectedItem.has(it) || actualItem.has(it) }
                .forEach { field ->
                    assertEquals(expectedItem.optDouble(field), actualItem.optDouble(field), 1e-9)
                }
        }
    }

    private fun JSONArray.toObjects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun decisionKey(item: JSONObject, fields: List<String>): String =
        fields.joinToString(":") { field -> item.get(field).toString() }
}
