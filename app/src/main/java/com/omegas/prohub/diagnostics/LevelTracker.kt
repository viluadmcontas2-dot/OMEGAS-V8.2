package com.omegas.prohub.diagnostics

import org.json.JSONObject

/**
 * Degraus do nível do cilindro e abastecimento, por CONTAGEM de quadros (sem relógio). O byte 13 do `48 01` é uma escada
 * (≈ 75 / 125 / 177 / 253) filtrada pela ECU, com "dither" de ±1: um nível só vale depois de [WINDOW] quadros válidos com
 * dispersão <= [STABLE_SPREAD]. Escala (medida no campo): 255 = vazio; cheio recém abastecido ≈ 31–78; 19–22 = sensor sem
 * leitura (central sem motor), sempre ignorado. Abastecimento = queda >= [REFUEL_DROP] contra o último nível conhecido,
 * inclusive o da sessão anterior ([lastKnownRaw], do livro-caixa). Eventos: `level_step` e `refuel_detected`. Puro.
 */
class LevelTracker(
    private val lastKnownRaw: Int?,
    private val emit: (type: String, data: JSONObject) -> Unit,
) {
    companion object {
        const val MIN_VALID = 26
        const val WINDOW = 15
        const val STABLE_SPREAD = 3
        const val STEP_MIN = 20
        const val REFUEL_DROP = 40
    }

    private val window = ArrayDeque<Pair<Long, Int>>()
    private var settled: Int? = null
    private var invalid = 0L

    fun invalidFrames(): Long = invalid
    fun lastSettled(): Int? = settled

    @Synchronized
    fun frame(elapsedMs: Long, raw: Int?) {
        if (raw == null || raw < MIN_VALID || raw > 255) { invalid += 1L; return }
        window.addLast(elapsedMs to raw)
        while (window.size > WINDOW) window.removeFirst()
        if (window.size < WINDOW) return
        val values = window.map { it.second }
        if (values.max() - values.min() > STABLE_SPREAD) return
        val level = values.sorted()[values.size / 2]
        val startedAt = window.first().first
        val before = settled
        if (before == null) {
            settled = level
            emit("level_step", step("INITIAL", null, level, startedAt, elapsedMs))
            val known = lastKnownRaw
            if (known != null && known - level >= REFUEL_DROP) emit("refuel_detected", refuel("BETWEEN_SESSIONS", known, level, startedAt))
        } else if (Math.abs(level - before) >= STEP_MIN) {
            settled = level
            emit("level_step", step("STEP", before, level, startedAt, elapsedMs))
            if (before - level >= REFUEL_DROP) emit("refuel_detected", refuel("IN_SESSION", before, level, startedAt))
        }
    }

    private fun step(kind: String, from: Int?, to: Int, at: Long, settledAt: Long) =
        JSONObject().put("kind", kind).put("fromRaw", from ?: JSONObject.NULL).put("toRaw", to)
            .put("atElapsedMs", at).put("settledAtElapsedMs", settledAt).put("unit", "raw_byte")

    private fun refuel(source: String, from: Int, to: Int, at: Long) =
        JSONObject().put("source", source).put("fromRaw", from).put("toRaw", to).put("drop", from - to)
            .put("atElapsedMs", at).put("threshold", REFUEL_DROP).put("unit", "raw_byte")
}
