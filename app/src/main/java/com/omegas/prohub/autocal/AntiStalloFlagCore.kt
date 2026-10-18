package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol

/**
 * Escrita manual do bit 0x20 da palavra 0 de FLAG_CONF3 (SerialCode 250), a caixa "Abilitazione anti-stallo" do ProgBase
 * (dono autorizou o botão em 2026-10-18). Lógica pura, sem USB: ler-modificar-escrever, quadro e conferência.
 *
 * Marcas: OBS-COD = a caixa é esse bit (`2026-10-18-anti-stallo-quadro-de-escrita.md`); DERIVADO = o quadro
 * `35 FA 00 20 00 00 00 4F` (nunca visto ao vivo: nenhuma captura alterna a caixa); OBS-FIO = o formato do SetVector de
 * 2 × u16 (FLAG_CONF1 `35 03 00 86 2C 51 10 4B`) e a ordem dos bytes da leitura (`29 FA 00 23` → `53 04 20 00 00 00 77`).
 *
 * Regras: só 1 bit muda (as outras 31 posições seguem como lidas); o sucesso só existe depois da LEITURA DE VOLTA igual
 * às duas palavras esperadas; esperar a ECU = esperar a resposta de cada transação (nenhum relógio aqui).
 */
internal object AntiStalloFlagCore {
    const val ADDRESS = 0x00FA
    const val WORD_COUNT = 2
    const val MASK = 0x0020

    enum class Outcome {
        /** ACK + leitura de volta igual ao esperado. */
        CONFIRMED,
        /** A ECU deu ACK, mas a leitura de volta é diferente do esperado. */
        READBACK_DIVERGED,
        /** Sem ACK (recusa ou falha de transporte); `valueChanged` diz o que a leitura de conferência mostrou. */
        NOT_ACKED,
        /** O valor da ECU já não era o da prévia: nada foi enviado. */
        CHANGED_MEANWHILE,
        /** Enviado, mas a leitura de volta falhou: o valor atual é desconhecido. */
        READBACK_FAILED,
        /** O bit já estava no estado pedido: nada foi enviado. */
        NOTHING_TO_DO,
        /** Falha antes de enviar qualquer coisa (leitura inicial). */
        ERROR,
    }

    class Plan(val before: IntArray, val after: IntArray, val enable: Boolean) {
        val noop: Boolean get() = before[0] == after[0]
        val changedMask: Int get() = before[0] xor after[0]
        val frame: ByteArray get() = AutoCalProtocol.writeVectorU16(ADDRESS, after)
    }

    class WriteReply(val acked: Boolean, val transportFailed: Boolean, val replyHex: String, val detail: String)

    class Io(
        /** Lê FLAG_CONF3 (`29 FA 00`): as duas palavras. Lança se a ECU não respondeu. */
        val read: () -> IntArray,
        val write: (frame: ByteArray) -> WriteReply,
    )

    class Result(
        val outcome: Outcome,
        val message: String,
        val readback: IntArray?,
        /** A leitura de conferência mostra o valor diferente do anterior (útil quando não houve ACK). */
        val valueChanged: Boolean = false,
        val frame: ByteArray? = null,
        val reply: WriteReply? = null,
    )

    private fun valid(words: IntArray) = words.size == WORD_COUNT && words.all { it in 0..0xFFFF }

    fun plan(current: IntArray, enable: Boolean): Plan {
        require(valid(current)) { "FLAG_CONF3 atual inválido: ${current.toList()}" }
        val w0 = if (enable) current[0] or MASK else current[0] and MASK.inv() and 0xFFFF
        return Plan(current.copyOf(), intArrayOf(w0, current[1]), enable)
    }

    /** Dados da resposta `29 FA 00` (4 bytes, palavra 0 lo/hi, palavra 1 lo/hi); tamanho diferente = desconhecido (null). */
    fun wordsFromPayload(payload: IntArray): IntArray? {
        if (payload.size != WORD_COUNT * 2) return null
        return IntArray(WORD_COUNT) { (payload[it * 2] and 0xFF) or ((payload[it * 2 + 1] and 0xFF) shl 8) }
    }

    fun readbackMatches(plan: Plan, readback: IntArray): Boolean =
        valid(readback) && readback[0] == plan.after[0] && readback[1] == plan.after[1]

    fun execute(io: Io, plan: Plan): Result {
        if (plan.noop) return Result(Outcome.NOTHING_TO_DO, "O anti-apagão já está no estado pedido; nada foi enviado.", plan.before)
        val current = try { io.read() } catch (error: Exception) {
            return Result(Outcome.ERROR, "Não foi possível reler a ECU antes de gravar: ${error.message ?: error.javaClass.simpleName}", null)
        }
        if (!valid(current) || current[0] != plan.before[0] || current[1] != plan.before[1]) {
            return Result(Outcome.CHANGED_MEANWHILE, "A ECU mudou desde a prévia; nada foi gravado. Refaça a prévia.", if (valid(current)) current else null)
        }
        val frame = plan.frame
        val reply = try { io.write(frame) } catch (error: Exception) {
            WriteReply(false, true, "", error.message ?: error.javaClass.simpleName)
        }
        val back = try { io.read() } catch (error: Exception) { null }
        if (back == null || !valid(back)) {
            return Result(
                Outcome.READBACK_FAILED,
                "A leitura de volta falhou: o valor atual da ECU é desconhecido. Confira antes de tentar de novo.",
                null, frame = frame, reply = reply,
            )
        }
        if (!reply.acked) {
            return Result(
                Outcome.NOT_ACKED, "A ECU não confirmou a gravação (${reply.detail}).", back,
                valueChanged = back[0] != plan.before[0] || back[1] != plan.before[1], frame = frame, reply = reply,
            )
        }
        return if (readbackMatches(plan, back)) {
            Result(Outcome.CONFIRMED, "ECU confirmou e a leitura de volta bate.", back, true, frame, reply)
        } else {
            Result(Outcome.READBACK_DIVERGED, "A ECU deu ACK, mas a leitura de volta é diferente do esperado.", back, back[0] != plan.before[0] || back[1] != plan.before[1], frame, reply)
        }
    }
}
