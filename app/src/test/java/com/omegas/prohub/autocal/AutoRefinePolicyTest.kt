package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.AutoRefinePolicy.Mode
import com.omegas.prohub.autocal.AutoRefinePolicy.Verdict
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decisão do dono (2026-10-16, D1): depois de cada AutoMatch da ECU o app pode refinar a Curva K sozinho, SEM teto por ponto,
 * só dentro das travas absolutas: 0,8 ≤ K ≤ 1,8; com Mapa K acima de 200 em alguma célula, K ≤ 1,5. Uma vez por AutoMatch.
 */
class AutoRefinePolicyTest {
    private val one = 16384
    private fun curve(vararg changes: Pair<Int, Int>, base: Int = one) = IntArray(30) { base }.also { c -> changes.forEach { (i, v) -> c[i] = v } }
    private fun raw(k: Double) = (k * 16384).toInt()

    private fun decide(
        current: IntArray? = curve(), ideal: IntArray? = curve(0 to raw(1.10), 1 to raw(1.10), 2 to raw(1.10)),
        mode: Mode = Mode.ON, map: Int? = 180, epoch: String = "e1", last: String? = null, available: Boolean = true,
    ) = AutoRefinePolicy.decide(mode, available, current, ideal, map, epoch, last)

    @Test fun `desligado nunca decide gravar`() {
        val d = decide(mode = Mode.OFF)
        assertEquals(Verdict.SKIP, d.verdict)
        assertEquals("DESLIGADO", d.reason)
    }

    @Test fun `o mesmo AutoMatch nao e refinado duas vezes`() {
        val d = decide(epoch = "e1", last = "e1")
        assertEquals(Verdict.SKIP, d.verdict)
        assertEquals("MESMO_AUTOMATCH", d.reason)
    }

    @Test fun `sem evidencia do motor ou com curva invalida nao grava`() {
        assertEquals("SEM_EVIDENCIA", decide(available = false).reason)
        assertEquals("CURVA_INVALIDA", decide(current = IntArray(29)).reason)
        assertEquals("CURVA_INVALIDA", decide(ideal = null).reason)
    }

    @Test fun `ligado grava os nos que o motor quer mudar`() {
        val d = decide(mode = Mode.ON)
        assertEquals(Verdict.WRITE, d.verdict)
        assertEquals(listOf(0, 1, 2), d.points.map { it.index })
        assertEquals(raw(1.10), d.points[0].targetRaw)
        assertEquals(one, d.points[0].currentRaw)
        assertFalse(d.points.any { it.clamped })
    }

    @Test fun `sombra calcula o mesmo mas nao manda gravar`() {
        val d = decide(mode = Mode.SHADOW)
        assertEquals(Verdict.SHADOW_WOULD_WRITE, d.verdict)
        assertEquals(3, d.points.size)
    }

    @Test fun `sem teto por ponto - salto grande dentro das travas passa inteiro`() {
        val d = decide(ideal = curve(5 to raw(1.60)), current = curve(5 to raw(1.0)))
        assertEquals(raw(1.60), d.points.single().targetRaw)
    }

    @Test fun `trava de cima 1,8 e, com mapa acima de 200, 1,5`() {
        assertEquals(29491, AutoRefinePolicy.ceilingRaw(180))
        assertEquals(29491, AutoRefinePolicy.ceilingRaw(200))
        assertEquals(24576, AutoRefinePolicy.ceilingRaw(201))
        assertEquals(24576, AutoRefinePolicy.ceilingRaw(null)) // mapa desconhecido: o mais cauteloso
        val high = decide(ideal = curve(0 to raw(1.9)), map = 180).points.single()
        assertEquals(29491, high.targetRaw); assertTrue(high.clamped)
        val capped = decide(ideal = curve(0 to raw(1.9)), map = 220).points.single()
        assertEquals(24576, capped.targetRaw); assertTrue(capped.clamped)
    }

    @Test fun `trava de baixo 0,8`() {
        val low = decide(ideal = curve(3 to raw(0.5))).points.single()
        assertEquals(13108, low.targetRaw); assertTrue(low.clamped)
    }

    @Test fun `mudanca menor que 0,5 por cento nao vale uma escrita`() {
        val d = decide(ideal = curve(0 to one + 40, 1 to one - 30))
        assertEquals(Verdict.SKIP, d.verdict)
        assertEquals("NADA_A_CORRIGIR", d.reason)
    }

    @Test fun `trava nunca inverte a direcao que o motor pediu`() {
        // ECU deixou o nó em 1,9 (acima da trava) e o motor quer 1,95: a trava puxaria para baixo, contra o pedido. Fica como está.
        val d = decide(current = curve(0 to raw(1.9)), ideal = curve(0 to raw(1.95)))
        assertEquals(Verdict.SKIP, d.verdict)
        // Já o motor querendo 1,5 num nó que está em 1,9 é uma descida legítima e passa.
        val down = decide(current = curve(0 to raw(1.9)), ideal = curve(0 to raw(1.5)))
        assertEquals(raw(1.5), down.points.single().targetRaw)
    }

    @Test fun `decisao informa as travas usadas`() {
        val d = decide(map = 230)
        assertEquals(13108, d.floorRaw)
        assertEquals(24576, d.ceilingRaw)
    }

    private fun mapCache(rows: String, extra: String = "[]", complete: Boolean = true) =
        JSONObject().put("complete", complete).put("rows", JSONArray(rows)).put("extraRow", JSONArray(extra))

    @Test fun `maior celula do Mapa K vem das linhas e da linha extra`() {
        assertEquals(220, AutoRefinePolicy.maxMapCell(mapCache("[[10,220],[5,6]]")))
        assertEquals(250, AutoRefinePolicy.maxMapCell(mapCache("[[10,20]]", extra = "[250]")))
    }

    @Test fun `mapa sem leitura completa e desconhecido - nunca zero`() {
        assertNull(AutoRefinePolicy.maxMapCell(null))
        assertNull(AutoRefinePolicy.maxMapCell(JSONObject()))
        assertNull(AutoRefinePolicy.maxMapCell(mapCache("[[10,220]]", complete = false)))
        assertNull(AutoRefinePolicy.maxMapCell(mapCache("[]")))
    }
}
