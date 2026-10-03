package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.sqrt

class OwnCurveFitterTest {
    @Test
    fun `sem leitura a curva propria e o prior puro`() {
        val ref = EquivalenceReplaySupport.reference(REFERENCE, 95)
        val c = OwnCurveFitter.fit(emptyList(), Fuel.GASOLINA, ref)
        assertEquals(OwnCurveFitter.GRID_CELLS, c.cells.size)
        val known = c.cells.filter { it.petrolMs != null }
        assertTrue(known.isNotEmpty())
        known.forEach {
            assertEquals(CellSource.REFERENCE, it.source)
            assertEquals(0, it.samples)
            assertEquals(OwnCurveFitter.priorAt(ref, it.mapBar)!!, it.petrolMs!!, 1e-9)
        }
    }

    @Test
    fun `celulas vizinhas maduras dominam e a discordancia com a referencia fica visivel`() {
        val ref = EquivalenceReplaySupport.reference(REFERENCE, 95)
        val obs = ArrayList<EquivalenceLedger.Obs>()
        for (cell in 18..22) {
            val center = OwnCurveFitter.center(cell)
            repeat(40) { i ->
                obs += EquivalenceLedger.Obs(i.toLong(), 2000.0, center - 0.01 + 0.0001 * (i % 5), 1.05 * OwnCurveFitter.priorAt(ref, center)!!)
            }
        }
        val c = OwnCurveFitter.fit(obs, Fuel.GASOLINA, ref)
        val cell = c.cells[20]
        assertEquals(CellSource.OWN, cell.source)
        assertEquals(40, cell.samples)
        assertEquals(0.05, cell.divergence!!, 0.01)
        assertNull(c.cells[10].divergence)
    }

    @Test
    fun `sem referencia so as celulas com leitura e o vao entre elas tem valor`() {
        val obs = ArrayList<EquivalenceLedger.Obs>()
        for (cell in listOf(20, 24)) {
            repeat(5) { i -> obs += EquivalenceLedger.Obs(i.toLong(), 2000.0, OwnCurveFitter.center(cell), 3.0 + 0.1 * cell) }
        }
        val c = OwnCurveFitter.fit(obs, Fuel.GNV, null)
        assertNull(c.cells[19].petrolMs)
        assertNotNull(c.cells[22].petrolMs)
        assertNull(c.cells[25].petrolMs)
        assertEquals(CellSource.OWN, c.cells[20].source)
    }

    /** Gate §1.6b: a Curva Própria madura tem de prever leituras não usadas no ajuste melhor que a Referência. */
    private fun crossValidate(refSeq: Int): Triple<Int, Double, Double> {
        val ref = EquivalenceReplaySupport.reference(REFERENCE, refSeq)
        val petrol = EquivalenceReplaySupport.ledger(REFERENCE).petrolObservations().filter { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM }
        val errOwn = ArrayList<Double>()
        val errRef = ArrayList<Double>()
        for (parity in 0..1) {
            val train = petrol.filterIndexed { i, _ -> (i / 40) % 2 == parity }
            val test = petrol.filterIndexed { i, _ -> (i / 40) % 2 != parity }
            val own = OwnCurveFitter.fit(train, Fuel.GASOLINA, ref)
            val byCell = HashMap<Int, ArrayList<Double>>()
            for (o in test) {
                val j = OwnCurveFitter.cellOf(o.map) ?: continue
                byCell.getOrPut(j) { ArrayList() }.add(ln(o.petrolMs))
            }
            for ((j, values) in byCell) {
                val cell = own.cells[j]
                val mine = cell.petrolMs ?: continue
                if (cell.source != CellSource.OWN || values.size < 3) continue
                val prior = OwnCurveFitter.priorAt(ref, OwnCurveFitter.center(j)) ?: continue
                val target = values.sorted()[values.size / 2]
                errOwn += ln(mine) - target
                errRef += ln(prior) - target
            }
        }
        fun rms(v: List<Double>) = sqrt(v.sumOf { it * it } / v.size)
        return Triple(errOwn.size, rms(errOwn), rms(errRef))
    }

    @Test
    fun `curva propria madura preve gasolina escondida melhor que a referencia congelada velha`() {
        val (judged, rmsOwn, rmsRef) = crossValidate(95)
        println("CV_REFERENCE_SEQ95 judged=$judged rmsOwn=$rmsOwn rmsRef=$rmsRef")
        assertTrue("células julgadas: $judged", judged >= 8)
        assertTrue("própria $rmsOwn ≥ referência $rmsRef", rmsOwn < rmsRef)
    }

    @Test
    fun `com a referencia ja relearnda a propria empata - nao piora de forma material`() {
        val (judged, rmsOwn, rmsRef) = crossValidate(2183)
        println("CV_REFERENCE_SEQ2183 judged=$judged rmsOwn=$rmsOwn rmsRef=$rmsRef")
        assertTrue(judged >= 8)
        assertTrue("própria $rmsOwn muito pior que a referência $rmsRef", rmsOwn <= rmsRef * 1.15)
    }

    @Test
    fun `trocar a referencia so reajusta - amostras por celula identicas`() {
        val obs = EquivalenceReplaySupport.ledger(REFERENCE).petrolObservations()
        val a = OwnCurveFitter.fit(obs, Fuel.GASOLINA, EquivalenceReplaySupport.reference(REFERENCE, 95))
        val b = OwnCurveFitter.fit(obs, Fuel.GASOLINA, EquivalenceReplaySupport.reference(REFERENCE, 2183))
        assertEquals(a.cells.map { it.samples }, b.cells.map { it.samples })
        assertEquals(a.cells.map { it.dispersion }, b.cells.map { it.dispersion })
    }

    @Test
    fun `mapFor devolve o MAP da primeira travessia`() {
        val c = OwnCurve(
            Fuel.GASOLINA,
            listOf(
                OwnCell(0.33, 4.0, 5, 0.0, CellSource.OWN, null),
                OwnCell(0.35, 5.0, 5, 0.0, CellSource.OWN, null),
            ),
        )
        assertEquals(0.34, OwnCurveFitter.mapFor(c, 4.5)!!, 1e-12)
        assertNull(OwnCurveFitter.mapFor(c, 6.0))
    }
}
