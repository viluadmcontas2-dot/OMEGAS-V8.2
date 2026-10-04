package com.omegas.prohub.properties

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RefinementJournal
import com.omegas.prohub.equivalence.JsonFiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Robustez de persistência: escrita serializada, sem voltar atrás, com .bak (defeito P1). */
class PersistenceRobustnessTest {
    private fun gasFrames(n: Int, t0: Long): List<EquivalenceLedger.Frame> =
        (0 until n).map { EquivalenceLedger.Frame(t0 + it * 100L, "GNV", 2500.0, 0.5, 5.0, 6.0) }

    private fun gasCountOnDisk(file: File): Int = EquivalenceLedger(file).gasObservations().size

    @Test
    fun `resetGas concorrente com salvamento debounced deixa o disco igual a memoria`() {
        repeat(40) { iteration ->
            val file = PropertySupport.tempFile("ledger$iteration")
            var now = 1_000_000L
            val ledger = EquivalenceLedger(file) { now }
            gasFrames(60, 1_000L).forEach { ledger.accept(it) }
            ledger.flush()
            now += 120_000L
            val start = CountDownLatch(1)
            val writer = thread {
                start.await()
                for (k in 0 until 30) gasFrames(4, 2_000_000L + k * 1000L).forEach { ledger.accept(it) }
            }
            val resetter = thread { start.await(); ledger.resetGas("CURVA_K_GRAVADA_PELO_APP") }
            start.countDown()
            writer.join(); resetter.join()
            ledger.flush()
            assertEquals("iteração $iteration", ledger.gasObservations().size, gasCountOnDisk(file))
        }
    }

    @Test
    fun `reset depois de salvar nunca e sobrescrito por payload antigo`() {
        repeat(40) { iteration ->
            val file = PropertySupport.tempFile("ledgerB$iteration")
            var now = 1_000_000L
            val ledger = EquivalenceLedger(file) { now }
            gasFrames(80, 1_000L).forEach { ledger.accept(it) }
            ledger.flush()
            assertTrue(gasCountOnDisk(file) > 0)
            now += 120_000L
            val start = CountDownLatch(1)
            val debounced = thread { start.await(); ledger.accept(EquivalenceLedger.Frame(9_000_000L, "GASOLINA", 2500.0, 0.5, 5.0)) }
            val reset = thread { start.await(); ledger.resetGas("MAPA_K_GRAVADO") }
            start.countDown()
            debounced.join(); reset.join()
            ledger.flush()
            assertEquals("iteração $iteration", 0, gasCountOnDisk(file))
        }
    }

    @Test
    fun `escritas usam arquivo temporario unico e nao deixam resto`() {
        val file = PropertySupport.tempFile("atomic")
        val threads = (0 until 8).map { id -> thread { repeat(25) { JsonFiles.write(file, JSONObject().put("format", "x").put("id", id).toString()) } } }
        threads.forEach { it.join() }
        assertEquals("x", JSONObject(file.readText()).getString("format"))
        val leftovers = file.parentFile.listFiles { f -> f.name.startsWith(file.name) && f.name.endsWith(".tmp") }.orEmpty()
        assertEquals(0, leftovers.size)
    }

    @Test
    fun `diario corrompido cai no bak em vez de apagar os experimentos`() {
        val file = PropertySupport.tempFile("journal")
        val journal = RefinementJournal(file)
        journal.recordCurveWrite(
            IntArray(30) { 100 }, IntArray(30) { if (it == 3) 110 else 100 }, IntArray(30) { it * 100 },
            JSONObject(), "TESTE", photoFile = "foto_antes.json",
        )
        journal.flush()
        val good = file.readText()
        File(file.parentFile, file.name + ".bak").writeText(good)
        file.writeText("{corrompido")
        val latest = RefinementJournal(file).json().optJSONObject("latest")
        assertEquals("foto_antes.json", latest?.optString("photoFile"))
        File(file.parentFile, file.name + ".bak").delete()
    }

    @Test
    fun `diario corrompido sem bak nao quebra`() {
        val file = PropertySupport.tempFile("journal2")
        file.writeText("nao e json")
        assertTrue(RefinementJournal(file).json().optBoolean("ok"))
    }

    @Test
    fun `livro com valor faltando no arquivo nao injeta NaN nas faixas`() {
        val file = PropertySupport.tempFile("ledgerNaN")
        file.writeText(
            """{"format":"${EquivalenceLedger.FORMAT}","petrol":{"t":[1,2,3],"rpm":[2500,null,2500],"map":[0.5,0.5,0.5],"ms":[5,5,5]},"gas":[]}""",
        )
        val ledger = EquivalenceLedger(file)
        val obs = ledger.petrolObservations()
        obs.forEach { assertTrue(it.rpm.isFinite() && it.map.isFinite() && it.petrolMs.isFinite()) }
    }
}
