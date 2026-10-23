package com.omegas.prohub.carinfo

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Leitor puro de bytes de Parcel (little-endian), para decodificar o que a multimídia manda SEM `android.os.Parcel`
 * (teste JVM com payload simulado) e sem nunca estourar: ao faltar byte lança [ParcelUnderflow], que o chamador captura.
 * Formato como o do Parcel do Android: int/float = 4 bytes; String = int com nº de chars (-1 = nula) + UTF-16 + terminador,
 * alinhado a 4 bytes; byte[] = int com o tamanho (-1 = nulo) + bytes alinhados a 4.
 */
class ParcelUnderflow(message: String) : RuntimeException(message)

class ParcelBytes(private val bytes: ByteArray) : DashBoardFieldReader {
    private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    override fun remainingBytes(): Int = buf.remaining()

    private fun need(n: Int) { if (n < 0 || buf.remaining() < n) throw ParcelUnderflow("faltam bytes ($n)") }

    override fun readInt(): Int { need(4); return buf.int }
    override fun readFloat(): Float { need(4); return buf.float }
    fun readLong(): Long { need(8); return buf.long }
    fun readDouble(): Double { need(8); return buf.double }

    fun readString(): String? {
        val chars = readInt()
        if (chars == -1) return null
        if (chars < 0 || chars > MAX_STRING_CHARS) throw ParcelUnderflow("texto inválido ($chars)")
        val body = chars * 2
        val padded = (body + 2 + 3) and 3.inv()
        need(padded)
        val text = String(bytes, buf.position(), body, Charsets.UTF_16LE)
        buf.position(buf.position() + padded)
        return text
    }

    fun readByteArray(): ByteArray? {
        val len = readInt()
        if (len == -1) return null
        if (len < 0 || len > MAX_BYTES) throw ParcelUnderflow("vetor inválido ($len)")
        val padded = (len + 3) and 3.inv()
        need(padded)
        val out = bytes.copyOfRange(buf.position(), buf.position() + len)
        buf.position(buf.position() + padded)
        return out
    }

    companion object {
        const val MAX_STRING_CHARS = 4096
        const val MAX_BYTES = 65_536

        fun hex(bytes: ByteArray, maxBytes: Int = Int.MAX_VALUE): String {
            val n = minOf(bytes.size, maxBytes)
            val sb = StringBuilder(n * 2 + 1)
            for (i in 0 until n) sb.append(Character.forDigit((bytes[i].toInt() shr 4) and 15, 16)).append(Character.forDigit(bytes[i].toInt() and 15, 16))
            if (bytes.size > n) sb.append('+')
            return sb.toString()
        }
    }
}
