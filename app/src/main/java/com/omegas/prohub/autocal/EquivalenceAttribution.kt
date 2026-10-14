package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Onde ajustar o quê (dono, 2026-10-11: "A CURVA DEVE APRENDER TUDO! E SABER ONDE AJUSTAR O QUÊ!").
 *
 * Todo par GNV × gasolina própria (lenta incluída) é evidência da Curva K (global) por padrão. Cada par cai num
 * grupo = coluna de RPM do Mapa K (a mais próxima de [KMapPhysicalAxes.rpmBins]) × faixa de t_gasolina
 * ([EquivalenceLedger.BANDS]). Um grupo só vira LOCAL (ajuste no Mapa K, fora da curva) se for SEPARÁVEL e DIFERENTE:
 *  (a) ≥ [MIN_PAIRS] pares e ≥ [MIN_VISITS] visitas no grupo, com as medianas por visita (contra as outras colunas)
 *      do mesmo sinal e espalhamento (desvio mediano entre visitas) ≤ 3%;
 *  (b) ≥ [MIN_OTHER_PAIRS] pares de OUTRAS colunas com t_gasolina a ±[SAME_T_MS] da mediana do grupo;
 *  (c) |mediana de ln(t_GNV/t_gasolina) do grupo − mediana das outras colunas no mesmo t| > ln(1,05) para entrar;
 *      um grupo que já era local só sai em ≤ ln(1,04) (histerese, margem do dono).
 * Sem separabilidade (ex.: só há lenta naquele t) fica tudo na curva. Os grupos são julgados do mais diferente para
 * o menos diferente; com só duas colunas no mesmo t a diferença é simétrica e a de menor RPM (a lenta) é julgada
 * primeiro. Grupo já julgado que difere das outras colunas (local, ou diferente mas sem prova: poucos pares, poucas
 * visitas, visitas instáveis) não serve de régua para os seguintes: a lenta instável nunca faz o cruzeiro parecer
 * "local" (os pares dele seguem na curva).
 *
 * Puro: não fala com a ECU e não grava nada. Paridade: tools/autocal_refine/blind_telemetry_test.py (attribution).
 */
object EquivalenceAttribution {
    val ENTER_LOG = ln(1.05)
    val EXIT_LOG = ln(1.04)
    const val MIN_PAIRS = 8
    const val MIN_VISITS = 3
    val VISIT_SPREAD_LOG = ln(1.03)
    const val MIN_OTHER_PAIRS = 8
    const val SAME_T_MS = 0.5

    const val LOCAL = "LOCAL"
    const val FEW_PAIRS = "POUCOS_PARES"
    const val FEW_VISITS = "POUCAS_VISITAS"
    const val NOT_SEPARABLE = "SEM_OUTRA_COLUNA_NO_MESMO_T"
    const val UNSTABLE = "VISITAS_INSTAVEIS"
    const val SAME_AS_CURVE = "IGUAL_AS_OUTRAS_COLUNAS"

    private val RPM = KMapPhysicalAxes.rpmBins()
    private val BANDS = EquivalenceLedger.BANDS

    data class Key(val column: Int, val band: Int)

    data class Group(
        val key: Key,
        val pairs: Int,
        val visits: Int,
        /** Medianas de t_gasolina e t_GNV do grupo (ms). */
        val petrolMs: Double,
        val gasMs: Double,
        /** Mediana de ln(t_GNV/t_gasolina) no grupo. */
        val errorLog: Double,
        /** Pares e mediana das outras colunas no mesmo t (null = nenhum). */
        val others: Int,
        val othersLog: Double?,
        /** Grupo − outras colunas (o que a curva não explica); null sem outras colunas. */
        val residualLog: Double?,
        val local: Boolean,
        val reason: String,
    ) {
        val rpm: Int get() = RPM[key.column]
        val fromMs: Double get() = BANDS[key.band].first
        val toMs: Double get() = BANDS[key.band].second

        fun json(): JSONObject = JSONObject()
            .put("column", key.column).put("rpm", rpm).put("band", key.band).put("fromMs", fromMs).put("toMs", toMs)
            .put("pairs", pairs).put("visits", visits).put("petrolMs", petrolMs).put("gasMs", gasMs)
            .put("errorPercent", (exp(errorLog) - 1.0) * 100.0)
            .put("others", others)
            .put("othersPercent", othersLog?.let { (exp(it) - 1.0) * 100.0 } ?: JSONObject.NULL)
            .put("residualPercent", residualLog?.let { (exp(it) - 1.0) * 100.0 } ?: JSONObject.NULL)
            .put("residualLog", residualLog ?: JSONObject.NULL)
            .put("local", local).put("reason", reason)
    }

    /** Coluna do Mapa K mais próxima do RPM (empate fica com a de menor RPM). */
    fun column(rpm: Double): Int {
        var best = 0
        for (i in 1 until RPM.size) if (abs(rpm - RPM[i]) < abs(rpm - RPM[best])) best = i
        return best
    }

    /** Faixa de t_gasolina ([EquivalenceLedger.BANDS]); -1 fora delas (abaixo de 3 ms ou acima de 12 ms). */
    fun band(petrolMs: Double): Int = BANDS.indexOfFirst { petrolMs >= it.first && petrolMs < it.second }

    fun keyOf(rpm: Double, petrolMs: Double): Key? = band(petrolMs).takeIf { it >= 0 }?.let { Key(column(rpm), it) }

    /** Mediana superior (a mesma regra do índice do acumulador). */
    private fun median(values: List<Double>): Double? = if (values.isEmpty()) null else values.sorted()[values.size / 2]

    private class Member(val pair: EquivalenceLedger.EvidencePair, val column: Int, val key: Key?, val error: Double) {
        /** Passagem pela região: números seguidos (na ordem das leituras) no mesmo grupo; outro grupo no meio abre visita nova. */
        var visit = 0
    }

    private class Stat(val key: Key, val members: List<Member>) {
        val petrolMs = median(members.map { it.pair.petrolRefMs })!!
        val gasMs = median(members.map { it.pair.gasPetrolMs })!!
        val error = median(members.map { it.error })!!
        /** Mediana de ln(t_GNV/t_gasolina) por visita (visita = leituras seguidas no mesmo grupo, sem relógio). */
        val visitErrors: List<Double> = members.groupBy { it.visit }.toSortedMap().values.map { median(it.map { m -> m.error })!! }
    }

    /**
     * Classifica os grupos. [pairs] = pares com gasolina PRÓPRIA (a curva de gasolina da ECU não tem RPM e não
     * serve para separar); [latched] = grupos que já eram locais (histerese). Devolve todos os grupos com o motivo.
     */
    fun classify(pairs: List<EquivalenceLedger.EvidencePair>, latched: Set<Key>): List<Group> {
        val members = pairs.filter { it.petrolRefMs > 0.0 && it.gasPetrolMs > 0.0 }.map {
            Member(it, column(it.rpm), keyOf(it.rpm, it.petrolRefMs), ln(it.gasPetrolMs / it.petrolRefMs))
        }
        var visit = 0
        var previous: Key? = null
        members.sortedBy { it.pair.t }.forEach { m ->
            if (m.key != previous) visit++
            m.visit = visit
            previous = m.key
        }
        val stats = members.filter { it.key != null }.groupBy { it.key!! }.map { (key, list) -> Stat(key, list) }
        fun othersOf(stat: Stat, excluded: Set<Key>): List<Double> = members.filter {
            it.column != stat.key.column && abs(it.pair.petrolRefMs - stat.petrolMs) <= SAME_T_MS && (it.key == null || it.key !in excluded)
        }.map { it.error }
        val initial = stats.associateWith { s -> median(othersOf(s, emptySet()))?.let { abs(s.error - it) } ?: -1.0 }
        // Quem já era local primeiro (histerese); depois a maior diferença; empate fica com a menor coluna (a lenta).
        val ordered = stats.sortedWith(compareBy<Stat>({ if (it.key in latched) 0 else 1 }, { -initial.getValue(it) }, { it.key.column }, { it.key.band }))
        // Fora da régua: grupos já julgados que diferem das outras colunas (locais ou diferentes sem prova).
        val excluded = HashSet<Key>()
        val out = ArrayList<Group>()
        for (s in ordered) {
            val others = othersOf(s, excluded)
            val othersLog = median(others)
            val residual = othersLog?.let { s.error - it }
            val visitResiduals = othersLog?.let { m -> s.visitErrors.map { it - m } }.orEmpty()
            val centre = median(visitResiduals)
            val stable = centre != null && (visitResiduals.all { it > 0.0 } || visitResiduals.all { it < 0.0 }) &&
                median(visitResiduals.map { abs(it - centre) })!! <= VISIT_SPREAD_LOG
            val threshold = if (s.key in latched) EXIT_LOG else ENTER_LOG
            val reason = when {
                s.members.size < MIN_PAIRS -> FEW_PAIRS
                s.visitErrors.size < MIN_VISITS -> FEW_VISITS
                others.size < MIN_OTHER_PAIRS || residual == null -> NOT_SEPARABLE
                !stable -> UNSTABLE
                abs(residual) <= threshold -> SAME_AS_CURVE
                else -> LOCAL
            }
            if (reason == LOCAL || (residual != null && abs(residual) > threshold)) excluded += s.key
            out += Group(s.key, s.members.size, s.visitErrors.size, s.petrolMs, s.gasMs, s.error, others.size, othersLog, residual, reason == LOCAL, reason)
        }
        return out.sortedWith(compareBy({ it.key.column }, { it.key.band }))
    }

    fun json(groups: List<Group>): JSONArray = JSONArray(groups.map { it.json() })
}
