package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.zip.GZIPOutputStream

/** Camada BRUTA sem perda (export v2): quadros 48 01 a taxa cheia, transações, multimídia e batimento, em texto colunar append-only. */
class RawStreamsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun streams(limit: Long = 16L * 1024 * 1024, queue: Int = 16_384) =
        RawStreams(File(temporary.root, "raw"), sessionKey = "S_abcd1234_boot0001_5000", bootId = "boot0001", segmentLimitBytes = limit, queueCapacity = queue)

    private fun lines(name: String): List<String> =
        File(temporary.root, "raw").listFiles { f -> f.name.startsWith("$name.") && f.name.endsWith(".tsv") }!!
            .sortedBy { it.name }.flatMap { it.readLines() }

    private fun frame(i: Int) = ByteArray(34) { ((i * 31 + it * 7) and 0xFF).toByte() }

    @Test
    fun `todo quadro gravado com os 34 bytes crus e tempo monotonico relativo`() {
        val s = streams()
        for (i in 0 until 100) s.frame(1_000L + i * 66L, frame(i), wallMs = 1_790_000_000_000L + i * 66, engineFrames = i + 1L, engineFailures = 0L)
        s.close()
        val rows = lines("frames_48_01").filter { !it.startsWith("#") }
        assertEquals(100, rows.size)
        assertEquals("0\t" + frame(0).joinToString("") { "%02x".format(it) }, rows[0])
        assertTrue(rows[99].startsWith("6534\t"))
        assertEquals(68, rows[5].substringAfter('\t').length)
    }

    @Test
    fun `arquivo comeca com cabecalho que o identifica sem depender do manifesto`() {
        val s = streams()
        s.frame(1_000, frame(1), 1L, 1L, 0L)
        s.close()
        val first = lines("frames_48_01").first()
        assertTrue(first.startsWith("#omegas-raw-v2"))
        assertTrue(first.contains("stream=frames_48_01"))
        assertTrue(first.contains("session=S_abcd1234_boot0001_5000"))
        assertTrue(first.contains("boot=boot0001"))
        assertTrue(first.contains("cols=e_ms,hex"))
    }

    @Test
    fun `batimento sai por contagem de quadros e guarda parede, monotonico e contadores`() {
        val s = streams()
        for (i in 0 until 150) s.frame(1_000L + i * 66L, frame(i), 1_790_000_000_000L + i * 66, i + 1L, 2L)
        s.close()
        val hb = lines("heartbeat").filter { !it.startsWith("#") }
        assertEquals(2, hb.size) // a cada 75 quadros, sem relogio
        val cols = hb[0].split('\t')
        assertEquals("4884", cols[0])
        assertEquals((1_790_000_000_000L + 74 * 66).toString(), cols[1])
        assertEquals("75", cols[2])
        assertEquals("2", cols[3])
    }

    @Test
    fun `transacao fora da telemetria guarda pedido, resposta, status e erro sem truncar`() {
        val s = streams()
        s.tx(2_000, "READ_ONLY", "param_snapshot 0x24", byteArrayOf(0x09, 0x24, 0x00, 0x2D), ok = true, status = 0x53, payload = ByteArray(300) { 7 }, ms = 12, error = "")
        s.tx(2_100, "MANUAL_WRITE", "k\ttab\nnovalinha", byteArrayOf(0x35), ok = false, status = -1, payload = ByteArray(0), ms = 5, error = "timeout\tx")
        s.close()
        val rows = lines("tx").filter { !it.startsWith("#") }
        assertEquals(2, rows.size)
        val a = rows[0].split('\t')
        assertEquals(listOf("0", "READ_ONLY", "83", "1", "12"), a.take(5))
        assertEquals("0924002d", a[5])
        assertEquals(600, a[6].length) // 300 bytes inteiros
        assertEquals(9, a.size)
        val b = rows[1].split('\t')
        assertEquals(9, b.size) // tab e quebra de linha do texto nao quebram a linha
        assertEquals("0", b[3])
    }

    @Test
    fun `multimidia grava cada mudanca com os dois relogios`() {
        val s = streams()
        s.mm(3_000, 1_790_000_003_000L, "car_info", """{"speed":12}""")
        s.mm(3_500, 1_790_000_003_500L, "tx17", """{"props":["0x11600207/0=3.2"]}""")
        s.close()
        val rows = lines("mm").filter { !it.startsWith("#") }
        assertEquals(listOf("0", "1790000003000", "car_info", """{"speed":12}"""), rows[0].split('\t'))
        assertEquals("tx17", rows[1].split('\t')[2])
    }

    @Test
    fun `rotaciona por tamanho sem cortar linha e sem perder quadro`() {
        val s = streams(limit = 2_000)
        for (i in 0 until 200) s.frame(1_000L + i * 66L, frame(i), 1L, i + 1L, 0L)
        s.close()
        val files = File(temporary.root, "raw").listFiles { f -> f.name.startsWith("frames_48_01.") }!!.sortedBy { it.name }
        assertTrue(files.size > 3)
        assertEquals("frames_48_01.0001.tsv", files[0].name)
        files.forEach { assertTrue(it.length() <= 2_200) ; assertTrue(it.readText().endsWith("\n")) }
        assertEquals(200, lines("frames_48_01").count { !it.startsWith("#") })
        files.drop(1).forEach { assertTrue(it.readLines().first().startsWith("#omegas-raw-v2")) }
    }

    @Test
    fun `fila cheia descarta e CONTA o descarte no batimento sem travar quem chama`() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val s = RawStreams(File(temporary.root, "raw"), "k", "b", segmentLimitBytes = 1 shl 20, queueCapacity = 8, beforeDrain = { gate.await() })
        for (i in 0 until 100) s.frame(1_000L + i * 66L, frame(i), 1L, i + 1L, 0L)
        assertTrue(s.dropped() > 0)
        gate.countDown()
        s.close()
        val kept = lines("frames_48_01").count { !it.startsWith("#") }
        assertTrue(kept < 100)
        assertTrue("quadros gravados + linhas descartadas cobrem todos os quadros", kept + s.dropped() >= 100L)
    }

    @Test
    fun `fsync por contagem de linhas e no fechamento`() {
        val s = streams()
        for (i in 0 until 500) s.frame(1_000L + i * 66L, frame(i), 1L, i + 1L, 0L)
        s.close()
        assertTrue("fsync em $s.syncCount", s.syncCount() >= 3)
    }

    @Test
    fun `quadros compactam bem em gzip (alvo da taxa cheia)`() {
        val r = Random(7)
        val s = streams()
        var rpm = 900; var gas = 400; var petrol = 1200; var map = 450
        val n = 20_000
        for (i in 0 until n) {
            rpm = (rpm + r.nextInt(41) - 20).coerceIn(700, 4500); gas = (gas + r.nextInt(21) - 10).coerceIn(0, 900)
            petrol = (petrol + r.nextInt(21) - 10).coerceIn(200, 2000); map = (map + r.nextInt(11) - 5).coerceIn(300, 900)
            val f = ByteArray(34)
            f[0] = rpm.toByte(); f[1] = (rpm shr 8).toByte(); f[6] = gas.toByte(); f[7] = (gas shr 8).toByte()
            f[8] = petrol.toByte(); f[9] = (petrol shr 8).toByte(); f[11] = 0x90.toByte(); f[12] = 40; f[13] = 127
            f[14] = 0xE0.toByte(); f[15] = 0x0A; f[16] = 60; f[17] = map.toByte(); f[18] = (map shr 8).toByte(); f[19] = 220.toByte()
            s.frame(1_000L + i * 66L, f, 1L, i + 1L, 0L)
        }
        s.close()
        val raw = File(temporary.root, "raw").listFiles { f -> f.name.startsWith("frames_48_01.") }!!.sortedBy { it.name }.joinToString("") { it.readText() }
        val bos = ByteArrayOutputStream(); GZIPOutputStream(bos).use { it.write(raw.toByteArray()) }
        val perFrame = bos.size().toDouble() / n
        println("bytes por quadro em gzip: $perFrame")
        assertTrue("$perFrame B/quadro", perFrame < 20.0)
    }
}
