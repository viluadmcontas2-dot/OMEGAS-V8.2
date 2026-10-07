package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor

/**
 * Apagamento automático de pontos FORA DA CURVA do GNV e da gasolina (spec 2026-10-07-autocal-apagar-lenta,
 * revisão 2): banda fora da curva é apagada toda vez que estiver fora, com o carro rodando no combustível dela
 * (rpm >= 1000), um combustível por comando e 5 s entre apagamentos.
 *
 * Liga o [OutlierCurveTracker] (detecção), o [AutoIdlePointCleaner] (política) e o
 * [AutoCalNativeActionManager.executeAutomaticPointDelete] (execução). O [IdleAcquisitionTracker] continua só
 * como evidência no recibo (fração de quadros com rpm < 1000 na última aquisição da banda). Mora no serviço:
 * funciona com a tela fechada. Toda decisão roda no [executor] próprio, nunca na thread do autoCalTick — o
 * monitor só entrega as leituras confirmadas dos buffers ([onBuffers]) e volta.
 *
 * Exceção à regra "nada muda sozinho": GNV e gasolina, com readback, guarda do outro combustível e registro.
 * Curva K continua só com o dono.
 *
 * Desenho aprovado (dono, 2026-10-07): a limpeza começa DESARMADA em toda sessão USB; só um toque do dono arma
 * ([setArmed]); o toque não apaga nada por si. Desarmam: outro toque, sessão USB nova, reset/ação manual do dono
 * ([onManualMutation]) e qualquer pausa por falha/guarda (que exige novo toque). A confirmação do próprio
 * apagamento automático NÃO desarma. Rearmar limpa a pausa, nunca o bloqueio de falha com mutação incerta.
 *
 * Revisão 2026-10-07: a INTENÇÃO manual do dono (reset/pausa/escrita K/Mapa/restauração) desarma na hora e de forma
 * síncrona ([onManualIntent]), antes de a escrita começar; o recibo da operação automática já em voo ainda é
 * processado, mas nenhum disparo novo sai. Achados importantes (mesmo dia): (1) toda falha REAL do automático,
 * inclusive a primeira de transporte e o recibo perdido (timeout), desarma até novo toque, preservando bloqueio e
 * releitura obrigatória; (2) o gerenciador revalida o contexto em voo via [automaticContextReason] antes das máscaras
 * e antes do commit; (3) a releitura obrigatória só é liberada por leitura completa, nova e posterior do combustível
 * alvo. Armar valida a geração USB atual ([currentSessionId]) contra a
 * sessão vista pelo coordenador, para não armar uma sessão velha enquanto o reset assíncrono está na fila. Toda
 * mudança de estado visível (inclusive as assíncronas) chama [onChanged], e só quando algo mudou.
 */
class AutoIdleCleanupCoordinator(
    private val telemetry: Mp48TelemetryWindowSource,
    private val executeDelete: (List<AutoCalPointDeleteProtocol.Target>, JSONObject) -> JSONObject,
    private val autoCalEnabled: () -> Int?,
    private val sessionAgeMs: () -> Long,
    /** Motivo para NÃO agir agora (ex.: sem controle local do MP48), ou nulo. */
    private val canAct: () -> String? = { null },
    private val record: (String, JSONObject) -> Unit = { _, _ -> },
    private val clock: () -> Long,
    private val executor: Executor,
    /** Geração USB atual (a mesma que o gerente de ações usa); nula = confiar na sessão vista. */
    private val currentSessionId: (() -> Long)? = null,
    /** Chamado (sob a trava, após a mudança) quando o estado publicado mudou: o serviço publica a revisão. */
    private val onChanged: () -> Unit = {},
) {
    /** Leitura confirmada dos três buffers de um combustível (G2/G4 ou snapshot completo), no relógio elapsed. */
    class Buffers(
        val sessionId: Long,
        val fuel: Fuel,
        val counters: IntArray,
        val timeRaw: IntArray?,
        val mapRaw: IntArray?,
        val observedAtElapsedMs: Long,
    )

    /** Por que o automático pausou nesta sessão; a tela traduz em português simples. */
    enum class PauseCode { OTHER_FUEL_GUARD, REPEATED_FAILURES, READBACK_INEFFECTIVE }

    /** Apagamento automático confirmado pela ECU (só as bandas com readback que provou o apagamento). */
    class RecentDelete(val receiptId: String, val fuel: Fuel, val indexes: List<Int>, val atMs: Long)

    /** O que a tela do AutoCal mostra: ligado/pausado, motivo e quantos pontos o app pediu para reaprender. */
    class UiSummary(
        val active: Boolean,
        /** O dono tocou em "Ativar limpeza automática" nesta sessão USB (e nada a desarmou desde então). */
        val armed: Boolean,
        /** Não está pausada por falha/guarda (distinto de armada). */
        val enabled: Boolean,
        val pauseCode: PauseCode?,
        val relearnedThisSession: Int,
        val recentDeletes: List<RecentDelete>,
        /** O que a limpeza está esperando agora, em português simples (vazio = apagamento em voo). */
        val waitReason: String = "",
    )

    private val outliers = OutlierCurveTracker()
    private val regimes = mapOf(
        Fuel.GAS to IdleAcquisitionTracker(fuelNames = IdleAcquisitionTracker.GNV_NAMES),
        Fuel.PETROL to IdleAcquisitionTracker(fuelNames = PETROL_NAMES),
    )
    /**
     * Apagamento automático em voo: o que foi pedido, quando e QUAL operação (geração USB + preparação do gerenciador).
     * Só o recibo com essa identidade conclui o voo; recibo de USB antiga, de outra preparação ou sem identidade é
     * ignorado e registrado (revisão 2026-10-07 #5): nunca consome, desarma nem libera o bloqueio de outra operação.
     */
    private class InFlight(val fuel: Fuel, val bands: List<Int>, val sinceMs: Long, val sessionId: Long) {
        var preparationId: String? = null
    }

    private val cleaner = AutoIdlePointCleaner()
    private var sessionId = 0L
    private var inFlight: InFlight? = null
    private var lastResult: JSONObject = JSONObject()
    @Volatile private var lastJson: JSONObject = JSONObject()
    @Volatile private var enabledNow = true
    @Volatile private var armed = false
    private var waitReason: String = DISARMED_REASON
    /** Por que está desarmada agora (toque do dono, falha, recibo perdido): a tela mostra isto enquanto desarmada. */
    private var disarmedReason: String = DISARMED_REASON
    private var pauseCode: PauseCode? = null
    private var relearned = 0
    private val recent = ArrayDeque<RecentDelete>()
    @Volatile private var summary = UiSummary(false, false, true, null, 0, emptyList(), DISARMED_REASON)
    private var publishedKey: String = ""

    /**
     * Toque do dono. Armar exige sessão USB válida e não apaga nada dentro do toque (a próxima avaliação decide).
     * Depois de uma pausa, armar limpa a pausa; o bloqueio de falha com mutação incerta continua até a releitura.
     * Síncrono (a ponte devolve o estado novo na hora), sob a mesma trava das decisões.
     */
    fun setArmed(armed: Boolean, source: String): JSONObject = synchronized(this) {
        if (armed) {
            val live = currentSessionId?.invoke() ?: sessionId
            val error = when {
                sessionId <= 0L || live <= 0L -> "Sem conexão USB com a ECU"
                live != sessionId -> "A conexão USB mudou; aguarde a leitura nova da ECU e toque de novo"
                else -> null
            }
            if (error != null) {
                return JSONObject().put("ok", false).put("armed", false).put("enabled", enabledNow)
                    .put("pauseCode", pauseCode?.name ?: JSONObject.NULL).put("error", error)
            }
            if (cleaner.disabledReason() != null) {
                cleaner.rearm()
                pauseCode = null
            }
            if (!this.armed) waitReason = ARMED_WAIT_REASON
            disarmedReason = DISARMED_REASON
        }
        setArmedLocked(armed, source)
        publish()
        JSONObject().put("ok", true).put("armed", this.armed).put("enabled", enabledNow)
            .put("pauseCode", pauseCode?.name ?: JSONObject.NULL)
    }

    /** Reset/ação manual confirmada do dono (ou falha manual com mutação possível): desarma; ele rearma se quiser. */
    fun onManualMutation(receipt: JSONObject) = submit {
        setArmedLocked(false, "manual:" + receipt.optString("action", "?"))
        publish()
    }

    /**
     * Intenção manual do dono (toque em reset/pausa/escrita K/Mapa/restauração): desarma ANTES de a escrita
     * começar, de forma síncrona. Não toca na operação automática já em voo (o recibo dela ainda é processado).
     */
    fun onManualIntent(action: String): Unit = synchronized(this) {
        setArmedLocked(false, "manual:" + action.ifBlank { "?" })
        publish()
    }

    fun onBuffers(buffers: Buffers) = submit {
        if (buffers.sessionId != sessionId) resetSessionLocked(buffers.sessionId)
        val time = buffers.timeRaw
        val map = buffers.mapRaw
        val complete = time != null && map != null &&
            minOf(buffers.counters.size, time.size, map.size) >= IdleAcquisitionTracker.BAND_COUNT
        if (complete) {
            // Só uma leitura COMPLETA (contador+tempo+MAP), de instante NOVO para este combustível (não replay/cache)
            // e posterior à falha libera a releitura obrigatória (revisão 2026-10-07 #3).
            val fresh = outliers.observe(
                buffers.fuel,
                OutlierCurveTracker.Reading(buffers.counters, time!!, map!!, buffers.observedAtElapsedMs),
            )
            if (fresh) cleaner.onReread(buffers.fuel, buffers.observedAtElapsedMs)
        }
        regimes.getValue(buffers.fuel).observe(
            IdleAcquisitionTracker.Reading(buffers.counters, time, map, buffers.observedAtElapsedMs),
        ) { from, to -> telemetry.recentTelemetryFrames(from, to) }
        evaluateLocked()
    }

    /**
     * Guarda de contexto de um apagamento automático JÁ EM VOO (revisão 2026-10-07 #2), chamada pelo gerenciador
     * antes das máscaras e antes do commit. Síncrona, sem I/O: lê só a janela de telemetria em memória e o estado
     * publicado. Devolve o motivo humano para abortar, ou nulo para seguir. Não substitui o `ensureSession` do
     * gerenciador (segurança conferida uma vez); soma a ele combustível, rpm, armamento e geração USB atuais.
     */
    fun automaticContextReason(fuel: Fuel): String? = synchronized(this) {
        if (!armed) return disarmedReason
        cleaner.disabledReason()?.let { return "Limpeza automática pausada: $it" }
        val live = currentSessionId?.invoke() ?: sessionId
        if (sessionId <= 0L || live != sessionId) return "A conexão USB mudou durante o apagamento"
        val now = clock()
        val latest = telemetry.recentTelemetryFrames(now - AutoIdlePointCleaner.FRAME_MAX_AGE_MS, now).lastOrNull()
        cleaner.contextReason(fuel, latest, now)
    }

    /** Chamado periodicamente: o carro pode começar a rodar no combustível do ponto sem leitura nova. */
    fun evaluate() = submit { evaluateLocked() }

    fun onSessionChanged(newSessionId: Long) = submit { resetSessionLocked(newSessionId) }

    /**
     * invalidateRound / ação manual confirmada: leituras anteriores não valem (o ponto volta a exigir duas leituras).
     * Não é leitura completa do alvo: intervalo, bloqueio de falha, releitura obrigatória e pausa seguem no cleaner;
     * só [onSessionChanged] ou uma leitura completa, nova e posterior do combustível alvo liberam a releitura.
     */
    fun onRoundInvalidated() = submit {
        outliers.reset()
        regimes.values.forEach { it.reset() }
        publish()
    }

    fun onActionConfirmed(receipt: JSONObject) = submit {
        if (!receipt.optBoolean("automatic", false) || receipt.optString("action") != "DELETE_POINT") return@submit
        val flight = flightFor(receipt) ?: return@submit
        inFlight = null
        val details = receipt.optJSONObject("details") ?: JSONObject()
        val fuel = flight.fuel
        val ambiguousBands = bandsWithResult(details, "AMBIGUOUS")
        // Readback ambíguo: consome o intervalo e espera releitura completa do alvo; nunca desliga a sessão.
        cleaner.onSucceeded(clock(), rereadRequired = details.optBoolean("readbackAmbiguous", false), fuels = setOf(fuel))
        val deleted = targetBands(details) - ambiguousBands.toSet()
        deleted.forEach { outliers.onDeleted(fuel, it) }
        regimes.getValue(fuel).markDeleted(deleted)
        outliers.forget(fuel, ambiguousBands + intList(details.optJSONArray("skippedChanged")))
        if (deleted.isNotEmpty()) {
            relearned += deleted.size
            recent.addLast(RecentDelete(receipt.optString("id"), fuel, deleted.sorted(), receipt.optLong("finishedAtMs", 0L)))
            while (recent.size > MAX_RECENT) recent.removeFirst()
        }
        val guard = details.optJSONObject("otherFuelGuard")
        if (guard?.optBoolean("abnormal", false) == true) {
            val other = if (fuel == Fuel.GAS) "A gasolina" else "O GNV"
            disableLocked("$other mudou de forma anormal durante o apagamento automático", receipt, PauseCode.OTHER_FUEL_GUARD)
        }
        publish()
    }

    fun onActionFailed(receipt: JSONObject) = submit {
        if (!receipt.optBoolean("automatic", false) || receipt.optString("action") != "DELETE_POINT") return@submit
        val flight = flightFor(receipt) ?: return@submit
        inFlight = null
        // Combustível/alvos da operação em voo (autoridade), não do recibo parseado.
        val fuel = flight.fuel
        val mutation = receipt.optBoolean("mutationMayHaveStarted", false)
        val emptyBands = intList(receipt.optJSONArray("emptyBands"))
        val skippedChanged = intList(receipt.optJSONArray("skippedChanged"))
        // Ponto já vazio ou readquirido desde a marca: sai dos candidatos até a próxima leitura.
        outliers.forget(fuel, emptyBands + skippedChanged)
        val ineffective = receipt.has("effective") && !receipt.optBoolean("effective", true)
        // Nada enviado e nada falhou na ECU (ponto já vazio / readquirido): não é falha real, não desarma.
        val benign = !mutation && !ineffective && (emptyBands.isNotEmpty() || skippedChanged.isNotEmpty())
        if (ineffective) {
            disableLocked("Readback mostrou que a ECU não apagou o ponto", receipt, PauseCode.READBACK_INEFFECTIVE)
        } else {
            // Toda falha consome tempo; N seguidas desligam a sessão.
            val wasEnabled = cleaner.disabledReason() == null
            cleaner.onFailed(clock(), mutation, fuels = setOf(fuel))
            val reason = cleaner.disabledReason()
            if (wasEnabled && reason != null) {
                pauseCode = PauseCode.REPEATED_FAILURES
                recordDisabled(reason, receipt)
                if (armed) setArmedLocked(false, "pause:${PauseCode.REPEATED_FAILURES.name}")
            } else if (!benign && armed) {
                // Revisão 2026-10-07 #1: toda falha real (inclusive a primeira, de transporte) desarma até novo toque.
                // Intervalo/bloqueio e releitura obrigatória ficam no cleaner: rearmar não os ignora.
                disarmedReason = if (mutation) FAILURE_UNCERTAIN_DISARMED_REASON else FAILURE_DISARMED_REASON
                setArmedLocked(false, "failure:" + receipt.optString("reasonCode").ifBlank { "?" })
            }
        }
        publish()
    }

    fun json(): JSONObject = JSONObject(lastJson.toString())

    /** Resumo para a tela (leitura sem trava: publicado a cada mudança). */
    fun uiSummary(): UiSummary = summary

    /** O apagamento automático pode agir nesta sessão: armado pelo dono E não pausado por readback/guarda/falhas. */
    fun automaticEnabled(): Boolean = armed && enabledNow

    private fun evaluateLocked() {
        val now = clock()
        inFlight?.let { flight ->
            if (now - flight.sinceMs < IN_FLIGHT_TIMEOUT_MS) return publish()
            // Recibo perdido: resultado INCERTO. Não autoriza novo envio: bloqueia, exige releitura completa do alvo e
            // desarma até novo toque (revisão 2026-10-07 #1); rearmar não apaga o bloqueio nem a releitura.
            inFlight = null
            cleaner.onFailed(now, mutationMayHaveStarted = true, fuels = setOf(flight.fuel))
            disarmedReason = TIMEOUT_DISARMED_REASON
            lastResult = JSONObject().put("ok", false).put("retryLater", false).put("error", TIMEOUT_DISARMED_REASON)
            try {
                record(
                    "autocal_auto_idle_timeout",
                    JSONObject()
                        .put("fuel", flight.fuel.wireName)
                        .put("bands", JSONArray(flight.bands))
                        .put("sinceElapsedMs", flight.sinceMs)
                        .put("nowElapsedMs", now)
                        .put("sessionId", sessionId)
                        .put("automatic", true)
                        .put("scope", "DELETE_OUTLIER_POINTS"),
                )
            } catch (_: Exception) {}
            if (armed) setArmedLocked(false, "timeout")
        }
        if (!armed) {
            waitReason = disarmedReason
            return publish()
        }
        val latest = telemetry.recentTelemetryFrames(now - AutoIdlePointCleaner.FRAME_MAX_AGE_MS, now).lastOrNull()
        val decision = cleaner.decide(
            AutoIdlePointCleaner.Input(
                nowElapsedMs = now,
                candidates = Fuel.entries.associateWith { outliers.candidates(it) },
                latestFrame = latest,
                autoCalEnabled = autoCalEnabled(),
                sessionAgeMs = sessionAgeMs(),
            ),
        )
        if (decision !is AutoIdlePointCleaner.Decision.Delete) {
            waitReason = (decision as AutoIdlePointCleaner.Decision.Wait).reason
            return publish()
        }
        canAct()?.let { reason ->
            waitReason = reason
            lastResult = JSONObject().put("ok", false).put("retryLater", true).put("error", reason)
            return publish()
        }
        waitReason = ""
        val regime = regimes.getValue(decision.fuel)
        val evidence = JSONObject()
            .put("reason", "fora_da_curva")
            .put("fuel", decision.fuel.wireName)
            .put("rule", "banda fora da curva ms x MAP (ajuste robusto do Refino), apagada com o carro rodando no combustível dela")
            .put("spec", "2026-10-07-autocal-apagar-lenta rev2")
            .put(
                "bands",
                JSONArray().also { array ->
                    decision.outliers.forEach { outlier ->
                        val json = outlier.toJson()
                        regime.lastAcquisition(outlier.band)?.let { acquisition ->
                            json.put("lastAcquisitionRegime", acquisition.regime.name)
                                .put("frames", acquisition.evidence.frames)
                                .put("idleFraction", acquisition.evidence.idleFraction)
                        }
                        array.put(json)
                    }
                },
            )
            .put(
                "trigger",
                JSONObject()
                    .put("rpm", decision.frame.rpm)
                    .put("fuel", decision.frame.fuel)
                    .put("mapBar", decision.frame.mapBar)
                    .put("frameElapsedMs", decision.frame.elapsedMs)
                    .put("decidedAtElapsedMs", now),
            )
            .put("minIntervalMs", AutoIdlePointCleaner.MIN_INTERVAL_MS)
        val targets = decision.bands.map { AutoCalPointDeleteProtocol.Target(decision.fuel, it) }
        outliers.onDeleteStarted(decision.fuel)
        val flight = InFlight(decision.fuel, decision.bands, now, sessionId)
        inFlight = flight
        val result = try {
            executeDelete(targets, evidence)
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("retryLater", true).put("error", error.message ?: "falha")
        }
        lastResult = JSONObject(result.toString())
        if (!result.optBoolean("ok", false)) {
            inFlight = null
            // Colisão (porta, guarda, preparação manual): tenta de novo; não consome o intervalo.
            cleaner.onRetryLater()
        } else {
            // Identidade da operação (o gerenciador a devolve ao enfileirar). O recibo chega pela mesma trava, logo
            // sempre depois desta atribuição. Sem identidade, nenhum recibo casa e o voo cai no timeout (incerto).
            flight.preparationId = result.optString("preparationId").ifBlank { null }
        }
        publish()
    }

    /**
     * O voo a que este recibo pertence, ou nulo quando o recibo não é dele: sem identidade (sessão/preparação),
     * de geração USB diferente da atual, sem voo aberto (ex.: já caiu no timeout) ou de outra preparação.
     * Recibo ignorado é registrado; nenhum estado muda.
     */
    private fun flightFor(receipt: JSONObject): InFlight? {
        val flight = inFlight
        val receiptSession = receipt.optLong("sessionId", -1L)
        val preparationId = receipt.optString("preparationId")
        val reason = when {
            receiptSession <= 0L || preparationId.isBlank() -> "recibo sem identidade de sessão/preparação"
            receiptSession != sessionId -> "recibo de sessão USB antiga"
            flight == null -> "nenhum apagamento automático em voo (recibo atrasado)"
            flight.sessionId != receiptSession || flight.preparationId != preparationId -> "recibo de outra operação"
            else -> null
        } ?: return flight
        try {
            record(
                "autocal_auto_idle_receipt_ignored",
                JSONObject()
                    .put("reason", reason)
                    .put("sessionId", sessionId)
                    .put("receiptSessionId", receiptSession)
                    .put("receiptId", receipt.optString("id"))
                    .put("preparationId", preparationId)
                    .put("inFlightPreparationId", flight?.preparationId ?: JSONObject.NULL)
                    .put("outcome", receipt.optString("outcome"))
                    .put("automatic", true)
                    .put("scope", "DELETE_OUTLIER_POINTS"),
            )
        } catch (_: Exception) {}
        return null
    }

    private fun resetSessionLocked(newSessionId: Long) {
        if (armed) setArmedLocked(false, "session")
        sessionId = newSessionId
        outliers.reset()
        regimes.values.forEach { it.reset() }
        cleaner.resetSession()
        inFlight = null
        disarmedReason = DISARMED_REASON
        lastResult = JSONObject()
        pauseCode = null
        relearned = 0
        recent.clear()
        publish()
    }

    private fun disableLocked(reason: String, receipt: JSONObject, code: PauseCode) {
        if (cleaner.disabledReason() == null) pauseCode = code
        cleaner.disable(reason)
        recordDisabled(reason, receipt)
        // Pausa exige novo toque do dono.
        if (armed) setArmedLocked(false, "pause:${code.name}")
    }

    private fun setArmedLocked(armed: Boolean, source: String) {
        if (this.armed == armed && source != "dono") return
        this.armed = armed
        try {
            record(
                "autocal_auto_cleanup_armed",
                JSONObject()
                    .put("armed", armed)
                    .put("source", source)
                    .put("sessionId", sessionId)
                    .put("automatic", true)
                    .put("scope", "DELETE_OUTLIER_POINTS"),
            )
        } catch (_: Exception) {}
    }

    private fun recordDisabled(reason: String, receipt: JSONObject) {
        try {
            record(
                "autocal_auto_idle_disabled",
                JSONObject()
                    .put("reason", reason)
                    .put("sessionId", sessionId)
                    .put("receiptId", receipt.optString("id"))
                    .put("receipt", JSONObject(receipt.toString()))
                    .put("automatic", true)
                    .put("scope", "DELETE_OUTLIER_POINTS"),
            )
        } catch (_: Exception) {}
    }

    private fun intList(array: JSONArray?): List<Int> =
        if (array == null) emptyList() else List(array.length()) { array.optInt(it, -1) }.filter { it >= 0 }

    private fun bandsWithResult(details: JSONObject, result: String): List<Int> {
        val rows = details.optJSONArray("effect") ?: return emptyList()
        return List(rows.length()) { rows.optJSONObject(it) }
            .filter { it?.optString("result") == result }
            .mapNotNull { it?.optInt("index", -1)?.takeIf { index -> index >= 0 } }
    }

    private fun targetBands(details: JSONObject): List<Int> {
        details.optJSONArray("targets")?.let { array ->
            return List(array.length()) { array.optJSONObject(it)?.optInt("index", -1) ?: -1 }.filter { it >= 0 }
        }
        return listOfNotNull(details.optInt("index", -1).takeIf { it >= 0 })
    }

    private fun publish() {
        enabledNow = cleaner.disabledReason() == null
        val waitForUi = if (!armed) disarmedReason else waitReason
        lastJson = JSONObject()
            .put("sessionId", sessionId)
            .put("automatic", true)
            .put("scope", "DELETE_OUTLIER_POINTS")
            .put("fuels", JSONArray().put("GAS").put("PETROL"))
            .put("armed", armed)
            .put("waitReason", waitForUi)
            .put("inFlight", inFlight != null)
            .put("inFlightFuel", inFlight?.fuel?.wireName ?: JSONObject.NULL)
            .put("outliers", outliers.json())
            .put("policy", cleaner.json())
            .put("lastResult", lastResult)
        summary = UiSummary(
            active = sessionId > 0L,
            armed = armed,
            enabled = enabledNow,
            pauseCode = if (enabledNow) null else pauseCode,
            relearnedThisSession = relearned,
            recentDeletes = recent.toList(),
            waitReason = waitForUi,
        )
        // Revisão só quando algo visível mudou: o tick de 500 ms com o mesmo estado não publica nada.
        val key = lastJson.toString()
        if (key != publishedKey) {
            publishedKey = key
            try { onChanged() } catch (_: Exception) {}
        }
    }

    private fun submit(block: () -> Unit) {
        try {
            executor.execute {
                try {
                    synchronized(this) {
                        try {
                            block()
                        } catch (error: Exception) {
                            // Regra 5: falha vira estado legível com próxima ação, nunca silêncio.
                            lastResult = JSONObject().put("ok", false).put("retryLater", true)
                                .put("error", "Apagamento automático: ${error.message ?: "falha inesperada"}; vai tentar de novo na próxima leitura")
                            try { publish() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Serviço encerrando: nada a decidir.
        }
    }

    companion object {
        /** Se o recibo nunca chegar (falha inesperada), libera a decisão depois deste prazo. */
        const val IN_FLIGHT_TIMEOUT_MS = 30_000L
        /** A tela só precisa dos últimos apagamentos para acinzentar os pontos até a próxima leitura. */
        const val MAX_RECENT = 5
        val PETROL_NAMES = setOf("GASOLINA", "PETROL")
        const val DISARMED_REASON = "Limpeza automática desligada: toque em Ativar limpeza automática"
        const val ARMED_WAIT_REASON = "Aguardando a próxima leitura da ECU"
        const val FAILURE_DISARMED_REASON =
            "Limpeza automática desligada depois de uma falha no apagamento: toque em Ativar limpeza automática para continuar"
        const val FAILURE_UNCERTAIN_DISARMED_REASON =
            "Limpeza automática desligada depois de uma falha com resultado incerto; aguarde a leitura nova da ECU e toque em Ativar limpeza automática"
        const val TIMEOUT_DISARMED_REASON =
            "A ECU não respondeu ao apagamento automático; limpeza desligada até a leitura nova da ECU e um novo toque em Ativar limpeza automática"
    }
}
