package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KBatchVerifyCoreTest {
    @Test fun linhasAfetadasSaoUnicas() {
        assertEquals(listOf(3, 5), KBatchVerifyCore.affectedRows(listOf(3, 3, 5, 3)))
    }

    @Test fun mapaNovoNaoConfereLinhaAntesDeEscrever() {
        // 24 células em 12 linhas: antes 12+1+24+1+12 = 50; agora 1+24+1+12 = 38.
        assertEquals(50, KBatchVerifyCore.legacyMapTransactions(24, 12))
        assertEquals(38, KBatchVerifyCore.mapTransactions(24, 12))
        assertEquals(30, KBatchVerifyCore.legacyMapTransactions(24, 2))
        assertEquals(28, KBatchVerifyCore.mapTransactions(24, 2))
    }

    @Test fun curvaLeVetorInteiroUmaVezAntesEUmaDepois() {
        assertEquals(32, KBatchVerifyCore.curveTransactions(30))
        assertEquals(3, KBatchVerifyCore.curveTransactions(1))
    }

    @Test fun confereSoCelulasAlteradas() {
        val expected = intArrayOf(10, 20, 30, 40)
        // Coluna 3 mudou pela ECU (AutoMatch) mas não foi tocada pelo operador: não reprova.
        assertNull(KBatchVerifyCore.firstMismatch(expected, intArrayOf(10, 20, 30, 99), listOf(0, 2)))
        assertEquals(2, KBatchVerifyCore.firstMismatch(expected, intArrayOf(10, 20, 31, 40), listOf(0, 2)))
    }

    @Test fun indiceForaDoVetorLidoReprova() {
        assertEquals(5, KBatchVerifyCore.firstMismatch(intArrayOf(1), intArrayOf(1), listOf(5)))
    }

    @Test fun mensagensDeProgressoReal() {
        assertEquals("Gravando 12 de 24…", KBatchVerifyCore.writingMessage(12, 24))
        assertEquals("Conferindo 1 de 2 linhas…", KBatchVerifyCore.verifyingMessage(1, 2))
    }
}
