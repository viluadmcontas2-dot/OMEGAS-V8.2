package com.omegas.prohub.telemetry

import kotlin.math.abs

/**
 * Evidência de um ciclo entre DOIS abastecimentos em condição final comparável.
 * purchasedM3 vem do recibo/visor do dispenser; nunca do byte de nível da MP48.
 * injectorOpenMinutesGnv já é derivado pelo SessionSummaryBuilder/TankLedger,
 * não é tempo de viagem, nem duração de um único injetor.
 */
data class CngFillCycle(
    val id: String,
    val injectorOpenMinutesGnv: Double,
    val purchasedM3: Double,
    val comparableFullToFull: Boolean,
    val coverage: Double,
)

/** Estimativa baseada em abastecimentos, NÃO medidor de vazão nem medida homologada. */
data class CngVolumeEstimate(
    val state: String,
    val referenceCycles: Int,
    val m3PerInjectorOpenMinute: Double?,
    val consumedM3: Double?,
    val kmPerM3: Double?,
    val holdoutErrorPct: Double?,
    val isDirectMeasurement: Boolean = false,
    val limitation: String = "Coeficiente empírico: depende dos injetores, pressão, temperatura, composição do gás e padrão de abastecimento.",
)

/**
 * Calibrador de leitura apenas: não comunica com ECU, não sugere nem aplica mapa.
 * Requer 2 ciclos para ajustar e um TERCEIRO, independente, para validar o erro.
 * Limite de erro de 5% é um gate conservador do produto, não certificação metrológica.
 */
object CngVolumeCalibration {
    private const val MIN_COVERAGE = 0.95
    private const val MAX_HOLDOUT_ERROR_PCT = 5.0
    private const val MAX_TRAINING_SPREAD_PCT = 10.0

    fun assess(
        cycles: List<CngFillCycle>,
        currentInjectorOpenMinutesGnv: Double?,
        currentKmOnGnv: Double?,
    ): CngVolumeEstimate {
        val valid = cycles.asSequence()
            .filter { it.id.isNotBlank() && it.comparableFullToFull }
            .filter { it.purchasedM3.isFinite() && it.purchasedM3 > 0.0 }
            .filter { it.injectorOpenMinutesGnv.isFinite() && it.injectorOpenMinutesGnv > 0.0 }
            .filter { it.coverage.isFinite() && it.coverage in MIN_COVERAGE..1.0 }
            .distinctBy { it.id }
            .toList()
        fun empty(state: String, err: Double? = null) = CngVolumeEstimate(
            state, valid.size, null, null, null, err,
        )
        if (valid.size < 3) return empty("SEM_CALIBRACAO")

        // O ciclo mais recente é reservado como prova, não participa do ajuste.
        val train = valid.dropLast(1).map { it.purchasedM3 / it.injectorOpenMinutesGnv }
        val sorted = train.sorted()
        val middle = sorted.size / 2
        val factor = if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
        if (!factor.isFinite() || factor <= 0.0) return empty("SEM_CALIBRACAO")
        val spreadPct = (sorted.last() - sorted.first()) / factor * 100.0
        if (!spreadPct.isFinite() || spreadPct > MAX_TRAINING_SPREAD_PCT) return empty("DIVERGENCIA_MEDIDA")

        val holdout = valid.last()
        val errorPct = abs(factor * holdout.injectorOpenMinutesGnv - holdout.purchasedM3) / holdout.purchasedM3 * 100.0
        if (!errorPct.isFinite() || errorPct > MAX_HOLDOUT_ERROR_PCT) return empty("DIVERGENCIA_MEDIDA", errorPct)

        val exposure = currentInjectorOpenMinutesGnv?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return empty("SEM_COBERTURA_ATUAL", errorPct)
        val estimated = factor * exposure
        val km = currentKmOnGnv?.takeIf { it.isFinite() && it > 0.0 }
        return CngVolumeEstimate(
            state = "VALIDADO_POR_ABASTECIMENTO",
            referenceCycles = valid.size,
            m3PerInjectorOpenMinute = factor,
            consumedM3 = estimated,
            kmPerM3 = if (km != null && estimated > 0.0) km / estimated else null,
            holdoutErrorPct = errorPct,
        )
    }
}
