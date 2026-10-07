package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Época da evidência nativa do GNV: buffers adquiridos sob a Curva K ANTIGA não podem virar alvo com o K novo
 * (K_alvo = K(T_g)·T_g/T_p aplicaria a correção duas vezes). Só conta o que o contador ganhou desde a gravação.
 */
class NativeGasEvidenceEpochTest {
    private fun snapshot(): JSONObject {
        val file = listOf("../fixtures/autocal/real/ref_2026-10-01_1719.json.gz", "fixtures/autocal/real/ref_2026-10-01_1719.json.gz")
            .map(::File).first { it.exists() }
        val snaps = JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }).getJSONArray("snapshots")
        for (i in 0 until snaps.length()) if (snaps.getJSONObject(i).getInt("sequence") == 95) return snaps.getJSONObject(i)
        error("snapshot 95 ausente")
    }

    private fun shifted(snap: JSONObject, deltaMs: Long, gasCountBump: Int = 0): JSONObject {
        val copy = JSONObject(snap.toString())
        copy.put("capturedAtMs", copy.getLong("capturedAtMs") + deltaMs)
        val fields = copy.getJSONArray("fields")
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            if (f.has("capturedAtMs")) f.put("capturedAtMs", f.getLong("capturedAtMs") + deltaMs)
            if (f.getString("key") == "NUM_BUF_UPD_GAS" && gasCountBump != 0) {
                val raw = f.getJSONArray("rawValues")
                for (b in 0 until raw.length()) if (raw.getInt(b) > 0) raw.put(b, raw.getInt(b) + gasCountBump)
            }
        }
        return copy
    }

    @Test
    fun `sem gravacao do app os contadores passam intactos`() {
        val epoch = NativeGasEvidenceEpoch()
        val counts = intArrayOf(0, 1, 10, 10)
        assertArrayEquals(counts, epoch.effectiveGasCounts(counts, gasCapturedAtMs = 5_000L, mulCapturedAtMs = 5_000L))
    }

    @Test
    fun `snapshot lido inteiro antes da gravacao e coerente com o K dele`() {
        val epoch = NativeGasEvidenceEpoch()
        epoch.markWrite(10_000L)
        val counts = intArrayOf(0, 1, 10, 10)
        assertArrayEquals(counts, epoch.effectiveGasCounts(counts, gasCapturedAtMs = 5_000L, mulCapturedAtMs = 6_000L))
    }

    @Test
    fun `buffers antigos com K novo nao sao evidencia`() {
        val epoch = NativeGasEvidenceEpoch()
        epoch.markWrite(10_000L)
        // MUL_ACT relido depois da gravação, buffers ainda da época antiga.
        assertArrayEquals(IntArray(4), epoch.effectiveGasCounts(intArrayOf(0, 1, 10, 10), gasCapturedAtMs = 9_000L, mulCapturedAtMs = 11_000L))
        // Primeira leitura dos buffers depois da gravação: vira a linha de base, nada ainda é da época nova.
        assertArrayEquals(IntArray(4), epoch.effectiveGasCounts(intArrayOf(0, 2, 10, 10), gasCapturedAtMs = 12_000L, mulCapturedAtMs = 11_000L))
        // Só o que o contador ganhou desde a linha de base conta; banda zerada (AutoMatch) recomeça do zero, toda nova.
        assertArrayEquals(intArrayOf(4, 3, 0, 2), epoch.effectiveGasCounts(intArrayOf(4, 5, 10, 2), gasCapturedAtMs = 20_000L, mulCapturedAtMs = 20_000L))
    }

    @Test
    fun `refino real nao usa buffers GNV de antes da gravacao do K`() {
        val snap = snapshot()
        val clean = AutoMatchSnapshotAnalysis.analyzeRefined(snap, epoch = NativeGasEvidenceEpoch())
        assertEquals("EQUIVALENCE", clean.getString("refinementMode"))

        val epoch = NativeGasEvidenceEpoch()
        // O app gravou a Curva K antes desta leitura: os buffers (contadores já em 10) são da curva antiga.
        epoch.markWrite(snap.getLong("capturedAtMs") - 60_000L)
        val stale = AutoMatchSnapshotAnalysis.analyzeRefined(snap, epoch = epoch)
        assertEquals("POLISH", stale.getString("refinementMode"))
        assertEquals(0, stale.getInt("changedCount"))
        assertTrue(stale.getBoolean("nativeEpochFiltered"))

        // Depois, os contadores sobem em todas as bandas com dado: essa parte é da curva nova e volta a valer.
        val fresh = AutoMatchSnapshotAnalysis.analyzeRefined(shifted(snap, 600_000L, gasCountBump = 6), epoch = epoch)
        assertEquals("EQUIVALENCE", fresh.getString("refinementMode"))
    }
}
