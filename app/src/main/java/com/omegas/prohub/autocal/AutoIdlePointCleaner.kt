package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Política pura do apagamento automático (spec 2026-10-07-autocal-apagar-lenta):
 * "banda do GNV cuja última aquisição foi na lenta → quando o carro estiver andando, apaga".
 *
 * Dispara quando: há banda marcada pelo [IdleAcquisitionTracker] com contador > 0 na última
 * leitura; o quadro de telemetria mais recente (fresco) é GNV com rpm >= 1000; AUTO_CAL_ENABLE == 1;
 * a sessão USB já passou do tempo de estabilização; e passaram >= 5 s do último apagamento
 * BEM-SUCEDIDO. Não há teto por sessão nem por ponto; não há guarda de AutoMatch (apagar antes do
 * AutoMatch é intencional). "Tentar depois" (porta ocupada, preparação manual) não consome o intervalo.
 *
 * Falha com mutação possível: bloqueia [failureBlockMs] e exige uma releitura nova dos buffers GNV.
 * Readback ineficaz ou gasolina alterada de forma anormal: [disable] até a próxima sessão USB.
 *
 * Não é thread-safe: o coordenador o usa a partir de um único executor.
 */
class AutoIdlePointCleaner(
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val failureBlockMs: Long = FAILURE_BLOCK_MS,
    private val settleMs: Long = SESSION_SETTLE_MS,
    private val drivingRpm: Int = IdleAcquisitionTracker.IDLE_RPM,
    private val frameMaxAgeMs: Long = FRAME_MAX_AGE_MS,
) {
    class Input(
        val nowElapsedMs: Long,
        val idleBands: Map<Int, IdleAcquisitionTracker.Evidence>,
        val lastCounters: IntArray?,
        val latestFrame: NativeAnchorTelemetryWindow.Frame?,
        val autoCalEnabled: Int?,
        val sessionAgeMs: Long,
    )

    sealed class Decision {
        data class Delete(
            val bands: List<Int>,
            val evidence: List<IdleAcquisitionTracker.Evidence>,
            val frame: NativeAnchorTelemetryWindow.Frame,
        ) : Decision()

        data class Wait(val reason: String) : Decision()
    }

    private var lastSuccessAtMs: Long? = null
    private var blockedUntilMs = 0L
    private var needsReread = false
    private var disabledReason: String? = null
    private var consecutiveFailures = 0
    private var lastDecision: String = ""

    fun decide(input: Input): Decision = decideInternal(input).also { lastDecision = it.toString().take(200) }

    private fun decideInternal(input: Input): Decision {
        disabledReason?.let { return Decision.Wait("Apagamento automático desligado nesta sessão: $it") }
        if (input.autoCalEnabled != 1) return Decision.Wait("AUTO_CAL_ENABLE não está em 1")
        if (input.sessionAgeMs < settleMs) return Decision.Wait("Sessão USB ainda estabilizando")
        if (needsReread) return Decision.Wait("Aguardando releitura dos buffers GNV depois de falha")
        if (input.nowElapsedMs < blockedUntilMs) return Decision.Wait("Automático bloqueado depois de falha")
        lastSuccessAtMs?.let { last ->
            if (input.nowElapsedMs - last < minIntervalMs) return Decision.Wait("Intervalo mínimo entre apagamentos")
        }
        if (input.idleBands.isEmpty()) return Decision.Wait("Nenhuma banda GNV marcada como lenta")
        val counters = input.lastCounters
        val targets = input.idleBands.keys
            .filter { band -> band in 0 until IdleAcquisitionTracker.BAND_COUNT }
            .filter { band -> (counters?.getOrNull(band) ?: 0) > 0 }
            .sorted()
        if (targets.isEmpty()) return Decision.Wait("Bandas marcadas já estão vazias na última leitura")
        val frame = input.latestFrame
        val moving = frame != null &&
            frame.plausible &&
            frame.fuel.uppercase() in IdleAcquisitionTracker.GNV_NAMES &&
            frame.rpm >= drivingRpm &&
            input.nowElapsedMs - frame.elapsedMs in 0..frameMaxAgeMs
        if (!moving) return Decision.Wait("Aguardando o carro andando no GNV (rpm >= $drivingRpm)")
        return Decision.Delete(targets, targets.mapNotNull { input.idleBands[it] }, frame!!)
    }

    /**
     * Apagamento confirmado. `rereadRequired`: o readback foi ambíguo (não prova nem desprova o apagamento):
     * consome o intervalo e espera uma releitura dos buffers GNV; nunca desliga a sessão.
     */
    fun onSucceeded(nowElapsedMs: Long, rereadRequired: Boolean = false) {
        lastSuccessAtMs = nowElapsedMs
        needsReread = rereadRequired
        blockedUntilMs = 0L
        consecutiveFailures = 0
    }

    /** Porta ocupada, preparação manual, guarda serial: só tenta de novo; nada é consumido. */
    fun onRetryLater() = Unit

    /**
     * Toda falha da ação automática (já aberta) consome tempo: sem mutação, o intervalo mínimo; com mutação
     * possível, [failureBlockMs] + releitura. [MAX_CONSECUTIVE_FAILURES] seguidas (ex.: NAK persistente)
     * desligam o automático na sessão. Colisão de porta/guarda antes de abrir a ação é [onRetryLater].
     */
    fun onFailed(nowElapsedMs: Long, mutationMayHaveStarted: Boolean) {
        consecutiveFailures += 1
        if (mutationMayHaveStarted) {
            blockedUntilMs = maxOf(blockedUntilMs, nowElapsedMs + failureBlockMs)
            needsReread = true
        } else {
            blockedUntilMs = maxOf(blockedUntilMs, nowElapsedMs + minIntervalMs)
        }
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES && disabledReason == null) {
            disable("$consecutiveFailures falhas seguidas do apagamento automático")
        }
    }

    /** Chegou uma leitura nova e confirmada dos buffers GNV. */
    fun onReread() {
        needsReread = false
    }

    fun disable(reason: String) {
        disabledReason = reason.take(200)
    }

    fun disabledReason(): String? = disabledReason

    /** invalidateRound / ação manual: zera bloqueios de leitura, mantém intervalo e desligamento da sessão. */
    fun reset() {
        needsReread = false
        blockedUntilMs = 0L
    }

    /** Sessão USB nova ou encerrada: tudo volta ao início. */
    fun resetSession() {
        lastSuccessAtMs = null
        blockedUntilMs = 0L
        needsReread = false
        disabledReason = null
        consecutiveFailures = 0
        lastDecision = ""
    }

    fun json(): JSONObject = JSONObject()
        .put("enabled", disabledReason == null)
        .put("disabledReason", disabledReason ?: JSONObject.NULL)
        .put("lastSuccessAtElapsedMs", lastSuccessAtMs ?: JSONObject.NULL)
        .put("blockedUntilElapsedMs", blockedUntilMs)
        .put("needsReread", needsReread)
        .put("lastDecision", lastDecision)
        .put("minIntervalMs", minIntervalMs)
        .put("scope", JSONArray().put("GNV"))

    companion object {
        /** ACK/readback (~1,2 s) + um snapshot nativo (mediana ~3 s). */
        const val MIN_INTERVAL_MS = 5_000L
        const val FAILURE_BLOCK_MS = 10_000L
        const val MAX_CONSECUTIVE_FAILURES = 5
        /** Mesmo tempo de estabilização do [NativeAutoCalMonitor]. */
        const val SESSION_SETTLE_MS = NativeAutoCalMonitor.SESSION_SETTLE_MS
        const val FRAME_MAX_AGE_MS = 1_500L
    }
}
