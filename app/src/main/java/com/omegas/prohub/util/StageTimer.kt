package com.omegas.prohub.util

import org.json.JSONObject

/**
 * Cronômetro de etapas de um trabalho curto (um quadro de telemetria). Só mede: não decide nada e não toca a ECU.
 * Existe para a próxima sessão dizer QUAL etapa deixou a entrega lenta (na sessão de 08/10 a entrega máxima por quadro
 * subiu de 69 ms para 1,3 s e 43 % dos quadros foram descartados sem que o registro apontasse a etapa).
 */
class StageTimer(private val nowNs: () -> Long = System::nanoTime) {
    private val names = arrayOfNulls<String>(MAX_STAGES)
    private val durationsNs = LongArray(MAX_STAGES)
    private var count = 0
    private val startedNs = nowNs()
    private var lastNs = startedNs

    /** Fecha a etapa que terminou agora. Etapas além do limite somam na última. */
    fun mark(name: String) {
        val now = nowNs()
        val index = if (count < MAX_STAGES) count++ else MAX_STAGES - 1
        names[index] = if (index == MAX_STAGES - 1 && count == MAX_STAGES && names[index] != null && names[index] != name) "outras" else name
        durationsNs[index] += (now - lastNs).coerceAtLeast(0L)
        lastNs = now
    }

    fun totalMs(): Long = (lastNs - startedNs).coerceAtLeast(0L) / 1_000_000L

    fun stageMs(name: String): Long =
        (0 until count).filter { names[it] == name }.sumOf { durationsNs[it] } / 1_000_000L

    /** Etapa mais demorada: nome e milissegundos (nulo se nada foi marcado). */
    fun worst(): Pair<String, Long>? =
        (0 until count).maxByOrNull { durationsNs[it] }?.let { (names[it] ?: "?") to durationsNs[it] / 1_000_000L }

    fun toJson(): JSONObject {
        val stages = JSONObject()
        for (i in 0 until count) stages.put(names[i] ?: "?", durationsNs[i] / 1_000_000.0)
        return JSONObject().put("totalMs", totalMs()).put("worst", worst()?.first ?: JSONObject.NULL).put("stagesMs", stages)
    }

    companion object {
        const val MAX_STAGES = 12
    }
}
