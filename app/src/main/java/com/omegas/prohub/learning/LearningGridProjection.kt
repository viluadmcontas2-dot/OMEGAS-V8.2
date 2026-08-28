package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

/**
 * Projeção única da memória física para a grade Landi usada pela tela e pelo .omegas.
 * A região continua sendo a evidência científica; a célula só existe quando a
 * geometria física que a define está KNOWN na sessão atual.
 */
object LearningGridProjection {
    /** Fixture histórica mantida apenas para testes/compatibilidade fora de sessão física gerenciada. */
    val rpmBins = KMapPhysicalAxes.rpmBins()
    val petrolBins = KMapPhysicalAxes.petrolBins()
    val mapBins = doubleArrayOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00)

    private data class AxisContext(
        val rpm: IntArray,
        val petrolMs: DoubleArray,
        val source: String,
        val geometryFingerprint: String,
    )

    private fun axisContext(): AxisContext? {
        val binding = LearningCalibrationAuthority.snapshot()
        if (binding != null && binding.geometryKnown()) {
            return AxisContext(
                rpm = binding.rpmAxis.toIntArray(),
                petrolMs = binding.petrolAxisMs.toDoubleArray(),
                source = "ECU_CURRENT",
                geometryFingerprint = binding.geometryFingerprint,
            )
        }
        if (LearningCalibrationAuthority.requiresKnownGeometry()) return null
        return AxisContext(
            rpm = rpmBins.copyOf(),
            petrolMs = petrolBins.copyOf(),
            source = "LEGACY_FIXTURE",
            geometryFingerprint = KMapPhysicalAxes.LOCK_SHA256,
        )
    }

    fun gridJson(): JSONObject {
        val axis = axisContext()
        if (axis == null) {
            return JSONObject()
                .put("rows", KMapPhysicalAxes.WRITABLE_ROWS)
                .put("columns", KMapPhysicalAxes.COLUMNS)
                .put("physicalCells", KMapPhysicalAxes.WRITABLE_ROWS * KMapPhysicalAxes.COLUMNS)
                .put("geometryKnown", false)
                .put("axisSource", "UNKNOWN_CURRENT_SESSION")
                .put("reasonCode", "MAP_GEOMETRY_UNKNOWN")
                .put("axisSchema", JSONObject.NULL)
                .put("lockSha256", JSONObject.NULL)
                .put("immutablePhysicalContract", false)
                .put("protocolRows", KMapPhysicalAxes.PROTOCOL_ROWS)
                .put("protocolColumns", KMapPhysicalAxes.COLUMNS)
                .put("specialRow", KMapPhysicalAxes.SPECIAL_ROW)
                .put("rpmBins", JSONArray())
                .put("petrolBins", JSONArray())
                .put("mapBins", JSONArray(mapBins.toList()))
                .put("learningModel", "CONTINUOUS_MULTIVARIATE_CONTROL_POINTS")
                .put("validEvidencePolicy", "RAW_ALLOWED_CELL_REQUIRES_KNOWN_GEOMETRY")
        }
        return JSONObject()
            .put("rows", axis.petrolMs.size)
            .put("columns", axis.rpm.size)
            .put("physicalCells", axis.petrolMs.size * axis.rpm.size)
            .put("geometryKnown", true)
            .put("axisSource", axis.source)
            .put("geometryFingerprint", axis.geometryFingerprint)
            .put("axisSchema", if (axis.source == "ECU_CURRENT") "mp48-map-geometry-v1" else KMapPhysicalAxes.SCHEMA)
            .put("lockSha256", if (axis.source == "LEGACY_FIXTURE") KMapPhysicalAxes.LOCK_SHA256 else JSONObject.NULL)
            .put("immutablePhysicalContract", axis.source == "LEGACY_FIXTURE")
            .put("protocolRows", KMapPhysicalAxes.PROTOCOL_ROWS)
            .put("protocolColumns", KMapPhysicalAxes.COLUMNS)
            .put("specialRow", KMapPhysicalAxes.SPECIAL_ROW)
            .put("rpmBins", JSONArray(axis.rpm.toList()))
            .put("petrolBins", JSONArray(axis.petrolMs.toList()))
            .put("mapBins", JSONArray(mapBins.toList()))
            .put("learningModel", "CONTINUOUS_MULTIVARIATE_CONTROL_POINTS")
            .put("validEvidencePolicy", "PONDERED_NOT_DISCARDED")
    }

    fun cellFor(rpm: Double, petrolMs: Double, mapBar: Double = 0.60): JSONObject {
        val axis = axisContext() ?: return unknownCell()
        val rpmAxis = axis.rpm.map(Int::toDouble).toDoubleArray()
        val row = nearest(axis.petrolMs, petrolMs)
        val column = nearest(rpmAxis, rpm)
        val mapIndex = nearest(mapBins, mapBar)
        return JSONObject()
            .put("geometryKnown", true)
            .put("axisSource", axis.source)
            .put("geometryFingerprint", axis.geometryFingerprint)
            .put("row", row)
            .put("column", column)
            .put("key", "$row:$column")
            .put("rpmBin", axis.rpm[column])
            .put("petrolBin", axis.petrolMs[row])
            .put("mapBin", mapBins[mapIndex])
            .put("continuousWeights", JSONArray(
                ContinuousLearningMath.bilinearWeights(
                    rpm = rpm,
                    petrolMs = petrolMs,
                    rpmAxis = rpmAxis,
                    petrolAxisMs = axis.petrolMs,
                ).map {
                    JSONObject()
                        .put("row", it.row)
                        .put("column", it.column)
                        .put("rpmBin", axis.rpm[it.column])
                        .put("petrolBin", axis.petrolMs[it.row])
                        .put("weight", it.weight)
                },
            ))
            .put("trilinearWeights", JSONArray(
                ContinuousLearningMath.trilinearWeights(
                    rpm = rpm,
                    petrolMs = petrolMs,
                    mapBar = mapBar,
                    rpmAxis = rpmAxis,
                    petrolAxisMs = axis.petrolMs,
                    mapBins = mapBins,
                ).map {
                    JSONObject()
                        .put("row", it.row)
                        .put("column", it.column)
                        .put("mapIndex", it.mapIndex)
                        .put("rpmBin", axis.rpm[it.column])
                        .put("petrolBin", axis.petrolMs[it.row])
                        .put("weight", it.weight)
                },
            ))
    }

    private fun unknownCell(): JSONObject = JSONObject()
        .put("geometryKnown", false)
        .put("axisSource", "UNKNOWN_CURRENT_SESSION")
        .put("reasonCode", "MAP_GEOMETRY_UNKNOWN")
        .put("row", -1)
        .put("column", -1)
        .put("key", "UNKNOWN")
        .put("rpmBin", JSONObject.NULL)
        .put("petrolBin", JSONObject.NULL)
        .put("mapBin", JSONObject.NULL)
        .put("continuousWeights", JSONArray())
        .put("trilinearWeights", JSONArray())

    /**
     * Pacote leve para explicar, em tempo real, a mesma interpolação bilinear
     * usada pelo aprendizado. É estritamente observacional: não altera memória,
     * sugestões nem escrita K.
     */
    fun liveInterpolationJson(
        rpm: Double,
        petrolMs: Double,
        mapBar: Double,
        sequence: Long,
        updatedAt: Long,
        telemetryValid: Boolean,
    ): JSONObject {
        val physicallyValid = telemetryValid && rpm > 0.0 && petrolMs > 0.0 &&
            rpm.isFinite() && petrolMs.isFinite() && mapBar.isFinite()
        val safeRpm = if (rpm.isFinite()) rpm.coerceAtLeast(0.0) else 0.0
        val safePetrolMs = if (petrolMs.isFinite()) petrolMs.coerceAtLeast(0.0) else 0.0
        val safeMapBar = if (mapBar.isFinite()) mapBar.coerceAtLeast(0.0) else 0.0
        val cell = cellFor(safeRpm, safePetrolMs, safeMapBar)
        val geometryKnown = cell.optBoolean("geometryKnown", false)
        val weights = cell.optJSONArray("continuousWeights") ?: JSONArray()
        val totalWeight = (0 until weights.length()).sumOf { index ->
            weights.optJSONObject(index)?.optDouble("weight", 0.0) ?: 0.0
        }
        return JSONObject()
            .put("valid", physicallyValid && geometryKnown)
            .put("educationalOnly", true)
            .put("affectsLearning", false)
            .put("affectsCalibration", false)
            .put("method", "BILINEAR_RPM_X_PETROL_MS")
            .put("geometryKnown", geometryKnown)
            .put("axisSource", cell.optString("axisSource", "UNKNOWN_CURRENT_SESSION"))
            .put("geometryFingerprint", cell.optString("geometryFingerprint", ""))
            .put("reasonCode", cell.optString("reasonCode", ""))
            .put("sequence", sequence)
            .put("updatedAt", updatedAt)
            .put("rpm", rpm)
            .put("petrolMs", petrolMs)
            .put("mapBar", mapBar)
            .put("totalWeight", totalWeight)
            .put("cell", cell)
    }

    fun sameCell(rpmA: Double, petrolA: Double, rpmB: Double, petrolB: Double): Boolean {
        val a = cellFor(rpmA, petrolA)
        val b = cellFor(rpmB, petrolB)
        if (!a.optBoolean("geometryKnown", false) || !b.optBoolean("geometryKnown", false)) return false
        return a.optInt("row", -1) == b.optInt("row", -1) && a.optInt("column", -1) == b.optInt("column", -1)
    }

    fun enrichRegion(region: JSONObject): JSONObject {
        val copy = JSONObject(region.toString())
        val cell = cellFor(
            rpm = copy.optDouble("rpm", copy.optDouble("rpm_mean", 0.0)),
            petrolMs = copy.optDouble("petrol_ms", copy.optDouble("petrol_mean", 0.0)),
            mapBar = copy.optDouble("map_bar", copy.optDouble("map_mean", 0.60)),
        )
        return copy
            .put("cell", cell)
            .put("cell_known", cell.optBoolean("geometryKnown", false))
            .put("cell_row", cell.optInt("row", -1))
            .put("cell_column", cell.optInt("column", -1))
            .put("cell_key", cell.optString("key", "UNKNOWN"))
    }

    fun project(regions: JSONArray, currentEpoch: Int): JSONArray {
        val grouped = linkedMapOf<String, MutableCell>()
        repeat(regions.length()) { index ->
            val raw = regions.optJSONObject(index) ?: return@repeat
            val region = enrichRegion(raw)
            val fuel = normalizeFuel(region.optString("fuel"))
            if (fuel == "UNKNOWN") return@repeat
            val regionEpoch = region.optInt("epoch", if (fuel == "PETROL") 0 else currentEpoch)
            val cell = region.optJSONObject("cell") ?: return@repeat
            if (!cell.optBoolean("geometryKnown", false)) return@repeat
            val axisSource = cell.optString("axisSource", "UNKNOWN")
            val geometryFingerprint = cell.optString("geometryFingerprint", "")
            val continuousWeights = cell.optJSONArray("continuousWeights")
            if (continuousWeights != null) {
                repeat(continuousWeights.length()) { wIndex ->
                    val wObj = continuousWeights.optJSONObject(wIndex) ?: return@repeat
                    val row = wObj.optInt("row", -1)
                    val column = wObj.optInt("column", -1)
                    val cellWeight = wObj.optDouble("weight", 0.0)
                    if (row < 0 || column < 0 || cellWeight <= 0.0) return@repeat

                    val key = "$fuel:$regionEpoch:$row:$column"
                    val weight = max(0.001, region.optDouble("weight", region.optDouble("samples", 1.0)) * cellWeight)
                    val target = grouped.getOrPut(key) {
                        MutableCell(
                            fuel = fuel,
                            epoch = regionEpoch,
                            row = row,
                            column = column,
                            rpmBin = wObj.optInt("rpmBin"),
                            petrolBin = wObj.optDouble("petrolBin"),
                            axisSource = axisSource,
                            geometryFingerprint = geometryFingerprint,
                        )
                    }
                    target.weight += weight
                    target.petrolWeighted += region.optDouble("petrol_ms", region.optDouble("petrol_mean", 0.0)) * weight
                    target.rpmWeighted += region.optDouble("rpm", region.optDouble("rpm_mean", 0.0)) * weight
                    target.mapWeighted += region.optDouble("map_bar", region.optDouble("map_mean", 0.0)) * weight
                    target.samples += (region.optInt("samples", 0) * cellWeight).toInt()
                    target.addEvidenceIds(region, index)
                    target.confidenceWeighted += region.optDouble("confidence", 0.0) * weight
                    target.stage = strongerStage(target.stage, region.optString("stage", "OBSERVED"))
                    target.regionIds.put(region.optString("id", "region-$index"))
                    target.updatedAt = max(target.updatedAt, region.optLong("updated_at", region.optLong("updatedAt", 0L)))
                }
            } else {
                val row = cell.optInt("row", -1)
                val column = cell.optInt("column", -1)
                if (row < 0 || column < 0) return@repeat
                val key = "$fuel:$regionEpoch:$row:$column"
                val weight = max(0.001, region.optDouble("weight", region.optDouble("samples", 1.0)))
                val target = grouped.getOrPut(key) {
                    MutableCell(
                        fuel = fuel,
                        epoch = regionEpoch,
                        row = row,
                        column = column,
                        rpmBin = cell.optInt("rpmBin"),
                        petrolBin = cell.optDouble("petrolBin"),
                        axisSource = axisSource,
                        geometryFingerprint = geometryFingerprint,
                    )
                }
                target.weight += weight
                target.petrolWeighted += region.optDouble("petrol_ms", region.optDouble("petrol_mean", 0.0)) * weight
                target.rpmWeighted += region.optDouble("rpm", region.optDouble("rpm_mean", 0.0)) * weight
                target.mapWeighted += region.optDouble("map_bar", region.optDouble("map_mean", 0.0)) * weight
                target.samples += region.optInt("samples", 0)
                target.addEvidenceIds(region, index)
                target.confidenceWeighted += region.optDouble("confidence", 0.0) * weight
                target.stage = strongerStage(target.stage, region.optString("stage", "OBSERVED"))
                target.regionIds.put(region.optString("id", "region-$index"))
                target.updatedAt = max(target.updatedAt, region.optLong("updated_at", region.optLong("updatedAt", 0L)))
            }
        }
        return JSONArray(grouped.values.map { it.toJson() })
    }

    fun integrity(
        regions: JSONArray,
        cells: JSONArray,
        comparisons: JSONArray,
        epoch: Int,
        mapHash: String,
    ): JSONObject {
        val petrolCells = mutableSetOf<String>()
        val cngCells = mutableSetOf<String>()
        repeat(cells.length()) { index ->
            val cell = cells.optJSONObject(index) ?: return@repeat
            val key = cell.optString("key")
            when (normalizeFuel(cell.optString("fuel"))) {
                "PETROL" -> petrolCells += key
                "CNG" -> if (cell.optInt("epoch", epoch) == epoch) cngCells += key
            }
        }
        val comparable = petrolCells.intersect(cngCells).size
        val projected = project(regions, epoch)
        val projectedByKey = keyed(projected)
        val suppliedByKey = keyed(cells)
        val onlyInMemory = projectedByKey.keys - suppliedByKey.keys
        val onlyInProjection = suppliedByKey.keys - projectedByKey.keys
        val valueDivergences = projectedByKey.keys.intersect(suppliedByKey.keys).filter { key ->
            projectedByKey.getValue(key).toString() != suppliedByKey.getValue(key).toString()
        }
        val projectionHash = sha256(cells.toString())
        val canonical = JSONObject()
            .put("format", "omegas-learning-v5")
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("regions", regions)
            .put("cells", cells)
            .put("comparisons", comparisons)
            .toString()
        return JSONObject()
            .put("ok", onlyInMemory.isEmpty() && onlyInProjection.isEmpty() && valueDivergences.isEmpty())
            .put("format", "omegas-learning-v5")
            .put("geometryKnown", axisContext() != null)
            .put("regions", regions.length())
            .put("cells", cells.length())
            .put("petrolCells", petrolCells.size)
            .put("cngCellsCurrentEpoch", cngCells.size)
            .put("comparableCells", comparable)
            .put("comparisons", comparisons.length())
            .put("epoch", epoch)
            .put("mapHash", mapHash)
            .put("digest", sha256(canonical))
            .put("projectionHash", projectionHash)
            .put("memoryProjectionHash", projectionHash)
            .put("interfaceProjectionHash", projectionHash)
            .put("exportProjectionHash", projectionHash)
            .put("onlyInMemory", JSONArray(onlyInMemory.sorted()))
            .put("onlyInInterface", JSONArray(onlyInProjection.sorted()))
            .put("onlyInExport", JSONArray())
            .put("valueDivergences", JSONArray(valueDivergences.sorted()))
            .put("memoryEqualsInterface", onlyInMemory.isEmpty() && onlyInProjection.isEmpty() && valueDivergences.isEmpty())
            .put("interfaceEqualsExport", true)
            .put("message", "Memória, grade e exportação usam a mesma projeção nativa")
    }

    private fun keyed(cells: JSONArray): Map<String, JSONObject> = buildMap {
        repeat(cells.length()) { index ->
            val cell = cells.optJSONObject(index) ?: return@repeat
            val key = "${normalizeFuel(cell.optString("fuel"))}:${cell.optInt("epoch")}:${cell.optString("key")}"
            put(key, cell)
        }
    }

    private fun normalizeFuel(raw: String): String = when (raw.uppercase()) {
        "PETROL", "GASOLINA" -> "PETROL"
        "CNG", "GNV" -> "CNG"
        else -> "UNKNOWN"
    }

    private fun nearest(values: DoubleArray, value: Double): Int {
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        values.forEachIndexed { index, candidate ->
            val distance = abs(candidate - value)
            if (distance < bestDistance) {
                bestIndex = index
                bestDistance = distance
            }
        }
        return bestIndex
    }

    private fun strongerStage(a: String, b: String): String =
        if (stageRank(b) > stageRank(a)) b.uppercase() else a.uppercase()

    private fun stageRank(stage: String): Int = when (stage.uppercase()) {
        "CONFIRMED" -> 4
        "ACCEPTED" -> 3
        "PROVISIONAL" -> 2
        "OBSERVED" -> 1
        else -> 0
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class MutableCell(
        val fuel: String,
        val epoch: Int,
        val row: Int,
        val column: Int,
        val rpmBin: Int,
        val petrolBin: Double,
        val axisSource: String,
        val geometryFingerprint: String,
        var weight: Double = 0.0,
        var petrolWeighted: Double = 0.0,
        var rpmWeighted: Double = 0.0,
        var mapWeighted: Double = 0.0,
        var samples: Int = 0,
        var confidenceWeighted: Double = 0.0,
        var stage: String = "OBSERVED",
        var updatedAt: Long = 0L,
        val regionIds: JSONArray = JSONArray(),
        val visitIds: MutableSet<String> = linkedSetOf(),
        val sessionIds: MutableSet<String> = linkedSetOf(),
        var visitCountFloor: Int = 0,
        var sessionCountFloor: Int = 0,
    ) {
        fun addEvidenceIds(region: JSONObject, index: Int) {
            val visits = region.optJSONArray("visits")
            val sessions = region.optJSONArray("sessions")
            if (visits != null) repeat(visits.length()) { visitIndex ->
                visits.optString(visitIndex).takeIf { it.isNotBlank() }?.let(visitIds::add)
            }
            if (sessions != null) repeat(sessions.length()) { sessionIndex ->
                sessions.optString(sessionIndex).takeIf { it.isNotBlank() }?.let(sessionIds::add)
            }

            // Quando a lista textual foi compactada, a contagem total permanece exata
            // no nível da região. Usamos-a como piso conservador sem fabricar IDs.
            visitCountFloor = max(
                visitCountFloor,
                max(region.optInt("visit_count", visits?.length() ?: 0), visits?.length() ?: 0),
            )
            sessionCountFloor = max(
                sessionCountFloor,
                max(region.optInt("session_count", sessions?.length() ?: 0), sessions?.length() ?: 0),
            )
        }

        fun toJson(): JSONObject = JSONObject()
            .put("fuel", fuel)
            .put("epoch", epoch)
            .put("row", row)
            .put("column", column)
            .put("key", "$row:$column")
            .put("geometryKnown", true)
            .put("axisSource", axisSource)
            .put("geometryFingerprint", geometryFingerprint)
            .put("rpm_bin", rpmBin)
            .put("petrol_bin", petrolBin)
            .put("rpm", if (weight > 0) rpmWeighted / weight else 0.0)
            .put("petrol_ms", if (weight > 0) petrolWeighted / weight else 0.0)
            .put("map_bar", if (weight > 0) mapWeighted / weight else 0.0)
            .put("samples", samples)
            .put("visit_count", max(visitIds.size, visitCountFloor))
            .put("session_count", max(sessionIds.size, sessionCountFloor))
            .put("confidence", if (weight > 0) confidenceWeighted / weight else 0.0)
            .put("stage", stage)
            .put("updated_at", updatedAt)
            .put("region_ids", regionIds)
    }
}
