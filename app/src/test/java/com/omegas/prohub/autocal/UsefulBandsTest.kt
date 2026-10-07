package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * T(MAP) nativo usa só as 16 bandas úteis (0..15) e só a banda cujo MAP do buffer está dentro dos limiares
 * MNFLD_PRESS_THD dela ([THD[b], THD[b+1]]; 740 leituras reais, nenhuma fora).
 */
class UsefulBandsTest {
    private fun snapshot(): JSONObject {
        val file = listOf("../fixtures/autocal/real/ref_2026-10-01_1719.json.gz", "fixtures/autocal/real/ref_2026-10-01_1719.json.gz")
            .map(::File).first { it.exists() }
        val snaps = JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }).getJSONArray("snapshots")
        for (i in 0 until snaps.length()) if (snaps.getJSONObject(i).getInt("sequence") == 95) return snaps.getJSONObject(i)
        error("snapshot 95 ausente")
    }

    private fun JSONObject.field(key: String): JSONObject {
        val fields = getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("key") == key }
    }

    private fun analyze(snap: JSONObject) = AutoMatchSnapshotAnalysis.analyzeRefined(snap, epoch = NativeGasEvidenceEpoch())

    private fun targetMaps(a: JSONObject) = (0 until a.getJSONArray("targets").length()).map { a.getJSONArray("targets").getJSONObject(it).getDouble("mapBar") }

    @Test
    fun `bandas 16 e 17 nao entram em T de MAP`() {
        val clean = analyze(snapshot())
        val snap = snapshot()
        // Banda 16 (acima de 1,0 bar) com dado maduro nos dois combustíveis: fora das 16 úteis.
        snap.field("NUM_BUF_UPD_GAS").getJSONArray("rawValues").put(16, 10)
        snap.field("PETR_INJ_TBUF_GAS").getJSONArray("rawValues").put(16, 6_000)
        snap.field("MNFLD_PRESS_BUF_GAS").getJSONArray("rawValues").put(16, 1_070)
        snap.field("NUM_BUF_UPD_PETR").getJSONArray("rawValues").put(16, 10)
        snap.field("PETR_INJ_TBUF").getJSONArray("rawValues").put(16, 5_200)
        snap.field("MNFLD_PRESS_BUF").getJSONArray("rawValues").put(16, 1_070)
        val withTail = analyze(snap)
        assertFalse(targetMaps(withTail).any { it > 1_024 / 1024.0 + 1e-9 })
        assertEquals(targetMaps(clean), targetMaps(withTail))
    }

    @Test
    fun `banda com MAP fora dos limiares dela e evidencia invalida`() {
        val clean = analyze(snapshot())
        val snap = snapshot()
        // Banda 3 da gasolina vale [358; 410] contagens; um buffer em 600 não é desta banda.
        snap.field("MNFLD_PRESS_BUF").getJSONArray("rawValues").put(3, 600)
        val bad = analyze(snap)
        assertEquals(clean.getInt("invalidEvidenceBands") + 1, bad.getInt("invalidEvidenceBands"))
        assertTrue(targetMaps(bad).none { kotlin.math.abs(it - 600 / 1024.0) < 1e-9 })
    }
}
