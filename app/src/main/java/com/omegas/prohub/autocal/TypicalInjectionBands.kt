package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/** Consulta de apresentação sobre a gasolina já coletada; não muda o refino. */
object TypicalInjectionBands {
    fun json(observations: List<EquivalenceLedger.Obs>): JSONArray = JSONArray().apply {
        EquivalenceLedger.BANDS.forEach { (lo, hi) ->
            val selected = observations.filter { it.petrolMs >= lo && it.petrolMs < hi && it.rpm.isFinite() && it.map.isFinite() }
            put(JSONObject().put("fromMs",lo).put("toMs",hi).put("samples",selected.size)
                .put("rpmMedian",if(selected.isEmpty())JSONObject.NULL else PresentationMedian.of(selected.map { it.rpm }))
                .put("mapMedian",if(selected.isEmpty())JSONObject.NULL else PresentationMedian.of(selected.map { it.map })))
        }
    }
}
