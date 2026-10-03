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
        /** O índice só é recalculado se algo mudou E passou ao menos isto desde o último cálculo (mudança estrutural fura). */
        const val INDEX_MIN_INTERVAL_MS = 1_000L
        /** Arquivo compacto: vetores paralelos em vez de um array por leitura. O formato antigo continua sendo lido. */
        const val LAYOUT_FLAT = "flat-v1"

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
        /** Lacuna maior que isto entre leituras estáveis de GNV abre outro episódio de condução. */
        const val EPISODE_GAP_MS = 3_000L
        /** Episódios distintos que uma faixa precisa ter para puxar proposta (= AutoMatchRefinedEngine.MIN_BAND_EPISODES). */
        const val MIN_BAND_EPISODES = AutoMatchRefinedEngine.MIN_BAND_EPISODES
    }

    data class Frame(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double, val gasMs: Double = 0.0)
    /** [episode] = trecho de condução em GNV (leituras estáveis separadas por no máximo [EPISODE_GAP_MS]); -1 = desconhecido. */
    data class Obs(val t: Long, val rpm: Double, val map: Double, val petrolMs: Double, val episode: Int = -1)
    /** [ecuRef] = a referência de gasolina veio da curva de gasolina da ECU (MAP), não de leituras próprias. */
    data class EvidencePair(
        val petrolRefMs: Double,
        val gasPetrolMs: Double,
        val rpm: Double,
        val ecuRef: Boolean = false,
        /** Episódio da leitura de GNV do par (-1 = desconhecido). */
        val episode: Int = -1,
        /** MAP médio da leitura de GNV do par (NaN = desconhecido). */
        val map: Double = Double.NaN,
    )

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
    private var episodeCounter = 0
    private var lastGasObsAt = Long.MIN_VALUE
    private var dirty = false
    @Volatile private var cachedIndex: JSONObject? = null
    private var cachedIndexRevision = -1L
    private var cachedIndexAt = 0L
    /** Muda só em mudança estrutural (reset do GNV, carga, curva da ECU): invalida o índice na hora. */
    private val structuralCounter = AtomicLong(0L)
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

    /**
     * [structural] = reset do GNV, carga do arquivo, curva da ECU: o índice cacheado deixa de valer já.
     * Leitura estável nova só avança a revisão; o índice é recalculado quando passar [INDEX_MIN_INTERVAL_MS]
     * (antes cada janela estável, ~2×/3 s, jogava fora o índice e reordenava até 30 mil leituras).
     */
    private fun touched(structural: Boolean = false) {
        revisionCounter.incrementAndGet()
        if (structural) {
            structuralCounter.incrementAndGet()
            cachedIndex = null
        }
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
            touched(structural = true)
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
            val stable = stableObservation()
            obs = if (stable != null && frame.fuel == "GNV") {
                if (lastGasObsAt == Long.MIN_VALUE || stable.t - lastGasObsAt > EPISODE_GAP_MS) episodeCounter++
                lastGasObsAt = stable.t
                stable.copy(episode = episodeCounter)
            } else stable
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
        touched(structural = true)
    }.also { maybeSave(force = true) }

    /**
     * O próprio app gravou esta curva. O GNV medido com a curva ANTERIOR não vale para a nova (o alvo é
     * K_alvo = K(t_gnv)·t_gnv/t_gas, medido sob o K que valia na hora): se a curva muda e ainda há GNV,
     * ele é descartado aqui, de forma explícita, mesmo que quem gravou esqueça o resetGas. A gasolina fica.
     * Curva igual à já adotada (ou primeira adoção, sem referência) não descarta nada.
     */
    fun adoptCurve(fingerprint: String) {
        val stale = synchronized(lock) {
            val previous = curveFingerprint
            curveFingerprint = fingerprint
            dirty = true
            previous != null && previous != fingerprint && gas.size > 0
        }
        if (stale) resetGas("CURVA_K_GRAVADA_PELO_APP") else maybeSave(force = true)
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

    /** Cópias das leituras estáveis, na ordem de chegada (o cérebro ajusta as Curvas Próprias sobre elas). */
    fun petrolObservations(): List<Obs> = synchronized(lock) { petrol.toList() }

    fun gasObservations(): List<Obs> = synchronized(lock) { gas.toList() }

    /**
     * Pares que podem corrigir a Curva K: só condução (RPM ≥ [DRIVING_MIN_RPM]) e Petrol Inj. acima
     * do piso da telemetria. A marcha lenta (~870 rpm, ~4,5 ms) tem estratégia própria da ECU e,
     * se entrasse aqui, puxava a curva da faixa de 4,5 ms para baixo e criava um degrau.
     */
    fun drivingPairs(): List<EvidencePair> =
        pairs().filter { it.rpm >= DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }

    private var cachedFine: Pair<Long, List<FineBins.Bin>>? = null

    /**
     * Lote H: os 54 bins finos da condução (derivados dos pares, que já persistem: nada novo no arquivo). Cache por
     * revisão; a leitura estável nova só avança a revisão e o recálculo é O(pares), uma vez por consulta.
     */
    fun fineBins(): List<FineBins.Bin> {
        synchronized(lock) {
            val revision = revisionCounter.get()
            cachedFine?.takeIf { it.first == revision }?.let { return it.second }
            val computed = FineBins.aggregate(drivingPairs())
            cachedFine = revision to computed
            return computed
        }
    }

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
            if (matches.size >= 2) { matches.sort(); EvidencePair(matches[matches.size / 2], g.petrolMs, g.rpm, episode = g.episode, map = g.map) }
            else ecuReferenceAt(g.map, ecuRef)?.let { EvidencePair(it, g.petrolMs, g.rpm, ecuRef = true, episode = g.episode, map = g.map) }
        }
    }

    /**
     * Índice de equivalência da condução (rpm ≥ 1000, ≥ 3 ms): razão mediana t_no_GNV / t_gasolina.
     * 1,00 = GNV pede exatamente o que a gasolina pede. >1 = GNV pobre (ECU compensa somando).
     */
    fun index(): JSONObject {
        cachedIndex?.let { cached ->
            val unchanged = cachedIndexRevision == revisionCounter.get()
            if (unchanged || clock() - cachedIndexAt < INDEX_MIN_INTERVAL_MS) {
                return JSONObject(cached.toString()).put("gasPerAir", gasPerAir() ?: JSONObject.NULL)
            }
        }
        val revisionAtStart = revisionCounter.get()
        val structuralAtStart = structuralCounter.get()
        val all = drivingPairs()
        fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val bands = JSONArray()
        BANDS.forEach { (lo, hi) ->
            val sel = all.filter { it.petrolRefMs >= lo && it.petrolRefMs < hi }
            val ratio = median(sel.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
            val episodes = sel.map { it.episode }
            val episodeCount = if (sel.isNotEmpty() && episodes.all { it >= 0 }) episodes.toSet().size else null
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", sel.size)
                .put("episodes", episodeCount ?: JSONObject.NULL)
                .put("ratio", ratio ?: JSONObject.NULL)
                // Fração dos pares da faixa cuja gasolina veio da curva da ECU (mais grossa que a própria).
                .put("ecuShare", if (sel.isEmpty()) 0.0 else sel.count { it.ecuRef }.toDouble() / sel.size))
        }
        val global = median(all.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
        val fine = FineBins.aggregate(all)
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
            // Lote H: as 18 faixas da ECU (agregadas de 54 bins finos) e os intervalos ENTRE os pontos da ECU.
            .put("bands18", FineBins.bands18Json(fine))
            .put("betweenBands", FineBins.betweenJson(fine))
            .put("fineGrid", JSONObject().put("count", FineBins.FINE_COUNT).put("perBand", FineBins.FINE_PER_BAND)
                .put("fromMs", FineBins.GRID_LO_MS).put("toMs", FineBins.GRID_HI_MS).put("reservoir", FineBins.RESERVOIR))
            .put("coverageGuidance", coverageGuidance(bands) ?: JSONObject.NULL)
            .put("automatic", false)
        // Mudança estrutural durante o cálculo invalida o resultado; leitura nova só o deixa "velho" (revisão).
        if (structuralCounter.get() == structuralAtStart) {
            cachedIndex = result
            cachedIndexRevision = revisionAtStart
            cachedIndexAt = clock()
        }
        return JSONObject(result.toString())
    }

    /**
     * Faixa de MAP (p10–p90, bar) em que a gasolina anda nesta faixa de Petrol Inj.: onde dirigir no GNV para cobri-la.
     * Vem das leituras de gasolina da condução; sem elas, da curva de gasolina da ECU; senão, desconhecida.
     */
    private fun bandMapRange(lo: Double, hi: Double): Pair<Double, Double>? {
        val maps = synchronized(lock) { petrol.filter { it.rpm >= DRIVING_MIN_RPM && it.petrolMs >= lo && it.petrolMs < hi }.map { it.map } }.sorted()
        if (maps.size >= 3) return maps[((maps.size - 1) * 0.1).toInt()] to maps[Math.round((maps.size - 1) * 0.9).toInt()]
        val ref = ecuPetrolRef
        if (ref.size >= 2) {
            fun mapAt(ms: Double): Double? {
                for (i in 0 until ref.size - 1) {
                    val (m0, t0) = ref[i]
                    val (m1, t1) = ref[i + 1]
                    if (ms >= t0 && ms <= t1 && t1 > t0) return m0 + (m1 - m0) * (ms - t0) / (t1 - t0)
                }
                return null
            }
            val a = mapAt(lo)
            val b = mapAt(hi)
            if (a != null && b != null) return minOf(a, b) to maxOf(a, b)
        }
        return null
    }

    /**
     * Guia de cobertura em linguagem do dono: a faixa de Petrol Inj. com mais evidência que ainda não vale
     * (menos de [MIN_BAND_EPISODES] trechos ou de 8 pares), com o MAP em que dirigir. Nulo quando todas valem.
     */
    private fun coverageGuidance(bands: JSONArray): String? {
        var pick: JSONObject? = null
        for (i in 0 until bands.length()) {
            val b = bands.optJSONObject(i) ?: continue
            val samples = b.optInt("samples")
            val episodes = if (b.isNull("episodes")) null else b.optInt("episodes")
            val lacking = samples < RefinementJournal.MIN_BAND_SAMPLES || (episodes != null && episodes < MIN_BAND_EPISODES)
            if (lacking && (pick == null || samples > pick.optInt("samples"))) pick = b
        }
        val band = pick ?: return null
        val br = java.util.Locale("pt", "BR")
        val from = band.optDouble("fromMs")
        val to = band.optDouble("toMs")
        val range = bandMapRange(from, to)
        val where = if (range != null) " entre %.2f e %.2f bar".format(br, range.first, range.second) else " nessa faixa de injeção"
        val episodes = if (band.isNull("episodes")) 0 else band.optInt("episodes")
        val progress = " (%d de %d trechos)".format(br, episodes, MIN_BAND_EPISODES)
        return "Falta dado na faixa %.1f–%.1f ms: dirija no GNV%s%s.".format(br, from, to, where, progress)
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
            toJsonText()
        }
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(target)) { target.writeText(payload); tmp.delete() }
        } catch (_: Exception) {
            synchronized(lock) { dirty = true }
        }
    }

    fun flush() = maybeSave(force = true)

    /** Número finito e curto: o arquivo guardava ~2 MB de doubles com 17 dígitos. */
    private fun num(builder: StringBuilder, value: Double, scale: Double) {
        val v = if (value.isFinite()) Math.round(value * scale) / scale else 0.0
        builder.append(v)
    }

    private fun appendLane(builder: StringBuilder, name: String, values: Collection<Obs>) {
        builder.append('"').append(name).append("\":{\"t\":[")
        var first = true
        values.forEach { if (!first) builder.append(','); builder.append(it.t); first = false }
        builder.append("],\"rpm\":[")
        first = true
        values.forEach { if (!first) builder.append(','); num(builder, it.rpm, 10.0); first = false }
        builder.append("],\"map\":[")
        first = true
        values.forEach { if (!first) builder.append(','); num(builder, it.map, 10_000.0); first = false }
        builder.append("],\"ms\":[")
        first = true
        values.forEach { if (!first) builder.append(','); num(builder, it.petrolMs, 10_000.0); first = false }
        builder.append("],\"ep\":[")
        first = true
        values.forEach { if (!first) builder.append(','); builder.append(it.episode); first = false }
        builder.append("]}")
    }

    /** Layout plano: quatro vetores paralelos por combustível (sem um JSONArray por leitura). */
    private fun toJsonText(): String {
        val b = StringBuilder(64 + (petrol.size + gas.size) * 36)
        b.append("{\"format\":").append(JSONObject.quote(FORMAT))
            .append(",\"layout\":").append(JSONObject.quote(LAYOUT_FLAT))
            .append(",\"curveFingerprint\":").append(curveFingerprint?.let { JSONObject.quote(it) } ?: "null")
            .append(",\"gasEpochReason\":").append(JSONObject.quote(gasEpochReason))
            .append(",\"gasEpochAt\":").append(gasEpochAt)
            .append(",\"gasUsefulRpmMs\":").append(if (gasUsefulRpmMs.isFinite()) gasUsefulRpmMs else 0.0)
            .append(",\"airRpmBar\":").append(if (airRpmBar.isFinite()) airRpmBar else 0.0)
            .append(',')
        appendLane(b, "petrol", petrol)
        b.append(',')
        appendLane(b, "gas", gas)
        b.append('}')
        return b.toString()
    }

    /**
     * Arquivo antigo (sem episódios): deriva-os da lacuna entre leituras de GNV. Com episódios gravados, só
     * recoloca o contador depois do maior id para o próximo trecho abrir um episódio novo.
     */
    private fun relabelEpisodes() {
        val list = gas.toList()
        if (list.isEmpty()) return
        if (list.all { it.episode >= 0 }) { episodeCounter = list.maxOf { it.episode }; return }
        var episode = 0
        var last = Long.MIN_VALUE
        val relabeled = list.sortedBy { it.t }.map { o ->
            if (last != Long.MIN_VALUE && o.t - last > EPISODE_GAP_MS) episode++
            last = o.t
            o.copy(episode = episode)
        }
        gas.clear()
        relabeled.forEach { gas.add(it) }
        episodeCounter = episode
    }

    private fun load() {
        val source = file?.takeIf { it.isFile } ?: return
        try {
            val root = JSONObject(source.readText())
            if (root.optString("format") != FORMAT) return
            // Lê os dois formatos: plano (vetores paralelos t/rpm/map/ms) e o antigo (um array [t,rpm,map,ms] por leitura).
            fun lane(name: String, into: CellLane) {
                val flat = root.optJSONObject(name)
                if (flat != null) {
                    val t = flat.optJSONArray("t") ?: return
                    val rpm = flat.optJSONArray("rpm") ?: return
                    val map = flat.optJSONArray("map") ?: return
                    val ms = flat.optJSONArray("ms") ?: return
                    val n = minOf(t.length(), rpm.length(), map.length(), ms.length())
                    val ep = flat.optJSONArray("ep")?.takeIf { it.length() >= n }
                    for (i in 0 until n) into.add(Obs(t.optLong(i), rpm.optDouble(i), map.optDouble(i), ms.optDouble(i), ep?.optInt(i, -1) ?: -1))
                    return
                }
                val array = root.optJSONArray(name) ?: return
                for (i in 0 until array.length()) {
                    val row = array.optJSONArray(i) ?: continue
                    into.add(Obs(row.optLong(0), row.optDouble(1), row.optDouble(2), row.optDouble(3)))
                }
            }
            lane("petrol", petrol)
            lane("gas", gas)
            relabelEpisodes()
            curveFingerprint = root.optString("curveFingerprint").takeIf { it.isNotBlank() && it != "null" }
            gasEpochReason = root.optString("gasEpochReason", "CARREGADO")
            gasEpochAt = root.optLong("gasEpochAt", 0L)
            gasUsefulRpmMs = root.optDouble("gasUsefulRpmMs", 0.0)
            airRpmBar = root.optDouble("airRpmBar", 0.0)
            touched(structural = true)
        } catch (_: Exception) {
            petrol.clear()
            gas.clear()
        }
    }
}
