package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KFactorManualPlannerTest {
    @Test
    fun `converte alvo decimal para Q14 sem escrever`() {
        val root = Files.createTempDirectory("omegas-k-factor-test").toFile()
        try {
            writeCache(root)
            val preview = KFactorManualPlanner.preview(root, 9, 1.05)

            assertTrue(preview.getBoolean("ok"))
            assertEquals(9, preview.getInt("index"))
            assertEquals(5.0, preview.getDouble("petrolMs"), 0.000001)
            assertEquals(0x426F, preview.getInt("currentRaw"))
            assertTrue(preview.getBoolean("changed"))
            assertTrue(preview.getBoolean("requiresReview"))
            assertFalse(preview.getBoolean("automatic"))
            assertFalse(preview.getBoolean("saturatedAtMaximum"))
            assertEquals(0.60, preview.getDouble("minimumFactor"), 0.0)
            assertEquals(KFactorProtocol.MAX_FACTOR, preview.getDouble("maximumFactor"), 0.0)
            assertEquals(4.0, preview.getDouble("maximumInputFactor"), 0.0)
            assertEquals(KFactorProtocol.MAX_RAW, preview.getInt("maximumRaw"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `aceita fator acima de tres e meio dentro do u16 Q14`() {
        val root = Files.createTempDirectory("omegas-k-factor-high").toFile()
        try {
            writeCache(root)
            val preview = KFactorManualPlanner.preview(root, 9, 3.90)
            assertTrue(preview.getBoolean("ok"))
            assertEquals(KFactorProtocol.rawFromFactor(3.90), preview.getInt("targetRaw"))
            assertFalse(preview.getBoolean("saturatedAtMaximum"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `operacional quatro satura exatamente no raw maximo`() {
        val root = Files.createTempDirectory("omegas-k-factor-max").toFile()
        try {
            writeCache(root)
            val maximum = KFactorManualPlanner.preview(root, 9, 4.0)
            assertTrue(maximum.getBoolean("ok"))
            assertTrue(maximum.getBoolean("saturatedAtMaximum"))
            assertEquals(4.0, maximum.getDouble("requestedFactor"), 0.0)
            assertEquals(KFactorProtocol.MAX_RAW, maximum.getInt("targetRaw"))
            assertEquals(KFactorProtocol.MAX_FACTOR, maximum.getDouble("targetFactor"), 0.0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `recusa somente abaixo do minimo ou acima da sentinela operacional`() {
        val root = Files.createTempDirectory("omegas-k-factor-limits").toFile()
        try {
            writeCache(root)
            assertFalse(KFactorManualPlanner.preview(root, 9, 0.59).getBoolean("ok"))
            assertFalse(KFactorManualPlanner.preview(root, 9, 4.0001).getBoolean("ok"))
            val maximum = KFactorManualPlanner.preview(root, 9, KFactorProtocol.MAX_FACTOR)
            assertTrue(maximum.getBoolean("ok"))
            assertEquals(KFactorProtocol.MAX_RAW, maximum.getInt("targetRaw"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `recusa prévia sem curva confirmada nesta conexão`() {
        val root = Files.createTempDirectory("omegas-k-factor-empty").toFile()
        try {
            val preview = KFactorManualPlanner.preview(root, 9, 1.05)
            assertFalse(preview.getBoolean("ok"))
            assertTrue(preview.getString("error").contains("Leia a curva"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeCache(root: File) {
        val axis = JSONArray()
        val factors = JSONArray()
        repeat(30) { index ->
            axis.put((index + 1) * 0x0100)
            factors.put(if (index == 9) 0x426F else 0x4000)
        }
        File(root, "k_factor_cache.json").writeText(
            JSONObject()
                .put("complete", true)
                .put("sessionConfirmed", true)
                .put("axisRaw", axis)
                .put("factorsRaw", factors)
                .toString(),
        )
    }
}
