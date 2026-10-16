package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Trava: salvar o aprendizado tem intervalo mínimo ([MotorLearningMemory.PERSIST_MIN_INTERVAL_MS]);
 * parada da conexão, fechamento e exportação salvam na hora. Relógio injetado: o teste não espera 30 s.
 */
class MotorLearningMemoryPersistIntervalTest {
    @get:Rule val temporary = TemporaryFolder()

    private var now = 1_700_000_000_000L

    private fun memory(file: File) = MotorLearningMemory(file, RingLog()) { now }

    private fun savedAt(file: File): Long = JSONObject(file.readText()).getLong("savedAt")

    private fun waitForChange(file: File, before: String, timeoutMs: Long = 5_000L): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (file.readText() == before && System.currentTimeMillis() < deadline) Thread.sleep(20L)
        return file.readText()
    }

    private fun firstSave(file: File): Pair<MotorLearningMemory, String> {
        val memory = memory(file)
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        memory.awaitPersistence()
        assertTrue(file.isFile && file.length() > 0L)
        return memory to file.readText()
    }

    @Test
    fun `segunda amostra dentro do intervalo nao regrava o arquivo ate alguem pedir`() {
        val file = temporary.newFile("intervalo.json").apply { delete() }
        val (memory, first) = firstSave(file)

        // Nova amostra 400 ms depois, relógio parado dentro dos 30 s: o worker espera o intervalo.
        memory.ingest(telemetry(at = 800L), accepted(sample("b", 800L)))
        Thread.sleep(400L)
        assertEquals("dentro do intervalo nada é regravado", first, file.readText())

        // Fechar salva o que estava pendente sem esperar o intervalo.
        memory.close()
        val closed = file.readText()
        assertNotEquals(first, closed)
        assertTrue(savedAt(file) > JSONObject(first).getLong("savedAt"))
        assertEquals(2, JSONObject(closed).getJSONArray("regions").getJSONObject(0).getInt("samples"))
    }

    @Test
    fun `com o intervalo vencido a amostra seguinte salva sozinha`() {
        val file = temporary.newFile("vencido.json").apply { delete() }
        val (memory, first) = firstSave(file)

        now += MotorLearningMemory.PERSIST_MIN_INTERVAL_MS + 1L
        memory.ingest(telemetry(at = 800L), accepted(sample("b", 800L)))
        val after = waitForChange(file, first)
        assertNotEquals("intervalo vencido: salva sem ninguém pedir", first, after)
        memory.close()
    }

    @Test
    fun `parar a conexao salva na hora sem esperar o intervalo`() {
        val file = temporary.newFile("parada.json").apply { delete() }
        val (memory, first) = firstSave(file)

        memory.ingest(telemetry(at = 800L), accepted(sample("b", 800L)))
        memory.endSession("USB_DISCONNECTED")
        val after = waitForChange(file, first)
        assertNotEquals("parada salva já, sem bloquear quem parou", first, after)
        memory.close()
    }

    @Test
    fun `exportar pede o salvamento do que estava pendente`() {
        val file = temporary.newFile("exportar.json").apply { delete() }
        val (memory, first) = firstSave(file)

        memory.ingest(telemetry(at = 800L), accepted(sample("b", 800L)))
        val exported = memory.export("test")
        assertEquals(2, exported.getJSONArray("regions").getJSONObject(0).getInt("samples"))
        val after = waitForChange(file, first)
        assertNotEquals("o que saiu exportado também foi para o disco", first, after)
        memory.close()
    }

    private fun accepted(sample: MotorSample) = SampleDecision.accepted(sample)

    private fun sample(
        id: String,
        at: Long,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
        petrolMs: Double = 4.0,
        quality: Double = 0.95,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = at,
        endedAtElapsedMs = at + 550L,
        fuel = fuel,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = if (fuel == Mp48Fuel.CNG) 65.0 else 0.0,
        quality = quality,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
    )

    private fun diagnostics() = SampleDiagnostics(
        frameCount = LearningTolerancePolicy().requiredFrames,
        durationMs = 550L,
        medianIntervalMs = 50L,
        waterCenterC = 80.0,
        minimumWaterC = 55,
        rpmCenterShift = 0.0,
        rpmCenterLimit = 62.5,
        rpmOscillation = 0.0,
        rpmOscillationLimit = 125.0,
        mapCenterShift = 0.0,
        mapCenterLimit = 0.025,
        mapOscillation = 0.0,
        mapOscillationLimit = 0.05,
        petrolCenterShift = 0.0,
        petrolCenterLimit = 0.24,
        petrolOscillationRatio = 0.0,
        petrolOscillationLimit = 0.15,
        pressureCenterShift = 0.0,
        pressureCenterLimit = 0.04,
        pressureOscillation = 0.0,
        pressureOscillationLimit = 0.08,
    )

    private fun telemetry(
        at: Long = 100L,
        rpm: Int = 2_500,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.0,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
