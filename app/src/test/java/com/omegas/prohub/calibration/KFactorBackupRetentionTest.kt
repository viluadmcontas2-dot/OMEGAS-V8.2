package com.omegas.prohub.calibration

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KFactorBackupRetentionTest {
    private fun fixtureFile(dir: File, name: String, modifiedAt: Long): File =
        File(dir, name).apply {
            writeText("test-only backup fixture")
            assertTrue(setLastModified(modifiedAt))
        }

    @Test
    fun `automatic cleanup never deletes manually saved K curves`() {
        val dir = Files.createTempDirectory("omegas-k-factor-backup-test").toFile()
        try {
            val manual = (0 until 4).map { index ->
                fixtureFile(dir, "MANUAL-$index-abcd1234.json", 1_700_000_000_000L + index)
            }
            val automatic = (0 until 40).map { index ->
                fixtureFile(dir, "automatic-$index.json", 1_700_000_010_000L + index)
            }
            val unrelated = fixtureFile(dir, "backup-write-in-progress.tmp", 1_700_000_020_000L)

            KFactorBackupRetention.pruneAutomatic(dir)

            manual.forEach { assertTrue("Manual backup was silently deleted: ${it.name}", it.isFile) }
            automatic.take(10).forEach { assertFalse("Old auto-backup should rotate: ${it.name}", it.exists()) }
            automatic.drop(10).forEach { assertTrue("New auto-backup should remain: ${it.name}", it.isFile) }
            assertTrue("Non-backup files must not be removed", unrelated.isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `all manual backups stay selectable when automatic history exceeds thirty`() {
        val dir = Files.createTempDirectory("omegas-k-factor-list-test").toFile()
        try {
            val manual = (0 until 35).map { index ->
                fixtureFile(dir, "MANUAL-$index-abcd1234.json", 1_700_000_000_000L + index)
            }
            val automatic = (0 until 40).map { index ->
                fixtureFile(dir, "automatic-$index.json", 1_700_000_010_000L + index)
            }

            val visible = KFactorBackupRetention.visibleFiles(dir.listFiles())
            assertEquals(65, visible.size)
            manual.forEach { assertTrue("Saved curve absent from restore selector: ${it.name}", it in visible) }
            automatic.take(10).forEach { assertFalse("Old auto-backup should not crowd selector: ${it.name}", it in visible) }
            automatic.drop(10).forEach { assertTrue("Recent auto-backup absent from selector: ${it.name}", it in visible) }
        } finally {
            dir.deleteRecursively()
        }
    }
}
