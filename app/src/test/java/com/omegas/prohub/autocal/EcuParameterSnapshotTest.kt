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

    // ---- Ferramentas > Avançado (2026-10-18): flags, progressões e giri por benzina entram no mesmo param_snapshot ----
    @Test fun `FLAG_CONF3 FLAG_CONF1 FLAG_CONF2 decodificam duas palavras de 16 bits (valores reais do LOGNOVO)`() {
        assertEquals(listOf(0, 0), EcuParameterSnapshot.decode(entry("FLAG_CONF3"), bytes(0, 0, 0, 0)))
        assertEquals(listOf(0x0020, 0), EcuParameterSnapshot.decode(entry("FLAG_CONF3"), bytes(0x20, 0, 0, 0)))
        assertEquals(listOf(0x2486, 0x1051), EcuParameterSnapshot.decode(entry("FLAG_CONF1"), bytes(0x86, 0x24, 0x51, 0x10)))
        assertEquals(listOf(1, 0), EcuParameterSnapshot.decode(entry("FLAG_CONF2"), bytes(1, 0, 0, 0)))
        assertEquals(250, entry("FLAG_CONF3").address)
        assertEquals(192, entry("FLAG_CONF2").address)
        assertEquals(3, entry("FLAG_CONF1").address)
        assertEquals(EcuParameterSnapshot.Kind.VECTOR, entry("FLAG_CONF3").kind)
    }

    @Test fun `FLAG_CONF3 com tamanho errado e desconhecido`() {
        assertNull(EcuParameterSnapshot.decode(entry("FLAG_CONF3"), bytes(0, 0)))
    }

    @Test fun `GIRI_PER_BENZINA le dois u16 (0 e 8500 no LOGNOVO)`() {
        assertEquals(161, entry("GIRI_PER_BENZINA").address)
        assertEquals(listOf(0, 8500), EcuParameterSnapshot.decode(entry("GIRI_PER_BENZINA"), bytes(0, 0, 0x34, 0x21)))
    }

    @Test fun `PARAM_PROGRESSIONI sao 4 linhas de 3 u16 lidas por 2A endereco 0x0134 (OBS-FIO)`() {
        val rows = EcuParameterSnapshot.CATALOG.filter { it.name.startsWith("PARAM_PROGRESSIONI_L") }
        assertEquals(listOf("PARAM_PROGRESSIONI_L0", "PARAM_PROGRESSIONI_L1", "PARAM_PROGRESSIONI_L2", "PARAM_PROGRESSIONI_L3"), rows.map { it.name })
        assertTrue(rows.all { it.address == 0x0134 && it.kind == EcuParameterSnapshot.Kind.MATRIX_ROW && it.width == 2 && it.count == 3 })
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.index })
        assertEquals(listOf(0, 4, 500), EcuParameterSnapshot.decode(rows[0], bytes(0, 0, 4, 0, 0xF4, 1)))
        assertEquals(listOf(25, 4, 500), EcuParameterSnapshot.decode(rows[3], bytes(25, 0, 4, 0, 0xF4, 1)))
    }

    @Test fun `pedido 2A de linha de parametro bate com o fio`() {
        val frame = EcuParameterSnapshot.matrixRowRequest(0x0134, 0)
        assertEquals("2A 34 01 00 5F", frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        assertEquals("2A 34 01 03 62", EcuParameterSnapshot.matrixRowRequest(0x0134, 3).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
    }

    @Test fun `NUMERO_INJ_BENZINA_CUTOFF continua no catalogo (um byte, 0x006A)`() {
        assertEquals(0x006A, entry("NUMERO_INJ_BENZINA_CUTOFF").address)
        assertEquals(listOf(0), EcuParameterSnapshot.decode(entry("NUMERO_INJ_BENZINA_CUTOFF"), bytes(0)))
    }

    // ---- revisão independente, item 4: várias unidades por grupo; falha de um grupo não descarta os outros ----
    @Test fun `grupos cobrem o catalogo uma vez so e o Avancado fica separado do nucleo`() {
        val all = EcuParameterSnapshot.GROUPS.flatten().map { it.name }
        assertEquals(EcuParameterSnapshot.CATALOG.map { it.name }.sorted(), all.sorted())
        assertEquals(all.size, all.toSet().size)
        val avancado = EcuParameterSnapshot.GROUPS.first { g -> g.any { it.name == "FLAG_CONF3" } }.map { it.name }
        assertTrue(avancado.containsAll(listOf("FLAG_CONF1", "FLAG_CONF2", "FLAG_CONF3", "PARAM_PROGRESSIONI_L0", "PARAM_PROGRESSIONI_L3")))
        assertTrue("núcleo misturado com o Avançado", avancado.none { it == "ANTI_STALLO" || it == "GIRI_MIN_CAMBIO" || it == "MAX_AUTOMATCH" })
    }

    @Test fun `nenhum grupo passa de 20 leituras (cada unidade da serial e curta)`() {
        assertTrue(EcuParameterSnapshot.GROUPS.size >= 3)
        EcuParameterSnapshot.GROUPS.forEach { assertTrue("grupo com ${it.size}", it.size in 1..20) }
    }

    @Test fun `um grupo que estoura nao descarta os outros - o snapshot sai completo`() {
        val quebrado = EcuParameterSnapshot.GROUPS.first { g -> g.any { it.name == "FLAG_CONF3" } }
        val items = EcuParameterSnapshot.runGroups { group ->
            if (group === quebrado) throw IllegalStateException("ECU ocupada")
            EcuParameterSnapshot.run(group) { ReadOutcome.Ok(IntArray(it.width * it.count) { 1 }) }
        }
        assertEquals(EcuParameterSnapshot.CATALOG.size, items.size)
        val porNome = items.associateBy { it.entry.name }
        assertEquals("ERRO_TRANSPORTE", porNome.getValue("FLAG_CONF3").state)
        assertNull(porNome.getValue("FLAG_CONF3").values)
        assertTrue(porNome.getValue("FLAG_CONF3").detail.contains("ECU ocupada"))
        assertEquals("OK", porNome.getValue("ANTI_STALLO").state)
        assertEquals("OK", porNome.getValue("MAX_AUTOMATCH").state)
        assertEquals(items.size - quebrado.size, items.count { it.state == "OK" })
    }

    @Test fun `cada grupo e lido uma vez, na ordem, sem relogio`() {
        val vistos = ArrayList<Int>()
        EcuParameterSnapshot.runGroups { group -> vistos += group.size; EcuParameterSnapshot.run(group) { ReadOutcome.Nack(0xCA) } }
        assertEquals(EcuParameterSnapshot.GROUPS.map { it.size }, vistos)
    }
}

