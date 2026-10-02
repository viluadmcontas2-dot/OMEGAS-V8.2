package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdConditionEngineTest {
    private fun frame(index: Int, rpm: Double = 1_850.0, petrolMs: Double = 4.5) = ObdConditionEngine.Frame(
        observedAtMs = 1_000L + index * 300L,
        fuel = "GNV",
        cellKey = "1850.0:4.5",
        mp48Rpm = rpm,
        petrolInjectionMs = petrolMs,
        obdRpm = rpm - 8.0,
        stft = -15.0 + index,
        ltft = -2.0,
        speedKmh = 60.0,
        coolantC = 89.0,
    )

    @Test fun `seis frames estaveis viram uma unica condicao`() {
        val engine = ObdConditionEngine()
        repeat(5) { assertTrue(engine.accept(frame(it)) is ObdConditionEngine.Result.Forming) }
        val result = engine.accept(frame(5)) as ObdConditionEngine.Result.Accepted

        assertEquals(6, result.condition.frameCount)
        assertEquals("GNV", result.condition.fuel)
        assertEquals("1850.0:4.5", result.condition.cellKey)
        assertEquals(-12.5, result.condition.stft, 0.001)
    }

    @Test fun `mudanca de celula descarta janela em vez de misturar evidencia`() {
        val engine = ObdConditionEngine()
        repeat(3) { engine.accept(frame(it)) }
        val moved = frame(3).copy(cellKey = "2500.0:6.0")
        val result = engine.accept(moved) as ObdConditionEngine.Result.Discarded

        assertEquals("mudança de célula física", result.reason)
        assertTrue(engine.accept(moved.copy(observedAtMs = 2_300L)) is ObdConditionEngine.Result.Forming)
    }

    @Test fun `oscilacao de rpm descarta condicao mesmo com frames individuais validos`() {
        val engine = ObdConditionEngine()
        repeat(5) { engine.accept(frame(it, rpm = 1_850.0)) }
        val result = engine.accept(frame(5, rpm = 2_010.0)) as ObdConditionEngine.Result.Discarded

        assertEquals("RPM variando na janela", result.reason)
    }

    @Test fun `lacuna nao permite reutilizar janela parcial`() {
        val engine = ObdConditionEngine()
        repeat(3) { engine.accept(frame(it)) }
        val late = frame(3).copy(observedAtMs = 5_000L)
        val result = engine.accept(late) as ObdConditionEngine.Result.Discarded

        assertEquals("lacuna entre leituras OBD×MP48", result.reason)
    }
}
