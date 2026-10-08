package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Trava curva-salvamento-so-manual (regra 15): nome didático e pasta Curva do arquivo salvo pelo dono. */
class KFactorCurveFileNameTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun nomeDidaticoComDataHoraLocalEPontos() {
        // 2026-10-08 19:42:07 UTC
        val ms = java.time.Instant.parse("2026-10-08T19:42:07Z").toEpochMilli()
        assertEquals("Curva K - 08-10-2026 19h42m07s - 30 pontos - salva manualmente.json", KFactorCurveFileName.of(ms, 30, utc))
    }

    @Test
    fun semCaractereInvalidoEPastaCurva() {
        val name = KFactorCurveFileName.of(1_700_000_000_000L, 30, utc)
        assertFalse(Regex("""[\\/:*?"<>|]""").containsMatchIn(name))
        assertTrue(Regex("""^Curva K - \d{2}-\d{2}-\d{4} \d{2}h\d{2}m\d{2}s - \d+ pontos - salva manualmente\.json$""").matches(name))
        assertEquals("Curva", KFactorCurveFileName.PUBLIC_SUBFOLDER)
    }
}
