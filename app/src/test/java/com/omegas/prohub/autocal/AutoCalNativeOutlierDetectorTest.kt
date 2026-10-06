package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalNativeOutlierDetectorTest {
    @Test
    fun `ponto maduro abaixo da curva robusta precisa repetir em leituras independentes antes de readquirir`() {
        val detector = AutoCalNativeOutlierDetector()

        assertNull(detector.observe(snapshot(capturedAtMs = 1_000L, outlierIndex = 4)))
        val decision = detector.observe(snapshot(capturedAtMs = 2_100L, outlierIndex = 4))

        requireNotNull(decision)
        assertEquals(AutoCalPointDeleteProtocol.Fuel.GAS, decision.fuel)
        assertEquals(4, decision.index)
        assertTrue(decision.expectedMapBar > decision.actualMapBar)
        assertTrue(decision.deltaBar >= AutoCalNativeOutlierDetector.MIN_DOWN_GAP_BAR)
        assertEquals(2, decision.confirmations)
        assertTrue(decision.reason.contains("REFINO_ROBUST_OUTLIER"))
    }

    @Test
    fun `curva coerente nunca vira candidato automatico`() {
        val detector = AutoCalNativeOutlierDetector()

        assertNull(detector.observe(snapshot(capturedAtMs = 1_000L, outlierIndex = null)))
        assertNull(detector.observe(snapshot(capturedAtMs = 2_100L, outlierIndex = null)))
        assertNull(detector.observe(snapshot(capturedAtMs = 3_200L, outlierIndex = null)))
        assertFalse(detector.statusJson().optBoolean("pending", false))
    }

    @Test
    fun `ponto ainda imaturo nao e apagado mesmo muito fora da curva`() {
        val detector = AutoCalNativeOutlierDetector()

        assertNull(detector.observe(snapshot(capturedAtMs = 1_000L, outlierIndex = 4, outlierMature = false)))
        assertNull(detector.observe(snapshot(capturedAtMs = 2_100L, outlierIndex = 4, outlierMature = false)))
        assertNull(detector.observe(snapshot(capturedAtMs = 3_200L, outlierIndex = 4, outlierMature = false)))
    }

    @Test
    fun `submissao automatica nao entra em loop na mesma geracao mas nova geracao pode reaprender`() {
        val detector = AutoCalNativeOutlierDetector()
        detector.observe(snapshot(capturedAtMs = 1_000L, outlierIndex = 4, generation = 3))
        val first = detector.observe(snapshot(capturedAtMs = 2_100L, outlierIndex = 4, generation = 3))
        requireNotNull(first)

        detector.markSubmitted(first)
        assertNull(detector.observe(snapshot(capturedAtMs = 3_200L, outlierIndex = 4, generation = 3)))
        assertNull(detector.observe(snapshot(capturedAtMs = 4_300L, outlierIndex = 4, generation = 3)))

        assertNull(detector.observe(snapshot(capturedAtMs = 5_400L, outlierIndex = 4, generation = 4)))
        val nextGeneration = detector.observe(snapshot(capturedAtMs = 6_500L, outlierIndex = 4, generation = 4))
        requireNotNull(nextGeneration)
        assertEquals(4, nextGeneration.index)
        assertEquals(4, nextGeneration.generation)
    }

    private fun snapshot(
        capturedAtMs: Long,
        outlierIndex: Int?,
        outlierMature: Boolean = true,
        generation: Int = 0,
    ): JSONObject {
        val maps = doubleArrayOf(0.20, 0.28, 0.36, 0.44, 0.52, 0.60, 0.68, 0.76)
        if (outlierIndex != null) maps[outlierIndex] = 0.18
        val points = JSONArray()
        repeat(maps.size) { index ->
            val mature = outlierIndex != index || outlierMature
            points.put(
                JSONObject()
                    .put("index", index)
                    .put("zone", if (index <= 5) 0 else 1)
                    .put("fuel", "GNV")
                    .put("timeMs", 2.0 + index)
                    .put("mapBar", maps[index])
                    .put("counter", if (mature) 6 else 1)
                    .put("threshold", 3)
                    .put("state", if (mature) "ZONA_ADQUIRIDA" else "ATIVIDADE")
                    .put("draw", true)
                    .put("previous", false),
            )
        }
        return JSONObject()
            .put("available", true)
            .put("capturedAtMs", capturedAtMs)
            .put("autoCalEnabled", 1)
            .put("acquisition", JSONObject().put("points", points))
            .put(
                "liveAcquisitionEpoch",
                JSONObject()
                    .put("usbSessionId", 42L)
                    .put("petrolGeneration", 0)
                    .put("gasGeneration", generation),
            )
    }
}
