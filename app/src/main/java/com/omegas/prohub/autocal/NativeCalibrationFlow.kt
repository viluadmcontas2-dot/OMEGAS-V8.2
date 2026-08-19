package com.omegas.prohub.autocal

/**
 * Identidade causal dos três fluxos nativos de calibração.
 *
 * O tipo existe para impedir que estados de aquisição AutoCal, AutoMatch e
 * calibração estacionária sejam reutilizados como se descrevessem o mesmo
 * processo físico. Ele não agenda I/O, não escreve ECU e não promove
 * observação nativa a sugestão científica.
 */
enum class NativeCalibrationFlow {
    AUTOCAL_ACQUISITION,
    AUTOMATCH,
    AUTOMATIC_ECU_CALIBRATION_STATIONARY,
}

sealed interface NativeCalibrationFlowState {
    val flow: NativeCalibrationFlow
}

enum class AutoCalAcquisitionFlowState : NativeCalibrationFlowState {
    IDLE,
    ENABLED,
    PAUSED;

    override val flow: NativeCalibrationFlow
        get() = NativeCalibrationFlow.AUTOCAL_ACQUISITION
}

enum class AutoMatchFlowState : NativeCalibrationFlowState {
    IDLE,
    OBSERVED;

    override val flow: NativeCalibrationFlow
        get() = NativeCalibrationFlow.AUTOMATCH
}

enum class StationaryCalibrationFlowState : NativeCalibrationFlowState {
    IDLE,
    OBSERVED;

    override val flow: NativeCalibrationFlow
        get() = NativeCalibrationFlow.AUTOMATIC_ECU_CALIBRATION_STATIONARY
}

object NativeCalibrationFlowGuard {
    /**
     * Uma transição só é válida dentro da mesma família causal.
     * Estados físicos detalhados continuam pertencendo aos owners específicos.
     */
    fun requireSameFlow(
        from: NativeCalibrationFlowState,
        to: NativeCalibrationFlowState,
    ) {
        require(from.flow == to.flow) {
            "Transição entre fluxos nativos distintos é proibida: ${from.flow} -> ${to.flow}"
        }
    }
}
