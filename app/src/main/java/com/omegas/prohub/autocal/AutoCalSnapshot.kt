package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class AutoCalFieldStatus {
    VALID,
    UNAVAILABLE,
    INVALID,
}

enum class AutoCalSnapshotSource {
    ECU_READ,
    REPLAY,
}

data class AutoCalReadObservation(
    val field: AutoCalProtocol.Field,
    val status: Int? = null,
    val payload: ByteArray? = null,
    val capturedAtMs: Long,
    val error: String? = null,
)

data class AutoCalFieldValue(
    val key: String,
    val address: Int,
    val index: Int? = null,
    val rawPayloadHex: String,
    val rawValues: IntArray,
    val physicalValues: DoubleArray,
    val elementCount: Int,
    val status: AutoCalFieldStatus,
    val capturedAtMs: Long,
    val error: String? = null,
) {
    val identity: String
        get() = "%04X:%s".format(address, index?.let { "%02X".format(it) } ?: "--")

    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("identity", identity)
        .put("address", address)
        .put("addressHex", "0x%04X".format(address))
        .put("index", index ?: JSONObject.NULL)
        .put("rawPayloadHex", rawPayloadHex)
        .put("rawValues", JSONArray(rawValues.toList()))
        .put("physicalValues", JSONArray(physicalValues.toList()))
        .put("elementCount", elementCount)
        .put("status", status.name)
        .put("capturedAtMs", capturedAtMs)
        .put("error", error ?: JSONObject.NULL)
}

data class AutoCalCoherenceGroup(
    val key: String,
    val spanMs: Long,
    val limitMs: Long,
    val coherent: Boolean,
    val fieldKeys: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("spanMs", spanMs)
        .put("limitMs", limitMs)
        .put("coherent", coherent)
        .put("fieldKeys", JSONArray(fieldKeys))
}

data class AutoCalSnapshot(
    val sessionId: String,
    val capturedAtMs: Long,
    val source: AutoCalSnapshotSource,
    val fields: Map<Int, AutoCalFieldValue>,
    val snapshotHash: String,
    val durationMs: Long,
    val validFieldSpanMs: Long,
    val temporalCoherent: Boolean,
    val coherenceGroups: List<AutoCalCoherenceGroup>,
    val warnings: List<String>,
) {
    val validFieldCount: Int get() = fields.values.count { it.status == AutoCalFieldStatus.VALID }
    val partial: Boolean get() = fields.values.any { it.status != AutoCalFieldStatus.VALID }
    val moduleVersion: Int?
        get() = field(AutoCalProtocol.MODULE_VERSION)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
            ?.singleOrNull()

    fun field(descriptor: AutoCalProtocol.Field): AutoCalFieldValue? = fields[storageKey(descriptor)]

    fun toJson(): JSONObject = JSONObject()
        .put("sessionId", sessionId)
        .put("capturedAtMs", capturedAtMs)
        .put("source", source.name)
        .put("snapshotHash", snapshotHash)
        .put("moduleVersion", moduleVersion ?: JSONObject.NULL)
        .put("durationMs", durationMs)
        .put("validFieldSpanMs", validFieldSpanMs)
        .put("maximumFieldSkewMs", AutoCalSnapshotBuilder.MAX_FIELD_SKEW_MS)
        .put("temporalCoherent", temporalCoherent)
        .put("coherenceGroups", JSONArray(coherenceGroups.map { it.toJson() }))
        .put("partial", partial)
        .put("validFieldCount", validFieldCount)
        .put("fieldCount", fields.size)
        .put("warnings", JSONArray(warnings))
        .put("automatic", false)
        .put("manualOnly", true)
        .put("fields", JSONArray(fields.toSortedMap().values.map { it.toJson() }))

    companion object {
        internal fun storageKey(field: AutoCalProtocol.Field): Int =
            if (field.index == null) {
                field.address
            } else {
                0x01000000 or (field.address shl 8) or field.index
            }
    }
}

object AutoCalSnapshotBuilder {
    /**
     * Limite global somente diagnóstico. A decisão de uso é tomada pelos grupos
     * de coerência, não pelo tempo total das transações seriais.
     */
    const val MAX_FIELD_SKEW_MS = 2_500L
    const val MAX_AUTOMATCH_GROUP_SKEW_MS = 2_000L
    const val MAX_ACQUISITION_GROUP_SKEW_MS = 2_500L

    private data class GroupDefinition(
        val key: String,
        val fields: List<AutoCalProtocol.Field>,
        val limitMs: Long,
    )

    private val groups = listOf(
        GroupDefinition(
            key = "AUTOMATCH_CURVES",
            fields = listOf(
                AutoCalProtocol.PETR_INJ_TBP,
                AutoCalProtocol.MNFLD_PRESS_THD,
                AutoCalProtocol.MUL_ACT,
                AutoCalProtocol.PETR_MNFLD_PRESS_RV,
                AutoCalProtocol.GAS_MNFLD_PRESS_RV,
            ),
            limitMs = MAX_AUTOMATCH_GROUP_SKEW_MS,
        ),
        GroupDefinition(
            key = "ACQUISITION_CURRENT",
            fields = listOf(
                AutoCalProtocol.NUM_BUF_UPD_PETR,
                AutoCalProtocol.NUM_BUF_UPD_GAS,
                AutoCalProtocol.VECT_AUTOCAL_U8_1,
                AutoCalProtocol.VECT_AUTOCAL_U8_2,
                AutoCalProtocol.PETR_INJ_TBUF_GAS,
                AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
                AutoCalProtocol.PETR_INJ_TBUF,
                AutoCalProtocol.MNFLD_PRESS_BUF,
            ),
            limitMs = MAX_ACQUISITION_GROUP_SKEW_MS,
        ),
    )

    fun build(
        observations: List<AutoCalReadObservation>,
        expectedFields: List<AutoCalProtocol.Field> = AutoCalProtocol.READ_ONLY_FIELDS,
        sessionId: String = UUID.randomUUID().toString(),
        source: AutoCalSnapshotSource = AutoCalSnapshotSource.ECU_READ,
        startedAtMs: Long = observations.minOfOrNull { it.capturedAtMs } ?: System.currentTimeMillis(),
        finishedAtMs: Long = observations.maxOfOrNull { it.capturedAtMs } ?: startedAtMs,
    ): AutoCalSnapshot {
        val latestByIdentity = observations.groupBy { it.field.identity }
            .mapValues { (_, values) -> values.maxByOrNull { it.capturedAtMs }!! }
        val fields = linkedMapOf<Int, AutoCalFieldValue>()
        val warnings = mutableListOf<String>()
        val moduleVersion = decodeModuleVersion(latestByIdentity[AutoCalProtocol.MODULE_VERSION.identity])
        val hasModuleSizedField = expectedFields.any {
            AutoCalProtocol.expectedElements(it, null) == null && it.expectedElementsHint != null
        }
        if (moduleVersion == null && hasModuleSizedField) {
            warnings += "MODULE_VERSION indisponível; vetores dinâmicos foram decodificados sem promover forma 18/30"
        }

        expectedFields.distinctBy { it.identity }
            .sortedWith(compareBy<AutoCalProtocol.Field> { it.address }.thenBy { it.index ?: -1 })
            .forEach { descriptor ->
                val observation = latestByIdentity[descriptor.identity]
                val value = when {
                    observation == null -> unavailable(descriptor, finishedAtMs, "Campo não retornado")
                    observation.error != null -> unavailable(descriptor, observation.capturedAtMs, observation.error)
                    observation.status == null || observation.payload == null -> unavailable(
                        descriptor,
                        observation.capturedAtMs,
                        "Resposta incompleta",
                    )
                    else -> try {
                        val decoded = AutoCalProtocol.decode(descriptor, observation.status, observation.payload)
                        AutoCalProtocol.requireExpectedShape(decoded, moduleVersion)
                        AutoCalFieldValue(
                            key = descriptor.key,
                            address = descriptor.address,
                            index = descriptor.index,
                            rawPayloadHex = decoded.rawPayload.hex(),
                            rawValues = decoded.rawValues,
                            physicalValues = decoded.physicalValues,
                            elementCount = decoded.elementCount,
                            status = AutoCalFieldStatus.VALID,
                            capturedAtMs = observation.capturedAtMs,
                        )
                    } catch (error: Exception) {
                        AutoCalFieldValue(
                            key = descriptor.key,
                            address = descriptor.address,
                            index = descriptor.index,
                            rawPayloadHex = observation.payload.hex(),
                            rawValues = intArrayOf(),
                            physicalValues = doubleArrayOf(),
                            elementCount = 0,
                            status = AutoCalFieldStatus.INVALID,
                            capturedAtMs = observation.capturedAtMs,
                            error = error.message ?: "Resposta inválida",
                        )
                    }
                }
                fields[AutoCalSnapshot.storageKey(descriptor)] = value
                if (value.status != AutoCalFieldStatus.VALID) {
                    warnings += "${descriptor.key}: ${value.error ?: value.status.name}"
                }
            }

        val validTimes = fields.values
            .filter { it.status == AutoCalFieldStatus.VALID }
            .map { it.capturedAtMs }
        val validFieldSpanMs = (validTimes.maxOrNull() ?: finishedAtMs) - (validTimes.minOrNull() ?: finishedAtMs)

        val coherenceGroups = groups.mapNotNull { definition ->
            val values = definition.fields.mapNotNull { descriptor ->
                fields[AutoCalSnapshot.storageKey(descriptor)]
                    ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            }
            if (values.isEmpty()) return@mapNotNull null
            val span = (values.maxOfOrNull { it.capturedAtMs } ?: 0L) -
                (values.minOfOrNull { it.capturedAtMs } ?: 0L)
            AutoCalCoherenceGroup(
                key = definition.key,
                spanMs = span,
                limitMs = definition.limitMs,
                coherent = values.size == definition.fields.size && span <= definition.limitMs,
                fieldKeys = values.map { it.key },
            )
        }
        val temporalCoherent = coherenceGroups.all { it.coherent }
        coherenceGroups.filterNot { it.coherent }.forEach { group ->
            warnings += "Grupo ${group.key} lido em ${group.spanMs} ms; limite ${group.limitMs} ms"
        }
        if (validFieldSpanMs > MAX_FIELD_SKEW_MS) {
            warnings += "Snapshot completo levou ${validFieldSpanMs} ms; grupos críticos foram avaliados separadamente"
        }

        return AutoCalSnapshot(
            sessionId = sessionId,
            capturedAtMs = finishedAtMs,
            source = source,
            fields = fields,
            snapshotHash = hash(fields),
            durationMs = (finishedAtMs - startedAtMs).coerceAtLeast(0L),
            validFieldSpanMs = validFieldSpanMs,
            temporalCoherent = temporalCoherent,
            coherenceGroups = coherenceGroups,
            warnings = warnings,
        )
    }

    private fun decodeModuleVersion(observation: AutoCalReadObservation?): Int? {
        if (observation?.error != null || observation?.status == null || observation.payload == null) return null
        return try {
            AutoCalProtocol.decode(
                AutoCalProtocol.MODULE_VERSION,
                observation.status,
                observation.payload,
            ).rawValues.singleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun unavailable(field: AutoCalProtocol.Field, at: Long, error: String) = AutoCalFieldValue(
        key = field.key,
        address = field.address,
        index = field.index,
        rawPayloadHex = "",
        rawValues = intArrayOf(),
        physicalValues = doubleArrayOf(),
        elementCount = 0,
        status = AutoCalFieldStatus.UNAVAILABLE,
        capturedAtMs = at,
        error = error,
    )

    private fun hash(fields: Map<Int, AutoCalFieldValue>): String {
        val canonical = fields.toSortedMap().values.joinToString("|") { field ->
            "%s:%s:%s".format(field.identity, field.status.name, field.rawPayloadHex)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ByteArray?.hex(): String = this?.joinToString("") { "%02X".format(it.toInt() and 0xFF) } ?: ""
}
