package com.omegas.prohub.diagnostics

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapa do carro dentro do resumo da sessão: Mapa K e Curva K ATIVOS (valores completos), cada mudança de ponto
 * (antes, depois, quando, quem causou, em qual tanque) e o líquido início → fim. Alimentado pelos MESMOS eventos que o
 * gravador já escreve (`k_read_map`, `k_map_state`, `k_read_curve`, `k_curve_state`, `k_batch_confirmed`,
 * `k_factor_batch_confirmed`, `autocal_native_action`): nenhum evento novo, o JSONL v1 não muda e a reconstrução de uma
 * sessão interrompida (rebuild) também enxerga as mudanças. Desconhecido é null, nunca 0. Puro: sem Android.
 */
internal class CarMapTracker(private val onMilestone: (tMs: Long, kind: String, text: String, high: Boolean) -> Unit) {
    companion object {
        const val MAP_ROWS = KMapPhysicalAxes.WRITABLE_ROWS
        const val MAP_COLS = KMapPhysicalAxes.COLUMNS
        const val CURVE_POINTS = 30
        const val MAX_BATCHES = 120
        const val MAX_LISTED_CHANGES = 600
        const val MAX_NET = 160
        /** Curva K: fator Q14 (0x4000 = 1,0). */
        const val Q14 = 16384.0
        /** Eixo da curva K em ms de gasolina: raw/512. */
        const val AXIS_DIV = 512.0

        fun causeText(cause: String): String = when (cause) {
            "DONO_MANUAL" -> "Você ajustou na tela"
            "RESTAURAR_BACKUP" -> "Você restaurou um backup"
            "CURVA_PARA_MAPA" -> "Você passou a correção da curva para o Mapa K"
            "REFINO" -> "Refino (você aprovou a correção)"
            "OWNER_AUTOMATCH" -> "AutoMatch manual (você mandou aplicar)"
            "OWNER_RESET_K" -> "Curva K voltou a 1,0 (você mandou)"
            "OWNER_RESET_ALL" -> "Recomeçar tudo (você mandou)"
            "ECU_AUTOMATCH" -> "A central aplicou o AutoMatch sozinha"
            else -> "Mudou sem registro no app (outro programa ou ação fora do app)"
        }

        /** Quem causou uma gravação do app, pelo motivo que a própria tela mandou. */
        fun causeOfReason(reason: String?): String {
            val r = (reason ?: "").lowercase()
            return when {
                r.contains("restaur") -> "RESTAURAR_BACKUP"
                r.contains("curva k →") || r.contains("curva k ->") || r.contains("refatorada") -> "CURVA_PARA_MAPA"
                r.contains("refin") -> "REFINO"
                r.contains("neutraliz") || r.contains("em 1.0") || r.contains("em 1,0") -> "OWNER_RESET_K"
                else -> "DONO_MANUAL"
            }
        }
    }

    private var mapStart: Array<IntArray>? = null
    private var mapNow: Array<IntArray>? = null
    private var mapHashStart: String? = null
    private var mapHashLast: String? = null
    private var mapAxes: JSONObject? = null
    private var mapExtra: JSONArray? = null
    private var curveStart: IntArray? = null
    private var curveNow: IntArray? = null
    private var curveAxis: IntArray? = null
    private var curveHashStart: String? = null
    private var curveHashLast: String? = null
    private val batches = ArrayList<JSONObject>()
    private var listed = 0
    private var totalCells = 0
    private var droppedBatches = 0
    private var n = 0
    private var pendingOwner: String? = null

    fun observe(type: String, data: JSONObject, tMs: Long, tankIndex: Int) {
        when (type) {
            "autocal_native_action" -> onAction(data)
            "k_read_map", "k_map_state" -> onMapState(type, data, tMs, tankIndex)
            "k_read_curve", "k_curve_state" -> onCurveState(type, data, tMs, tankIndex)
            "k_batch_confirmed" -> onMapBatch(data, tMs, tankIndex)
            "k_factor_batch_confirmed" -> onCurveBatch(data, tMs, tankIndex)
        }
    }

    private fun onAction(d: JSONObject) {
        if (d.optString("outcome") != "CONFIRMED") return
        pendingOwner = when (d.optString("action")) {
            "MANUAL_AUTOMATCH" -> "OWNER_AUTOMATCH"
            "RESET_K_FACTOR" -> "OWNER_RESET_K"
            "RESET_ALL" -> "OWNER_RESET_ALL"
            else -> pendingOwner
        }
    }

    // ------------------------------------------------------------ mapa K

    private fun parseRows(d: JSONObject): Array<IntArray>? {
        val rows = d.optJSONArray("rows") ?: return null
        if (rows.length() != MAP_ROWS) return null
        val out = Array(MAP_ROWS) { IntArray(MAP_COLS) }
        for (r in 0 until MAP_ROWS) {
            val line = rows.optJSONArray(r) ?: return null
            if (line.length() != MAP_COLS) return null
            for (c in 0 until MAP_COLS) {
                if (line.isNull(c)) return null
                val v = line.optInt(c, -1)
                if (v !in 0..255) return null
                out[r][c] = v
            }
        }
        return out
    }

    private fun copyRows(a: Array<IntArray>) = Array(a.size) { a[it].copyOf() }

    private fun cellJson(row: Int, col: Int, before: Int?, after: Int?, ms: Double? = null, rpm: Int? = null) = JSONObject()
        .put("row", row).put("column", col)
        .put("petrolMs", ms ?: KMapPhysicalAxes.petrolBins()[row]).put("rpm", rpm ?: KMapPhysicalAxes.rpmBins()[col])
        .put("before", before ?: JSONObject.NULL).put("after", after ?: JSONObject.NULL)

    private fun onMapState(type: String, d: JSONObject, tMs: Long, tank: Int) {
        val rows = parseRows(d) ?: return
        val hash = d.optString("hash").takeIf { it.isNotBlank() }
        d.optJSONObject("axes")?.let { mapAxes = it }
        d.optJSONArray("extraRow")?.let { mapExtra = it }
        val now = mapNow
        if (now == null) {
            // Primeira leitura: se a sessão já gravou células antes dela, o ponto de partida é o estado atual sem elas.
            val start = copyRows(rows)
            for (b in batches.asReversed()) {
                if (b.optString("kind") != "MAP_K") continue
                val cells = b.optJSONArray("changes") ?: continue
                for (i in 0 until cells.length()) {
                    val c = cells.getJSONObject(i)
                    if (!c.isNull("before")) start[c.getInt("row")][c.getInt("column")] = c.getInt("before")
                }
            }
            mapStart = start
            mapHashStart = hash
        } else {
            val diff = diffRows(now, rows)
            if (diff.isNotEmpty()) {
                addBatch(
                    "MAP_K", "UNKNOWN_EXTERNAL", "leitura diferente da anterior", null, mapHashLast, hash,
                    diff.map { (r, c) -> cellJson(r, c, now[r][c], rows[r][c]) }, tMs, tank,
                )
            }
        }
        mapNow = copyRows(rows)
        if (hash != null) mapHashLast = hash
    }

    private fun diffRows(a: Array<IntArray>, b: Array<IntArray>): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (r in 0 until MAP_ROWS) for (c in 0 until MAP_COLS) if (a[r][c] != b[r][c]) out += r to c
        return out
    }

    private fun onMapBatch(d: JSONObject, tMs: Long, tank: Int) {
        val ev = d.optJSONArray("confirmedEvents") ?: return
        val cells = ArrayList<JSONObject>()
        var reason: String? = null
        for (i in 0 until ev.length()) {
            val e = ev.optJSONObject(i) ?: continue
            if (!e.has("row") || !e.has("column") || e.isNull("before") || e.isNull("after")) continue
            val r = e.getInt("row"); val c = e.getInt("column")
            if (r !in 0 until MAP_ROWS || c !in 0 until MAP_COLS) continue
            reason = reason ?: e.optString("reason").takeIf { it.isNotBlank() }
            cells += cellJson(
                r, c, e.getInt("before"), e.getInt("after"),
                if (e.has("petrolMs") && !e.isNull("petrolMs")) e.optDouble("petrolMs") else null,
                if (e.has("rpm") && !e.isNull("rpm")) e.optInt("rpm") else null,
            )
            mapNow?.get(r)?.set(c, e.getInt("after"))
        }
        if (cells.isEmpty()) return
        val newHash = d.optString("newHash").takeIf { it.isNotBlank() }
        if (newHash != null) mapHashLast = newHash
        addBatch("MAP_K", causeOfReason(reason), reason, d.optString("adjustmentId").takeIf { it.isNotBlank() },
            d.optString("oldHash").takeIf { it.isNotBlank() }, newHash, cells, tMs, tank)
    }

    // ------------------------------------------------------------ curva K

    private fun intArray(a: JSONArray?, size: Int): IntArray? {
        if (a == null || a.length() != size) return null
        val out = IntArray(size)
        for (i in 0 until size) { if (a.isNull(i)) return null; out[i] = a.optInt(i) }
        return out
    }

    private fun curveHash(f: IntArray) = Totals.sha256(f.joinToString(","))

    private fun curveChange(i: Int, before: Int?, after: Int?, axis: Int?) = JSONObject()
        .put("index", i)
        .put("axisRaw", axis ?: JSONObject.NULL)
        .put("petrolMs", axis?.let { Totals.nullable(it / AXIS_DIV, 3) } ?: JSONObject.NULL)
        .put("beforeRaw", before ?: JSONObject.NULL).put("afterRaw", after ?: JSONObject.NULL)
        .put("beforeFactor", before?.let { Totals.nullable(it / Q14, 4) } ?: JSONObject.NULL)
        .put("afterFactor", after?.let { Totals.nullable(it / Q14, 4) } ?: JSONObject.NULL)

    private fun onCurveState(type: String, d: JSONObject, tMs: Long, tank: Int) {
        val f = intArray(d.optJSONArray("factorsRaw"), CURVE_POINTS) ?: return
        intArray(d.optJSONArray("axisRaw"), CURVE_POINTS)?.let { curveAxis = it }
        val hash = d.optString("hash").takeIf { it.isNotBlank() } ?: curveHash(f)
        val origin = d.optString("origin")
        val previous = intArray(d.optJSONArray("previousFactorsRaw"), CURVE_POINTS)
        val now = curveNow
        if (now == null) {
            val start = f.copyOf()
            for (b in batches.asReversed()) {
                if (b.optString("kind") != "CURVE_K") continue
                val pts = b.optJSONArray("changes") ?: continue
                for (i in 0 until pts.length()) { val p = pts.getJSONObject(i); if (!p.isNull("beforeRaw")) start[p.getInt("index")] = p.getInt("beforeRaw") }
            }
            curveStart = if (previous != null && type == "k_curve_state" && origin == "ECU_NATIVE_OR_EXTERNAL") previous.copyOf() else start
            val curveAtStart = curveStart ?: f
            curveHashStart = if (curveAtStart.contentEquals(f)) hash else curveHash(curveAtStart)
        }
        if (type == "k_curve_state" && origin == "ECU_NATIVE_OR_EXTERNAL" && previous != null) {
            val changes = (0 until CURVE_POINTS).filter { previous[it] != f[it] }.map { curveChange(it, previous[it], f[it], curveAxis?.get(it)) }
            if (changes.isNotEmpty()) {
                val cause = pendingOwner ?: "ECU_AUTOMATCH"
                pendingOwner = null
                addBatch("CURVE_K", cause, null, null, curveHashLast ?: curveHash(previous), hash, changes, tMs, tank)
            }
        } else if (now != null && !now.contentEquals(f)) {
            val changes = (0 until CURVE_POINTS).filter { now[it] != f[it] }.map { curveChange(it, now[it], f[it], curveAxis?.get(it)) }
            addBatch("CURVE_K", "UNKNOWN_EXTERNAL", "leitura diferente da anterior", null, curveHashLast, hash, changes, tMs, tank)
        }
        curveNow = f.copyOf()
        curveHashLast = hash
    }

    private fun onCurveBatch(d: JSONObject, tMs: Long, tank: Int) {
        val ev = d.optJSONArray("confirmedEvents") ?: return
        val changes = ArrayList<JSONObject>()
        var reason: String? = null
        for (i in 0 until ev.length()) {
            val e = ev.optJSONObject(i) ?: continue
            if (!e.has("index") || e.isNull("beforeRaw") || e.isNull("afterRaw")) continue
            val idx = e.getInt("index")
            if (idx !in 0 until CURVE_POINTS) continue
            reason = reason ?: e.optString("reason").takeIf { it.isNotBlank() }
            changes += curveChange(idx, e.getInt("beforeRaw"), e.getInt("afterRaw"), curveAxis?.get(idx))
            curveNow?.set(idx, e.getInt("afterRaw"))
        }
        if (changes.isEmpty()) return
        val newHash = d.optString("newHash").takeIf { it.isNotBlank() }
        if (newHash != null) curveHashLast = newHash
        addBatch("CURVE_K", causeOfReason(reason), reason, d.optString("adjustmentId").takeIf { it.isNotBlank() },
            d.optString("oldHash").takeIf { it.isNotBlank() }, newHash, changes, tMs, tank)
    }

    // ------------------------------------------------------------ registro

    private fun addBatch(
        kind: String, cause: String, reason: String?, adjustmentId: String?, oldHash: String?, newHash: String?,
        changes: List<JSONObject>, tMs: Long, tank: Int,
    ) {
        n += 1
        totalCells += changes.size
        val room = (MAX_LISTED_CHANGES - listed).coerceAtLeast(0)
        val shown = changes.take(room)
        listed += shown.size
        val what = if (kind == "MAP_K") "Mapa K: ${changes.size} célula${if (changes.size == 1) "" else "s"}" else "Curva K: ${changes.size} ponto${if (changes.size == 1) "" else "s"}"
        val verb = if (cause == "UNKNOWN_EXTERNAL" || cause == "ECU_AUTOMATCH") "mudou" else "gravado"
        onMilestone(tMs, if (kind == "MAP_K") "map_change" else "curve_change", "$what $verb. ${causeText(cause)}.", true)
        if (batches.size >= MAX_BATCHES) { droppedBatches += 1; return }
        batches.add(
            JSONObject().put("n", n).put("tMs", tMs).put("tankIndex", tank).put("kind", kind)
                .put("cause", cause).put("causeText", causeText(cause))
                .put("reason", reason ?: JSONObject.NULL).put("adjustmentId", adjustmentId ?: JSONObject.NULL)
                .put("oldHash", oldHash ?: JSONObject.NULL).put("newHash", newHash ?: JSONObject.NULL)
                .put("cellsChanged", changes.size)
                .put("changesTruncated", shown.size < changes.size)
                .put("changes", JSONArray(shown)),
        )
    }

    // ------------------------------------------------------------ saída

    private fun rowsJson(a: Array<IntArray>?): Any = a?.let { rows -> JSONArray().also { out -> rows.forEach { out.put(JSONArray(it.toList())) } } } ?: JSONObject.NULL

    private fun pointsJson(f: IntArray?): Any = f?.let { arr ->
        JSONArray().also { out ->
            arr.forEachIndexed { i, raw ->
                val axis = curveAxis?.get(i)
                out.put(
                    JSONObject().put("index", i).put("axisRaw", axis ?: JSONObject.NULL)
                        .put("petrolMs", axis?.let { Totals.nullable(it / AXIS_DIV, 3) } ?: JSONObject.NULL)
                        .put("factorRaw", raw).put("factor", Totals.nullable(raw / Q14, 4)),
                )
            }
        }
    } ?: JSONObject.NULL

    fun carMapJson(): JSONObject {
        val axes = JSONObject().put("rowAxis", "petrolMs").put("columnAxis", "rpm")
            .put("petrolMsBins", JSONArray(KMapPhysicalAxes.petrolBins().toList()))
            .put("rpmBins", JSONArray(KMapPhysicalAxes.rpmBins().toList()))
        val map = JSONObject().put("known", mapNow != null)
            .put("hashStart", mapHashStart ?: JSONObject.NULL).put("hashLast", mapHashLast ?: JSONObject.NULL)
            .put("axes", axes).put("rows", rowsJson(mapNow)).put("rowsAtStart", rowsJson(mapStart))
            .put("extraRow", mapExtra ?: JSONObject.NULL)
            .put("valueNote", "valor cru 0-255 gravado na central, 12 linhas (ms de gasolina) x 12 colunas (rpm); piso de segurança do app = 100")
        val curve = JSONObject().put("known", curveNow != null)
            .put("hashStart", curveHashStart ?: JSONObject.NULL).put("hashLast", curveHashLast ?: JSONObject.NULL)
            .put("points", pointsJson(curveNow)).put("pointsAtStart", pointsJson(curveStart))
            .put("valueNote", "fator Q14 (16384 = 1,0); teto de escrita do app = 1,8; eixo em ms de gasolina (axisRaw/512)")
        return JSONObject().put("map", map).put("curve", curve)
    }

    fun changeLogJson(): JSONArray = JSONArray(batches.map { JSONObject(it.toString()) })

    fun changeLogMeta(): JSONObject = JSONObject().put("batches", n).put("cellsChangedTotal", totalCells)
        .put("listedChanges", listed).put("droppedBatches", droppedBatches)
        .put("truncated", droppedBatches > 0 || listed < totalCells)

    /** Líquido início → fim da sessão (célula a célula): o que ficou diferente, não o caminho. */
    fun netChangeJson(): JSONObject {
        val map = JSONArray()
        val s = mapStart; val e = mapNow
        if (s != null && e != null) {
            loop@ for (r in 0 until MAP_ROWS) for (c in 0 until MAP_COLS) if (s[r][c] != e[r][c]) {
                if (map.length() >= MAX_NET) break@loop
                map.put(JSONObject().put("row", r).put("column", c).put("petrolMs", KMapPhysicalAxes.petrolBins()[r])
                    .put("rpm", KMapPhysicalAxes.rpmBins()[c]).put("start", s[r][c]).put("end", e[r][c]))
            }
        }
        val curve = JSONArray()
        val cs = curveStart; val ce = curveNow
        if (cs != null && ce != null) {
            for (i in 0 until CURVE_POINTS) if (cs[i] != ce[i]) {
                curve.put(JSONObject().put("index", i).put("petrolMs", curveAxis?.get(i)?.let { Totals.nullable(it / AXIS_DIV, 3) } ?: JSONObject.NULL)
                    .put("startRaw", cs[i]).put("endRaw", ce[i])
                    .put("startFactor", Totals.nullable(cs[i] / Q14, 4)).put("endFactor", Totals.nullable(ce[i] / Q14, 4)))
            }
        }
        return JSONObject().put("map", map).put("curve", curve)
    }
}

/**
 * Linha do tempo humana da sessão: só marcos que aconteceram (conexão, central reconhecida, calibração do nível lida,
 * abastecimento, trocas de combustível, gravações do dono, AutoCal, fim). Cada item: `tMs` monotônico desde o início,
 * `kind` estável e `text` em português simples. Nada é inventado: sem evento, sem linha.
 */
internal class TimelineTracker {
    companion object { const val MAX_ITEMS = 160; const val MAX_FUEL = 24; const val MAX_OWNER_ACTIONS = 10 }

    private class Item(val tMs: Long, val kind: String, val text: String, val high: Boolean, val order: Int)
    private val items = ArrayList<Item>()
    private var order = 0
    private var fuelCount = 0
    private var ownerCount = 0
    private var antiCount = 0
    private var recognized = false
    private var levelCal = false
    private var carInfoSeen = false
    private var startWall = 0L

    fun add(tMs: Long, kind: String, text: String, high: Boolean = true) {
        if (items.size >= MAX_ITEMS) {
            val drop = items.indexOfFirst { !it.high }
            if (drop < 0) return
            items.removeAt(drop)
        }
        items += Item(tMs, kind, text, high, order++)
    }

    fun setStartWall(ms: Long) { startWall = ms }

    fun observe(type: String, data: JSONObject, tMs: Long, atElapsedRel: Long?) {
        val t = atElapsedRel ?: tMs
        when (type) {
            "session_started" -> add(tMs, "session_started", "A gravação da sessão começou.")
            "lifecycle" -> lifecycle(data.optString("kind"), tMs, "")
            "lifecycle_backlog" -> data.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val wall = e.optLong("wallMs", 0L)
                    if (wall > 0L && startWall > 0L) lifecycle(e.optString("kind"), wall - startWall, " (antes de a gravação começar)")
                }
            }
            "autocal_native_snapshot", "autocal_manual_snapshot" -> if (!recognized) {
                val fields = data.optJSONArray("fields") ?: return
                for (i in 0 until fields.length()) {
                    val f = fields.optJSONObject(i) ?: continue
                    if (f.optString("key") == "MODULE_VERSION" && f.optString("status") == "VALID") {
                        val v = f.optJSONArray("rawValues")?.takeIf { it.length() > 0 && !it.isNull(0) }?.optInt(0) ?: continue
                        recognized = true
                        add(tMs, "central_recognized", "Central reconhecida (módulo do AutoCal, versão $v).")
                        break
                    }
                }
            }
            "level_calibration" -> if (!levelCal) {
                levelCal = true
                val refs = data.optJSONArray("refs")
                if (data.optBoolean("complete") && refs != null && refs.length() == 4) {
                    add(tMs, "level_calibration", "Calibração do nível do cilindro lida da central (referências ${(0 until 4).joinToString(", ") { refs.optInt(it).toString() }}).")
                } else add(tMs, "level_calibration", "Calibração do nível lida só em parte; o que faltou fica como — (desconhecido).")
            }
            "car_info" -> if (!carInfoSeen) { carInfoSeen = true; add(tMs, "multimedia", "Multimídia do carro conectada: o painel começou a ser lido.") }
            "refuel_detected" -> add(t, "refuel", "Abastecimento detectado: o nível foi de ${data.opt("fromRaw")} para ${data.opt("toRaw")} (255 = vazio).")
            "fuel_change" -> if (fuelCount < MAX_FUEL) {
                fuelCount += 1
                val to = data.optString("to"); val from = data.optString("from")
                val rpm = if (data.isNull("rpm")) "" else ", ${data.optInt("rpm")} rpm"
                val text = when {
                    to == "GASOLINA" && from == "GNV" -> "O carro trocou de GNV para gasolina$rpm."
                    to == "GNV" && from == "GASOLINA" -> "O carro voltou para o GNV$rpm."
                    to == "GNV" -> "O carro passou a rodar a GNV$rpm."
                    to == "GASOLINA" -> "O carro passou a rodar a gasolina$rpm."
                    to == "MOTOR_OFF" -> "O motor parou."
                    from == "MOTOR_OFF" -> "O motor ligou."
                    else -> return
                }
                add(atElapsedRel ?: tMs, "fuel_change", text, high = false)
            }
            "autocal_native_action" -> {
                val label = data.optString("label").takeIf { it.isNotBlank() } ?: data.optString("action")
                val ok = data.optString("outcome") == "CONFIRMED"
                add(tMs, "autocal_action", "AutoCal: $label — ${if (ok) "concluído" else "não concluído"}.")
            }
            "user_action" -> if (ownerCount < MAX_OWNER_ACTIONS) {
                ownerCount += 1
                val action = data.optString("action")
                add(tMs, "owner_action", if (action == "registerRefuel") "Você registrou um abastecimento no app." else "Você usou: $action (${data.optString("target")}).", high = false)
            }
            "ecu_command" -> {
                val reason = data.optString("reason")
                if (antiCount < 3 && data.optBoolean("ok") && reason.contains("anti", ignoreCase = true)) {
                    antiCount += 1
                    add(tMs, "anti_apagao", "Anti-apagão gravado na central.")
                }
            }
            "session_stopped" -> add(tMs, "session_stopped", "A sessão terminou (${data.optString("reason").ifBlank { "motivo não registrado" }}).")
        }
    }

    private fun lifecycle(kind: String, tMs: Long, suffix: String) {
        when (kind) {
            "usb_connected" -> add(tMs, "usb_connected", "Cabo da central conectado$suffix.")
            "usb_disconnected" -> add(tMs, "usb_disconnected", "Cabo da central desconectado$suffix.")
            "engine_exited" -> add(tMs, "engine_restart", "A leitura da central reiniciou$suffix.", high = false)
        }
    }

    fun json(): JSONArray {
        val sorted = items.sortedWith(compareBy<Item>({ it.tMs }, { it.order }))
        return JSONArray().also { arr ->
            sorted.forEach { arr.put(JSONObject().put("tMs", it.tMs).put("kind", it.kind).put("text", it.text).put("important", it.high)) }
        }
    }
}
