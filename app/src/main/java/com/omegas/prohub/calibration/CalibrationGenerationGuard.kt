package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol

data class CalibrationGenerationCheck(
    val stable: Boolean,
    val reasons: Set<String>,
)

/** Detecta se AutoMatch/MUL_ACT mudou durante uma aquisição composta. */
object CalibrationGenerationGuard {
    fun evaluate(
        countStart: Int,
        countEnd: Int,
        mulActStart: IntArray,
        mulActEnd: IntArray,
    ): CalibrationGenerationCheck {
        require(countStart in 0..0xFFFF && countEnd in 0..0xFFFF) { "Contador AutoMatch fora de U16" }
        require(mulActStart.size == KFactorProtocol.POINT_COUNT) { "MUL_ACT inicial exige 30 raws" }
        require(mulActEnd.size == KFactorProtocol.POINT_COUNT) { "MUL_ACT final exige 30 raws" }
        require(mulActStart.all { it in 0..KFactorProtocol.MAX_RAW }) { "MUL_ACT inicial fora de U16" }
        require(mulActEnd.all { it in 0..KFactorProtocol.MAX_RAW }) { "MUL_ACT final fora de U16" }

        val reasons = linkedSetOf<String>()
        if (countStart != countEnd) reasons += "AUTOMATCH_COUNT_CHANGED"
        if (!mulActStart.contentEquals(mulActEnd)) reasons += "MUL_ACT_CHANGED"
        return CalibrationGenerationCheck(stable = reasons.isEmpty(), reasons = reasons)
    }
}
