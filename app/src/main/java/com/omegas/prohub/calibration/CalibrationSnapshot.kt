package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationShapeV7

/**
 * Fotografia física bruta de uma calibração MP48 em um instante.
 *
 * Este tipo deliberadamente não atribui ainda fingerprint, geração USB,
 * proveniência ou estado reconciliado: esses contratos pertencem aos
 * micropassos 056+ do Programa Definitivo V8.2 NEXT.
 *
 * Os valores permanecem em representação raw para que a fotografia não dependa
 * de arredondamento de UI nem de uma segunda interpretação física.
 */
data class CalibrationSnapshot private constructor(
    val mapKRowsRaw: List<List<Int>>,
    val rpmAxisRaw: List<Int>,
    val petrolTimeAxisRaw: List<Int>,
    val curveKRaw: List<Int>,
    val relevantStateRaw: Map<Int, List<Int>>,
) {
    companion object {
        /**
         * Captura cópias defensivas dos objetos físicos já lidos da ECU.
         * A chave de relevantStateRaw é o endereço/identificador MP48 do objeto.
         */
        fun capture(
            mapKRowsRaw: List<List<Int>>,
            rpmAxisRaw: List<Int>,
            petrolTimeAxisRaw: List<Int>,
            curveKRaw: List<Int>,
            relevantStateRaw: Map<Int, List<Int>> = emptyMap(),
        ): CalibrationSnapshot {
            CalibrationShapeV7.requireMap(mapKRowsRaw)
            require(rpmAxisRaw.isNotEmpty()) { "Snapshot exige eixo RPM raw" }
            require(petrolTimeAxisRaw.isNotEmpty()) { "Snapshot exige eixo de tempo raw" }
            require(curveKRaw.size == CalibrationShapeV7.CURVE_K_POINTS) {
                "Snapshot exige ${CalibrationShapeV7.CURVE_K_POINTS} pontos raw da Curva K"
            }
            require(rpmAxisRaw.all(::isRawByte)) { "Eixo RPM contém valor fora de U8" }
            require(petrolTimeAxisRaw.all(::isRawByte)) { "Eixo de tempo contém valor fora de U8" }
            require(curveKRaw.all(::isRawByte)) { "Curva K contém valor fora de U8" }
            require(relevantStateRaw.keys.all { it >= 0 }) { "Endereço MP48 relevante inválido" }
            require(relevantStateRaw.values.flatten().all(::isRawByte)) {
                "Estado relevante contém valor fora de U8"
            }

            return CalibrationSnapshot(
                mapKRowsRaw = mapKRowsRaw.map { it.toList() },
                rpmAxisRaw = rpmAxisRaw.toList(),
                petrolTimeAxisRaw = petrolTimeAxisRaw.toList(),
                curveKRaw = curveKRaw.toList(),
                relevantStateRaw = relevantStateRaw
                    .toSortedMap()
                    .mapValues { (_, bytes) -> bytes.toList() },
            )
        }

        private fun isRawByte(value: Int): Boolean = value in 0..0xFF
    }
}
