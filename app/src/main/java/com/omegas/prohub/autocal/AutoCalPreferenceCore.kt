package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol

/**
 * Preferências da central do AutoCal original (painel "Preferências"): lógica pura, sem USB.
 * Fonte: docs/progbase/protocolo/autocal-preferencias.md (OBS-COD: quadros gerados executando o original em CPU emulada).
 *
 * Quadro de gravação: primeiro byte = 0x11 + largura + (indexado ? 1 : 0); depois SC (2 bytes, little-endian), [índice],
 * valor little-endian, soma módulo 256. Leitura: número = `09`; elemento de vetor = vetor inteiro `29` (provado na central
 * para SC 123/124) fatiado por largura. A leitura `0A` (elemento) não tem resposta provada, por isso não é usada.
 *
 * O que NÃO está provado (vai como "não comprovado" na tela): o efeito de cada parâmetro no firmware, a faixa de fábrica,
 * o ACK real destes SC na central do dono e o fatiamento do vetor `29` para SC 357/359/370/331/332. O valor de fábrica é
 * DESCONHECIDO: nada aqui inventa padrão. Nunca grava MUL_ACT (curva K) nem Mapa K.
 */
internal object AutoCalPreferenceCore {
    const val ENABLE_ID = "AUTO_CAL_ENABLE"
    const val MAP_MIN_ID = "LIMIT_PRESSURE_MIN"
    const val MAP_MAX_ID = "LIMIT_PRESSURE_MAX"
    const val MAP_REF_PREFIX = "MNFLD_PRESS_THD:"
    const val INJ_REF_PREFIX = "PETR_INJ_TBP:"
    const val REF_POINTS = 18

    data class Spec(
        val id: String,
        val sc: Int,
        /** -1 = número escalar; >= 0 = elemento de vetor. */
        val index: Int,
        val width: Int,
        val min: Int,
        val max: Int,
        val signed: Boolean = false,
    ) {
        val indexed get() = index >= 0
    }

    val SPECS: List<Spec> = buildList {
        add(Spec(ENABLE_ID, 330, -1, 1, 0, 1))
        add(Spec("VECT_AUTOCAL_U8_2", 357, 2, 1, 1, 99))
        add(Spec("MAX_RPM_FOR_AUTOCAL", 378, -1, 2, 400, 8000))
        add(Spec("DISABLE_ACQ_BAND", 395, -1, 1, 0, 1))
        add(Spec(MAP_MIN_ID, 361, -1, 2, 0, 4096, signed = true))
        add(Spec(MAP_MAX_ID, 362, -1, 2, 0, 4096, signed = true))
        for (i in 0 until REF_POINTS) add(Spec("$MAP_REF_PREFIX$i", 332, i, 2, 0, 4 * 1024))
        for (i in 0 until REF_POINTS) add(Spec("$INJ_REF_PREFIX$i", 331, i, 2, 0, 40 * 512))
        add(Spec("EN_CDN_T_THD", 359, 1, 2, 0, 0xFFFF))
        add(Spec("DIFF_ENG_SPD_THD", 387, -1, 2, 0, 0xFFFF))
        add(Spec("DELTA_ENG_SPD_THD", 388, -1, 2, 0, 0xFFFF))
        add(Spec("DIFF_MNFLD_PRESS_THD", 389, -1, 2, 0, 0xFFFF))
        add(Spec("DELTA_MNFLD_PRESS_THD", 390, -1, 2, 0, 0xFFFF))
        add(Spec("DIFF_PETR_TINJ_T_THD", 391, -1, 2, 0, 0xFFFF))
        add(Spec("DELTA_PETR_INJ_T_THD", 392, -1, 2, 0, 0xFFFF))
        add(Spec("VECT_AUTOCAL_U8_0", 357, 0, 1, 0, 255))
        add(Spec("VECT_AUTOCAL_U8_1", 357, 1, 1, 0, 255))
        for (i in 0 until 10) add(Spec("CALIBRATION_VAL_1:$i", 370, i, 1, 0, 255))
    }

    private val BY_ID = SPECS.associateBy { it.id }
    fun spec(id: String): Spec? = BY_ID[id]

    fun writeFrame(spec: Spec, raw: Int): ByteArray {
        val op = 0x11 + spec.width + if (spec.indexed) 1 else 0
        val body = ArrayList<Byte>()
        body += op.toByte()
        body += (spec.sc and 0xFF).toByte()
        body += ((spec.sc ushr 8) and 0xFF).toByte()
        if (spec.indexed) body += spec.index.toByte()
        body += (raw and 0xFF).toByte()
        if (spec.width == 2) body += ((raw ushr 8) and 0xFF).toByte()
        return Mp48Protocol.frame(body.toByteArray())
    }

    fun readFrame(spec: Spec): ByteArray =
        if (spec.indexed) AutoCalProtocol.readVector(spec.sc) else AutoCalProtocol.readScalar(spec.sc)

    /** Valor do parâmetro dentro da resposta (número ou vetor inteiro). null = resposta curta/estranha: desconhecido. */
    fun decode(spec: Spec, payload: IntArray): Int? {
        val offset = if (spec.indexed) spec.index * spec.width else 0
        if (!spec.indexed && payload.size != spec.width) return null
        if (payload.size < offset + spec.width) return null
        var v = payload[offset] and 0xFF
        if (spec.width == 2) v = v or ((payload[offset + 1] and 0xFF) shl 8)
        return if (spec.signed) v.toShort().toInt() else v
    }

    enum class Outcome { CONFIRMED, NOTHING_TO_DO, INVALID, READ_FAILED, NOT_ACKED, READBACK_FAILED, READBACK_DIVERGED }

    class WriteReply(val acked: Boolean, val detail: String = "")

    class Io(
        /** Envia um quadro de leitura e devolve o payload; lança se a central não respondeu. */
        val read: (frame: ByteArray) -> IntArray,
        val write: (frame: ByteArray) -> WriteReply,
    )

    class Result(
        val outcome: Outcome,
        val id: String,
        val before: Int?,
        val after: Int?,
        val message: String,
        val frame: ByteArray? = null,
    ) {
        val ok get() = outcome == Outcome.CONFIRMED || outcome == Outcome.NOTHING_TO_DO
    }

    /** Leitura de vários parâmetros: um pedido por SC (vetor inteiro). Desconhecido = null, nunca 0. */
    fun readMany(io: Io, ids: List<String>): Map<String, Int?> {
        val cache = HashMap<Int, IntArray?>()
        val out = LinkedHashMap<String, Int?>()
        for (id in ids) {
            val spec = spec(id)
            if (spec == null) { out[id] = null; continue }
            val payload = cache.getOrPut(spec.sc) { try { io.read(readFrame(spec)) } catch (_: Exception) { null } }
            out[id] = payload?.let { decode(spec, it) }
        }
        return out
    }

    /** Texto de recusa (sem enviar nada) ou null se o valor é aceitável. `read` devolve o valor atual de outro parâmetro. */
    fun refusal(spec: Spec, raw: Int, read: (String) -> Int?): String? {
        if (raw !in spec.min..spec.max) return "Valor fora da faixa (${spec.min} a ${spec.max})."
        if (spec.id == ENABLE_ID && raw == 0) return "Desligar o aprendizado pausa a curva. Use só Ligar."
        if (spec.sc == AutoCalProtocol.MUL_ACT.address) return "Curva K não é gravada aqui."
        if (spec.id == MAP_MIN_ID || spec.id == MAP_MAX_ID) {
            val other = read(if (spec.id == MAP_MIN_ID) MAP_MAX_ID else MAP_MIN_ID)
                ?: return "Não consegui ler o outro limite. Nada foi enviado."
            val ok = if (spec.id == MAP_MIN_ID) raw < other else raw > other
            if (!ok) return "O limite inferior tem que ficar abaixo do superior."
        }
        val prefix = listOf(MAP_REF_PREFIX, INJ_REF_PREFIX).firstOrNull { spec.id.startsWith(it) }
        if (prefix != null) {
            if (spec.index > 0) {
                val prev = read("$prefix${spec.index - 1}") ?: return "Não consegui ler o ponto vizinho. Nada foi enviado."
                if (raw <= prev) return "Cada ponto tem que ser maior que o anterior."
            }
            if (spec.index < REF_POINTS - 1) {
                val next = read("$prefix${spec.index + 1}") ?: return "Não consegui ler o ponto vizinho. Nada foi enviado."
                if (raw >= next) return "Cada ponto tem que ser menor que o seguinte."
            }
        }
        return null
    }

    fun execute(io: Io, id: String, raw: Int): Result {
        val spec = spec(id) ?: return Result(Outcome.INVALID, id, null, null, "Parâmetro desconhecido.")
        val cache = HashMap<Int, IntArray?>()
        fun load(s: Spec): Int? {
            val payload = cache.getOrPut(s.sc) { try { io.read(readFrame(s)) } catch (_: Exception) { null } }
            return payload?.let { decode(s, it) }
        }
        val before = load(spec) ?: return Result(Outcome.READ_FAILED, id, null, null, "Não consegui ler o valor atual. Nada foi enviado.")
        refusal(spec, raw) { other -> spec(other)?.let(::load) }?.let { return Result(Outcome.INVALID, id, before, null, it) }
        if (before == raw) return Result(Outcome.NOTHING_TO_DO, id, before, before, "Já está com esse valor.")
        val frame = writeFrame(spec, raw)
        val reply = try { io.write(frame) } catch (e: Exception) { WriteReply(false, e.message ?: e.javaClass.simpleName) }
        if (!reply.acked) return Result(Outcome.NOT_ACKED, id, before, null, "A central não confirmou. Não foi dado como gravado.", frame)
        val after = try { decode(spec, io.read(readFrame(spec))) } catch (_: Exception) { null }
            ?: return Result(Outcome.READBACK_FAILED, id, before, null, "A central confirmou, mas não consegui reler. Não foi dado como gravado.", frame)
        return if (after == raw) Result(Outcome.CONFIRMED, id, before, after, "Gravado e conferido.", frame)
        else Result(Outcome.READBACK_DIVERGED, id, before, after, "A releitura veio diferente. Não foi dado como gravado.", frame)
    }
}
