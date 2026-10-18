package com.omegas.prohub.autocal

import com.omegas.prohub.util.AtomicJsonFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp

/**
 * Acumulador de equivalência GNV × gasolina a partir da telemetria MP48.
 *
 * Cada leitura "estável" (3 quadros seguidos, ≤1,2 s, RPM ±150, MAP ±0,03 bar) é
 * reduzida à média dos 3 quadros — isso remove o zigue-zague de 8↔9 ms que existe
 * até na gasolina. A janela zera ao emitir: leituras independentes, sem sobreposição (dono, 2026-10-16: a janela
 * deslizante fazia 20 "leituras" de ~1 s de quadros quase iguais). A gasolina vira a referência por RPM×MAP (independe da Curva K e
 * persiste); o GNV vale só para a curva vigente e é descartado quando a curva ou o
 * mapa mudam. Um par (t_gasolina mediano no mesmo RPM±150/MAP±0,02, t_no_GNV) diz
 * qual K o motor pede: K_alvo(t_gas) = K(t_gnv)·t_gnv/t_gas.
 *
 * Dono, 2026-10-11: "a curva deve aprender tudo e saber onde ajustar o quê". Todas as rotações (lenta incluída)
 * entram nos pares, no índice, na gasolina de referência e no veredito/ganho do diário; só os grupos que
 * [EquivalenceAttribution] separa como LOCAIS (coluna do Mapa K × faixa de t, diferentes das outras colunas no
 * mesmo t) saem da evidência da curva e viram proposta de ajuste local no Mapa K.
 *
 * Paridade com tools/autocal_refine/blind_telemetry_test.py (stable_frames; production_pairs = pairs(),
 * production_pairs(global_only=False) = allPairs()), exceto a janela que zera ao emitir (o script ainda desliza).
 * Puro: não fala com a ECU; persiste num arquivo JSON (AtomicJsonFile: fsync, reserva .bak, renomeação atômica).
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
        /**
         * SÓ DIAGNÓSTICO (gás por ar e fração de lenta dos pontos do gráfico): abaixo disso é "lenta". Não filtra
         * pares, índice, gasolina de referência, veredito nem ganho (dono, 2026-10-11: a curva aprende tudo).
         */
        const val IDLE_DIAGNOSTIC_RPM = 1_350.0
        val BANDS = listOf(3.0 to 4.5, 4.5 to 6.0, 6.0 to 7.5, 7.5 to 9.0, 9.0 to 12.0)
        private const val SAVE_INTERVAL_MS = 60_000L

        /** Impressão digital da Curva K (mesma forma usada pela bridge sobre o snapshot da ECU). */
        fun fingerprint(mulActRaw: IntArray): String = JSONArray(mulActRaw.toList()).toString().hashCode().toString(16)
        /** Tempo morto do injetor de gás medido no histórico (gás ≈ gasolina×K×Mapa/100 + 0,99 ms). */
        const val GAS_DEAD_TIME_MS = 0.99
        /** Pontos maduros mínimos da curva de gasolina da ECU para servir de referência (Platina 00f6fd02). */
        const val ECU_REF_MIN_POINTS = 6
        /** Faixa de MAP mínima coberta pela curva da ECU (uma curva curta demais extrapola). */
        const val ECU_REF_MIN_SPAN_BAR = 0.20
        /** Quanto fora da faixa da curva da ECU ainda vale (bar). */
        const val ECU_REF_MARGIN_BAR = 0.03
        /** Região RPM×MAP de gasolina própria com tantas leituras estáveis está madura e congela. */
        const val PETROL_FREEZE_SAMPLES = 8
    }

    /** [engineHot] = água acima do mínimo do aprendizado (null = desconhecido): gasolina fria não vira referência. */
    data class Frame @JvmOverloads constructor(
        val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double, val gasMs: Double = 0.0,
        val engineHot: Boolean? = null,
    )
    data class Obs(val t: Long, val rpm: Double, val map: Double, val petrolMs: Double)
    /**
     * [t] = quando a leitura de GNV foi feita (para separar o que foi medido antes e depois de uma gravação);
     * [ecuRef] = a gasolina de referência veio da curva de gasolina da ECU, não de leituras próprias;
     * [map] = MAP (bar) da leitura de GNV, para contar regiões RPM×MAP distintas por faixa.
     */
    data class EvidencePair(
        val petrolRefMs: Double, val gasPetrolMs: Double, val rpm: Double, val t: Long = 0L, val ecuRef: Boolean = false, val map: Double = 0.0,
    )

    private val lock = Any()
    /** Uma gravação em disco por vez (telemetria e flush do serviço podem coincidir no mesmo .tmp). */
    private val saveLock = Any()
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
        fun cellSize(obs: Obs): Int = cells[cellKey(obs.rpm, obs.map)]?.size ?: 0
        /** Remove só as leituras que [predicate] aponta (as outras regiões ficam intactas). */
        fun removeIf(predicate: (Obs) -> Boolean): Int {
            var removed = 0
            val emptied = ArrayList<Long>()
            cells.forEach { (key, cell) ->
                val before = cell.size
                cell.removeAll { predicate(it.second) }
                removed += before - cell.size
                if (cell.isEmpty()) emptied += key
            }
            emptied.forEach { cells.remove(it) }
            count -= removed
            if (removed > 0) flat = null
            return removed
        }
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
    private var cachedIndex: JSONObject? = null
    /** index(since) por instante, válido enquanto a revisão não muda (o diário pede o mesmo "depois" várias vezes). */
    private val sinceCache = HashMap<Long, JSONObject>()
    private var sinceCacheRevision = -1L
    private var cachedDense: Triple<Double, Int, JSONObject>? = null
    /** Sobe a cada mudança nas leituras: os pares só são recalculados quando algo mudou. */
    private var revision = 0L
    /** Pares da curva (sem os grupos locais) e a atribuição, por revisão: valem até a próxima mudança do ledger. */
    private var cachedEvidence: Triple<Long, List<EvidencePair>, List<EquivalenceAttribution.Group>>? = null
    /** Motivo de o arquivo não ter sido lido (ou lido em parte); vai no índice para a tela dizer em palavras. */
    private var loadError: String? = null
    /** Grupos que já eram locais (histerese 5% entra / 4% sai). Persistido. */
    private var localLatch: Set<EquivalenceAttribution.Key> = emptySet()
    /** Curva que estava na ECU antes da última gravação do app: leitura velha dela não é "mudou por fora". */
    private var adoptedFrom: String? = null
    /** Última invalidação parcial (só os pontos que a gravação mudou), para a tela explicar. */
    private var lastInvalidation: JSONObject? = null
    /**
     * Curva de gasolina que a ECU já tem (pontos maduros MAP × Petrol Inj., sem as bandas de lenta). Só completa
     * onde não há gasolina própria (Platina 00f6fd02). Persistida: reconexão, app reiniciado ou leitura com campo
     * indisponível não a apagam (erro de transporte não é "a ECU não tem gasolina").
     */
    @Volatile private var ecuPetrolRef: List<Pair<Double, Double>> = emptyList()
    /** Pontos de gasolina com atividade na ECU na última leitura conhecida (persistido). */
    private var lastPetrolActivity: Int? = null
    /** Geração de gasolina do NativeAutoCalAcquisitionEpoch (recibos de reset + contador zerado), por sessão USB. */
    private var petrolGenerationSession = 0L
    private var petrolGeneration = -1
    /** Sobe cada vez que a gasolina própria recomeça: o diário só compara antes/depois na mesma época. */
    private var petrolEpoch = 0

    init { load() }

    /**
     * Atualiza a curva de gasolina da ECU: pares (MAP bar, Petrol Inj. ms) dos pontos maduros.
     * Curva curta demais, estreita demais ou fora do físico é ignorada (volta a "sem referência da ECU").
     */
    fun setEcuPetrolReference(points: List<Pair<Double, Double>>?) {
        if (points == null) return // desconhecido: mantém a última referência
        val clean = points.filter { (m, t) -> m.isFinite() && t.isFinite() && m in 0.05..2.5 && t in MIN_PETROL_MS..40.0 }
            .groupBy { Math.round(it.first * 1_000) }
            .map { (_, group) -> group.sumOf { it.first } / group.size to group.sumOf { it.second } / group.size }
            .sortedBy { it.first }
        val usable = if (clean.size >= ECU_REF_MIN_POINTS && clean.last().first - clean.first().first >= ECU_REF_MIN_SPAN_BAR) clean else emptyList()
        synchronized(lock) {
            if (usable == ecuPetrolRef) return
            ecuPetrolRef = usable
            revision++
            cachedIndex = null
            cachedEvidence = null
            dirty = true
        }
        maybeSave(force = true)
    }

    /** Identidade da curva de gasolina da ECU em uso ("" sem ela): o diário só julga antes/depois com a mesma. */
    private fun ecuReferenceToken(ref: List<Pair<Double, Double>>): String =
        if (ref.isEmpty()) "" else JSONArray(ref.map { JSONArray().put(it.first).put(it.second) }).toString().hashCode().toString(16)

    /** Muda quando a referência de gasolina muda de época (curva da ECU ou gasolina própria recomeçada). */
    fun referenceToken(): String = synchronized(lock) { ecuReferenceToken(ecuPetrolRef) + "|" + petrolEpoch }

    /**
     * A ECU recomeçou a aquisição de gasolina ("Readquirir gasolina" ou aquisição nova): só então a gasolina
     * própria congelada é descartada e volta a aprender. Os buffers da ECU só enchem dentro de uma aquisição;
     * cair a zero, ou cair à metade ou menos (a ECU já readquiriu algumas bandas entre duas leituras), é
     * aquisição nova. O último valor é persistido (reiniciar o app no meio não perde a queda). Retorna true se descongelou.
     */
    fun notePetrolAcquisition(activePetrolPoints: Int): Boolean {
        val restarted = synchronized(lock) {
            val previous = lastPetrolActivity
            if (previous != activePetrolPoints) { lastPetrolActivity = activePetrolPoints; dirty = true }
            previous != null && previous > 0 && activePetrolPoints < previous &&
                (activePetrolPoints == 0 || (activePetrolPoints * 2 <= previous && previous - activePetrolPoints >= 3))
        }
        if (restarted) clearPetrol() else maybeSave()
        return restarted
    }

    /**
     * Geração de gasolina do NativeAutoCalAcquisitionEpoch (sobe com o recibo de "Readquirir gasolina"/reset e
     * quando o contador da ECU zera). Vale por sessão USB; persistida. Retorna true se descongelou.
     */
    fun notePetrolGeneration(usbSessionId: Long, generation: Int): Boolean {
        if (usbSessionId <= 0L || generation < 0) return false
        val restarted = synchronized(lock) {
            val sameSession = usbSessionId == petrolGenerationSession
            val bumped = sameSession && petrolGeneration >= 0 && generation > petrolGeneration
            if (!sameSession || generation != petrolGeneration) {
                petrolGenerationSession = usbSessionId
                petrolGeneration = generation
                dirty = true
            }
            bumped
        }
        if (restarted) clearPetrol() else maybeSave()
        return restarted
    }

    private fun clearPetrol() {
        synchronized(lock) {
            petrol.clear()
            petrolEpoch++
            dirty = true
            revision++
            cachedIndex = null
            cachedDense = null
            cachedEvidence = null
        }
        maybeSave(force = true)
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

    /**
     * Alimenta um quadro de telemetria (fuel = GASOLINA/GNV/…). A gasolina própria de cada região RPM×MAP
     * congela com [PETROL_FREEZE_SAMPLES] leituras estáveis com o motor quente: depois do AutoMatch a referência
     * não fica "sempre aprendendo" (só uma aquisição nova de gasolina descongela, [notePetrolAcquisition] /
     * [notePetrolGeneration]). Gasolina com o motor frio (partida, enriquecida) nunca vira referência.
     */
    fun accept(frame: Frame) {
        var obs: Obs?
        synchronized(lock) {
            if (frame.fuel != "GASOLINA" && frame.fuel != "GNV" || frame.rpm <= 0 || frame.map <= 0 || frame.petrolMs < MIN_PETROL_MS) {
                window.clear()
                return
            }
            if (frame.fuel == "GNV" && frame.rpm >= IDLE_DIAGNOSTIC_RPM && frame.gasMs > 0.0) {
                gasUsefulRpmMs += (frame.gasMs - GAS_DEAD_TIME_MS).coerceAtLeast(0.0) * frame.rpm
                airRpmBar += frame.map * frame.rpm
            }
            if (window.isNotEmpty() && window.last().fuel != frame.fuel) window.clear()
            window.addLast(frame)
            while (window.size > 3) window.removeFirst()
            obs = stableObservation()
            // Leitura emitida consome os 3 quadros: a próxima só com 3 quadros novos (independentes, sem sobreposição).
            if (obs != null) window.clear()
            if (obs != null && frame.fuel == "GASOLINA" && (frame.engineHot == false || petrol.cellSize(obs) >= PETROL_FREEZE_SAMPLES)) obs = null
            if (obs != null) {
                (if (frame.fuel == "GASOLINA") petrol else gas).add(obs)
                dirty = true
                revision++
                cachedIndex = null
                cachedDense = null
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
        lastInvalidation = null
        dirty = true
        revision++
        cachedIndex = null
        cachedDense = null
        cachedEvidence = null
    }.also { maybeSave(force = true) }

    /**
     * Gravação de Curva K em alguns pontos: só o GNV medido onde esses pontos agem deixa de valer.
     * [rangesMs] = trechos de Petrol Inj. (ms, abertos) em que o K interpolado mudou. O resto fica.
     * Retorna quantas leituras de GNV saíram.
     */
    fun invalidateGas(reason: String, rangesMs: List<Pair<Double, Double>>): Int {
        val removed = synchronized(lock) {
            val ranges = rangesMs.filter { it.first < it.second }
            val n = gas.removeIf { obs -> ranges.any { obs.petrolMs > it.first && obs.petrolMs < it.second } }
            // Gás por ar é uma média da calibração inteira: recomeça a cada calibração nova (como antes).
            gasUsefulRpmMs = 0.0
            airRpmBar = 0.0
            lastInvalidation = JSONObject().put("reason", reason).put("at", clock()).put("removed", n)
                .put("ranges", JSONArray(ranges.map { JSONArray().put(it.first).put(if (it.second.isFinite()) it.second else JSONObject.NULL) }))
            dirty = true
            revision++
            cachedIndex = null
            cachedDense = null
            cachedEvidence = null
            n
        }
        maybeSave(force = true)
        return removed
    }

    /**
     * O diário confirmou que a curva lida mudou por fora do app (inclusive a volta para a curva de antes depois de
     * várias leituras): descarta o GNV e passa a valer a curva lida.
     */
    fun curveChangedOutside(fingerprint: String) {
        synchronized(lock) {
            curveFingerprint = fingerprint
            adoptedFrom = null
        }
        resetGas("CURVA_K_MUDOU_FORA_DO_APP")
    }

    /** O próprio app gravou esta curva: adota sem descartar o GNV medido com ela. */
    fun adoptCurve(fingerprint: String) {
        synchronized(lock) {
            if (curveFingerprint != null && curveFingerprint != fingerprint) adoptedFrom = curveFingerprint
            curveFingerprint = fingerprint
            dirty = true
        }
        maybeSave(force = true)
    }

    /**
     * Alinha à Curva K lida da ECU; se mudou por fora do app (ProgBase, AutoMatch), descarta o GNV.
     * Logo depois de uma gravação do app a leitura da ECU ainda pode trazer a curva anterior por alguns
     * segundos: isso é leitura atrasada, não mudança por fora (antes descartava o GNV duas vezes).
     * Retorna true quando a curva mudou por fora.
     */
    fun alignCurve(fingerprint: String): Boolean {
        val changed = synchronized(lock) {
            when {
                fingerprint == curveFingerprint -> { adoptedFrom = null; false }
                adoptedFrom != null && fingerprint == adoptedFrom -> false
                else -> {
                    val previous = curveFingerprint
                    curveFingerprint = fingerprint
                    adoptedFrom = null
                    dirty = true
                    previous != null
                }
            }
        }
        if (changed) resetGas("CURVA_K_MUDOU_FORA_DO_APP") else maybeSave()
        return changed
    }

    /**
     * Pares (t_gasolina de referência, t_no_GNV) para a curva vigente: a única evidência de telemetria que chega
     * ao motor. Todas as rotações, lenta incluída (dono, 2026-10-11: a curva aprende tudo), menos os pares dos
     * grupos que [EquivalenceAttribution] separou como locais (esses vão para o Mapa K, não para a curva). A
     * referência é a gasolina própria (congelada) no mesmo RPM×MAP; a curva de gasolina da ECU só completa onde não
     * há própria (Platina 00f6fd02). Recalcula só quando as leituras mudam.
     */
    fun pairs(): List<EvidencePair> = evidence().second

    /** Grupos coluna do Mapa K × faixa de t com o motivo (local ou na curva). */
    fun attribution(): List<EquivalenceAttribution.Group> = evidence().third

    fun localGroupsJson(): JSONArray = EquivalenceAttribution.json(attribution().filter { it.local })

    /**
     * Pares e atribuição. Copia as leituras sob a trava e calcula FORA dela (a telemetria não espera o casamento de
     * 30 mil leituras); o resultado vale enquanto a revisão do ledger (incrementada em cada leitura aceita) não muda,
     * então mesmo retrato ⇒ mesma saída, sem prazo de relógio. Mudança
     * estrutural (gravação, reset, gasolina recomeçada, referência da ECU) zera o cache na hora.
     */
    private fun evidence(): Triple<Long, List<EvidencePair>, List<EquivalenceAttribution.Group>> {
        val rev: Long
        val petrolCopy: List<Obs>
        val gasCopy: List<Obs>
        val ecuRef: List<Pair<Double, Double>>
        val latch: Set<EquivalenceAttribution.Key>
        synchronized(lock) {
            cachedEvidence?.takeIf { it.first == revision }?.let { return it }
            rev = revision
            petrolCopy = petrol.toList()
            gasCopy = gas.toList()
            ecuRef = ecuPetrolRef
            latch = localLatch
        }
        val all = computePairs(petrolCopy, gasCopy, ecuRef)
        // Separar exige gasolina própria no mesmo RPM×MAP: a curva de gasolina da ECU não tem RPM.
        val groups = EquivalenceAttribution.classify(all.filter { !it.ecuRef }, latch)
        val local = groups.filter { it.local }.map { it.key }.toSet()
        val global = if (local.isEmpty()) all else all.filter { EquivalenceAttribution.keyOf(it.rpm, it.petrolRefMs) !in local }
        val result = Triple(rev, global, groups)
        synchronized(lock) {
            if (local != localLatch) { localLatch = local; dirty = true }
            // Só guarda se ninguém mudou o ledger durante o cálculo (senão o próximo pedido recalcula).
            cachedEvidence = result
        }
        return result
    }

    /** Todos os casamentos com gasolina própria, grupos locais incluídos (diagnóstico e evidência do ajuste local). */
    fun allPairs(): List<EvidencePair> {
        val (petrolCopy, gasCopy) = synchronized(lock) { petrol.toList() to gas.toList() }
        return computePairs(petrolCopy, gasCopy, ecuRef = emptyList())
    }

    /** Puro sobre cópias: roda fora da trava. */
    private fun computePairs(petrolObs: Collection<Obs>, gasObs: Collection<Obs>, ecuRef: List<Pair<Double, Double>>): List<EvidencePair> {
        // Grade RPM×MAP com célula = janela de casamento: só as 3×3 células vizinhas podem casar.
        // Mesmo resultado da busca exaustiva, sem 4000×1500 comparações por recálculo na multimídia.
        fun cell(rpm: Double, map: Double) = Math.floorDiv(rpm.toLong(), MATCH_RPM.toLong()) * 1_000_003L +
            Math.floorDiv((map * 1_000).toLong(), (MATCH_MAP * 1_000).toLong())
        val grid = HashMap<Long, MutableList<Obs>>()
        petrolObs.forEach { grid.getOrPut(cell(it.rpm, it.map)) { ArrayList() }.add(it) }
        val matches = ArrayList<Double>()
        return gasObs.mapNotNull { g ->
            matches.clear()
            val r0 = Math.floorDiv(g.rpm.toLong(), MATCH_RPM.toLong())
            val m0 = Math.floorDiv((g.map * 1_000).toLong(), (MATCH_MAP * 1_000).toLong())
            for (dr in -1L..1L) for (dm in -1L..1L) {
                grid[(r0 + dr) * 1_000_003L + (m0 + dm)]?.forEach {
                    if (abs(it.rpm - g.rpm) <= MATCH_RPM && abs(it.map - g.map) <= MATCH_MAP) matches += it.petrolMs
                }
            }
            if (matches.size >= 2) { matches.sort(); EvidencePair(matches[matches.size / 2], g.petrolMs, g.rpm, g.t, map = g.map) }
            else ecuReferenceAt(g.map, ecuRef)?.let { EvidencePair(it, g.petrolMs, g.rpm, g.t, ecuRef = true, map = g.map) }
        }
    }

    /**
     * Índice de equivalência (todas as rotações, ≥ 3 ms, sem os grupos locais): razão mediana t_no_GNV / t_gasolina.
     * 1,00 = GNV pede exatamente o que a gasolina pede. >1 = GNV pobre (ECU compensa somando).
     * Com [sinceMs] > 0 só conta GNV medido a partir desse instante (o "depois" de uma gravação).
     */
    fun index(sinceMs: Long = 0L): JSONObject {
        val startRevision = synchronized(lock) {
            if (sinceMs <= 0L) cachedIndex?.let { return JSONObject(it.toString()).put("gasPerAir", gasPerAir() ?: JSONObject.NULL) }
            else if (sinceCacheRevision == revision) sinceCache[sinceMs]?.let { return JSONObject(it.toString()) }
            revision
        }
        val all = pairs().filter { it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS && it.t >= sinceMs }
        val localGroups = localGroupsJson()
        fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val bands = JSONArray()
        BANDS.forEach { (lo, hi) ->
            val sel = all.filter { it.petrolRefMs >= lo && it.petrolRefMs < hi }
            val ratio = median(sel.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", sel.size)
                // Regiões RPM×MAP distintas entre as leituras da faixa: parado no mesmo ponto, 24 leituras não são 24 evidências.
                .put("cells", sel.map { cellKey(it.rpm, it.map) }.toSet().size)
                .put("ratio", ratio ?: JSONObject.NULL)
                // Fração dos pares da faixa cuja gasolina veio da curva da ECU.
                .put("ecuShare", if (sel.isEmpty()) 0.0 else sel.count { it.ecuRef }.toDouble() / sel.size))
        }
        val global = median(all.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
        val petrolCount: Int
        val gasCount: Int
        val reason: String
        val at: Long
        val invalidation: JSONObject?
        val ecuPoints: Int
        val ecuToken: String
        val epoch: Int
        synchronized(lock) {
            petrolCount = petrol.size; gasCount = gas.size; reason = gasEpochReason; at = gasEpochAt
            invalidation = lastInvalidation?.let { JSONObject(it.toString()) }
            ecuPoints = ecuPetrolRef.size
            ecuToken = ecuReferenceToken(ecuPetrolRef)
            epoch = petrolEpoch
        }
        val ecuPairs = all.count { it.ecuRef }
        val ownPairs = all.size - ecuPairs
        // De onde vem a gasolina de referência (a tela diz isso em palavras).
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
            .put("gasInvalidation", invalidation ?: JSONObject.NULL)
            .put("petrolReference", petrolReference)
            .put("ecuPetrolPoints", ecuPoints)
            .put("ecuReferencePairs", ecuPairs)
            .put("ownReferencePairs", ownPairs)
            // Identidade da referência de gasolina (o diário só julga antes/depois com a mesma).
            .put("ecuReference", ecuToken)
            .put("petrolEpoch", epoch)
            .put("petrolFreezeSamples", PETROL_FREEZE_SAMPLES)
            // Grupos coluna do Mapa K × faixa de t separados como ajuste local (fora deste índice e da curva).
            .put("localGroups", localGroups)
            // Gás útil (tempo de gás − tempo morto) por unidade de ar admitido (MAP×RPM), condução em GNV:
            // independe do trânsito; compara calibrações no mesmo carro. Unidade relativa.
            .put("gasPerAir", gasPerAir() ?: JSONObject.NULL)
            .put("bands", bands)
            // Arquivo que não pôde ser lido (ou lido em parte) ao abrir: a tela diz em palavras; nada foi zerado nem apagado.
            .put("loadError", loadError ?: JSONObject.NULL)
            .put("automatic", false)
        synchronized(lock) {
            if (revision == startRevision) {
                if (sinceMs > 0L) {
                    if (sinceCacheRevision != revision) { sinceCache.clear(); sinceCacheRevision = revision }
                    if (sinceCache.size >= 16) sinceCache.clear()
                    sinceCache[sinceMs] = JSONObject(result.toString()).put("sinceMs", sinceMs)
                } else cachedIndex = result
            }
        }
        if (sinceMs > 0L) return result.put("sinceMs", sinceMs)
        return JSONObject(result.toString())
    }

    /** Muda sempre que as leituras, a época do GNV ou a curva de gasolina da ECU mudam (chave de memo). */
    fun revision(): Long = synchronized(lock) { revision }

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
                    .put("rpmMedian", PresentationMedian.of(values.map { it.rpm }))
                    .put("idleShare", values.count { it.rpm < IDLE_DIAGNOSTIC_RPM }.toDouble() / values.size)) }
            }
        }
        val result = JSONObject().put("petrol", lane(petrol)).put("gas", lane(gas))
            .put("binBar", binBar).put("minSamples", minSamples)
        cachedDense = Triple(binBar, minSamples, result)
        JSONObject(result.toString())
    }

    // ------------------------------------------------------------ persistência

    /** Cópia do estado para serializar fora da trava (a telemetria não espera o JSON de 30 mil leituras). */
    private class Persisted(
        val petrol: List<Obs>, val gas: List<Obs>, val curveFingerprint: String?, val gasEpochReason: String, val gasEpochAt: Long,
        val gasUsefulRpmMs: Double, val airRpmBar: Double, val ecuPetrolRef: List<Pair<Double, Double>>, val lastPetrolActivity: Int?,
        val petrolGenerationSession: Long, val petrolGeneration: Int, val petrolEpoch: Int, val localLatch: Set<EquivalenceAttribution.Key>,
    )

    /** Uma gravação por vez ([saveLock], como no diário); copia sob a trava das leituras, serializa e grava fora dela. */
    private fun maybeSave(force: Boolean = false) {
        val target = file ?: return
        synchronized(saveLock) {
            val now = clock()
            val state = synchronized(lock) {
                if (!dirty || (!force && now - lastSaveAt < SAVE_INTERVAL_MS)) return
                lastSaveAt = now
                dirty = false
                Persisted(petrol.toList(), gas.toList(), curveFingerprint, gasEpochReason, gasEpochAt, gasUsefulRpmMs, airRpmBar,
                    ecuPetrolRef, lastPetrolActivity, petrolGenerationSession, petrolGeneration, petrolEpoch, localLatch)
            }
            try {
                AtomicJsonFile.write(target, toJson(state).toString())
            } catch (_: Exception) {
                synchronized(lock) { dirty = true }
            }
        }
    }

    fun flush() = maybeSave(force = true)

    private fun toJson(s: Persisted): JSONObject {
        fun lane(values: Collection<Obs>) = JSONArray().apply {
            values.forEach { put(JSONArray().put(it.t).put(it.rpm).put(it.map).put(it.petrolMs)) }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("curveFingerprint", s.curveFingerprint ?: JSONObject.NULL)
            .put("gasEpochReason", s.gasEpochReason)
            .put("gasEpochAt", s.gasEpochAt)
            .put("gasUsefulRpmMs", s.gasUsefulRpmMs)
            .put("airRpmBar", s.airRpmBar)
            .put("petrol", lane(s.petrol))
            .put("gas", lane(s.gas))
            .put("ecuPetrolRef", JSONArray(s.ecuPetrolRef.map { JSONArray().put(it.first).put(it.second) }))
            .put("lastPetrolActivity", s.lastPetrolActivity ?: JSONObject.NULL)
            .put("petrolGenerationSession", s.petrolGenerationSession)
            .put("petrolGeneration", s.petrolGeneration)
            .put("petrolEpoch", s.petrolEpoch)
            .put("localLatch", JSONArray(s.localLatch.map { JSONArray().put(it.column).put(it.band) }))
    }

    /**
     * Arquivo (ou a reserva `.bak`) → memória. Formato desconhecido ou JSON inválido não zera o que está na memória nem
     * apaga arquivo: fica o motivo em [loadError] (o índice leva para a tela). Antes o `catch` zerava tudo em silêncio e
     * o salvamento seguinte apagava o que sobrava.
     */
    private fun load() {
        val target = file ?: return
        val root = AtomicJsonFile.readJson(target) ?: run {
            if (target.isFile || AtomicJsonFile.backupOf(target).isFile) loadError = "Arquivo de equivalência ilegível (nem a reserva .bak serviu)"
            return
        }
        if (root.optString("format") != FORMAT) {
            loadError = "Formato desconhecido do arquivo de equivalência: ${root.optString("format")}"
            return
        }
        try {
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
            root.optJSONArray("ecuPetrolRef")?.let { a ->
                ecuPetrolRef = (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optDouble(0) to it.optDouble(1) } }
                    .filter { it.first.isFinite() && it.second.isFinite() }
            }
            lastPetrolActivity = if (root.isNull("lastPetrolActivity") || !root.has("lastPetrolActivity")) null else root.optInt("lastPetrolActivity")
            petrolGenerationSession = root.optLong("petrolGenerationSession", 0L)
            petrolGeneration = root.optInt("petrolGeneration", -1)
            petrolEpoch = root.optInt("petrolEpoch", 0)
            localLatch = root.optJSONArray("localLatch")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.takeIf { it.length() == 2 }?.let { EquivalenceAttribution.Key(it.optInt(0), it.optInt(1)) } }.toSet()
            } ?: emptySet()
        } catch (error: Exception) {
            // Fica o que já foi lido; só o motivo é registrado.
            loadError = "Arquivo de equivalência lido em parte: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}
