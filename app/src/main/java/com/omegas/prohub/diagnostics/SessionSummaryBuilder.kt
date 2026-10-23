package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Resumo leve da sessão (`omegas-session-summary-v1`), montado de forma INCREMENTAL a partir do mesmo fluxo de eventos
 * que o [SessionRecorder] grava. Estado pequeno: somas, janelas de 5 valores e a série de 1 Hz já em texto; nenhum
 * evento fica guardado. Puro (sem Android, sem relógio): o tempo vem dos próprios dados.
 *
 * Regras do dono: desconhecido é null, nunca 0; cada fonte de velocidade (multimídia, GPS, proxy de rpm) tem a sua
 * conta e o seu rótulo, sem mistura silenciosa; o app nunca escreve na ECU/CAN (aqui só se lê eventos).
 *
 * Linha do tempo: `captured_elapsed_ms` da telemetria (monotônico, não sofre com relógio errado) ancorado no primeiro
 * quadro; eventos sem monotônico (multimídia, GPS, mapa) andam pela diferença de parede desde o último quadro.
 */
class SessionSummaryBuilder(private val ctx: SummaryContext) {
    companion object {
        const val SCHEMA = "omegas-session-summary-v1"
        const val SERIES_FILE = "series_1hz.csv"
        /** Lacuna de telemetria (carro desligou, USB caiu): acima disso o intervalo não entra nas somas. */
        const val GAP_MS = 2_000L
        /** O GPS chega num retrato completo a cada ~3 s: buraco maior que isso não é integrado. */
        const val GPS_GAP_MS = 5_000L
        const val MOVING_KMH = 2.0
        const val MOVING_RPM = 1_100
        /** Cilindros do motor (fórmula do dono: gas_ms × rpm/120 × 4 × dt). */
        const val CYLINDERS = 4
        /** Abastecimento = o nível bruto cai ao menos isto (255 = vazio; cheio fica em ~31–76). */
        const val REFUEL_DROP_RAW = 40
        const val REFUEL_CONFIRM_BUCKETS = 5
        const val LEVEL_WINDOW = 5
        /** Escrita periódica: a cada 60 s de tempo DOS DADOS (sem relógio) para sobreviver à queda de energia do carro. */
        const val WRITE_EVERY_DATA_MS = 60_000L
        const val MAX_GAP_ITEMS = 200
        const val MAX_SERIES_ROWS = 36_000
        /** Relógio de parede anterior a isto é suspeito (aparelho voltou a 2025 sem rede). */
        const val MIN_PLAUSIBLE_WALL_MS = 1_767_225_600_000L
        const val DRIFT_SUSPECT_MS = 10_000L
        private const val MM_SPEED_MAX = 400.0
        private val MM_COLUMNS = listOf(
            "mm_odo_km" to "odo", "mm_rpm" to "rotationRate", "mm_fuel_pct" to "fuelPercentage",
            "mm_range_km" to "cruisingRange", "mm_avg_cons" to "avgFuelCont", "mm_inst_cons" to "instFuelCont",
        )
        val COLUMNS: List<String> = listOf(
            "t_s", "rpm", "map_bar", "speed_kmh", "speed_src", "moving", "moving_src", "gnv_level_raw",
            "gas_ms", "petrol_ms", "fuel",
        ) + MM_COLUMNS.map { it.first }
    }

    private class Sample(val t: Long, val rpm: Double?, val gasMs: Double?, val pulse: Boolean, val fuel: String?)
    private class Speed(val t: Long, val v: Double)

    private class Bucket(val sec: Long) {
        var rpmSum = 0.0; var rpmN = 0
        var mapSum = 0.0; var mapN = 0
        var gasSum = 0.0; var gasN = 0
        var petSum = 0.0; var petN = 0
        var gnvN = 0; var petrolN = 0
        val levels = ArrayList<Int>()
        var mmSpeed: Double? = null
        var gpsSpeed: Double? = null
        val mm = LinkedHashMap<String, Double>()
        val delta = Totals()
    }

    private class Segment(val index: Int, val startT: Long) {
        var endT: Long? = null
        var endedByRefuel = false
        val totals = Totals()
        val startWindow = ArrayList<Int>()
        val lastWindow = ArrayDeque<Int>()
        var minRaw: Int? = null
        var maxRaw: Int? = null
        var runMax: Int? = null
        fun startRaw(): Int? = median(startWindow)
        fun endRaw(): Int? = median(lastWindow.toList())
    }

    private class Pending(val bucket: Bucket, val median: Int?)

    // ---- linha do tempo e relógio
    private var firstWall = 0L
    private var lastWall = 0L
    private var anchored = false
    private var anchorElapsed = 0L
    private var anchorBase = 0L
    private var lastTelRel = 0L
    private var lastTelWall = 0L
    private var maxRel = 0L
    private var lastWriteRel = 0L
    private var offsetFirst: Long? = null
    private var offsetLast: Long? = null
    private var firstElapsed: Long? = null
    private var lastElapsed: Long? = null
    private var monotonicSamples = 0L
    private val gpsOffsets = ArrayList<Long>()
    private var lastGpsTs = 0L

    // ---- eventos
    private var events = 0L
    private var lastSequence = 0L
    private var stopped = false
    private var stopReason: String? = null
    private var fullSnapshots = 0L

    // ---- telemetria
    private var samples = 0L
    private var plausibleFlagged = 0L
    private var plausibleTrue = 0L
    private var levelValid = 0L
    private var rpmMax: Double? = null
    private var prev: Sample? = null
    private var currentFuel: String? = null
    private var gapCount = 0L
    private var gapTotalMs = 0L
    private val gapItems = ArrayList<LongArray>()

    // ---- fontes de velocidade
    private var prevMm: Speed? = null
    private var prevGps: Speed? = null
    private var mmEvents = 0L
    private var mmIncomplete = 0L
    private var mmSpeedValid = 0L
    private class Stat { var n = 0L; var min = 0.0; var max = 0.0; var first = 0.0; var last = 0.0 }
    private val mmFields = LinkedHashMap<String, Stat>()
    private var odoFirst: Double? = null
    private var odoLast: Double? = null
    private var rawEvents = 0L
    private var rawChannelsActive = 0
    private var rawChannelsTotal: Int? = null
    private val rawTx = LinkedHashMap<String, Long>()
    private val rawProps = LinkedHashSet<String>()
    private val rawDashboard = LinkedHashSet<String>()
    private val rawCabin = LinkedHashSet<String>()

    // ---- série, nível e tanque
    private val rows = StringBuilder()
    private var rowCount = 0
    private var seriesTruncated = false
    private var open: Bucket? = null
    private val closed = ArrayList<Segment>()
    private var cur = Segment(0, 0L)
    private val pending = ArrayList<Pending>()
    private val intraRefuels = ArrayList<JSONObject>()
    private var overallMin: Int? = null
    private var overallMax: Int? = null

    // ---- mapa e curva
    private class Identity(var atStart: String?) {
        var firstSeen: String? = null
        var last: String? = atStart
    }
    private val mapId = Identity(ctx.mapHashAtStart?.takeIf { it.isNotBlank() })
    private val curveId = Identity(ctx.curveHashAtStart?.takeIf { it.isNotBlank() })
    private val idChanges = ArrayList<JSONObject>()
    private var mapWrites = 0L
    private var curveWrites = 0L

    init {
        rows.setLength(0)
    }

    private val startMs: Long get() = if (ctx.startMs > 0L) ctx.startMs else firstWall

    // ============================================================ entrada

    @Synchronized
    fun observe(sequence: Long, type: String, source: String, data: JSONObject, recordedAtMs: Long) {
        if (events == 0L) firstWall = recordedAtMs
        events += 1L
        lastWall = recordedAtMs
        lastSequence = maxOf(lastSequence, sequence)
        when (type) {
            "telemetry" -> onTelemetry(payloadOf(data), recordedAtMs)
            "car_info" -> onCarInfo(data, recordedAtMs)
            "car_info_raw" -> onCarInfoRaw(data)
            "full_snapshot" -> onFullSnapshot(data, recordedAtMs)
            "k_read_map", "k_map_state" -> data.optString("hash").takeIf { it.isNotBlank() }
                ?.let { identity(mapId, "map", it, type, data, relOf(recordedAtMs, null)) }
            "k_read_curve", "k_curve_state" -> (data.optString("hash").takeIf { it.isNotBlank() } ?: curveHashOf(data))
                ?.let { identity(curveId, "curve", it, type, data, relOf(recordedAtMs, null)) }
            "k_batch_confirmed" -> mapWrites += 1L
            "k_factor_batch_confirmed" -> curveWrites += 1L
            "session_stopped" -> {
                stopped = true
                stopReason = data.optString("reason").takeIf { it.isNotBlank() }
                maxRel = maxOf(maxRel, relOf(recordedAtMs, null))
            }
        }
    }

    /** O telemetry gravado tem o quadro direto em `data`; formatos antigos o trazem em `event.data` ou `data.data`. */
    private fun payloadOf(data: JSONObject): JSONObject =
        data.optJSONObject("event")?.optJSONObject("data") ?: data.optJSONObject("data") ?: data

    @Synchronized fun dueForWrite(): Boolean = maxRel - lastWriteRel >= WRITE_EVERY_DATA_MS
    @Synchronized fun markWritten() { lastWriteRel = maxRel }

    // ============================================================ tempo

    private fun relOf(wall: Long, elapsed: Long?): Long {
        val start = startMs
        val rel = if (elapsed != null) {
            if (!anchored) {
                anchored = true
                anchorElapsed = elapsed
                anchorBase = maxOf(0L, wall - start)
            }
            val r = elapsed - anchorElapsed + anchorBase
            lastTelRel = r
            lastTelWall = wall
            r
        } else if (anchored) {
            lastTelRel + maxOf(0L, wall - lastTelWall)
        } else {
            maxOf(0L, wall - start)
        }
        if (rel > maxRel) maxRel = rel
        return rel
    }

    // ============================================================ telemetria

    private fun onTelemetry(p: JSONObject, wall: Long) {
        val elapsed = if (p.has("captured_elapsed_ms") && !p.isNull("captured_elapsed_ms")) p.optLong("captured_elapsed_ms") else null
        val rel = relOf(wall, elapsed)
        if (elapsed != null) {
            monotonicSamples += 1L
            val offset = wall - elapsed
            if (offsetFirst == null) offsetFirst = offset
            offsetLast = offset
            if (firstElapsed == null) firstElapsed = elapsed
            lastElapsed = elapsed
        }
        samples += 1L
        val rpm = number(p, "rpm")?.takeIf { it in 0.0..20_000.0 }
        val fuel = normFuel(p.optString("fuel", ""))
        val gasMs = (number(p, "gas_ms_diagnostic") ?: number(p, "gas_ms"))?.takeIf { it in 0.0..1_000.0 }
        val pulse = when {
            p.has("gas_pulse_present") && !p.isNull("gas_pulse_present") -> p.optBoolean("gas_pulse_present")
            number(p, "gas_raw") != null -> (number(p, "gas_raw") ?: 0.0) > 0.0
            else -> (gasMs ?: 0.0) > 0.0
        }
        val level = number(p, "level_raw")?.toInt()?.takeIf { it in 0..255 && number(p, "level_raw") == it.toDouble() }
        if (p.has("plausible") && !p.isNull("plausible")) {
            plausibleFlagged += 1L
            if (p.optBoolean("plausible")) plausibleTrue += 1L
        }
        if (level != null) levelValid += 1L
        if (rpm != null) rpmMax = maxOf(rpmMax ?: rpm, rpm)

        var b = open
        if (b == null || rel / 1000L > b.sec) {
            b?.let { flush(it) }
            b = Bucket(rel / 1000L)
            open = b
        }
        val before = prev
        if (before != null) {
            val dt = rel - before.t
            if (dt > GAP_MS) gap(before.t, rel) else if (dt > 0L) account(b.delta, before, dt)
        }
        prev = Sample(rel, rpm, gasMs, pulse, fuel)
        currentFuel = fuel
        if (rpm != null) { b.rpmSum += rpm; b.rpmN += 1; b.delta.src.getValue(SpeedSource.RPM_PROXY).samples += 1L }
        number(p, "load_bar")?.let { b.mapSum += it; b.mapN += 1 }
        gasMs?.let { b.gasSum += it; b.gasN += 1 }
        number(p, "petrol_ms")?.takeIf { it in 0.0..1_000.0 }?.let { b.petSum += it; b.petN += 1 }
        if (fuel == "GNV") b.gnvN += 1 else if (fuel == "GASOLINA") b.petrolN += 1
        level?.let { b.levels.add(it) }
    }

    /** Intervalo de [dt] ms a partir do quadro anterior (o valor anterior vale até o quadro seguinte). */
    private fun account(t: Totals, s: Sample, dt: Long) {
        val ms = dt.toDouble()
        t.coveredMs += ms
        if (s.fuel == "GNV") t.gnvMs += ms else if (s.fuel == "GASOLINA") t.petrolMs += ms
        if (s.pulse) t.gasInjectingMs += ms
        if (s.rpm != null) {
            t.rpmWeighted += s.rpm * ms
            val proxy = t.src.getValue(SpeedSource.RPM_PROXY)
            proxy.coverageMs += ms
            if (s.rpm > MOVING_RPM) proxy.movingMs += ms else proxy.stoppedMs += ms
            if (s.gasMs != null) t.injectorOpenMs += s.gasMs * (s.rpm / 120.0) * CYLINDERS * (ms / 1000.0)
        }
    }

    private fun gap(startT: Long, endT: Long) {
        gapCount += 1L
        gapTotalMs += endT - startT
        if (gapItems.size < MAX_GAP_ITEMS) gapItems.add(longArrayOf(startT, endT))
    }

    // ============================================================ multimídia, GPS, mapa

    private fun onCarInfo(d: JSONObject, wall: Long) {
        val rel = relOf(wall, null)
        mmEvents += 1L
        if (d.has("complete") && !d.optBoolean("complete", true)) mmIncomplete += 1L
        for (key in d.keys()) {
            if (key == "complete" || key == "sources") continue
            val v = number(d, key) ?: continue
            if (mmFields.size >= 32 && !mmFields.containsKey(key)) continue
            val st = mmFields.getOrPut(key) { Stat().also { it.first = v; it.min = v; it.max = v } }
            st.n += 1L; st.last = v; st.min = minOf(st.min, v); st.max = maxOf(st.max, v)
        }
        val b = bucketFor(rel)
        number(d, "odo")?.takeIf { it >= 0.0 }?.let { if (odoFirst == null) odoFirst = it; odoLast = it }
        MM_COLUMNS.forEach { (_, key) -> number(d, key)?.let { b.mm[key] = it } }
        val speed = number(d, "speed")?.takeIf { it in 0.0..MM_SPEED_MAX } ?: return
        mmSpeedValid += 1L
        integrate(b, SpeedSource.MM, prevMm, rel, speed, GAP_MS)
        prevMm = Speed(rel, speed)
        b.mmSpeed = speed
    }

    private fun onFullSnapshot(d: JSONObject, wall: Long) {
        fullSnapshots += 1L
        val gps = d.optJSONObject("gps") ?: return
        // Sem fix o app manda speedKmh 0.0: só vale com GPS ligado, posição conhecida e fix novo (senão seria 0 de mentira).
        val ts = gps.optLong("timestamp", 0L)
        if (!gps.optBoolean("enabled", false) || gps.isNull("latitude") || !gps.has("latitude") || ts <= 0L) return
        if (ts == lastGpsTs) return
        lastGpsTs = ts
        val rel = relOf(wall, null)
        if (gpsOffsets.size < 16) gpsOffsets.add(ts - wall)
        val speed = number(gps, "speedKmh")?.takeIf { it in 0.0..MM_SPEED_MAX } ?: return
        val b = bucketFor(rel)
        integrate(b, SpeedSource.GPS, prevGps, rel, speed, GPS_GAP_MS)
        prevGps = Speed(rel, speed)
        b.gpsSpeed = speed
    }

    /** Trapézio entre duas leituras da mesma fonte; buraco acima de [gapLimit] não integra (conta como lacuna da fonte). */
    private fun integrate(b: Bucket, source: String, before: Speed?, rel: Long, speed: Double, gapLimit: Long) {
        val s = b.delta.src.getValue(source)
        s.samples += 1L
        if (before == null) return
        val dt = rel - before.t
        if (dt <= 0L) return
        if (dt > gapLimit) { s.gapMs += dt.toDouble(); return }
        s.coverageMs += dt.toDouble()
        if (before.v > MOVING_KMH) s.movingMs += dt.toDouble() else s.stoppedMs += dt.toDouble()
        val km = (before.v + speed) / 2.0 * dt / 3_600_000.0
        s.km += km
        if (currentFuel == "GNV") s.kmOnGnv += km
    }

    private fun onCarInfoRaw(d: JSONObject) {
        rawEvents += 1L
        rawChannelsActive = maxOf(rawChannelsActive, d.optInt("channelsActive", 0))
        if (d.has("channelsTotal")) rawChannelsTotal = d.optInt("channelsTotal")
        d.optJSONObject("tx")?.let { tx ->
            for (k in tx.keys()) rawTx[k] = maxOf(rawTx[k] ?: 0L, tx.optJSONObject(k)?.optLong("n", 0L) ?: 0L)
        }
        d.optJSONObject("props")?.optJSONArray("values")?.let { values ->
            for (i in 0 until values.length()) if (rawProps.size < 64) rawProps.add(values.optString(i).substringBefore('='))
        }
        d.optJSONObject("dashboard")?.let { dash ->
            for (k in dash.keys()) if (!dash.isNull(k) && rawDashboard.size < 32) rawDashboard.add(k)
        }
        d.optJSONObject("cabin")?.let { cab -> for (k in cab.keys()) if (rawCabin.size < 32) rawCabin.add(k) }
    }

    private fun curveHashOf(d: JSONObject): String? {
        val raw = d.optJSONArray("factorsRaw") ?: return null
        if (raw.length() == 0) return null
        return Totals.sha256((0 until raw.length()).joinToString(",") { raw.optInt(it).toString() })
    }

    private fun identity(id: Identity, kind: String, hash: String, via: String, d: JSONObject, rel: Long) {
        if (id.firstSeen == null) id.firstSeen = hash
        if (id.last != hash) {
            if (idChanges.size < 30) {
                idChanges.add(
                    JSONObject().put("t", rel).put("kind", kind).put("hash", hash).put("via", via)
                        .put("origin", d.optString("origin").takeIf { it.isNotBlank() } ?: JSONObject.NULL),
                )
            }
            id.last = hash
        }
    }

    private fun bucketFor(rel: Long): Bucket {
        open?.let { return it }
        return Bucket(rel / 1000L).also { open = it }
    }

    // ============================================================ fechamento de segundos, nível e tanque

    private fun flush(b: Bucket) {
        appendRow(b)
        val med = median(b.levels)
        val runMax = cur.runMax
        val candidate = med != null && runMax != null && med <= runMax - REFUEL_DROP_RAW
        if (candidate || (pending.isNotEmpty() && med == null)) {
            pending.add(Pending(b, med))
            if (pending.count { it.median != null } >= REFUEL_CONFIRM_BUCKETS) confirmRefuel()
            return
        }
        applyPending()
        apply(b, med)
    }

    private fun apply(b: Bucket, med: Int?) {
        cur.totals.plus(b.delta)
        if (med == null) return
        if (cur.startWindow.size < LEVEL_WINDOW) cur.startWindow.add(med)
        cur.lastWindow.addLast(med)
        while (cur.lastWindow.size > LEVEL_WINDOW) cur.lastWindow.removeFirst()
        cur.minRaw = minOf(cur.minRaw ?: med, med)
        cur.maxRaw = maxOf(cur.maxRaw ?: med, med)
        cur.runMax = maxOf(cur.runMax ?: med, med)
        overallMin = minOf(overallMin ?: med, med)
        overallMax = maxOf(overallMax ?: med, med)
    }

    private fun applyPending() {
        pending.forEach { apply(it.bucket, it.median) }
        pending.clear()
    }

    /** O nível caiu >= 40 e ficou assim por 5 segundos: fecha o segmento antigo e o novo começa no primeiro desses segundos. */
    private fun confirmRefuel() {
        val from = cur.runMax ?: return
        val medians = pending.mapNotNull { it.median }
        val to = median(medians) ?: return
        val atT = pending.first().bucket.sec * 1000L
        cur.endT = atT
        cur.endedByRefuel = true
        closed.add(cur)
        intraRefuels.add(JSONObject().put("atT", atT).put("fromRaw", from).put("toRaw", to).put("drop", from - to))
        cur = Segment(closed.size, atT)
        applyPending()
    }

    private fun appendRow(b: Bucket) {
        if (rowCount >= MAX_SERIES_ROWS) { seriesTruncated = true; return }
        rows.append(rowOf(b)).append('\n')
        rowCount += 1
    }

    private fun rowOf(b: Bucket): String {
        val cells = ArrayList<String>(COLUMNS.size)
        val rpm = if (b.rpmN > 0) b.rpmSum / b.rpmN else null
        val speed = b.mmSpeed ?: b.gpsSpeed
        val speedSrc = if (b.mmSpeed != null) SpeedSource.MM else if (b.gpsSpeed != null) SpeedSource.GPS else ""
        val movingSrc = when {
            b.mmSpeed != null -> SpeedSource.MM
            b.gpsSpeed != null -> SpeedSource.GPS
            rpm != null -> SpeedSource.RPM_PROXY
            else -> ""
        }
        val moving = when (movingSrc) {
            SpeedSource.MM, SpeedSource.GPS -> if ((speed ?: 0.0) > MOVING_KMH) "1" else "0"
            SpeedSource.RPM_PROXY -> if ((rpm ?: 0.0) > MOVING_RPM) "1" else "0"
            else -> ""
        }
        cells += b.sec.toString()
        cells += rpm?.let { Math.round(it).toString() } ?: ""
        cells += if (b.mapN > 0) fmt(b.mapSum / b.mapN, 3) else ""
        cells += speed?.let { fmt(it, 1) } ?: ""
        cells += speedSrc
        cells += moving
        cells += movingSrc
        cells += median(b.levels)?.toString() ?: ""
        cells += if (b.gasN > 0) fmt(b.gasSum / b.gasN, 3) else ""
        cells += if (b.petN > 0) fmt(b.petSum / b.petN, 3) else ""
        cells += if (b.gnvN + b.petrolN == 0) "" else if (b.gnvN >= b.petrolN) "GNV" else "GASOLINA"
        MM_COLUMNS.forEach { (_, key) -> cells += b.mm[key]?.let { fmt(it, 2) } ?: "" }
        return cells.joinToString(",")
    }

    /** Série de 1 Hz (CSV): só segundos com dado; célula vazia = desconhecido. */
    @Synchronized
    fun seriesCsv(): String {
        val sb = StringBuilder(COLUMNS.joinToString(",")).append('\n').append(rows)
        open?.let { if (rowCount < MAX_SERIES_ROWS) sb.append(rowOf(it)).append('\n') }
        return sb.toString()
    }

    // ============================================================ saída

    @Synchronized
    fun summary(final: Boolean): JSONObject {
        val view = segmentsView()
        val total = Totals().also { t -> view.forEach { t.plus(it.totals) } }
        val elapsed = maxRel
        val covered = total.coveredMs
        val primary = SpeedSource.ALL.firstOrNull { total.src.getValue(it).coverageMs > 0.0 }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("final", final)
            .put("closedCleanly", if (!final) JSONObject.NULL else stopped)
            .put("identity", identityJson(elapsed))
            .put("clock", clockJson())
            .put("build", JSONObject().put("appVersion", ctx.appVersion ?: JSONObject.NULL).put("commit", ctx.commit ?: JSONObject.NULL))
            .put(
                "context",
                JSONObject().put("carProfile", ctx.carProfile ?: JSONObject.NULL).put("vin", ctx.vin ?: JSONObject.NULL)
                    .put("deviceId", ctx.deviceId ?: JSONObject.NULL).put("fuelDominant", dominantFuel(total)),
            )
            .put(
                "durations",
                JSONObject().put("telemetryCoveredMs", Totals.nullable(covered.takeIf { samples > 0L }, 1))
                    .put("gnvMs", Totals.nullable(total.gnvMs.takeIf { samples > 0L }, 1))
                    .put("petrolMs", Totals.nullable(total.petrolMs.takeIf { samples > 0L }, 1))
                    .put("telemetrySamples", samples),
            )
            .put("movement", movementJson(total, primary))
            .put(
                "engine",
                JSONObject().put("rpmMean", Totals.nullable((total.rpmWeighted / covered).takeIf { covered > 0.0 }, 1))
                    .put("rpmMax", Totals.nullable(rpmMax, 0))
                    .put("gasInjectingMs", Totals.nullable(total.gasInjectingMs.takeIf { covered > 0.0 }, 1))
                    .put("injectorOpenMinutesGnv", Totals.nullable((total.injectorOpenMs / 60_000.0).takeIf { covered > 0.0 }, 6))
                    .put("injectorFormula", "soma(gas_ms * rpm/120 * $CYLINDERS cilindros * dt_s) / 60000 ms; so intervalos <= ${GAP_MS} ms"),
            )
            .put("gnvLevel", levelJson(view))
            .put("tankSegments", JSONArray().also { arr -> view.forEach { arr.put(segmentJson(it)) } })
            .put("mapCurve", mapCurveJson())
            .put("multimedia", multimediaJson())
            .put(
                "gaps",
                JSONObject().put("thresholdMs", GAP_MS).put("count", gapCount).put("totalMs", gapTotalMs)
                    .put("truncated", gapCount > gapItems.size)
                    .put("items", JSONArray().also { arr -> gapItems.forEach { arr.put(JSONObject().put("startT", it[0]).put("endT", it[1]).put("ms", it[1] - it[0])) } }),
            )
            .put("quality", qualityJson(total, elapsed))
            .put(
                "series",
                JSONObject().put("file", SERIES_FILE).put("rows", rowCount + if (open != null && rowCount < MAX_SERIES_ROWS) 1 else 0)
                    .put("truncated", seriesTruncated).put("columns", JSONArray(COLUMNS))
                    .put("note", "1 linha por segundo com dado; celula vazia = desconhecido; t_s em segundos monotonicos desde o inicio"),
            )
            .put("safety", JSONObject().put("appWritesEcu", false).put("automaticEcuWrite", false))
    }

    /** Segmentos fechados + o atual com o que ainda está pendente e o segundo aberto (cópia, sem alterar o estado). */
    private fun segmentsView(): List<Segment> {
        val currentCopy = Segment(cur.index, cur.startT).also { c ->
            c.totals.plus(cur.totals)
            c.startWindow.addAll(cur.startWindow)
            c.lastWindow.addAll(cur.lastWindow)
            c.minRaw = cur.minRaw; c.maxRaw = cur.maxRaw; c.runMax = cur.runMax
        }
        val tail = ArrayList<Pending>(pending)
        open?.let { tail.add(Pending(it, median(it.levels))) }
        tail.forEach { p ->
            currentCopy.totals.plus(p.bucket.delta)
            val med = p.median ?: return@forEach
            if (currentCopy.startWindow.size < LEVEL_WINDOW) currentCopy.startWindow.add(med)
            currentCopy.lastWindow.addLast(med)
            while (currentCopy.lastWindow.size > LEVEL_WINDOW) currentCopy.lastWindow.removeFirst()
            currentCopy.minRaw = minOf(currentCopy.minRaw ?: med, med)
            currentCopy.maxRaw = maxOf(currentCopy.maxRaw ?: med, med)
        }
        return closed + currentCopy
    }

    private fun identityJson(elapsed: Long) = JSONObject()
        .put("sessionId", ctx.sessionId).put("sessionUuid", ctx.sessionUuid)
        .put("startMs", if (startMs > 0L) startMs else JSONObject.NULL)
        .put("startUtc", if (startMs > 0L) utc(startMs) else JSONObject.NULL)
        .put("endMs", if (lastWall > 0L) lastWall else JSONObject.NULL)
        .put("elapsedMs", elapsed)
        .put("wallDurationMs", if (lastWall > 0L && startMs > 0L) maxOf(0L, lastWall - startMs) else JSONObject.NULL)
        .put("events", events).put("lastSequence", lastSequence)
        .put("stopReason", stopReason ?: JSONObject.NULL)

    private fun clockJson(): JSONObject {
        val reasons = JSONArray()
        val start = startMs
        if (start in 1 until MIN_PLAUSIBLE_WALL_MS) reasons.put("WALL_BEFORE_2026")
        val drift = if (offsetFirst != null && offsetLast != null) offsetLast!! - offsetFirst!! else null
        if (drift != null && Math.abs(drift) > DRIFT_SUSPECT_MS) reasons.put("WALL_JUMP")
        val gpsRef = median(gpsOffsets)
        return JSONObject()
            .put("source", "WALL_VS_ELAPSEDREALTIME")
            .put("wallStartMs", if (start > 0L) start else JSONObject.NULL)
            .put("monotonicSamples", monotonicSamples)
            .put("firstElapsedMs", firstElapsed ?: JSONObject.NULL)
            .put("lastElapsedMs", lastElapsed ?: JSONObject.NULL)
            .put("wallMinusElapsedFirstMs", offsetFirst ?: JSONObject.NULL)
            .put("wallMinusElapsedLastMs", offsetLast ?: JSONObject.NULL)
            .put("driftMs", drift ?: JSONObject.NULL)
            .put("clockSuspect", reasons.length() > 0)
            .put("suspectReasons", reasons)
            .put("gpsReferenceOffsetMs", gpsRef ?: JSONObject.NULL)
            .put("gpsReferenceSamples", gpsOffsets.size)
            .put("note", "gpsReferenceOffsetMs = hora do GPS (UTC) menos a parede do aparelho; somar a startMs corrige o relogio. t da serie e monotonico.")
    }

    private fun movementJson(total: Totals, primary: String?): JSONObject {
        val bySource = JSONObject()
            .put(SpeedSource.MM, total.sourceJson(SpeedSource.MM, "thresholdKmh" to MOVING_KMH))
            .put(SpeedSource.GPS, total.sourceJson(SpeedSource.GPS, "thresholdKmh" to MOVING_KMH))
            .put(SpeedSource.RPM_PROXY, total.sourceJson(SpeedSource.RPM_PROXY, "thresholdRpm" to MOVING_RPM))
        val p = JSONObject()
        if (primary == null) p.put("source", JSONObject.NULL) else {
            val s = total.src.getValue(primary)
            p.put("source", primary).put("proxy", primary == SpeedSource.RPM_PROXY)
                .put("movingMs", Totals.nullable(s.movingMs, 1)).put("stoppedMs", Totals.nullable(s.stoppedMs, 1))
                .put("km", Totals.nullable(total.kmOf(primary), 4))
        }
        val odoKm = if (odoFirst != null && odoLast != null && odoLast!! >= odoFirst!! && odoLast != odoFirst) odoLast!! - odoFirst!! else null
        return JSONObject()
            .put("primary", p)
            .put("bySource", bySource)
            .put(
                "odometer",
                JSONObject().put("firstKm", odoFirst ?: JSONObject.NULL).put("lastKm", odoLast ?: JSONObject.NULL)
                    .put("km", Totals.nullable(odoKm, 3)).put("unit", "km (assumido, nao confirmado)"),
            )
            .put("speedUnitAssumed", "km/h")
            .put("note", "Prioridade MM > GPS > RPM_PROXY; fontes nunca somadas. RPM_PROXY (rpm > $MOVING_RPM) so mede tempo, sem km.")
    }

    private fun levelJson(view: List<Segment>): JSONObject {
        val startRaw = view.first().startRaw()
        val endRaw = view.lastOrNull { it.endRaw() != null }?.endRaw()
        val prevEnd = ctx.previousGnvEndRaw
        val drop = if (prevEnd != null && startRaw != null) prevEnd - startRaw else null
        return JSONObject()
            .put("unit", "raw_byte").put("scale", "0-255").put("orientation", "INVERTED_255_EMPTY")
            .put("note", "level_raw (byte 13 do 48 01): 255 = vazio, cheio ~31-76; abastecimento = queda >= $REFUEL_DROP_RAW. remainingIndex = 1 - raw/255 e PROXY.")
            .put("startRaw", startRaw ?: JSONObject.NULL)
            .put("endRaw", endRaw ?: JSONObject.NULL)
            .put("minRaw", overallMin ?: view.mapNotNull { it.minRaw }.minOrNull() ?: JSONObject.NULL)
            .put("maxRaw", overallMax ?: view.mapNotNull { it.maxRaw }.maxOrNull() ?: JSONObject.NULL)
            .put("remainingIndexStart", startRaw?.let { Totals.nullable(1.0 - it / 255.0, 3) } ?: JSONObject.NULL)
            .put("remainingIndexEnd", endRaw?.let { Totals.nullable(1.0 - it / 255.0, 3) } ?: JSONObject.NULL)
            .put(
                "refuel",
                JSONObject().put("threshold", REFUEL_DROP_RAW)
                    .put("previousEndRaw", prevEnd ?: JSONObject.NULL)
                    .put("dropBetween", drop ?: JSONObject.NULL)
                    .put("betweenSessions", if (drop == null) JSONObject.NULL else drop >= REFUEL_DROP_RAW)
                    .put("intraSession", JSONArray(intraRefuels.map { JSONObject(it.toString()) })),
            )
    }

    private fun segmentJson(s: Segment): JSONObject {
        val t = s.totals
        fun perSource(f: (String) -> Any) = JSONObject().also { o -> SpeedSource.ALL.forEach { o.put(it, f(it)) } }
        return JSONObject()
            .put("index", s.index).put("startT", s.startT).put("endT", s.endT ?: JSONObject.NULL)
            .put("endedByRefuel", s.endedByRefuel)
            .put("startRaw", s.startRaw() ?: JSONObject.NULL).put("endRaw", s.endRaw() ?: JSONObject.NULL)
            .put("minRaw", s.minRaw ?: JSONObject.NULL).put("maxRaw", s.maxRaw ?: JSONObject.NULL)
            .put("coveredMs", Totals.nullable(t.coveredMs.takeIf { it > 0.0 }, 1))
            .put("gnvMs", Totals.nullable(t.gnvMs.takeIf { t.coveredMs > 0.0 }, 1))
            .put("gasInjectingMs", Totals.nullable(t.gasInjectingMs.takeIf { t.coveredMs > 0.0 }, 1))
            .put("injectorOpenMinutesGnv", Totals.nullable((t.injectorOpenMs / 60_000.0).takeIf { t.coveredMs > 0.0 }, 6))
            .put("km", perSource { Totals.nullable(t.kmOf(it), 4) })
            .put("kmOnGnv", perSource { Totals.nullable(t.kmOf(it, onGnv = true), 4) })
            .put("movingMs", perSource { Totals.nullable(t.src.getValue(it).movingMs.takeIf { _ -> t.src.getValue(it).coverageMs > 0.0 }, 1) })
            .put("stoppedMs", perSource { Totals.nullable(t.src.getValue(it).stoppedMs.takeIf { _ -> t.src.getValue(it).coverageMs > 0.0 }, 1) })
    }

    private fun mapCurveJson(): JSONObject {
        fun id(i: Identity) = JSONObject().put("hashAtStart", i.atStart ?: JSONObject.NULL)
            .put("hashFirstSeen", i.firstSeen ?: JSONObject.NULL).put("hashLast", i.last ?: JSONObject.NULL)
            .put("confirmedInSession", i.firstSeen != null)
        return JSONObject()
            .put("identityKnown", mapId.last != null || curveId.last != null)
            .put("map", id(mapId)).put("curve", id(curveId))
            .put("changes", JSONArray(idChanges.map { JSONObject(it.toString()) }))
            .put("calibrationWritesConfirmed", JSONObject().put("map", mapWrites).put("curve", curveWrites))
            .put("note", "hashAtStart vem do cache do app (nao confirmado pela ECU nesta sessao); confirmedInSession = visto em leitura da ECU.")
    }

    private fun multimediaJson(): JSONObject {
        val fields = JSONObject()
        val absent = JSONObject()
        mmFields.forEach { (k, s) ->
            fields.put(k, JSONObject().put("n", s.n).put("min", s.min).put("max", s.max).put("first", s.first).put("last", s.last))
            absent.put(k, mmEvents - s.n)
        }
        return JSONObject()
            .put("events", mmEvents).put("incompleteEvents", mmIncomplete)
            .put("fieldsSeen", fields).put("absentCounts", absent)
            .put("speedValidPct", if (mmEvents > 0L) Totals.nullable(mmSpeedValid * 100.0 / mmEvents, 2) else JSONObject.NULL)
            .put(
                "rawChannels",
                JSONObject().put("events", rawEvents).put("channelsActiveMax", rawChannelsActive)
                    .put("channelsTotal", rawChannelsTotal ?: JSONObject.NULL)
                    .put("txCalls", JSONObject().also { o -> rawTx.forEach { (k, v) -> o.put(k, v) } })
                    .put("propsSeen", JSONArray(rawProps.toList()))
                    .put("dashboardValuesSeen", JSONArray(rawDashboard.toList()))
                    .put("cabinKeys", JSONArray(rawCabin.toList())),
            )
    }

    private fun qualityJson(total: Totals, elapsed: Long): JSONObject {
        fun pct(n: Long, d: Long): Any = if (d > 0L) Totals.nullable(n * 100.0 / d, 2) else JSONObject.NULL
        return JSONObject()
            .put("telemetrySamples", samples)
            .put("plausiblePct", pct(plausibleTrue, plausibleFlagged))
            .put("levelValidPct", pct(levelValid, samples))
            .put("mmSpeedValidPct", pct(mmSpeedValid, mmEvents))
            .put("telemetryCoveragePct", if (elapsed > 0L) Totals.nullable(total.coveredMs * 100.0 / elapsed, 2) else JSONObject.NULL)
            .put("fullSnapshots", fullSnapshots)
    }

    private fun dominantFuel(total: Totals): Any = when {
        total.gnvMs <= 0.0 && total.petrolMs <= 0.0 -> JSONObject.NULL
        total.gnvMs >= total.petrolMs -> "GNV"
        else -> "GASOLINA"
    }

    // ============================================================ utilidades

    private fun number(o: JSONObject, key: String): Double? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.opt(key)
        val d = when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null } ?: return null
        return d.takeIf { it.isFinite() }
    }

    private fun normFuel(raw: String): String? = when (raw.uppercase()) {
        "GNV", "CNG", "GAS" -> "GNV"
        "PETROL", "GASOLINA" -> "GASOLINA"
        else -> null
    }

    private fun fmt(v: Double, digits: Int): String {
        val f = Math.pow(10.0, digits.toDouble())
        val r = Math.round(v * f) / f
        return if (r == Math.floor(r) && Math.abs(r) < 1e12) r.toLong().toString() else r.toString()
    }

    private fun utc(ms: Long): String = java.time.Instant.ofEpochMilli(ms).toString()
}

private fun median(values: List<Int>): Int? = if (values.isEmpty()) null else values.sorted()[values.size / 2]
private fun median(values: List<Long>): Long? = if (values.isEmpty()) null else values.sorted()[values.size / 2]
