package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionByteCapTest {
    private fun info(name: String, bytes: Long, at: Long, active: Boolean = false, published: Boolean = true) =
        SessionByteCap.Info(name, bytes, at, active, published)

    @Test
    fun `acima do teto normal sessao sem copia publica nao e apagada`() {
        val entries = listOf(
            info("nao-publicada-antiga", 600, 1, published = false),
            info("publicada", 400, 5),
            info("nova", 400, 9),
        )
        // total 1400, teto 1000, emergência 2000: a mais antiga é a não publicada, mas sai a publicada
        assertEquals(listOf("publicada"), SessionByteCap.select(entries, 1_000, emergencyCapBytes = 2_000))
        // mesmo sem publicada suficiente para caber, a não publicada fica
        assertEquals(listOf("publicada", "nova"), SessionByteCap.select(entries, 100, emergencyCapBytes = 2_000))
    }

    @Test
    fun `acima do teto de emergencia a nao publicada mais antiga sai`() {
        val entries = listOf(
            info("nao-publicada-antiga", 600, 1, published = false),
            info("nao-publicada-nova", 600, 2, published = false),
            info("publicada", 400, 5),
        )
        // total 1600 > emergência 1000: primeiro as publicadas, depois a não publicada mais antiga
        assertEquals(
            listOf("publicada", "nao-publicada-antiga"),
            SessionByteCap.select(entries, 500, emergencyCapBytes = 1_000),
        )
    }

    @Test
    fun `sessao em publicacao nunca e apagada nem na emergencia`() {
        val entries = listOf(
            info("parada-na-fila-do-publisher", 5_000, 1, active = true, published = false),
            info("publicada", 400, 5),
        )
        assertEquals(listOf("publicada"), SessionByteCap.select(entries, 100, emergencyCapBytes = 1_000))
    }

    @Test
    fun `teto de emergencia de 3 GB`() {
        assertEquals(3L * 1024L * 1024L * 1024L, SessionByteCap.EMERGENCY_TOTAL_BYTES_CAP)
    }

    @Test
    fun `abaixo do teto nada e apagado`() {
        val entries = listOf(info("a", 100, 1), info("b", 100, 2))
        assertTrue(SessionByteCap.select(entries, 1_000).isEmpty())
    }

    @Test
    fun `acima do teto apaga a sessao fechada mais antiga primeiro e para quando cabe`() {
        val entries = listOf(
            info("nova", 400, 30),
            info("antiga", 400, 10),
            info("meio", 400, 20),
        )
        // total 1200, teto 800: basta apagar a mais antiga
        assertEquals(listOf("antiga"), SessionByteCap.select(entries, 800))
        // teto 400: apaga as duas mais antigas
        assertEquals(listOf("antiga", "meio"), SessionByteCap.select(entries, 400))
    }

    @Test
    fun `a sessao que esta gravando nunca entra mesmo sendo a mais antiga`() {
        val entries = listOf(
            info("gravando", 900, 1, active = true),
            info("fechada", 400, 5),
        )
        assertEquals(listOf("fechada"), SessionByteCap.select(entries, 500))
    }

    @Test
    fun `teto de 1,5 GB`() {
        assertEquals(1_610_612_736L, SessionByteCap.TOTAL_BYTES_CAP)
    }

    @Test
    fun `duas sessoes no mesmo milissegundo recebem ids diferentes`() {
        val stamp = "2026-10-03_12-00-00-123"
        val first = SessionIdFormat.build(stamp, "abcdef0123456789", 1)
        val second = SessionIdFormat.build(stamp, "abcdef0123456789", 2)
        assertTrue(first != second)
        assertTrue(first.startsWith("session_2026-10-03_12-00-00"))
        assertTrue(first.endsWith("_abcdef01"))
        // a exportação nomeia o ZIP pela data (yyyy-MM-dd_HH-mm): o id precisa continuá-la
        val match = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})[_-]([0-9]{2})-([0-9]{2})").find(first)
        assertEquals("2026", match?.groupValues?.get(1))
        assertEquals("00", match?.groupValues?.get(5))
    }
}
