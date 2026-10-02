package com.omegas.prohub.autocal

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DenseBandsTest {
    private fun seeded(p: List<List<Double>>, g: List<List<Double>> = emptyList()): EquivalenceLedger {
        val file = File(Files.createTempDirectory("dense").toFile(), "ledger.json")
        file.writeText(JSONObject().put("format", EquivalenceLedger.FORMAT)
            .put("petrol", JSONArray(p.map { JSONArray(it) })).put("gas", JSONArray(g.map { JSONArray(it) })).toString())
        return EquivalenceLedger(file)
    }
    @Test fun `median centers counts and idle share use stable observations only`() {
        val rows = listOf(1.0,8.0,5.0,7.0,3.0,9.0).mapIndexed { i, ms -> listOf(i.toDouble(), if(i<2)800.0 else 2200.0,0.60,ms) }
        val band = seeded(rows).denseBandsJson().getJSONArray("petrol").getJSONObject(0)
        assertEquals(6.0,band.getDouble("tpetMs"),1e-9)
        assertEquals(0.6125,band.getDouble("mapBar"),1e-9)
        assertEquals(2200.0,band.getDouble("rpmMedian"),1e-9)
        assertEquals(6,band.getInt("samples")); assertEquals(2.0/6,band.getDouble("idleShare"),1e-9)
    }
    @Test fun `sparse bins omitted and gas reset preserves petrol and invalidates cache`() {
        val rows = (0..4).map { listOf(it.toDouble(),2000.0,0.6,5.0) }
        val sparse = (0..3).map { listOf(it.toDouble(),2300.0,0.7,6.0) }
        val ledger=seeded(rows+sparse,rows)
        val before=ledger.denseBandsJson();assertEquals(1,before.getJSONArray("petrol").length());assertEquals(1,before.getJSONArray("gas").length())
        before.getJSONArray("petrol").getJSONObject(0).put("samples",999)
        assertEquals(5,ledger.denseBandsJson().getJSONArray("petrol").getJSONObject(0).getInt("samples"))
        ledger.resetGas("TEST");assertEquals(0,ledger.denseBandsJson().getJSONArray("gas").length());assertEquals(1,ledger.denseBandsJson().getJSONArray("petrol").length())
    }
    @Test fun `new stable samples invalidate cache and query settings are respected`() {
        val ledger=EquivalenceLedger();assertEquals(0,ledger.denseBandsJson().getJSONArray("petrol").length())
        repeat(7){ledger.accept(EquivalenceLedger.Frame(it*280L,"GASOLINA",2000.0,0.62,5.0))}
        assertEquals(1,ledger.denseBandsJson().getJSONArray("petrol").length());assertEquals(0,ledger.denseBandsJson(minSamples=6).getJSONArray("petrol").length())
        assertEquals(0.625,ledger.denseBandsJson(binBar=0.05).getJSONArray("petrol").getJSONObject(0).getDouble("mapBar"),1e-9)
        assertEquals(5,ledger.index().getInt("petrolObservations"));assertTrue(ledger.pairs().isEmpty())
    }
    @Test fun `linear median agrees with sorted oracle including duplicates and even sizes`() {
        val random=java.util.Random(17)
        for(n in 1..300){val values=List(n){random.nextInt(30).toDouble()};val sorted=values.sorted();val expected=if(n%2==1)sorted[n/2]else(sorted[n/2-1]+sorted[n/2])/2
            assertEquals(expected,PresentationMedian.of(values),0.0)}
    }
}
