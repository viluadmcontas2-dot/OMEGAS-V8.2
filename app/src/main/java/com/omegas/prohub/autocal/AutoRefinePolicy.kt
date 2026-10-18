package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Política do Refino automático depois de um AutoMatch da ECU (decisão do dono, 2026-10-16, D1).
 *
 * - SEM teto por ponto: o motor pode propor qualquer valor; só as travas absolutas limitam.
 * - Travas: 0,8 ≤ K ≤ 1,8; se alguma célula do Mapa K passa de 200, K ≤ 1,5 (mapa desconhecido = o mais cauteloso, 1,5).
 * - A trava nunca inverte a direção que o motor pediu (nó fora da trava que o motor queria subir/descer mais não é puxado ao contrário).
 * - Uma vez por AutoMatch (época); mudança < 0,5 % não vale uma escrita (0,5 % é critério deste código, não do dono).
 * - SOMBRA decide igual ao LIGADO mas nunca manda gravar: serve para ver na sessão o que seria escrito antes de ligar.
 * Função pura: quem escreve na ECU é o KFactorManager (ACK + leitura de volta).
 */
object AutoRefinePolicy {
    enum class Mode { OFF, SHADOW, ON }
    enum class Verdict { WRITE, SHADOW_WOULD_WRITE, SKIP }

    data class Point(val index: Int, val currentRaw: Int, val targetRaw: Int, val clamped: Boolean)
    data class Decision(val verdict: Verdict, val reason: String, val points: List<Point>, val floorRaw: Int, val ceilingRaw: Int)

    const val FLOOR_RAW = 13_108 // ceil(0,8 × 16384)
    const val HIGH_MAP_CELL = 200
    const val HIGH_MAP_CEILING_RAW = 24_576 // 1,5 × 16384
    const val MIN_RELATIVE_CHANGE = 0.005

    fun ceilingRaw(mapMaxCell: Int?): Int =
        if (mapMaxCell == null || mapMaxCell > HIGH_MAP_CELL) HIGH_MAP_CEILING_RAW else KFactorProtocol.MAX_WRITE_RAW

    fun decide(
        mode: Mode,
        available: Boolean,
        currentRaw: IntArray?,
        idealRaw: IntArray?,
        mapMaxCell: Int?,
        epochKey: String,
        lastEpochKey: String?,
    ): Decision {
        val ceiling = ceilingRaw(mapMaxCell)
        fun skip(reason: String) = Decision(Verdict.SKIP, reason, emptyList(), FLOOR_RAW, ceiling)
        if (mode == Mode.OFF) return skip("DESLIGADO")
        if (epochKey == lastEpochKey) return skip("MESMO_AUTOMATCH")
        if (!available) return skip("SEM_EVIDENCIA")
        if (currentRaw == null || idealRaw == null || currentRaw.size != KFactorProtocol.POINT_COUNT || idealRaw.size != KFactorProtocol.POINT_COUNT) {
            return skip("CURVA_INVALIDA")
        }
        val points = ArrayList<Point>()
        for (i in 0 until KFactorProtocol.POINT_COUNT) {
            val current = currentRaw[i]
            val ideal = idealRaw[i]
            if (current <= 0 || ideal <= 0) continue
            val target = ideal.coerceIn(FLOOR_RAW, ceiling)
            if (target == current) continue
            // A trava não pode empurrar contra o pedido do motor.
            if ((ideal > current) != (target > current)) continue
            if (kotlin.math.abs(target - current).toDouble() / current < MIN_RELATIVE_CHANGE) continue
            points += Point(i, current, target, clamped = target != ideal)
        }
        if (points.isEmpty()) return skip("NADA_A_CORRIGIR")
        val verdict = if (mode == Mode.ON) Verdict.WRITE else Verdict.SHADOW_WOULD_WRITE
        return Decision(verdict, if (mode == Mode.ON) "GRAVAR" else "SOMBRA", points, FLOOR_RAW, ceiling)
    }

    /** Maior célula do Mapa K lido por inteiro (linhas visíveis + linha extra); sem leitura completa = null (desconhecido, nunca 0). */
    fun maxMapCell(cache: JSONObject?): Int? {
        if (cache == null || !cache.optBoolean("complete", false)) return null
        var best: Int? = null
        fun scan(rows: JSONArray?) {
            if (rows == null) return
            for (r in 0 until rows.length()) {
                val cell = rows.opt(r)
                if (cell is JSONArray) scan(cell)
                else if (cell is Number) best = maxOf(best ?: Int.MIN_VALUE, cell.toInt())
            }
        }
        scan(cache.optJSONArray("rows"))
        scan(cache.optJSONArray("extraRow"))
        return best
    }
}
