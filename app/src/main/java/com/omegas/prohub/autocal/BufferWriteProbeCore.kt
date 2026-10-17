package com.omegas.prohub.autocal

/**
 * Sonda de escrita única nos pontos de aquisição do GNV (dono, 2026-10-17).
 *
 * Pergunta que ela responde, sem adivinhar: a ECU aceita escrita em PETR_INJ_TBUF_GAS (0x015F) e o valor FICA?
 * No ProgBase esses vetores são `ReadOnly` (DFM TAutoCalDM) e nenhuma captura mostra o original escrevendo neles.
 *
 * Regras de segurança (toda a lógica pura, sem USB, para testar com ECU falsa):
 * - só mexe numa banda JÁ CHEIA (contador = 10: a ECU para de atualizar banda cheia), nunca em banda em aquisição;
 * - UMA escrita de um valor, passo mínimo (+8 em 1/512 ms = 0,016 ms), no mesmo quadro SetNumber+índice que já grava a curva K;
 * - NAK ou erro de transporte: nada é assumido; só segue se a leitura provar que o valor mudou;
 * - se o valor mudou, o original SEMPRE é restaurado e conferido por leitura (até 3 tentativas), mesmo em exceção.
 */
internal object BufferWriteProbeCore {
    const val FULL_COUNT = 10
    const val STEP_RAW = 8
    const val BAND_COUNT = 18
    const val SETTLE_READS = 3
    const val SETTLE_PAUSE_MS = 500L
    const val RESTORE_ATTEMPTS = 3

    enum class Outcome {
        /** Sem banda cheia: nada foi escrito. */
        NO_STABLE_BAND,
        /** A ECU recusou (ou não confirmou) e a leitura prova que nada mudou. */
        REFUSED,
        /** A ECU aceitou e o valor continuou lá nas leituras seguintes. */
        ACCEPTED_AND_HELD,
        /** A ECU aceitou, a leitura de volta bateu, mas depois o valor voltou ao original (ECU sobrescreveu). */
        ACCEPTED_THEN_OVERWRITTEN,
        /** A ECU aceitou mas o valor lido não é o enviado nem o original (ajustado/limitado pela ECU). */
        ACCEPTED_BUT_DIFFERENT,
        /** Falha de leitura/transporte antes de provar qualquer coisa. */
        ERROR,
    }

    class WriteReply(
        val acked: Boolean,
        val transportFailed: Boolean,
        val requestHex: String,
        val replyHex: String,
        val detail: String,
    )

    class Io(
        val readTimes: () -> IntArray,
        val readCounters: () -> IntArray,
        val write: (index: Int, raw: Int) -> WriteReply,
        val pause: (Long) -> Unit,
    )

    class Result(
        val outcome: Outcome,
        val band: Int,
        val counter: Int,
        val original: Int,
        val target: Int,
        val write: WriteReply?,
        val readbacks: List<Int>,
        val restoreNeeded: Boolean,
        val restored: Boolean,
        val finalValue: Int,
        val neighborsChanged: Int,
        val message: String,
    )

    fun run(io: Io): Result {
        val times0: IntArray
        val counters: IntArray
        try {
            times0 = io.readTimes()
            counters = io.readCounters()
        } catch (error: Exception) {
            return empty(Outcome.ERROR, "Leitura inicial falhou: ${error.message ?: error.javaClass.simpleName}")
        }
        if (times0.size < BAND_COUNT || counters.size < BAND_COUNT) {
            return empty(Outcome.ERROR, "Leitura incompleta dos pontos (${times0.size}/${counters.size} de $BAND_COUNT)")
        }
        val band = (0 until BAND_COUNT)
            .filter { counters[it] >= FULL_COUNT && times0[it] in 1..(0xFFFF - STEP_RAW) }
            .maxByOrNull { counters[it] }
            ?: return empty(Outcome.NO_STABLE_BAND, "Nenhuma banda do GNV está cheia (contador 10): nada foi escrito")
        val original = times0[band]
        val target = original + STEP_RAW

        var reply: WriteReply? = null
        val readbacks = ArrayList<Int>()
        var finalValue = original
        var restored = false
        var restoreNeeded = false
        var outcome = Outcome.ERROR
        var message = ""
        var neighbors = 0
        var touched = false
        try {
            reply = io.write(band, target)
            // Só avança se a leitura PROVAR o que aconteceu; NAK e erro de transporte não são presumidos.
            val first = io.readTimes()[band]
            readbacks += first
            touched = first != original
            if (!reply.acked && !touched) {
                outcome = if (reply.transportFailed) Outcome.ERROR else Outcome.REFUSED
                message = if (reply.transportFailed) {
                    "Falha de transporte na escrita; a leitura mostra o valor original (nada mudou): ${reply.detail}"
                } else {
                    "A ECU não aceitou a escrita (${reply.detail}); o valor continua o original"
                }
            } else {
                repeat(SETTLE_READS) {
                    io.pause(SETTLE_PAUSE_MS)
                    readbacks += io.readTimes()[band]
                }
                val last = readbacks.last()
                finalValue = last
                touched = readbacks.any { it != original }
                outcome = when {
                    readbacks.all { it == target } -> Outcome.ACCEPTED_AND_HELD
                    readbacks.first() == target && last == original -> Outcome.ACCEPTED_THEN_OVERWRITTEN
                    else -> Outcome.ACCEPTED_BUT_DIFFERENT
                }
                message = when (outcome) {
                    Outcome.ACCEPTED_AND_HELD -> "A ECU aceitou e o valor ficou ($original → $target) nas ${readbacks.size} leituras"
                    Outcome.ACCEPTED_THEN_OVERWRITTEN -> "A ECU aceitou, mas voltou ao valor original sozinha"
                    else -> "A ECU aceitou, mas o valor lido não é o enviado (leituras: ${readbacks.joinToString()})"
                }
            }
        } catch (error: Exception) {
            message = "Falha durante a sonda: ${error.message ?: error.javaClass.simpleName}"
            outcome = Outcome.ERROR
        } finally {
            // O original volta SEMPRE que algo pode ter mudado (também após exceção): leitura decide, não o ACK.
            val current = try { io.readTimes()[band] } catch (_: Exception) { -1 }
            // Banda esvaziada pela própria ECU (AutoMatch/reset no meio da sonda): o valor antigo não volta, senão
            // reintroduziria uma média que a ECU acabou de descartar.
            val stillFull = try { io.readCounters()[band] >= FULL_COUNT } catch (_: Exception) { true }
            if (!stillFull) {
                message += " | A ECU esvaziou a banda sozinha durante a sonda; não restaurei"
            } else if (current != original && (touched || current == -1 || reply?.acked == true)) {
                restoreNeeded = true
                var attempt = 0
                while (attempt < RESTORE_ATTEMPTS && !restored) {
                    attempt += 1
                    try {
                        io.write(band, original)
                        val check = io.readTimes()[band]
                        finalValue = check
                        restored = check == original
                    } catch (_: Exception) {
                        // tenta de novo; o resultado só vale com leitura de volta igual
                    }
                }
                if (!restored) message += " | ATENÇÃO: não consegui restaurar $original (valor atual $finalValue); use Reset GNV"
            } else if (current == original) {
                finalValue = original
            }
            try {
                val end = io.readTimes()
                neighbors = (0 until BAND_COUNT).count { it != band && end[it] != times0[it] }
            } catch (_: Exception) {
                neighbors = -1
            }
        }
        return Result(
            outcome = outcome,
            band = band,
            counter = counters[band],
            original = original,
            target = target,
            write = reply,
            readbacks = readbacks,
            restoreNeeded = restoreNeeded,
            restored = restored,
            finalValue = finalValue,
            neighborsChanged = neighbors,
            message = message,
        )
    }

    private fun empty(outcome: Outcome, message: String) = Result(
        outcome = outcome, band = -1, counter = 0, original = 0, target = 0, write = null,
        readbacks = emptyList(), restoreNeeded = false, restored = false, finalValue = 0,
        neighborsChanged = 0, message = message,
    )
}
