package com.omegas.prohub.diagnostics

import org.json.JSONObject
import java.security.MessageDigest

/** Contexto fixo da sessão para o resumo (tudo que não vem do fluxo de eventos). Campo desconhecido = null. */
data class SummaryContext(
    val sessionId: String,
    val sessionUuid: String,
    val startMs: Long,
    /** Chave estável (aparelho + boot + início monotônico) e boot; null em sessões antigas. */
    val sessionKey: String? = null,
    val bootId: String? = null,
    val startElapsedMs: Long? = null,
    val buildType: String? = null,
    val appVersion: String? = null,
    val commit: String? = null,
    val deviceId: String? = null,
    val carProfile: String? = null,
    val vin: String? = null,
    /** Hash do Mapa K / Curva K guardados no app quando a sessão começou (não confirmados pela ECU nesta sessão). */
    val mapHashAtStart: String? = null,
    val curveHashAtStart: String? = null,
    /** Último nível bruto do cilindro da sessão anterior (livro-caixa); null = não se sabe. */
    val previousGnvEndRaw: Int? = null,
)

/** Fontes de velocidade, em ordem de prioridade (multimídia, GPS, proxy de rpm). */
internal object SpeedSource {
    const val MM = "MM"
    const val GPS = "GPS"
    const val RPM_PROXY = "RPM_PROXY"
    val ALL = listOf(MM, GPS, RPM_PROXY)
}

/** Somas de um trecho (segmento de tanque ou segundo). Tempo em ms; km por trapézio. Soma = `plus`. */
internal class SrcTotals {
    var samples = 0L
    var coverageMs = 0.0
    var movingMs = 0.0
    var stoppedMs = 0.0
    var gapMs = 0.0
    var km = 0.0
    var kmOnGnv = 0.0

    fun isEmpty() = samples == 0L && coverageMs == 0.0 && gapMs == 0.0
    fun plus(o: SrcTotals) {
        samples += o.samples; coverageMs += o.coverageMs; movingMs += o.movingMs; stoppedMs += o.stoppedMs
        gapMs += o.gapMs; km += o.km; kmOnGnv += o.kmOnGnv
    }
}

internal class Totals {
    var coveredMs = 0.0
    var gnvMs = 0.0
    var petrolMs = 0.0
    var gasInjectingMs = 0.0
    var injectorOpenMs = 0.0
    var rpmWeighted = 0.0
    val src = SpeedSource.ALL.associateWith { SrcTotals() }

    fun plus(o: Totals) {
        coveredMs += o.coveredMs; gnvMs += o.gnvMs; petrolMs += o.petrolMs; gasInjectingMs += o.gasInjectingMs
        injectorOpenMs += o.injectorOpenMs; rpmWeighted += o.rpmWeighted
        SpeedSource.ALL.forEach { src.getValue(it).plus(o.src.getValue(it)) }
    }

    fun copy(): Totals = Totals().also { it.plus(this) }

    fun isEmpty() = coveredMs == 0.0 && src.values.all { it.isEmpty() }

    /** km por fonte: null quando a fonte nunca mediu nada (desconhecido), nunca 0; proxy de rpm não tem km. */
    fun kmOf(source: String, onGnv: Boolean = false): Double? {
        if (source == SpeedSource.RPM_PROXY) return null
        val s = src.getValue(source)
        if (s.coverageMs <= 0.0) return null
        return if (onGnv) s.kmOnGnv else s.km
    }

    fun sourceJson(source: String, thresholdLabel: Pair<String, Number>): JSONObject {
        val s = src.getValue(source)
        val o = JSONObject()
        o.put("available", s.samples > 0L)
        o.put("proxy", source == SpeedSource.RPM_PROXY)
        o.put(thresholdLabel.first, thresholdLabel.second)
        o.put("samples", s.samples)
        o.put("coverageMs", nullable(s.coverageMs.takeIf { s.samples > 0L }, 1))
        o.put("movingMs", nullable(s.movingMs.takeIf { s.coverageMs > 0.0 }, 1))
        o.put("stoppedMs", nullable(s.stoppedMs.takeIf { s.coverageMs > 0.0 }, 1))
        o.put("gapMs", nullable(s.gapMs.takeIf { s.samples > 0L }, 1))
        o.put("km", nullable(kmOf(source), 4))
        o.put("kmOnGnv", nullable(kmOf(source, onGnv = true), 4))
        return o
    }

    companion object {
        fun nullable(value: Double?, digits: Int): Any {
            if (value == null || value.isNaN() || value.isInfinite()) return JSONObject.NULL
            val f = Math.pow(10.0, digits.toDouble())
            return Math.round(value * f) / f
        }

        fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
