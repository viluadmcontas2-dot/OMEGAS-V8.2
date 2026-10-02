package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.Mp48TelemetryScale
import kotlin.math.roundToInt

/** Posição relativa, nunca volume. Perfil incompleto/sem prova usa o fallback rotulado. */
class LevelEstimator(private val config: Config = Config()) {
    enum class Direction { INCREASING, DECREASING }
    data class Config(
        val references: List<Int> = emptyList(),
        val fullRaw: Int? = null,
        val sensorType: Int? = null,
        val direction: Direction? = null,
        val fast: Double? = null,
        val slow: Double? = null,
        val ledParameters: List<Int> = emptyList(),
        val filterPolicyVerified: Boolean = false,
        val ledPolicyVerified: Boolean = false,
    )
    data class Reading(val filteredRaw: Double, val percent: Int, val position: Double, val leds: Int?, val proxy: Boolean)
    private var filtered: Double? = null
    private var previousLeds: Int? = null
    private val anchors = config.references + listOfNotNull(config.fullRaw)
    private val geometryKnown = config.sensorType != null && anchors.size == 5 && anchors.all { it in 0..255 } &&
        when(config.direction) {
            Direction.INCREASING -> anchors.zipWithNext().all { (a,b) -> a < b }
            Direction.DECREASING -> anchors.zipWithNext().all { (a,b) -> a > b }
            null -> false
        }
    private val thresholds = config.ledParameters.drop(1).takeIf { it.size == 4 && it.all { n -> n in 0..100 } && it.zipWithNext().all { (a,b) -> a < b } }
    @Synchronized fun accept(raw: Int, fastMode: Boolean? = null): Reading? {
        if(raw !in 0..255) return null
        val alpha=(if(fastMode==true)config.fast else config.slow)?.takeIf { it.isFinite() && it in 0.0..1.0 }
        // Ler coeficientes não prova a equação temporal ou o predicado FAST/SLOW.
        // O leitor não habilita esta política enquanto a prova não existir.
        val verified=geometryKnown && config.filterPolicyVerified && fastMode!=null && alpha!=null
        if(!verified) {
            filtered=null;previousLeds=null
            val percentage=Mp48TelemetryScale.levelPercentage(raw)
            return Reading(raw.toDouble(),percentage,percentage.toDouble(),null,true)
        }
        val value=filtered?.let { it + alpha!! * (raw-it) } ?: raw.toDouble()
        filtered=value
        val position=interpolate(value)
        val leds=if(config.ledPolicyVerified && thresholds!=null && config.ledParameters.first() in 0..100) {
            var n=previousLeds ?: thresholds.count { position>=it }
            while(n>0 && position<thresholds[n-1])n--
            while(n<thresholds.size && position>=thresholds[n]+config.ledParameters.first())n++
            previousLeds=n;n
        } else null
        return Reading(value,position.roundToInt().coerceIn(0,100),position,leds,false)
    }
    private fun interpolate(raw: Double): Double {
        for(i in 0..3){val a=anchors[i].toDouble();val b=anchors[i+1].toDouble()
            if(raw>=minOf(a,b)&&raw<=maxOf(a,b))return i*25.0+(raw-a)/(b-a)*25.0}
        return if(config.direction==Direction.INCREASING && raw<anchors.first() || config.direction==Direction.DECREASING && raw>anchors.first())0.0 else 100.0
    }
}
