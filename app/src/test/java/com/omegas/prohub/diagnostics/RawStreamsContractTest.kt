package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato Kotlin <-> Python da camada bruta: a sessão sintética abaixo gera SEMPRE os mesmos arquivos .tsv da pasta raw,
 * guardados em `tests/fixtures/export-v2/raw/` e lidos por `tools/sessoes/export_v2.py`. Mudou o formato de propósito?
 * Rode com OMEGAS_REGEN_FIXTURES=1, ajuste o Python e `docs/EXPORT-SESSOES.md`.
 */
class RawStreamsContractTest {
    private val fixture = File("../tests/fixtures/export-v2/raw")

    private fun frame(i: Int, level: Int, moving: Boolean): ByteArray {
        val rpm = if (moving) 2200 + (i % 7) * 10 else 900 + (i % 3)
        val gas = if (moving) 3700 else 1950          // contagens (x0,00256 ms)
        val petrol = if (moving) 1500 else 1200
        val map = if (moving) 620 else 410
        val f = ByteArray(34)
        f[0] = rpm.toByte(); f[1] = (rpm shr 8).toByte()
        f[2] = 0x32; f[3] = 0x1F                          // bytes 2-3: lentos, nao decodificados pelo app
        f[6] = gas.toByte(); f[7] = (gas shr 8).toByte()
        f[8] = petrol.toByte(); f[9] = (petrol shr 8).toByte()
        f[11] = 0x90.toByte(); f[12] = 69; f[13] = level.toByte()
        f[14] = 0xE0.toByte(); f[15] = 0x07; f[16] = 60
        f[17] = map.toByte(); f[18] = (map shr 8).toByte(); f[19] = 224.toByte()
        return f
    }

    @Test
    fun `fluxos brutos sinteticos batem com o fixture que o Python le`() {
        val out = File(System.getProperty("java.io.tmpdir"), "omegas-raw-contract-" + System.nanoTime())
        val s = RawStreams(out, sessionKey = "S_abcd1234_9f8e7d6c_7000", bootId = "9f8e7d6c", startElapsedMs = 7_000_000L)
        val wall0 = 1_790_000_000_000L
        for (i in 0 until 150) {
            val moving = i >= 30
            s.frame(7_000_000L + i * 66L, frame(i, level = 126 + (i % 2), moving = moving), wall0 + i * 66L, i + 1L, 0L)
        }
        s.tx(7_000_100L, "READ_ONLY", "param LO_PASS", byteArrayOf(0x0A, 0x14, 0x01, 0x01, 0x20), true, 0x53, byteArrayOf(0x33, 0x13), 12, "")
        s.mm(7_001_000L, wall0 + 1_000L, "car_info", """{"speed":54,"complete":true}""")
        s.mm(7_002_000L, wall0 + 2_000L, "tx17", """{"tx":17,"bytes":4,"hex":"01020304"}""")
        s.close()
        val names = out.list()!!.sorted()
        assertEquals(listOf("frames_48_01.0001.tsv", "heartbeat.0001.tsv", "mm.0001.tsv", "tx.0001.tsv"), names)
        if (!fixture.isDirectory || System.getenv("OMEGAS_REGEN_FIXTURES") == "1") {
            fixture.mkdirs()
            names.forEach { File(out, it).copyTo(File(fixture, it), overwrite = true) }
        }
        names.forEach { assertEquals("$it mudou; veja o comentario da classe", File(fixture, it).readText(), File(out, it).readText()) }
        assertTrue(File(fixture, "heartbeat.0001.tsv").readLines().size >= 3)
    }
}
