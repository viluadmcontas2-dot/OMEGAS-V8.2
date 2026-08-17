package com.omegas.prohub.calibration

enum class CalibrationProvenance {
    FULL_ECU_READ,
    POST_WRITE_READBACK,
    AUTOCAL_RECONCILE,
    RECOVERY_READ,
    RESTORED_HISTORY,
    UNKNOWN,
}

enum class CalibrationCompleteness {
    KNOWN,
    PARTIAL,
    UNKNOWN,
}

enum class CalibrationFreshness {
    CURRENT_SESSION,
    STALE,
    UNKNOWN,
}

object CalibrationIdentityStateResolver {
    fun completeness(
        usbSessionId: Long?,
        generation: Int?,
        functionFingerprint: String,
        geometryFingerprint: String,
        mapHash: String,
        curveAxisFingerprint: String,
        curveFactorsFingerprint: String,
    ): CalibrationCompleteness {
        val present = listOf(
            usbSessionId != null && usbSessionId > 0L,
            generation != null && generation >= 0,
            functionFingerprint.isNotBlank(),
            geometryFingerprint.isNotBlank(),
            mapHash.isNotBlank(),
            curveAxisFingerprint.isNotBlank(),
            curveFactorsFingerprint.isNotBlank(),
        )
        return when {
            present.all { it } -> CalibrationCompleteness.KNOWN
            present.any { it } -> CalibrationCompleteness.PARTIAL
            else -> CalibrationCompleteness.UNKNOWN
        }
    }

    fun materiallyUsable(
        completeness: CalibrationCompleteness,
        freshness: CalibrationFreshness,
        provenance: CalibrationProvenance,
    ): Boolean = completeness == CalibrationCompleteness.KNOWN &&
        freshness == CalibrationFreshness.CURRENT_SESSION &&
        provenance in setOf(
            CalibrationProvenance.FULL_ECU_READ,
            CalibrationProvenance.POST_WRITE_READBACK,
            CalibrationProvenance.AUTOCAL_RECONCILE,
            CalibrationProvenance.RECOVERY_READ,
        )
}

data class EffectiveCalibrationState(
    val mapRow: Int,
    val mapColumn: Int,
    val curveIndex: Int,
    val mapRaw: Int,
    val curveRaw: Int,
    val mapEffective: Double,
    val curveEffective: Double,
    val currentEffective: Double,
    val provenance: CalibrationProvenance,
)

/** Identidade material da calibração física vigente, separada das revisions locais. */
class CalibrationIdentity private constructor(
    val functionFingerprint: String,
    val geometryFingerprint: String,
    val mapHash: String,
    val curveAxisFingerprint: String,
    val curveFactorsFingerprint: String,
    val usbSessionId: Long,
    val generation: Int,
    val provenance: CalibrationProvenance,
    val completeness: CalibrationCompleteness,
    val freshness: CalibrationFreshness,
    val capturedAtMs: Long,
    val mapRevision: Long?,
    val curveRevision: Long?,
    private val mapRowsRaw: List<List<Int>>?,
    private val curveFactorsRaw: List<Int>?,
    val schema: String,
) {
    fun materiallyUsable(): Boolean = CalibrationIdentityStateResolver.materiallyUsable(
        completeness = completeness,
        freshness = freshness,
        provenance = provenance,
    )

    fun effectiveState(mapRow: Int, mapColumn: Int, curveIndex: Int): EffectiveCalibrationState {
        require(materiallyUsable()) { "CalibrationIdentity não está materialmente utilizável" }
        val map = requireNotNull(mapRowsRaw) { "Mapa K indisponível" }
        val curve = requireNotNull(curveFactorsRaw) { "MUL_ACT indisponível" }
        require(mapRow in 0 until 12) { "Linha editável Map K inválida: $mapRow" }
        require(mapColumn in 0 until 12) { "Coluna Map K inválida: $mapColumn" }
        require(curveIndex in 0 until 30) { "Índice Curva K inválido: $curveIndex" }
        val mapRaw = map[mapRow][mapColumn]
        val curveRaw = curve[curveIndex]
        val mapEffective = mapRaw / 128.0
        val curveEffective = curveRaw / 16384.0
        return EffectiveCalibrationState(
            mapRow = mapRow,
            mapColumn = mapColumn,
            curveIndex = curveIndex,
            mapRaw = mapRaw,
            curveRaw = curveRaw,
            mapEffective = mapEffective,
            curveEffective = curveEffective,
            currentEffective = mapEffective * curveEffective,
            provenance = provenance,
        )
    }

    fun toSerializableMap(): Map<String, Any?> = linkedMapOf(
        "schema" to schema,
        "functionFingerprint" to functionFingerprint,
        "geometryFingerprint" to geometryFingerprint,
        "mapHash" to mapHash,
        "curveAxisFingerprint" to curveAxisFingerprint,
        "curveFactorsFingerprint" to curveFactorsFingerprint,
        "usbSessionId" to usbSessionId,
        "generation" to generation,
        "provenance" to provenance.name,
        "completeness" to completeness.name,
        "freshness" to freshness.name,
        "capturedAtMs" to capturedAtMs,
        "mapRevision" to mapRevision,
        "curveRevision" to curveRevision,
    )

    companion object {
        const val SCHEMA = "mp48-calibration-identity-v1"

        fun fromComposite(
            composite: CompositeCalibrationSnapshot,
            capturedAtMs: Long,
            mapRevision: Long?,
            curveRevision: Long?,
            provenance: CalibrationProvenance = CalibrationProvenance.FULL_ECU_READ,
        ): CalibrationIdentity {
            require(capturedAtMs >= 0L) { "capturedAtMs inválido" }
            require(provenance in setOf(
                CalibrationProvenance.FULL_ECU_READ,
                CalibrationProvenance.POST_WRITE_READBACK,
                CalibrationProvenance.AUTOCAL_RECONCILE,
                CalibrationProvenance.RECOVERY_READ,
            )) { "Snapshot físico KNOWN exige provenance de leitura/reconciliação" }
            return CalibrationIdentity(
                functionFingerprint = CalibrationFunctionFingerprint.from(composite),
                geometryFingerprint = composite.mapGeometry.fingerprint(),
                mapHash = composite.mapHash,
                curveAxisFingerprint = composite.curve.axisFingerprint(),
                curveFactorsFingerprint = composite.curve.factorsFingerprint(),
                usbSessionId = composite.usbSessionId,
                generation = composite.autoMatchCount,
                provenance = provenance,
                completeness = CalibrationCompleteness.KNOWN,
                freshness = CalibrationFreshness.CURRENT_SESSION,
                capturedAtMs = capturedAtMs,
                mapRevision = mapRevision,
                curveRevision = curveRevision,
                mapRowsRaw = composite.mapRowsRaw.map { it.toList() },
                curveFactorsRaw = composite.curve.factorsRaw.toList(),
                schema = SCHEMA,
            )
        }

        fun observational(
            usbSessionId: Long?,
            generation: Int?,
            provenance: CalibrationProvenance,
            freshness: CalibrationFreshness,
            capturedAtMs: Long,
            functionFingerprint: String = "",
            geometryFingerprint: String = "",
            mapHash: String = "",
            curveAxisFingerprint: String = "",
            curveFactorsFingerprint: String = "",
            mapRevision: Long? = null,
            curveRevision: Long? = null,
        ): CalibrationIdentity {
            require(capturedAtMs >= 0L) { "capturedAtMs inválido" }
            val completeness = CalibrationIdentityStateResolver.completeness(
                usbSessionId = usbSessionId,
                generation = generation,
                functionFingerprint = functionFingerprint,
                geometryFingerprint = geometryFingerprint,
                mapHash = mapHash,
                curveAxisFingerprint = curveAxisFingerprint,
                curveFactorsFingerprint = curveFactorsFingerprint,
            )
            return CalibrationIdentity(
                functionFingerprint = functionFingerprint,
                geometryFingerprint = geometryFingerprint,
                mapHash = mapHash,
                curveAxisFingerprint = curveAxisFingerprint,
                curveFactorsFingerprint = curveFactorsFingerprint,
                usbSessionId = usbSessionId ?: 0L,
                generation = generation ?: -1,
                provenance = provenance,
                completeness = completeness,
                freshness = freshness,
                capturedAtMs = capturedAtMs,
                mapRevision = mapRevision,
                curveRevision = curveRevision,
                mapRowsRaw = null,
                curveFactorsRaw = null,
                schema = SCHEMA,
            )
        }
    }
}
