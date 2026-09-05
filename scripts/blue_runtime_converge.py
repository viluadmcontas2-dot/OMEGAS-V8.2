#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def path(rel: str) -> Path:
    return ROOT / rel


def write(rel: str, content: str) -> None:
    target = path(rel)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def delete(rel: str) -> None:
    target = path(rel)
    if target.is_file():
        target.unlink()
    elif target.is_dir():
        for child in sorted(target.rglob("*"), reverse=True):
            if child.is_file() or child.is_symlink():
                child.unlink()
            elif child.is_dir():
                child.rmdir()
        target.rmdir()


def replace(rel: str, old: str, new: str) -> None:
    target = path(rel)
    text = target.read_text(encoding="utf-8")
    if old in text:
        target.write_text(text.replace(old, new), encoding="utf-8")


def regex_replace(rel: str, pattern: str, replacement: str, flags: int = 0) -> None:
    target = path(rel)
    text = target.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, flags=flags)
    if count:
        target.write_text(updated, encoding="utf-8")


# -----------------------------------------------------------------------------
# 1. Clean, unversioned calibration shape and protocol infrastructure.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/omegas/prohub/calibration/CalibrationShape.kt",
    r'''package com.omegas.prohub.calibration

/** Physical dimensions proven for this MP48 calibration. */
object CalibrationShape {
    const val CURVE_K_POINTS = 30
    const val MAP_K_COLUMNS = 12
    const val MAP_K_TIME_THRESHOLDS = 12
    const val MAP_K_EDITABLE_ROWS = 12
    const val MAP_K_STORAGE_ROWS = 13

    fun requireCurve(curve: List<Double>) {
        require(curve.size == CURVE_K_POINTS) {
            "Curva K exige $CURVE_K_POINTS pontos"
        }
        require(curve.all { it.isFinite() && it > 0.0 }) {
            "Curva K contém fator inválido"
        }
    }

    fun requireMap(map: List<List<Int>>) {
        require(map.size == MAP_K_STORAGE_ROWS && map.all { it.size == MAP_K_COLUMNS }) {
            "Mapa K exige ${MAP_K_STORAGE_ROWS}x${MAP_K_COLUMNS} no armazenamento físico"
        }
        require(map.flatten().all { it in 0..0xFF }) {
            "Mapa K aceita somente valores U8"
        }
    }

    fun requireEditableCell(row: Int, column: Int) {
        require(row in 0 until MAP_K_EDITABLE_ROWS) {
            "Linha do Mapa K não editável: $row"
        }
        require(column in 0 until MAP_K_COLUMNS) {
            "Coluna do Mapa K inexistente: $column"
        }
    }
}
''',
)

write(
    "app/src/main/java/com/omegas/prohub/ecu/AebProtocolFrames.kt",
    r'''package com.omegas.prohub.ecu

import com.omegas.prohub.calibration.CalibrationShape

/**
 * Quadros AEB reconstruídos do ProgBase e validados contra tráfego serial real.
 * O checksum é a soma dos bytes anteriores truncada em U8.
 */
object AebProtocolFrames {
    const val ADDRESS_MAP_K = 0x0054
    const val ADDRESS_AUTOCAL_INJECTION_AXIS = 0x014B
    const val ADDRESS_AUTOCAL_PRESSURE_THRESHOLDS = 0x014C
    const val ADDRESS_MUL_ACT = 0x0161
    const val ADDRESS_PETROL_PRESSURE_RESAMPLED = 0x018D
    const val ADDRESS_CNG_PRESSURE_RESAMPLED = 0x018E

    fun getNumber(address: Int): ByteArray = addressed(0x09, address)
    fun getNumber(address: Int, index: Int): ByteArray = addressed(0x0A, address, byte(index))
    fun getNumber(address: Int, row: Int, column: Int): ByteArray =
        addressed(0x0B, address, byte(row), byte(column))
    fun getVector(address: Int): ByteArray = addressed(0x29, address)
    fun getVector(address: Int, row: Int): ByteArray = addressed(0x2A, address, byte(row))

    fun setNumber(address: Int, body: ByteArray): ByteArray {
        require(body.isNotEmpty() && body.size <= 6)
        return addressed(0x11 + body.size, address, *body)
    }

    fun setVector(address: Int, payload: ByteArray): ByteArray {
        requireAddress(address)
        return if (payload.size <= 5) {
            withChecksum(byteArrayOf((0x31 + payload.size).toByte(), low(address), high(address)) + payload)
        } else {
            val blockLength = payload.size + 2
            require(blockLength <= 0xFF)
            withChecksum(
                byteArrayOf(0x37, low(address), blockLength.toByte(), high(address)) + payload,
            )
        }
    }

    fun readMapRow(row: Int): ByteArray {
        require(row in 0 until CalibrationShape.MAP_K_STORAGE_ROWS)
        return getVector(ADDRESS_MAP_K, row)
    }

    fun writeMapCell(row: Int, column: Int, value: Int): ByteArray {
        CalibrationShape.requireEditableCell(row, column)
        require(value in 0..0xFF)
        return setNumber(
            ADDRESS_MAP_K,
            byteArrayOf(row.toByte(), column.toByte(), value.toByte()),
        )
    }

    fun writeMapRow(row: Int, values: List<Int>): ByteArray {
        require(row in 0 until CalibrationShape.MAP_K_STORAGE_ROWS)
        require(values.size == CalibrationShape.MAP_K_COLUMNS)
        require(values.all { it in 0..0xFF })
        return setVector(ADDRESS_MAP_K, byteArrayOf(row.toByte()) + values.map { it.toByte() }.toByteArray())
    }

    fun readMulAct(): ByteArray = getVector(ADDRESS_MUL_ACT)

    fun writeMulActPoint(index: Int, rawU16: Int): ByteArray {
        require(index in 0 until CalibrationShape.CURVE_K_POINTS)
        require(rawU16 in 0..0xFFFF)
        return setNumber(
            ADDRESS_MUL_ACT,
            byteArrayOf(index.toByte(), low(rawU16), high(rawU16)),
        )
    }

    fun writeMulAct(rawU16: List<Int>): ByteArray {
        require(rawU16.size == CalibrationShape.CURVE_K_POINTS)
        require(rawU16.all { it in 0..0xFFFF })
        val payload = ByteArray(rawU16.size * 2)
        rawU16.forEachIndexed { index, value ->
            payload[index * 2] = low(value)
            payload[index * 2 + 1] = high(value)
        }
        return setVector(ADDRESS_MUL_ACT, payload)
    }

    fun checksumIsValid(frame: ByteArray): Boolean =
        frame.isNotEmpty() && checksum(frame.copyOf(frame.size - 1)) == frame.last()

    private fun addressed(opcode: Int, address: Int, vararg body: Byte): ByteArray {
        requireAddress(address)
        return withChecksum(byteArrayOf(opcode.toByte(), low(address), high(address)) + body)
    }

    private fun withChecksum(content: ByteArray): ByteArray = content + checksum(content)
    private fun checksum(content: ByteArray): Byte =
        content.fold(0) { sum, value -> (sum + value.toUByte().toInt()) and 0xFF }.toByte()
    private fun requireAddress(address: Int) = require(address in 0..0xFFFF)
    private fun low(value: Int): Byte = (value and 0xFF).toByte()
    private fun high(value: Int): Byte = ((value ushr 8) and 0xFF).toByte()
    private fun byte(value: Int): Byte {
        require(value in 0..0xFF)
        return value.toByte()
    }
}
''',
)

# Pure protocol/shape users move to the unversioned names before old packages die.
for base in [path("app/src/main/java"), path("app/src/test/java")]:
    if not base.exists():
        continue
    for target in base.rglob("*.kt"):
        text = target.read_text(encoding="utf-8")
        updated = text.replace(
            "com.omegas.v7.protocol.AebProtocolFramesV7",
            "com.omegas.prohub.ecu.AebProtocolFrames",
        ).replace(
            "AebProtocolFramesV7",
            "AebProtocolFrames",
        ).replace(
            "com.omegas.v7.runtime.CalibrationShapeV7",
            "com.omegas.prohub.calibration.CalibrationShape",
        ).replace(
            "CalibrationShapeV7",
            "CalibrationShape",
        )
        if updated != text:
            target.write_text(updated, encoding="utf-8")

# -----------------------------------------------------------------------------
# 2. Blue-native domain. No suggestion engine, no version facade.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/omegas/prohub/blue/BlueDomain.kt",
    r'''package com.omegas.prohub.blue

import com.omegas.prohub.calibration.CalibrationShape

enum class FuelKind { PETROL, CNG }

data class CalibrationRevision(
    val curveK: Long,
    val mapK: Long,
) {
    init {
        require(curveK >= 0)
        require(mapK >= 0)
    }
}

data class CalibrationState(
    val revision: CalibrationRevision,
    val curveK: List<Double>,
    val mapK: List<List<Int>>,
) {
    init {
        CalibrationShape.requireCurve(curveK)
        CalibrationShape.requireMap(mapK)
    }
}

data class FuelEvidence(
    val id: String,
    val fuel: FuelKind,
    val collectedAtMs: Long,
    val visitId: String,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val quality: Double,
    val cngRevision: CalibrationRevision?,
    val waterC: Double = UNKNOWN_TEMPERATURE_C,
    val gasC: Double = UNKNOWN_TEMPERATURE_C,
    val pressureDiffBar: Double = 0.0,
) {
    companion object {
        const val UNKNOWN_TEMPERATURE_C = -273.15
    }

    init {
        require(id.isNotBlank())
        require(visitId.isNotBlank())
        require(collectedAtMs >= 0)
        require(rpm.isFinite() && rpm >= 0.0)
        require(mapBar.isFinite() && mapBar >= 0.0)
        require(petrolMs.isFinite() && petrolMs >= 0.0)
        require(quality.isFinite() && quality in 0.0..1.0)
        require(waterC.isFinite())
        require(gasC.isFinite())
        require(pressureDiffBar.isFinite())
        require((fuel == FuelKind.PETROL && cngRevision == null) ||
            (fuel == FuelKind.CNG && cngRevision != null))
    }
}

data class FuelComparison(
    val id: String,
    val revision: CalibrationRevision,
    val petrolVisitId: String,
    val cngVisitId: String,
    val rpm: Double,
    val mapBar: Double,
    val petrolTargetMs: Double,
    val petrolOnCngMs: Double,
    val errorPercent: Double,
    val quality: Double,
    val createdAtMs: Long,
)

data class BlueLearningState(
    val sessionId: String,
    val calibration: CalibrationState,
    val petrolEvidence: List<FuelEvidence> = emptyList(),
    val cngEvidenceByRevision: Map<CalibrationRevision, List<FuelEvidence>> = emptyMap(),
    val comparisons: List<FuelComparison> = emptyList(),
) {
    init { require(sessionId.isNotBlank()) }

    fun activeCngEvidence(): List<FuelEvidence> = cngEvidenceByRevision[calibration.revision].orEmpty()
    fun activeComparisons(): List<FuelComparison> = comparisons.filter { it.revision == calibration.revision }
}
''',
)

# -----------------------------------------------------------------------------
# 3. One causal engine. Gasoline is reference; CNG is compared with it.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt",
    r'''package com.omegas.prohub.blue

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Single scientific authority for gasoline reference and CNG deviation.
 *
 * A coordinate hit is not enough. Reference quality rewards physical proximity,
 * stable evidence and thermal context. The engine never invents an actuator
 * response: a K correction is available only after a causal gain was measured
 * from an actual before/after calibration event.
 */
class BlueCausalEngine(
    private val policy: BluePolicy = BluePolicy(),
) {
    fun calibrationState(revision: CalibrationRevision): BlueCalibrationStateId =
        BlueCalibrationStateId(revision.curveK, revision.mapK)

    fun petrolReference(
        target: FuelEvidence,
        petrolEvidence: List<FuelEvidence>,
    ): BluePetrolReference? {
        val candidates = petrolEvidence
            .asSequence()
            .filter { it.fuel == FuelKind.PETROL }
            .filter { it.petrolMs > 0.0 && it.quality >= policy.minimumEvidenceQuality }
            .map { evidence -> BlueCandidate(evidence, normalizedDistance(evidence, target)) }
            .filter { it.distance <= policy.maximumNormalizedDistance }
            .sortedBy { it.distance }
            .take(policy.maximumReferenceBursts)
            .toList()
        if (candidates.isEmpty()) return null

        val values = candidates.map { it.evidence.petrolMs }.sorted()
        val median = if (values.size % 2 == 0) {
            (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        } else values[values.size / 2]
        val meanQuality = candidates.map { it.evidence.quality }.average()
        val proximity = exp(-candidates.map { it.distance }.average()).coerceIn(0.0, 1.0)
        val quality = (sqrt(meanQuality * target.quality.coerceIn(0.0, 1.0)) * proximity)
            .coerceIn(0.0, 1.0)
        return BluePetrolReference(
            petrolMs = median,
            quality = quality,
            supportCount = candidates.size,
            nearestDistance = candidates.first().distance,
        )
    }

    fun cngErrorLog(petrolOnCngMs: Double, petrolReferenceMs: Double): Double {
        require(petrolOnCngMs > 0.0 && petrolReferenceMs > 0.0)
        return ln(petrolOnCngMs / petrolReferenceMs)
    }

    fun errorPercentFromLog(errorLog: Double): Double = (exp(errorLog) - 1.0) * 100.0

    fun actuatorGain(
        beforeErrorLog: Double,
        afterErrorLog: Double,
        beforeK: Double,
        afterK: Double,
    ): BlueActuatorGain? {
        if (!beforeK.isFinite() || !afterK.isFinite() || beforeK <= 0.0 || afterK <= 0.0) return null
        val deltaLnK = ln(afterK / beforeK)
        if (abs(deltaLnK) < policy.minimumActuatorStepLog) return null
        val gain = -(afterErrorLog - beforeErrorLog) / deltaLnK
        if (!gain.isFinite() || gain <= policy.minimumAcceptedGain || gain > policy.maximumAcceptedGain) return null
        return BlueActuatorGain(gain)
    }

    fun correctionMultiplier(errorLog: Double, gain: BlueActuatorGain?): Double? {
        gain ?: return null
        return exp(errorLog / gain.gain).coerceIn(
            policy.minimumCorrectionMultiplier,
            policy.maximumCorrectionMultiplier,
        )
    }

    fun reconcile(
        state: BlueLearningState,
        nowMs: Long = System.currentTimeMillis(),
    ): List<FuelComparison> {
        val activeRevision = state.calibration.revision
        val historical = state.comparisons.filter { it.revision != activeRevision }
        val active = state.activeCngEvidence().mapNotNull { cng ->
            compare(cng, state.petrolEvidence, activeRevision, nowMs)
        }
        return (historical + active)
            .distinctBy { it.id }
            .sortedWith(compareBy<FuelComparison> { it.createdAtMs }.thenBy { it.cngVisitId })
    }

    private fun compare(
        cng: FuelEvidence,
        petrolEvidence: List<FuelEvidence>,
        revision: CalibrationRevision,
        nowMs: Long,
    ): FuelComparison? {
        val reference = petrolReference(cng, petrolEvidence) ?: return null
        if (reference.quality < policy.minimumComparisonQuality) return null
        val errorLog = cngErrorLog(cng.petrolMs, reference.petrolMs)
        val errorPercent = errorPercentFromLog(errorLog)
        if (abs(cng.petrolMs - reference.petrolMs) <= policy.absoluteDeadbandMs ||
            abs(errorPercent) <= policy.relativeDeadbandPercent
        ) {
            return null
        }
        return FuelComparison(
            id = "${revision.curveK}:${revision.mapK}:${cng.visitId}",
            revision = revision,
            petrolVisitId = "reference:${cng.visitId}",
            cngVisitId = cng.visitId,
            rpm = cng.rpm,
            mapBar = cng.mapBar,
            petrolTargetMs = reference.petrolMs,
            petrolOnCngMs = cng.petrolMs,
            errorPercent = errorPercent,
            quality = reference.quality,
            createdAtMs = max(cng.collectedAtMs, nowMs.coerceAtLeast(0L)),
        )
    }

    private fun normalizedDistance(reference: FuelEvidence, target: FuelEvidence): Double {
        val rpmScale = max(policy.minimumRpmWindow, target.rpm * policy.relativeRpmWindow)
        val rpm = abs(reference.rpm - target.rpm) / rpmScale
        val map = abs(reference.mapBar - target.mapBar) / policy.mapWindowBar
        val water = when {
            reference.waterC == FuelEvidence.UNKNOWN_TEMPERATURE_C ||
                target.waterC == FuelEvidence.UNKNOWN_TEMPERATURE_C -> 0.0
            else -> abs(reference.waterC - target.waterC) / policy.waterWindowC
        }
        return sqrt(rpm.pow(2) + map.pow(2) + water.pow(2))
    }

    private data class BlueCandidate(val evidence: FuelEvidence, val distance: Double)
}

data class BluePolicy(
    val minimumEvidenceQuality: Double = 0.45,
    val minimumComparisonQuality: Double = 0.50,
    val minimumRpmWindow: Double = 120.0,
    val relativeRpmWindow: Double = 0.06,
    val mapWindowBar: Double = 0.08,
    val waterWindowC: Double = 8.0,
    val maximumNormalizedDistance: Double = 1.75,
    val maximumReferenceBursts: Int = 7,
    val absoluteDeadbandMs: Double = 0.08,
    val relativeDeadbandPercent: Double = 2.0,
    val minimumActuatorStepLog: Double = 0.003,
    val minimumAcceptedGain: Double = 0.05,
    val maximumAcceptedGain: Double = 12.0,
    val minimumCorrectionMultiplier: Double = 0.80,
    val maximumCorrectionMultiplier: Double = 1.20,
)

data class BluePetrolReference(
    val petrolMs: Double,
    val quality: Double,
    val supportCount: Int,
    val nearestDistance: Double,
)

data class BlueActuatorGain(val gain: Double)

data class BlueCalibrationStateId(val curveK: Long, val mapK: Long)

data class BlueCorrectionProposal(
    val calibrationState: BlueCalibrationStateId,
    val correctionMultiplier: Double?,
    val errorLog: Double,
    val errorPercent: Double,
    val actuatorGain: BlueActuatorGain?,
    val automaticWrite: Boolean = false,
)
''',
)

write(
    "app/src/main/java/com/omegas/prohub/blue/BlueAutoCalAdapter.kt",
    r'''package com.omegas.prohub.blue

import org.json.JSONObject
import kotlin.math.ln

/** Auto-Cal consumes the single causal engine and never owns correction math. */
class BlueAutoCalAdapter(
    private val engine: BlueCausalEngine,
) {
    fun proposal(
        comparison: FuelComparison,
        gain: BlueActuatorGain?,
    ): BlueCorrectionProposal {
        require(comparison.petrolTargetMs > 0.0)
        require(comparison.petrolOnCngMs > 0.0)
        val errorLog = engine.cngErrorLog(comparison.petrolOnCngMs, comparison.petrolTargetMs)
        return BlueCorrectionProposal(
            calibrationState = engine.calibrationState(comparison.revision),
            correctionMultiplier = engine.correctionMultiplier(errorLog, gain),
            errorLog = errorLog,
            errorPercent = engine.errorPercentFromLog(errorLog),
            actuatorGain = gain,
            automaticWrite = false,
        )
    }

    fun proposalJson(comparison: FuelComparison, gain: BlueActuatorGain?): JSONObject {
        val proposal = proposal(comparison, gain)
        return JSONObject()
            .put("ok", true)
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
            .put("automatic", false)
            .put("manualOnly", true)
            .put("curveRevision", proposal.calibrationState.curveK)
            .put("mapRevision", proposal.calibrationState.mapK)
            .put("petrolReferenceMs", comparison.petrolTargetMs)
            .put("petrolOnCngMs", comparison.petrolOnCngMs)
            .put("errorLog", proposal.errorLog)
            .put("errorPercent", proposal.errorPercent)
            .put("actuatorGain", proposal.actuatorGain?.gain ?: JSONObject.NULL)
            .put("correctionMultiplier", proposal.correctionMultiplier ?: JSONObject.NULL)
            .put("state", if (proposal.correctionMultiplier == null) "MEASURE_ACTUATOR_GAIN" else "PROPOSAL_READY")
    }

    fun learnGain(
        beforePetrolOnCngMs: Double,
        beforePetrolReferenceMs: Double,
        afterPetrolOnCngMs: Double,
        afterPetrolReferenceMs: Double,
        beforeK: Double,
        afterK: Double,
    ): BlueActuatorGain? = engine.actuatorGain(
        beforeErrorLog = ln(beforePetrolOnCngMs / beforePetrolReferenceMs),
        afterErrorLog = ln(afterPetrolOnCngMs / afterPetrolReferenceMs),
        beforeK = beforeK,
        afterK = afterK,
    )
}
''',
)

write(
    "app/src/main/java/com/omegas/prohub/blue/BlueEvidenceProjection.kt",
    r'''package com.omegas.prohub.blue

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject

/** Passive view of current K cells. It never calculates a correction target. */
object BlueEvidenceProjection {
    fun build(learningSnapshot: JSONObject, confirmedMapSnapshot: JSONObject?): JSONObject {
        val rows = confirmedMapSnapshot?.optJSONArray("rows")
        val petrolBins = KMapPhysicalAxes.petrolBins()
        val rpmBins = KMapPhysicalAxes.rpmBins()
        val cells = JSONArray()
        petrolBins.indices.forEach { row ->
            rpmBins.indices.forEach { column ->
                val currentK = mapValue(rows, row, column)
                cells.put(
                    JSONObject()
                        .put("key", "$row:$column")
                        .put("row", row)
                        .put("column", column)
                        .put("rpm", rpmBins[column])
                        .put("petrolMs", petrolBins[row])
                        .put("currentK", currentK ?: JSONObject.NULL)
                        .put("targetK", JSONObject.NULL)
                        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
                        .put("automaticWrite", false),
                )
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("source", "BLUE_CAUSAL_ENGINE")
            .put("epoch", learningSnapshot.optInt("epoch", 1).coerceAtLeast(1))
            .put("rows", petrolBins.size)
            .put("columns", rpmBins.size)
            .put("physicalAxis", "RPM_X_PETROL_INJECTION_MS")
            .put("cells", cells)
            .put("automaticWrite", false)
            .put("humanReviewRequired", true)
    }

    private fun mapValue(rows: JSONArray?, row: Int, column: Int): Int? {
        val line = rows?.optJSONArray(row) ?: return null
        if (column !in 0 until line.length()) return null
        return (line.opt(column) as? Number)?.toInt()?.takeIf { it in 0..255 }
    }
}
''',
)

# -----------------------------------------------------------------------------
# 4. Clean calibration coordinator: readback + physical evidence + Blue proposal.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt",
    r'''package com.omegas.prohub.calibration

import com.omegas.prohub.blue.BlueAutoCalAdapter
import com.omegas.prohub.blue.BlueCausalEngine
import com.omegas.prohub.blue.BlueLearningState
import com.omegas.prohub.blue.CalibrationRevision
import com.omegas.prohub.blue.CalibrationState
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
 */
class BlueCalibrationCoordinator(
    private val mapManager: KWriteManager,
    private val factorManager: KFactorManager,
) {
    private val lock = Any()
    private val engine = BlueCausalEngine()
    private val autoCal = BlueAutoCalAdapter(engine)
    private var state: BlueLearningState? = null

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
        autoCal.proposalJson(comparison, gain = null)
            .put("available", true)
    }

    private fun stateJsonLocked(): JSONObject {
        val current = state ?: return JSONObject()
            .put("ready", false)
            .put("reason", "CALIBRATION_NOT_SYNCED")
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
        val latest = current.activeComparisons().maxByOrNull { it.createdAtMs }
        return JSONObject()
            .put("ready", true)
            .put("sessionId", current.sessionId)
            .put("revision", revisionJson(current.calibration.revision))
            .put("curvePoints", current.calibration.curveK.size)
            .put("mapStorageRows", current.calibration.mapK.size)
            .put("petrolEvidence", current.petrolEvidence.size)
            .put("activeCngEvidence", current.activeCngEvidence().size)
            .put("activeComparisons", current.activeComparisons().size)
            .put("latestComparison", latest?.let(::comparisonJson) ?: JSONObject.NULL)
            .put("proposal", proposalJsonLocked(latest))
            .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
            .put("automaticWrite", false)
    }

    private fun proposalJsonLocked(comparison: com.omegas.prohub.blue.FuelComparison?): Any =
        comparison?.let { autoCal.proposalJson(it, gain = null) } ?: JSONObject.NULL

    private fun comparisonJson(value: com.omegas.prohub.blue.FuelComparison): JSONObject = JSONObject()
        .put("id", value.id)
        .put("rpm", value.rpm)
        .put("mapBar", value.mapBar)
        .put("petrolReferenceMs", value.petrolTargetMs)
        .put("petrolOnCngMs", value.petrolOnCngMs)
        .put("errorPercent", value.errorPercent)
        .put("quality", value.quality)
        .put("createdAt", value.createdAtMs)

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
''',
)

write(
    "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt",
    r'''package com.omegas.prohub.service

import com.omegas.prohub.blue.BlueEvidenceProjection
import com.omegas.prohub.calibration.BlueCalibrationCoordinator
import com.omegas.prohub.learning.LearningTelemetrySchemaMigration
import org.json.JSONObject
import java.io.File
import java.util.WeakHashMap

private object BlueCalibrationRegistry {
    private val lock = Any()
    private val coordinators = WeakHashMap<TelemetryForegroundService, BlueCalibrationCoordinator>()

    fun get(service: TelemetryForegroundService): BlueCalibrationCoordinator = synchronized(lock) {
        coordinators.getOrPut(service) {
            BlueCalibrationCoordinator(service.kWriter, service.kFactor)
        }
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        coordinators.remove(service)
        BlueProjectionCache.remove(service)
        Unit
    }
}

private object BlueProjectionCache {
    private data class Entry(val signature: String, val snapshot: JSONObject)
    private val lock = Any()
    private val entries = WeakHashMap<TelemetryForegroundService, Entry>()

    fun get(service: TelemetryForegroundService): JSONObject = synchronized(lock) {
        val signature = signature(service)
        entries[service]?.takeIf { it.signature == signature }?.let {
            return JSONObject(it.snapshot.toString())
        }
        val learning = service.runtime.exportLearning(service.settings.deviceId)
        val mapFile = File(service.paths.runtimeRoot, "k_map_cache.json")
        val map = try {
            mapFile.takeIf { it.isFile }?.let { JSONObject(it.readText(Charsets.UTF_8)) }
                ?.takeIf { it.optBoolean("complete", false) && it.optBoolean("sessionConfirmed", false) }
        } catch (_: Exception) { null }
        val snapshot = BlueEvidenceProjection.build(learning, map)
        entries[service] = Entry(signature, JSONObject(snapshot.toString()))
        JSONObject(snapshot.toString())
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        entries.remove(service)
        Unit
    }

    private fun signature(service: TelemetryForegroundService): String {
        val root = service.paths.runtimeRoot
        return listOf(
            File(root, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE),
            File(root, "learning_evidence.json"),
            File(root, "k_map_cache.json"),
        ).joinToString("|") { file ->
            if (file.isFile) "${file.name}:${file.lastModified()}:${file.length()}" else "${file.name}:missing"
        }
    }
}

fun TelemetryForegroundService.blueCalibrationStateJson(): String = try {
    val coordinator = BlueCalibrationRegistry.get(this)
    JSONObject(coordinator.stateJson().toString())
        .put("evidenceProjection", BlueProjectionCache.get(this))
        .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ready", false)
        .put("error", error.message ?: "Estado Blue indisponível")
        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
        .toString()
}

fun TelemetryForegroundService.blueSynchronizeCalibration(): String = try {
    BlueCalibrationRegistry.get(this).synchronizeFromEcu().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao sincronizar calibração").toString()
}

fun TelemetryForegroundService.blueReconcileConfirmedManualWrite(): String = try {
    BlueCalibrationRegistry.get(this).reconcileConfirmedManualWrite().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha no readback após escrita").toString()
}

fun TelemetryForegroundService.blueIngestLearningSnapshot(payload: String): String = try {
    BlueCalibrationRegistry.get(this).ingestLearningSnapshot(JSONObject(payload)).toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao importar evidência").toString()
}

fun TelemetryForegroundService.blueProposalJson(): String = try {
    BlueCalibrationRegistry.get(this).proposalJson().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Proposta Blue indisponível").toString()
}

fun TelemetryForegroundService.releaseBlueCalibrationCoordinator() {
    BlueCalibrationRegistry.remove(this)
}
''',
)

# -----------------------------------------------------------------------------
# 5. Clean bridge. Manual writes preserve safety, ACK and readback.
# -----------------------------------------------------------------------------
old_bridge = path("app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt")
if old_bridge.is_file():
    bridge = old_bridge.read_text(encoding="utf-8")
    bridge = bridge.replace("V7JavascriptBridge", "BlueJavascriptBridge")
    bridge = bridge.replace("com.omegas.prohub.service.v7CalibrationStateJson", "com.omegas.prohub.service.blueCalibrationStateJson")
    bridge = bridge.replace("com.omegas.prohub.service.v7IngestLearningSnapshot", "com.omegas.prohub.service.blueIngestLearningSnapshot")
    bridge = bridge.replace("com.omegas.prohub.service.v7ReconcileConfirmedManualWrite", "com.omegas.prohub.service.blueReconcileConfirmedManualWrite")
    bridge = re.sub(r"import com\.omegas\.prohub\.service\.v7(?:LoadSession|SaveSession|SessionFilesJson|SynchronizeAdvisorSuggestions|SynchronizeCalibration)\n", "", bridge)
    bridge = bridge.replace("v7CalibrationStateJson", "blueCalibrationStateJson")
    bridge = bridge.replace("v7IngestLearningSnapshot", "blueIngestLearningSnapshot")
    bridge = bridge.replace("v7ReconcileConfirmedManualWrite(\"CURVE_K\")", "blueReconcileConfirmedManualWrite()")
    bridge = bridge.replace("v7ReconcileConfirmedManualWrite(\"MAP_K\")", "blueReconcileConfirmedManualWrite()")
    bridge = bridge.replace("omegas-v7-web-operations", "omegas-blue-calibration")
    bridge = bridge.replace("V8", "calibração")
    bridge = bridge.replace("V7", "Blue")
    # Remove old session/advice API sections; SessionRecorder and Blue evidence are the only session paths.
    bridge = re.sub(
        r"\n    @JavascriptInterface\n    fun listSessionFiles\(\): String =.*?\n\n    @JavascriptInterface\n    fun getLastOperation",
        "\n\n    @JavascriptInterface\n    fun getLastOperation",
        bridge,
        flags=re.S,
    )
    bridge = re.sub(
        r"\n    /\*\* Operação pura:.*?fun synchronizeAdvisorSuggestions\(adviceJson: String\): String =.*?\n\n    /\*\* Importação explícita",
        "\n\n    /** Importação explícita",
        bridge,
        flags=re.S,
    )
    bridge = re.sub(
        r"\n    @JavascriptInterface\n    fun synchronizeFromEcu\(fileName: String\): String =.*?\n    }\n",
        "\n    @JavascriptInterface\n    fun synchronizeFromEcu(): String = startOperation(\"SYNCHRONIZING_ECU\") { service ->\n        service.blueSynchronizeCalibration()\n    }\n",
        bridge,
        count=1,
        flags=re.S,
    )
    bridge = re.sub(
        r"\n    /\*\*\n     \* Compatibilidade de API:.*?fun applySuggestion\(suggestionId: String\): String =.*?\.toString\(\)\n",
        "\n",
        bridge,
        count=1,
        flags=re.S,
    )
    bridge = re.sub(
        r"\n    @JavascriptInterface\n    fun saveSession\(fileName: String\): String =.*?\n        \?: unavailable\(\)\n\n    @JavascriptInterface\n    fun loadSession\(fileName: String\): String =.*?\n        \?: unavailable\(\)\n",
        "\n",
        bridge,
        flags=re.S,
    )
    write("app/src/main/java/com/omegas/prohub/web/BlueJavascriptBridge.kt", bridge)

# -----------------------------------------------------------------------------
# 6. Wire Android and UI to Blue names.
# -----------------------------------------------------------------------------
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "import com.omegas.prohub.web.V7JavascriptBridge", "import com.omegas.prohub.web.BlueJavascriptBridge")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "private var v7Bridge: V7JavascriptBridge? = null", "private var blueBridge: BlueJavascriptBridge? = null")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "v7Bridge?.destroy()", "blueBridge?.destroy()")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "v7Bridge = null", "blueBridge = null")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "OmegasV7", "OmegasBlue")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "v7Bridge = V7JavascriptBridge(this)", "blueBridge = BlueJavascriptBridge(this)")
replace("app/src/main/java/com/omegas/prohub/MainActivity.kt", "webView.addJavascriptInterface(v7Bridge!!, \"OmegasBlue\")", "webView.addJavascriptInterface(blueBridge!!, \"OmegasBlue\")")

replace("app/src/main/assets/ui/core/native-api.js", "this.v7 = root.OmegasV7 || null;", "this.blue = root.OmegasBlue || null;")
replace("app/src/main/assets/ui/core/native-api.js", "invoke(this.v7,", "invoke(this.blue,")
replace("app/src/main/assets/ui/core/native-api.js", "Ponte V7 indisponível", "Ponte de calibração indisponível")
replace("app/src/main/assets/ui/core/native-api.js", "generation: 'V7'", "generation: 'BLUE'")

replace("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", "import com.omegas.prohub.service.v7CalibrationStateJson", "import com.omegas.prohub.service.blueCalibrationStateJson")
replace("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", "service.v7CalibrationStateJson()", "service.blueCalibrationStateJson()")
replace("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", "Estado V7 indisponível", "Estado Blue indisponível")
replace("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", ".put(\"predictor\", calibration.optJSONObject(\"predictor\") ?: JSONObject())", ".put(\"evidenceProjection\", calibration.optJSONObject(\"evidenceProjection\") ?: JSONObject())")
regex_replace("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", r'\n\s*File\(root, "v7_sessions"\),', "")

# Auto-Cal reads the real Blue proposal instead of returning a placeholder.
replace("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt", "service.importNativeAutoCalSnapshot(snapshotJson)", "service.importNativeAutoCalSnapshot(snapshotJson).also { service.blueIngestLearningSnapshot(snapshotJson) }")
replace("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt", "import com.omegas.prohub.service.TelemetryForegroundService", "import com.omegas.prohub.service.TelemetryForegroundService\nimport com.omegas.prohub.service.blueProposalJson")
regex_replace(
    "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt",
    r'\n    /\*\*\n     \* Compatibilidade de API:.*?fun getResidualAnalysis\(\): String = blueAuthorityStatus\(\)\n',
    '\n    @JavascriptInterface\n    fun getAnalysis(): String = activityRef.get()?.serviceOrNull()?.blueProposalJson() ?: unavailable()\n\n    @JavascriptInterface\n    fun getResidualAnalysis(): String = getAnalysis()\n',
    flags=re.S,
)
regex_replace(
    "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt",
    r'\n    /\*\* Legacy draft API.*?fun clearDraft\(\): String = blueAuthorityStatus\(\)\n',
    "\n",
    flags=re.S,
)
regex_replace(
    "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt",
    r'\n    private fun blueAuthorityStatus\(\): String = JSONObject\(\).*?\.toString\(\)\n',
    "\n",
    flags=re.S,
)
replace("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt", '.put("legacyDraftEngine", false)\n', '')
replace("app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt", '.put("legacyMath", false)\n', '')

# -----------------------------------------------------------------------------
# 7. Reduce learning store to evidence storage: no second decision engine.
# -----------------------------------------------------------------------------
signal = path("app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt")
if signal.is_file():
    text = signal.read_text(encoding="utf-8")
    text = text.replace("import java.util.concurrent.Executors\n", "")
    text = text.replace("import java.util.concurrent.atomic.AtomicBoolean\n", "")
    text = text.replace("import java.util.concurrent.atomic.AtomicLong\n", "")
    text = text.replace("Fachada única V6 sobre a memória persistida.", "Armazenamento único de evidência física persistida.")
    text = re.sub(
        r'companion object \{.*?\n    \}',
        'companion object {\n        const val FORMAT = "omegas-learning-blue-mp48"\n        const val DATA_REVISION = 1\n        const val EVIDENCE_STATE_SCHEMA = "omegas-learning-evidence-blue"\n    }',
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace('File(stateFile.parentFile ?: stateFile.absoluteFile.parentFile, "learning_v6_evidence.json")', 'File(stateFile.parentFile ?: stateFile.absoluteFile.parentFile, "learning_evidence.json")')
    text = re.sub(
        r'\n    private val advisorExecutor =.*?@Volatile private var advisor = analyzeCurrentMemory\(\)\n',
        '\n',
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace("        requestAdvisorRefresh(result)\n", "")
    text = text.replace("return decorate(result, includeAdvisor = false)", "return decorate(result, includeDetails = false)")
    text = re.sub(r'\n\s*\.put\("signalDrivenV5", true\)', '', text)
    text = re.sub(r'\n\s*\.put\("assistedCalibration", advisor\)', '', text)
    text = re.sub(r'\n\s*\.put\("advisorRevision", advisorRequestedRevision\.get\(\)\)', '', text)
    text = re.sub(r'\n\s*\.put\("advisorPublishedRevision", advisorPublishedRevision\.get\(\)\)', '', text)
    text = re.sub(r'\n\s*\.put\("advisorFresh", advisorPublishedRevision\.get\(\) >= advisorRequestedRevision\.get\(\)\)', '', text)
    text = re.sub(r'\n\s*\.put\("adaptiveConfidence", adaptiveConfidenceJson\(\)\)', '', text)
    text = re.sub(
        r'if \(incomingFormat !in setOf\(FORMAT, LEGACY_FORMAT, LEGACY_FORMAT_OLD\) \|\|\n\s*payload\.optString\("telemetryScaleSchema"\) != Mp48Protocol\.TELEMETRY_SCALE_SCHEMA \|\|\n\s*incomingRevision !in setOf\(DATA_REVISION, LEGACY_DATA_REVISION\)\n\s*\)',
        'if (incomingFormat != FORMAT ||\n            payload.optString("telemetryScaleSchema") != Mp48Protocol.TELEMETRY_SCALE_SCHEMA ||\n            incomingRevision != DATA_REVISION\n        )',
        text,
    )
    text = re.sub(r'\n\s*scheduleAdvisorRefresh\(advisorRevisionGate\.force\(\)\)', '', text)
    text = re.sub(
        r'\n    private fun adaptiveConfidenceJson\(\): JSONObject \{.*?\n    private fun requestAdvisorRefresh\(result: JSONObject\) \{.*?\n    fun close\(\) \{',
        '\n    fun close() {',
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace("        advisorExecutor.shutdownNow()\n", "")
    text = re.sub(
        r'\n    private fun analyzeCurrentMemory\(\): JSONObject = try \{.*?\n    private fun decorate\(source: JSONObject, includeAdvisor: Boolean = true\): JSONObject \{',
        '\n    private fun decorate(source: JSONObject, includeDetails: Boolean = true): JSONObject {',
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace('.put("signal_driven_v5", true)\n', '')
    text = text.replace("if (includeAdvisor) {", "if (includeDetails) {")
    text = re.sub(
        r'\n\s*\.put\("assisted_calibration", advisor\)\n\s*\.put\("advisor_revision", advisorRequestedRevision\.get\(\)\)\n\s*\.put\("advisor_published_revision", advisorPublishedRevision\.get\(\)\)\n\s*\.put\("advisor_fresh", advisorPublishedRevision\.get\(\) >= advisorRequestedRevision\.get\(\)\)',
        '',
        text,
    )
    text = text.replace("if (includeAdvisor) decision.toJson() else decision.toTelemetryJson()", "if (includeDetails) decision.toJson() else decision.toTelemetryJson()")
    signal.write_text(text, encoding="utf-8")

# -----------------------------------------------------------------------------
# 8. Delete competing decision/session architecture. Physical writers stay.
# -----------------------------------------------------------------------------
for rel in [
    "app/src/main/java/com/omegas/v7",
    "app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt",
    "app/src/main/java/com/omegas/prohub/calibration/CalibrationWriterReadBackV7.kt",
    "app/src/main/java/com/omegas/prohub/calibration/ExistingCalibrationWriterV7.kt",
    "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt",
    "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt",
    "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt",
    "app/src/main/java/com/omegas/prohub/blue/BluePredictorProjection.kt",
    "app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt",
    "app/src/main/java/com/omegas/prohub/learning/AdvisorRevisionGate.kt",
    "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceQualityCompatibility.kt",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt",
]:
    delete(rel)

# Old decision tests encode behavior that no longer exists. Keep physical/protocol tests.
old_test_pattern = re.compile(r"V7|Advisor|Predictor|AutoMatch|legacy|legado", re.I)
for base in [path("app/src/test/java"), path("tests")]:
    if not base.exists():
        continue
    for target in list(base.rglob("*")):
        if not target.is_file() or target.suffix not in {".kt", ".py", ".cjs"}:
            continue
        if target.name == "test_blue_source_architecture_contract.py":
            continue
        try:
            content = target.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if old_test_pattern.search(target.name) or old_test_pattern.search(content):
            target.unlink()

# Remove old UI decision surface. Learn + Auto-Cal are the Blue consumers.
for rel in [
    "app/src/main/assets/ui/components/predictor-current-cell.js",
    "app/src/main/assets/ui/core/predictor-model.js",
    "app/src/main/assets/ui/screens/predictor.js",
    "app/src/main/assets/ui/styles-predictor.css",
    "app/src/main/assets/ui/styles-predictor-live.css",
]:
    delete(rel)

# Strip stale script/style references and route registration if present.
for rel in ["app/src/main/assets/ui/index.html", "app/src/main/assets/ui/app.js"]:
    target = path(rel)
    if not target.is_file():
        continue
    text = target.read_text(encoding="utf-8")
    text = re.sub(r'^.*predictor.*(?:\.js|\.css).*$\n?', '', text, flags=re.I | re.M)
    text = re.sub(r'^\s*predictor:\s*\[[^\n]*\],\s*$\n?', '', text, flags=re.I | re.M)
    target.write_text(text, encoding="utf-8")

# The migration inventory is deliberately transient.
delete("blue-migration-inventory.txt")

print("BLUE_HARD_CUT_APPLIED")
