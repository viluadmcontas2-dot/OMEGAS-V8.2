package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.json.JSONObject

/**
 * Os 5 números do anti-apagão (ANTI_STALLO, SerialCode 307 = 0x0133, 5 × u16), editáveis pelo dono (2026-10-18).
 * Lógica pura, sem USB, espelho de AntiStalloFlagCore: ler-modificar-escrever, quadro, faixa e conferência.
 *
 * Marcas: OBS-COD = DFM do ProgBase (`TFormConfig.EditAntiStallo0..4`: faixa MÁXIMA 6000/1000/10000/1000/1000; vetor com padrão
 * 1600·100·1500·2·20; `docs/evidence/2026-10-18-tela-anti-stallo-original.md`). DERIVADO = o DFM não traz MinValue; o campo
 * é u16 sem sinal, então o piso é 0. INFERIDO (forte) = qual edit liga em qual posição (DataIndex 0..4 do edit = posição do vetor);
 * o código Delphi que liga os dois não foi visto.
 *
 * Quadro (DERIVADO do original, `TAebProtocol.SetVector`): 5 × u16 = 10 bytes de dados; com n+1 >= 7 o original manda o SetVector
 * ESTENDIDO `37 <end. baixo> <n+1> <end. alto> <dados> <soma>`; o opcode compacto (0x31 + tamanho) só existe até 0x36 (5 bytes).
 * Por isso o quadro é `37 33 0C 01 ...`, e não `3B`. O montador é o mesmo do original (`AutoCalProtocol.writeVectorU16`).
 *
 * Regras: só os campos pedidos mudam (os outros vêm da leitura ao vivo); nunca aceita valor fora da faixa; o sucesso só existe
 * depois da LEITURA DE VOLTA igual aos 5 números esperados; esperar o módulo = esperar a resposta (nenhum relógio aqui).
 */
internal object AntiStalloParamsCore {
    const val ADDRESS = 0x0133
    const val COUNT = 5

    /** Faixa permitida de um campo e o valor de fábrica (padrão do vetor no DFM). */
    class Field(val index: Int, val min: Int, val max: Int, val factory: Int)

    val FIELDS = listOf(
        Field(0, 0, 6000, 1600),
        Field(1, 0, 1000, 100),
        Field(2, 0, 10000, 1500),
        Field(3, 0, 1000, 2),
        Field(4, 0, 1000, 20),
    )
    val FACTORY: IntArray = IntArray(COUNT) { FIELDS[it].factory }

    enum class Outcome { CONFIRMED, READBACK_DIVERGED, NOT_ACKED, CHANGED_MEANWHILE, READBACK_FAILED, NOTHING_TO_DO, ERROR }

    class Plan(val before: IntArray, val after: IntArray) {
        val noop: Boolean get() = before.contentEquals(after)
        val changedIndices: List<Int> get() = (0 until COUNT).filter { before[it] != after[it] }
        val frame: ByteArray get() = AutoCalProtocol.writeVectorU16(ADDRESS, after)
    }

    class WriteReply(val acked: Boolean, val transportFailed: Boolean, val replyHex: String, val detail: String)

    class Io(
        /** Lê ANTI_STALLO (`29 33 01`): os 5 números. Lança se o módulo não respondeu. */
        val read: () -> IntArray,
        val write: (frame: ByteArray) -> WriteReply,
    )

    class Result(
        val outcome: Outcome,
        val message: String,
        val readback: IntArray?,
        val valueChanged: Boolean = false,
        val frame: ByteArray? = null,
        val reply: WriteReply? = null,
    )

    private fun valid(values: IntArray) = values.size == COUNT && values.all { it in 0..0xFFFF }

    fun factoryChanges(): Map<Int, Int> = FIELDS.associate { it.index to it.factory }
    fun factoryChangesJson(): String = JSONObject().also { o -> FIELDS.forEach { o.put(it.index.toString(), it.factory) } }.toString()

    /** Texto em linguagem simples, ou null se todos os valores pedidos estão dentro da faixa. */
    fun validate(changes: Map<Int, Int>): String? {
        for ((index, value) in changes) {
            val field = FIELDS.getOrNull(index) ?: return "Esse campo não existe."
            if (value < field.min || value > field.max) return "O valor tem que ficar entre ${field.min} e ${field.max}."
        }
        return null
    }

    fun plan(current: IntArray, changes: Map<Int, Int>): Plan {
        require(valid(current)) { "Valores atuais inválidos: ${current.toList()}" }
        val problem = validate(changes)
        require(problem == null) { problem ?: "" }
        val after = current.copyOf()
        for ((index, value) in changes) after[index] = value
        return Plan(current.copyOf(), after)
    }

    /** Dados da resposta `29 33 01` (10 bytes, little-endian por posição); tamanho diferente = desconhecido (null). */
    fun valuesFromPayload(payload: IntArray): IntArray? {
        if (payload.size != COUNT * 2) return null
        return IntArray(COUNT) { (payload[it * 2] and 0xFF) or ((payload[it * 2 + 1] and 0xFF) shl 8) }
    }

    fun readbackMatches(plan: Plan, readback: IntArray): Boolean = valid(readback) && readback.contentEquals(plan.after)

    private const val UNCERTAIN = "Não sei como ficou no módulo."

    fun execute(io: Io, plan: Plan): Result {
        if (plan.noop) return Result(Outcome.NOTHING_TO_DO, "Esses números já estão assim no módulo. Nada foi enviado.", plan.before)
        val current = try { io.read() } catch (error: Exception) {
            return Result(Outcome.ERROR, "Não consegui ler o módulo antes de gravar. Nada foi enviado. Confira o cabo e tente de novo.", null)
        }
        if (!valid(current) || !current.contentEquals(plan.before)) {
            return Result(Outcome.CHANGED_MEANWHILE, "Os números do módulo mudaram desde a conferência. Nada foi gravado. Refaça a conferência.", if (valid(current)) current else null)
        }
        val frame = plan.frame
        val reply = try { io.write(frame) } catch (error: Exception) {
            WriteReply(false, true, "", error.message ?: error.javaClass.simpleName)
        }
        val back = try { io.read() } catch (error: Exception) { null }
        if (back == null || !valid(back)) {
            return Result(
                Outcome.READBACK_FAILED,
                "$UNCERTAIN A leitura depois de gravar falhou. Confira o cabo, espere a leitura voltar e olhe os números antes de tentar de novo.",
                null, frame = frame, reply = reply,
            )
        }
        if (!reply.acked) {
            val changed = !back.contentEquals(plan.before)
            return Result(
                Outcome.NOT_ACKED,
                (if (changed) "$UNCERTAIN " else "") + "O módulo não confirmou a gravação. " + (if (changed) "Os números mudaram mesmo assim; confira a tela." else "Os números continuam como estavam. Tente de novo."),
                back, valueChanged = changed, frame = frame, reply = reply,
            )
        }
        return if (readbackMatches(plan, back)) {
            Result(Outcome.CONFIRMED, "Gravado e conferido no módulo.", back, true, frame, reply)
        } else {
            Result(
                Outcome.READBACK_DIVERGED,
                "$UNCERTAIN O módulo aceitou, mas os números lidos depois são outros. Confira a tela antes de tentar de novo.",
                back, !back.contentEquals(plan.before), frame, reply,
            )
        }
    }
}
