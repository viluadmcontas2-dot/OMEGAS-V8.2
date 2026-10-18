package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.EcuParameterSnapshot.Entry
import com.omegas.prohub.autocal.EcuParameterSnapshot.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payloads reais: ProgBase, PortmonLOGNOVO (12/08/2026), leitura da conexão. Só leitura: o leitor nem recebe função de escrita. */
class EcuParameterSnapshotTest {
    private fun entry(name: String) = EcuParameterSnapshot.CATALOG.first { it.name == name }
    private fun bytes(vararg v: Int) = v

    @Test fun `ANTI_STALLO decodifica cinco valores de 16 bits`() {
        val values = EcuParameterSnapshot.decode(entry("ANTI_STALLO"), bytes(64, 6, 100, 0, 220, 5, 2, 0, 20, 0))
        assertEquals(listOf(1600, 100, 1500, 2, 20), values)
    }

    @Test fun `escalar de 16 bits pelo tamanho da resposta`() {
        assertEquals(listOf(1200), EcuParameterSnapshot.decode(entry("GIRI_MIN_CAMBIO"), bytes(176, 4)))
        assertEquals(listOf(100), EcuParameterSnapshot.decode(entry("SMAGRIMENTO_RIENTRO_CUTOFF"), bytes(100)))
    }

    @Test fun `escalar com sinal`() {
        assertEquals(listOf(50), EcuParameterSnapshot.decode(entry("CORR_ARRICCHIMENTO"), bytes(50)))
        assertEquals(listOf(-2), EcuParameterSnapshot.decode(entry("CORR_ARRICCHIMENTO"), bytes(0xFE)))
    }

    @Test fun `tamanho de resposta errado vira desconhecido e nao zero`() {
        assertNull(EcuParameterSnapshot.decode(entry("ANTI_STALLO"), bytes(64, 6, 100)))
        assertNull(EcuParameterSnapshot.decode(entry("GIRI_MIN_CAMBIO"), bytes()))
    }

    @Test fun `catalogo cobre os parametros do estudo e nao repete nome`() {
        val names = EcuParameterSnapshot.CATALOG.map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (n in listOf("ANTI_STALLO", "GIRI_MIN_CAMBIO", "RITARDO_CAMBIO", "SMAGRIMENTO_RIENTRO_CUTOFF",
            "NUMERO_INIETTATE_SMAGRIMENTO", "GIRI_TEMPO_CUTOFF", "CALIBRATION_VAL_1", "MAX_RPM_FOR_AUTOCAL", "DELTA_ENG_SPD_THD")) {
            assertTrue("falta $n", n in names)
        }
        assertEquals(0x0133, entry("ANTI_STALLO").address)
        assertEquals(0x0010, entry("GIRI_MIN_CAMBIO").address)
    }

    @Test fun `NACK e falha de transporte ficam registrados e nada vira zero`() {
        val items = EcuParameterSnapshot.run { e ->
            when (e.name) {
                "ANTI_STALLO" -> ReadOutcome.Ok(bytes(64, 6, 100, 0, 220, 5, 2, 0, 20, 0))
                "GIRI_MIN_CAMBIO" -> ReadOutcome.Nack(0xCA)
                "RITARDO_CAMBIO" -> ReadOutcome.Failed("timeout")
                else -> ReadOutcome.Nack(0xCA)
            }
        }
        fun item(n: String) = items.first { it.entry.name == n }
        assertEquals("OK", item("ANTI_STALLO").state)
        assertEquals(listOf(1600, 100, 1500, 2, 20), item("ANTI_STALLO").values)
        assertEquals("NACK", item("GIRI_MIN_CAMBIO").state)
        assertNull(item("GIRI_MIN_CAMBIO").values)
        assertEquals("ERRO_TRANSPORTE", item("RITARDO_CAMBIO").state)
        assertNull(item("RITARDO_CAMBIO").values)
        assertEquals(EcuParameterSnapshot.CATALOG.size, items.size)
    }

    @Test fun `leitura que falha de forma inesperada nao derruba as demais`() {
        val items = EcuParameterSnapshot.run { e ->
            if (e.name == "ANTI_STALLO") throw IllegalStateException("cabo") else ReadOutcome.Ok(IntArray(e.width * e.count))
        }
        assertEquals("ERRO_TRANSPORTE", items.first { it.entry.name == "ANTI_STALLO" }.state)
        assertEquals("OK", items.first { it.entry.name == "GIRI_MIN_CAMBIO" }.state)
    }

    @Test fun `json da sessao traz valores e deixa desconhecido como nulo`() {
        val items = EcuParameterSnapshot.run { e ->
            if (e.name == "ANTI_STALLO") ReadOutcome.Ok(bytes(64, 6, 100, 0, 220, 5, 2, 0, 20, 0)) else ReadOutcome.Nack(0xCA)
        }
        val json = EcuParameterSnapshot.toJson(items, "AUTO_START")
        assertEquals("AUTO_START", json.getString("trigger"))
        assertEquals(1, json.getInt("okCount"))
        assertEquals(EcuParameterSnapshot.CATALOG.size - 1, json.getInt("unknownCount"))
        val params = json.getJSONObject("params")
        assertEquals(1500, params.getJSONObject("ANTI_STALLO").getJSONArray("values").getInt(2))
        assertEquals(0x0133, params.getJSONObject("ANTI_STALLO").getInt("address"))
        assertTrue(params.getJSONObject("GIRI_MIN_CAMBIO").isNull("values"))
        assertEquals("NACK", params.getJSONObject("GIRI_MIN_CAMBIO").getString("state"))
    }
}
