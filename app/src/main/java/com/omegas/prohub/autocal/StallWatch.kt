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
    }

    data class Frame(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double)

    private val lock = Any()
    private val recent = ArrayDeque<Frame>()
    private var dropAt: Long? = null
    private var dropContext: List<Frame> = emptyList()
    private var stalled = false
    private val events = ArrayList<JSONObject>()

    init { load() }

    /** Alimenta um quadro de telemetria. Retorna o evento quando um apagão é confirmado. */
    fun accept(frame: Frame): JSONObject? {
        val event = synchronized(lock) {
            recent.addLast(frame)
            while (recent.isNotEmpty() && frame.t - recent.first().t > LOOKBACK_MS + CONFIRM_MS + 2_000L) recent.removeFirst()
            if (frame.rpm >= RUNNING_RPM) { stalled = false; dropAt = null; return@synchronized null }
            if (stalled || frame.rpm >= DEAD_RPM) return@synchronized null
            val start = dropAt
            if (start == null) {
                val before = recent.filter { it.t < frame.t && frame.t - it.t <= LOOKBACK_MS && it.rpm >= RUNNING_RPM }
                if (before.isEmpty() || before.count { it.fuel == "GNV" } * 2 < before.size) return@synchronized null
                if (steadyIdle(before)) return@synchronized null // desligou na lenta: não é apagão
                dropAt = frame.t
                dropContext = before
                return@synchronized null
            }
            if (frame.t - start < CONFIRM_MS) return@synchronized null
            stalled = true
            dropAt = null
            record(start, dropContext)
        }
        if (event != null) save()
        return event
    }

    /** Marcha lenta estável por ~3 s antes de cair = motorista desligou o carro. */
    private fun steadyIdle(before: List<Frame>): Boolean {
        if (before.size < 4) return false
        val span = before.last().t - before.first().t
        val rpmSpread = before.maxOf { it.rpm } - before.minOf { it.rpm }
        return span >= 2_500L && rpmSpread < 150.0 && before.all { it.rpm < 1_100.0 }
    }

    private fun median(values: List<Double>): Double = values.sorted().let { it[it.size / 2] }

    private fun record(at: Long, before: List<Frame>): JSONObject {
        val last = before.filter { at - it.t <= 2_000L }.ifEmpty { before }
        val event = JSONObject()
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
            }.sortedByDescending { it.optInt("count") }
        JSONObject().put("count", events.size)
            .put("events", JSONArray(events.takeLast(10).map { JSONObject(it.toString()) }))
            .put("regions", JSONArray(bins))
    }

    private fun save() {
        val target = file ?: return
        val payload = synchronized(lock) { JSONObject().put("format", FORMAT).put("events", JSONArray(events.map { JSONObject(it.toString()) })) }
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
        } catch (_: Exception) {
            events.clear()
        }
    }
}
