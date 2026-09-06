package com.omegas.prohub.obd

/** Conversão canônica do byte Mode 01 PID 06 para STFT em porcentagem SAE. */
object ObdStftCodec {
    fun percent(rawByte: Int): Double {
        require(rawByte in 0..255) { "STFT raw byte fora do domínio: $rawByte" }
        return (rawByte - 128.0) * 100.0 / 128.0
    }
}
