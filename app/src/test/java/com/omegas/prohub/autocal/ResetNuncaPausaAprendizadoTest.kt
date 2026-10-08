package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TRAVA PERMANENTE (regra 14 do AGENTS.md, dono 2026-10-08): reset-nunca-pausa-aprendizado.
 * Reset de gasolina/GNV/tudo nunca deixa o aprendizado pausado: AUTO_CAL_ENABLE termina em 1, confirmado por
 * readback, mesmo se a ECU pausar após o reset ou se o comando der timeout. Nenhum agente pode remover.
 */
class ResetNuncaPausaAprendizadoTest {
    private val enableRead = AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE)
    private val enableOn = hex("12 4A 01 01 5E")
    private val allFields = (
        listOf(
            AutoCalProtocol.NUM_BUF_UPD_PETR, AutoCalProtocol.PETR_INJ_TBUF,
            AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            AutoCalProtocol.NUM_BUF_UPD_GAS, AutoCalProtocol.PETR_INJ_TBUF_GAS,
            AutoCalProtocol.MNFLD_PRESS_BUF_GAS, AutoCalProtocol.ACQUIRED_ZONES_GAS,
        )
    )

    /** Botão → bytes (tabela documentada no relatório). Se mudar, este teste falha. */
    @Test
    fun `mapa botao para comando e exato e nunca cruza combustivel`() {
        val table = mapOf(
            "RESET_PETROL" to "02 24 04 01 2B", // Zerar/Reler gasolina
            "RESET_GAS" to "02 24 04 02 2C",    // Zerar/Reler GNV
            "RESET_ALL" to "02 24 04 04 2E",    // Zerar tudo
        )
        table.forEach { (name, bytes) ->
            assertArrayEquals(name, hex(bytes), AutoCalNativeActionManager.Action.valueOf(name).request)
        }
        assertEquals(0x01, AutoCalProtocol.ManualActionMode.RESET_PETROL.wireValue)
        assertEquals(0x02, AutoCalProtocol.ManualActionMode.RESET_GAS.wireValue)
        assertEquals(0x04, AutoCalProtocol.ManualActionMode.RESET_ALL.wireValue)
        // Curva K não usa 0x24: são 30 escritas em MUL_ACT.
        assertEquals(0, AutoCalNativeActionManager.Action.RESET_K_FACTOR.request.size)
        assertEquals(30, AutoCalProtocol.resetKFactorMulActFrames().size)
    }

    @Test
    fun `reset gasolina e GNV religam o aprendizado quando a ECU pausa e nunca mandam o comando do outro`() {
        for ((name, own, other) in listOf(
            Triple("RESET_PETROL", "02 24 04 01 2B", "02 24 04 02 2C"),
            Triple("RESET_GAS", "02 24 04 02 2C", "02 24 04 01 2B"),
        )) {
            val run = run(name, pausedByReset = true)
            assertEquals(name, "CONFIRMED", run.state)
            assertEquals("$name termina aprendendo", 1, run.enable)
            assertEquals(name, 1, run.sent.count { it.contentEquals(hex(own)) })
            assertEquals(name, 0, run.sent.count { it.contentEquals(hex(other)) })
            assertEquals(name, 0, run.sent.count { it.contentEquals(hex("12 4A 01 00 5D")) })
            val resetAt = run.sent.indexOfFirst { it.contentEquals(hex(own)) }
            val enableAt = run.sent.indexOfFirst { it.contentEquals(enableOn) }
            assertTrue("$name: religar só depois do reset", enableAt > resetAt && resetAt >= 0)
        }
    }

    @Test
    fun `reset com a ECU ja aprendendo nao reescreve nada alem do comando`() {
        val run = run("RESET_GAS", pausedByReset = false)
        assertEquals("CONFIRMED", run.state)
        assertEquals(1, run.enable)
        assertEquals(0, run.sent.count { it.contentEquals(enableOn) })
    }

    @Test
    fun `timeout no comando de reset ainda religa o aprendizado`() {
        val run = run("RESET_PETROL", pausedByReset = true, resetTransportFails = true)
        assertEquals("FAILED", run.state) // erro de transporte continua sendo erro do reset...
        assertEquals("...mas o aprendizado termina ligado", 1, run.enable)
    }

    @Test
    fun `se o aprendizado nao volta o reset nao e concluido e o dono e avisado`() {
        val run = run("RESET_GAS", pausedByReset = true, ignoreEnableWrite = true)
        assertEquals("FAILED", run.state)
        assertTrue(run.message, run.message.contains("Iniciar"))
        assertEquals(3, run.sent.count { it.contentEquals(enableOn) })
    }

    private class Run(val state: String, val message: String, val enable: Int, val sent: List<ByteArray>)

    private fun run(
        name: String,
        pausedByReset: Boolean,
        resetTransportFails: Boolean = false,
        ignoreEnableWrite: Boolean = false,
    ): Run {
        val sent = CopyOnWriteArrayList<ByteArray>()
        var enable = 1
        val action = AutoCalNativeActionManager.Action.valueOf(name)
        val manager = AutoCalNativeActionManager(
            receiptFile = Files.createTempDirectory("reset-nunca-pausa").resolve("r.json").toFile(),
            isConnected = { true },
            currentSessionId = { 1L },
            otherCalibrationBusy = { false },
            transaction = { request, _, _, _ ->
                sent += request
                when {
                    request.contentEquals(action.request) -> {
                        if (pausedByReset) enable = 0
                        if (resetTransportFails) {
                            UsbProtocolReply(ok = false, status = -1, payload = ByteArray(0), request = request, echo = ByteArray(0), error = "timeout")
                        } else ack(request, byteArrayOf())
                    }
                    request.contentEquals(enableOn) -> { if (!ignoreEnableWrite) enable = 1; ack(request, byteArrayOf()) }
                    request.contentEquals(enableRead) -> ack(request, byteArrayOf(enable.toByte()))
                    else -> {
                        val field = allFields.firstOrNull { AutoCalProtocol.read(it).contentEquals(request) }
                        requireNotNull(field) { "leitura inesperada" }
                        ack(request, ByteArray((field.expectedElementsHint ?: 1) * (if (field.encoding == AutoCalProtocol.Encoding.U8_OR_U16_LE) 1 else field.encoding.bytesPerElement)))
                    }
                }
            },
            fieldsForReceipt = allFields,
            guard = SerialWriteGuard(),
        )
        val prepared = manager.prepare(name)
        manager.execute(prepared.getString("preparationId"))
        repeat(600) { if (manager.isBusy()) Thread.sleep(10L) }
        val status = manager.statusJson()
        val result = Run(status.getString("state"), status.optString("message"), enable, sent.toList())
        manager.close()
        return result
    }

    private fun ack(request: ByteArray, payload: ByteArray) = UsbProtocolReply(
        ok = true, status = Mp48Protocol.STATUS_ACK, payload = payload, request = request, echo = request,
    )

    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
