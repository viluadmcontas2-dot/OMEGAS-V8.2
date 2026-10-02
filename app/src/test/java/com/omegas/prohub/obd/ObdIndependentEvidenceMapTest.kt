package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdIndependentEvidenceMapTest {
    private fun observe(
        map: ObdIndependentEvidenceMap,
        fuel: String,
        stft: Double,
        rpm: Double = 2_000.0,
        load: Double = 40.0,
        closedLoop: Boolean = true,
        coolant: Double? = 90.0,
        now: Long = 1_000L,
    ) = map.observe(
        fuel = fuel,
        rpm = rpm,
        loadPct = load,
        stftPct = stft,
        ltftPct = 1.0,
        speedKmh = 60.0,
        coolantC = coolant,
        mapKpa = 52.0,
        mafGps = 11.5,
        throttlePct = 24.0,
        closedLoop = closedLoop,
        minimumCoolantC = 60.0,
        nowMs = now,
    )

    @Test fun `mapa usa rpm e carga OBD sem eixo de tempo de injecao`() {
        val map = ObdIndependentEvidenceMap { 2 }
        val result = observe(map, "GASOLINA", 2.0)
        val json = map.toJson()

        assertTrue(result.accepted)
        assertEquals("rpm", json.getJSONObject("axes").getString("x"))
        assertEquals("calculatedLoadPct", json.getJSONObject("axes").getString("y"))
        assertFalse(json.getBoolean("affectsLearning"))
        assertFalse(json.getBoolean("affectsCalibration"))
    }

    @Test fun `localizacao da celula live existe antes da decisao de coleta`() {
        val map = ObdIndependentEvidenceMap { 2 }
        val location = map.locate(2_000.0, 40.0)
        assertTrue(location.valid)
        assertEquals(5, location.row)
        assertEquals(6, location.column)
        assertEquals("5:6", location.key)
        assertEquals(2_000.0, location.rpmBin!!, 0.001)
        assertEquals(40.0, location.loadBin!!, 0.001)
    }

    @Test fun `amostra recusada preserva celula atual mas nao contamina mapa`() {
        val map = ObdIndependentEvidenceMap { 2 }
        val openLoop = observe(map, "GNV", -3.0, closedLoop = false)
        assertFalse(openLoop.accepted)
        assertEquals("FORA_CLOSED_LOOP", openLoop.reason)
        assertEquals("5:6", openLoop.key)
        assertEquals(0, map.toJson().getJSONObject("gnv").length())

        val cold = observe(map, "GNV", -3.0, coolant = 40.0)
        assertFalse(cold.accepted)
        assertEquals("MOTOR_FRIO", cold.reason)
        assertEquals("5:6", cold.key)
        assertEquals(0, map.toJson().getJSONObject("gnv").length())
    }

    @Test fun `carga invalida impede inclusive localizacao`() {
        val map = ObdIndependentEvidenceMap { 2 }
        val result = observe(map, "GNV", 0.0, load = 120.0)
        assertEquals("CARGA_OBD_INVALIDA", result.reason)
        assertEquals(null, result.key)
    }

    @Test fun `gasolina e gnv formam comparacao somente na mesma celula OBD`() {
        val map = ObdIndependentEvidenceMap { 2 }
        observe(map, "GASOLINA", 2.0, now = 1_000L)
        observe(map, "GASOLINA", 4.0, now = 1_200L)
        observe(map, "GNV", 8.0, now = 1_400L)
        observe(map, "GNV", 10.0, now = 1_600L)

        val validation = map.toJson().getJSONObject("validation")
        val cell = validation.getJSONObject("5:6")
        assertTrue(cell.getBoolean("comparisonReady"))
        assertTrue(cell.getBoolean("independentOfMp48Axes"))
        assertEquals(6.0, cell.getDouble("deltaStft"), 0.001)
    }

    @Test fun `persistencia preserva somente evidencia observacional`() {
        val original = ObdIndependentEvidenceMap { 1 }
        observe(original, "GNV", -3.5, now = 2_000L)
        val restored = ObdIndependentEvidenceMap { 1 }
        restored.load(original.persistenceJson())

        val gnv = restored.toJson().getJSONObject("gnv")
        assertTrue(gnv.has("5:6"))
        assertEquals(-3.5, gnv.getJSONObject("5:6").getJSONObject("stft").getDouble("mean"), 0.001)
    }
}
