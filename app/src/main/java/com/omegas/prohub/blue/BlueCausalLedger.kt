package com.omegas.prohub.blue

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BluePendingIntervention(
    val id: String,
    val actuator: BlueActuatorAddress,
    val beforeRevision: CalibrationRevision,
    val beforeK: Double,
    val targetK: Double,
    val beforeComparisonId: String,
    val scientificRegionId: String,
    val preparedAtMs: Long,
)

data class BlueConfirmedActuatorChange(
    val actuator: BlueActuatorAddress,
    val beforeK: Double,
    val afterK: Double,
)

data class BlueInterventionConfirmation(
    val id: String,
    val afterRevision: CalibrationRevision,
    val ackConfirmed: Boolean,
    val readbackConfirmed: Boolean,
    val changes: List<BlueConfirmedActuatorChange>,
    val confirmedAtMs: Long,
)

enum class BlueLedgerState { PREPARED, CONFIRMED, ABSTAIN }

data class BlueLedgerDecision(
    val state: BlueLedgerState,
    val reason: String,
    val intervention: BlueCausalIntervention? = null,
)

/**
 * Durable hand-off between a human-approved writer and causal attribution.
 * Pending intent and confirmed readback are stored atomically; a failed,
 * partial or multi-actuator completion is consumed as ABSTAIN.
 */
class BlueCausalLedger(private val file: File) {
    private val lock = Any()
    private val pending = linkedMapOf<String, BluePendingIntervention>()
    private val confirmed = linkedMapOf<String, BlueCausalIntervention>()

    init { synchronized(lock) { loadLocked() } }

    fun prepare(value: BluePendingIntervention): BlueLedgerDecision = synchronized(lock) {
        require(value.id.isNotBlank())
        require(value.beforeK.isFinite() && value.beforeK > 0.0)
        require(value.targetK.isFinite() && value.targetK > 0.0)
        require(value.beforeK != value.targetK)
        require(value.beforeComparisonId.isNotBlank())
        require(value.scientificRegionId.isNotBlank())
        require(value.preparedAtMs >= 0L)
        pending[value.id] = value
        confirmed.remove(value.id)
        persistLocked()
        BlueLedgerDecision(BlueLedgerState.PREPARED, "WAITING_FOR_ACK_READBACK")
    }

    fun pending(id: String): BluePendingIntervention? = synchronized(lock) { pending[id] }

    fun confirmed(id: String): BlueCausalIntervention? = synchronized(lock) { confirmed[id] }

    fun latestConfirmed(): BlueCausalIntervention? = synchronized(lock) {
        confirmed.values.maxByOrNull(BlueCausalIntervention::confirmedAtMs)
    }

    fun confirm(value: BlueInterventionConfirmation): BlueLedgerDecision = synchronized(lock) {
        val prepared = pending.remove(value.id)
            ?: return@synchronized BlueLedgerDecision(BlueLedgerState.ABSTAIN, "PENDING_NOT_FOUND")
        val decision = when {
            !value.ackConfirmed || !value.readbackConfirmed ->
                abstain("WRITE_NOT_CONFIRMED")
            value.changes.size != 1 ->
                abstain("INTERVENTION_NOT_ISOLATED")
            value.changes.single().actuator != prepared.actuator ->
                abstain("ACTUATOR_MISMATCH")
            value.changes.single().beforeK != prepared.beforeK ||
                value.changes.single().afterK != prepared.targetK ->
                abstain("READBACK_VALUE_MISMATCH")
            else -> {
                val change = value.changes.single()
                val intervention = BlueCausalIntervention(
                    id = prepared.id,
                    actuator = prepared.actuator,
                    beforeRevision = prepared.beforeRevision,
                    afterRevision = value.afterRevision,
                    beforeK = change.beforeK,
                    afterK = change.afterK,
                    ackConfirmed = true,
                    readbackConfirmed = true,
                    changedActuators = listOf(change.actuator),
                    confirmedAtMs = value.confirmedAtMs,
                )
                confirmed[intervention.id] = intervention
                BlueLedgerDecision(
                    BlueLedgerState.CONFIRMED,
                    "ACK_AND_FULL_READBACK_CONFIRMED",
                    intervention,
                )
            }
        }
        persistLocked()
        decision
    }

    private fun abstain(reason: String) = BlueLedgerDecision(BlueLedgerState.ABSTAIN, reason)

    private fun loadLocked() {
        if (!file.isFile || file.length() == 0L) return
        val root = try { JSONObject(file.readText(Charsets.UTF_8)) } catch (_: Exception) { return }
        val pendingJson = root.optJSONArray("pending") ?: JSONArray()
        repeat(pendingJson.length()) { index ->
            decodePending(pendingJson.optJSONObject(index))?.let { pending[it.id] = it }
        }
        val confirmedJson = root.optJSONArray("confirmed") ?: JSONArray()
        repeat(confirmedJson.length()) { index ->
            decodeIntervention(confirmedJson.optJSONObject(index))?.let { confirmed[it.id] = it }
        }
    }

    private fun persistLocked() {
        file.parentFile?.mkdirs()
        val root = JSONObject()
            .put("schema", "omegas-blue-causal-ledger-v1")
            .put("pending", JSONArray(pending.values.map(::encodePending)))
            .put("confirmed", JSONArray(confirmed.values.map(::encodeIntervention)))
        val temp = File(file.parentFile ?: file.absoluteFile.parentFile, file.name + ".tmp")
        temp.writeText(root.toString(2), Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encodePending(value: BluePendingIntervention) = JSONObject()
        .put("id", value.id)
        .put("actuator", encodeAddress(value.actuator))
        .put("beforeRevision", encodeRevision(value.beforeRevision))
        .put("beforeK", value.beforeK)
        .put("targetK", value.targetK)
        .put("beforeComparisonId", value.beforeComparisonId)
        .put("scientificRegionId", value.scientificRegionId)
        .put("preparedAtMs", value.preparedAtMs)

    private fun decodePending(value: JSONObject?): BluePendingIntervention? = try {
        requireNotNull(value)
        BluePendingIntervention(
            id = value.getString("id"),
            actuator = decodeAddress(value.getJSONObject("actuator")),
            beforeRevision = decodeRevision(value.getJSONObject("beforeRevision")),
            beforeK = value.getDouble("beforeK"),
            targetK = value.getDouble("targetK"),
            beforeComparisonId = value.getString("beforeComparisonId"),
            scientificRegionId = value.getString("scientificRegionId"),
            preparedAtMs = value.getLong("preparedAtMs"),
        )
    } catch (_: Exception) { null }

    private fun encodeIntervention(value: BlueCausalIntervention) = JSONObject()
        .put("id", value.id)
        .put("actuator", encodeAddress(value.actuator))
        .put("beforeRevision", encodeRevision(value.beforeRevision))
        .put("afterRevision", encodeRevision(value.afterRevision))
        .put("beforeK", value.beforeK)
        .put("afterK", value.afterK)
        .put("ackConfirmed", value.ackConfirmed)
        .put("readbackConfirmed", value.readbackConfirmed)
        .put("changedActuators", JSONArray(value.changedActuators.map(::encodeAddress)))
        .put("confirmedAtMs", value.confirmedAtMs)

    private fun decodeIntervention(value: JSONObject?): BlueCausalIntervention? = try {
        requireNotNull(value)
        val addresses = value.getJSONArray("changedActuators")
        BlueCausalIntervention(
            id = value.getString("id"),
            actuator = decodeAddress(value.getJSONObject("actuator")),
            beforeRevision = decodeRevision(value.getJSONObject("beforeRevision")),
            afterRevision = decodeRevision(value.getJSONObject("afterRevision")),
            beforeK = value.getDouble("beforeK"),
            afterK = value.getDouble("afterK"),
            ackConfirmed = value.getBoolean("ackConfirmed"),
            readbackConfirmed = value.getBoolean("readbackConfirmed"),
            changedActuators = List(addresses.length()) { decodeAddress(addresses.getJSONObject(it)) },
            confirmedAtMs = value.getLong("confirmedAtMs"),
        )
    } catch (_: Exception) { null }

    private fun encodeAddress(value: BlueActuatorAddress) = JSONObject()
        .put("kind", value.kind.name)
        .put("index", value.index ?: JSONObject.NULL)
        .put("row", value.row ?: JSONObject.NULL)
        .put("column", value.column ?: JSONObject.NULL)

    private fun decodeAddress(value: JSONObject): BlueActuatorAddress =
        when (BlueActuatorKind.valueOf(value.getString("kind"))) {
            BlueActuatorKind.CURVE_POINT -> BlueActuatorAddress.curvePoint(value.getInt("index"))
            BlueActuatorKind.MAP_CELL -> BlueActuatorAddress.mapCell(
                value.getInt("row"),
                value.getInt("column"),
            )
        }

    private fun encodeRevision(value: CalibrationRevision) = JSONObject()
        .put("curveK", value.curveK)
        .put("mapK", value.mapK)

    private fun decodeRevision(value: JSONObject) = CalibrationRevision(
        curveK = value.getLong("curveK"),
        mapK = value.getLong("mapK"),
    )
}
