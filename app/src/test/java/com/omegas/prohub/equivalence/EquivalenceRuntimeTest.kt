package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EquivalenceRuntimeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun phases() = EquivalencePhases(null) { 0L }
    private val none: (List<Double>) -> DoubleArray? = { null }

    private fun feed(rt: EquivalenceRuntime) =
        EquivalenceReplaySupport.frames(REFERENCE).forEach { rt.onFrame(it.t, it.fuel, it.rpm, it.map, it.petrolMs, 1L) }

    @Test
    fun `trocar a referencia nao perde dados do livro so reajusta e recomeca a prova`() {
        val ledger = EquivalenceReplaySupport.ledger(REFERENCE, curveSeq = 2183)
        val rt = EquivalenceRuntime(null)
        val phases = phases()
        feed(rt)
        val snap2183 = EquivalenceReplaySupport.snapshot(REFERENCE, 2183)
        val acq95 = EquivalenceReplaySupport.acquisition(REFERENCE, 95)
        val acq2183 = EquivalenceReplaySupport.acquisition(REFERENCE, 2183)
        val firstReference = rt.freeze(acq95, phases)
        val r1 = rt.evaluate(ledger, phases, snap2183, acq2183, true, none)!!
        val before = EquivalenceReplaySupport.curve(REFERENCE, 2183).second
        // ponto 0 (0,5 ms) nunca tem leitura de condução: a prova dele fica aberta durante o teste
        rt.onCurveWritten(before, before.copyOf().also { it[0] += 400 }, phases)
        val nPetrol = ledger.petrolObservations().size
        val nGas = ledger.gasObservations().size
        rt.freeze(acq2183, phases)
        val r2 = rt.evaluate(ledger, phases, snap2183, acq2183, true, none)!!
        assertEquals(nPetrol, ledger.petrolObservations().size)
        assertEquals(nGas, ledger.gasObservations().size)
        assertEquals(r1.ownPetrol.cells.map { it.samples }, r2.ownPetrol.cells.map { it.samples })
        assertEquals(PointState.EM_PROVA, r2.points[0].state)
        assertEquals(0L, phases.json().getJSONArray("proofs").getJSONObject(0).getLong("onlineMs"))
        // Independência do Refino: trocar a referência nativa não reescreve a curva própria.
        assertEquals(r1.ownPetrol.cells.map { it.divergence }, r2.ownPetrol.cells.map { it.divergence })
        assertNotEquals(firstReference.id, rt.references.current()?.id)
    }

    @Test
    fun `json do resultado traz as chaves do contrato e a deriva da ECU`() {
        val ledger = EquivalenceReplaySupport.ledger(REFERENCE, curveSeq = 2183)
        val rt = EquivalenceRuntime(null)
        val phases = phases()
        feed(rt)
        val acq95 = EquivalenceReplaySupport.acquisition(REFERENCE, 95)
        val acq2183 = EquivalenceReplaySupport.acquisition(REFERENCE, 2183)
        rt.freeze(acq95, phases)
        rt.evaluate(ledger, phases, EquivalenceReplaySupport.snapshot(REFERENCE, 2183), acq2183, true, none)!!
        val json = JSONObject(rt.json(acq2183).toString())
        assertTrue(json.getBoolean("ok"))
        assertTrue(json.getBoolean("available"))
        assertEquals(EquivalenceJson.FORMAT, json.getString("format"))
        assertTrue(json.has("index"))
        assertTrue(json.has("coverage"))
        assertEquals(false, json.getBoolean("automatic"))
        val reference = json.getJSONObject("reference")
        assertTrue(reference.getBoolean("frozen"))
        assertTrue(reference.getBoolean("canFreeze"))
        assertTrue(reference.getString("id").startsWith("REF-"))
        assertTrue(reference.getDouble("ecuDrift") > 0.10)
        assertEquals(30, json.getJSONArray("points").length())
        assertEquals(OwnCurveFitter.GRID_CELLS, json.getJSONObject("ownPetrol").getJSONArray("cells").length())
        assertEquals(OwnCurveFitter.GRID_CELLS, json.getJSONObject("ownGas").getJSONArray("cells").length())
        assertTrue(json.getJSONObject("nextAction").getString("kind") in NextActionKind.values().map { it.name })
        val point = json.getJSONArray("points").getJSONObject(12)
        listOf("index", "axisMs", "state", "mixture", "usage", "samples", "sources").forEach { assertTrue(it, point.has(it)) }
    }

    @Test
    fun `sem Curva K lida o resultado diz por que`() {
        val rt = EquivalenceRuntime(null)
        assertNull(rt.evaluate(EquivalenceReplaySupport.ledger(REFERENCE, curveSeq = 2183), phases(), null, null, true, none))
        val json = rt.json(null)
        assertEquals(false, json.getBoolean("available"))
        assertEquals("CURVA_K_NAO_LIDA", json.getString("reason"))
    }

    @Test
    fun `congelar sem aquisicao madura falha legivel e nao muda nada`() {
        val rt = EquivalenceRuntime(null)
        val phases = phases()
        val frozen = rt.freeze(EquivalenceReplaySupport.acquisition(REFERENCE, 95), phases)
        val e = assertThrows(IllegalStateException::class.java) { rt.freeze(EquivalenceReplaySupport.acquisition(REFERENCE, 962), phases) }
        assertEquals("AQUISICAO_IMATURA", e.message)
        assertThrows(IllegalStateException::class.java) { rt.freeze(null, phases) }
        assertEquals(frozen, rt.references.current())
    }

    @Test
    fun `reset de GNV interrompe as provas abertas exceto a gravacao que elas provam`() {
        val rt = EquivalenceRuntime(null)
        val phases = phases()
        rt.onCurveWritten(IntArray(30) { 16384 }, IntArray(30) { 16384 }.also { it[3] = 16000 }, phases)
        assertEquals(1, phases.openProofCount())
        rt.onGasReset("CURVA_K_GRAVADA", phases)
        assertEquals(1, phases.openProofCount())
        rt.onGasReset("AUTOMATCH_NATIVO", phases)
        assertEquals(0, phases.openProofCount())
    }

    @Test
    fun `Referencia congelada sobrevive ao reinicio e o Desfazer volta a anterior`() {
        val phases = phases()
        val first = EquivalenceRuntime(tmp.root)
        val a = first.freeze(EquivalenceReplaySupport.acquisition(REFERENCE, 95), phases)
        assertEquals(a, EquivalenceRuntime(tmp.root).references.current())
        val b = first.freeze(EquivalenceReplaySupport.acquisition(REFERENCE, 2183), phases)
        assertNotEquals(a.id + a.points.size, b.id + b.points.size)
        assertEquals(a, first.restorePreviousReference(phases))
        assertEquals(b, first.references.previous())
    }
}
