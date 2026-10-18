package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Último param_snapshot da sessão USB para a aba Avançado: valor de outra sessão ou nunca lido = não lido (nunca 0). */
class AdvancedParamsStoreTest {
    private fun snapshot(): JSONObject {
        val items = EcuParameterSnapshot.run { e ->
            when (e.name) {
                "FLAG_CONF3" -> EcuParameterSnapshot.ReadOutcome.Ok(intArrayOf(0, 0, 0, 0))
                else -> EcuParameterSnapshot.ReadOutcome.Nack(0xCA)
            }
        }
        return EcuParameterSnapshot.toJson(items, "AUTO_START")
    }

    @Test fun `antes de qualquer leitura nada foi lido`() {
        val json = AdvancedParamsStore().json(1L)
        assertFalse(json.getBoolean("read"))
        assertEquals(0, json.getJSONObject("params").length())
    }

    @Test fun `depois do param_snapshot da sessao devolve os valores lidos e o gatilho`() {
        val store = AdvancedParamsStore()
        store.update(7L, snapshot())
        val json = store.json(7L)
        assertTrue(json.getBoolean("read"))
        assertEquals("AUTO_START", json.getString("trigger"))
        val flag = json.getJSONObject("params").getJSONObject("FLAG_CONF3")
        assertEquals("OK", flag.getString("state"))
        assertEquals(2, flag.getJSONArray("values").length())
        assertTrue(json.getJSONObject("params").getJSONObject("ANTI_STALLO").isNull("values"))
    }

    @Test fun `outra sessao USB nao herda valor antigo`() {
        val store = AdvancedParamsStore()
        store.update(7L, snapshot())
        assertFalse(store.json(8L).getBoolean("read"))
        assertFalse(store.json(0L).getBoolean("read"))
    }

    @Test fun `leitura ao vivo do FLAG_CONF3 substitui so ele e marca o gatilho`() {
        val store = AdvancedParamsStore()
        store.update(7L, snapshot())
        store.replaceVector(7L, "FLAG_CONF3", 0x00FA, intArrayOf(0x20, 0), "AO_VIVO")
        val json = store.json(7L)
        val values = json.getJSONObject("params").getJSONObject("FLAG_CONF3").getJSONArray("values")
        assertEquals(0x20, values.getInt(0))
        assertEquals("NACK", json.getJSONObject("params").getJSONObject("FLAG_CONF1").getString("state"))
    }

    @Test fun `leitura ao vivo numa sessao sem snapshot cria so aquele valor`() {
        val store = AdvancedParamsStore()
        store.replaceVector(9L, "FLAG_CONF3", 0x00FA, intArrayOf(0, 0), "AO_VIVO")
        val json = store.json(9L)
        assertTrue(json.getBoolean("read"))
        assertEquals(1, json.getJSONObject("params").length())
    }
}
