package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.BufferWriteProbeCore.Io
import com.omegas.prohub.autocal.BufferWriteProbeCore.Outcome
import com.omegas.prohub.autocal.BufferWriteProbeCore.WriteReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ECU falsa: prova cada desfecho da sonda de escrita única sem USB. */
class BufferWriteProbeCoreTest {
    private class FakeEcu(
        val times: IntArray = IntArray(18) { if (it in 3..5) 1000 + it else 0 },
        val counters: IntArray = IntArray(18) { if (it in 3..5) 10 else 0 },
        var behavior: Behavior = Behavior.ACCEPT_AND_HOLD,
    ) {
        enum class Behavior { ACCEPT_AND_HOLD, NAK, ACCEPT_THEN_REVERT, ACCEPT_CLAMP, TRANSPORT_FAIL_WRITTEN, TRANSPORT_FAIL_NOT_WRITTEN, AUTOMATCH_DURING, OVERWRITE_AFTER_ECU_CYCLE }
        val writes = ArrayList<Pair<Int, Int>>()
        private var reads = 0
        /** Quadros de telemetria que a ECU entregou desde a escrita da sonda (o ciclo dela anda com as respostas). */
        var framesSinceWrite = 0
        val frameWaits = ArrayList<Int>()

        fun io() = Io(
            readTimes = {
                reads += 1
                if (behavior == Behavior.AUTOMATCH_DURING && reads >= 3) { times.fill(0); counters.fill(0) }
                if (behavior == Behavior.ACCEPT_THEN_REVERT && reads >= 3 && writes.size == 1) times[3] = 1003
                // A ECU só sobrescreve depois de rodar o próprio ciclo: precisa de quadros novos, não de leituras seguidas.
                if (behavior == Behavior.OVERWRITE_AFTER_ECU_CYCLE && writes.size == 1 && framesSinceWrite >= ECU_CYCLE_FRAMES) times[3] = 1003
                times.copyOf()
            },
            readCounters = { counters.copyOf() },
            awaitFrames = { count -> frameWaits += count; framesSinceWrite += count },
            write = { index, raw ->
                writes += index to raw
                framesSinceWrite = 0
                when (behavior) {
                    Behavior.NAK -> WriteReply(false, false, "14 5F 01 04", "CA 01 08", "status 0xCA")
                    Behavior.ACCEPT_CLAMP -> { times[index] = if (raw > 1003) raw - 3 else raw; ok() }
                    Behavior.TRANSPORT_FAIL_WRITTEN -> { times[index] = raw; WriteReply(false, true, "", "", "timeout") }
                    Behavior.TRANSPORT_FAIL_NOT_WRITTEN -> WriteReply(false, true, "", "", "timeout")
                    else -> { times[index] = raw; ok() }
                }
            },
        )

        private fun ok() = WriteReply(true, false, "14 5F 01 04", "53 00 53", "ACK")

        companion object { const val ECU_CYCLE_FRAMES = 6 }
    }

    @Test fun `sem banda cheia nao escreve nada`() {
        val ecu = FakeEcu(counters = IntArray(18) { 5 })
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.NO_STABLE_BAND, result.outcome)
        assertTrue(ecu.writes.isEmpty())
    }

    @Test fun `ECU aceita e o valor fica - restaura o original e confere`() {
        val ecu = FakeEcu()
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.ACCEPTED_AND_HELD, result.outcome)
        assertEquals(1 + BufferWriteProbeCore.SETTLE_READS, result.readbacks.size)
        assertEquals(3, result.band)
        assertEquals(1003, result.original)
        assertEquals(1011, result.target)
        assertTrue(result.restoreNeeded)
        assertTrue(result.restored)
        assertEquals(1003, ecu.times[3])
        assertEquals(listOf(3 to 1011, 3 to 1003), ecu.writes)
    }

    @Test fun `NAK - nada muda e nao ha restauracao`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.NAK)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.REFUSED, result.outcome)
        assertFalse(result.restoreNeeded)
        assertEquals(1, ecu.writes.size)
        assertEquals(1003, ecu.times[3])
    }

    @Test fun `ECU aceita mas desfaz sozinha - sem nova escrita de restauracao`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.ACCEPT_THEN_REVERT)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.ACCEPTED_THEN_OVERWRITTEN, result.outcome)
        assertFalse(result.restoreNeeded)
        assertEquals(1, ecu.writes.size)
        assertEquals(1003, ecu.times[3])
    }

    @Test fun `ECU limita o valor - restaura o original`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.ACCEPT_CLAMP)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.ACCEPTED_BUT_DIFFERENT, result.outcome)
        assertTrue(result.restored)
        assertEquals(1003, ecu.times[3])
    }

    @Test fun `falha de transporte mas o valor nao mudou - erro sem restaurar`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.TRANSPORT_FAIL_NOT_WRITTEN)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.ERROR, result.outcome)
        assertFalse(result.restoreNeeded)
        assertEquals(1, ecu.writes.size)
    }

    @Test fun `falha de transporte mas a escrita pegou - leitura prova e restaura`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.TRANSPORT_FAIL_WRITTEN)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertTrue(result.restoreNeeded)
        assertTrue(result.restored)
        assertEquals(1003, ecu.times[3])
    }

    @Test fun `AutoMatch esvazia a banda no meio - nao devolve media antiga`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.AUTOMATCH_DURING)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertFalse(result.restored)
        assertTrue(result.message.contains("esvaziou"))
        // só a escrita da sonda; nenhuma escrita de restauração sobre banda zerada
        assertEquals(1, ecu.writes.size)
        assertEquals(0, ecu.times[3])
    }

    @Test fun `escolhe a banda mais cheia dentro do limite do passo`() {
        val ecu = FakeEcu(times = IntArray(18) { 2000 }, counters = IntArray(18) { 10 })
        ecu.times[0] = 0xFFFF
        ecu.counters[0] = 10
        val result = BufferWriteProbeCore.run(ecu.io())
        assertTrue(result.band != 0)
        assertTrue(result.target <= 0xFFFF)
    }

    @Test fun `ECU sobrescreve depois do proprio ciclo - leituras esperam quadros novos e detectam`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.OVERWRITE_AFTER_ECU_CYCLE)
        val result = BufferWriteProbeCore.run(ecu.io())
        assertEquals(Outcome.ACCEPTED_THEN_OVERWRITTEN, result.outcome)
        assertEquals(1011, result.readbacks.first())
        assertEquals(1003, result.readbacks.last())
        // entre a leitura de volta e cada leitura de conferência: K quadros de telemetria (contagem, sem relógio)
        assertEquals(List(BufferWriteProbeCore.SETTLE_READS) { BufferWriteProbeCore.FRAMES_BETWEEN_READS }, ecu.frameWaits)
    }

    @Test fun `recusa nao espera quadros`() {
        val ecu = FakeEcu(behavior = FakeEcu.Behavior.NAK)
        BufferWriteProbeCore.run(ecu.io())
        assertTrue(ecu.frameWaits.isEmpty())
    }
}
