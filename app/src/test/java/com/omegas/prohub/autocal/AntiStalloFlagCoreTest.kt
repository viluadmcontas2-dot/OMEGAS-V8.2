package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.AntiStalloFlagCore.Io
import com.omegas.prohub.autocal.AntiStalloFlagCore.Outcome
import com.omegas.prohub.autocal.AntiStalloFlagCore.WriteReply
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Escrita manual do bit 0x20 (palavra 0) de FLAG_CONF3 (anti-stallo), dono 2026-10-18. Lógica pura: ECU falsa.
 * O quadro `35 FA 00 20 00 00 00 4F` é DERIVADO do código do ProgBase (nunca visto ao vivo).
 */
class AntiStalloFlagCoreTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private class FakeEcu(var words: IntArray, val behavior: Behavior = Behavior.ACCEPT) {
        enum class Behavior { ACCEPT, NAK, ACCEPT_BUT_KEEP_OLD, ACCEPT_WRONG_BIT, TRANSPORT_FAIL_WRITTEN, TRANSPORT_FAIL_NOT_WRITTEN, READBACK_FAILS, CHANGES_BEFORE_WRITE }
        val frames = ArrayList<ByteArray>()
        val events = ArrayList<String>()
        fun io() = Io(
            read = {
                events += "read"
                if (behavior == Behavior.READBACK_FAILS && frames.isNotEmpty()) throw IllegalStateException("timeout")
                if (behavior == Behavior.CHANGES_BEFORE_WRITE && frames.isEmpty() && events.count { it == "read" } == 1) words = intArrayOf(words[0] xor 0x0100, words[1])
                words.copyOf()
            },
            write = { frame ->
                events += "write"
                frames += frame
                val payload = IntArray(2) { (frame[3 + it * 2].toInt() and 0xFF) or ((frame[4 + it * 2].toInt() and 0xFF) shl 8) }
                when (behavior) {
                    Behavior.NAK -> WriteReply(false, false, "CA 01 10", "status 0xCA")
                    Behavior.ACCEPT_BUT_KEEP_OLD -> WriteReply(true, false, "53 00 53", "ACK")
                    Behavior.ACCEPT_WRONG_BIT -> { words = intArrayOf(payload[0] xor 0x0001, payload[1]); WriteReply(true, false, "53 00 53", "ACK") }
                    Behavior.TRANSPORT_FAIL_WRITTEN -> { words = payload; WriteReply(false, true, "", "timeout") }
                    Behavior.TRANSPORT_FAIL_NOT_WRITTEN -> WriteReply(false, true, "", "timeout")
                    else -> { words = payload; WriteReply(true, false, "53 00 53", "ACK") }
                }
            },
        )
    }

    // ---------- plano / quadro ----------
    @Test fun `ligar com tudo zero gera o quadro derivado 35 FA 00 20 00 00 00 4F`() {
        val plan = AntiStalloFlagCore.plan(intArrayOf(0, 0), true)
        assertEquals("35 FA 00 20 00 00 00 4F", hex(plan.frame))
        assertArrayEquals(intArrayOf(0x0020, 0), plan.after)
        assertEquals(0x20, plan.changedMask)
        assertFalse(plan.noop)
    }

    @Test fun `caso do codigo original OBS-COD - 0C10 ABCD mais marcar gera 35 FA 00 30 0C CD AB E3`() {
        val plan = AntiStalloFlagCore.plan(intArrayOf(0x0C10, 0xABCD), true)
        assertEquals("35 FA 00 30 0C CD AB E3", hex(plan.frame))
        assertArrayEquals(intArrayOf(0x0C30, 0xABCD), plan.after)
    }

    @Test fun `desligar apaga so o bit 0x20`() {
        val plan = AntiStalloFlagCore.plan(intArrayOf(0x0020, 0), false)
        assertEquals("35 FA 00 00 00 00 00 2F", hex(plan.frame))
    }

    @Test fun `ler-modificar-escrever preserva todos os outros bits das duas palavras`() {
        val on = AntiStalloFlagCore.plan(intArrayOf(0xDFCF, 0xA5A5), true)
        assertArrayEquals(intArrayOf(0xDFEF, 0xA5A5), on.after)
        assertEquals(0x20, on.before[0] xor on.after[0])
        assertEquals(on.before[1], on.after[1])
        val off = AntiStalloFlagCore.plan(intArrayOf(0xFFFF, 0xFFFF), false)
        assertArrayEquals(intArrayOf(0xFFDF, 0xFFFF), off.after)
        assertEquals("35 FA 00 DF FF FF FF " + "%02X".format((0x35 + 0xFA + 0xDF + 0xFF + 0xFF + 0xFF) and 0xFF), hex(off.frame))
    }

    @Test fun `ordem de bytes bate com a leitura real 53 04 20 00 00 00 77`() {
        // a leitura devolve os dados na mesma ordem do quadro: palavra 0 (lo,hi), palavra 1 (lo,hi)
        assertArrayEquals(intArrayOf(0x0020, 0), AntiStalloFlagCore.wordsFromPayload(intArrayOf(0x20, 0, 0, 0)))
        assertArrayEquals(intArrayOf(0x1234, 0xABCD), AntiStalloFlagCore.wordsFromPayload(intArrayOf(0x34, 0x12, 0xCD, 0xAB)))
        assertNull(AntiStalloFlagCore.wordsFromPayload(intArrayOf(0x20, 0, 0)))
        assertNull(AntiStalloFlagCore.wordsFromPayload(intArrayOf(0x20, 0, 0, 0, 0)))
    }

    @Test fun `ja no estado pedido e noop`() {
        assertTrue(AntiStalloFlagCore.plan(intArrayOf(0x0020, 7), true).noop)
        assertTrue(AntiStalloFlagCore.plan(intArrayOf(0, 7), false).noop)
    }

    @Test fun `valor atual invalido nao vira plano`() {
        for (bad in listOf(intArrayOf(), intArrayOf(0), intArrayOf(0, 0, 0), intArrayOf(0, -1), intArrayOf(0x10000, 0))) {
            try { AntiStalloFlagCore.plan(bad, true); fail("deveria recusar ${bad.toList()}") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun `leitura de volta so vale se as duas palavras forem iguais`() {
        val plan = AntiStalloFlagCore.plan(intArrayOf(0x4000, 5), true)
        assertTrue(AntiStalloFlagCore.readbackMatches(plan, intArrayOf(0x4020, 5)))
        assertFalse(AntiStalloFlagCore.readbackMatches(plan, intArrayOf(0x4000, 5)))
        assertFalse(AntiStalloFlagCore.readbackMatches(plan, intArrayOf(0x4020, 4)))
        assertFalse(AntiStalloFlagCore.readbackMatches(plan, intArrayOf(0x4030, 5)))
        assertFalse(AntiStalloFlagCore.readbackMatches(plan, intArrayOf(0x4020)))
    }

    // ---------- execucao ----------
    @Test fun `caminho feliz le, escreve UM quadro, le de volta e so entao confirma`() {
        val ecu = FakeEcu(intArrayOf(0, 0))
        val plan = AntiStalloFlagCore.plan(intArrayOf(0, 0), true)
        val r = AntiStalloFlagCore.execute(ecu.io(), plan)
        assertEquals(Outcome.CONFIRMED, r.outcome)
        assertEquals(listOf("read", "write", "read"), ecu.events)
        assertEquals(1, ecu.frames.size)
        assertEquals("35 FA 00 20 00 00 00 4F", hex(ecu.frames[0]))
        assertArrayEquals(intArrayOf(0x20, 0), r.readback)
    }

    @Test fun `a ECU mudou entre a previa e a gravacao - nada e escrito`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.CHANGES_BEFORE_WRITE)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.CHANGED_MEANWHILE, r.outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `NAK da ECU nao e sucesso e a leitura mostra que nada mudou`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.NAK)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.NOT_ACKED, r.outcome)
        assertArrayEquals(intArrayOf(0, 0), r.readback)
        assertFalse(r.valueChanged)
    }

    @Test fun `falha de transporte que de fato gravou nao e declarada como sucesso mas avisa que mudou`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.TRANSPORT_FAIL_WRITTEN)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.NOT_ACKED, r.outcome)
        assertTrue(r.valueChanged)
    }

    @Test fun `ACK mas a ECU guardou o valor antigo - leitura divergente`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.ACCEPT_BUT_KEEP_OLD)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.READBACK_DIVERGED, r.outcome)
        assertArrayEquals(intArrayOf(0, 0), r.readback)
    }

    @Test fun `ACK mas outro bit saiu diferente - leitura divergente`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.ACCEPT_WRONG_BIT)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.READBACK_DIVERGED, r.outcome)
    }

    @Test fun `leitura de volta falha - valor desconhecido, nunca sucesso`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.READBACK_FAILS)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertEquals(Outcome.READBACK_FAILED, r.outcome)
        assertNull(r.readback)
    }

    @Test fun `noop nunca envia quadro`() {
        val ecu = FakeEcu(intArrayOf(0x20, 0))
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0x20, 0), true))
        assertEquals(Outcome.NOTHING_TO_DO, r.outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    // Revisão independente (item 3): quando o app não sabe o que a ECU tem, a mensagem diz isso (nunca "nada foi gravado").
    @Test fun `leitura de volta falha - mensagem diz estado incerto`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.READBACK_FAILS)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertTrue(r.message, r.message.startsWith("Estado incerto — releia a ECU"))
    }

    @Test fun `leitura divergente - mensagem diz estado incerto`() {
        val ecu = FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.ACCEPT_WRONG_BIT)
        val r = AntiStalloFlagCore.execute(ecu.io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertTrue(r.message, r.message.startsWith("Estado incerto — releia a ECU"))
    }

    @Test fun `sem ACK com valor mudado e estado incerto, com valor intacto e recusa limpa`() {
        val mudou = AntiStalloFlagCore.execute(FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.TRANSPORT_FAIL_WRITTEN).io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertTrue(mudou.message, mudou.message.startsWith("Estado incerto — releia a ECU"))
        val intacto = AntiStalloFlagCore.execute(FakeEcu(intArrayOf(0, 0), FakeEcu.Behavior.NAK).io(), AntiStalloFlagCore.plan(intArrayOf(0, 0), true))
        assertFalse(intacto.message, intacto.message.contains("Estado incerto"))
    }
}
