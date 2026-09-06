package com.omegas.prohub.obd

/** Scientific fuel label supplied by the MP48 telemetry stream. */
enum class ObdScientificFuel { PETROL, CNG }

/**
 * Single fuel-state gate for OBD/MP48 scientific pairing.
 *
 * MP48 transition is still gasoline: the engine is consuming gasoline until the
 * gas state is actually active. Cut-off and unknown states are deliberately not
 * scientific fuel labels and therefore return null.
 */
object ObdFuelState {
    fun normalize(raw: String): ObdScientificFuel? = when (raw.trim().uppercase()) {
        "PETROL", "GASOLINA", "TRANSITION", "TRANSICAO", "TRANSIÇÃO" -> ObdScientificFuel.PETROL
        "CNG", "GNV", "GAS" -> ObdScientificFuel.CNG
        "CUT_OFF", "CUT-OFF", "CUTOFF" -> null
        else -> null
    }
}
