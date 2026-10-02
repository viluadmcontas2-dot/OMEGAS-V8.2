package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.io.File

/**
 * Separa definitivamente evidências calculadas com a escala MP48 antiga.
 *
 * Mapa K, backups e histórico de escrita ficam fora deste arquivo e não são
 * tocados. A memória anterior é colocada em quarentena para auditoria, nunca
 * convertida em conclusões novas.
 */
object LearningTelemetrySchemaMigration {
    const val ACTIVE_STATE_FILE = "native_learning_state_mp48_v4.json"
    const val LEGACY_ACTIVE_STATE_FILE = "native_learning_state_mp48_v3.json"
    const val LEGACY_ACTIVE_STATE_FILE_OLD = "native_learning_state_mp48_v2.json"
    const val LEGACY_STATE_FILE = "native_learning_state.json"
    const val NOTICE_FILE = "learning_scale_reset_mp48_v4.json"

    fun prepare(runtimeRoot: File, log: RingLog): JSONObject {
        runtimeRoot.mkdirs()
        val active = File(runtimeRoot, ACTIVE_STATE_FILE)
        val notice = File(runtimeRoot, NOTICE_FILE)
        if (active.isFile) return readNotice(notice).put("activeState", active.name)

        val legacyActive = listOf(
            File(runtimeRoot, LEGACY_ACTIVE_STATE_FILE),
            File(runtimeRoot, LEGACY_ACTIVE_STATE_FILE_OLD),
        ).firstOrNull { it.isFile }
        if (legacyActive != null) {
            val quarantine = File(runtimeRoot, "learning_quarantine").apply { mkdirs() }
            val target = File(quarantine, "${legacyActive.nameWithoutExtension}_pre_v3_${System.currentTimeMillis()}.json")
            legacyActive.copyTo(target, overwrite = true)
            legacyActive.delete()
            val migrated = JSONObject()
                .put("migrated", true)
                .put("reason", "Memoria anterior isolada para revisao V6; nenhuma evidencia foi reinterpretada")
                .put("activeState", active.name)
                .put("quarantinedFiles", org.json.JSONArray(listOf(target.name)))
                .put("mapAndWriteHistoryPreserved", true)
            notice.writeText(migrated.toString(2), Charsets.UTF_8)
            return migrated
        }

        val legacy = File(runtimeRoot, LEGACY_STATE_FILE)
        val legacyBackup = File(runtimeRoot, "$LEGACY_STATE_FILE.bak")
        if (!legacy.isFile && !legacyBackup.isFile) {
            return JSONObject()
                .put("migrated", false)
                .put("reason", "Nenhuma memória antiga encontrada")
                .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
                .put("activeState", active.name)
        }

        val quarantine = File(runtimeRoot, "learning_quarantine").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val moved = mutableListOf<String>()
        listOf(legacy, legacyBackup).filter { it.isFile }.forEach { source ->
            val target = File(quarantine, "${source.nameWithoutExtension}_pre_mp48_v2_$stamp.json")
            if (source.renameTo(target)) {
                moved += target.name
            } else {
                source.copyTo(target, overwrite = true)
                source.delete()
                moved += target.name
            }
        }

        val result = JSONObject()
            .put("migrated", true)
            .put("reason", "Escala de telemetria corrigida; evidências anteriores foram isoladas")
            .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
            .put("activeState", active.name)
            .put("quarantinedFiles", org.json.JSONArray(moved))
            .put("createdAt", stamp)
            .put("mapAndWriteHistoryPreserved", true)
        notice.writeText(result.toString(2), Charsets.UTF_8)
        log.add(
            "WARN",
            "LEARNING-SCHEMA",
            "Aprendizado anterior isolado por mudança de escala MP48; mapa K e histórico preservados",
        )
        return result
    }

    fun status(runtimeRoot: File): JSONObject = readNotice(File(runtimeRoot, NOTICE_FILE))
        .put("telemetryScaleSchema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
        .put("activeState", ACTIVE_STATE_FILE)

    private fun readNotice(file: File): JSONObject = try {
        if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject().put("migrated", false)
    } catch (_: Exception) {
        JSONObject().put("migrated", false).put("noticeCorrupt", true)
    }
}
