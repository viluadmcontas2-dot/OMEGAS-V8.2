package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyDataSweeperTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `apaga so o legado do aprendizado e e idempotente`() {
        val runtime = tmp.newFolder("runtime"); val backups = tmp.newFolder("backups")
        LegacyDataSweeper.RUNTIME_FILES.forEach { File(runtime, it).writeText("{}") }
        LegacyDataSweeper.RUNTIME_DIRS.forEach { File(runtime, "$it/old.json").apply { parentFile!!.mkdirs(); writeText("{}") } }
        File(backups, "learning_checkpoints/history/a.omegas").apply { parentFile!!.mkdirs(); writeText("x") }
        val keep = mapOf(
            "refinement_autopilot.json" to """{"format":"omegas-refinement-autopilot-v1","phase":"VERIFICANDO"}""",
            "equivalence_ledger.json" to """{"format":"omegas-equivalence-ledger-v1"}""",
            "refinement_journal.json" to "{}", "stall_watch.json" to "{}", "k_map_cache.json" to "{}",
            "k_factor_cache.json" to "{}", "autocal_native_receipts.json" to "[]")
        keep.forEach { (name, body) -> File(runtime, name).writeText(body) }
        val curveBackup = File(backups, "curve_k_2026-10-01.json").apply { writeText("{}") }

        val removed = LegacyDataSweeper.sweep(runtime, backups)

        assertEquals(LegacyDataSweeper.RUNTIME_FILES + listOf("learning_quarantine/", "v7_sessions/", "learning_checkpoints/"), removed)
        keep.forEach { (name, body) -> assertEquals(name, body, File(runtime, name).readText()) }
        assertTrue(curveBackup.isFile)
        assertEquals(emptyList<String>(), LegacyDataSweeper.sweep(runtime, backups))
    }

    @Test fun `raiz inexistente nao lanca`() {
        assertEquals(emptyList<String>(), LegacyDataSweeper.sweep(File(tmp.root, "nada"), File(tmp.root, "nada2")))
    }
}
