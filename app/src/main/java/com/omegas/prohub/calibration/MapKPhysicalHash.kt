package com.omegas.prohub.calibration

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Hash canônico e versionado das 13×12 raws físicas do Mapa K. */
object MapKPhysicalHash {
    const val SCHEMA = "mp48-map-k-physical-v1"
    const val ROWS = 13
    const val COLUMNS = 12

    fun hash(rows: List<List<Int>>): String {
        require(rows.size == ROWS) { "Mapa K físico exige exatamente $ROWS linhas; recebidas ${rows.size}" }
        require(rows.all { it.size == COLUMNS }) { "Cada linha do Mapa K físico exige exatamente $COLUMNS colunas" }
        require(rows.flatten().all { it in 0..0xFF }) { "Mapa K físico contém raw fora de U8" }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SCHEMA.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        rows.forEach { row -> row.forEach { raw -> digest.update(raw.toByte()) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
