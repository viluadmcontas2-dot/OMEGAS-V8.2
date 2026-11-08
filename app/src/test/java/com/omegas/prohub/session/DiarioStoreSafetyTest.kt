package com.omegas.prohub.session

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.NotDirectoryException

class DiarioStoreSafetyTest {
    @Test
    fun missing_file_is_an_empty_diary() {
        val directory = Files.createTempDirectory("omegas-diario-missing").toFile()
        try {
            assertTrue(DiarioStore(File(directory, "diario.jsonl")).load().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun inaccessible_parent_is_not_reported_as_an_empty_diary() {
        val directory = Files.createTempDirectory("omegas-diario-parent").toFile()
        try {
            val parentFile = File(directory, "not-a-directory")
            parentFile.writeText("sou um arquivo")
            val error = runCatching {
                DiarioStore(File(parentFile, "diario.jsonl")).load()
            }.exceptionOrNull()

            assertTrue(
                "Caminho inacessível foi confundido com diário ausente",
                error is NotDirectoryException,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun one_corrupt_line_does_not_discard_other_valid_events() {
        val directory = Files.createTempDirectory("omegas-diario-lines").toFile()
        try {
            val file = File(directory, "diario.jsonl")
            file.writeText(
                "{\"seq\":1,\"leituras\":1}\n" +
                    "{linha corrompida}\n" +
                    "{\"seq\":2,\"leituras\":2}\n",
            )

            val events = DiarioStore(file).load()

            assertEquals(listOf(1, 2), events.map { it.getInt("seq") })
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test
    fun save_replaces_an_existing_file_with_the_complete_new_diary() {
        val directory = Files.createTempDirectory("omegas-diario-replace").toFile()
        try {
            val file = File(directory, "diario.jsonl")
            file.writeText("conteudo anterior\n")

            DiarioStore(file).save(listOf(JSONObject().put("seq", 7).put("leituras", 7)))

            assertEquals("{\"seq\":7,\"leituras\":7}\n", file.readText())
            assertTrue(!File(directory, ".diario.jsonl.tmp").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failed_replacement_keeps_existing_directory_contents() {
        val directory = Files.createTempDirectory("omegas-diario-preserve").toFile()
        try {
            val targetDirectory = File(directory, "diario.jsonl")
            assertTrue(targetDirectory.mkdir())
            val sentinel = File(targetDirectory, "preservar")
            sentinel.writeText("conteudo intacto")

            val error = runCatching {
                DiarioStore(targetDirectory).save(listOf(JSONObject().put("seq", 8).put("leituras", 8)))
            }.exceptionOrNull()

            assertNotNull("Falha de substituição foi ocultada", error)
            assertEquals("conteudo intacto", sentinel.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

}
