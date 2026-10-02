package com.omegas.prohub.autocal

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
        const val MAX_PETROL_OBS = 4_000
        const val MAX_GAS_OBS = 1_500
        /** Abaixo disso a ECU tem estratégia de lenta própria: fora do índice de condução. */
        const val DRIVING_MIN_RPM = 1_000.0
        val BANDS = listOf(3.0 to 4.5, 4.5 to 6.0, 6.0 to 7.5, 7.5 to 9.0, 9.0 to 12.0)
        private const val SAVE_INTERVAL_MS = 60_000L

        /** Impressão digital da Curva K (mesma forma usada pela bridge sobre o snapshot da ECU). */
        fun fingerprint(mulActRaw: IntArray): String = JSONArray(mulActRaw.toList()).toString().hashCode().toString(16)
        /** Tempo morto do injetor de gás medido no histórico (gás ≈ gasolina×K×Mapa/100 + 0,99 ms). */
        const val GAS_DEAD_TIME_MS = 0.99
    }

    data class Frame(val t: Long, val fuel: String, val rpm: Double, val map: Double, val petrolMs: Double, val gasMs: Double = 0.0)
    data class Obs(val t: Long, val rpm: Double, val map: Double, val petrolMs: Double)
    data class EvidencePair(val petrolRefMs: Double, val gasPetrolMs: Double, val rpm: Double)

    private val lock = Any()
    private val window = ArrayDeque<Frame>(3)
    private val petrol = ArrayDeque<Obs>()
    private val gas = ArrayDeque<Obs>()
    private var gasEpochReason = "INICIO"
    private var gasEpochAt = 0L
    private var curveFingerprint: String? = null
    private var lastSaveAt = 0L
    private var gasUsefulRpmMs = 0.0
    private var airRpmBar = 0.0
    private var dirty = false
    @Volatile private var cachedIndex: JSONObject? = null

    init { load() }

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
                val lane = if (frame.fuel == "GASOLINA") petrol else gas
                lane.addLast(obs)
                val cap = if (frame.fuel == "GASOLINA") MAX_PETROL_OBS else MAX_GAS_OBS
                while (lane.size > cap) lane.removeFirst()
                dirty = true
                cachedIndex = null
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
        cachedIndex = null
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
        val ref = petrol.toList()
        gas.mapNotNull { g ->
            val matches = ref.filter { abs(it.rpm - g.rpm) <= MATCH_RPM && abs(it.map - g.map) <= MATCH_MAP }
                .map { it.petrolMs }.sorted()
            if (matches.size < 2) null else EvidencePair(matches[matches.size / 2], g.petrolMs, g.rpm)
        }
    }

    /**
     * Índice de equivalência da condução (rpm ≥ 1000, ≥ 3 ms): razão mediana t_no_GNV / t_gasolina.
     * 1,00 = GNV pede exatamente o que a gasolina pede. >1 = GNV pobre (ECU compensa somando).
     */
    fun index(): JSONObject {
        cachedIndex?.let { return JSONObject(it.toString()).put("gasPerAir", gasPerAir() ?: JSONObject.NULL) }
        val all = pairs().filter { it.rpm >= DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }
        fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val bands = JSONArray()
        BANDS.forEach { (lo, hi) ->
            val sel = all.filter { it.petrolRefMs >= lo && it.petrolRefMs < hi }
            val ratio = median(sel.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", sel.size)
                .put("ratio", ratio ?: JSONObject.NULL))
        }
        val global = median(all.map { ln(it.gasPetrolMs / it.petrolRefMs) })?.let(::exp)
        val petrolCount: Int
        val gasCount: Int
        val reason: String
        val at: Long
        synchronized(lock) { petrolCount = petrol.size; gasCount = gas.size; reason = gasEpochReason; at = gasEpochAt }
        val result = JSONObject()
            .put("ok", true)
            .put("format", FORMAT)
            .put("ratio", global ?: JSONObject.NULL)
            .put("samples", all.size)
            .put("petrolObservations", petrolCount)
            .put("gasObservations", gasCount)
            .put("gasEpochReason", reason)
            .put("gasEpochAt", at)
            .put("drivingMinRpm", DRIVING_MIN_RPM)
            // Gás útil (tempo de gás − tempo morto) por unidade de ar admitido (MAP×RPM), condução em GNV:
            // independe do trânsito; compara calibrações no mesmo carro. Unidade relativa.
            .put("gasPerAir", gasPerAir() ?: JSONObject.NULL)
            .put("bands", bands)
            .put("automatic", false)
        cachedIndex = result
        return JSONObject(result.toString())
    }

    fun gasPerAir(): Double? = synchronized(lock) { if (airRpmBar > 0) gasUsefulRpmMs / airRpmBar else null }

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
            fun lane(array: JSONArray?, into: ArrayDeque<Obs>) {
                if (array == null) return
                for (i in 0 until array.length()) {
                    val row = array.optJSONArray(i) ?: continue
                    into.addLast(Obs(row.optLong(0), row.optDouble(1), row.optDouble(2), row.optDouble(3)))
                }
            }
            lane(root.optJSONArray("petrol"), petrol)
            lane(root.optJSONArray("gas"), gas)
            curveFingerprint = root.optString("curveFingerprint").takeIf { it.isNotBlank() && it != "null" }
            gasEpochReason = root.optString("gasEpochReason", "CARREGADO")
            gasEpochAt = root.optLong("gasEpochAt", 0L)
            gasUsefulRpmMs = root.optDouble("gasUsefulRpmMs", 0.0)
            airRpmBar = root.optDouble("airRpmBar", 0.0)
        } catch (_: Exception) {
            petrol.clear()
            gas.clear()
        }
    }
}
