package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class FailureKindTest {
    @Test fun `timeout sem mensagem e transporte`() {
        assertEquals(FailureKind.TRANSPORT, FailureKind.of(java.util.concurrent.TimeoutException()))
    }

    @Test fun `falha de entrada e saida sem texto conhecido e transporte`() {
        assertEquals(FailureKind.TRANSPORT, FailureKind.of(java.io.IOException("falhou")))
    }

    @Test fun `calibration failure mantem a origem declarada`() {
        assertEquals(FailureKind.ECU, FailureKind.of(CalibrationFailure("recusou", FailureKind.ECU)))
    }

    @Test fun `mensagem de usb desconectado e transporte e ack invalido e ecu`() {
        assertEquals(FailureKind.TRANSPORT, FailureKind.ofMessage("USB desconectado"))
        assertEquals(FailureKind.ECU, FailureKind.ofMessage("ACK inválido em escrita K"))
        assertEquals(FailureKind.APP, FailureKind.ofMessage(""))
    }
}
