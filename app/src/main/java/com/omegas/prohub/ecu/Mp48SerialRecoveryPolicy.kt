package com.omegas.prohub.ecu

import org.json.JSONObject

/**
 * Transport-only recovery policy for the MP48 serial session.
 * These thresholds are deliberately not part of the learning tolerance model.
 */
object Mp48SerialRecoveryPolicy {
    const val toleratedFailures: Int = 3
    const val hardRecoveryFailures: Int = 10
    const val hardRecoverySilenceMs: Long = 1_800L

    fun toJson(): JSONObject = JSONObject()
        .put("toleratedFailures", toleratedFailures)
        .put("hardRecoveryFailures", hardRecoveryFailures)
        .put("hardRecoverySilenceMs", hardRecoverySilenceMs)
        .put("ownerConfigurable", false)
        .put("scope", "MP48_TRANSPORT_RECOVERY")
}
