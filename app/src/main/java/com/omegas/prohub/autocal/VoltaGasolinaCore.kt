package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import java.math.BigInteger

/**
 * Volta automática à gasolina por PRESSÃO BAIXA do gás (ProgBase, aba F4: "Automatic change back to petrol",
 * "Low pressure time for change back", "Low pressure value for change back"). Lógica pura, sem USB.
 * Fonte: docs/progbase/protocolo/volta-automatica-gasolina.md.
 *
 * 1. Caixa + tempo (CheckRitornoBenzinaClick 0x4A9484, OBS-COD): TEMPO_RITORNO_BENZINA, SerialCode 62 (0x3E), u8, em s.
 *    Desmarcar grava 255 (= desligada); marcar regrava o tempo guardado, ou 0. Quadro SetNumber `12 3E 00 vv soma`.
 *    Faixa do campo de tempo: 0..3 s (DFM EditRitornoBenzina: Precision 1, MaxValue 3).
 * 2. Pressão (EditBassaPressioneMpKeyPress 0x4C526C, OBS-COD): NÃO é um número só. Recalcula a tabela de 15 pontos
 *    RIF_PRESS_DIFF (SC 123, mbar) e COEFF_PRESS_DIFF (SC 124) a partir do valor digitado e de RIF_PRESS_DIFF[10] lido da
 *    central, e manda 30 quadros `14 7B|7C 00 i lo hi soma`, alternando 7B/7C, i = 0..14. A conta abaixo reproduz o x87 do
 *    original BIT A BIT (precisão estendida de 64 bits do Default8087CW 0x1332, cada operação arredondada como a FPU, e
 *    `fstp qword` arredondando para double); o Trunc dos setters é exato (produto double × 1000 cabe em 64 bits).
 *    Conferido contra o original executado (tests/fixtures/volta_gasolina_original_grade.json, 70 tabelas × 30 quadros).
 *
 * Central do dono (PortmonLOGNOVO, 12/08/2026, OBS-FIO): `09 3E 00` → 0 s; `29 7B 00` → 1250 1324 … 2000 (P0 = RIF[10]) … 2335;
 * `29 7C 00` → 2617 2325 … 0 … −1007. Essa tabela é EXATAMENTE `table(1250, 2000)`: prova que a tela F4 "1,25 bar" é
 * RIF_PRESS_DIFF[0] (não o campo LR; nesta central o SC 152 é TINJ_FILTRO) e que o coeficiente vai ×100 em 16 bits
 * (SD+0x1DD4 = 0). A gravação da pressão fica atrás de [PRESSURE_WRITE_ENABLED] E da trava [consistentWithOriginal]: só
 * grava se a tabela lida AGORA for a que o original geraria (outra família de central = recusa sem enviar).
 */
internal object VoltaGasolinaCore {
    const val TEMPO_ADDRESS = 0x003E
    const val RIF_ADDRESS = 0x007B
    const val COEFF_ADDRESS = 0x007C
    const val POINTS = 15
    const val OFF = 255
    const val TEMPO_MAX = 3
    const val PMP_MIN_MBAR = 200
    const val PMP_MAX_MBAR = 2000

    /**
     * Interruptor INTERNO da gravação da pressão. Ligado: os três desconhecidos do documento foram resolvidos pela leitura real
     * do LOGNOVO (P0 = 2000; campo = RIF_PRESS_DIFF[0]; coeficiente ×100). Mesmo ligado, [consistentWithOriginal] trava cada toque.
     */
    const val PRESSURE_WRITE_ENABLED = true

    /** O que ainda não tem prova (não bloqueia; vai em "Detalhes técnicos"). */
    val NOT_PROVEN = listOf(
        "ACK da central a cada um dos 30 quadros `14 7B|7C 00 i lo hi` no carro (formato executado do original; resposta inferida do LOG)",
        "efeito no carro: a central volta à gasolina quando a pressão (Press.diff.) cai abaixo do ponto 0 (comparação inferida, não medida)",
    )

    /**
     * A tabela lida é a que o original geraria a partir do próprio ponto 0 e do P0 (com o truncamento do original, o ponto 0
     * pode ter perdido 1 mbar: testa os dois). Falso = central de outra família/codificação: não gravar.
     */
    fun consistentWithOriginal(rif: IntArray, coeff: IntArray): Boolean {
        if (rif.size != POINTS || coeff.size != POINTS || rif[10] !in 1..0xFFFF) return false
        val c = IntArray(POINTS) { coeff[it] and 0xFFFF }
        return listOf(rif[0], rif[0] + 1).any { mbar ->
            mbar in 1..0xFFFF && table(mbar, rif[10]).let { it.rif.contentEquals(rif) && it.coeff.contentEquals(c) }
        }
    }

    // ---------- tempo ----------

    fun tempoFrame(value: Int): ByteArray {
        require(value in 0..0xFF)
        return Mp48Protocol.frame(byteArrayOf(0x12, (TEMPO_ADDRESS and 0xFF).toByte(), 0x00, value.toByte()))
    }

    fun validTempo(value: Int) = value == OFF || value in 0..TEMPO_MAX

    /** Caixa do original: desligar = 255; ligar = tempo guardado (> 0 e válido) ou 0. */
    fun toggleTarget(on: Boolean, saved: Int): Int = if (!on) OFF else if (saved in 1..TEMPO_MAX) saved else 0

    // ---------- tabela ----------

    class Table(val a: DoubleArray, val b: DoubleArray, val rif: IntArray, val coeff: IntArray)

    /** Conta de 0x4C526C + setters 0x4327C0 / 0x432670 (com SD+0x1DD4 = 0). `pmpMbar` = valor digitado em mbar. */
    fun table(pmpMbar: Int, p0Raw: Int): Table {
        require(p0Raw in 1..0xFFFF)
        val pmp = Ext.of(pmpMbar / 1000.0)
        val p0 = Ext.mul(Ext.of(p0Raw.toLong()), P0_SCALE).toDouble()           // getter 0x43277C → fstp qword
        val p0e = Ext.of(p0)
        val x = Ext.add(Ext.mul(Ext.sub(p0e, Ext.of(0.95)), Ext.of(1.176470588235294)), Ext.of(1.1)).toDouble()
        val xe = Ext.of(x)
        val a = DoubleArray(POINTS) { i ->
            when {
                i == 0 -> pmp.toDouble()
                i == 14 -> x
                i == 10 -> p0
                i < 10 -> Ext.add(Ext.div(Ext.mul(Ext.sub(p0e, pmp), Ext.of(i.toLong())), Ext.of(10L)), pmp).toDouble()
                else -> Ext.add(Ext.div(Ext.mul(Ext.sub(xe, p0e), Ext.of((i - 10).toLong())), Ext.of(4L)), p0e).toDouble()
            }
        }
        val b = DoubleArray(POINTS) { i ->
            val s = Ext.sqrt(Ext.div(Ext.of(a[i]), p0e))
            Ext.mul(Ext.sub(Ext.of(1L), s), Ext.of(125L)).toDouble()
        }
        val rif = IntArray(POINTS) { Ext.truncTimes(a[it], 1000) and 0xFFFF }
        val coeff = IntArray(POINTS) {
            val v = when { b[it] < -99.9 -> -99.9; b[it] > 99.9 -> 99.9; else -> b[it] }   // 0x432670: limita a ±99,9
            Ext.truncTimes(v, 100) and 0xFFFF
        }
        return Table(a, b, rif, coeff)
    }

    /** 30 quadros na ordem do original: para i = 0..14, RIF[i] e depois COEFF[i]. */
    fun tableFrames(rif: IntArray, coeff: IntArray): List<ByteArray> {
        require(rif.size == POINTS && coeff.size == POINTS)
        return (0 until POINTS).flatMap { i ->
            listOf(AutoCalProtocol.writeIndexedU16(RIF_ADDRESS, i, rif[i] and 0xFFFF), AutoCalProtocol.writeIndexedU16(COEFF_ADDRESS, i, coeff[i] and 0xFFFF))
        }
    }

    fun wordsFromPayload(payload: IntArray, count: Int): IntArray? {
        if (payload.size != count * 2) return null
        return IntArray(count) { (payload[it * 2] and 0xFF) or ((payload[it * 2 + 1] and 0xFF) shl 8) }
    }

    // ---------- execução (ler → gravar com ACK → reler → comparar) ----------

    enum class Outcome { CONFIRMED, READBACK_DIVERGED, NOT_ACKED, PARTIAL, READBACK_FAILED, NOTHING_TO_DO, INVALID, DISABLED, INCONSISTENT, ERROR }

    class WriteReply(val acked: Boolean, val transportFailed: Boolean, val replyHex: String, val detail: String)

    class Io(
        val readTempo: () -> Int,
        val readRif: () -> IntArray,
        val readCoeff: () -> IntArray,
        val write: (frame: ByteArray) -> WriteReply,
    )

    class Result(
        val outcome: Outcome,
        val message: String,
        val beforeTempo: Int? = null,
        val tempo: Int? = null,
        val beforeRif: IntArray? = null,
        val beforeCoeff: IntArray? = null,
        val rif: IntArray? = null,
        val coeff: IntArray? = null,
        val frames: List<ByteArray> = emptyList(),
        val sent: Int = 0,
    ) {
        val ok: Boolean get() = outcome == Outcome.CONFIRMED || outcome == Outcome.NOTHING_TO_DO
    }

    /**
     * Junta tempo e tabela de um mesmo toque. O primeiro que não deu certo manda no resultado. O "antes" só vai para o
     * Desfazer da parte que de fato mandou algum quadro.
     */
    fun combine(tempo: Result?, table: Result?): Result {
        val parts = listOfNotNull(tempo, table)
        require(parts.isNotEmpty())
        if (parts.size == 1) return parts[0]
        val bad = parts.firstOrNull { !it.ok }
        val outcome = bad?.outcome ?: if (parts.any { it.outcome == Outcome.CONFIRMED }) Outcome.CONFIRMED else Outcome.NOTHING_TO_DO
        val message = bad?.message ?: if (outcome == Outcome.CONFIRMED) "Gravado e conferido na central." else "Já está assim na central. Nada foi enviado."
        val t = tempo!!; val p = table!!
        return Result(
            outcome, message,
            beforeTempo = if (t.sent > 0) t.beforeTempo else null, tempo = t.tempo,
            beforeRif = if (p.sent > 0) p.beforeRif else null, beforeCoeff = if (p.sent > 0) p.beforeCoeff else null,
            rif = p.rif, coeff = p.coeff, frames = t.frames + p.frames, sent = t.sent + p.sent,
        )
    }

    private const val UNCERTAIN = "Não sei como ficou na central."

    fun executeTempo(io: Io, after: Int): Result {
        if (!validTempo(after)) return Result(Outcome.INVALID, "A espera tem que ficar entre 0 e $TEMPO_MAX segundos.")
        val before = try { io.readTempo() } catch (_: Exception) {
            return Result(Outcome.ERROR, "Não consegui ler a central. Nada foi enviado. Confira o cabo e tente de novo.")
        }
        if (before == after) return Result(Outcome.NOTHING_TO_DO, "Já está assim na central. Nada foi enviado.", before, before)
        val frame = tempoFrame(after)
        val reply = try { io.write(frame) } catch (e: Exception) { WriteReply(false, true, "", e.message ?: "falha") }
        val back = try { io.readTempo() } catch (_: Exception) { null }
        val frames = listOf(frame)
        if (back == null) return Result(Outcome.READBACK_FAILED, "$UNCERTAIN A leitura depois de gravar falhou. Confira o cabo e olhe de novo.", before, null, frames = frames, sent = 1)
        if (!reply.acked) {
            return Result(Outcome.NOT_ACKED, if (back == before) "A central não confirmou. Continua como estava. Tente de novo." else "$UNCERTAIN A central não confirmou, mas o valor mudou. Confira a tela.", before, back, frames = frames, sent = 1)
        }
        return if (back == after) Result(Outcome.CONFIRMED, "Gravado e conferido na central.", before, back, frames = frames, sent = 1)
        else Result(Outcome.READBACK_DIVERGED, "$UNCERTAIN A central aceitou, mas leu outro valor depois. Confira a tela.", before, back, frames = frames, sent = 1)
    }

    /** Pressão digitada → tabela do original → 30 quadros. Só com [enabled] (interruptor interno); senão nada sai. */
    fun executePressure(io: Io, pmpMbar: Int, enabled: Boolean = PRESSURE_WRITE_ENABLED): Result {
        if (!enabled) return Result(Outcome.DISABLED, "Mudar a pressão não está liberado neste aplicativo. Nada foi enviado.")
        if (pmpMbar !in PMP_MIN_MBAR..PMP_MAX_MBAR || pmpMbar % 5 != 0) return Result(Outcome.INVALID, "A pressão tem que ficar entre 0,2 e 2,0 bar.")
        return executeVectors(io) { rif, coeff ->
            if (!consistentWithOriginal(rif, coeff)) null else table(pmpMbar, rif[10]).let { it.rif to it.coeff }
        }
    }

    /** Desfazer da pressão: regrava a tabela de antes, ponto a ponto, e confere. */
    fun executeRestore(io: Io, rif: IntArray, coeff: IntArray): Result = executeVectors(io) { _, _ -> rif.copyOf() to coeff.copyOf() }

    private fun executeVectors(io: Io, target: (IntArray, IntArray) -> Pair<IntArray, IntArray>?): Result {
        val beforeRif: IntArray
        val beforeCoeff: IntArray
        try {
            beforeRif = io.readRif(); beforeCoeff = io.readCoeff()
        } catch (_: Exception) {
            return Result(Outcome.ERROR, "Não consegui ler a central. Nada foi enviado. Confira o cabo e tente de novo.")
        }
        if (beforeRif.size != POINTS || beforeCoeff.size != POINTS) return Result(Outcome.ERROR, "A central respondeu num formato inesperado. Nada foi enviado.")
        val (rif, coeff) = target(beforeRif, beforeCoeff)
            ?: return Result(Outcome.INCONSISTENT, "A tabela de pressão desta central não é a que o programa original monta. Por segurança, nada foi enviado.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = beforeRif, coeff = beforeCoeff)
        if (rif.contentEquals(beforeRif) && coeff.contentEquals(beforeCoeff)) {
            return Result(Outcome.NOTHING_TO_DO, "Já está assim na central. Nada foi enviado.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = beforeRif, coeff = beforeCoeff)
        }
        val frames = tableFrames(rif, coeff)
        var sent = 0
        var stopped = false
        for (frame in frames) {
            val reply = try { io.write(frame) } catch (e: Exception) { WriteReply(false, true, "", e.message ?: "falha") }
            sent++
            if (!reply.acked) { stopped = true; break }
        }
        val backRif = try { io.readRif() } catch (_: Exception) { null }
        val backCoeff = try { io.readCoeff() } catch (_: Exception) { null }
        if (backRif == null || backCoeff == null) {
            return Result(Outcome.READBACK_FAILED, "$UNCERTAIN A leitura depois de gravar falhou. Toque em Desfazer para voltar ao que estava.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, frames = frames, sent = sent)
        }
        val unchanged = backRif.contentEquals(beforeRif) && backCoeff.contentEquals(beforeCoeff)
        if (stopped) {
            return if (unchanged) Result(Outcome.NOT_ACKED, "A central não confirmou. Continua como estava. Tente de novo.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = backRif, coeff = backCoeff, frames = frames, sent = sent)
            else Result(Outcome.PARTIAL, "A central parou de confirmar no meio. Toque em Desfazer para voltar ao que estava.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = backRif, coeff = backCoeff, frames = frames, sent = sent)
        }
        return if (backRif.contentEquals(rif) && backCoeff.contentEquals(coeff)) {
            Result(Outcome.CONFIRMED, "Gravado e conferido na central.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = backRif, coeff = backCoeff, frames = frames, sent = sent)
        } else {
            Result(Outcome.READBACK_DIVERGED, "$UNCERTAIN A central aceitou, mas leu outros valores depois. Toque em Desfazer.", beforeRif = beforeRif, beforeCoeff = beforeCoeff, rif = backRif, coeff = backCoeff, frames = frames, sent = sent)
        }
    }

    // ---------- x87 estendido (64 bits de mantissa), exato ----------

    /** Constante 0.001 em estendido do getter 0x43277C (`fld tbyte [0x4327AC]` = 0x83126E978D4FDF3B × 2^-73). */
    private val P0_SCALE = Ext(false, BigInteger("83126E978D4FDF3B", 16), -73)

    /** Número binário ±mag × 2^exp. Cada operação devolve o valor arredondado como a FPU (64 bits, ao par mais próximo). */
    private class Ext(val neg: Boolean, val mag: BigInteger, val exp: Int) {
        fun toDouble(): Double {
            val r = round(neg, mag, exp, false, 53)
            return (if (r.neg) -1.0 else 1.0) * Math.scalb(r.mag.toDouble(), r.exp)
        }

        companion object {
            fun of(v: Long): Ext = Ext(v < 0, BigInteger.valueOf(v).abs(), 0)
            fun of(d: Double): Ext {
                require(!d.isNaN() && !d.isInfinite())
                if (d == 0.0) return Ext(false, BigInteger.ZERO, 0)
                val bits = java.lang.Double.doubleToRawLongBits(d)
                val e = ((bits ushr 52) and 0x7FF).toInt()
                val f = bits and 0xFFFFFFFFFFFFFL
                val m = if (e == 0) f else f or (1L shl 52)
                return Ext(d < 0, BigInteger.valueOf(m), (if (e == 0) 1 else e) - 1075)
            }

            private fun signed(x: Ext) = if (x.neg) x.mag.negate() else x.mag

            fun add(a: Ext, b: Ext): Ext {
                val e = minOf(a.exp, b.exp)
                val s = signed(a).shiftLeft(a.exp - e).add(signed(b).shiftLeft(b.exp - e))
                return round(s.signum() < 0, s.abs(), e, false, 64)
            }

            fun sub(a: Ext, b: Ext) = add(a, Ext(!b.neg, b.mag, b.exp))
            fun mul(a: Ext, b: Ext) = round(a.neg != b.neg, a.mag.multiply(b.mag), a.exp + b.exp, false, 64)

            fun div(a: Ext, b: Ext): Ext {
                require(b.mag.signum() != 0)
                val k = 140 + b.mag.bitLength()
                val qr = a.mag.shiftLeft(k).divideAndRemainder(b.mag)
                return round(a.neg != b.neg, qr[0], a.exp - b.exp - k, qr[1].signum() != 0, 64)
            }

            fun sqrt(a: Ext): Ext {
                require(!a.neg || a.mag.signum() == 0)
                var shift = 280
                if ((a.exp - shift) % 2 != 0) shift++
                val n = a.mag.shiftLeft(shift)
                val s = isqrt(n)
                return round(false, s, (a.exp - shift) / 2, s.multiply(s) != n, 64)
            }

            /** trunc(d × k) com o produto exato (o x87 estendido não arredonda double × 1000 nem × 100). */
            fun truncTimes(d: Double, k: Int): Int {
                val x = of(d)
                val p = x.mag.multiply(BigInteger.valueOf(k.toLong()))
                val t = if (x.exp >= 0) p.shiftLeft(x.exp) else p.shiftRight(-x.exp)   // magnitude: shiftRight = trunc
                return (if (x.neg) t.negate() else t).toInt()
            }

            private fun isqrt(n: BigInteger): BigInteger {
                if (n.signum() == 0) return n
                var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
                while (true) {
                    val y = x.add(n.divide(x)).shiftRight(1)
                    if (y >= x) return x
                    x = y
                }
            }

            /** Arredonda ±mag×2^exp (+ um resto positivo minúsculo se `sticky`) para `bits` bits, ao par mais próximo. */
            fun round(neg: Boolean, mag: BigInteger, exp: Int, sticky: Boolean, bits: Int): Ext {
                if (mag.signum() == 0) return Ext(false, BigInteger.ZERO, 0)
                val len = mag.bitLength()
                val shift = len - bits
                if (shift <= 0) return Ext(neg, mag, exp)   // cabe; o resto (sticky) fica bem abaixo de meia unidade
                var q = mag.shiftRight(shift)
                val rem = mag.subtract(q.shiftLeft(shift))
                val half = BigInteger.ONE.shiftLeft(shift - 1)
                val c = rem.compareTo(half)
                if (c > 0 || (c == 0 && (sticky || q.testBit(0)))) q = q.add(BigInteger.ONE)
                return Ext(neg, q, exp + shift)
            }
        }
    }
}
