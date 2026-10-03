package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UsageMeterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** T(MAP) = 10·MAP ms em todas as células da grade. */
    private fun linearCurve() = OwnCurve(
        Fuel.GASOLINA,
        (0 until OwnCurveFitter.GRID_CELLS).map {
            val center = OwnCurveFitter.center(it)
            OwnCell(center, 10.0 * center, 5, 0.0, CellSource.OWN, null)
        },
    )

    private fun axisMs(): List<Double> = EquivalenceReplaySupport.curve(REFERENCE, 95).first.map { it / 512.0 }

    private fun frames(m: UsageMeter, start: Long, count: Int, rpm: Double, map: Double, session: Long, fuel: String = "GNV"): Long {
        var t = start
        repeat(count) {
            m.accept(t, fuel, rpm, map, session)
            t += 500
        }
        return t
    }

    @Test
    fun `fracao do tempo de conducao por ponto pelo MAP equivalente`() {
        val m = UsageMeter(null)
        var t = frames(m, 0, 60, 2000.0, 0.50, 1)
        t = frames(m, t, 20, 2000.0, 0.70, 1)
        frames(m, t, 40, 850.0, 0.35, 1)
        val u = m.reading().byPoint(axisMs(), linearCurve())
        assertEquals(0.75, u[9], 0.01)
        assertEquals(0.25, u[13], 0.01)
        assertEquals(1.0, u.sum(), 1e-9)
    }

    @Test
    fun `corte de combustivel nao conta como conducao`() {
        val m = UsageMeter(null)
        frames(m, 0, 20, 2500.0, 0.20, 1, fuel = "CUTOFF")
        assertTrue(m.reading().cellMs.all { it == 0.0 })
        assertTrue(m.reading().byPoint(axisMs(), linearCurve()).all { it == 0.0 })
    }

    @Test
    fun `janela movel guarda so as ultimas 10 sessoes`() {
        val m = UsageMeter(null)
        frames(m, 0, 4, 2000.0, 0.30, 1)
        for (session in 2L..11L) frames(m, session * 100_000L, 4, 2000.0, 0.50, session)
        val cells = m.reading().cellMs
        assertEquals(0.0, cells[OwnCurveFitter.cellOf(0.30)!!], 0.0)
        assertTrue(cells[OwnCurveFitter.cellOf(0.50)!!] > 0.0)
    }

    @Test
    fun `dt longo nao infla o uso`() {
        val m = UsageMeter(null)
        m.accept(0, "GNV", 2000.0, 0.50, 1)
        m.accept(60_000, "GNV", 2000.0, 0.50, 1)
        assertEquals(1_000.0, m.reading().cellMs.sum(), 0.0)
    }

    @Test
    fun `persiste e rele igual`() {
        val file = tmp.newFile("usage.json")
        val m = UsageMeter(file)
        frames(m, 0, 30, 2000.0, 0.60, 1)
        m.flush()
        assertArrayEquals(m.reading().cellMs, UsageMeter(file).reading().cellMs, 0.0)
    }
}
