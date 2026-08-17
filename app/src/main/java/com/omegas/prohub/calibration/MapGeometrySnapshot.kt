package com.omegas.prohub.calibration

enum class MapGeometryProvenance {
    FULL_ECU_READ,
    UNKNOWN,
}

enum class MapGeometryCompleteness {
    KNOWN,
    PARTIAL,
    UNKNOWN,
}

/** Snapshot imutável da geometria física corrente do Mapa K. */
class MapGeometrySnapshot private constructor(
    val timeAxisRaw: List<Int>,
    val timeAxisMs: List<Double>,
    val rpmAxisRaw: List<Int>,
    val usbSessionId: Long,
    val provenance: MapGeometryProvenance,
    val completeness: MapGeometryCompleteness,
    val schema: String,
) {
    fun toSerializableMap(): Map<String, Any> = linkedMapOf(
        "schema" to schema,
        "usbSessionId" to usbSessionId,
        "provenance" to provenance.name,
        "completeness" to completeness.name,
        "timeAxisRaw" to timeAxisRaw,
        "timeAxisMs" to timeAxisMs,
        "rpmAxisRaw" to rpmAxisRaw,
    )

    companion object {
        const val SCHEMA = "mp48-map-geometry-v1"
        private const val AXIS_POINTS = 12

        fun create(
            timeAxisRaw: IntArray,
            timeAxisMs: DoubleArray,
            rpmAxisRaw: IntArray,
            usbSessionId: Long,
            provenance: MapGeometryProvenance,
            completeness: MapGeometryCompleteness,
        ): MapGeometrySnapshot {
            require(timeAxisRaw.size == AXIS_POINTS) { "timeAxisRaw exige exatamente $AXIS_POINTS valores" }
            require(timeAxisMs.size == AXIS_POINTS) { "timeAxisMs exige exatamente $AXIS_POINTS valores" }
            require(rpmAxisRaw.size == AXIS_POINTS) { "rpmAxisRaw exige exatamente $AXIS_POINTS valores" }
            require(timeAxisRaw.all { it in 0..0xFFFF }) { "timeAxisRaw contém valor fora de U16" }
            require(rpmAxisRaw.all { it in 0..0xFFFF }) { "rpmAxisRaw contém valor fora de U16" }
            require(timeAxisMs.all { it.isFinite() }) { "timeAxisMs contém valor não finito" }

            return MapGeometrySnapshot(
                timeAxisRaw = timeAxisRaw.toList(),
                timeAxisMs = timeAxisMs.toList(),
                rpmAxisRaw = rpmAxisRaw.toList(),
                usbSessionId = usbSessionId,
                provenance = provenance,
                completeness = completeness,
                schema = SCHEMA,
            )
        }
    }
}
