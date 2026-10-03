package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * A Referência: a curva de gasolina que a ECU tinha quando o dono tocou "Congelar". A ECU pode reaprender
 * depois (zerar e readquirir); a Referência congelada não muda. Só o dono congela; o anterior fica em
 * memória até o fim da sessão USB como foto do Desfazer. Nada aqui fala com a ECU.
 */
class ReferenceStore(private val file: File?) {
    companion object {
        const val FORMAT = "omegas-reference-v1"
        const val PROVISIONAL_ID = "PROVISORIO"

        /**
         * Pontos de gasolina que a ECU deu como adquiridos (ZONA_ADQUIRIDA, não "anterior"), limpos como o
         * livro limpa a curva da ECU. Lista vazia = aquisição imatura (poucos pontos ou faixa curta demais).
         */
        fun pointsFrom(acquisition: JSONObject?): List<RefPoint> {
            val array = acquisition?.optJSONArray("points") ?: return emptyList()
            val raw = ArrayList<RefPoint>()
            for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                if (p.optString("fuel") != "GASOLINA" || p.optBoolean("previous")) continue
                if (p.optString("state") != "ZONA_ADQUIRIDA") continue
                if (p.isNull("timeMs") || p.isNull("mapBar")) continue
                val ms = p.optDouble("timeMs", Double.NaN)
                val map = p.optDouble("mapBar", Double.NaN)
                if (!ms.isFinite() || !map.isFinite() || ms <= 0.0 || map <= 0.0) continue
                val counter = if (p.isNull("counter")) 0 else p.optInt("counter", 0)
                raw += RefPoint(map, ms, counter)
            }
            val clean = raw.filter { it.mapBar in 0.05..2.5 && it.petrolMs in EquivalenceLedger.MIN_PETROL_MS..40.0 }
            val grouped = clean.groupBy { floor(it.mapBar * 1_000 + 0.5).toLong() }
                .values
                .map { g -> RefPoint(g.sumOf { it.mapBar } / g.size, g.sumOf { it.petrolMs } / g.size, g.maxOf { it.maturity }) }
                .sortedBy { it.mapBar }
            val usable = grouped.size >= EquivalenceLedger.ECU_REF_MIN_POINTS &&
                grouped.last().mapBar - grouped.first().mapBar >= EquivalenceLedger.ECU_REF_MIN_SPAN_BAR
            return if (usable) grouped else emptyList()
        }

        fun fingerprint(points: List<RefPoint>): String =
            JSONArray(points.map { "${it.mapBar}:${it.petrolMs}:${it.maturity}" }).toString().hashCode().toString(16)
    }

    private val lock = Any()
    private var current: Reference? = null
    private var previous: Reference? = null

    init {
        load()
    }

    fun current(): Reference? = synchronized(lock) { current }

    /** Congela a gasolina madura da ECU como Referência. Aquisição imatura: erro legível, nada muda. */
    fun freeze(acquisition: JSONObject, now: Long): Reference = synchronized(lock) {
        val points = pointsFrom(acquisition)
        if (points.isEmpty()) throw IllegalStateException("AQUISICAO_IMATURA")
        val fresh = Reference("REF-$now", now, fingerprint(points), points)
        previous = current
        current = fresh
        save()
        fresh
    }

    /** A Referência anterior desta sessão (só memória). */
    fun previous(): Reference? = synchronized(lock) { previous }

    /** Desfazer: troca atual e anterior. Devolve o novo atual (nulo, e nada muda, sem anterior). */
    fun restorePrevious(): Reference? = synchronized(lock) {
        val back = previous
        if (back != null) {
            previous = current
            current = back
            save()
        }
        back
    }

    fun endSession() {
        synchronized(lock) { previous = null }
    }

    /**
     * Quanto a ECU ao vivo se afastou da Referência: maior |ao_vivo(m)/ref(m) − 1| nos MAP da Referência
     * dentro da faixa viva. Nulo sem Referência, com aquisição viva imatura ou sem sobreposição.
     */
    fun ecuDrift(acquisition: JSONObject?): Double? {
        val ref = synchronized(lock) { current } ?: return null
        val live = pointsFrom(acquisition)
        if (live.isEmpty()) return null
        val maps = live.map { it.mapBar }
        val values = live.map { it.petrolMs }
        var worst: Double? = null
        for (p in ref.points) {
            if (p.mapBar < maps.first() || p.mapBar > maps.last()) continue
            val drift = abs(AutoMatchRefinedEngine.interp(p.mapBar, maps, values) / p.petrolMs - 1.0)
            val so = worst
            worst = if (so == null) drift else max(so, drift)
        }
        return worst
    }

    /** Referência provisória: a gasolina da ECU ao vivo. Nunca persiste; nula se imatura. */
    fun provisional(acquisition: JSONObject?): Reference? {
        val points = pointsFrom(acquisition)
        if (points.isEmpty()) return null
        return Reference(PROVISIONAL_ID, 0L, fingerprint(points), points)
    }

    private fun save() {
        val now = current ?: return
        val rows = JSONArray()
        for (p in now.points) rows.put(JSONArray().put(p.mapBar).put(p.petrolMs).put(p.maturity))
        val root = JSONObject().put("format", FORMAT).put(
            "current",
            JSONObject().put("id", now.id).put("frozenAt", now.frozenAt)
                .put("ecuAcquisitionFingerprint", now.ecuAcquisitionFingerprint)
                .put("points", rows),
        )
        JsonFiles.write(file, root.toString())
    }

    private fun load() {
        val root = JsonFiles.read(file, FORMAT) ?: return
        try {
            val c = root.optJSONObject("current") ?: return
            val array = c.optJSONArray("points") ?: return
            val points = ArrayList<RefPoint>()
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: return
                points += RefPoint(row.getDouble(0), row.getDouble(1), row.getInt(2))
            }
            if (points.isEmpty()) return
            current = Reference(c.getString("id"), c.optLong("frozenAt", 0L), c.optString("ecuAcquisitionFingerprint"), points)
        } catch (_: Exception) {
            current = null
        }
    }
}
