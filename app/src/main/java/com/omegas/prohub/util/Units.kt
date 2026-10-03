package com.omegas.prohub.util

import java.util.Locale

/**
 * Única fonte de formatos de exibição (GLOSSARIO): pt-BR fixo, nunca o idioma do aparelho.
 * ms 2 casas · limites de faixa 1 casa · MAP 3 casas · K 3 casas · diferença em % com sinal e 1 casa ·
 * RPM inteiro com ponto de milhar · desconhecido sempre "—".
 */
object Units {
    val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    const val UNKNOWN = "—"

    private fun fmt(value: Double?, pattern: String): String =
        if (value == null || value.isNaN() || value.isInfinite()) UNKNOWN else String.format(PT_BR, pattern, value)

    /** "3,45" */
    fun ms(value: Double?): String = fmt(value, "%.2f")
    fun msUnit(value: Double?): String = if (value == null || value.isNaN() || value.isInfinite()) UNKNOWN else ms(value) + " ms"
    /** Limite de faixa de injeção: "3,4" */
    fun msBand(value: Double?): String = fmt(value, "%.1f")
    /** "0,412" */
    fun map(value: Double?): String = fmt(value, "%.3f")
    fun mapUnit(value: Double?): String = if (value == null || value.isNaN() || value.isInfinite()) UNKNOWN else map(value) + " bar"
    /** K: 3 casas, sem unidade. */
    fun k(value: Double?): String = fmt(value, "%.3f")
    /** Diferença em %, com sinal: "+2,1%", "-0,4%", "0,0%". */
    fun gapPercent(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return UNKNOWN
        val rounded = Math.round(value * 10.0) / 10.0
        val sign = if (rounded > 0) "+" else ""
        return sign + String.format(PT_BR, "%.1f", if (rounded == 0.0) 0.0 else rounded) + "%"
    }
    /** Percentual sem sinal e sem casa: "3%". */
    fun percentWhole(value: Double?): String = if (value == null || value.isNaN() || value.isInfinite()) UNKNOWN else fmt(value, "%.0f") + "%"
    /** RPM inteiro: "2.032". */
    fun rpm(value: Double?): String = fmt(value, "%,.0f")
    fun rpm(value: Int?): String = rpm(value?.toDouble())
    /** Razão GNV/gasolina como diferença percentual com sinal. */
    fun gapFromRatio(ratio: Double?): String = if (ratio == null) UNKNOWN else gapPercent((ratio - 1.0) * 100.0)
}
