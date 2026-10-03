package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.RealSessionReplaySupport
import com.omegas.prohub.autocal.StallWatch
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExperienceMeterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** [seconds] de condução a um quadro a cada [stepMs], rpm dado por [rpmAt] (índice do quadro). */
    private fun drive(
        m: ExperienceMeter, fuel: String, map: Double, start: Long, seconds: Int, stepMs: Long,
        rpmAt: (Int, Long) -> Double,
    ): Long {
        var t = start
        var i = 0
        while (t < start + seconds * 1_000L) {
            m.accept(t, fuel, rpmAt(i, t - start), map, 5.0)
            t += stepMs
            i++
        }
        return t
    }

    private fun alternating(base: Double, amplitude: Double): (Int, Long) -> Double =
        { i, _ -> base + if (i % 2 == 0) amplitude else -amplitude }

    private fun event(kind: String, map: Double) =
        JSONObject().put("kind", kind).put("mapBar", map).put("at", 1L)

    @Test
    fun `tendencia de RPM nao e tremor`() {
        val m = ExperienceMeter(null)
        val t = drive(m, "GNV", 0.60, 0, 60, 300) { _, dt -> 2000.0 + 100.0 * (dt / 1000.0) }
        drive(m, "GASOLINA", 0.60, t + 5_000, 60, 300, alternating(2000.0, 20.0))
        val ratio = m.reading().roughnessRatio(0.60)
        assertNotNull(ratio)
        assertTrue("razão $ratio", ratio!! <= 0.05)
    }

    @Test
    fun `GNV pobre escorrega de ms mas casa com a gasolina pelo MAP`() {
        val m = ExperienceMeter(null)
        val t = drive(m, "GASOLINA", 0.60, 0, 60, 300, alternating(2000.0, 20.0))
        drive(m, "GNV", 0.60, t + 5_000, 60, 300, alternating(2000.0, 40.0))
        assertEquals(2.0, m.reading().roughnessRatio(0.60)!!, 0.05)
        // célula do "ms cru" do GNV (onde a gasolina pediria 6,9 ms): não recebe nada
        assertNull(m.reading().roughnessRatio(0.70))
    }

    @Test
    fun `quase-apagao por hora compara GNV com gasolina no mesmo MAP`() {
        val m = ExperienceMeter(null)
        val t = drive(m, "GASOLINA", 0.60, 0, 400, 1_000) { _, _ -> 2000.0 }
        drive(m, "GNV", 0.60, t + 5_000, 400, 1_000) { _, _ -> 2000.0 }
        m.onStall(event(StallWatch.KIND_NEAR, 0.60))
        m.onStall(event(StallWatch.KIND_NEAR, 0.60))
        assertEquals(5.0, m.reading().nearStallRatio(0.60)!!, 1e-9)
        assertNull(m.reading().nearStallRatio(0.90))
    }

    @Test
    fun `resetGas apaga so o GNV e persiste no arquivo`() {
        val file = tmp.newFile("experience.json")
        val m = ExperienceMeter(file)
        val t = drive(m, "GASOLINA", 0.60, 0, 60, 300, alternating(2000.0, 20.0))
        drive(m, "GNV", 0.60, t + 5_000, 60, 300, alternating(2000.0, 40.0))
        m.flush()
        assertNotNull(ExperienceMeter(file).reading().roughnessRatio(0.60))
        m.resetGas("TESTE")
        assertNull(m.reading().roughnessRatio(0.60))
        m.flush()
        val reloaded = ExperienceMeter(file).reading()
        assertNull(reloaded.roughnessRatio(0.60))
    }

    @Test
    fun `arquivo corrompido abre vazio`() {
        val file = tmp.newFile("experience.json")
        file.writeText("{nao json")
        assertNull(ExperienceMeter(file).reading().roughnessRatio(0.60))
    }

    @Test
    fun `replay real da sessao REFERENCE nao quebra e mantem as razoes coerentes`() {
        val m = ExperienceMeter(null)
        RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(RealSessionReplaySupport.REFERENCE)).forEach {
            m.accept(it.t, it.fuel, it.rpm, it.map, it.petrolMs)
        }
        assertTrue(m.revision() > 0)
        val reading = m.reading()
        var cell = 10
        while (cell <= 55) {
            val bar = 0.10 + cell * 0.02
            reading.roughnessRatio(bar)?.let { assertTrue(it >= 0.0) }
            reading.nearStallRatio(bar)?.let { assertTrue(it > 0.0) }
            cell++
        }
    }
}
