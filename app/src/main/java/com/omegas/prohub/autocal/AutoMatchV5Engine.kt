package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale
import com.omegas.prohub.ecu.KFactorProtocol
import kotlin.math.abs

/**
 * Emulador determinístico da hipótese de referência do AutoMatch.
 *
 * A reconstrução usa as 18 bandas de pressão, compara horizontalmente as curvas
 * de gasolina e GNV, suaviza três bandas, aplica deadband de 1%, ganho de 1/3,
 * limita cada execução a ±5% e atualiza multiplicativamente o MUL_ACT anterior.
 *
 * Esta classe não acessa USB, não escreve na ECU e mantém
 * [AutoMatchV5Result.nativeFirmwareExact] como falso. A fórmula V6 foi fechada no
 * laboratório virtual do projeto; não é apresentada como prova da rotina OEM.
 */
object AutoMatchV5Engine {
    const val ALGORITHM = "OMEGAS_INFERRED_HORIZONTAL_G1_3_S3_DB1_CAP5_MUL_Q14_V2"
    const val PRESSURE_BAND_COUNT = 18
    const val SMOOTHING_WINDOW = 3
    const val GAIN = 1.0 / 3.0
    const val DEADBAND_RATIO = 0.01
    const val MAX_STEP_RATIO = 0.05
    const val SUPPORT_MIN_MS = 2.785
    const val SUPPORT_MAX_MS = 12.791
    const val MIN_FACTOR = 0.60
    const val MAX_FACTOR = KFactorProtocol.MAX_FACTOR

    fun calculate(
        petrol: AutoMatchCurve30,
        gas: AutoMatchCurve30,
        pressureBandsRaw: IntArray,
        previousFactorsRaw: IntArray,
    ): AutoMatchV5Result {
        require(pressureBandsRaw.size == PRESSURE_BAND_COUNT) {
            "AutoMatch exige $PRESSURE_BAND_COUNT bandas de pressão"
        }
        require(previousFactorsRaw.size == KFactorProtocol.POINT_COUNT) {
            "MUL_ACT deve possuir ${KFactorProtocol.POINT_COUNT} pontos"
        }
        require(pressureBandsRaw.all { it in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() }) {
            "Bandas de pressão contêm valor S16 inválido"
        }
        require((1 until pressureBandsRaw.size).all { pressureBandsRaw[it] > pressureBandsRaw[it - 1] }) {
            "Bandas de pressão devem ser estritamente crescentes"
        }
        require(previousFactorsRaw.all { it in 0..KFactorProtocol.MAX_RAW }) {
            "MUL_ACT contém Q14 inválido"
        }

        val bands: List<BandSample> = pressureBandsRaw.toList().mapNotNull { pressureRaw ->
            val petrolTimeRaw = inverseTimeRaw(pressureRaw, petrol) ?: return@mapNotNull null
            val gasTimeRaw = inverseTimeRaw(pressureRaw, gas) ?: return@mapNotNull null
            val petrolTimeMs = petrolTimeRaw / KFactorProtocol.AXIS_COUNTS_PER_MS
            if (petrolTimeMs !in SUPPORT_MIN_MS..SUPPORT_MAX_MS || petrolTimeRaw <= 0.0) {
                return@mapNotNull null
            }
            BandSample(
                pressureRaw = pressureRaw,
                petrolTimeRaw = petrolTimeRaw,
                gasTimeRaw = gasTimeRaw,
                ratio = gasTimeRaw / petrolTimeRaw,
            )
        }

        if (bands.size < SMOOTHING_WINDOW) {
            return unavailable(
                petrol = petrol,
                previousFactorsRaw = previousFactorsRaw,
                warning = "As curvas não possuem ao menos $SMOOTHING_WINDOW bandas comuns na faixa de suporte",
                validBandCount = bands.size,
            )
        }

        val smoothed = smoothEdge(bands.map { it.ratio }.toDoubleArray())
        val bandSteps = DoubleArray(bands.size) { index ->
            val error = smoothed[index] - 1.0
            if (abs(error) < DEADBAND_RATIO) 0.0
            else (GAIN * error).coerceIn(-MAX_STEP_RATIO, MAX_STEP_RATIO)
        }
        val bandTimes = bands.map { it.petrolTimeRaw }.toDoubleArray()
        val firstSupportRaw = bandTimes.first()
        val lastSupportRaw = bandTimes.last()

        val points = List(KFactorProtocol.POINT_COUNT) { index ->
            val referenceRaw = petrol.axisTimeRaw[index]
            val step = interpolateConstantEdges(referenceRaw.toDouble(), bandTimes, bandSteps)
            val currentRaw = previousFactorsRaw[index]
            val currentFactor = KFactorProtocol.factorFromRaw(currentRaw)
            val calculatedFactor = (currentFactor * (1.0 + step)).coerceIn(MIN_FACTOR, MAX_FACTOR)
            val calculatedRaw = KFactorProtocol.rawFromFactor(calculatedFactor)
            val gasEquivalentRaw = inverseTimeRaw(petrol.mapRaw[index], gas)?.toInt()
            val origin = when {
                referenceRaw < firstSupportRaw -> AutoMatchPointOrigin.EXTENDED_LEFT
                referenceRaw > lastSupportRaw -> AutoMatchPointOrigin.EXTENDED_RIGHT
                else -> AutoMatchPointOrigin.CALCULATED
            }
            AutoMatchPoint(
                index = index,
                referenceTimeRaw = referenceRaw,
                referenceTimeMs = AutoCalScale.injectionMs(referenceRaw),
                targetMapRaw = petrol.mapRaw[index],
                gasEquivalentTimeRaw = gasEquivalentRaw,
                gasEquivalentTimeMs = gasEquivalentRaw?.let(AutoCalScale::injectionMs),
                currentRaw = currentRaw,
                currentFactor = currentFactor,
                stepRatio = step,
                factorRaw = calculatedRaw,
                factor = KFactorProtocol.factorFromRaw(calculatedRaw),
                origin = origin,
            )
        }

        val first = points.indexOfFirst { it.origin == AutoMatchPointOrigin.CALCULATED }
            .takeIf { it >= 0 }
        val last = points.indexOfLast { it.origin == AutoMatchPointOrigin.CALCULATED }
            .takeIf { it >= 0 }
        val warnings = buildList {
            add("AutoMatch inferido: fórmula de referência, não firmware OEM exato")
            if (bands.size < PRESSURE_BAND_COUNT) {
                add("${bands.size}/$PRESSURE_BAND_COUNT bandas participaram da atualização")
            }
        }
        return AutoMatchV5Result(
            points = points,
            firstCalculatedIndex = first,
            lastCalculatedIndex = last,
            validBandCount = bands.size,
            supportStartMs = firstSupportRaw / KFactorProtocol.AXIS_COUNTS_PER_MS,
            supportEndMs = lastSupportRaw / KFactorProtocol.AXIS_COUNTS_PER_MS,
            warnings = warnings,
        )
    }

    private fun unavailable(
        petrol: AutoMatchCurve30,
        previousFactorsRaw: IntArray,
        warning: String,
        validBandCount: Int,
    ): AutoMatchV5Result = AutoMatchV5Result(
        points = List(KFactorProtocol.POINT_COUNT) { index ->
            val currentRaw = previousFactorsRaw[index]
            AutoMatchPoint(
                index = index,
                referenceTimeRaw = petrol.axisTimeRaw[index],
                referenceTimeMs = AutoCalScale.injectionMs(petrol.axisTimeRaw[index]),
                targetMapRaw = petrol.mapRaw[index],
                gasEquivalentTimeRaw = null,
                gasEquivalentTimeMs = null,
                currentRaw = currentRaw,
                currentFactor = KFactorProtocol.factorFromRaw(currentRaw),
                stepRatio = null,
                factorRaw = null,
                factor = null,
                origin = AutoMatchPointOrigin.UNAVAILABLE,
            )
        },
        firstCalculatedIndex = null,
        lastCalculatedIndex = null,
        validBandCount = validBandCount,
        supportStartMs = null,
        supportEndMs = null,
        warnings = listOf(warning, "AutoMatch inferido: fórmula de referência, não firmware OEM exato"),
    )

    private fun inverseTimeRaw(targetMapRaw: Int, curve: AutoMatchCurve30): Double? {
        for (index in 0 until KFactorProtocol.POINT_COUNT - 1) {
            if (!curve.valid[index] || !curve.valid[index + 1]) continue
            val x0 = curve.axisTimeRaw[index].toDouble()
            val x1 = curve.axisTimeRaw[index + 1].toDouble()
            val y0 = curve.mapRaw[index]
            val y1 = curve.mapRaw[index + 1]
            if (x1 <= x0) continue
            if (y0 == y1) {
                if (targetMapRaw == y0) return x0
                continue
            }
            if (targetMapRaw !in minOf(y0, y1)..maxOf(y0, y1)) continue
            val fraction = (targetMapRaw - y0).toDouble() / (y1 - y0).toDouble()
            return x0 + (x1 - x0) * fraction
        }
        return null
    }

    private fun smoothEdge(values: DoubleArray): DoubleArray = DoubleArray(values.size) { index ->
        val left = values[(index - 1).coerceAtLeast(0)]
        val center = values[index]
        val right = values[(index + 1).coerceAtMost(values.lastIndex)]
        (left + center + right) / SMOOTHING_WINDOW.toDouble()
    }

    private fun interpolateConstantEdges(
        x: Double,
        axis: DoubleArray,
        values: DoubleArray,
    ): Double {
        require(axis.size == values.size && axis.isNotEmpty())
        if (x <= axis.first()) return values.first()
        if (x >= axis.last()) return values.last()
        val upper = axis.indexOfFirst { it >= x }.coerceAtLeast(1)
        val lower = upper - 1
        val span = axis[upper] - axis[lower]
        if (span <= 0.0) return values[lower]
        val fraction = ((x - axis[lower]) / span).coerceIn(0.0, 1.0)
        return values[lower] + (values[upper] - values[lower]) * fraction
    }

    private data class BandSample(
        val pressureRaw: Int,
        val petrolTimeRaw: Double,
        val gasTimeRaw: Double,
        val ratio: Double,
    )
}

class AutoMatchCurve30(
    val axisTimeRaw: IntArray,
    val mapRaw: IntArray,
    val valid: BooleanArray = BooleanArray(mapRaw.size) { true },
) {
    init {
        require(axisTimeRaw.size == KFactorProtocol.POINT_COUNT) {
            "Eixo AutoMatch deve possuir ${KFactorProtocol.POINT_COUNT} pontos"
        }
        require(mapRaw.size == KFactorProtocol.POINT_COUNT) {
            "Curva AutoMatch deve possuir ${KFactorProtocol.POINT_COUNT} pontos"
        }
        require(valid.size == KFactorProtocol.POINT_COUNT) {
            "Máscara AutoMatch deve possuir ${KFactorProtocol.POINT_COUNT} pontos"
        }
        require(axisTimeRaw.all { it in 1..0xFFFF }) { "Eixo AutoMatch contém tempo inválido" }
        require((1 until axisTimeRaw.size).all { index -> axisTimeRaw[index] > axisTimeRaw[index - 1] }) {
            "Eixo AutoMatch deve ser estritamente crescente"
        }
        require(mapRaw.all { it in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() }) {
            "Curva AutoMatch contém MAP S16 inválido"
        }
    }
}

enum class AutoMatchPointOrigin {
    CALCULATED,
    EXTENDED_LEFT,
    EXTENDED_RIGHT,
    UNAVAILABLE,
}

data class AutoMatchPoint(
    val index: Int,
    val referenceTimeRaw: Int,
    val referenceTimeMs: Double,
    val targetMapRaw: Int,
    val gasEquivalentTimeRaw: Int?,
    val gasEquivalentTimeMs: Double?,
    val currentRaw: Int,
    val currentFactor: Double,
    val stepRatio: Double?,
    val factorRaw: Int?,
    val factor: Double?,
    val origin: AutoMatchPointOrigin,
)

data class AutoMatchV5Result(
    val algorithm: String = AutoMatchV5Engine.ALGORITHM,
    val nativeFirmwareExact: Boolean = false,
    val points: List<AutoMatchPoint>,
    val firstCalculatedIndex: Int?,
    val lastCalculatedIndex: Int?,
    val validBandCount: Int,
    val supportStartMs: Double?,
    val supportEndMs: Double?,
    val warnings: List<String>,
) {
    val calculatedCount: Int get() = points.count { it.origin == AutoMatchPointOrigin.CALCULATED }
    val extendedCount: Int get() = points.count {
        it.origin == AutoMatchPointOrigin.EXTENDED_LEFT ||
            it.origin == AutoMatchPointOrigin.EXTENDED_RIGHT
    }
    val complete: Boolean get() = points.size == KFactorProtocol.POINT_COUNT && points.all { it.factorRaw != null }
}
