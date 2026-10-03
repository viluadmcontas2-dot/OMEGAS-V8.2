package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import kotlin.math.max

/**
 * Tipos do cérebro único (Fatia F4). O pacote só observa: não importa `android.*` nem toca a ECU
 * (contrato em tests/test_f4_equivalence_wiring_contract.py).
 */
enum class Fuel { GASOLINA, GNV }

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
)

enum class NextActionKind { OPERATION, FREEZE_REFERENCE, CONTESTED, APPLY, PROVING, COLLECT, NOTHING }

data class NextAction(
    val kind: NextActionKind,
    val text: String,
    val route: String?,
    val subpage: String?,
    val pointIndexes: List<Int>,
)

data class EquivalenceResult(
    val points: List<EquivalencePoint>,
    val index: Double?,
    val coverage: Int,
    val provisional: Boolean,
    val nextAction: NextAction,
    val proposal: AutoMatchRefinedEngine.Result?,
    val ownPetrol: OwnCurve,
    val ownGas: OwnCurve,
)

object EquivalenceTolerances {
    const val MIN = 0.04
    const val LIGHT = 0.08
    const val DIVERGENCE_ALARM = 0.08
    const val WORSE_DELTA = 0.04
    const val WORSE_ERROR = 0.05
    const val CELL_BAR = 0.02
    const val USAGE_SESSIONS = 10

    /** Maior entre 4% e duas dispersões da célula. */
    fun tolerance(dispersion: Double): Double = max(MIN, 2.0 * dispersion)
}

/** Veredito por ponto de uma prova aberta (EquivalencePhases) + minutos de condução que faltam. */
data class ProofOutcome(val states: Map<Int, PointState>, val remainingMinutes: Int?) {
    companion object {
        val NONE = ProofOutcome(emptyMap(), null)
    }
}
