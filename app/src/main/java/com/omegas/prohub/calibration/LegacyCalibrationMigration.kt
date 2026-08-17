package com.omegas.prohub.calibration

enum class LegacyCalibrationClassification {
    LEGACY_OBSERVATIONAL,
    REJECTED_INCOMPATIBLE,
    CORRUPT,
}

data class LegacyCalibrationMigrationResult(
    val classification: LegacyCalibrationClassification,
    val identity: CalibrationIdentity,
    val reason: String,
)

/** Migração fail-closed de persistência anterior à CalibrationIdentity NEXT. */
object LegacyCalibrationMigration {
    private val acceptedV7Schemas = setOf(
        "OMEGAS_V7_SESSION_2",
        "OMEGAS_V7_SESSION_3",
        "OMEGAS_V7_SESSION_4",
        "OMEGAS_V7_SESSION_5",
        "OMEGAS_V7_SESSION_6",
    )

    fun fromV7Session(text: String, capturedAtMs: Long): LegacyCalibrationMigrationResult {
        val values = linkedMapOf<String, String>()
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty() || lines.any { '=' !in it }) {
            return result(LegacyCalibrationClassification.CORRUPT, capturedAtMs, reason = "LEGACY_TEXT_CORRUPT")
        }
        lines.forEach { line ->
            val split = line.indexOf('=')
            if (split <= 0) return result(LegacyCalibrationClassification.CORRUPT, capturedAtMs, reason = "LEGACY_LINE_CORRUPT")
            values[line.substring(0, split)] = line.substring(split + 1)
        }
        val schema = values["schema"]
        if (schema !in acceptedV7Schemas) {
            return result(LegacyCalibrationClassification.REJECTED_INCOMPATIBLE, capturedAtMs, reason = "LEGACY_SCHEMA_INCOMPATIBLE")
        }
        val revision = values["revision"]?.split(',')
        val curveRevision = revision?.getOrNull(0)?.toLongOrNull()
        val mapRevision = revision?.getOrNull(1)?.toLongOrNull()
        return result(
            classification = LegacyCalibrationClassification.LEGACY_OBSERVATIONAL,
            capturedAtMs = capturedAtMs,
            reason = "MISSING_MODERN_GEOMETRY_AND_FINGERPRINT",
            curveRevision = curveRevision,
            mapRevision = mapRevision,
        )
    }

    fun fromLegacyCacheKeys(keys: Set<String>, capturedAtMs: Long): LegacyCalibrationMigrationResult {
        val looksLegacy = keys.any { it in setOf("rows", "extraRow", "allRows", "axes", "hash", "sessionId") }
        return if (looksLegacy) {
            result(
                LegacyCalibrationClassification.LEGACY_OBSERVATIONAL,
                capturedAtMs,
                reason = "LEGACY_CACHE_WITHOUT_MODERN_IDENTITY",
            )
        } else {
            result(LegacyCalibrationClassification.CORRUPT, capturedAtMs, reason = "UNRECOGNIZED_CACHE")
        }
    }

    private fun result(
        classification: LegacyCalibrationClassification,
        capturedAtMs: Long,
        reason: String,
        curveRevision: Long? = null,
        mapRevision: Long? = null,
    ): LegacyCalibrationMigrationResult {
        val identity = CalibrationIdentity.observational(
            usbSessionId = null,
            generation = null,
            provenance = CalibrationProvenance.RESTORED_HISTORY,
            freshness = CalibrationFreshness.STALE,
            capturedAtMs = capturedAtMs.coerceAtLeast(0L),
            mapRevision = mapRevision,
            curveRevision = curveRevision,
        )
        return LegacyCalibrationMigrationResult(classification, identity, reason)
    }
}
