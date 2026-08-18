package com.omegas.prohub.learning

import org.json.JSONObject
import java.io.File

enum class LearningRestoreState {
    EMPTY,
    CURRENT_COMPATIBLE,
    LEGACY_OBSERVATIONAL,
    INCOMPATIBLE,
    CORRUPT,
}

enum class LearningIdentityRestoreState {
    CURRENT_KNOWN_GEOMETRY,
    IDENTITY_ONLY,
    MISSING,
    INVALID,
}

/**
 * Diagnóstico explícito do que existe no disco antes de expor memória restaurada.
 * Não converte ciência antiga e não impede a telemetria de iniciar.
 */
internal object LearningRestoreValidator {
    const val CALIBRATION_BINDING_FILE = "learning_calibration_binding_v1.json"

    fun validate(runtimeRoot: File, activeState: File): JSONObject {
        val state = inspectState(activeState)
        val identity = inspectIdentity(File(runtimeRoot, CALIBRATION_BINDING_FILE))
        val historicalOnly = state == LearningRestoreState.LEGACY_OBSERVATIONAL ||
            identity == LearningIdentityRestoreState.MISSING ||
            identity == LearningIdentityRestoreState.INVALID ||
            identity == LearningIdentityRestoreState.IDENTITY_ONLY
        return JSONObject()
            .put("state", state.name)
            .put("identity_state", identity.name)
            .put("telemetry_allowed", true)
            .put("petrol_history_observational", state != LearningRestoreState.CORRUPT && state != LearningRestoreState.INCOMPATIBLE)
            .put("restored_cng_actionable", false)
            .put("legacy_observational", historicalOnly)
            .put("requires_live_calibration_identity_for_cng", true)
            .put("active_state_file", activeState.name)
            .put("calibration_binding_file", CALIBRATION_BINDING_FILE)
    }

    private fun inspectState(file: File): LearningRestoreState {
        if (!file.isFile) return LearningRestoreState.EMPTY
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optString("format") != MotorLearningMemory.FORMAT) {
                LearningRestoreState.INCOMPATIBLE
            } else if (root.optString("stateDigest").isBlank()) {
                LearningRestoreState.LEGACY_OBSERVATIONAL
            } else {
                LearningRestoreState.CURRENT_COMPATIBLE
            }
        } catch (_: Exception) {
            LearningRestoreState.CORRUPT
        }
    }

    private fun inspectIdentity(file: File): LearningIdentityRestoreState {
        if (!file.isFile) return LearningIdentityRestoreState.MISSING
        return try {
            val binding = LearningCalibrationBinding.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
                ?: return LearningIdentityRestoreState.INVALID
            if (binding.geometryKnown()) {
                LearningIdentityRestoreState.CURRENT_KNOWN_GEOMETRY
            } else {
                LearningIdentityRestoreState.IDENTITY_ONLY
            }
        } catch (_: Exception) {
            LearningIdentityRestoreState.INVALID
        }
    }
}
