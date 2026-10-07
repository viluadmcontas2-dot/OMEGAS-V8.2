package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Política pura do apagamento automático (spec 2026-10-07-autocal-apagar-lenta, revisão 2):
 * "banda fora da curva → apaga com o carro rodando no combustível dela".
 *
 * Dispara quando: há banda fora da curva ([OutlierCurveTracker]) com contador > 0 na última leitura de um
 * combustível; o quadro de telemetria mais recente (fresco) é desse MESMO combustível com rpm >= 1000 (GNV
 * fora da curva com o carro em GNV; gasolina fora da curva com o carro em gasolina); AUTO_CAL_ENABLE == 1; a
 * sessão USB já passou do tempo de estabilização; e passaram >= 5 s do último apagamento (intervalo global,
 * um combustível por comando). Não há teto por ponto nem "forma real": o ponto contaminado volta no mesmo
 * lugar e é apagado de novo. "Tentar depois" (porta ocupada, preparação manual) não consome o intervalo.
 *
 * Toda falha consome tempo; com mutação possível, bloqueia [failureBlockMs] e exige releitura. Readback
 * ineficaz, guarda do outro combustível ou 5 falhas seguidas: [disable] até a próxima sessão USB.
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
        /** Bandas fora da curva por combustível (as com contador 0 são ignoradas). */
        val candidates: Map<Fuel, List<OutlierCurveTracker.Outlier>>,
        val latestFrame: NativeAnchorTelemetryWindow.Frame?,
        val autoCalEnabled: Int?,
        val sessionAgeMs: Long,
    )

    sealed class Decision {
        data class Delete(
            val fuel: Fuel,
            val bands: List<Int>,
            val outliers: List<OutlierCurveTracker.Outlier>,
            val frame: NativeAnchorTelemetryWindow.Frame,
        ) : Decision()

        data class Wait(val reason: String) : Decision()
    }

    private var lastSuccessAtMs: Long? = null
    private var blockedUntilMs = 0L
    /**
     * Releitura obrigatória por combustível: instante (elapsed) da falha/ambiguidade. Só uma leitura COMPLETA e
     * confirmada daquele combustível, observada DEPOIS desse instante, libera (revisão 2026-10-07 #3).
     */
    private val rereadPendingSince = HashMap<Fuel, Long>()
    private val needsReread: Boolean get() = rereadPendingSince.isNotEmpty()
    private var disabledReason: String? = null
    private var consecutiveFailures = 0
    private var lastDecision: String = ""

    fun decide(input: Input): Decision = decideInternal(input).also { lastDecision = it.toString().take(200) }

    private fun decideInternal(input: Input): Decision {
        disabledReason?.let { return Decision.Wait("Limpeza automática pausada: $it") }
        if (input.autoCalEnabled != 1) return Decision.Wait("O aprendizado da ECU está pausado ou ainda não foi lido")
        if (input.sessionAgeMs < settleMs) return Decision.Wait("Conexão USB ainda estabilizando")
        if (needsReread) return Decision.Wait("Aguardando uma leitura nova da ECU depois de uma falha")
        if (input.nowElapsedMs < blockedUntilMs) return Decision.Wait("Aguardando um pouco depois de uma falha")
        lastSuccessAtMs?.let { last ->
            if (input.nowElapsedMs - last < minIntervalMs) return Decision.Wait("Intervalo mínimo entre apagamentos")
        }
        val usable = input.candidates.mapValues { (_, list) ->
            list.filter { it.counter > 0 && it.band in 0 until IdleAcquisitionTracker.BAND_COUNT }.sortedBy { it.band }
        }.filterValues { it.isNotEmpty() }
        if (usable.isEmpty()) return Decision.Wait("Nenhum ponto fora da curva")
        val frame = input.latestFrame
        val running = frame != null &&
            frame.plausible &&
            frame.rpm >= drivingRpm &&
            input.nowElapsedMs - frame.elapsedMs in 0..frameMaxAgeMs
        val frameFuel = frame?.let { fuelOf(it.fuel) }
        val outliers = if (running && frameFuel != null) usable[frameFuel] else null
        if (outliers == null) {
            // Frase para a tela (regra 6): sem rpm nem código; o número vai em "Detalhes técnicos" (json).
            val waiting = usable.keys.sorted().joinToString(" ou ") { if (it == Fuel.GAS) "no GNV" else "na gasolina" }
            return Decision.Wait("Aguardando o carro rodar $waiting")
        }
        return Decision.Delete(frameFuel!!, outliers.map { it.band }, outliers, frame!!)
    }

    /**
     * Contexto de um apagamento automático JÁ EM VOO (revisão 2026-10-07 #2): o combustível e o rpm podem mudar
     * entre a decisão e o envio. Devolve o motivo humano para NÃO enviar máscaras/commit agora, ou nulo se o quadro
     * mais recente é fresco, plausível, do MESMO combustível do alvo e com o carro rodando. Puro: sem I/O.
     */
    fun contextReason(fuel: Fuel, latestFrame: NativeAnchorTelemetryWindow.Frame?, nowElapsedMs: Long): String? {
        if (latestFrame == null || !latestFrame.plausible || nowElapsedMs - latestFrame.elapsedMs !in 0..frameMaxAgeMs) {
            return "Sem telemetria fresca do carro durante o apagamento"
        }
        val frameFuel = fuelOf(latestFrame.fuel)
        if (frameFuel != fuel) {
            return if (fuel == Fuel.GAS) "O carro passou para a gasolina durante o apagamento do GNV"
            else "O carro passou para o GNV durante o apagamento da gasolina"
        }
        if (latestFrame.rpm < drivingRpm) return "O carro parou de rodar durante o apagamento"
        return null
    }

    private fun fuelOf(wire: String): Fuel? = when (wire.uppercase()) {
        in IdleAcquisitionTracker.GNV_NAMES -> Fuel.GAS
        "GASOLINA", "PETROL" -> Fuel.PETROL
        else -> null
    }

    /**
     * Apagamento confirmado. `rereadRequired`: o readback foi ambíguo (não prova nem desprova o apagamento):
     * consome o intervalo e espera uma releitura completa dos buffers de [fuels]; nunca desliga a sessão.
     */
    fun onSucceeded(nowElapsedMs: Long, rereadRequired: Boolean = false, fuels: Set<Fuel> = Fuel.entries.toSet()) {
        lastSuccessAtMs = nowElapsedMs
        if (rereadRequired) requireReread(fuels, nowElapsedMs) else rereadPendingSince.clear()
        blockedUntilMs = 0L
        consecutiveFailures = 0
    }

    /** Porta ocupada, preparação manual, guarda serial: só tenta de novo; nada é consumido. */
    fun onRetryLater() = Unit

    /**
     * Toda falha da ação automática (já aberta) consome tempo: sem mutação, o intervalo mínimo; com mutação
     * possível, [failureBlockMs] + releitura completa de [fuels] (sem combustível conhecido: dos dois).
     * [MAX_CONSECUTIVE_FAILURES] seguidas (ex.: NAK persistente) desligam o automático na sessão. Colisão de
     * porta/guarda antes de abrir a ação é [onRetryLater]. Desarmar é decisão do coordenador.
     */
    fun onFailed(nowElapsedMs: Long, mutationMayHaveStarted: Boolean, fuels: Set<Fuel> = Fuel.entries.toSet()) {
        consecutiveFailures += 1
        if (mutationMayHaveStarted) {
            blockedUntilMs = maxOf(blockedUntilMs, nowElapsedMs + failureBlockMs)
            requireReread(fuels, nowElapsedMs)
        } else {
            blockedUntilMs = maxOf(blockedUntilMs, nowElapsedMs + minIntervalMs)
        }
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES && disabledReason == null) {
            disable("$consecutiveFailures falhas seguidas do apagamento automático")
        }
    }

    private fun requireReread(fuels: Set<Fuel>, sinceElapsedMs: Long) {
        (fuels.ifEmpty { Fuel.entries.toSet() }).forEach { fuel ->
            rereadPendingSince[fuel] = maxOf(rereadPendingSince[fuel] ?: Long.MIN_VALUE, sinceElapsedMs)
        }
    }

    /**
     * Chegou uma leitura COMPLETA e confirmada dos buffers de [fuel] (o coordenador só chama com os três vetores).
     * Libera a releitura obrigatória desse combustível apenas se foi observada DEPOIS da falha; leitura do outro
     * combustível, de instante igual/anterior (replay, cache) não libera nada.
     */
    fun onReread(fuel: Fuel, observedAtElapsedMs: Long) {
        val since = rereadPendingSince[fuel] ?: return
        if (observedAtElapsedMs > since) rereadPendingSince.remove(fuel)
    }

    fun disable(reason: String) {
        disabledReason = reason.take(200)
    }

    fun disabledReason(): String? = disabledReason

    /**
     * Novo toque do dono depois de uma pausa: limpa a pausa e a contagem de falhas seguidas, mas NÃO o bloqueio
     * de falha com mutação incerta ([blockedUntilMs]/[needsReread]): rearmar não pode ignorar o que ainda não foi relido.
     */
    fun rearm() {
        disabledReason = null
        consecutiveFailures = 0
    }

    /**
     * Sessão USB nova ou encerrada: tudo volta ao início. É, com [onReread] (leitura completa, nova e posterior do
     * combustível alvo), a única saída da releitura obrigatória: invalidação de round ou confirmação manual não
     * leem o alvo e não liberam nada.
     */
    fun resetSession() {
        lastSuccessAtMs = null
        blockedUntilMs = 0L
        rereadPendingSince.clear()
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
        .put("rereadPendingFuels", JSONArray(rereadPendingSince.keys.map { it.wireName }.sorted()))
        .put("lastDecision", lastDecision)
        .put("minIntervalMs", minIntervalMs)
        .put("drivingRpm", drivingRpm)
        .put("scope", JSONArray().put(Fuel.GAS.wireName).put(Fuel.PETROL.wireName))

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
