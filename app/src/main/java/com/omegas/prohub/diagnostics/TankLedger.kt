package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Livro-caixa de tanques (`omegas-tank-ledger-v1`): arquivo pequeno e cumulativo. Guarda os SEGMENTOS de cada resumo
 * fechado (idempotente pela identidade da sessão) e recalcula os ciclos a cada gravação: um ciclo começa no primeiro
 * dado ou num abastecimento (nível bruto cai >= 40 entre sessões, ou dentro da sessão). O nível do cilindro é INVERTIDO
 * (255 = vazio). "km por unidade de nível" e "minutos de injetor por 100 de nível" são PROXY e só saem com dados
 * suficientes. Desconhecido é null, nunca 0. Puro: sem Android.
 */
class TankLedger(private val file: File) {
    companion object {
        const val SCHEMA = "omegas-tank-ledger-v1"
        /** Menos que isto de nível consumido no ciclo não sustenta uma razão (ruído do sensor). */
        const val MIN_LEVEL_UNITS = 20
        private val KM_SOURCES = listOf(SpeedSource.MM, SpeedSource.GPS)
    }

    private val sessions = LinkedHashMap<String, JSONObject>()

    init {
        try {
            if (file.isFile) {
                val saved = JSONObject(file.readText(Charsets.UTF_8))
                if (saved.optString("schema") == SCHEMA) {
                    val stored = saved.optJSONObject("sessions") ?: JSONObject()
                    for (key in stored.keys()) stored.optJSONObject(key)?.let { sessions[key] = it }
                }
            }
        } catch (_: Exception) {
            sessions.clear()
        }
    }

    /** Guarda (ou substitui) o resumo da sessão e regrava o arquivo. Só chame com resumo FECHADO. */
    @Synchronized
    fun record(summary: JSONObject): JSONObject {
        val identity = summary.optJSONObject("identity") ?: return json()
        val id = identity.optString("sessionId")
        val uuid = identity.optString("sessionUuid")
        val key = uuid.ifBlank { id }
        if (key.isBlank()) return json()
        sessions[key] = entryOf(summary, identity, id, uuid)
        val out = json()
        SessionSummaryFiles.atomicWrite(file, out.toString(2).toByteArray(Charsets.UTF_8))
        return out
    }

    private fun entryOf(summary: JSONObject, identity: JSONObject, id: String, uuid: String) = JSONObject()
        .put("sessionId", id).put("sessionUuid", uuid)
        .put("startMs", identity.opt("startMs") ?: JSONObject.NULL)
        .put("clockSuspect", summary.optJSONObject("clock")?.optBoolean("clockSuspect") ?: false)
        .put("closedCleanly", summary.opt("closedCleanly") ?: JSONObject.NULL)
        .put("segments", JSONArray(summary.optJSONArray("tankSegments")?.toString() ?: "[]"))

    /** O livro-caixa COMO FICARIA com este resumo (parcial ou final), sem gravar nada: base do comparativo do pacote. */
    @Synchronized
    fun jsonIncluding(summary: JSONObject): JSONObject {
        val identity = summary.optJSONObject("identity") ?: return json()
        val id = identity.optString("sessionId")
        val uuid = identity.optString("sessionUuid")
        val key = uuid.ifBlank { id }
        if (key.isBlank()) return json()
        val saved = LinkedHashMap(sessions)
        sessions[key] = entryOf(summary, identity, id, uuid)
        return try { json() } finally { sessions.clear(); sessions.putAll(saved) }
    }

    /** Último nível bruto conhecido (da sessão mais recente com nível), ou null. */
    @Synchronized
    fun lastEndRaw(): Int? = json().let { if (it.isNull("lastEndRaw")) null else it.optInt("lastEndRaw") }

    @Synchronized
    fun json(): JSONObject {
        val ordered = sessions.values.sortedWith(compareBy({ it.optLong("startMs", Long.MAX_VALUE) }, { it.optString("sessionId") }))
        val cycles = ArrayList<Cycle>()
        var prevEnd: Int? = null
        var current: Cycle? = null
        ordered.forEach { s ->
            val segments = s.optJSONArray("segments") ?: JSONArray()
            var touched: Cycle? = null
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONObject(i)
                val startRaw = seg.optIntOrNull("startRaw")
                val startedBy = when {
                    current == null -> "FIRST_SESSION"
                    i > 0 -> "REFUEL_IN_SESSION"
                    prevEnd != null && startRaw != null && prevEnd!! - startRaw >= SessionSummaryBuilder.REFUEL_DROP_RAW -> "REFUEL_BETWEEN_SESSIONS"
                    else -> null
                }
                if (startedBy != null) {
                    current = Cycle(cycles.size, startedBy, s.optString("sessionId"))
                    cycles.add(current!!)
                }
                val c = current!!
                c.add(s, seg, withSession = touched !== c)
                touched = c
                seg.optIntOrNull("endRaw")?.let { prevEnd = it }
            }
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("sessionCount", sessions.size)
            .put("lastEndRaw", prevEnd ?: JSONObject.NULL)
            .put("levelOrientation", "INVERTED_255_EMPTY")
            .put("cycles", JSONArray().also { arr -> cycles.forEachIndexed { i, c -> arr.put(c.json(open = i == cycles.lastIndex)) } })
            .put("sessions", JSONObject().also { o -> sessions.forEach { (k, v) -> o.put(k, v) } })
            .put("note", "Ciclo = entre abastecimentos. PROXY = estimativa; so com nivel consumido >= $MIN_LEVEL_UNITS. Reprocessar a mesma sessao nao duplica.")
    }

    private class Cycle(val index: Int, val startedBy: String, val firstSession: String) {
        var lastSession = firstSession
        val sessionIds = LinkedHashSet<String>()
        /** (sessão, índice do segmento de tanque) que entraram neste ciclo: liga mudança de mapa/curva ao tanque. */
        val segmentRefs = ArrayList<Pair<String, Int>>()
        var startRaw: Int? = null
        var lastRaw: Int? = null
        val km = KM_SOURCES.associateWith { Acc() }
        val kmGnv = KM_SOURCES.associateWith { Acc() }
        val moving = SpeedSource.ALL.associateWith { Acc() }
        val stopped = SpeedSource.ALL.associateWith { Acc() }
        val gnvMs = Acc()
        val injector = Acc()
        var withMM = 0
        var withGPS = 0
        var withLevel = 0
        var uncertain = false
        private val mmSessions = HashSet<String>()
        private val gpsSessions = HashSet<String>()
        private val levelSessions = HashSet<String>()

        class Acc { var sum = 0.0; var seen = false; fun add(v: Double?) { if (v != null) { sum += v; seen = true } }; fun value(): Double? = if (seen) sum else null }

        fun add(session: JSONObject, seg: JSONObject, withSession: Boolean) {
            val sid = session.optString("sessionId")
            lastSession = sid
            sessionIds.add(sid)
            segmentRefs.add(sid to seg.optInt("index", 0))
            if (session.optBoolean("clockSuspect")) uncertain = true
            if (startRaw == null) startRaw = seg.optIntOrNull("startRaw")
            seg.optIntOrNull("endRaw")?.let { lastRaw = it; levelSessions.add(sid) }
            KM_SOURCES.forEach { src ->
                val km = seg.optJSONObject("km")?.optDoubleOrNull(src)
                km.let { km2 -> this.km.getValue(src).add(km2) }
                kmGnv.getValue(src).add(seg.optJSONObject("kmOnGnv")?.optDoubleOrNull(src))
                if (km != null) (if (src == SpeedSource.MM) mmSessions else gpsSessions).add(sid)
            }
            SpeedSource.ALL.forEach { src ->
                moving.getValue(src).add(seg.optJSONObject("movingMs")?.optDoubleOrNull(src))
                stopped.getValue(src).add(seg.optJSONObject("stoppedMs")?.optDoubleOrNull(src))
            }
            gnvMs.add(seg.optDoubleOrNull("gnvMs"))
            injector.add(seg.optDoubleOrNull("injectorOpenMinutesGnv"))
        }

        private fun per(map: Map<String, Acc>, digits: Int) = JSONObject().also { o -> map.forEach { (k, v) -> o.put(k, Totals.nullable(v.value(), digits)) } }

        fun json(open: Boolean): JSONObject {
            val consumed = if (startRaw != null && lastRaw != null && lastRaw!! >= startRaw!!) lastRaw!! - startRaw!! else null
            val enough = consumed != null && consumed >= MIN_LEVEL_UNITS
            val kmSrc = KM_SOURCES.firstOrNull { (kmGnv.getValue(it).value() ?: 0.0) > 0.0 }
            val kmPerUnit: Any = if (enough && kmSrc != null) {
                JSONObject().put("value", Totals.nullable(kmGnv.getValue(kmSrc).sum / consumed!!, 5)).put("source", kmSrc)
                    .put("label", "PROXY").put("unit", "km no GNV por unidade de nivel bruto")
            } else JSONObject.NULL
            val injPer100: Any = if (enough && injector.value() != null) {
                JSONObject().put("value", Totals.nullable(injector.sum / consumed!! * 100.0, 4)).put("label", "PROXY")
                    .put("unit", "minutos de injetor GNV aberto por 100 de nivel bruto")
            } else JSONObject.NULL
            val gnvPer100: Any = if (enough && gnvMs.value() != null) {
                JSONObject().put("value", Totals.nullable(gnvMs.sum / 60_000.0 / consumed!! * 100.0, 3)).put("label", "PROXY")
                    .put("unit", "minutos rodando em GNV por 100 de nivel bruto")
            } else JSONObject.NULL
            return JSONObject()
                .put("index", index).put("startedBy", startedBy).put("open", open)
                .put("firstSessionId", firstSession).put("lastSessionId", lastSession)
                .put("sessions", sessionIds.size)
                .put("sessionIds", JSONArray(sessionIds.toList()))
                .put("segmentRefs", JSONArray(segmentRefs.map { JSONObject().put("sessionId", it.first).put("segmentIndex", it.second) }))
                .put("startRaw", startRaw ?: JSONObject.NULL).put("lastRaw", lastRaw ?: JSONObject.NULL)
                .put("levelConsumedRaw", consumed ?: JSONObject.NULL)
                .put("enoughData", enough)
                .put("km", per(km, 4)).put("kmOnGnv", per(kmGnv, 4))
                .put("movingMs", per(moving, 1)).put("stoppedMs", per(stopped, 1))
                .put("gnvMs", Totals.nullable(gnvMs.value(), 1))
                .put("injectorOpenMinutesGnv", Totals.nullable(injector.value(), 4))
                .put("kmPerRawUnit", kmPerUnit)
                .put("injectorMinutesPer100Level", injPer100)
                .put("gnvMinutesPer100Level", gnvPer100)
                .put(
                    "coverage",
                    JSONObject().put("sessions", sessionIds.size).put("withMM", mmSessions.size)
                        .put("withGPS", gpsSessions.size).put("withLevel", levelSessions.size),
                )
                .put("orderUncertain", uncertain)
        }
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
