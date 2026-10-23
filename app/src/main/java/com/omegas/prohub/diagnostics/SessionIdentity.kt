package com.omegas.prohub.diagnostics

import java.util.UUID

/**
 * Identidade ESTÁVEL da sessão (export v2): aparelho + boot + início monotônico. Não nasce da parede do relógio, que
 * erra (2025 em vez de 2026) e salta (RTC corrigido no meio da gravação, 343 dias numa sessão real). Puro.
 */
object SessionIdentity {
    /** `S_<aparelho 8>_<boot 8>_<início monotônico em s>`; duas cópias da mesma sessão sempre dão a mesma chave. */
    fun sessionKey(deviceId: String, bootId: String, startElapsedMs: Long): String =
        "S_${deviceId.take(8)}_${bootId.replace("-", "").take(8)}_${startElapsedMs / 1000L}"

    /** UUID determinístico da chave (nunca aleatório: reprocessar a sessão não cria outra). */
    fun uuidOf(key: String): String = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()

    /**
     * Identificador do boot: `/proc/sys/kernel/random/boot_id` (8 primeiros hex). Sem leitura (SELinux, erro), deriva de
     * `parede − monotônico` arredondado a 1 min e marca com prefixo `d` (derivado; pode virar no limite do minuto).
     */
    fun bootId(read: () -> String?, wallMs: Long, elapsedMs: Long): String {
        val raw = try { read() } catch (_: Exception) { null }?.trim().orEmpty().replace("-", "")
        if (raw.length >= 8 && raw.take(8).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return raw.take(8).lowercase()
        return "d" + java.lang.Long.toHexString(((wallMs - elapsedMs) / 60_000L)).takeLast(7).padStart(7, '0')
    }
}
