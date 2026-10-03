package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classe 3: as sessões reais do proprietário passam pelo detector de apagão.
 *
 * Nenhuma das três sessões tem um apagão completo (RPM 0 com a chave ligada); todas têm
 * "quase apagões": o RPM despenca de condução para 400–590 rpm e volta (embreagem,
 * quebra-molas, reentrada do GNV depois do corte). As contagens abaixo foram conferidas
 * quadro a quadro na extração e são o contrato do detector sobre dado real.
 */
class StallWatchRealSessionTest {
    private fun replay(name: String): StallWatch {
        val watch = StallWatch(null)
        val frames = RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name))
        frames.forEach { watch.accept(RealSessionReplaySupport.stallFrame(it)) }
        watch.tick(frames.last().t + StallWatch.RESTART_WINDOW_MS + 1_000L)
        return watch
    }

    @Test
    fun `sessao so GNV tem cinco quase apagoes e nenhum apagao`() {
        val json = replay(RealSessionReplaySupport.GNV_ONLY).json()
        assertEquals(5, json.getInt("nearCount"))
        assertEquals(0, json.getInt("count"))
        // A sessão começa com a ECU em DESLIGADO (RPM 0) antes da partida: não é apagão.
        assertEquals(0, json.getJSONObject("ignored").getInt("idleShutdowns"))
        assertFalse(json.getBoolean("pending"))
        val events = json.getJSONArray("events")
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            assertEquals("GNV", event.getString("fuelBefore"))
            assertTrue(event.getDouble("rpmMin") < StallWatch.RUNNING_RPM)
            assertTrue(event.getDouble("rpmMin") >= StallWatch.DEAD_RPM)
            assertTrue("o motor girava antes da queda", event.getDouble("rpmBefore") >= StallWatch.RUNNING_RPM)
            assertTrue(event.getDouble("petrolMs") > 0.0)
            assertTrue(event.getDouble("mapBar") > 0.0)
        }
    }

    @Test
    fun `sessao do AutoMatch nativo tem seis quase apagoes e nenhum apagao`() {
        val json = replay(RealSessionReplaySupport.AUTOMATCH).json()
        assertEquals(6, json.getInt("nearCount"))
        assertEquals(0, json.getInt("count"))
    }

    @Test
    fun `sessao de referencia tem tres quase apagoes incluindo a reentrada do GNV depois do corte`() {
        val json = replay(RealSessionReplaySupport.REFERENCE).json()
        assertEquals(3, json.getInt("nearCount"))
        assertEquals(0, json.getInt("count"))
        // Regiões onde mais acontece: faixa de Petrol Inj. com MAP mediano, para a tela.
        val regions = json.getJSONArray("regions")
        assertTrue(regions.length() >= 1)
        assertTrue(regions.getJSONObject(0).getInt("nearCount") >= 1)
    }
}
