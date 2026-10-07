package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.StallWatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Experiência de dirigir: tremor de RPM e quase-apagões por hora, GNV contra gasolina **no mesmo MAP**
 * (célula de 0,02 bar da Curva Própria), nunca no ms cru: o GNV pobre "escorrega" de faixa de ms, mas o
 * motor o sente na mesma carga. Só observa.
 *
 * Tremor = RMS do resíduo da reta de mínimos quadrados rpm × t numa janela de [WINDOW_FRAMES] quadros do
 * mesmo combustível (a tendência sai: acelerar não é tremer). Quase-apagões da gasolina vêm de uma
 * StallWatch sombra (sem arquivo) que aplica o mesmo critério da do GNV sem tocar o núcleo dela.
 */
class ExperienceMeter(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-experience-meter-v1"
        const val WINDOW_FRAMES = 5
        const val WINDOW_MAX_MS = 2_000L
        const val CELL_CAP = 60
        const val MIN_WINDOWS = 6
        const val MIN_HOURS = 0.05
        const val MAX_FRAME_DT_MS = 1_000L
        const val POOL_CELLS = 1
        private const val SAVE_INTERVAL_MS = 60_000L
        private const val MS_PER_HOUR = 3_600_000.0
    }

    private class Lane {
        val rough = Array(OwnCurveFitter.GRID_CELLS) { ArrayList<Double>() }
        val hours = DoubleArray(OwnCurveFitter.GRID_CELLS)
        val near = IntArray(OwnCurveFitter.GRID_CELLS)
        fun clear() {
            rough.forEach { it.clear() }
            hours.fill(0.0)
            near.fill(0)
        }
    }

    private class WindowFrame(val t: Long, val rpm: Double, val map: Double)

    private val lock = Any()
    private val gnv = Lane()
    private val petrol = Lane()
    private val window = ArrayList<WindowFrame>()
    private var windowFuel = ""
    private var lastT: Long? = null
    private var lastFuel = ""
    private val shadow = StallWatch(null, clock)
    private var lastShadowEvent: JSONObject? = null
    private var lastGasEvent: JSONObject? = null
    private val revisionCounter = AtomicLong(0L)
    private var dirty = false
    private var lastSaveAt = 0L

    init {
        load()
    }

    fun revision(): Long = revisionCounter.get()

    /** Um quadro de telemetria (fuel = GASOLINA/GNV/…; outros combustíveis só quebram a janela). */
    fun accept(t: Long, fuel: String, rpm: Double, map: Double, petrolMs: Double) {
        synchronized(lock) {
            val dt = if (fuel != lastFuel) 0L else lastT?.let { (t - it).coerceIn(0L, MAX_FRAME_DT_MS) } ?: 0L
            lastT = t
            lastFuel = fuel
            if (fuel == "GASOLINA") {
                val event = shadow.accept(StallWatch.Frame(t, "GNV", rpm, map, petrolMs))
                if (event != null && event !== lastShadowEvent && countsAsNearStall(event)) {
                    lastShadowEvent = event
                    OwnCurveFitter.cellOf(event.optDouble("mapBar", Double.NaN))?.let { petrol.near[it]++ }
                    touch()
                }
            }
            val lane = when (fuel) {
                "GASOLINA" -> petrol
                "GNV" -> gnv
                else -> null
            }
            if (lane == null || rpm < EquivalenceLedger.DRIVING_MIN_RPM || map <= 0.0) {
                window.clear()
                return
            }
            val cell = OwnCurveFitter.cellOf(map)
            if (cell != null && dt > 0L) {
                lane.hours[cell] += dt / MS_PER_HOUR
                touch()
            }
            if (windowFuel != fuel || (window.isNotEmpty() && t - window.last().t > WINDOW_MAX_MS)) window.clear()
            windowFuel = fuel
            window += WindowFrame(t, rpm, map)
            if (window.size == WINDOW_FRAMES) {
                closeWindow(lane)
                window.clear()
            }
        }
        maybeSave(false)
    }

    private fun closeWindow(lane: Lane) {
        val first = window.first()
        val last = window.last()
        if (last.t - first.t > WINDOW_MAX_MS) return
        val maps = window.map { it.map }
        if (window.maxOf { it.map } - window.minOf { it.map } > EquivalenceLedger.STABLE_MAP_SPREAD) return
        val cell = OwnCurveFitter.cellOf(maps.sum() / maps.size) ?: return
        val x = window.map { (it.t - first.t) / 1000.0 }
        val y = window.map { it.rpm }
        val mx = x.average()
        val my = y.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in x.indices) {
            sxx += (x[i] - mx) * (x[i] - mx)
            sxy += (x[i] - mx) * (y[i] - my)
        }
        val slope = if (sxx > 0.0) sxy / sxx else 0.0
        var sq = 0.0
        for (i in x.indices) {
            val residual = y[i] - (my + slope * (x[i] - mx))
            sq += residual * residual
        }
        val bucket = lane.rough[cell]
        bucket += sqrt(sq / x.size)
        while (bucket.size > CELL_CAP) bucket.removeAt(0)
        touch()
    }

    private fun countsAsNearStall(event: JSONObject): Boolean {
        val kind = event.optString("kind")
        return kind == StallWatch.KIND_NEAR || kind == StallWatch.KIND_STALL
    }

    /** Evento do StallWatch principal (só GNV por construção). */
    fun onStall(event: JSONObject) {
        synchronized(lock) {
            if (!countsAsNearStall(event) || event === lastGasEvent) return
            lastGasEvent = event
            OwnCurveFitter.cellOf(event.optDouble("mapBar", Double.NaN))?.let { gnv.near[it]++ }
            touch()
        }
        maybeSave(false)
    }

    /** O GNV medido com a curva antiga sai; a gasolina fica. */
    @Suppress("UNUSED_PARAMETER")
    fun resetGas(reason: String) {
        synchronized(lock) {
            gnv.clear()
            if (windowFuel == "GNV") window.clear()
            touch()
        }
        maybeSave(true)
    }

    fun flush() = maybeSave(true)

    fun reading(): Reading = synchronized(lock) {
        Reading(
            Array(OwnCurveFitter.GRID_CELLS) { gnv.rough[it].toDoubleArray() },
            Array(OwnCurveFitter.GRID_CELLS) { petrol.rough[it].toDoubleArray() },
            gnv.hours.copyOf(), petrol.hours.copyOf(), gnv.near.copyOf(), petrol.near.copyOf(),
        )
    }

    /** Cópia imutável das leituras, com as razões GNV/gasolina já agrupadas nas células vizinhas. */
    class Reading internal constructor(
        private val gnvRough: Array<DoubleArray>,
        private val petrolRough: Array<DoubleArray>,
        private val gnvHours: DoubleArray,
        private val petrolHours: DoubleArray,
        private val gnvNear: IntArray,
        private val petrolNear: IntArray,
    ) {
        private fun span(cell: Int): IntRange = max(0, cell - POOL_CELLS)..minOf(OwnCurveFitter.GRID_CELLS - 1, cell + POOL_CELLS)

        private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

        /** Mediana do tremor GNV / mediana na gasolina (piso 1 rpm), células j±[POOL_CELLS], ≥ [MIN_WINDOWS] cada. */
        fun roughnessRatio(mapBar: Double): Double? {
            val cell = OwnCurveFitter.cellOf(mapBar) ?: return null
            val g = span(cell).flatMap { gnvRough[it].toList() }
            val p = span(cell).flatMap { petrolRough[it].toList() }
            if (g.size < MIN_WINDOWS || p.size < MIN_WINDOWS) return null
            return median(g) / max(median(p), 1.0)
        }

        /** ((nG+0,5)/hG) / ((nP+0,5)/hP) nas células vizinhas; horas ≥ [MIN_HOURS] cada. */
        fun nearStallRatio(mapBar: Double): Double? {
            val cell = OwnCurveFitter.cellOf(mapBar) ?: return null
            val hG = span(cell).sumOf { gnvHours[it] }
            val hP = span(cell).sumOf { petrolHours[it] }
            if (hG < MIN_HOURS || hP < MIN_HOURS) return null
            val nG = span(cell).sumOf { gnvNear[it] }
            val nP = span(cell).sumOf { petrolNear[it] }
            return ((nG + 0.5) / hG) / ((nP + 0.5) / hP)
        }
    }

    // ------------------------------------------------------------ persistência

    private fun touch() {
        dirty = true
        revisionCounter.incrementAndGet()
    }

    private fun laneJson(lane: Lane): JSONObject {
        val rough = JSONArray()
        for (cell in lane.rough) rough.put(JSONArray(cell.map { Math.round(it * 1_000.0) / 1_000.0 }))
        return JSONObject().put("rough", rough)
            .put("hours", JSONArray(lane.hours.toList()))
            .put("near", JSONArray(lane.near.toList()))
    }

    private fun maybeSave(force: Boolean) {
        val target = file ?: return
        val payload = synchronized(lock) {
            val now = clock()
            if (!dirty || (!force && now - lastSaveAt < SAVE_INTERVAL_MS)) return
            lastSaveAt = now
            dirty = false
            JSONObject().put("format", FORMAT).put("gnv", laneJson(gnv)).put("petrol", laneJson(petrol)).toString()
        }
        if (!JsonFiles.write(target, payload)) synchronized(lock) { dirty = true }
    }

    private fun loadLane(source: JSONObject?, lane: Lane) {
        if (source == null) return
        val rough = source.optJSONArray("rough")
        val hours = source.optJSONArray("hours")
        val near = source.optJSONArray("near")
        for (j in 0 until OwnCurveFitter.GRID_CELLS) {
            rough?.optJSONArray(j)?.let { row ->
                for (i in 0 until row.length()) {
                    val v = row.optDouble(i, Double.NaN)
                    if (v.isFinite() && v >= 0.0) lane.rough[j].add(v)
                }
                while (lane.rough[j].size > CELL_CAP) lane.rough[j].removeAt(0)
            }
            val h = hours?.optDouble(j, 0.0) ?: 0.0
            lane.hours[j] = if (h.isFinite() && h >= 0.0) h else 0.0
            lane.near[j] = max(0, near?.optInt(j, 0) ?: 0)
        }
    }

    private fun load() {
        val root = JsonFiles.read(file, FORMAT) ?: return
        try {
            loadLane(root.optJSONObject("gnv"), gnv)
            loadLane(root.optJSONObject("petrol"), petrol)
        } catch (_: Exception) {
            gnv.clear()
            petrol.clear()
        }
    }
}
