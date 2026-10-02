package com.omegas.prohub.telemetry

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Observação por versão confirmada. Não contém writer nem decisões de calibração. */
class CalibrationScoreboard(private val file: File? = null) {
    companion object { const val MIN_KM = 5.0 } // Janela operacional mínima; não prova estatística.
    private var lastAt: Long? = null
    private val epochs = mutableListOf<JSONObject>()
    private var lastDistance: Double? = null
    private var lastPosition: Double? = null
    private var lastGas = false
    private var segmentStart: Double? = null
    private var segmentBaseDrop = 0.0
    private var lowestPosition: Double? = null
    private var lastSaveAt = 0L
    init {
        try {
            if (file?.isFile == true) {
                val array=JSONObject(file.readText()).optJSONArray("epochs") ?: JSONArray()
                for (i in 0 until array.length()) array.optJSONObject(i)?.let { epochs.add(it) }
            }
        } catch (_: Exception) { /* Histórico inválido não fabrica uma época. */ }
    }
    @Synchronized fun confirm(id: String, target: String, humanConfirmed: Boolean, readbackValid: Boolean, timestamp: Long, closingGasPerAir: Double? = null): Boolean {
        if (!humanConfirmed || !readbackValid || id.isBlank() || target !in listOf("CURVE_K","MAP_K") || epochs.any { it.optString("id") == id }) return false
        if(closingGasPerAir!=null && closingGasPerAir.isFinite() && closingGasPerAir>0) epochs.lastOrNull()?.put("gasPerAir",closingGasPerAir)
        epochs.lastOrNull()?.put("closedAt",timestamp)
        epochs.add(JSONObject().put("id",id).put("target",target).put("confirmedAt",timestamp)
            .put("km",0.0).put("levelDrop",0.0).put("levelObserved",false).put("gasPerAir",JSONObject.NULL).put("active",true).put("refuelObserved",false))
        while(epochs.size>32) epochs.removeAt(0)
        lastAt=null;lastDistance=null;lastPosition=null;lastGas=false;segmentStart=null;lowestPosition=null
        save();return true
    }
    @Synchronized fun bindEpoch(token: String) { epochs.lastOrNull()?.put("ledgerEpoch",token);save() }
    @Synchronized fun observe(gasPerAir: Double?, distanceKm: Double?, position: Double?, gasActive: Boolean, timestamp: Long = System.currentTimeMillis(), ledgerEpoch: String? = null) {
        val e=epochs.lastOrNull()?.takeIf { it.optBoolean("active") } ?: return
        if(ledgerEpoch!=null && e.has("ledgerEpoch") && e.optString("ledgerEpoch")!=ledgerEpoch){interrupt("EQUIVALENCE_EPOCH_CHANGED");return}
        if (gasActive && gasPerAir != null && gasPerAir.isFinite() && gasPerAir > 0) e.put("gasPerAir",gasPerAir)
        val distance=distanceKm?.takeIf { it.isFinite() && it >= 0 }
        val level=position?.takeIf { it.isFinite() && it in 0.0..100.0 }
        if(level!=null)e.put("levelObserved",true)
        val continuous=gasActive && lastGas && lastAt!=null && timestamp-lastAt!! in 0L..15_000L
        if (continuous) {
            val priorDistance=lastDistance
            if(distance!=null && priorDistance!=null && distance >= priorDistance) e.put("km",e.optDouble("km")+distance-priorDistance)
            val priorLevel=lastPosition
            if(level!=null && priorLevel!=null) {
                if(level - (lowestPosition ?: priorLevel)>5) e.put("refuelObserved",true)
                else if(!e.optBoolean("refuelObserved")) e.put("levelDrop",maxOf(e.optDouble("levelDrop"),segmentBaseDrop+((segmentStart ?: priorLevel)-level).coerceAtLeast(0.0)))
                lowestPosition=minOf(lowestPosition ?: level, level)
            }
        }
        if (!continuous || level == null) { segmentStart=if(gasActive) level else null;segmentBaseDrop=e.optDouble("levelDrop");lowestPosition=if(gasActive) level else null }
        lastDistance=if(gasActive) distance else null
        lastPosition=if(gasActive) level else null
        lastGas=gasActive
        lastAt=timestamp
        if(System.currentTimeMillis()-lastSaveAt>=60_000L) save()
    }
    @Synchronized fun interrupt(reason: String) {
        epochs.lastOrNull()?.put("active",false)?.put("interrupted",reason)
        lastAt=null;lastDistance=null;lastPosition=null;lastGas=false;segmentStart=null;lowestPosition=null;save()
    }
    @Synchronized fun json(): JSONObject {
        fun decorated(e:JSONObject):JSONObject {
            val out=JSONObject(e.toString());val drop=e.optDouble("levelDrop",0.0)
            if(!e.optBoolean("levelObserved"))out.put("levelDrop",JSONObject.NULL)
            out.put("kmPerStep",if(drop>=25 && !e.optBoolean("refuelObserved")) e.optDouble("km")/(drop/25) else JSONObject.NULL)
            return out
        }
        val current=epochs.lastOrNull(); val previous=epochs.dropLast(1).lastOrNull()
        val a=current?.optDouble("gasPerAir",Double.NaN);val b=previous?.optDouble("gasPerAir",Double.NaN)
        val enough=current!=null && previous!=null && current.optDouble("km")>=MIN_KM && previous.optDouble("km")>=MIN_KM && current.optBoolean("active") && !previous.has("interrupted")
        val delta=if(enough && a!=null && b!=null && a.isFinite() && b.isFinite() && b>0) (a/b-1)*100 else null
        return JSONObject().put("current",current?.let(::decorated) ?: JSONObject.NULL)
            .put("previous",previous?.let(::decorated) ?: JSONObject.NULL)
            .put("gasPerAirChangePercent",delta ?: JSONObject.NULL)
            .put("minimumKm",MIN_KM).put("comparisonState",if(delta!=null)"OBSERVATIONAL" else "INSUFFICIENT")
            .put("epochs",JSONArray(epochs.map(::decorated)))
            .put("interpretation","Índice observacional; não comprova economia nem litros. GPS somente nos trechos contínuos em GNV; degrau = 25 pontos de referência.")
    }
    @Synchronized fun save() {
        val destination=file ?: return
        try {
            destination.parentFile?.mkdirs();val tmp=File(destination.path+".tmp")
            tmp.writeText(JSONObject().put("epochs",JSONArray(epochs)).toString())
            if(!tmp.renameTo(destination)) tmp.delete()
            lastSaveAt=System.currentTimeMillis()
        } catch (_: Exception) { /* Observação continua sem bloquear telemetria. */ }
    }
}
