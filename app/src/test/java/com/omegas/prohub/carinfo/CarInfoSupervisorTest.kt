package com.omegas.prohub.carinfo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarInfoSupervisorTest {
    private var now = 1_000L
    private var bindResult = true
    private var bindCalls = 0
    private var unbindCalls = 0
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    private fun supervisor() = CarInfoSupervisor(
        clock = { now },
        bind = { bindCalls += 1; bindResult },
        unbind = { unbindCalls += 1 },
        schedule = { delay, action -> scheduled += delay to action },
    )

    private fun runScheduled() {
        val pending = scheduled.toList()
        scheduled.clear()
        pending.forEach { it.second() }
    }

    @Test
    fun `desligado nao tenta ligar`() {
        val s = supervisor()
        val json = s.json()
        assertFalse(json.getBoolean("enabled"))
        assertFalse(json.getBoolean("connected"))
        assertEquals("desligado", json.getString("state"))
        assertEquals(0, bindCalls)
        assertEquals(JSONObject.NULL, json.get("sample"))
        assertEquals(JSONObject.NULL, json.get("lastSampleAtMs"))
        assertTrue(json.getBoolean("readOnly"))
    }

    @Test
    fun `ligar e conectar zera falhas`() {
        val s = supervisor()
        s.start()
        assertEquals(1, bindCalls)
        assertEquals("conectando", s.json().getString("state"))
        s.onConnected()
        val json = s.json()
        assertTrue(json.getBoolean("connected"))
        assertEquals("conectado", json.getString("state"))
        assertEquals("", json.getString("lastError"))
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `bind recusado vira indisponivel com nova tentativa espacada`() {
        bindResult = false
        val s = supervisor()
        s.start()
        assertEquals("indisponivel", s.json().getString("state"))
        assertEquals(1, scheduled.size)
        assertEquals(CarInfoSupervisor.BASE_RETRY_MS, scheduled[0].first)
        runScheduled()
        assertEquals(2, bindCalls)
        assertEquals(CarInfoSupervisor.BASE_RETRY_MS * 2, scheduled[0].first)
        runScheduled()
        assertEquals(CarInfoSupervisor.BASE_RETRY_MS * 4, scheduled[0].first)
        repeat(20) { runScheduled() }
        assertEquals(CarInfoSupervisor.MAX_RETRY_MS, scheduled[0].first)
        assertTrue(s.json().getString("lastError").isNotBlank())
    }

    @Test
    fun `queda depois de conectado solta o bind e reagenda`() {
        val s = supervisor()
        s.start()
        s.onConnected()
        s.onFailure("serviço morreu")
        assertFalse(s.json().getBoolean("connected"))
        assertEquals("serviço morreu", s.json().getString("lastError"))
        assertEquals(1, unbindCalls)
        assertEquals(1, scheduled.size)
        assertEquals(CarInfoSupervisor.BASE_RETRY_MS, scheduled[0].first)
    }

    @Test
    fun `amostra registra hora e contagem`() {
        val s = supervisor()
        s.start()
        s.onConnected()
        now = 5_000L
        s.onSample(CarInfoSample(speed = 47, odo = 12345, complete = true))
        now = 9_000L
        s.onSample(CarInfoSample(speed = 48, complete = true))
        val json = s.json()
        assertEquals(5_000L, json.getLong("firstSampleAtMs"))   // primeira leitura da ligação (para a dica de 60 s)
        assertEquals(2, json.getInt("samples"))
        assertEquals(9_000L, json.getLong("lastSampleAtMs"))
        assertEquals(48, json.getJSONObject("sample").getInt("speed"))
        assertEquals(JSONObject.NULL, json.getJSONObject("sample").get("odo"))
    }

    @Test
    fun `desligar cancela tentativa pendente`() {
        bindResult = false
        val s = supervisor()
        s.start()
        s.stop()
        runScheduled()
        assertEquals(1, bindCalls)
        assertEquals("desligado", s.json().getString("state"))
    }

    @Test
    fun `tentativa antiga nao vale depois de religar`() {
        bindResult = false
        val s = supervisor()
        s.start()
        val stale = scheduled.toList()
        scheduled.clear()
        s.stop()
        bindResult = true
        s.start()
        s.onConnected()
        stale.forEach { it.second() }
        assertEquals(2, bindCalls)
        assertTrue(s.json().getBoolean("connected"))
    }

    @Test
    fun `falha de quem ja foi desligado e ignorada`() {
        val s = supervisor()
        s.start()
        s.stop()
        s.onFailure("tarde demais")
        assertTrue(scheduled.isEmpty())
        assertEquals("desligado", s.json().getString("state"))
    }

    @Test
    fun `ligar duas vezes nao duplica o bind`() {
        val s = supervisor()
        s.start()
        s.start()
        assertEquals(1, bindCalls)
    }
}
