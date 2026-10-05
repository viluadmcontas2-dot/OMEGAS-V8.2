package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.FailureKind
import com.omegas.prohub.calibration.KFactorManualPlanner
import com.omegas.prohub.calibration.LiveCellProjection
import com.omegas.prohub.runtime.RuntimeSnapshotBus
import com.omegas.prohub.storage.AppPaths
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Ponte exclusiva da interface nativa. Não injeta HTML nem expõe código substituível. */
class HubJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()
    private val appContext: android.content.Context = activityRef.get()!!.applicationContext
    private val mapReadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-web-k-map-read").apply { isDaemon = true }
    }
    private val mapReadBusy = AtomicBoolean(false)
    private val uiSnapshots = RuntimeSnapshotBus()
    @Volatile private var interpolationCache: Pair<Long, JSONObject>? = null

    fun destroy() {
        mapReadExecutor.shutdownNow()
    }

    @Volatile private var mapReadResult = JSONObject()
        .put("ok", true)
        .put("state", "IDLE")
        .put("busy", false)

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço indisponível")
        .toString()

    private fun releaseIdentity(): JSONObject = JSONObject()
        .put("product", BuildConfig.OMEGAS_PRODUCT)
        .put("generation", BuildConfig.OMEGAS_GENERATION)
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("channel", BuildConfig.OMEGAS_CHANNEL)
        .put("applicationId", BuildConfig.APPLICATION_ID)
        .put("engine", BuildConfig.OMEGAS_ENGINE)
        .put("telemetrySchema", BuildConfig.OMEGAS_TELEMETRY_SCHEMA)
        .put("learningSchema", BuildConfig.OMEGAS_LEARNING_SCHEMA)
        .put("mapSchema", BuildConfig.OMEGAS_MAP_SCHEMA)
        .put("kFactorSchema", BuildConfig.OMEGAS_K_FACTOR_SCHEMA)
        .put("kFactorState", BuildConfig.OMEGAS_K_FACTOR_STATE)
        .put("safetyMode", BuildConfig.OMEGAS_SAFETY_MODE)
        .put("automaticCalibration", BuildConfig.OMEGAS_AUTOMATIC_CALIBRATION)
        .put("commit", BuildConfig.OMEGAS_BUILD_COMMIT)
        .put("debug", BuildConfig.DEBUG)

    @JavascriptInterface
    fun getReleaseIdentity(): String = releaseIdentity().toString()

    /**
     * PresentSnapshot = cópia latest-only do TelemetryStateStore. Não lê arquivo,
     * não serializa histórico e não toca serial/learning/writer.
     */
    @JavascriptInterface
    fun getPresentSnapshot(): String = activity?.serviceOrNull()?.let { service ->
        val root = try { JSONObject(service.telemetryStore.liveJson()) } catch (_: Exception) { JSONObject() }
        val live = root.optJSONObject("live") ?: JSONObject()
        // A interpolação só muda quando o quadro muda: guardada por sequência (o tick da UI é mais
        // rápido que a telemetria).
        val sequence = root.optLong("sequence", 0L)
        val cached = interpolationCache
        val interpolation = if (cached != null && cached.first == sequence) {
            cached.second
        } else {
            LiveCellProjection.liveInterpolationJson(
                rpm = live.optDouble("rpm", 0.0),
                petrolMs = live.optDouble("petrol_ms", live.optDouble("petrolMs", 0.0)),
                mapBar = live.optDouble("load_bar", live.optDouble("map_bar", 0.0)),
                sequence = sequence,
                updatedAt = root.optLong("updatedAt", 0L),
                telemetryValid = root.optBoolean("valid", false),
            ).also { interpolationCache = sequence to it }
        }
        root.put("ok", true)
            .put("telemetryAgeMs", root.optLong("ageMs", -1L))
            .put("interpolation", interpolation)
            // Lote D: revisão por tipo (live/evidence/tables/session); só sobe quando o dado muda. Chave nova, aditiva.
            .put("revisions", service.revisionsObject().getJSONObject("revisions"))
        uiSnapshots.publishPresent(root)
        uiSnapshots.presentJson().toString()
    } ?: unavailable()

    /**
     * Igual a [getPresentSnapshot], mas a UI informa a última sequência que já pintou. Se nada mudou,
     * devolve só `{changed:false}` com a idade (sem montar nem serializar o quadro).
     * Lote D (chave aditiva): ambas as formas trazem `revisions:{live,evidence,tables,session}`; a UI relê
     * `getNativeMonitorSnapshot`/sessões só quando a revisão do tipo mudou.
     */
    @JavascriptInterface
    fun getPresentSnapshotIfChanged(lastSequence: Long): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        val sequence = service.telemetryStore.sequenceNow()
        if (lastSequence >= 0L && sequence == lastSequence) {
            val age = service.telemetryStore.ageMs().let { if (it == Long.MAX_VALUE) -1L else it }
            val revisions = service.revisionsObject().getJSONObject("revisions")
            return "{\"ok\":true,\"changed\":false,\"sequence\":$sequence,\"telemetryAgeMs\":$age,\"revisions\":$revisions}"
        }
        return getPresentSnapshot()
    }

    @JavascriptInterface
    fun getStatus(): String = activity?.serviceOrNull()?.let { service ->
        val status = service.status()
        val kStatus = try { JSONObject(service.kWriteStatusJson()) } catch (_: Exception) { JSONObject() }
        val kDetails = kStatus.optJSONObject("details") ?: JSONObject()
        val factorStatus = try { JSONObject(service.kFactorStatusJson()) } catch (_: Exception) { JSONObject() }
        JSONObject()
            .put("serviceRunning", status.serviceRunning)
            .put("engineRunning", status.engineRunning)
            .put("engineReady", status.engineReady)
            .put("engineStuck", status.engineStuck)
            .put("ecuLinkState", status.ecuLinkState)
            .put("ecuLinkMessage", status.ecuLinkMessage)
            .put("usbRecovering", status.usbRecovering)
            .put("enginePausedByUser", status.enginePausedByUser)
            .put("engineVersion", status.engineVersion)
            .put("usbConnected", status.usbConnected)
            .put("usbDevice", status.usbDevice)
            .put("usbPermissionPending", status.usbPermissionPending)
            .put("usbPermissionDenied", status.usbPermissionDenied)
            .put("baudRate", status.baudRate)
            .put("serialFormat", status.serialFormat)
            .put("ecuState", status.ecuState)
            .put("fuelState", status.fuelState)
            .put("rpm", status.rpm)
            .put("petrolMs", status.petrolMs)
            .put("gasMs", status.gasMs)
            .put("mapBar", status.mapBar)
            .put("gasPressureBar", status.gasPressureBar)
            .put("lastError", status.lastError)
            .put("uptimeSeconds", status.uptimeSeconds)
            .put("gpsEnabled", status.gpsEnabled)
            .put("lanEnabled", status.lanEnabled)
            .put("lanAddress", status.lanAddress)
            .put("directTelemetryAgeMs", status.directTelemetryAgeMs)
            .put("calibrationBusy", service.kWriter.isBusy() || service.kFactor.isBusy())
            .put("kMapState", kStatus.optString("state", "IDLE"))
            .put("kMapMessage", kStatus.optString("message", ""))
            .put("kMapProgress", kStatus.optInt("progress", 0))
            .put("kMapUpdatedAt", kDetails.optLong("updatedAt", 0L))
            .put("kMapHash", kDetails.optString("hash", ""))
            .put("kMapCells", kDetails.optInt("cells", 0))
            .put("kFactorState", factorStatus.optString("state", "IDLE"))
            .put("kFactorMessage", factorStatus.optString("message", ""))
            .put("kFactorProgress", factorStatus.optInt("progress", 0))
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("release", releaseIdentity())
            .toString()
    } ?: JSONObject()
        .put("serviceRunning", false)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("release", releaseIdentity())
        .toString()

    @JavascriptInterface fun connectUsb(deviceName: String): Boolean =
        activity?.serviceOrNull()?.connectUsb(deviceName.ifBlank { null }, userInitiated = true) ?: false
    @JavascriptInterface fun disconnectUsb() = activity?.serviceOrNull()?.disconnectUsb()

    @JavascriptInterface fun getFullEngineSnapshot(): String = activity?.serviceOrNull()?.fullEngineSnapshotJson() ?: "{}"

    @JavascriptInterface
    fun getLiveTelemetry(): String = activity?.serviceOrNull()?.let { service ->
        val root = JSONObject(service.telemetryStore.liveJson())
        val live = root.optJSONObject("live") ?: JSONObject()
        val interpolation = LiveCellProjection.liveInterpolationJson(
            rpm = live.optDouble("rpm", 0.0),
            petrolMs = live.optDouble("petrol_ms", live.optDouble("petrolMs", 0.0)),
            mapBar = live.optDouble("load_bar", live.optDouble("map_bar", 0.0)),
            sequence = root.optLong("sequence", 0L),
            updatedAt = root.optLong("updatedAt", 0L),
            telemetryValid = root.optBoolean("valid", false),
        )
        root.put("ok", true)
            .put("telemetryAgeMs", root.optLong("ageMs", -1L))
            .put("interpolation", interpolation)
            .toString()
    } ?: unavailable()

    @JavascriptInterface fun runEngineSelfTests(): String = activity?.serviceOrNull()?.engineSelfTestJson() ?: unavailable()

    /**
     * Inicia a leitura completa fora da thread JavaScript. A WebView continua
     * renderizando a telemetria enquanto as 13 linhas disputam a porta de forma
     * justa com o ciclo nativo da ECU.
     */
    @JavascriptInterface
    fun startKMapRead(): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        if (!mapReadBusy.compareAndSet(false, true)) {
            return JSONObject()
                .put("ok", false)
                .put("busy", true)
                .put("error", "A leitura do mapa K já está em andamento")
                .toString()
        }
        val startedAt = System.currentTimeMillis()
        mapReadResult = JSONObject()
            .put("ok", true)
            .put("state", "READING")
            .put("busy", true)
            .put("startedAt", startedAt)
        mapReadExecutor.execute {
            val result = try {
                JSONObject(service.readKMap())
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("failureKind", FailureKind.of(error))
                    .put("error", error.message ?: "Falha ao ler mapa K")
            }
            mapReadResult = JSONObject(result.toString())
                .put("state", if (result.optBoolean("ok")) "COMPLETED" else "FAILED")
                .put("busy", false)
                .put("startedAt", startedAt)
                .put("finishedAt", System.currentTimeMillis())
            mapReadBusy.set(false)
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("state", "READING")
            .put("startedAt", startedAt)
            .toString()
    }

    @JavascriptInterface
    fun getKMapReadResult(): String = JSONObject(mapReadResult.toString())
        .put("busy", mapReadBusy.get())
        .toString()

    @JavascriptInterface fun previewKFactorPoint(index: Int, targetFactor: Double): String =
        KFactorManualPlanner.preview(AppPaths(appContext).runtimeRoot, index, targetFactor).toString()

    @JavascriptInterface fun getSessionRecorderStatus(): String = activity?.serviceOrNull()?.sessionRecorderStatusJson() ?: "{}"
    @JavascriptInterface fun listRecordedSessions(): String = activity?.serviceOrNull()?.sessionRecorderListJson() ?: "[]"
    @JavascriptInterface fun setSessionRecorderSettings(
        telemetryEveryMs: Long,
        maxSessionMb: Int,
        keepSessions: Int,
        autoStartOnUsb: Boolean,
        captureRawUsb: Boolean,
    ): String = activity?.serviceOrNull()?.updateSessionRecorderSettings(
        telemetryEveryMs,
        maxSessionMb,
        keepSessions,
        autoStartOnUsb,
        captureRawUsb,
    ) ?: unavailable()
    @JavascriptInterface fun startSessionRecording(reason: String): String = activity?.serviceOrNull()?.startSessionRecording(reason) ?: unavailable()
    @JavascriptInterface fun stopSessionRecording(reason: String): String = activity?.serviceOrNull()?.stopSessionRecording(reason) ?: unavailable()
    @JavascriptInterface fun exportSession(sessionId: String) = activity?.exportSession(sessionId)
    @JavascriptInterface fun getLogs(): String = activity?.serviceOrNull()?.logsJson() ?: "[]"
    @JavascriptInterface fun exportLogs() = activity?.exportLogs()
    @JavascriptInterface fun exportData() = activity?.exportData()
}
