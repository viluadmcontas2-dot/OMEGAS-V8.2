package com.omegas.prohub.calibration

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Nome e pasta do arquivo da Curva K que o dono salva (regra 15 do AGENTS.md).
 * Único lugar que decide o nome: Download/Omegas/Curva/Curva K - dd-MM-yyyy HH'h'mm'm'ss's' - N pontos - salva manualmente.json
 * Só o botão Salvar publica este arquivo; a foto interna do Desfazer fica no armazenamento privado do app.
 */
object KFactorCurveFileName {
    const val PUBLIC_SUBFOLDER = "Curva"
    const val SUFFIX = "salva manualmente"

    fun of(createdAtMs: Long, pointCount: Int, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("dd-MM-yyyy HH'h'mm'm'ss's'", Locale.US).apply { timeZone = zone }
        return "Curva K - ${format.format(Date(createdAtMs))} - $pointCount pontos - $SUFFIX.json"
    }
}
