package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.AntiStalloParamsCore.Io
import com.omegas.prohub.autocal.AntiStalloParamsCore.Outcome
import com.omegas.prohub.autocal.AntiStalloParamsCore.WriteReply
import com.omegas.prohub.ecu.AutoCalProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Os 5 números do anti-apagão (ANTI_STALLO, SerialCode 307 = 0x0133, 5 × u16), editáveis pelo dono (2026-10-18).
 * Espelha AntiStalloFlagCoreTest. Marcas: OBS-COD = DFM do ProgBase (faixa máxima de cada edit, padrão do vetor);
 * DERIVADO = quadro montado pelo mesmo montador do original (AutoCalProtocol.writeVectorU16); INFERIDO = ordem campo → posição.
 *
 * Quadro: 5 × u16 = 10 bytes de dados (> 5) => SetVector ESTENDIDO 0x37 (decompilação do original, TAebProtocol.SetVector:
 * n >= 7 envia 0x37, endereço baixo, n+1, endereço alto, dados). NÃO é 0x3B: o opcode compacto só vai até 0x36.
 */
class AntiStalloParamsCoreTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    private val factory = intArrayOf(1600, 100, 1500, 2, 20)

    private class FakeEcu(var values: IntArray, val behavior: Behavior = Behavior.ACCEPT) {
        enum class Behavior { ACCEPT, NAK, ACCEPT_BUT_KEEP_OLD, ACCEPT_WRONG_VALUE, TRANSPORT_FAIL_WRITTEN, TRANSPORT_FAIL_NOT_WRITTEN, READBACK_FAILS, CHANGES_BEFORE_WRITE }
        val frames = ArrayList<ByteArray>()
        val events = ArrayList<String>()
        fun io() = Io(
            read = {
                events += "read"
                if (behavior == Behavior.READBACK_FAILS && frames.isNotEmpty()) throw IllegalStateException("timeout")
                if (behavior == Behavior.CHANGES_BEFORE_WRITE && frames.isEmpty() && events.count { it == "read" } == 1) values = intArrayOf(values[0] + 1, values[1], values[2], values[3], values[4])
                values.copyOf()
            },
            write = { frame ->
                events += "write"
                frames += frame
                // quadro estendido: 37 lo len hi <10 bytes> ck
                val sent = IntArray(5) { (frame[4 + it * 2].toInt() and 0xFF) or ((frame[5 + it * 2].toInt() and 0xFF) shl 8) }
                when (behavior) {
                    Behavior.NAK -> WriteReply(false, false, "CA 01 10", "status 0xCA")
                    Behavior.ACCEPT_BUT_KEEP_OLD -> WriteReply(true, false, "53 00 53", "ACK")
                    Behavior.ACCEPT_WRONG_VALUE -> { values = sent.copyOf().also { it[2] += 1 }; WriteReply(true, false, "53 00 53", "ACK") }
                    Behavior.TRANSPORT_FAIL_WRITTEN -> { values = sent; WriteReply(false, true, "", "timeout") }
                    Behavior.TRANSPORT_FAIL_NOT_WRITTEN -> WriteReply(false, true, "", "timeout")
                    else -> { values = sent; WriteReply(true, false, "53 00 53", "ACK") }
                }
            },
        )
    }

    // ---------- constantes ----------
    @Test fun `endereco 307 e cinco numeros`() {
        assertEquals(307, AntiStalloParamsCore.ADDRESS)
        assertEquals(0x0133, AntiStalloParamsCore.ADDRESS)
        assertEquals(5, AntiStalloParamsCore.COUNT)
    }

    @Test fun `valores de fabrica sao os do DFM 1600 100 1500 2 20`() {
        assertArrayEquals(factory, AntiStalloParamsCore.FACTORY)
        assertArrayEquals(factory, IntArray(5) { AntiStalloParamsCore.FIELDS[it].factory })
    }

    @Test fun `faixa maxima de cada campo e a do DFM - 6000 1000 10000 1000 1000`() {
        assertArrayEquals(intArrayOf(6000, 1000, 10000, 1000, 1000), IntArray(5) { AntiStalloParamsCore.FIELDS[it].max })
        assertArrayEquals(IntArray(5), IntArray(5) { AntiStalloParamsCore.FIELDS[it].min })
        for (f in AntiStalloParamsCore.FIELDS) assertTrue("fábrica dentro da faixa: ${f.index}", f.factory in f.min..f.max)
    }

    // ---------- quadro ----------
    @Test fun `quadro dos valores de fabrica e o SetVector estendido 37 33 0C 01`() {
        val plan = AntiStalloParamsCore.plan(intArrayOf(1700, 100, 1500, 2, 20), mapOf(0 to 1600))
        assertEquals("37 33 0C 01 40 06 64 00 DC 05 02 00 14 00 18", hex(plan.frame))
    }

    @Test fun `o quadro e exatamente o do montador do original (writeVectorU16) e o primeiro byte NAO e 3B`() {
        val plan = AntiStalloParamsCore.plan(intArrayOf(1600, 100, 1500, 2, 20), mapOf(0 to 1700, 4 to 30))
        assertArrayEquals(AutoCalProtocol.writeVectorU16(0x0133, plan.after), plan.frame)
        assertEquals(0x37, plan.frame[0].toInt() and 0xFF)
        assertEquals(15, plan.frame.size)
        // conta independente: soma dos bytes anteriores, módulo 256
        val soma = plan.frame.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
        assertEquals(soma, plan.frame.last().toInt() and 0xFF)
    }

    @Test fun `ordem dos bytes e a da leitura - little-endian por posicao`() {
        assertArrayEquals(
            intArrayOf(1600, 100, 1500, 2, 20),
            AntiStalloParamsCore.valuesFromPayload(intArrayOf(0x40, 0x06, 0x64, 0x00, 0xDC, 0x05, 0x02, 0x00, 0x14, 0x00)),
        )
        assertNull(AntiStalloParamsCore.valuesFromPayload(IntArray(9)))
        assertNull(AntiStalloParamsCore.valuesFromPayload(IntArray(11)))
        assertNull(AntiStalloParamsCore.valuesFromPayload(IntArray(0)))
    }

    // ---------- plano: ler-modificar-escrever ----------
    @Test fun `so o campo pedido muda e os outros vem da leitura ao vivo`() {
        val live = intArrayOf(1234, 99, 1400, 3, 21)
        val plan = AntiStalloParamsCore.plan(live, mapOf(2 to 2000))
        assertArrayEquals(intArrayOf(1234, 99, 2000, 3, 21), plan.after)
        assertArrayEquals(live, plan.before)
        assertEquals(listOf(2), plan.changedIndices)
        assertFalse(plan.noop)
    }

    @Test fun `mesmo valor que ja esta na ECU e noop`() {
        assertTrue(AntiStalloParamsCore.plan(factory, mapOf(0 to 1600, 3 to 2)).noop)
        assertTrue(AntiStalloParamsCore.plan(factory, emptyMap()).noop)
    }

    @Test fun `restaurar fabrica troca so o que difere`() {
        val plan = AntiStalloParamsCore.plan(intArrayOf(1800, 100, 1500, 5, 20), AntiStalloParamsCore.factoryChanges())
        assertArrayEquals(factory, plan.after)
        assertEquals(listOf(0, 3), plan.changedIndices)
    }

    @Test fun `valor atual invalido nao vira plano`() {
        for (bad in listOf(intArrayOf(), intArrayOf(1, 2, 3, 4), intArrayOf(1, 2, 3, 4, 5, 6), intArrayOf(1, 2, 3, 4, -1), intArrayOf(1, 2, 3, 4, 0x10000))) {
            try { AntiStalloParamsCore.plan(bad, mapOf(0 to 1)); fail("deveria recusar ${bad.toList()}") } catch (_: IllegalArgumentException) {}
        }
    }

    // ---------- validação: nunca aceitar fora da faixa ----------
    @Test fun `valor dentro da faixa passa, nos dois limites`() {
        for (f in AntiStalloParamsCore.FIELDS) {
            assertNull("min ${f.index}", AntiStalloParamsCore.validate(mapOf(f.index to f.min)))
            assertNull("max ${f.index}", AntiStalloParamsCore.validate(mapOf(f.index to f.max)))
        }
    }

    @Test fun `um acima do maximo e um abaixo do minimo sao recusados com texto em linguagem simples`() {
        for (f in AntiStalloParamsCore.FIELDS) {
            val acima = AntiStalloParamsCore.validate(mapOf(f.index to f.max + 1))
            assertNotNull("acima ${f.index}", acima)
            val abaixo = AntiStalloParamsCore.validate(mapOf(f.index to f.min - 1))
            assertNotNull("abaixo ${f.index}", abaixo)
            assertTrue(acima!!, acima.contains("${f.max}") && acima.contains("${f.min}"))
        }
    }

    @Test fun `indice inexistente e lista vazia de campos fora da faixa`() {
        assertNotNull(AntiStalloParamsCore.validate(mapOf(5 to 1)))
        assertNotNull(AntiStalloParamsCore.validate(mapOf(-1 to 1)))
    }

    @Test fun `plano com valor fora da faixa lanca e nunca monta quadro`() {
        try { AntiStalloParamsCore.plan(factory, mapOf(0 to 6001)); fail("deveria recusar") } catch (_: IllegalArgumentException) {}
        try { AntiStalloParamsCore.plan(factory, mapOf(2 to 10001)); fail("deveria recusar") } catch (_: IllegalArgumentException) {}
        try { AntiStalloParamsCore.plan(factory, mapOf(1 to -5)); fail("deveria recusar") } catch (_: IllegalArgumentException) {}
    }

    @Test fun `leitura de volta so vale se os cinco numeros forem iguais`() {
        val plan = AntiStalloParamsCore.plan(factory, mapOf(0 to 1700))
        assertTrue(AntiStalloParamsCore.readbackMatches(plan, intArrayOf(1700, 100, 1500, 2, 20)))
        assertFalse(AntiStalloParamsCore.readbackMatches(plan, factory))
        assertFalse(AntiStalloParamsCore.readbackMatches(plan, intArrayOf(1700, 100, 1500, 2, 21)))
        assertFalse(AntiStalloParamsCore.readbackMatches(plan, intArrayOf(1700, 100, 1500, 2)))
    }

    // ---------- execução ----------
    @Test fun `caminho feliz le, escreve UM quadro, le de volta e so entao confirma`() {
        val ecu = FakeEcu(factory.copyOf())
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.CONFIRMED, r.outcome)
        assertEquals(listOf("read", "write", "read"), ecu.events)
        assertEquals(1, ecu.frames.size)
        assertEquals(0x37, ecu.frames[0][0].toInt() and 0xFF)
        assertArrayEquals(intArrayOf(1700, 100, 1500, 2, 20), r.readback)
    }

    @Test fun `a ECU mudou entre a previa e a gravacao - nada e escrito`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.CHANGES_BEFORE_WRITE)
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.CHANGED_MEANWHILE, r.outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `NAK nao e sucesso e a leitura mostra que nada mudou`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.NAK)
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.NOT_ACKED, r.outcome)
        assertFalse(r.valueChanged)
    }

    @Test fun `falha de transporte que gravou mesmo assim avisa que mudou e nao e sucesso`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.TRANSPORT_FAIL_WRITTEN)
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.NOT_ACKED, r.outcome)
        assertTrue(r.valueChanged)
    }

    @Test fun `ACK mas o modulo guardou o valor antigo - leitura divergente`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.ACCEPT_BUT_KEEP_OLD)
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.READBACK_DIVERGED, r.outcome)
        assertArrayEquals(factory, r.readback)
    }

    @Test fun `ACK mas outro numero saiu diferente - leitura divergente`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.ACCEPT_WRONG_VALUE)
        assertEquals(Outcome.READBACK_DIVERGED, AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700))).outcome)
    }

    @Test fun `leitura de volta falha - desconhecido, nunca sucesso`() {
        val ecu = FakeEcu(factory.copyOf(), FakeEcu.Behavior.READBACK_FAILS)
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
        assertEquals(Outcome.READBACK_FAILED, r.outcome)
        assertNull(r.readback)
    }

    @Test fun `noop nunca envia quadro`() {
        val ecu = FakeEcu(factory.copyOf())
        val r = AntiStalloParamsCore.execute(ecu.io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1600)))
        assertEquals(Outcome.NOTHING_TO_DO, r.outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `mensagens em linguagem simples - sem sigla tecnica e dizendo o que fazer`() {
        val casos = listOf(
            FakeEcu.Behavior.NAK, FakeEcu.Behavior.ACCEPT_BUT_KEEP_OLD, FakeEcu.Behavior.READBACK_FAILS,
            FakeEcu.Behavior.CHANGES_BEFORE_WRITE, FakeEcu.Behavior.TRANSPORT_FAIL_WRITTEN,
        )
        for (b in casos) {
            val r = AntiStalloParamsCore.execute(FakeEcu(factory.copyOf(), b).io(), AntiStalloParamsCore.plan(factory, mapOf(0 to 1700)))
            for (proibido in listOf("ECU", "FLAG", "bit", "0x", "SerialCode", "u16")) assertFalse("$b: ${r.message}", r.message.contains(proibido))
            assertTrue("$b: mensagem vazia", r.message.length > 20)
        }
    }
}
