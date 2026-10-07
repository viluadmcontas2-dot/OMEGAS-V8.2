package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor

/**
 * Apagamento automático de pontos do GNV aprendidos na marcha lenta (spec 2026-10-07-autocal-apagar-lenta).
 *
 * Liga o [IdleAcquisitionTracker] (detecção), o [AutoIdlePointCleaner] (política) e o
 * [AutoCalNativeActionManager.executeAutomaticPointDelete] (execução). Mora no serviço: funciona com a
 * tela fechada. Toda decisão roda no [executor] próprio, nunca na thread do autoCalTick — o monitor só
 * entrega as leituras confirmadas dos buffers GNV ([onGasBuffers]) e volta.
 *
 * Exceção à regra "nada muda sozinho": só GNV, com readback e registro. Gasolina e Curva K continuam só com o dono.
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
) {
    /** Leitura confirmada dos três buffers GNV (grupo G4 ou snapshot completo), no relógio elapsed. */
    class GasBuffers(
        val sessionId: Long,
        val counters: IntArray,
        val timeRaw: IntArray?,
        val mapRaw: IntArray?,
        val observedAtElapsedMs: Long,
    )

    /** Por que o automático pausou nesta sessão; a tela traduz em português simples. */
    enum class PauseCode { PETROL_GUARD, REPEATED_FAILURES, READBACK_INEFFECTIVE }

    /** Apagamento automático confirmado pela ECU (só as bandas com readback que provou o apagamento). */
    class RecentDelete(val receiptId: String, val indexes: List<Int>, val atMs: Long)

    /** O que a tela do AutoCal mostra: ligado/pausado, motivo e quantos pontos o app pediu para reaprender. */
    class UiSummary(
        val active: Boolean,
        val enabled: Boolean,
        val pauseCode: PauseCode?,
        val relearnedThisSession: Int,
        val recentDeletes: List<RecentDelete>,
    )

    private val tracker = IdleAcquisitionTracker()
    private val cleaner = AutoIdlePointCleaner()
    private var sessionId = 0L
    private var inFlightSinceMs: Long? = null
    private var lastResult: JSONObject = JSONObject()
    @Volatile private var lastJson: JSONObject = JSONObject()
    @Volatile private var enabledNow = true
    private var pauseCode: PauseCode? = null
    private var relearned = 0
    private val recent = ArrayDeque<RecentDelete>()
    @Volatile private var summary = UiSummary(false, true, null, 0, emptyList())

    fun onGasBuffers(buffers: GasBuffers) = submit {
        if (buffers.sessionId != sessionId) resetSessionLocked(buffers.sessionId)
        tracker.observe(
            IdleAcquisitionTracker.Reading(buffers.counters, buffers.timeRaw, buffers.mapRaw, buffers.observedAtElapsedMs),
        ) { from, to -> telemetry.recentTelemetryFrames(from, to) }
        cleaner.onReread()
        evaluateLocked()
    }

    /** Chamado periodicamente: o carro pode começar a andar sem leitura nova dos buffers. */
    fun evaluate() = submit { evaluateLocked() }

    fun onSessionChanged(newSessionId: Long) = submit { resetSessionLocked(newSessionId) }

    /** invalidateRound / ação manual confirmada: leituras anteriores não valem; intervalo e desligamento seguem. */
    fun onRoundInvalidated() = submit {
        tracker.reset()
        cleaner.reset()
        publish()
    }

    fun onActionConfirmed(receipt: JSONObject) = submit {
        if (!receipt.optBoolean("automatic", false) || receipt.optString("action") != "DELETE_POINT") return@submit
        inFlightSinceMs = null
        val details = receipt.optJSONObject("details") ?: JSONObject()
        val ambiguousBands = bandsWithResult(details, "AMBIGUOUS")
        // Readback ambíguo (revisão #1): consome o intervalo e espera releitura; nunca desliga a sessão.
        cleaner.onSucceeded(clock(), rereadRequired = details.optBoolean("readbackAmbiguous", false))
        val deleted = targetBands(details) - ambiguousBands.toSet()
        tracker.markDeleted(deleted)
        tracker.forget(ambiguousBands + intList(details.optJSONArray("skippedChanged")))
        if (deleted.isNotEmpty()) {
            relearned += deleted.size
            recent.addLast(RecentDelete(receipt.optString("id"), deleted.sorted(), receipt.optLong("finishedAtMs", 0L)))
            while (recent.size > MAX_RECENT) recent.removeFirst()
        }
        val petrol = details.optJSONObject("petrolGuard")
        if (petrol?.optBoolean("abnormal", false) == true) {
            disableLocked("Gasolina mudou de forma anormal durante o apagamento automático", receipt, PauseCode.PETROL_GUARD)
        }
        publish()
    }

    fun onActionFailed(receipt: JSONObject) = submit {
        if (!receipt.optBoolean("automatic", false) || receipt.optString("action") != "DELETE_POINT") return@submit
        inFlightSinceMs = null
        receipt.optJSONArray("emptyBands")?.let { array -> tracker.markDeleted(intList(array)) }
        // A banda readquiriu andando entre a marca e o disparo (revisão #3): a marca sai.
        tracker.forget(intList(receipt.optJSONArray("skippedChanged")))
        if (receipt.has("effective") && !receipt.optBoolean("effective", true)) {
            disableLocked("Readback mostrou que a ECU não apagou o ponto", receipt, PauseCode.READBACK_INEFFECTIVE)
        } else {
            // Toda falha consome tempo (revisão #2); N seguidas desligam a sessão.
            val wasEnabled = cleaner.disabledReason() == null
            cleaner.onFailed(clock(), receipt.optBoolean("mutationMayHaveStarted", false))
            val reason = cleaner.disabledReason()
            if (wasEnabled && reason != null) {
                pauseCode = PauseCode.REPEATED_FAILURES
                recordDisabled(reason, receipt)
            }
        }
        publish()
    }

    private fun intList(array: JSONArray?): List<Int> =
        if (array == null) emptyList() else List(array.length()) { array.optInt(it, -1) }.filter { it >= 0 }

    private fun bandsWithResult(details: JSONObject, result: String): List<Int> {
        val rows = details.optJSONArray("effect") ?: return emptyList()
        return List(rows.length()) { rows.optJSONObject(it) }
            .filter { it?.optString("result") == result }
            .mapNotNull { it?.optInt("index", -1)?.takeIf { index -> index >= 0 } }
    }

    fun json(): JSONObject = JSONObject(lastJson.toString())

    /** Resumo para a tela (leitura sem trava: publicado a cada mudança). */
    fun uiSummary(): UiSummary = summary

    /** O apagamento automático está ligado nesta sessão (não foi desligado por readback ineficaz/gasolina). */
    fun automaticEnabled(): Boolean = enabledNow

    private fun evaluateLocked() {
        val now = clock()
        inFlightSinceMs?.let { since ->
            if (now - since < IN_FLIGHT_TIMEOUT_MS) return publish()
            inFlightSinceMs = null // callback perdido: não trava o automático para sempre
        }
        val latest = telemetry.recentTelemetryFrames(now - AutoIdlePointCleaner.FRAME_MAX_AGE_MS, now).lastOrNull()
        val decision = cleaner.decide(
            AutoIdlePointCleaner.Input(
                nowElapsedMs = now,
                idleBands = tracker.idleBands(),
                lastCounters = tracker.lastCounters(),
                latestFrame = latest,
                autoCalEnabled = autoCalEnabled(),
                sessionAgeMs = sessionAgeMs(),
            ),
        )
        if (decision !is AutoIdlePointCleaner.Decision.Delete) return publish()
        canAct()?.let { reason ->
            lastResult = JSONObject().put("ok", false).put("retryLater", true).put("error", reason)
            return publish()
        }
        val evidence = JSONObject()
            .put("reason", "lenta")
            .put("rule", "banda GNV cuja última aquisição foi na lenta, apagada com o carro andando")
            .put("spec", "2026-10-07-autocal-apagar-lenta")
            .put("bands", JSONArray().also { array -> decision.evidence.forEach { array.put(it.toJson()) } })
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
        val targets = decision.bands.map { AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, it) }
        inFlightSinceMs = now
        val result = try {
            executeDelete(targets, evidence)
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("retryLater", true).put("error", error.message ?: "falha")
        }
        lastResult = JSONObject(result.toString())
        if (!result.optBoolean("ok", false)) {
            inFlightSinceMs = null
            // Colisão (porta, guarda, preparação manual): tenta de novo; não consome o intervalo.
            cleaner.onRetryLater()
        }
        publish()
    }

    private fun resetSessionLocked(newSessionId: Long) {
        sessionId = newSessionId
        tracker.reset()
        cleaner.resetSession()
        inFlightSinceMs = null
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
                    .put("scope", "GNV"),
            )
        } catch (_: Exception) {}
    }

    private fun targetBands(details: JSONObject): List<Int> {
        details.optJSONArray("targets")?.let { array ->
            return List(array.length()) { array.optJSONObject(it)?.optInt("index", -1) ?: -1 }.filter { it >= 0 }
        }
        return listOfNotNull(details.optInt("index", -1).takeIf { it >= 0 })
    }

    private fun publish() {
        enabledNow = cleaner.disabledReason() == null
        lastJson = JSONObject()
            .put("sessionId", sessionId)
            .put("automatic", true)
            .put("scope", "GNV")
            .put("inFlight", inFlightSinceMs != null)
            .put("tracker", tracker.json())
            .put("policy", cleaner.json())
            .put("lastResult", lastResult)
        summary = UiSummary(
            active = sessionId > 0L,
            enabled = enabledNow,
            pauseCode = if (enabledNow) null else pauseCode,
            relearnedThisSession = relearned,
            recentDeletes = recent.toList(),
        )
    }

    private fun submit(block: () -> Unit) {
        try {
            executor.execute {
                try { synchronized(this) { block() } } catch (_: Exception) {}
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
    }
}
