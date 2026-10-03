package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp
import java.util.concurrent.atomic.AtomicLong

/**
 * Acumulador de equivalência GNV × gasolina a partir da telemetria MP48.
 *
 * Cada leitura "estável" (3 quadros seguidos, ≤1,2 s, RPM ±150, MAP ±0,03 bar) é
 * reduzida à média dos 3 quadros — isso remove o zigue-zague de 8↔9 ms que existe
 * até na gasolina. A gasolina vira a referência por RPM×MAP (independe da Curva K e
 * persiste); o GNV vale só para a curva vigente e é descartado quando a curva ou o
 * mapa mudam. Um par (t_gasolina mediano no mesmo RPM±150/MAP±0,02, t_no_GNV) diz
 * qual K a condução real pede: K_alvo(t_gas) = K(t_gnv)·t_gnv/t_gas.
 *
 * Paridade com tools/autocal_refine/blind_telemetry_test.py (stable_frames,
 * telemetry_pairs). Puro: não fala com a ECU; persiste num arquivo JSON.
 */
class EquivalenceLedger(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val FORMAT = "omegas-equivalence-ledger-v1"
        const val STABLE_WINDOW_MS = 1_200L
        const val STABLE_RPM_SPREAD = 150.0
        const val STABLE_MAP_SPREAD = 0.03
        const val MATCH_RPM = 150.0
        const val MATCH_MAP = 0.02
        const val MIN_PETROL_MS = 1.0
        /** Teto por região RPM×MAP (célula = janela de casamento 150 rpm × 0,02 bar). */
        const val CELL_CAP = 30
        /** Teto de segurança por combustível; acima dele sai a leitura mais antiga da região mais cheia. */
        const val MAX_PETROL_OBS = 20_000
        const val MAX_GAS_OBS = 10_000

        fun cellKey(rpm: Double, map: Double): Long = Math.floorDiv(rpm.toLong(), MATCH_RPM.toLong()) * 1_000_003L +
            Math.floorDiv((map * 1_000).toLong(), (MATCH_MAP * 1_000).toLong())
        /** Abaixo disso a ECU tem estratégia de lenta própria: fora do índice de condução. */
        const val DRIVING_MIN_RPM = 1_000.0
        val BANDS = listOf(3.0 to 4.5, 4.5 to 6.0, 6.0 to 7.5, 7.5 to 9.0, 9.0 to 12.0)
        private const val SAVE_INTERVAL_MS = 60_000L

        /** Impressão digital da Curva K (mesma forma usada pela bridge sobre o snapshot da ECU). */
        fun fingerprint(mulActRaw: IntArray): String = JSONArray(mulActRaw.toList()).toString().hashCode().toString(16)
        /** Tempo morto do injetor de gás medido no histórico (gás ≈ gasolina×K×Mapa/100 + 0,99 ms). */
        const val GAS_DEAD_TIME_MS = 0.99
        /** Pontos maduros mínimos da curva de gasolina da ECU para servir de referência. */
        const val ECU_REF_MIN_POINTS = 6
        /** Faixa de MAP mínima coberta pela curva da ECU (uma curva curta demais extrapola). */
        const val ECU_REF_MIN_SPAN_BAR = 0.20
        /** Quanto fora da faixa da curva da ECU ainda vale (bar). */
        const val ECU_REF_MARGIN_BAR = 0.03
    }

    data class Frame(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double, val gasMs: Double = 0.0)
    data class Obs(val t: Long, val rpm: Double, val map: Double, val petrolMs: Double)
    /** [ecuRef] = a referência de gasolina veio da curva de gasolina da ECU (MAP), não de leituras próprias. */
    data class EvidencePair(val petrolRefMs: Double, val gasPetrolMs: Double, val rpm: Double, val ecuRef: Boolean = false)

    private val lock = Any()
    private val window = ArrayDeque<Frame>(3)
    /**
     * Leituras guardadas por região RPM×MAP. Antes era uma fila única: com telemetria a ~80 ms
     * ela se renovava em poucos minutos e regiões inteiras sumiam só por passar o tempo
     * (o operador via "nossos pontos" 36 → 18 → 22). Agora cada região guarda as suas
     * CELL_CAP leituras mais recentes e não perde lugar para outra.
     */
    private class CellLane(private val cap: Int) : AbstractCollection<Obs>() {
        private val cells = LinkedHashMap<Long, ArrayDeque<Pair<Long, Obs>>>()
        private var seq = 0L
        private var count = 0
        private var flat: List<Obs>? = null
        override val size: Int get() = count
        override fun iterator(): Iterator<Obs> = flatten().iterator()
        fun add(obs: Obs) {
            val cell = cells.getOrPut(cellKey(obs.rpm, obs.map)) { ArrayDeque() }
            cell.addLast(seq++ to obs)
            count++
            if (cell.size > CELL_CAP) { cell.removeFirst(); count-- }
            while (count > cap) {
                val fullest = cells.values.maxByOrNull { it.size } ?: break
                fullest.removeFirst(); count--
            }
            flat = null
        }
        fun clear() { cells.clear(); count = 0; flat = null }
        private fun flatten(): List<Obs> = flat ?: cells.values.flatten().sortedBy { it.first }.map { it.second }.also { flat = it }
    }

    private val petrol = CellLane(MAX_PETROL_OBS)
    private val gas = CellLane(MAX_GAS_OBS)
    private var gasEpochReason = "INICIO"
    private var gasEpochAt = 0L
    private var curveFingerprint: String? = null
    private var lastSaveAt = 0L
    private var gasUsefulRpmMs = 0.0
    private var airRpmBar = 0.0
    private var dirty = false
    @Volatile private var cachedIndex: JSONObject? = null
    private var cachedDense: Triple<Double, Int, JSONObject>? = null
    private var cachedPairs: Pair<Long, List<EvidencePair>>? = null
    private val revisionCounter = AtomicLong(0L)
    /**
     * Curva de gasolina que a ECU já tem (pontos maduros MAP × Petrol Inj.). A ECU entrega isso ao conectar,
     * mesmo num app recém-instalado, e vale como referência onde o app não mediu a gasolina. Não é gravada
     * no arquivo: sempre vem da ECU conectada. Leituras próprias de gasolina têm precedência.
     */
    @Volatile private var ecuPetrolRef: List<Pair<Double, Double>> = emptyList()

    /** Muda quando qualquer leitura entra, a época do GNV recomeça ou o arquivo é carregado. */
    fun revision(): Long = revisionCounter.get()

    private fun touched() {
        revisionCounter.incrementAndGet()
        cachedIndex = null
        cachedDense = null
        cachedPairs = null
    }

    init { load() }

    /**
     * Atualiza a curva de gasolina da ECU: pares (MAP bar, Petrol Inj. ms) dos pontos maduros.
     * Curva curta demais, estreita demais ou fora do físico é ignorada (volta a "sem referência da ECU").
     * Só mexe nas contas (revisão) quando a curva realmente mudou.
     */
    fun setEcuPetrolReference(points: List<Pair<Double, Double>>) {
        val clean = points.filter { (m, t) -> m.isFinite() && t.isFinite() && m in 0.05..2.5 && t in MIN_PETROL_MS..40.0 }
            .groupBy { Math.round(it.first * 1_000) }
            .map { (_, group) -> group.sumOf { it.first } / group.size to group.sumOf { it.second } / group.size }
            .sortedBy { it.first }
        val usable = if (clean.size >= ECU_REF_MIN_POINTS && clean.last().first - clean.first().first >= ECU_REF_MIN_SPAN_BAR) clean else emptyList()
        synchronized(lock) {
            if (usable == ecuPetrolRef) return
            ecuPetrolRef = usable
            touched()
        }
    }

    private fun ecuReferenceAt(map: Double, ref: List<Pair<Double, Double>>): Double? {
        if (ref.isEmpty() || map < ref.first().first - ECU_REF_MARGIN_BAR || map > ref.last().first + ECU_REF_MARGIN_BAR) return null
        if (map <= ref.first().first) return ref.first().second
        if (map >= ref.last().first) return ref.last().second
        for (i in 0 until ref.size - 1) {
            val (m0, t0) = ref[i]
            val (m1, t1) = ref[i + 1]
            if (map in m0..m1) return if (m1 > m0) t0 + (t1 - t0) * (map - m0) / (m1 - m0) else t0
        }
        return null
    }

    /** Alimenta um quadro de telemetria (fuel = GASOLINA/GNV/…). */
    fun accept(frame: Frame) {
        val obs: Obs?
        synchronized(lock) {
            if (frame.fuel != "GASOLINA" && frame.fuel != "GNV" || frame.rpm <= 0 || frame.map <= 0 || frame.petrolMs < MIN_PETROL_MS) {
                window.clear()
                return
            }
            if (frame.fuel == "GNV" && frame.rpm >= DRIVING_MIN_RPM && frame.gasMs > 0.0) {
                gasUsefulRpmMs += (frame.gasMs - GAS_DEAD_TIME_MS).coerceAtLeast(0.0) * frame.rpm
                airRpmBar += frame.map * frame.rpm
            }
            if (window.isNotEmpty() && window.last().fuel != frame.fuel) window.clear()
            window.addLast(frame)
            while (window.size > 3) window.removeFirst()
            obs = stableObservation()
            if (obs != null) {
                (if (frame.fuel == "GASOLINA") petrol else gas).add(obs)
                dirty = true
                touched()
            }
        }
        if (obs != null) maybeSave()
    }

    private fun stableObservation(): Obs? {
        if (window.size < 3) return null
        val a = window.first()
        val c = window.last()
        if (c.t - a.t > STABLE_WINDOW_MS) return null
        val rpmSpread = window.maxOf { it.rpm } - window.minOf { it.rpm }
        val mapSpread = window.maxOf { it.map } - window.minOf { it.map }
        if (rpmSpread > STABLE_RPM_SPREAD || mapSpread > STABLE_MAP_SPREAD) return null
        val middle = window.elementAt(1)
        return Obs(middle.t, window.sumOf { it.rpm } / 3.0, window.sumOf { it.map } / 3.0, window.sumOf { it.petrolMs } / 3.0)
    }

    /** A curva/mapa mudou: o GNV medido com a curva antiga deixa de valer. A gasolina fica. */
    fun resetGas(reason: String) = synchronized(lock) {
        gas.clear()
        gasUsefulRpmMs = 0.0
        airRpmBar = 0.0
        gasEpochReason = reason
        gasEpochAt = clock()
        dirty = true
        touched()
    }.also { maybeSave(force = true) }

    /** O próprio app gravou esta curva: adota sem descartar o GNV medido com ela. */
    fun adoptCurve(fingerprint: String) {
        synchronized(lock) { curveFingerprint = fingerprint; dirty = true }
        maybeSave(force = true)
    }

    /** Alinha à Curva K lida da ECU; se mudou por fora do app (ProgBase, AutoMatch), descarta o GNV. */
    fun alignCurve(fingerprint: String) {
        val changed = synchronized(lock) {
            val previous = curveFingerprint
            curveFingerprint = fingerprint
            dirty = true
            previous != null && previous != fingerprint
        }
        if (changed) resetGas("CURVA_K_MUDOU_FORA_DO_APP") else maybeSave()
    }

    /** Pares (t_gasolina de referência, t_no_GNV) para a curva vigente. */
    fun pairs(): List<EvidencePair> = synchronized(lock) {
        val revision = revisionCounter.get()
        cachedPairs?.takeIf { it.first == revision }?.let { return@synchronized it.second }
        val computed = computePairs()
        cachedPairs = revision to computed
        computed
    }

    /**
     * Pares que podem corrigir a Curva K: só condução (RPM ≥ [DRIVING_MIN_RPM]) e Petrol Inj. acima
     * do piso da telemetria. A marcha lenta (~870 rpm, ~4,5 ms) tem estratégia própria da ECU e,
     * se entrasse aqui, puxava a curva da faixa de 4,5 ms para baixo e criava um degrau.
     */
    fun drivingPairs(): List<EvidencePair> =
        pairs().filter { it.rpm >= DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }

    private fun computePairs(): List<EvidencePair> {
        // Grade RPM×MAP com célula = janela de casamento: só as 3×3 células vizinhas podem casar.
        // Mesmo resultado da busca exaustiva, sem 4000×1500 comparações por recálculo na multimídia.
        fun cell(rpm: Double, map: Double) = Math.floorDiv(rpm.toLong(), MATCH_RPM.toLong()) * 1_000_003L +
            Math.floorDiv((map * 1_000).toLong(), (MATCH_MAP * 1_000).toLong())
        val grid = HashMap<Long, MutableList<Obs>>()
        petrol.forEach { grid.getOrPut(cell(it.rpm, it.map)) { ArrayList() }.add(it) }
        val matches = ArrayList<Double>()
        val ecuRef = ecuPetrolRef
        return gas.mapNotNull { g ->
            matches.clear()
            val r0 = Math.floorDiv(g.rpm.toLong(), MATCH_RPM.toLong())
            val m0 = Math.floorDiv((g.map * 1_000).toLong(), (MATCH_MAP * 1_000).toLong())
            for (dr in -1L..1L) for (dm in -1L..1L) {
                grid[(r0 + dr) * 1_000_003L + (m0 + dm)]?.forEach {
                    if (abs(it.rpm - g.rpm) <= MATCH_RPM && abs(it.map - g.map) <= MATCH_MAP) matches += it.petrolMs
                }
            }
            if (matches.size >= 2) { matches.sort(); EvidencePair(matches[matches.size / 2], g.petrolMs, g.rpm) }
            else ecuReferenceAt(g.map, ecuRef)?.let { EvidencePair(it, g.petrolMs, g.rpm, ecuRef = true) }
        }
    }

    /**
     * Índice de equivalência da condução (rpm ≥ 1000, ≥ 3 ms): razão mediana t_no_GNV / t_gasolina.
     * 1,00 = GNV pede exatamente o que a gasolina pede. >1 = GNV pobre (ECU compensa somando).
     */
    fun index(): JSONObject {
        cachedIndex?.let { return JSONObject(it.toString()).put("gasPerAir", gasPerAir() ?: JSONObject.NULL) }
        val revisionAtStart = revisionCounter.get()
        val all = drivingPairs()
        fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val bands = JSONArray()
        BANDS.forEach { (lo, hi) ->
            val sel = all.filter { it.petrolRefMs >= lo && it.petrolRefMs < hi }
            val ratio = median(sel.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", sel.size)
                .put("ratio", ratio ?: JSONObject.NULL)
                // Fração dos pares da faixa cuja gasolina veio da curva da ECU (mais grossa que a própria).
                .put("ecuShare", if (sel.isEmpty()) 0.0 else sel.count { it.ecuRef }.toDouble() / sel.size))
        }
        val global = median(all.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
        val petrolCount: Int
        val gasCount: Int
        val reason: String
        val at: Long
        synchronized(lock) { petrolCount = petrol.size; gasCount = gas.size; reason = gasEpochReason; at = gasEpochAt }
        val ecuPairs = all.count { it.ecuRef }
        val ownPairs = all.size - ecuPairs
        val ecuPoints = ecuPetrolRef.size
        // De onde vem a gasolina de referência (a tela e o piloto dizem isso em palavras).
        val petrolReference = when {
            ecuPairs > 0 && ownPairs > 0 -> "MISTA"
            ecuPairs > 0 -> "ECU"
            ownPairs > 0 || petrolCount >= 40 -> "PROPRIA"
            ecuPoints > 0 -> "ECU"
            else -> "NENHUMA"
        }
        val result = JSONObject()
            .put("ok", true)
            .put("format", FORMAT)
            .put("ratio", global ?: JSONObject.NULL)
            .put("samples", all.size)
            .put("petrolObservations", petrolCount)
            .put("gasObservations", gasCount)
            .put("gasEpochReason", reason)
            .put("gasEpochAt", at)
            .put("petrolReference", petrolReference)
            .put("ecuPetrolPoints", ecuPoints)
            .put("ecuReferencePairs", ecuPairs)
            .put("ownReferencePairs", ownPairs)
            .put("drivingMinRpm", DRIVING_MIN_RPM)
            .put("revision", revisionAtStart)
            // Gás útil (tempo de gás − tempo morto) por unidade de ar admitido (MAP×RPM), condução em GNV:
            // independe do trânsito; compara calibrações no mesmo carro. Unidade relativa.
            .put("gasPerAir", gasPerAir() ?: JSONObject.NULL)
            .put("bands", bands)
            .put("automatic", false)
        // Uma leitura que entrou durante o cálculo invalida o resultado: não guardar índice velho.
        if (revisionCounter.get() == revisionAtStart) cachedIndex = result
        return JSONObject(result.toString())
    }

    /** Identidade observacional da época, sem tocar no acumulador. */
    fun gasEpochToken(): String = synchronized(lock) { "$gasEpochAt:$gasEpochReason" }

    fun gasPerAir(): Double? = synchronized(lock) { if (airRpmBar > 0) gasUsefulRpmMs / airRpmBar else null }

    fun typicalBandsJson(): JSONArray = TypicalInjectionBands.json(synchronized(lock) { petrol.toList() })

    /** Consulta visual: não alimenta o motor de equivalência. Cache invalidado com as observações. */
    fun denseBandsJson(binBar: Double = 0.025, minSamples: Int = 5): JSONObject = synchronized(lock) {
        require(binBar.isFinite() && binBar > 0.0 && minSamples > 0)
        cachedDense?.takeIf { it.first == binBar && it.second == minSamples }?.let {
            return@synchronized JSONObject(it.third.toString())
        }
        fun lane(observations: Collection<Obs>): JSONArray {
            val bins = linkedMapOf<Long, MutableList<Obs>>()
            observations.forEach { o ->
                if (o.map.isFinite() && o.rpm.isFinite() && o.petrolMs.isFinite()) {
                    val key = kotlin.math.floor(o.map / binBar + 1e-10).toLong()
                    bins.getOrPut(key) { mutableListOf() }.add(o)
                }
            }
            return JSONArray().apply {
                bins.forEach { (key, values) -> if (values.size >= minSamples) put(JSONObject()
                    .put("mapBar", (key + 0.5) * binBar)
                    .put("tpetMs", PresentationMedian.of(values.map { it.petrolMs }))
                    .put("samples", values.size)
                    .put("lastAtMs", values.maxOf { it.t })
                    .put("rpmMedian", PresentationMedian.of(values.map { it.rpm }))
                    .put("idleShare", values.count { it.rpm < DRIVING_MIN_RPM }.toDouble() / values.size)) }
            }
        }
        val result = JSONObject().put("petrol", lane(petrol)).put("gas", lane(gas))
            .put("binBar", binBar).put("minSamples", minSamples)
        cachedDense = Triple(binBar, minSamples, result)
        JSONObject(result.toString())
    }

    // ------------------------------------------------------------ persistência

    private fun maybeSave(force: Boolean = false) {
        val target = file ?: return
        val now = clock()
        val payload = synchronized(lock) {
            if (!dirty || (!force && now - lastSaveAt < SAVE_INTERVAL_MS)) return
            lastSaveAt = now
            dirty = false
            toJson()
        }
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) { target.writeText(payload.toString()); tmp.delete() }
        } catch (_: Exception) {
            synchronized(lock) { dirty = true }
        }
    }

    fun flush() = maybeSave(force = true)

    private fun toJson(): JSONObject {
        fun lane(values: Collection<Obs>) = JSONArray().apply {
            values.forEach { put(JSONArray().put(it.t).put(it.rpm).put(it.map).put(it.petrolMs)) }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("curveFingerprint", curveFingerprint ?: JSONObject.NULL)
            .put("gasEpochReason", gasEpochReason)
            .put("gasEpochAt", gasEpochAt)
            .put("gasUsefulRpmMs", gasUsefulRpmMs)
            .put("airRpmBar", airRpmBar)
            .put("petrol", lane(petrol))
            .put("gas", lane(gas))
    }

    private fun load() {
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.optString("format") != FORMAT) return
            fun lane(array: JSONArray?, into: CellLane) {
                if (array == null) return
                for (i in 0 until array.length()) {
                    val row = array.optJSONArray(i) ?: continue
                    into.add(Obs(row.optLong(0), row.optDouble(1), row.optDouble(2), row.optDouble(3)))
                }
            }
            lane(root.optJSONArray("petrol"), petrol)
            lane(root.optJSONArray("gas"), gas)
            curveFingerprint = root.optString("curveFingerprint").takeIf { it.isNotBlank() && it != "null" }
            gasEpochReason = root.optString("gasEpochReason", "CARREGADO")
            gasEpochAt = root.optLong("gasEpochAt", 0L)
            gasUsefulRpmMs = root.optDouble("gasUsefulRpmMs", 0.0)
            airRpmBar = root.optDouble("airRpmBar", 0.0)
            touched()
        } catch (_: Exception) {
            petrol.clear()
            gas.clear()
        }
    }
}
