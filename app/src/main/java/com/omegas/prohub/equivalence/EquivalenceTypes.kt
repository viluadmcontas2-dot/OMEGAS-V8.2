package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import kotlin.math.max

/**
 * Tipos do cérebro único (Fatia F4). O pacote só observa: não importa `android.*` nem toca a ECU
 * (contrato em tests/test_f4_equivalence_wiring_contract.py).
 */
enum class Fuel { GASOLINA, GNV }

enum class OperatingRegime {
    IDLE,
    DRIVING;

    fun accepts(rpm: Double): Boolean = when (this) {
        IDLE -> rpm < com.omegas.prohub.autocal.EquivalenceLedger.DRIVING_MIN_RPM
        DRIVING -> rpm >= com.omegas.prohub.autocal.EquivalenceLedger.DRIVING_MIN_RPM
    }
}

/** Ponto da Referência: MAP (bar), Petrol Inj. (ms) e maturidade (contador da ECU). */
data class RefPoint(val mapBar: Double, val petrolMs: Double, val maturity: Int)

/** Gasolina da ECU congelada pelo dono (ou provisória, vinda ao vivo da ECU). */
data class Reference(
    val id: String,
    val frozenAt: Long,
    val ecuAcquisitionFingerprint: String,
    val points: List<RefPoint>,
)

enum class CellSource { REFERENCE, BLENDED, OWN }

/** Célula de 0,02 bar da Curva Própria. [petrolMs] nulo = sem Referência e sem leitura ali. */
data class OwnCell(
    val mapBar: Double,
    val petrolMs: Double?,
    val samples: Int,
    val dispersion: Double,
    val source: CellSource,
    val divergence: Double?,
)

class OwnCurve(val fuel: Fuel, val cells: List<OwnCell>) {
    private val knownMap: DoubleArray
    private val knownMs: DoubleArray

    init {
        val maps = ArrayList<Double>()
        val values = ArrayList<Double>()
        for (cell in cells) {
            val ms = cell.petrolMs ?: continue
            maps += cell.mapBar
            values += ms
        }
        knownMap = maps.toDoubleArray()
        knownMs = values.toDoubleArray()
    }

    /** Interpolação linear entre os centros das células com valor; nulo fora da primeira/última. */
    fun at(mapBar: Double): Double? {
        if (knownMap.isEmpty() || mapBar.isNaN()) return null
        if (mapBar < knownMap.first() || mapBar > knownMap.last()) return null
        for (i in 1 until knownMap.size) {
            if (mapBar <= knownMap[i]) {
                val span = knownMap[i] - knownMap[i - 1]
                if (span <= 0.0) return knownMs[i - 1]
                return knownMs[i - 1] + (knownMs[i] - knownMs[i - 1]) * (mapBar - knownMap[i - 1]) / span
            }
        }
        return knownMs.last()
    }
}

data class RegimeCurves(val petrol: OwnCurve, val gas: OwnCurve)

data class RegimeAssessment(
    val regime: OperatingRegime,
    val pairs: Int,
    val effectiveSamples: Double,
    val mixture: Double?,
    val dispersion: Double?,
    val judgeable: Boolean,
)

enum class PointState { SEM_DADOS, APRENDENDO, MEDIDO, EQUIVALENTE, POBRE, RICO, EM_PROVA, CONFIRMADO, CONTESTADO, INCONCLUSIVO }

data class EquivalencePoint(
    val index: Int,
    val axisMs: Double,
    val kCurrent: Double,
    val kTarget: Double?,
    /** kTarget/kCurrent − 1 (fração; positivo = GNV pobre). */
    val mixture: Double?,
    val tolerance: Double,
    val roughnessRatio: Double?,
    val nearStallRatio: Double?,
    val slope: Double?,
    val usage: Double,
    val samples: Int,
    val sources: Set<String>,
    val state: PointState,
    /** Visitas distintas (trechos separados por ≥ 60 s) que sustentam o veredito do ponto. */
    val episodes: Int = 0,
)

enum class NextActionKind { OPERATION, FREEZE_REFERENCE, CONTESTED, APPLY, PROVING, COLLECT, NOTHING }

data class NextAction(
    val kind: NextActionKind,
    val text: String,
    val route: String?,
    val subpage: String?,
    /** APPLY: os pontos que a gravação muda (= o que o botão grava); COLLECT/PROVING/...: os pontos a que a frase se refere. */
    val pointIndexes: List<Int>,
    /**
     * APPLY do cérebro: a Curva K lida (30 raw) e a proposta (30 raw, já com a trava da baixa de
     * [com.omegas.prohub.autocal.AutoMatchSnapshotAnalysis.LOW_GUARD_MS]). Fonte única do que a UI grava: o mesmo
     * motor e os mesmos pares do veredito. Nulos nas outras ações.
     */
    val currentRaw: List<Int>? = null,
    val refinedRaw: List<Int>? = null,
)

data class EquivalenceResult(
    val points: List<EquivalencePoint>,
    /** Fração 0..1 do uso julgada como equivalente; nulo ("—") quando [judgedUsage] não passa de [EquivalenceTolerances.MIN_JUDGED_USAGE]. */
    val index: Double?,
    val coverage: Int,
    val provisional: Boolean,
    val nextAction: NextAction,
    val proposal: AutoMatchRefinedEngine.Result?,
    val ownPetrol: OwnCurve,
    val ownGas: OwnCurve,
    /** Fração 0..1 do uso da condução que caiu em pontos julgados (nem SEM_DADOS, APRENDENDO nem MEDIDO). */
    val judgedUsage: Double = 0.0,
    val regimeCurves: Map<OperatingRegime, RegimeCurves> = emptyMap(),
    val regimeAssessments: Map<OperatingRegime, RegimeAssessment> = emptyMap(),
)

object EquivalenceTolerances {
    /** Piso da tolerância: GNV equivalente à gasolina na margem de ±4%. */
    const val MIN = 0.04
    /** Teto da tolerância: até ±5% é aceito; nunca mais largo (dispersão alta não alarga o critério, tira o ponto do julgamento). */
    const val MAX = 0.05
    const val DIVERGENCE_ALARM = 0.08
    const val WORSE_DELTA = 0.04
    const val WORSE_ERROR = 0.05
    const val CELL_BAR = 0.02
    const val USAGE_SESSIONS = 10
    /** O índice só vira número quando pelo menos esta fração do uso da condução está em pontos julgados. */
    const val MIN_JUDGED_USAGE = 0.5

    /** Duas dispersões da célula, limitado a [MIN, MAX]: o critério nunca passa de ±5%. */
    fun tolerance(dispersion: Double): Double = (2.0 * dispersion).coerceIn(MIN, MAX)
}

/** Veredito por ponto de uma prova aberta (EquivalencePhases) + minutos de condução que faltam. */
data class ProofOutcome(
    val states: Map<Int, PointState>,
    val remainingMinutes: Int?,
    /** Motivo de cada prova fechada sem veredito de confirmação (INCONCLUSIVO): nada é apagado em silêncio. */
    val reasons: Map<Int, String> = emptyMap(),
) {
    companion object {
        val NONE = ProofOutcome(emptyMap(), null)
        const val REASON_NO_CONVERGENCE = "NAO_CONVERGIU"
        const val REASON_EXHAUSTED = "TENTATIVAS_ESGOTADAS"
        /** Tentativas de ajuste (gravar → provar) por ponto antes de parar de propor. */
        const val MAX_ATTEMPTS = 2
    }
}
