package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.JsonFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Detector de "apagão" no GNV. Uma queda brusca de RPM sozinha não basta: a decisão olha
 * o que acontece ANTES, DURANTE e DEPOIS da queda.
 *
 * ANTES (até [LOOKBACK_MS]): o motor girava (RPM ≥ [RUNNING_RPM]), o último combustível
 * conhecido era GNV (CUTOFF/TRANSIÇÃO não decidem) e não era marcha lenta estável por ~3 s
 * (isso é o motorista desligando o carro). Quando o GPS existe, a velocidade entra no evento.
 *
 * DURANTE: RPM abaixo de [DEAD_RPM] (a MP48 passa a reportar DESLIGADO) com a telemetria
 * continuando a chegar por [CONFIRM_MS]: chave ligada, motor morto. Se a telemetria para
 * antes disso (chave/cabo/app), não houve prova de motor morto: não vira apagão.
 *
 * DEPOIS: só ANOTA o que aconteceu, nunca apaga um apagão confirmado. Religou em até
 * [RESTART_WINDOW_MS] = "religou"; telemetria parou em seguida = "telemetria parou"; sem
 * religar = "sem religar" (e a velocidade do GPS, se houver, fica no evento).
 *
 * QUASE_APAGOU: o RPM despenca de condução (≥ [DRIVING_RPM] nos últimos 2 s) para abaixo de
 * [RUNNING_RPM] sem chegar a morrer. É o tranco de embreagem/quebra-molas visto nas sessões
 * reais; conta separado e, se o motor morrer em seguida, o mesmo evento vira APAGOU.
 *
 * Só observa. Não grava na ECU e não muda o refino sozinho: alimenta a tela e a trava da baixa.
 */
class StallWatch(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-stall-watch-v2"
        const val LEGACY_FORMAT = "omegas-stall-watch-v1"
        const val RUNNING_RPM = 600.0
        const val DEAD_RPM = 300.0
        /** Condução de verdade (acima da lenta) nos 2 s anteriores a uma queda brusca. */
        const val DRIVING_RPM = 1_000.0
        /** RPM < DEAD_RPM por este tempo, com telemetria chegando = apagou (não foi ruído). */
        const val CONFIRM_MS = 800L
        /** Janela antes da queda onde o motor tem que estar girando no GNV. */
        const val LOOKBACK_MS = 4_000L
        const val DIP_LOOKBACK_MS = 2_000L
        /** Telemetria calada por este tempo logo depois da queda = chave/cabo/app, não apagão. */
        const val GAP_MS = 5_000L
        /** Prazo para o motor religar na mesma sessão e confirmar "apagou e religou". */
        const val RESTART_WINDOW_MS = 60_000L
        /** Acima disso o GPS diz que o carro andava quando o motor morreu. */
        const val MOVING_KMH = 5.0
        const val MAX_EVENTS = 50
        const val BIN_MS = 0.5
        const val KIND_STALL = "APAGOU"
        const val KIND_NEAR = "QUASE_APAGOU"
        const val AFTER_RESTARTED = "RELIGOU"
        const val AFTER_TELEMETRY_STOPPED = "TELEMETRIA_PAROU"
        const val AFTER_NO_RESTART = "SEM_RELIGAR"
        private val KNOWN_FUELS = setOf("GASOLINA", "GNV")
    }

    data class Frame(
        val t: Long,
        val fuel: String,
        val rpm: Double,
        val map: Double,
        val petrolMs: Double,
        /** Velocidade do GPS no quadro, quando o GPS está ligado; null = desconhecida. */
        val speedKmh: Double? = null,
    )

    private val lock = Any()
    private val recent = ArrayDeque<Frame>()
    private var dipActive = false
    private var dropAt: Long? = null
    private var dropContext: List<Frame> = emptyList()
    /** Apagão confirmado esperando o motor religar (ou a sessão morrer). */
    private var openEvent: JSONObject? = null
    private var lastFrameAt = 0L
    private val events = ArrayList<JSONObject>()
    private var idleShutdowns = 0
    private var cutShutdowns = 0
    private val revisionCounter = AtomicLong(0L)
    /** O que veio depois de cada apagão confirmado, esperando o serviço gravar na sessão. */
    private val annotations = ArrayList<JSONObject>()

    init { load() }

    /** Entrega (uma vez) as anotações de "depois" para a sessão gravar como `engine_stall_after`. */
    fun drainAnnotations(): List<JSONObject> = synchronized(lock) {
        val copy = annotations.map { JSONObject(it.toString()) }
        annotations.clear()
        copy
    }

    /** Muda a cada evento novo, religada ou descarte: serve de chave de cache para a UI. */
    fun revision(): Long = revisionCounter.get()

    /** Alimenta um quadro de telemetria. Retorna o evento quando um apagão/quase-apagão é registrado. */
    fun accept(frame: Frame): JSONObject? {
        val event = synchronized(lock) {
            if (lastFrameAt > 0L && frame.t - lastFrameAt > GAP_MS) onTelemetryGap()
            recent.addLast(frame)
            while (recent.isNotEmpty() && frame.t - recent.first().t > LOOKBACK_MS + CONFIRM_MS + 2_000L) recent.removeFirst()
            lastFrameAt = frame.t
            if (frame.rpm >= RUNNING_RPM) {
                openEvent?.let { open ->
                    open.put("religou", true).put("depois", AFTER_RESTARTED)
                        .put("religouEmS", (frame.t - open.optLong("at")) / 1000.0)
                    annotations += JSONObject().put("at", open.optLong("at")).put("depois", AFTER_RESTARTED)
                        .put("religou", true).put("religouEmS", open.optDouble("religouEmS"))
                    openEvent = null
                    revisionCounter.incrementAndGet()
                }
                dipActive = false
                dropAt = null
                return@synchronized null
            }
            val before = recent.filter { it.t < frame.t && frame.t - it.t <= LOOKBACK_MS && it.rpm >= RUNNING_RPM }
            val fuelBefore = before.lastOrNull { it.fuel in KNOWN_FUELS }?.fuel
            val gnv = fuelBefore == "GNV"
            if (!dipActive && frame.rpm >= DEAD_RPM) {
                dipActive = true
                val driving = before.any { frame.t - it.t <= DIP_LOOKBACK_MS && it.rpm >= DRIVING_RPM }
                if (driving && gnv && !steadyIdle(before)) return@synchronized record(KIND_NEAR, frame.t, frame.rpm, before)
                return@synchronized null
            }
            if (frame.rpm >= DEAD_RPM) return@synchronized null
            dipActive = true
            val start = dropAt
            if (start == null) {
                if (before.isEmpty() || !gnv) return@synchronized null
                if (steadyIdle(before)) { idleShutdowns++; return@synchronized null } // desligou na lenta
                dropAt = frame.t
                dropContext = before
                return@synchronized null
            }
            if (openEvent != null || frame.t - start < CONFIRM_MS) return@synchronized null
            dropAt = null
            confirmStall(start, dropContext)
        }
        if (event != null) save()
        return event
    }

    /**
     * Relógio do serviço (a cada poucos segundos): decide o que uma queda sem telemetria nova
     * significa. Queda ainda sem confirmação + silêncio = chave desligada (não é apagão).
     * Apagão já confirmado só ganha a anotação do que veio depois; continua na conta.
     */
    fun tick(now: Long = clock()) {
        val changed = synchronized(lock) {
            var changed = false
            if (dropAt != null && lastFrameAt > 0L && now - lastFrameAt > GAP_MS) {
                dropAt = null
                cutShutdowns++
                changed = true
            }
            val open = openEvent
            if (open != null) {
                val silent = now - lastFrameAt > GAP_MS
                if (silent || now - open.optLong("at") > RESTART_WINDOW_MS) {
                    closeWithoutRestart(open, telemetryStopped = silent)
                    changed = true
                }
            }
            changed
        }
        if (changed) save()
    }

    private fun onTelemetryGap() {
        if (dropAt != null) { dropAt = null; cutShutdowns++ }
        openEvent?.let { closeWithoutRestart(it, telemetryStopped = true) }
        recent.clear()
    }

    private fun closeWithoutRestart(open: JSONObject, telemetryStopped: Boolean) {
        open.put("religou", false)
        open.put("depois", if (telemetryStopped) AFTER_TELEMETRY_STOPPED else AFTER_NO_RESTART)
        annotations += JSONObject().put("at", open.optLong("at"))
            .put("depois", if (telemetryStopped) AFTER_TELEMETRY_STOPPED else AFTER_NO_RESTART).put("religou", false)
        openEvent = null
        revisionCounter.incrementAndGet()
    }

    /** Marcha lenta estável por ~3 s antes de cair = motorista desligou o carro. */
    private fun steadyIdle(before: List<Frame>): Boolean {
        if (before.size < 4) return false
        val span = before.last().t - before.first().t
        val rpmSpread = before.maxOf { it.rpm } - before.minOf { it.rpm }
        return span >= 2_500L && rpmSpread < 150.0 && before.all { it.rpm < 1_100.0 }
    }

    private fun median(values: List<Double>): Double = values.sorted().let { it[it.size / 2] }

    private fun confirmStall(at: Long, before: List<Frame>): JSONObject {
        val previous = events.lastOrNull()
        val event = if (previous != null && previous.optString("kind") == KIND_NEAR && at - previous.optLong("at") <= GAP_MS) {
            // O quase-apagão virou apagão: mesmo evento, mais grave.
            fill(previous, KIND_STALL, at, 0.0, before)
        } else {
            record(KIND_STALL, at, 0.0, before)
        }
        event.put("religou", JSONObject.NULL)
        openEvent = event
        return event
    }

    private fun record(kind: String, at: Long, rpmMin: Double, before: List<Frame>): JSONObject {
        val event = fill(JSONObject(), kind, at, rpmMin, before)
        events += event
        while (events.size > MAX_EVENTS) events.removeAt(0)
        revisionCounter.incrementAndGet()
        return event
    }

    private fun fill(event: JSONObject, kind: String, at: Long, rpmMin: Double, before: List<Frame>): JSONObject {
        val last = before.filter { at - it.t <= DIP_LOOKBACK_MS }.ifEmpty { before }
        val speeds = last.mapNotNull { it.speedKmh }
        event.put("kind", kind)
            .put("at", at)
            .put("rpmBefore", median(last.map { it.rpm }))
            .put("rpmMin", rpmMin)
            .put("petrolMs", median(last.map { it.petrolMs }))
            .put("mapBar", median(last.map { it.map }))
            .put("minPetrolMs", last.minOf { it.petrolMs })
            .put("fuelBefore", last.lastOrNull { it.fuel in KNOWN_FUELS }?.fuel ?: JSONObject.NULL)
            .put("speedKmh", if (speeds.isEmpty()) JSONObject.NULL else median(speeds))
            .put("decelerating", last.size >= 2 && last.last().rpm < last.first().rpm - 150.0)
        revisionCounter.incrementAndGet()
        return event
    }

    /** Eventos + agrupamento por faixa de Petrol Inj. (onde mais apaga). */
    fun json(): JSONObject = synchronized(lock) {
        val stalls = events.filter { it.optString("kind") == KIND_STALL }
        val near = events.filter { it.optString("kind") == KIND_NEAR }
        // Região = faixa de 0,5 ms de Petrol Inj. onde o motor engasgou. Cada uma carrega ONDE (MAP, RPM, ms), QUANTAS vezes
        // e QUANDO (primeira/última), para o cérebro propor um ajuste LOCAL na Curva K (ver [StallLocalFix]).
        val bins = events.groupBy { kotlin.math.floor(it.optDouble("petrolMs") / BIN_MS) * BIN_MS }
            .map { (from, list) ->
                JSONObject().put("fromMs", from).put("toMs", from + BIN_MS).put("count", list.size)
                    .put("stallCount", list.count { it.optString("kind") == KIND_STALL })
                    .put("nearCount", list.count { it.optString("kind") == KIND_NEAR })
                    .put("mapBar", median(list.map { it.optDouble("mapBar") }))
                    .put("rpmBefore", median(list.map { it.optDouble("rpmBefore") }))
                    .put("rpm", median(list.map { it.optDouble("rpmBefore") }))
                    .put("ms", median(list.map { it.optDouble("petrolMs") }))
                    .put("firstAt", list.minOf { it.optLong("at") })
                    .put("lastAt", list.maxOf { it.optLong("at") })
                    .put("ats", JSONArray(list.map { it.optLong("at") }.sorted()))
            }.sortedByDescending { it.optInt("count") }
        JSONObject().put("format", FORMAT)
            .put("count", stalls.size)
            .put("restartedCount", stalls.count { it.optString("depois") == AFTER_RESTARTED })
            .put("nearCount", near.size)
            .put("pending", dropAt != null || openEvent != null)
            .put("ignored", JSONObject().put("idleShutdowns", idleShutdowns).put("cutShutdowns", cutShutdowns))
            .put("events", JSONArray(events.takeLast(10).map { JSONObject(it.toString()) }))
            .put("regions", JSONArray(bins))
            .put("criteria", JSONObject()
                .put("before", "RPM ≥ ${RUNNING_RPM.toInt()} no GNV, sem lenta estável; quase-apagão exige ≥ ${DRIVING_RPM.toInt()} rpm nos 2 s anteriores")
                .put("during", "RPM < ${DEAD_RPM.toInt()} por ${CONFIRM_MS} ms com telemetria viva; telemetria calada em ${GAP_MS / 1000} s = desligou")
                .put("after", "só anota: religou em ${RESTART_WINDOW_MS / 1000} s, telemetria parou ou sem religar; o apagão confirmado nunca some"))
    }

    /** Serializa as gravações: a mais recente sempre vence, e dois `save()` nunca se intercalam no mesmo arquivo. */
    private val saveLock = Any()

    private fun save() {
        val target = file ?: return
        synchronized(saveLock) {
            val payload = synchronized(lock) {
                JSONObject().put("format", FORMAT)
                    .put("idleShutdowns", idleShutdowns)
                    .put("cutShutdowns", cutShutdowns)
                    .put("events", JSONArray(events.map { JSONObject(it.toString()) }))
            }
            try {
                // .tmp único + fsync + troca atômica (+ .bak se o rename falhar), como o resto do cérebro.
                JsonFiles.writeAtomic(target, payload.toString())
            } catch (_: Exception) {
            }
        }
    }

    private fun load() {
        try {
            // Principal corrompido/ausente cai no .bak em vez de perder os apagões já registrados.
            val root = JsonFiles.readJsonWithBak(file) {
                val format = it.optString("format")
                format == FORMAT || format == LEGACY_FORMAT
            } ?: return
            idleShutdowns = root.optInt("idleShutdowns", 0)
            cutShutdowns = root.optInt("cutShutdowns", 0)
            root.optJSONArray("events")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { event ->
                    if (!event.has("kind")) event.put("kind", KIND_STALL)
                    events.add(event)
                }
            }
        } catch (_: Exception) {
            events.clear()
        }
    }
}
