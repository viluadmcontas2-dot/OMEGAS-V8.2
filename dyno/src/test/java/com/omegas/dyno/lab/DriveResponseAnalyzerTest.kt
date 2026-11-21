package com.omegas.dyno.lab

import com.omegas.dyno.lab.DriveResponseAnalyzer.Frame
import org.junit.Assert.*
import org.junit.Test

class DriveResponseAnalyzerTest {
    private val analyzer = DriveResponseAnalyzer()

    @Test fun missingData() {
        assertUnavailable(emptyList())
        assertUnavailable(recording().map { it.copy(tpsPct = null) })
        assertUnavailable(recording().map { it.copy(mapKpa = null) })
        assertUnavailable(recording().map { it.copy(speedKmh = null) })
    }

    @Test fun staleFuel() {
        assertUnavailable(recording { it.copy(fuelAgeMs = 1501) })
    }

    @Test fun coldAndUnknownTemperature() {
        assertUnavailable(recording { it.copy(ectC = 69.9) })
        assertUnavailable(recording { it.copy(ectC = null) })
    }

    @Test fun gapAfterResponseInvalidatesEvent() {
        assertUnavailable(recording().filter { it.tMs % 3000 !in 800L..1200L })
    }

    @Test fun unmatchedOperatingConditionsAndInsufficientPairs() {
        assertUnavailable(recording(gasCount = 4))
        assertUnavailable(recording { it.copy(mapKpa = 60.0) })
        assertUnavailable(recording { it.copy(tpsPct = if (it.tpsPct == 20.0) 20.0 else 40.0) })
        assertUnavailable(recording { it.copy(rpm = it.rpm + 500, speedKmh = (it.rpm + 500) / 50) })
    }

    @Test fun pairedLagDetectsSlowerGnvAtInclusiveTemperatureAndFuelAgeLimits() {
        val result = analyzer.analyze(recording())
        assertEquals(5, result.eventsMatched)
        assertEquals(400.0, result.lagMs!!, 0.0)
        assertEquals("GNV com mais atraso", result.comparison)
        assertNull(result.curveKAdvice)
    }

    @Test fun pedalStepAndRpmResponseUseTheirExactThresholds() {
        fun capture(step: Double, delay: Long) = recording().map {
            val offset = it.tMs % 3000
            it.copy(
                tMs = it.tMs / 3000 * 3000 + if (offset == 400L) delay else offset,
                tpsPct = if (offset < 400) 20.0 else 20.0 + step,
                rpm = if (offset < 600) 2050.0 else 2170.0,
                speedKmh = if (offset < 600) 41.0 else 43.4,
            )
        }
        val result = analyzer.analyze(capture(5.0, 700).filter { it.tMs % 3000 != 600L })
        assertEquals(5, result.eventsMatched)
        assertNull(result.lagMs)
        assertEquals("Rotação sem resposta nas duas amostras", result.comparison)
        assertUnavailable(capture(4.9, 500))
        assertUnavailable(capture(5.0, 701).filter { it.tMs % 3000 != 600L })
    }

    @Test fun gearShiftInvalidatesEventEvenAfterRpmResponse() {
        assertUnavailable(recording {
            if (it.tMs % 3000 >= 800) it.copy(speedKmh = it.speedKmh!! * 1.12) else it
        })
    }

    @Test fun roughnessCapturesHesitationAndSuddenRecovery() {
        val result = analyzer.analyze(recording {
            val rpm = when (it.tMs % 3000) {
                800L -> it.rpm - 180
                1000L -> it.rpm + 180
                else -> it.rpm
            }
            it.copy(rpm = rpm, speedKmh = rpm / 50)
        })
        assertTrue(result.roughness!! > analyzer.analyze(recording()).roughness!!)
    }

    @Test fun adviceRequiresConsistentTrimsAndCompleteness() {
        assertNotNull(analyzer.analyze(recording { it.copy(stft = 9.0, ltft = 4.0) }).curveKAdvice)
        assertNull(analyzer.analyze(recording { it.copy(stft = 9.0, ltft = -4.0) }).curveKAdvice)
        assertNull(analyzer.analyze(recording { it.copy(stft = 9.0, ltft = null) }).curveKAdvice)
        val sparse = recording { it.copy(stft = 9.0, ltft = 4.0) }
            .filter { it.tMs % 3000 in setOf(0L, 400L, 1000L, 1600L, 2000L) }
        val result = analyzer.analyze(sparse)
        assertEquals(5, result.eventsMatched)
        assertNull(result.curveKAdvice)
    }

    @Test fun countsUnresponsiveEventsWithoutInventingLag() {
        val result = analyzer.analyze(recording { it.copy(rpm = 2050.0, speedKmh = 41.0) })
        assertEquals(5, result.eventsMatched)
        assertNull(result.lagMs)
        assertEquals("GNV com mais hesitações", result.comparison)
    }

    private fun assertUnavailable(frames: List<Frame>) {
        val result = analyzer.analyze(frames)
        assertEquals(0, result.eventsMatched)
        assertNull(result.lagMs)
        assertNull(result.roughness)
        assertNull(result.confidence)
        assertNull(result.curveKAdvice)
    }

    private fun recording(gasCount: Int = 5, gnv: (Frame) -> Frame = { it }): List<Frame> =
        (0 until gasCount + 5).flatMap { index ->
            val gas = index < gasCount
            (0L..2200L step 200L).map { offset ->
                val response = if (gas) 400L else 600L
                val rpm = 2050.0 + if (offset < response) 0.0 else 140 + (offset - response) * 0.2
                val frame = Frame(
                    index * 3000L + offset, if (gas) "GASOLINA" else "GNV", 1500,
                    rpm, if (offset == 0L) 20.0 else 30.0, 45.0, 70.0, rpm / 50, 0.0, 0.0,
                )
                if (gas) frame else gnv(frame)
            }
        }
}
