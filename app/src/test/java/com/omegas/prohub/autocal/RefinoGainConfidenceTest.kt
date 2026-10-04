package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Classe 2: ganho só aprende de evidência independente e precisa, sem tocar nos escritores. */
class RefinoGainConfidenceTest {
    private val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private val before = IntArray(30) { 16384 }
    private val after = before.copyOf().also { for (i in 5..29) it[i] = 17000 }

    private fun statistics(nEff: Double = 20.0, dispersion: Double = 0.002) = JSONObject()
        .put("model", "overlap-lag1-mad-v1")
        .put("effectiveSamples", nEff).put("dispersionLog", dispersion)

    private fun index(ratio: Double, stats: JSONObject? = statistics()) = JSONObject().put("bands",
        JSONArray(EquivalenceLedger.BANDS.map { (lo, hi) ->
            JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", 40)
                .put("ratio", ratio).put("episodes", 1).put("interiorCovered", true)
                .put("evidenceStats", stats?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
        }))

    private fun experiment(baseline: JSONObject, current: JSONObject, file: File? = null): RefinementJournal =
        RefinementJournal(file) { 1_000L }.also {
            it.recordCurveWrite(before, after, axis, baseline, "ajuste", photoFile = "foto-antes.json")
            it.evaluate(current)
        }

    private fun scale(journal: RefinementJournal) = journal.json().getJSONArray("bandScale").getDouble(2)

    @Test fun overlappingBaselineCannotTeachFirmerGain() {
        // 40 quadros idênticos têm n efetivo ≈ 1,03, apesar da contagem bruta.
        val j = experiment(index(1.10, statistics(40.0 / 39.0)), index(1.06))
        assertEquals("repetição não ensina ganho", 1.0, scale(j), 1e-12)
    }

    @Test fun overlappingPostWriteSamplesCannotTeachFirmerGain() {
        val j = experiment(index(1.10), index(1.06, statistics(1.0)))
        assertEquals("depois também precisa de independência", 1.0, scale(j), 1e-12)
    }

    @Test fun broadUncertaintyCannotTeachFirmerGain() {
        // t(20) * 0,25 / sqrt(20) > 4%: amostras numerosas, mas imprecisas.
        val j = experiment(index(1.10), index(1.06, statistics(20.0, 0.25)))
        assertEquals("dispersão não vira aprendizado", 1.0, scale(j), 1e-12)
    }

    @Test fun legacySnapshotsRemainReadableButCannotInventConfidence() {
        val j = experiment(index(1.10, null), index(1.06))
        assertEquals("VERIFICADO", j.json().getJSONObject("latest").getString("status"))
        assertEquals(1.0, scale(j), 1e-12)
        assertEquals("foto-antes.json", j.json().getJSONObject("latest").getString("photoFile"))
    }

    @Test fun reliableImprovementStillTeachesFirmerGain() {
        val j = experiment(index(1.10), index(1.06))
        assertEquals(1.15, scale(j), 1e-12)
        val d = j.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2).getJSONObject("decision")
        assertTrue(d.getBoolean("learningAllowed"))
        assertEquals("CONFIDENT_BEFORE_AND_AFTER", d.getString("learningReasonCode"))
        assertNotNull(d.getJSONObject("evidenceBefore"))
        assertNotNull(d.getJSONObject("evidenceAfter"))
    }

    @Test fun reliableOvershootStillSoftensGain() {
        val j = experiment(index(1.05), index(0.95))
        assertEquals(0.7, scale(j), 1e-12)
    }

    @Test fun malformedStatisticsFailClosed() {
        val bad = listOf(
            statistics(41.0), statistics(-1.0), statistics(20.0, -0.01),
            statistics().put("model", "unknown"),
            statistics().put("effectiveSamples", "NaN"),
            statistics().put("dispersionLog", "Infinity"),
        )
        for (s in bad) {
            val j = experiment(index(1.10), index(1.06, s))
            assertEquals(s.toString(), 1.0, scale(j), 1e-12)
        }
    }

    @Test fun confidenceAndPhotoSurviveJournalReadback() {
        val dir = Files.createTempDirectory("refino-confidence").toFile()
        try {
            val file = File(dir, "journal.json")
            experiment(index(1.10), index(1.06, statistics(1.0)), file).flush()
            val restored = RefinementJournal(file) { 1_000L }
            assertEquals(1.0, scale(restored), 1e-12)
            val latest = restored.json().getJSONObject("latest")
            assertEquals("foto-antes.json", latest.getString("photoFile"))
            val decision = latest.getJSONArray("bands").getJSONObject(2).getJSONObject("decision")
            assertFalse(decision.getBoolean("learningAllowed"))
            assertEquals("UNCERTAIN_AFTER", decision.getString("learningReasonCode"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun realLedgerPublishesConfidenceWithoutChangingRatioOrSamples() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        for ((fuel, ms) in listOf("GASOLINA" to 5.0, "GNV" to 5.5)) {
            repeat(10) {
                ledger.accept(EquivalenceLedger.Frame(t, fuel, 2000.0, 0.60, ms))
                t += 280L
            }
            t += 5_000L
        }
        val band = ledger.index().getJSONArray("bands").getJSONObject(1)
        assertEquals(8, band.getInt("samples"))
        assertEquals(1.1, band.getDouble("ratio"), 1e-9)
        val stats = band.optJSONObject("evidenceStats")
        assertNotNull("confiança precisa vir da telemetria real, não de fixture", stats)
        assertEquals("overlap-lag1-mad-v1", stats!!.getString("model"))
        assertTrue(stats.getDouble("effectiveSamples") < 3.0)
    }
}
