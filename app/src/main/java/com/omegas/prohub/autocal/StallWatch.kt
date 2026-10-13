package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Detector de "apagão" no GNV: o motor estava girando e o RPM despenca para ~0 com a
 * telemetria ainda viva (chave ligada), sem ter vindo de uma marcha lenta estável
 * (que é o desligar normal do carro). Registra ONDE aconteceu — Petrol Inj., MAP e RPM
 * logo antes — porque é ali que a curva ficou pobre (desaceleração, embreagem, quebra-molas).
 *
 * Também vê o "quase apagou" (tropeço): RPM < NEAR_RPM por pelo menos NEAR_MIN_MS e o motor volta sozinho.
 * Nas sessões reais do dono (corpus tests/fixtures/sessoes-uteis, 9,4 h de GNV): 13 apagões e 26 tropeços; 88% dos
 * tropeços tiveram o Petrol Inj. mínimo entre 1,5 e 3,5 ms e 10 vieram logo depois do corte (cutoff).
 * Desligar a chave na marcha lenta (o motor nunca passou de KEYOFF_MAX_RPM nos últimos segundos) não é apagão: no
 * corpus, 5 dos 18 "apagões" da regra antiga eram só isso.
 *
 * Só observa. Não grava na ECU e não muda o refino sozinho: alimenta a tela e a trava da baixa.
 */
class StallWatch(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-stall-watch-v1"
        const val RUNNING_RPM = 600.0
        const val DEAD_RPM = 300.0
        /** RPM < DEAD_RPM por este tempo, com telemetria chegando = apagou (não foi ruído). */
        const val CONFIRM_MS = 800L
        /** Janela antes da queda onde o motor tem que estar girando no GNV. */
        const val LOOKBACK_MS = 4_000L
        const val MAX_EVENTS = 50
        const val BIN_MS = 0.5
        /** Quase apagou: RPM abaixo disto, pelo menos NEAR_MIN_MS, e voltou a >= RUNNING_RPM sem chegar a apagar. */
        const val NEAR_RPM = 500.0
        const val NEAR_MIN_MS = 300L
        /** Se o motor não passou de 1000 rpm nos últimos segundos, o RPM a zero é a chave sendo desligada. */
        const val KEYOFF_MAX_RPM = 1_000.0
        const val KIND_STALL = "STALL"
        const val KIND_NEAR = "NEAR_STALL"
    }

    data class Frame(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double)

    private val lock = Any()
    private val recent = ArrayDeque<Frame>()
    private var dropAt: Long? = null
    private var dropContext: List<Frame> = emptyList()
    private var stalled = false
    private val events = ArrayList<JSONObject>()
    private val nearEvents = ArrayList<JSONObject>()
    private class Near(val at: Long, var minRpm: Double, val before: List<Frame>, val afterCutoff: Boolean)
    private var near: Near? = null

    init { load() }

    /** Alimenta um quadro de telemetria. Retorna o evento (kind STALL ou NEAR_STALL) quando confirmado. */
    fun accept(frame: Frame): JSONObject? {
        val event = synchronized(lock) {
            recent.addLast(frame)
            while (recent.isNotEmpty() && frame.t - recent.first().t > LOOKBACK_MS + CONFIRM_MS + 2_000L) recent.removeFirst()
            if (frame.rpm >= RUNNING_RPM) {
                val pending = near
                val wasStalled = stalled
                stalled = false; dropAt = null; near = null
                if (pending != null && !wasStalled && frame.t - pending.at >= NEAR_MIN_MS) return@synchronized recordNear(pending)
                return@synchronized null
            }
            if (stalled) return@synchronized null
            val before = recent.filter { it.t < frame.t && frame.t - it.t <= LOOKBACK_MS && it.rpm >= RUNNING_RPM }
            val gnv = before.isNotEmpty() && before.count { it.fuel == "GNV" } * 2 >= before.size
            // Nunca passou de KEYOFF_MAX_RPM nos últimos segundos: é a chave sendo desligada na lenta, não apagão nem tropeço.
            val revved = recent.any { it.t < frame.t && it.rpm >= KEYOFF_MAX_RPM }
            val tracking = near
            if (tracking != null) tracking.minRpm = minOf(tracking.minRpm, frame.rpm)
            else if (frame.rpm < NEAR_RPM && dropAt == null && gnv && revved) {
                near = Near(frame.t, frame.rpm, before, recent.any { it.t < frame.t && frame.t - it.t <= LOOKBACK_MS && it.fuel == "CUTOFF" })
            }
            if (frame.rpm >= DEAD_RPM) return@synchronized null
            val start = dropAt
            if (start == null) {
                if (!gnv || !revved) return@synchronized null
                dropAt = frame.t
                dropContext = before
                return@synchronized null
            }
            if (frame.t - start < CONFIRM_MS) return@synchronized null
            stalled = true
            dropAt = null
            near = null
            record(start, dropContext)
        }
        if (event != null) save()
        return event
    }

    private fun recordNear(n: Near): JSONObject {
        val last = n.before.filter { n.at - it.t <= 2_000L }.ifEmpty { n.before }
        val event = JSONObject()
            .put("kind", KIND_NEAR)
            .put("at", n.at)
            .put("rpmBefore", median(last.map { it.rpm }))
            .put("petrolMs", median(last.map { it.petrolMs }))
            .put("mapBar", median(last.map { it.map }))
            .put("minPetrolMs", last.minOf { it.petrolMs })
            .put("minRpm", n.minRpm)
            .put("afterCutoff", n.afterCutoff)
        nearEvents += event
        while (nearEvents.size > MAX_EVENTS) nearEvents.removeAt(0)
        return event
    }

    private fun median(values: List<Double>): Double = values.sorted().let { it[it.size / 2] }

    private fun record(at: Long, before: List<Frame>): JSONObject {
        val last = before.filter { at - it.t <= 2_000L }.ifEmpty { before }
        val event = JSONObject()
            .put("kind", KIND_STALL)
            .put("at", at)
            .put("rpmBefore", median(last.map { it.rpm }))
            .put("petrolMs", median(last.map { it.petrolMs }))
            .put("mapBar", median(last.map { it.map }))
            .put("minPetrolMs", last.minOf { it.petrolMs })
            .put("decelerating", last.size >= 2 && last.last().rpm < last.first().rpm - 150.0)
        events += event
        while (events.size > MAX_EVENTS) events.removeAt(0)
        return event
    }

    /** Eventos + agrupamento por faixa de Petrol Inj. (onde mais apaga). */
    fun json(): JSONObject = synchronized(lock) {
        val bins = events.groupBy { kotlin.math.floor(it.optDouble("petrolMs") / BIN_MS) * BIN_MS }
            .map { (from, list) ->
                JSONObject().put("fromMs", from).put("toMs", from + BIN_MS).put("count", list.size)
                    .put("mapBar", median(list.map { it.optDouble("mapBar") }))
                    .put("rpmBefore", median(list.map { it.optDouble("rpmBefore") }))
                    .put("rpmLow", list.minOf { it.optDouble("rpmBefore") })
                    .put("rpmHigh", list.maxOf { it.optDouble("rpmBefore") })
            }.sortedByDescending { it.optInt("count") }
        val nearBins = nearEvents.groupBy { kotlin.math.floor(it.optDouble("minPetrolMs") / BIN_MS) * BIN_MS }
            .map { (from, list) ->
                JSONObject().put("fromMs", from).put("toMs", from + BIN_MS).put("count", list.size)
                    .put("mapBar", median(list.map { it.optDouble("mapBar") }))
                    .put("rpmBefore", median(list.map { it.optDouble("rpmBefore") }))
                    .put("rpmLow", list.minOf { it.optDouble("rpmBefore") })
                    .put("rpmHigh", list.maxOf { it.optDouble("rpmBefore") })
                    .put("afterCutoff", list.count { it.optBoolean("afterCutoff") })
            }.sortedByDescending { it.optInt("count") }
        JSONObject().put("count", events.size)
            .put("events", JSONArray(events.takeLast(10).map { JSONObject(it.toString()) }))
            .put("regions", JSONArray(bins))
            .put("nearCount", nearEvents.size)
            .put("nearEvents", JSONArray(nearEvents.takeLast(10).map { JSONObject(it.toString()) }))
            .put("nearRegions", JSONArray(nearBins))
    }

    private fun save() {
        val target = file ?: return
        val payload = synchronized(lock) { JSONObject().put("format", FORMAT).put("events", JSONArray(events.map { JSONObject(it.toString()) })).put("nearEvents", JSONArray(nearEvents.map { JSONObject(it.toString()) })) }
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) { target.writeText(payload.toString()); tmp.delete() }
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.optString("format") != FORMAT) return
            root.optJSONArray("events")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(events::add) }
            root.optJSONArray("nearEvents")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(nearEvents::add) }
        } catch (_: Exception) {
            events.clear(); nearEvents.clear()
        }
    }
}
