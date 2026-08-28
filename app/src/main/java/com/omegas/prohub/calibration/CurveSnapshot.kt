package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class CurveSnapshotProvenance {
    FULL_ECU_READ,
    UNKNOWN,
}

enum class CurveSnapshotCompleteness {
    KNOWN,
    PARTIAL,
    UNKNOWN,
}

/** Snapshot imutável da Curva K/MUL_ACT corrente. */
class CurveSnapshot private constructor(
    val petrolAxisRaw: List<Int>,
    val factorsRaw: List<Int>,
    val petrolAxisMs: List<Double>,
    val factors: List<Double>,
    val usbSessionId: Long,
    val provenance: CurveSnapshotProvenance,
    val completeness: CurveSnapshotCompleteness,
    val schema: String,
) {
    fun axisFingerprint(): String = fingerprint("axis", petrolAxisRaw)

    fun factorsFingerprint(): String = fingerprint("factors", factorsRaw)

    fun toSerializableMap(): Map<String, Any> = linkedMapOf(
        "schema" to schema,
        "usbSessionId" to usbSessionId,
        "provenance" to provenance.name,
        "completeness" to completeness.name,
        "petrolAxisRaw" to petrolAxisRaw,
        "factorsRaw" to factorsRaw,
        "petrolAxisMs" to petrolAxisMs,
        "factors" to factors,
        "axisFingerprint" to axisFingerprint(),
        "factorsFingerprint" to factorsFingerprint(),
    )

    private fun fingerprint(kind: String, values: List<Int>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(schema.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(kind.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        values.forEach { raw ->
            digest.update((raw and 0xFF).toByte())
            digest.update(((raw ushr 8) and 0xFF).toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SCHEMA = "mp48-curve-snapshot-v1"

        fun create(
            petrolAxisRaw: IntArray,
            factorsRaw: IntArray,
            usbSessionId: Long,
            provenance: CurveSnapshotProvenance,
            completeness: CurveSnapshotCompleteness,
        ): CurveSnapshot {
            require(petrolAxisRaw.size == KFactorProtocol.POINT_COUNT) {
                "Eixo Curva K exige exatamente ${KFactorProtocol.POINT_COUNT} raws"
            }
            require(factorsRaw.size == KFactorProtocol.POINT_COUNT) {
                "MUL_ACT exige exatamente ${KFactorProtocol.POINT_COUNT} raws"
            }
            require(petrolAxisRaw.all { it in 0..KFactorProtocol.MAX_RAW }) { "Eixo Curva K contém raw fora de U16" }
            require(factorsRaw.all { it in 0..KFactorProtocol.MAX_RAW }) { "MUL_ACT contém raw fora de U16" }

            return CurveSnapshot(
                petrolAxisRaw = petrolAxisRaw.toList(),
                factorsRaw = factorsRaw.toList(),
                petrolAxisMs = petrolAxisRaw.map(KFactorProtocol::petrolMsFromAxisRaw),
                factors = factorsRaw.map(KFactorProtocol::factorFromRaw),
                usbSessionId = usbSessionId,
                provenance = provenance,
                completeness = completeness,
                schema = SCHEMA,
            )
        }
    }
}
