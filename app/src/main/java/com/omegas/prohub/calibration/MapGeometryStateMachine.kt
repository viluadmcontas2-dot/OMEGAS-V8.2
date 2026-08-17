package com.omegas.prohub.calibration

enum class MapGeometryStatus {
    ABSENT,
    READING,
    KNOWN,
    PARTIAL,
    UNKNOWN,
}

data class MapGeometryState(
    val status: MapGeometryStatus,
    val snapshot: MapGeometrySnapshot? = null,
    val timeAxisAvailable: Boolean = false,
    val rpmAxisAvailable: Boolean = false,
    val reason: String = "",
)

/** Estado explícito da aquisição da geometria; KNOWN só nasce de snapshot completo. */
class MapGeometryStateMachine {
    var state: MapGeometryState = MapGeometryState(MapGeometryStatus.ABSENT)
        private set

    fun beginRead() {
        state = MapGeometryState(MapGeometryStatus.READING)
    }

    fun complete(snapshot: MapGeometrySnapshot) {
        check(state.status == MapGeometryStatus.READING) { "Geometria só pode virar KNOWN a partir de READING" }
        require(snapshot.completeness == MapGeometryCompleteness.KNOWN) {
            "Snapshot incompleto não pode virar geometry KNOWN"
        }
        state = MapGeometryState(
            status = MapGeometryStatus.KNOWN,
            snapshot = snapshot,
            timeAxisAvailable = true,
            rpmAxisAvailable = true,
        )
    }

    fun fail(
        timeAxisAvailable: Boolean,
        rpmAxisAvailable: Boolean,
        reason: String,
    ) {
        check(state.status == MapGeometryStatus.READING) { "Falha de geometria só pode fechar uma leitura ativa" }
        state = if (timeAxisAvailable xor rpmAxisAvailable) {
            MapGeometryState(
                status = MapGeometryStatus.PARTIAL,
                timeAxisAvailable = timeAxisAvailable,
                rpmAxisAvailable = rpmAxisAvailable,
                reason = reason,
            )
        } else {
            MapGeometryState(
                status = MapGeometryStatus.UNKNOWN,
                timeAxisAvailable = false,
                rpmAxisAvailable = false,
                reason = reason,
            )
        }
    }

    fun reset() {
        state = MapGeometryState(MapGeometryStatus.ABSENT)
    }
}
