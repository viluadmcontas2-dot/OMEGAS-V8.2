package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchSnapshotAnalysisTest {
    private val bands = intArrayOf(
        154, 256, 307, 358, 410, 461, 512, 563, 614,
        666, 717, 768, 819, 870, 922, 973, 1024, 1126,
    )

    @Test
    fun `curvas iguais preservam MUL atual e publicam formula inferida`() {
        val axis = observedAxis()
        val map = recoveredPetrol()
        val current = IntArray(30) { 0x5000 + it }
        val analysis = AutoMatchSnapshotAnalysis.analyze(snapshot(axis, bands, map, map, current))

        assertTrue(analysis.getBoolean("available"))
        assertTrue(analysis.getBoolean("complete"))
        assertFalse(analysis.getBoolean("nativeFirmwareExact"))
        assertFalse(analysis.getBoolean("automatic"))
        assertEquals("AUTOMATCH_INFERIDO_V2", analysis.getString("mode"))
        assertEquals("MULTIPLICATIVE_ON_PREVIOUS_MUL", analysis.getJSONObject("formula").getString("update"))
        assertEquals(14, analysis.getInt("validBandCount"))
        assertEquals(30, analysis.getJSONArray("points").length())
        val point = analysis.getJSONArray("points").getJSONObject(10)
        assertEquals(current[10], point.getInt("currentRaw"))
        assertEquals(current[10], point.getInt("calculatedRaw"))
        assertEquals(0.0, point.getDouble("stepPercent"), 0.0)
        assertEquals(0.0, point.getDouble("deltaPercent"), 0.0)
    }

    @Test
    fun `MUL atual e obrigatório para atualização multiplicativa`() {
        val result = AutoMatchSnapshotAnalysis.analyze(
            snapshot(observedAxis(), bands, recoveredPetrol(), recoveredPetrol(), null),
        )
        assertFalse(result.getBoolean("available"))
        assertEquals(1, result.getJSONArray("missingFields").length())
        assertEquals(AutoCalProtocol.MUL_ACT.key, result.getJSONArray("missingFields").getString(0))
        assertTrue(result.getString("message").contains("MUL_ACT"))
    }

    @Test
    fun `campos ausentes explicam todos os cinco requisitos`() {
        val result = AutoMatchSnapshotAnalysis.analyze(JSONObject().put("fields", JSONArray()))
        assertFalse(result.getBoolean("available"))
        assertEquals(5, result.getJSONArray("missingFields").length())
        assertEquals(5, result.getJSONArray("requirements").length())
        assertFalse(result.getBoolean("nativeFirmwareExact"))
    }

    @Test
    fun `fixture histórica publica passo e atualização sobre MUL`() {
        val analysis = AutoMatchSnapshotAnalysis.analyze(
            snapshot(
                observedAxis(),
                bands,
                recoveredPetrol(),
                recoveredGas(),
                IntArray(30) { KFactorProtocol.Q14_SCALE },
            ),
        )
        assertTrue(analysis.getBoolean("available"))
        val points = analysis.getJSONArray("points")
        assertEquals(16_439, points.getJSONObject(0).getInt("calculatedRaw"))
        assertEquals(16_183, points.getJSONObject(7).getInt("calculatedRaw"))
        assertTrue(points.getJSONObject(0).getDouble("stepPercent") > 0.0)
        assertTrue(points.getJSONObject(7).getDouble("stepPercent") < 0.0)
        assertEquals("EXTENDED_LEFT", points.getJSONObject(0).getString("origin"))
        assertEquals("CALCULATED", points.getJSONObject(7).getString("origin"))
    }

    @Test
    fun `eixo inválido retorna erro auditável sem escrita`() {
        val axis = IntArray(30) { 256 }
        val map = recoveredPetrol()
        val result = AutoMatchSnapshotAnalysis.analyze(
            snapshot(axis, bands, map, map, IntArray(30) { 0x4000 }),
        )
        assertFalse(result.getBoolean("ok"))
        assertFalse(result.getBoolean("automatic"))
        assertTrue(result.has("error"))
    }

    @Test
    fun `campos AutoMatch realmente fora da janela temporal nao geram proposta`() {
        val snapshot = snapshot(observedAxis(), bands, recoveredPetrol(), recoveredGas(), IntArray(30) { 0x4000 })
        val fields = snapshot.getJSONArray("fields")
        repeat(fields.length()) { index ->
            fields.getJSONObject(index).put("capturedAtMs", if (index == 0) 100L else 2_101L)
        }
        val result = AutoMatchSnapshotAnalysis.analyze(snapshot)
        assertFalse(result.getBoolean("available"))
        assertEquals(2_001L, result.getLong("requiredFieldSpanMs"))
        assertEquals(AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS, result.getLong("maximumFieldSkewMs"))
    }

    private fun snapshot(
        axis: IntArray,
        pressureBands: IntArray,
        petrol: IntArray,
        gas: IntArray,
        mul: IntArray?,
    ): JSONObject {
        val fields = JSONArray()
            .put(field(AutoCalProtocol.PETR_INJ_TBP.key, axis))
            .put(field(AutoCalProtocol.MNFLD_PRESS_THD.key, pressureBands))
            .put(field(AutoCalProtocol.PETR_MNFLD_PRESS_RV.key, petrol))
            .put(field(AutoCalProtocol.GAS_MNFLD_PRESS_RV.key, gas))
        if (mul != null) fields.put(field(AutoCalProtocol.MUL_ACT.key, mul))
        return JSONObject()
            .put("snapshotHash", "fixture")
            .put("fields", fields)
    }

    private fun field(key: String, raw: IntArray): JSONObject = JSONObject()
        .put("key", key)
        .put("status", AutoCalFieldStatus.VALID.name)
        .put("rawValues", JSONArray(raw.toList()))

    private fun observedAxis() = KFactorProtocol.OBSERVED_PETROL_AXIS_MS
        .map { (it * KFactorProtocol.AXIS_COUNTS_PER_MS).toInt() }
        .toIntArray()

    private fun recoveredPetrol() = intArrayOf(
        10, 45, 113, 182, 251, 319, 387, 456, 530, 590,
        645, 711, 734, 760, 781, 802, 813, 824, 837, 853,
        910, 961, 1012, 1063, 1114, 1165, 1216, 1267, 1369, 1471,
    )

    private fun recoveredGas() = intArrayOf(
        78, 126, 174, 222, 270, 310, 398, 493, 535, 577,
        648, 692, 732, 764, 790, 800, 811, 822, 832, 853,
        902, 974, 1046, 1118, 1190, 1262, 1334, 1406, 1550, 1694,
    )
}
