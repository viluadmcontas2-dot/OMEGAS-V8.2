package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * Cruzamento de comandos (dono, 2026-10-14): o pipeline REAL do Refino (RefinementCycle + EquivalenceLedger + RefinementJournal +
 * RefinementAutopilot, com persistência em arquivo) contra uma ECU falsa, com eventos embaralhados: AutoMatch da ECU,
 * Readquirir, pausar/religar o AutoCal, "refinar por minha conta", gravação de proposta, gravação manual da Curva K,
 * Mapa K gravado, restaurar, desfazer, queda do cabo, reabrir o app, leitura atrasada da ECU.
 * Cada passo confere INVARIANTES (nada de estado velho, porta de gravação, fase×botão, persistência).
 *
 * O modelo da ECU é INFERIDO: o AutoMatch fica "pendente" quando as zonas completam e dispara depois de um atraso aleatório.
 * Só mede quanto a regra "só evidência" abre a porta com um AutoMatch pendente (risco que o dono aceitou); não é prova do firmware.
 */
class FakeEcuWorld(val seed: Int) {
    val rnd = Random(seed)
    var now = 5_000_000L
    val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    fun ms(i: Int) = axisRaw[i] / 512.0
    val bandPetrolMs = doubleArrayOf(3.75, 5.25, 6.75, 8.25, 10.5)
    val bandMap = doubleArrayOf(0.40, 0.55, 0.70, 0.85, 1.00)
    val target = IntArray(30) { (16384 * (1.0 + 0.08 * Math.sin(it / 4.0))).toInt() }

    // ECU
    var curve = IntArray(30) { 16384 }
    var readCurve = curve.copyOf()
    var readAt = 0L
    var count = 0
    val max = 3
    var enabled = 1
    var petrolZones = 4
    var gasZones = 4
    var everObserved = false; var seenBefore = false
    var noAmTicks = 0 // depois de readquirir a ECU precisa readquirir (minutos): sem AutoMatch nos próximos ticks (INFERIDO)
    var staleTicks = 0; var staleP = 0; var staleG = 0 // leitura atrasada da aquisição depois de readquirir/AutoMatch (INFERIDO)
    var pendingAutoMatchIn = -1 // ticks até o AutoMatch (modelo INFERIDO)
    var online = true

    // App
    val dir: File = java.nio.file.Files.createTempDirectory("cruz$seed").toFile()
    val journalFile = File(dir, "journal.json")
    val pilotFile = File(dir, "pilot.json")
    var ledger = EquivalenceLedger(null) { now }
    var journal = RefinementJournal(journalFile) { now }
    var pilot = RefinementAutopilot(pilotFile) { now }
    var cycle = newCycle()
    var last = JSONObject()
    var restartPending = false
    var manual = false

    // Estatísticas
    var writes = 0; var unsafeWrites = 0; var gateClosedTicks = 0; var restarts = 0; var reopens = 0; var autoMatches = 0
    var restores = 0; var manualWrites = 0; var mapKWrites = 0; var undos = 0
    val violations = ArrayList<String>()
    val log = ArrayDeque<String>()
    fun ev(x: String) { log.addLast(x); if (log.size > 14) log.removeFirst() }

    private fun newCycle() = RefinementCycle(ledger, journal, pilot, monotonicMs = { now }) { snapshot, _, _ -> analysisOf(snapshot) }

    private fun zone(i: Int) = when (i) { in 0..5 -> 0; in 6..9 -> 1; in 10..13 -> 2; else -> 3 }
    private fun reportedP() = if (staleTicks > 0) staleP else petrolZones
    private fun reportedG() = if (staleTicks > 0) staleG else gasZones
    private fun acquisition(): JSONObject = JSONObject().put("points", JSONArray().apply {
        listOf("GASOLINA" to reportedP(), "GNV" to reportedG()).forEach { (fuel, zones) ->
            repeat(18) { i ->
                val done = zone(i) < zones
                put(JSONObject().put("fuel", fuel).put("zone", zone(i)).put("zoneAcquired", done)
                    .put("state", if (done) "ZONA_ADQUIRIDA" else "AGUARDANDO"))
            }
        }
    })
    private fun monitor() = JSONObject().put("autoMatchCount", count).put("maxAutomatch", max).put("autoCalEnabled", enabled).put("acquisition", acquisition())
    private fun snapshot(): JSONObject = JSONObject().put("snapshotHash", "S$readAt").put("capturedAtMs", readAt).put("fields", JSONArray()
        .put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", JSONArray(axisRaw.toList())).put("capturedAtMs", readAt))
        .put(JSONObject().put("key", "MUL_ACT").put("status", "VALID").put("rawValues", JSONArray(readCurve.toList())).put("capturedAtMs", readAt)))

    private fun analysisOf(snapshot: JSONObject): JSONObject {
        val mul = (0 until snapshot.getJSONArray("fields").length()).map { snapshot.getJSONArray("fields").getJSONObject(it) }
            .first { it.getString("key") == "MUL_ACT" }.getJSONArray("rawValues")
        val current = IntArray(30) { mul.getInt(it) }
        val capturedAt = snapshot.getLong("capturedAtMs")
        val points = JSONArray()
        for (i in 0 until 30) {
            val want = if (Math.abs(current[i] - target[i]) > target[i] * 0.02) target[i] else current[i]
            points.put(JSONObject().put("index", i).put("referenceTimeMs", ms(i)).put("currentRaw", current[i])
                .put("calculatedRaw", want).put("origin", if (want == current[i]) "HELD" else "MEASURED"))
        }
        return JSONObject().put("ok", true).put("available", true).put("snapshotHash", "S$capturedAt").put("refinementMode", "EQUIVALENCE")
            .put("capturedAtMs", capturedAt).put("mulActCapturedAtMs", capturedAt).put("points", points)
    }

    // ---- eventos
    private fun frames(fuel: String, map: Double, petrolMs: Double, n: Int) {
        now += 2_000
        repeat(n) { ledger.accept(EquivalenceLedger.Frame(now, fuel, 2000.0, map, petrolMs, 0.0, true)); now += 280 }
    }
    fun drive() {
        val b = rnd.nextInt(5)
        val idx = (b * 6 + 3).coerceAtMost(29)
        // GNV/gasolina = 1 quando a curva está no alvo; passou ou ficou aquém → ratio fora de 1 (física inferida, só para exercitar o diário).
        val ratio = 1.0 + (rnd.nextDouble() - 0.5) * 0.03 + (target[idx] - curve[idx]).toDouble() / 16384.0 * 1.2
        if (rnd.nextInt(4) == 0) frames("GASOLINA", bandMap[b], bandPetrolMs[b], 10 + rnd.nextInt(10))
        frames("GNV", bandMap[b], bandPetrolMs[b] * ratio, 10 + rnd.nextInt(20))
    }
    fun readEcu() { ev("READ"); readAt = now; readCurve = curve.copyOf() }
    fun automatch() {
        if (count >= max || enabled == 0 || noAmTicks > 0) return
        staleP = petrolZones; staleG = gasZones; staleTicks = if (everObserved) 1 + rnd.nextInt(3) else 0
        ev("AM"); count++; autoMatches++
        for (i in 0 until 30) curve[i] = (curve[i] * (1.0 + (rnd.nextDouble() - 0.5) * 0.06)).toInt().coerceIn(11000, 29000)
        gasZones = 1; pendingAutoMatchIn = -1
    }
    fun restart(kind: Int) {
        restarts++; ev("R$kind"); noAmTicks = 6
        staleP = petrolZones; staleG = gasZones; staleTicks = if (everObserved) 1 + rnd.nextInt(3) else 0
        when (kind) { 0 -> { gasZones = 0 }; 1 -> { petrolZones = 0 }; else -> { petrolZones = 0; gasZones = 0; count = 0 } }
        pendingAutoMatchIn = -1
        pilot.noteAcquisitionRestart(); restartPending = true
    }
    fun fillZones() {
        if (rnd.nextInt(3) == 0 && gasZones < 4) gasZones++
        if (rnd.nextInt(5) == 0 && petrolZones < 4) petrolZones++
        if (petrolZones >= 4 && gasZones >= 4 && count < max && enabled == 1 && pendingAutoMatchIn < 0 && rnd.nextInt(3) == 0) pendingAutoMatchIn = 1 + rnd.nextInt(30)
    }
    fun reopen() {
        reopens++; ev("REOPEN"); staleTicks = 0 // app novo lê a ECU do zero: leitura fresca, sem atraso de antes
        journal.evaluate(JSONObject().put("samples", 0).put("bands", JSONArray()), false) // força o salvamento como o serviço (healthTick)
        pilot = RefinementAutopilot(pilotFile) { now }
        journal = RefinementJournal(journalFile) { now }
        // EquivalenceLedger não persiste neste cenário (sem arquivo): reabrir app = acumulador novo, como no serviço sem cache.
        ledger = EquivalenceLedger(null) { now }
        cycle = newCycle()
        if (manual) { manual = false; // "por minha conta" não sobrevive
            check(!pilot.json().optBoolean("manual", false)) { "manual sobreviveu" } }
    }
    fun write(points: JSONArray, reason: String): Boolean {
        val listed = JSONArray()
        for (k in 0 until points.length()) {
            val p = points.getJSONObject(k)
            if (curve[p.getInt("index")] != p.getInt("currentRaw")) return false // lista velha: a ECU recusaria
            listed.put(JSONObject().put("index", p.getInt("index")).put("currentRaw", p.getInt("currentRaw")).put("targetRaw", p.getInt("targetRaw")))
            curve[p.getInt("index")] = p.getInt("targetRaw")
        }
        now += 1_000
        ev("WRITE"); cycle.recordConfirmedCurve(JSONObject().put("adjustmentId", "KF-$now").put("points", listed)
            .put("confirmedEvents", JSONArray().put(JSONObject().put("reason", reason)))
            .put("curve", JSONObject().put("axisRaw", JSONArray(axisRaw.toList())).put("factorsRaw", JSONArray(curve.toList()))))
        return true
    }

    fun tick() {
        now += 3_000
        if (noAmTicks > 0) noAmTicks--
        if (pendingAutoMatchIn > 0) { pendingAutoMatchIn--; if (pendingAutoMatchIn == 0) automatch() }
        fillZones()
        cycle.evaluate(online)
        seenBefore = everObserved; if (online) everObserved = true
        ev("T(" + reportedP() + "/" + reportedG() + ",c" + count + ")")
        last = cycle.observe(online, online, if (online) monitor() else null, if (online) snapshot() else JSONObject().put("available", false))
        checkInvariants()
        if (staleTicks > 0) staleTicks--
    }

    private fun bad(msg: String) { if (violations.size < 12) violations += "seed=$seed: $msg [online=$online manual=$manual count=$count enabled=$enabled zonas=$petrolZones/$gasZones stale=$staleTicks] últimos=${log.joinToString(" > ")}" }

    private fun checkInvariants() {
        val a = last
        val reason = if (a.isNull("ecuDoneReason")) null else a.optString("ecuDoneReason")
        val done = a.optBoolean("ecuDone")
        val action = a.optJSONObject("action")?.optString("kind").orEmpty()
        val phase = a.optString("phase")
        val complete = reportedP() >= 4 && reportedG() >= 4
        // A: porta de gravação
        if (!done && (action == "review" || action == "restore")) bad("ação '$action' liberada com a ECU trabalhando ($phase)")
        if (!done) gateClosedTicks++
        // B/C: o "terminou" sempre tem causa vista AGORA (nada de estado velho)
        if (online) when (reason) {
            "MANUAL" -> if (!a.optBoolean("manual")) bad("MANUAL sem manual ligado")
            "AUTOCAL_DESLIGADO" -> if (enabled != 0) bad("AUTOCAL_DESLIGADO com AutoCal ligado (estado velho)")
            "MAX_AUTOMATCH" -> if (count < max) bad("MAX_AUTOMATCH com $count/$max")
            "AQUISICAO_COMPLETA" -> if (!complete) bad("AQUISICAO_COMPLETA com zonas $petrolZones/$gasZones (estado velho)")
            null -> {}
            else -> bad("motivo desconhecido $reason")
        }
        if (reason == null && done) bad("ecuDone sem motivo")
        // D: depois de readquirir, aquisição velha "completa" não vale (só se ainda completa DE VERDADE e já vista incompleta)
        if (staleTicks > 0 && seenBefore && online && reason == "AQUISICAO_COMPLETA" && !(petrolZones >= 4 && gasZones >= 4)) bad("relatchou aquisição velha (leitura atrasada) reason=$reason lastObsCount=${last.opt("autoMatchCount")} pilotFile=${pilotFile.readText().take(220)}")
        if (!complete) restartPending = false
        // E: fase × proposta × botão
        if (phase == "PROPOSTA_PRONTA" && (a.optJSONObject("proposal")?.optInt("count", 0) ?: 0) <= 0) bad("PROPOSTA_PRONTA sem pontos")
        if (phase == "PROPOSTA_PRONTA" && action != "review") bad("PROPOSTA_PRONTA sem botão review ($action)")
        if (phase == "RESTAURAR_TRECHO" && action != "restore") bad("RESTAURAR_TRECHO sem botão restore")
        if (phase == "ECU_TRABALHANDO" && done) bad("ECU_TRABALHANDO com ecuDone")
        // I: "piorou" só com 20+ leituras (ou prazo parcial por leituras)
        val latest = journal.json().optJSONObject("latest")
        val bands = latest?.optJSONArray("bands")
        if (latest != null && bands != null && latest.optString("status") == RefinementJournal.STATUS_VERIFYING) {
            val reads = (0 until bands.length()).sumOf { bands.getJSONObject(it).optInt("samplesAfter", 0) }
            for (i in 0 until bands.length()) {
                val b = bands.getJSONObject(i)
                if (b.optString("verdict") == "PIOROU" && b.optInt("samplesAfter") < RefinementJournal.WORSE_MIN_SAMPLES && reads < RefinementJournal.VERIFY_PARTIAL_SAMPLES) bad("PIOROU com ${b.optInt("samplesAfter")} leituras")
            }
        }
        // J: pontos a restaurar só onde a ECU ainda tem o valor gravado
        val rp = journal.restorePoints()
        for (k in 0 until rp.length()) { if (!online || !readCurve.contentEquals(curve)) break; val p = rp.getJSONObject(k); if (curve[p.getInt("index")] != p.getInt("currentRaw")) bad("restaurar oferece ponto que a ECU já não tem (${p.getInt("index")})") }
    }

    fun step() {
        val r = rnd.nextInt(100)
        when {
            r < 38 -> drive()
            r < 60 -> tick()
            r < 62 -> readEcu()
            r < 66 -> automatch()
            r < 69 -> restart(rnd.nextInt(3))
            r < 72 -> { ev("EN" + (1 - enabled)); enabled = 1 - enabled; if (enabled == 0) pendingAutoMatchIn = -1 }
            r < 76 -> { manual = !manual; pilot.setManual(manual) }
            r < 86 -> { // gravar proposta (só se a tela oferece o botão)
                val act = last.optJSONObject("action")
                if (act != null && act.optString("kind") == "review" && act.optBoolean("enabled")) {
                    val pts = last.getJSONObject("proposal").getJSONArray("points")
                    val truthPending = pendingAutoMatchIn >= 0
                    if (write(pts, "Refino OMEGAS: curva refinada confirmada")) { writes++; if (truthPending && !manual) unsafeWrites++ }
                    readEcu(); tick()
                }
            }
            r < 89 -> { // gravação manual da Curva K em um ponto
                val i = rnd.nextInt(30); val arr = JSONArray().put(JSONObject().put("index", i).put("currentRaw", curve[i]).put("targetRaw", (curve[i] * 1.03).toInt()))
                if (write(arr, "Ajuste manual confirmado na UI clean-slate")) manualWrites++
                readEcu()
            }
            r < 91 -> { ev("MAPAK"); cycle.mapKWritten(); mapKWrites++ }
            r < 94 -> { // restaurar
                val act = last.optJSONObject("action")
                if (act != null && act.optString("kind") == "restore" && act.optBoolean("enabled")) {
                    val pts = journal.restorePoints()
                    if (write(pts, "Refino OMEGAS: restaurar pontos que pioraram")) restores++
                    readEcu(); tick()
                }
            }
            r < 96 -> { online = !online; ev("ONLINE=$online") }
            r < 98 -> reopen()
            else -> { readEcu(); drive(); tick() }
        }
    }
}

fun main(args: Array<String>) {
    val runs = args.firstOrNull()?.toIntOrNull() ?: 300
    val stepsPer = args.getOrNull(1)?.toIntOrNull() ?: 400
    var total = 0
    val all = ArrayList<String>()
    var agg = LongArray(10)
    for (seed in 1..runs) {
        val w = FakeEcuWorld(seed)
        try { repeat(stepsPer) { w.step() } } catch (e: Throwable) { all += "seed=$seed EXCEÇÃO ${e::class.simpleName}: ${e.message}\n" + e.stackTrace.take(4).joinToString("\n") { "    $it" } }
        all += w.violations
        total += w.violations.size
        agg[0] += w.writes.toLong(); agg[1] += w.unsafeWrites.toLong(); agg[2] += w.gateClosedTicks.toLong(); agg[3] += w.restarts.toLong()
        agg[4] += w.reopens.toLong(); agg[5] += w.autoMatches.toLong(); agg[6] += w.restores.toLong(); agg[7] += w.manualWrites.toLong(); agg[8] += w.mapKWrites.toLong()
        w.dir.deleteRecursively()
    }
    println("CRUZAMENTOS: $runs mundos × $stepsPer passos")
    println("eventos: gravações do Refino=${agg[0]} (com AutoMatch pendente no modelo: ${agg[1]}), restaurar=${agg[6]}, manuais=${agg[7]}, MapaK=${agg[8]}, readquirir=${agg[3]}, reabrir=${agg[4]}, AutoMatch=${agg[5]}, ticks com porta fechada=${agg[2]}")
    println("VIOLAÇÕES: ${all.size}")
    all.take(25).forEach { println("  $it") }
    if (all.isNotEmpty()) kotlin.system.exitProcess(1)
}
