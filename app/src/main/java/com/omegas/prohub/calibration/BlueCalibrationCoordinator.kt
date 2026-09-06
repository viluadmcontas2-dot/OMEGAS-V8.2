package com.omegas.prohub.calibration

import com.omegas.prohub.blue.BlueAutoCalAdapter
import com.omegas.prohub.blue.BlueCausalEngine
import com.omegas.prohub.blue.BlueLearningState
import com.omegas.prohub.blue.BlueWitnessConfidence
import com.omegas.prohub.blue.CalibrationRevision
import com.omegas.prohub.blue.CalibrationState
import com.omegas.prohub.blue.FuelComparison
import com.omegas.prohub.blue.FuelEvidence
import com.omegas.prohub.blue.FuelKind
import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Integration boundary between proven ECU readers/writers and the Blue engine.
 * This class never writes a suggestion automatically. Manual writers remain the
 * only path to the ECU and every confirmed write is followed by fresh readback.
 * OBD witness data is projected only after Blue has finished its causal math.
 */
class BlueCalibrationCoordinator(
    private val mapManager: KWriteManager,
    private val factorManager: KFactorManager,
) {
    private val lock = Any()
    private val engine = BlueCausalEngine()
    private val autoCal = BlueAutoCalAdapter(engine)
    private var state: BlueLearningState? = null
    private var latestObdWitness: JSONObject? = null

    fun synchronizeFromEcu(): JSONObject = synchronized(lock) {
        val mapResult = mapManager.readFullMap()
        require(mapResult.optBoolean("ok")) { mapResult.optString("error", "Falha ao ler Mapa K") }
        val curveResult = factorManager.readCurve()
        require(curveResult.optBoolean("ok")) { curveResult.optString("error", "Falha ao ler Curva K") }

        val map = decodeMap(mapResult.optJSONArray("allRows"))
        val curve = decodeCurve(curveResult.optJSONArray("factorsRaw"))
        val previous = state
        val revision = previous?.let {
            CalibrationRevision(
                curveK = it.calibration.revision.curveK + if (it.calibration.curveK == curve) 0 else 1,
                mapK = it.calibration.revision.mapK + if (it.calibration.mapK == map) 0 else 1,
            )
        } ?: CalibrationRevision(0, 0)
        val calibration = CalibrationState(revision, curve, map)
        val next = previous?.copy(calibration = calibration) ?: BlueLearningState(
            sessionId = UUID.randomUUID().toString(),
            calibration = calibration,
        )
        state = next.copy(comparisons = engine.reconcile(next))
        stateJsonLocked()
            .put("ok", true)
            .put("source", "ECU_ACK_READBACK")
    }

    fun ingestLearningSnapshot(snapshot: JSONObject): JSONObject = synchronized(lock) {
        val current = requireState()
        val regions = snapshot.optJSONArray("regions") ?: JSONArray()
        val epoch = snapshot.optInt("epoch", 1)
        val petrol = current.petrolEvidence.associateBy { it.id }.toMutableMap()
        val cng = current.cngEvidenceByRevision.mapValues { it.value.associateBy(FuelEvidence::id).toMutableMap() }.toMutableMap()
        var petrolImported = 0
        var cngImported = 0

        repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            val fuel = when (region.optString("fuel").uppercase()) {
                "PETROL", "GASOLINA" -> FuelKind.PETROL
                "CNG", "GNV", "GAS" -> FuelKind.CNG
                else -> return@repeat
            }
            if (fuel == FuelKind.CNG && region.optInt("epoch", epoch) != epoch) return@repeat
            val visits = region.optJSONArray("visits")
            val visitIds = buildList {
                if (visits != null) repeat(visits.length()) {
                    visits.optString(it).takeIf(String::isNotBlank)?.let(::add)
                }
                if (isEmpty()) add(region.optString("id", "region-$index"))
            }.distinct()
            visitIds.forEach { visitId ->
                val id = "${region.optString("id", "region-$index")}:$visitId"
                val evidence = FuelEvidence(
                    id = id,
                    fuel = fuel,
                    collectedAtMs = region.optLong("updated_at", System.currentTimeMillis()).coerceAtLeast(0L),
                    visitId = visitId,
                    rpm = region.optDouble("rpm", 0.0).coerceAtLeast(0.0),
                    mapBar = region.optDouble("map_bar", 0.0).coerceAtLeast(0.0),
                    petrolMs = region.optDouble("petrol_ms", 0.0).coerceAtLeast(0.0),
                    quality = region.optDouble("quality", region.optDouble("confidence", 0.0)).coerceIn(0.0, 1.0),
                    cngRevision = if (fuel == FuelKind.CNG) current.calibration.revision else null,
                    waterC = finiteOrUnknown(region.optDouble("water_c", FuelEvidence.UNKNOWN_TEMPERATURE_C)),
                    gasC = finiteOrUnknown(region.optDouble("gas_c", FuelEvidence.UNKNOWN_TEMPERATURE_C)),
                    pressureDiffBar = finiteOrZero(region.optDouble("pressure_diff_bar", 0.0)),
                )
                if (fuel == FuelKind.PETROL) {
                    if (petrol.putIfAbsent(id, evidence) == null) petrolImported += 1
                } else {
                    val bucket = cng.getOrPut(current.calibration.revision) { mutableMapOf() }
                    if (bucket.putIfAbsent(id, evidence) == null) cngImported += 1
                }
            }
        }

        val updated = current.copy(
            petrolEvidence = petrol.values.sortedBy { it.collectedAtMs },
            cngEvidenceByRevision = cng.mapValues { it.value.values.sortedBy(FuelEvidence::collectedAtMs) },
        )
        state = updated.copy(comparisons = engine.reconcile(updated))
        stateJsonLocked()
            .put("ok", true)
            .put("petrolImported", petrolImported)
            .put("cngImported", cngImported)
    }

    fun updateObdWitness(witness: JSONObject): JSONObject = synchronized(lock) {
        latestObdWitness = JSONObject(witness.toString())
        JSONObject()
            .put("ok", true)
            .put("observationalOnly", true)
            .put("calibrationState", witness.optString("calibrationState").ifBlank { JSONObject.NULL })
    }

    fun reconcileConfirmedManualWrite(): JSONObject = synchronized(lock) {
        val previous = requireState().calibration.revision
        val synced = synchronizeFromEcu()
        synced.put("previousRevision", revisionJson(previous))
            .put("currentRevision", revisionJson(requireState().calibration.revision))
            .put("source", "CONFIRMED_MANUAL_WRITE_READBACK")
    }

    fun stateJson(): JSONObject = synchronized(lock) { stateJsonLocked() }

    fun proposalJson(): JSONObject = synchronized(lock) {
        val active = state?.activeComparisons().orEmpty()
        val comparison = active.maxByOrNull { it.createdAtMs }
            ?: return@synchronized JSONObject()
                .put("ok", true)
                .put("available", false)
                .put("state", "WAITING_FOR_EQUIVALENT_FUEL_EVIDENCE")
                .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
                .put("automatic", false)
                .put("manualOnly", true)
        projectWitness(
            baseJson = autoCal.proposalJson(comparison, gain = null),
            comparison = comparison,
        ).put("available", true)
    }

    private fun stateJsonLocked(): JSONObject {
        val current = state ?: return JSONObject()
            .put("ready", false)
            .put("reason", "CALIBRATION_NOT_SYNCED")
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
        val latest = current.activeComparisons().maxByOrNull { it.createdAtMs }
        val latestJson = latest?.let(::comparisonJson)
        return JSONObject()
            .put("ready", true)
            .put("sessionId", current.sessionId)
            .put("revision", revisionJson(current.calibration.revision))
            .put("curvePoints", current.calibration.curveK.size)
            .put("mapStorageRows", current.calibration.mapK.size)
            .put("petrolEvidence", current.petrolEvidence.size)
            .put("activeCngEvidence", current.activeCngEvidence().size)
            .put("activeComparisons", current.activeComparisons().size)
            .put("latestComparison", latestJson ?: JSONObject.NULL)
            .put("baseConfidence", latestJson?.optDouble("baseConfidence") ?: JSONObject.NULL)
            .put("effectiveConfidence", latestJson?.optDouble("effectiveConfidence") ?: JSONObject.NULL)
            .put("obdWitness", latestJson?.optJSONObject("obdWitness") ?: JSONObject.NULL)
            .put("proposal", proposalJsonLocked(latest))
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
            .put("automaticWrite", false)
    }

    private fun proposalJsonLocked(comparison: FuelComparison?): Any = comparison?.let {
        projectWitness(autoCal.proposalJson(it, gain = null), it)
    } ?: JSONObject.NULL

    private fun comparisonJson(value: FuelComparison): JSONObject = projectWitness(
        baseJson = JSONObject()
            .put("id", value.id)
            .put("rpm", value.rpm)
            .put("mapBar", value.mapBar)
            .put("petrolReferenceMs", value.petrolTargetMs)
            .put("petrolOnCngMs", value.petrolOnCngMs)
            .put("errorPercent", value.errorPercent)
            .put("quality", value.quality)
            .put("createdAt", value.createdAtMs),
        comparison = value,
    )

    private fun projectWitness(baseJson: JSONObject, comparison: FuelComparison): JSONObject =
        BlueWitnessConfidence.project(
            baseJson = baseJson,
            blueErrorPercent = comparison.errorPercent,
            baseQuality = comparison.quality,
            witness = latestObdWitness,
            expectedCalibrationState = calibrationStateId(comparison.revision),
        )

    private fun calibrationStateId(value: CalibrationRevision): String =
        "map-${value.mapK}:curve-${value.curveK}"

    private fun decodeCurve(raw: JSONArray?): List<Double> {
        require(raw != null && raw.length() == CalibrationShape.CURVE_K_POINTS) {
            "Readback da Curva K não possui 30 pontos"
        }
        return List(raw.length()) { KFactorProtocol.factorFromRaw(raw.getInt(it)) }
    }

    private fun decodeMap(raw: JSONArray?): List<List<Int>> {
        require(raw != null && raw.length() == CalibrationShape.MAP_K_STORAGE_ROWS) {
            "Readback do Mapa K não possui 13 linhas"
        }
        return List(raw.length()) { row ->
            val values = raw.getJSONArray(row)
            require(values.length() == CalibrationShape.MAP_K_COLUMNS) { "Linha K inválida: $row" }
            List(values.length()) { column -> values.getInt(column) }
        }
    }

    private fun requireState(): BlueLearningState = state
        ?: error("Leia Curva K e Mapa K da ECU antes de comparar combustíveis")

    private fun revisionJson(value: CalibrationRevision): JSONObject = JSONObject()
        .put("curveK", value.curveK)
        .put("mapK", value.mapK)

    private fun finiteOrUnknown(value: Double): Double =
        if (value.isFinite()) value else FuelEvidence.UNKNOWN_TEMPERATURE_C
    private fun finiteOrZero(value: Double): Double = if (value.isFinite()) value else 0.0
}
