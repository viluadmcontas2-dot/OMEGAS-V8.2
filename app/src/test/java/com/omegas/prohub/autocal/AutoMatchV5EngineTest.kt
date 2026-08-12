package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchV5EngineTest {
    private val pressureBands = intArrayOf(
        154, 256, 307, 358, 410, 461, 512, 563, 614,
        666, 717, 768, 819, 870, 922, 973, 1024, 1126,
    )

    @Test
    fun `curvas iguais preservam o MUL anterior em vez de substituir por um`() {
        val petrol = recoveredPetrol()
        val previous = IntArray(KFactorProtocol.POINT_COUNT) { 0x4000 + it }

        val result = calculate(petrol, petrol, previous)

        assertTrue(result.complete)
        assertFalse(result.nativeFirmwareExact)
        assertEquals(AutoMatchV5Engine.ALGORITHM, result.algorithm)
        assertEquals(14, result.validBandCount)
        assertArrayEquals(previous, result.points.map { it.factorRaw!! }.toIntArray())
        assertTrue(result.points.all { it.stepRatio == 0.0 })
    }

    @Test
    fun `fixture recuperada aplica ganho suavizacao deadband e extensao`() {
        val result = calculate(
            petrolMap = recoveredPetrol(),
            gasMap = recoveredGas(),
            previous = IntArray(KFactorProtocol.POINT_COUNT) { KFactorProtocol.Q14_SCALE },
        )

        assertTrue(result.complete)
        assertEquals(14, result.validBandCount)
        assertEquals(5, result.firstCalculatedIndex)
        assertEquals(21, result.lastCalculatedIndex)
        assertEquals(17, result.calculatedCount)
        assertEquals(13, result.extendedCount)
        assertEquals(16_439, result.points[0].factorRaw)
        assertEquals(16_426, result.points[5].factorRaw)
        assertEquals(16_183, result.points[7].factorRaw)
        assertEquals(16_437, result.points[10].factorRaw)
        assertEquals(16_332, result.points[21].factorRaw)
        assertEquals(16_316, result.points[22].factorRaw)
        assertEquals(16_316, result.points[29].factorRaw)
        assertEquals(AutoMatchPointOrigin.EXTENDED_LEFT, result.points[4].origin)
        assertEquals(AutoMatchPointOrigin.CALCULATED, result.points[5].origin)
        assertEquals(AutoMatchPointOrigin.CALCULATED, result.points[21].origin)
        assertEquals(AutoMatchPointOrigin.EXTENDED_RIGHT, result.points[22].origin)
        assertTrue(result.warnings.any { it.contains("não firmware OEM exato") })
    }

    @Test
    fun `segunda execucao usa multiplicativamente o resultado anterior`() {
        val first = calculate(
            recoveredPetrol(),
            recoveredGas(),
            IntArray(KFactorProtocol.POINT_COUNT) { KFactorProtocol.Q14_SCALE },
        )
        val firstRaw = first.points.map { it.factorRaw!! }.toIntArray()
        val second = calculate(recoveredPetrol(), recoveredGas(), firstRaw)

        assertTrue(second.points[0].factorRaw!! > first.points[0].factorRaw!!)
        assertTrue(second.points[7].factorRaw!! < first.points[7].factorRaw!!)
        assertEquals(first.points[13].factorRaw, second.points[13].factorRaw)
        assertTrue(second.points.all { point ->
            val old = firstRaw[point.index]
            val delta = kotlin.math.abs(point.factorRaw!! - old) / old.toDouble()
            delta <= AutoMatchV5Engine.MAX_STEP_RATIO + 1.0 / KFactorProtocol.Q14_SCALE
        })
    }

    @Test
    fun `erro abaixo de um por cento fica na zona morta`() {
        val petrol = recoveredPetrol()
        val gas = gasFromTimeScale(petrol, 1.005)
        val previous = IntArray(KFactorProtocol.POINT_COUNT) { 0x5000 }

        val result = calculate(petrol, gas, previous)

        assertArrayEquals(previous, result.points.map { it.factorRaw!! }.toIntArray())
        assertTrue(result.points.all { it.stepRatio == 0.0 })
    }

    @Test
    fun `erro grande e limitado a cinco por cento por execucao`() {
        val petrol = recoveredPetrol()
        val gas = gasFromTimeScale(petrol, 1.30)
        val previous = IntArray(KFactorProtocol.POINT_COUNT) { KFactorProtocol.Q14_SCALE }

        val result = calculate(petrol, gas, previous)

        assertTrue(result.complete)
        assertTrue(result.points.any { kotlin.math.abs(it.stepRatio ?: 0.0) == AutoMatchV5Engine.MAX_STEP_RATIO })
        result.points.forEach { point ->
            val delta = kotlin.math.abs(point.factorRaw!! - point.currentRaw) / point.currentRaw.toDouble()
            assertTrue(delta <= AutoMatchV5Engine.MAX_STEP_RATIO + 1.0 / KFactorProtocol.Q14_SCALE)
        }
    }

    @Test
    fun `curvas sem bandas comuns retornam resultado incompleto e auditavel`() {
        val petrol = recoveredPetrol()
        val gas = IntArray(KFactorProtocol.POINT_COUNT) { it }
        val result = calculate(
            petrol,
            gas,
            IntArray(KFactorProtocol.POINT_COUNT) { KFactorProtocol.Q14_SCALE },
        )

        assertFalse(result.complete)
        assertNull(result.firstCalculatedIndex)
        assertNull(result.lastCalculatedIndex)
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.points.all { it.origin == AutoMatchPointOrigin.UNAVAILABLE })
        assertTrue(result.points.all { it.factorRaw == null })
    }

    @Test
    fun `contrato rejeita dimensoes bandas eixo e MUL invalidos`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AutoMatchCurve30(IntArray(29) { it + 1 }, IntArray(29))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AutoMatchCurve30(IntArray(30) { 100 }, IntArray(30))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            calculate(recoveredPetrol(), recoveredGas(), IntArray(29))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AutoMatchV5Engine.calculate(
                curve(recoveredPetrol()),
                curve(recoveredGas()),
                pressureBands.copyOf(17),
                IntArray(30) { 0x4000 },
            )
        }
    }

    private fun calculate(
        petrolMap: IntArray,
        gasMap: IntArray,
        previous: IntArray,
    ): AutoMatchV5Result = AutoMatchV5Engine.calculate(
        petrol = curve(petrolMap),
        gas = curve(gasMap),
        pressureBandsRaw = pressureBands,
        previousFactorsRaw = previous,
    )

    private fun curve(map: IntArray): AutoMatchCurve30 = AutoMatchCurve30(
        axisTimeRaw = KFactorProtocol.OBSERVED_PETROL_AXIS_MS
            .map { (it * KFactorProtocol.AXIS_COUNTS_PER_MS).toInt() }
            .toIntArray(),
        mapRaw = map,
    )

    private fun gasFromTimeScale(petrol: IntArray, scale: Double): IntArray {
        val axis = KFactorProtocol.OBSERVED_PETROL_AXIS_MS
        return IntArray(KFactorProtocol.POINT_COUNT) { index ->
            interpolate(axis[index] / scale, axis, petrol).toInt()
        }
    }

    private fun interpolate(x: Double, axis: DoubleArray, values: IntArray): Double {
        if (x <= axis.first()) return values.first().toDouble()
        if (x >= axis.last()) return values.last().toDouble()
        val upper = axis.indexOfFirst { it >= x }.coerceAtLeast(1)
        val lower = upper - 1
        val fraction = (x - axis[lower]) / (axis[upper] - axis[lower])
        return values[lower] + (values[upper] - values[lower]) * fraction
    }

    private fun recoveredPetrol() = intArrayOf(
        10, 45, 113, 182, 251, 319, 387, 456, 530, 590,
        645, 711, 734, 760, 781, 802, 813, 824, 837, 853,
        910, 961, 1012, 1063, 1114, 1165, 1216, 1267, 1369, 1471,
    )

    private fun recoveredGas() = intArrayOf(
        78, 126, 174, 222, 270, 310, 398, 493, 535, 577,
        648, 692, 732, 764, 790, 800, 811, 822, 832, 853,
        902, 974, 1046, 1118, 1190, 1262, 1334, 1406, 1550, 1694,
    )
}
