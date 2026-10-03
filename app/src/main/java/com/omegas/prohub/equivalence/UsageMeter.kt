package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/**
 * Onde o dono passa o tempo: ms de condução (rpm ≥ 1000, injetando gasolina ou GNV) por célula de MAP de
 * 0,02 bar, nas últimas [EquivalenceTolerances.USAGE_SESSIONS] sessões. O índice pesa cada ponto da Curva K
 * por este uso. Só observa.
 */
class UsageMeter(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-usage-meter-v1"
        const val MAX_FRAME_DT_MS = 1_000L
        private const val SAVE_INTERVAL_MS = 60_000L
    }

    private val lock = Any()
    /** Uma linha por sessão (a mais antiga primeiro); cada linha soma ms por célula da grade. */
    private val sessions = ArrayList<DoubleArray>()
    /** Id externo (conexão USB) da sessão aberta; não persiste: após reiniciar o app o primeiro quadro abre sessão nova. */
    private var openExternalId: Long? = null
    private var lastT: Long? = null
    private val revisionCounter = AtomicLong(0L)
    private var dirty = false
    private var lastSaveAt = 0L

    init {
        load()
    }

    fun revision(): Long = revisionCounter.get()

    /** Um quadro. [sessionId] novo abre sessão; passa de [EquivalenceTolerances.USAGE_SESSIONS] sai a mais antiga. */
    fun accept(t: Long, fuel: String, rpm: Double, map: Double, sessionId: Long) {
        synchronized(lock) {
            if (openExternalId != sessionId || sessions.isEmpty()) {
                openExternalId = sessionId
                lastT = null
                sessions += DoubleArray(OwnCurveFitter.GRID_CELLS)
                while (sessions.size > EquivalenceTolerances.USAGE_SESSIONS) sessions.removeAt(0)
                dirty = true
                revisionCounter.incrementAndGet()
            }
            val dt = lastT?.let { min((t - it).coerceAtLeast(0L), MAX_FRAME_DT_MS) } ?: 0L
            lastT = t
            if ((fuel != "GASOLINA" && fuel != "GNV") || rpm < EquivalenceLedger.DRIVING_MIN_RPM || map <= 0.0 || dt <= 0L) return
            val cell = OwnCurveFitter.cellOf(map) ?: return
            sessions.last()[cell] += dt.toDouble()
            dirty = true
            revisionCounter.incrementAndGet()
        }
        maybeSave(false)
    }

    fun flush() = maybeSave(true)

    fun reading(): Reading = synchronized(lock) {
        val sum = DoubleArray(OwnCurveFitter.GRID_CELLS)
        for (row in sessions) for (j in row.indices) sum[j] += row[j]
        Reading(sum)
    }

    class Reading internal constructor(val cellMs: DoubleArray) {
        /**
         * Fração do tempo de condução por ponto da Curva K: cada célula vai ao ponto de eixo mais próximo (em
         * ln ms) do ms que a Curva Própria da gasolina pede ali. Célula sem valor na curva não conta; o
         * denominador é só o tempo atribuível, então soma 1,0 (ou tudo 0 se nada é atribuível).
         */
        fun byPoint(axisMs: List<Double>, ownPetrol: OwnCurve): List<Double> {
            val out = DoubleArray(axisMs.size)
            var total = 0.0
            for (j in cellMs.indices) {
                val ms = cellMs[j]
                if (ms <= 0.0) continue
                val t = ownPetrol.at(OwnCurveFitter.center(j)) ?: continue
                if (t <= 0.0 || axisMs.isEmpty()) continue
                var best = 0
                var bestDistance = Double.MAX_VALUE
                for (i in axisMs.indices) {
                    val distance = abs(ln(t) - ln(axisMs[i]))
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = i
                    }
                }
                out[best] += ms
                total += ms
            }
            return if (total > 0.0) out.map { it / total } else out.toList()
        }
    }

    // ------------------------------------------------------------ persistência

    private fun maybeSave(force: Boolean) {
        val target = file ?: return
        val payload = synchronized(lock) {
            val now = clock()
            if (!dirty || (!force && now - lastSaveAt < SAVE_INTERVAL_MS)) return
            lastSaveAt = now
            dirty = false
            val rows = JSONArray()
            for (row in sessions) rows.put(JSONArray(row.map { Math.round(it) }))
            JSONObject().put("format", FORMAT).put("sessions", rows).toString()
        }
        if (!JsonFiles.write(target, payload)) synchronized(lock) { dirty = true }
    }

    private fun load() {
        val root = JsonFiles.read(file, FORMAT) ?: return
        try {
            val rows = root.optJSONArray("sessions") ?: return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                val cells = DoubleArray(OwnCurveFitter.GRID_CELLS)
                for (j in 0 until minOf(row.length(), cells.size)) {
                    val v = row.optDouble(j, 0.0)
                    cells[j] = if (v.isFinite() && v >= 0.0) v else 0.0
                }
                sessions += cells
            }
            while (sessions.size > EquivalenceTolerances.USAGE_SESSIONS) sessions.removeAt(0)
        } catch (_: Exception) {
            sessions.clear()
        }
    }
}
