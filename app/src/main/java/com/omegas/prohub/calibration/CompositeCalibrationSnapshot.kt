package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48GeometryCodec
import com.omegas.prohub.ecu.Mp48Protocol

/** Snapshot físico coerente derivado de uma única aquisição composta. */
class CompositeCalibrationSnapshot private constructor(
    val usbSessionId: Long,
    val autoMatchCount: Int,
    val mapGeometry: MapGeometrySnapshot,
    val curve: CurveSnapshot,
    val mapRowsRaw: List<List<Int>>,
    val mapHash: String,
    val generationStable: Boolean,
    val schema: String,
) {
    companion object {
        const val SCHEMA = "mp48-composite-calibration-v1"

        fun promote(raw: CompositeCalibrationRawRead): CompositeCalibrationSnapshot {
            require(raw.usbSessionId > 0L) { "Sessão USB inválida" }
            require(raw.curveAxisRaw.size == KFactorProtocol.POINT_COUNT) { "Curve axis incompleto" }
            require(raw.mulActStartRaw.size == KFactorProtocol.POINT_COUNT) { "MUL_ACT start incompleto" }
            require(raw.mulActEndRaw.size == KFactorProtocol.POINT_COUNT) { "MUL_ACT end incompleto" }
            require(raw.mapTimeAxisRaw.size == Mp48GeometryCodec.AXIS_POINTS) { "TEMPI_PER_K incompleto" }
            require(raw.mapRpmAxisRaw.size == Mp48GeometryCodec.AXIS_POINTS) { "GIRI_PER_K incompleto" }
            require(raw.mapRowsRaw.size == Mp48Protocol.MAP_ROWS) { "Mapa K exige ${Mp48Protocol.MAP_ROWS} linhas físicas" }
            require(raw.mapRowsRaw.all { it.size == Mp48Protocol.MAP_COLUMNS }) { "Mapa K exige ${Mp48Protocol.MAP_COLUMNS} colunas por linha" }
            require(raw.mapRowsRaw.flatten().all { it in 0..0xFF }) { "Mapa K contém raw fora de U8" }

            val generation = CalibrationGenerationGuard.evaluate(
                countStart = raw.autoMatchCountStart,
                countEnd = raw.autoMatchCountEnd,
                mulActStart = raw.mulActStartRaw.toIntArray(),
                mulActEnd = raw.mulActEndRaw.toIntArray(),
            )
            require(generation.stable) {
                "Snapshot híbrido rejeitado: ${generation.reasons.sorted().joinToString(",")}" 
            }

            val timeRaw = raw.mapTimeAxisRaw.toIntArray()
            val rpmRaw = raw.mapRpmAxisRaw.toIntArray()
            val geometry = MapGeometrySnapshot.create(
                timeAxisRaw = timeRaw,
                timeAxisMs = Mp48GeometryCodec.timeAxisMs(timeRaw),
                rpmAxisRaw = rpmRaw,
                usbSessionId = raw.usbSessionId,
                provenance = MapGeometryProvenance.FULL_ECU_READ,
                completeness = MapGeometryCompleteness.KNOWN,
            )
            val curve = CurveSnapshot.create(
                petrolAxisRaw = raw.curveAxisRaw.toIntArray(),
                factorsRaw = raw.mulActEndRaw.toIntArray(),
                usbSessionId = raw.usbSessionId,
                provenance = CurveSnapshotProvenance.FULL_ECU_READ,
                completeness = CurveSnapshotCompleteness.KNOWN,
            )
            require(geometry.usbSessionId == curve.usbSessionId) { "Componentes pertencem a sessões diferentes" }

            val copiedRows = raw.mapRowsRaw.map { it.toList() }
            return CompositeCalibrationSnapshot(
                usbSessionId = raw.usbSessionId,
                autoMatchCount = raw.autoMatchCountEnd,
                mapGeometry = geometry,
                curve = curve,
                mapRowsRaw = copiedRows,
                mapHash = MapKPhysicalHash.hash(copiedRows),
                generationStable = true,
                schema = SCHEMA,
            )
        }
    }
}
