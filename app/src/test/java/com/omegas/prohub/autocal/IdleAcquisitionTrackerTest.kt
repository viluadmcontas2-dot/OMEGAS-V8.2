package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 1: detecção pura de "a última aquisição GNV da banda foi na marcha lenta" (spec 2026-10-07). */
class IdleAcquisitionTrackerTest {
    private val tracker = IdleAcquisitionTracker()

    @Test
    fun `primeira leitura so estabelece baseline`() {
        val acquisitions = tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        assertTrue(acquisitions.isEmpty())
        assertTrue(tracker.idleBands().isEmpty())
    }

    @Test
    fun `contador subiu com telemetria na lenta marca a banda`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        val window = idleFrames(map = 410 / 1024.0, from = 1_100, count = 5)
        val acquisitions = tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), window)
        assertEquals(1, acquisitions.size)
        assertEquals(IdleAcquisitionTracker.Regime.LENTA, acquisitions.single().regime)
        val evidence = tracker.idleBands().getValue(4)
        assertEquals(5, evidence.frames)
        assertEquals(1.0, evidence.idleFraction, 1e-9)
        assertEquals(410 / 1024.0, evidence.mapBar, 1e-9)
    }

    @Test
    fun `aquisicao andando nao marca e remove marca antiga`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), idleFrames(410 / 1024.0, 1_100, 5))
        assertTrue(4 in tracker.idleBands())
        val driving = drivingFrames(410 / 1024.0, 3_100, 5)
        val acquisitions = tracker.observe(reading(counters(4 to 5), map(4 to 410), at = 5_000), driving)
        assertEquals(IdleAcquisitionTracker.Regime.ANDANDO, acquisitions.single().regime)
        assertFalse(4 in tracker.idleBands())
    }

    @Test
    fun `contador saturado em 10 com valor mudando conta como aquisicao`() {
        tracker.observe(reading(counters(5 to 10), map(5 to 500), time(5 to 1_800), at = 1_000), frames())
        val acquisitions = tracker.observe(
            reading(counters(5 to 10), map(5 to 500), time(5 to 1_850), at = 3_000),
            idleFrames(500 / 1024.0, 1_100, 4),
        )
        assertEquals(1, acquisitions.size)
        assertTrue(5 in tracker.idleBands())
    }

    @Test
    fun `contador saturado sem mudanca de valor nao e aquisicao`() {
        tracker.observe(reading(counters(5 to 10), map(5 to 500), time(5 to 1_800), at = 1_000), frames())
        val acquisitions = tracker.observe(
            reading(counters(5 to 10), map(5 to 500), time(5 to 1_800), at = 3_000),
            idleFrames(500 / 1024.0, 1_100, 4),
        )
        assertTrue(acquisitions.isEmpty())
    }

    @Test
    fun `banda com MAP 0 ou contador 0 e ignorada`() {
        tracker.observe(reading(counters(3 to 0, 6 to 2), map(3 to 400, 6 to 0), at = 1_000), frames())
        val acquisitions = tracker.observe(
            reading(counters(3 to 0, 6 to 3), map(3 to 420, 6 to 0), at = 3_000),
            idleFrames(400 / 1024.0, 1_100, 6),
        )
        assertTrue(acquisitions.isEmpty())
        assertTrue(tracker.idleBands().isEmpty())
    }

    @Test
    fun `queda de contador e rebaseline e limpa a marca`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), idleFrames(410 / 1024.0, 1_100, 5))
        assertTrue(4 in tracker.idleBands())
        val acquisitions = tracker.observe(reading(counters(4 to 1), map(4 to 410), at = 5_000), idleFrames(410 / 1024.0, 3_100, 5))
        assertTrue(acquisitions.isEmpty())
        assertFalse(4 in tracker.idleBands())
    }

    @Test
    fun `duas bandas no mesmo intervalo sao classificadas cada uma pela sua MAP`() {
        tracker.observe(reading(counters(2 to 1, 9 to 1), map(2 to 330, 9 to 700), at = 1_000), frames())
        val window = idleFrames(330 / 1024.0, 1_100, 4) + drivingFrames(700 / 1024.0, 1_600, 4)
        val acquisitions = tracker.observe(reading(counters(2 to 2, 9 to 2), map(2 to 330, 9 to 700), at = 3_000), window)
        assertEquals(2, acquisitions.size)
        assertEquals(IdleAcquisitionTracker.Regime.LENTA, acquisitions.first { it.band == 2 }.regime)
        assertEquals(IdleAcquisitionTracker.Regime.ANDANDO, acquisitions.first { it.band == 9 }.regime)
        assertEquals(setOf(2), tracker.idleBands().keys)
    }

    @Test
    fun `menos de tres quadros nao marca`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), idleFrames(410 / 1024.0, 1_100, 2))
        assertTrue(tracker.idleBands().isEmpty())
    }

    @Test
    fun `quadros de gasolina ou fora da MAP da banda nao contam`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        val petrol = idleFrames(410 / 1024.0, 1_100, 5).map { it.copy(fuel = "GASOLINA") }
        val farMap = idleFrames(410 / 1024.0 + 0.05, 1_700, 5)
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), petrol + farMap)
        assertTrue(tracker.idleBands().isEmpty())
    }

    @Test
    fun `bandas acima de 15 nao entram`() {
        tracker.observe(reading(counters(16 to 1), map(16 to 900), at = 1_000), frames())
        val acquisitions = tracker.observe(reading(counters(16 to 2), map(16 to 900), at = 3_000), idleFrames(900 / 1024.0, 1_100, 5))
        assertTrue(acquisitions.isEmpty())
    }

    @Test
    fun `janela de telemetria pedida e exatamente o intervalo entre as leituras`() {
        var asked: Pair<Long, Long>? = null
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000)) { from, to -> asked = from to to; emptyList() }
        assertNull(asked)
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000)) { from, to -> asked = from to to; emptyList() }
        assertEquals(1_000L to 3_000L, asked)
    }

    @Test
    fun `apagar zera o contador conhecido e limpa a marca e reset esquece tudo`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 1_000), frames())
        tracker.observe(reading(counters(4 to 4), map(4 to 410), at = 3_000), idleFrames(410 / 1024.0, 1_100, 5))
        tracker.markDeleted(listOf(4))
        assertTrue(tracker.idleBands().isEmpty())
        assertEquals(0, tracker.lastCounters()!![4])
        tracker.reset()
        assertNull(tracker.lastCounters())
    }

    @Test
    fun `R8 leitura fora de ordem nao substitui a linha de base`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), at = 3_000), frames())
        tracker.observe(reading(counters(4 to 9), map(4 to 410), at = 2_000), frames())
        assertEquals(3, tracker.lastCounters()!![4])
        assertEquals(3_000L, tracker.lastObservedAtElapsedMs())
    }

    @Test
    fun `R3 evidencia leva contador tempo e MAP da classificacao`() {
        tracker.observe(reading(counters(4 to 3), map(4 to 410), time(4 to 1_500), at = 1_000), frames())
        tracker.observe(reading(counters(4 to 4), map(4 to 410), time(4 to 1_520), at = 3_000), idleFrames(410 / 1024.0, 1_100, 5))
        val json = tracker.idleBands().getValue(4).toJson()
        assertEquals(4, json.getInt("counterAfter"))
        assertEquals(1_520, json.getInt("timeRaw"))
        assertEquals(410, json.getInt("mapRaw"))
    }

    // ---- apoio ----

    private fun frames(): (Long, Long) -> List<NativeAnchorTelemetryWindow.Frame> = { _, _ -> emptyList() }

    private fun windowOf(list: List<NativeAnchorTelemetryWindow.Frame>): (Long, Long) -> List<NativeAnchorTelemetryWindow.Frame> =
        { from, to -> list.filter { it.elapsedMs in from..to } }

    private fun idleFrames(map: Double, from: Long, count: Int) =
        List(count) { frame(from + it * 100L, rpm = 850, map = map) }

    private fun drivingFrames(map: Double, from: Long, count: Int) =
        List(count) { frame(from + it * 100L, rpm = 2_100, map = map) }

    private fun frame(at: Long, rpm: Int, map: Double, fuel: String = "GNV") = NativeAnchorTelemetryWindow.Frame(
        sequence = at, elapsedMs = at, rpm = rpm, mapBar = map, petrolMs = 3.0, fuel = fuel,
    )

    private fun IdleAcquisitionTracker.observe(
        reading: IdleAcquisitionTracker.Reading,
        list: List<NativeAnchorTelemetryWindow.Frame>,
    ) = observe(reading, windowOf(list))

    private fun counters(vararg values: Pair<Int, Int>) = IntArray(18).also { a -> values.forEach { (i, v) -> a[i] = v } }
    private fun map(vararg values: Pair<Int, Int>) = IntArray(18).also { a -> values.forEach { (i, v) -> a[i] = v } }
    private fun time(vararg values: Pair<Int, Int>) = IntArray(18).also { a -> values.forEach { (i, v) -> a[i] = v } }

    private fun reading(counters: IntArray, map: IntArray, time: IntArray = IntArray(18), at: Long) =
        IdleAcquisitionTracker.Reading(counters = counters, timeRaw = time, mapRaw = map, observedAtElapsedMs = at)
}
