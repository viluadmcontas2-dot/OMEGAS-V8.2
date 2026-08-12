package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdLearningGateTest {
    private fun qualifiedInput() = ObdLearningGate.Input(
        mp48Present = true,
        mp48Fuel = "GNV",
        manualFuel = "GASOLINA",
        mp48Rpm = 1_850.0,
        petrolInjectionMs = 4.5,
        mp48ObservedAtMs = 1_000L,
        obdRpm = 1_840.0,
        obdObservedAtMs = 1_100L,
        stftObservedAtMs = 1_100L,
        obdRpmObservedAtMs = 1_200L,
        closedLoopObservedAtMs = 900L,
        stftStartedAtMs = 980L,
        obdRpmStartedAtMs = 1_080L,
        closedLoopStartedAtMs = 800L,
        coolantObservedAtMs = 900L,
        stftMp48ObservedAtMs = 1_000L,
        rpmMp48ObservedAtMs = 1_150L,
        stft = -15.0,
        coolantC = 88.0,
        closedLoop = true,
        maxRpmDifference = 250.0,
        maxTimeSkewMs = 250L,
    )

    @Test fun `MP48 confirma combustivel e vence selecao manual`() {
        val state = ObdLearningGate.fuelState(true, "GNV", "GASOLINA")
        assertEquals("GNV", state.fuel)
        assertEquals(ObdLearningGate.FuelSource.MP48_CONFIRMED, state.source)
        assertTrue(state.canQualifyMap)
    }

    @Test fun `selecao manual organiza painel mas nao libera aprendizado sem MP48`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(mp48Present = false, mp48Fuel = null, manualFuel = "GNV"),
            minimumCoolantC = 70.0,
        )
        assertFalse(result.accepted)
        assertEquals("GNV", result.fuelState.fuel)
        assertEquals(ObdLearningGate.FuelSource.MANUAL_OPERATOR, result.fuelState.source)
        assertEquals(ObdLearningGate.ReasonCode.MP48_UNAVAILABLE, result.reasonCode)
        assertTrue(result.resetCondition)
    }

    @Test fun `sem selecao e sem MP48 permanece desconhecido`() {
        val state = ObdLearningGate.fuelState(false, null, "NÃO SEI")
        assertNull(state.fuel)
        assertEquals(ObdLearningGate.FuelSource.UNKNOWN, state.source)
        assertFalse(state.canQualifyMap)
    }

    @Test fun `sinal do GNV e direto e gasolina apenas gera alerta`() {
        assertEquals("TENDÊNCIA RICA · reduzir combustível gradualmente", ObdLearningGate.directGnvSignal(-15.0, 3.0))
        assertEquals("GASOLINA_FORA_DO_NEUTRO", ObdLearningGate.gasolineAdvisory(12.0, 3.0))
        assertNull(ObdLearningGate.gasolineAdvisory(1.0, 3.0))
    }

    @Test fun `rpm divergente rejeita mesmo com MP48 presente`() {
        val result = ObdLearningGate.evaluate(qualifiedInput().copy(obdRpm = 2_200.0), 70.0)
        assertFalse(result.accepted)
        assertEquals(ObdLearningGate.ReasonCode.RPM_DIVERGENCE, result.reasonCode)
        assertTrue(result.resetCondition)
    }

    @Test fun `cada PID pode ser pareado com a propria amostra MP48`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(
                closedLoopStartedAtMs = 700L,
                closedLoopObservedAtMs = 820L,
                stftStartedAtMs = 900L,
                stftObservedAtMs = 1_020L,
                stftMp48ObservedAtMs = 1_000L,
                obdRpmStartedAtMs = 1_080L,
                obdRpmObservedAtMs = 1_200L,
                rpmMp48ObservedAtMs = 1_170L,
                coolantObservedAtMs = 900L,
            ),
            70.0,
        )
        assertTrue(result.accepted)
        assertEquals(ObdLearningGate.ReasonCode.ACCEPTED, result.reasonCode)
        assertEquals(20L, result.metrics.stftMp48SkewMs)
        assertEquals(30L, result.metrics.rpmMp48SkewMs)
    }

    @Test fun `limite de 250 ms ainda qualifica o STFT pareado`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(
                stftObservedAtMs = 1_250L,
                stftStartedAtMs = 1_100L,
                stftMp48ObservedAtMs = 1_000L,
                obdRpmObservedAtMs = 1_250L,
                obdRpmStartedAtMs = 1_100L,
                rpmMp48ObservedAtMs = 1_000L,
                coolantObservedAtMs = 1_250L,
                closedLoopObservedAtMs = 1_250L,
            ),
            70.0,
        )
        assertTrue(result.accepted)
    }

    @Test fun `um milissegundo alem da janela rejeita o par STFT ECU`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(
                stftObservedAtMs = 1_251L,
                stftStartedAtMs = 1_100L,
                stftMp48ObservedAtMs = 1_000L,
            ),
            70.0,
        )
        assertFalse(result.accepted)
        assertEquals(ObdLearningGate.ReasonCode.STFT_MP48_SKEW, result.reasonCode)
        assertFalse(result.resetCondition)
    }

    @Test fun `latencia individual de STFT entra no limite`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(stftStartedAtMs = 700L, stftObservedAtMs = 1_100L),
            70.0,
        )
        assertFalse(result.accepted)
        assertEquals(ObdLearningGate.ReasonCode.STFT_READ_TOO_SLOW, result.reasonCode)
        assertFalse(result.resetCondition)
    }

    @Test fun `temperatura lenta vencida nao qualifica evidencia`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(
                stftObservedAtMs = 20_000L,
                stftStartedAtMs = 19_900L,
                stftMp48ObservedAtMs = 20_000L,
                obdRpmObservedAtMs = 20_000L,
                obdRpmStartedAtMs = 19_900L,
                rpmMp48ObservedAtMs = 20_000L,
                closedLoopObservedAtMs = 20_000L,
                coolantObservedAtMs = 4_999L,
                maxContextAgeMs = 15_000L,
            ),
            70.0,
        )
        assertFalse(result.accepted)
        assertEquals(ObdLearningGate.ReasonCode.COOLANT_CONTEXT_STALE, result.reasonCode)
    }

    @Test fun `motivo nativo inclui metricas para interface`() {
        val result = ObdLearningGate.evaluate(
            qualifiedInput().copy(stftStartedAtMs = 700L, stftObservedAtMs = 1_100L),
            70.0,
        )
        assertEquals(400L, result.metrics.stftReadMs)
        assertEquals(250L, result.metrics.configuredPairLimitMs)
    }
}
