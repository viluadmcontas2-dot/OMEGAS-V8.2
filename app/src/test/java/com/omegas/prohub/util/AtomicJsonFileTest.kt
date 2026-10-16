package com.omegas.prohub.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicJsonFileTest {
    private fun json(v: Int) = JSONObject().put("format", "teste").put("v", v).toString()

    @Test
    fun `arquivo corrompido ou vazio cai na reserva bak e nada e apagado`() {
        val dir = Files.createTempDirectory("atomic-json").toFile()
        val target = File(dir, "estado.json")
        val backup = AtomicJsonFile.backupOf(target)
        AtomicJsonFile.write(target, json(1))
        assertEquals(1, AtomicJsonFile.readJson(target)!!.getInt("v"))
        assertFalse("sem arquivo anterior não há reserva", backup.exists())
        AtomicJsonFile.write(target, json(2))
        assertEquals(2, AtomicJsonFile.readJson(target)!!.getInt("v"))
        assertEquals(json(1), AtomicJsonFile.read(backup))
        // A ignição cortou no meio: arquivo principal truncado ou vazio → vale a reserva.
        target.writeText("{\"format\":\"teste\",\"v\":")
        assertEquals(1, AtomicJsonFile.readJson(target)!!.getInt("v"))
        target.writeText("")
        assertEquals(1, AtomicJsonFile.readJson(target)!!.getInt("v"))
        // Lixo no principal nunca toma o lugar da reserva boa na gravação seguinte: vai para .corrupt.
        target.writeText("lixo")
        AtomicJsonFile.write(target, json(3))
        assertEquals(3, AtomicJsonFile.readJson(target)!!.getInt("v"))
        assertEquals("reserva boa preservada", 1, JSONObject(backup.readText()).getInt("v"))
        assertEquals("lixo", File(dir, "estado.json.corrupt").readText())
        assertFalse(File(dir, "estado.json.tmp").exists())
        // Nem o arquivo nem a reserva servem → null; quem chama registra o motivo, nada é apagado.
        target.writeText("x")
        backup.writeText("")
        assertNull(AtomicJsonFile.readJson(target))
        assertNull(AtomicJsonFile.read(target))
        assertTrue(target.exists() && backup.exists())
        dir.deleteRecursively()
    }
}
