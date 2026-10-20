package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.BuildConfig
import com.omegas.prohub.MainActivity
import com.omegas.prohub.calibration.CalibrationWriteSafetyPolicy
import com.omegas.prohub.calibration.KFactorManualPlanner
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.learning.LearningGridProjection
import com.omegas.prohub.learning.LearningTelemetrySchemaMigration
import com.omegas.prohub.learning.LearningTemperatureSettings
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.learning.LearningUiSnapshotAssembler
import com.omegas.prohub.learning.SignalLearningStore
import com.omegas.prohub.runtime.RuntimeSnapshotBus
import com.omegas.prohub.service.TelemetryForegroundService
import com.omegas.prohub.service.v7CalibrationStateJson
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Ponte exclusiva da interface nativa. Não injeta HTML nem expõe código substituível. */
class HubJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()
    private val appContext: android.content.Context = activityRef.get()!!.applicationContext
    private val learningTemperature = LearningTemperatureSettings(appContext)
    private val learningTolerances = LearningToleranceSettings(appContext)
    private val mapReadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-web-k-map-read").apply { isDaemon = true }
    }
    private val mapReadBusy = AtomicBoolean(false)
    private val scienceRefreshBusy = AtomicBoolean(false)
    private val uiSnapshots = RuntimeSnapshotBus()
    private val presentGate = PresentSnapshotGate()
    @Volatile private var publishedScienceSignature = ""

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

    private fun safetyBlocked(reason: String): String = JSONObject()
        .put("ok", false)
        .put("safetyBlocked", true)
        .put("error", reason)
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
        presentEnvelope(service).toString()
    } ?: unavailable()

    /**
     * Igual a [getPresentSnapshot], mas a UI informa a sequência do último quadro que recebeu. Sem quadro novo
     * (nem runtime/GPS/sessão), devolve só `{ok, changed:false, revision, sequence, telemetryAgeMs}`, sem copiar
     * nem serializar o estado; senão, o quadro completo com `changed:true`.
     */
    @JavascriptInterface
    fun getPresentSnapshotIfChanged(lastSequence: Long): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        val store = try { service.telemetryStore } catch (_: Exception) { return unavailable() }
        return presentGate.respond(store, lastSequence) { presentEnvelope(service) }
    }

    /** Quadro completo `{ok, revision, data}`: cópia latest-only do store + interpolação da célula atual. */
    private fun presentEnvelope(service: TelemetryForegroundService): JSONObject {
        val root = try { service.telemetryStore.liveObject() } catch (_: Exception) { JSONObject() }
        val live = root.optJSONObject("live") ?: JSONObject()
        val interpolation = presentGate.interpolation(
            rpm = live.optDouble("rpm", 0.0),
            petrolMs = live.optDouble("petrol_ms", live.optDouble("petrolMs", 0.0)),
            mapBar = live.optDouble("load_bar", live.optDouble("map_bar", 0.0)),
            valid = root.optBoolean("valid", false),
            sequence = root.optLong("sequence", 0L),
            updatedAt = root.optLong("updatedAt", 0L),
        )
        root.put("ok", true)
            .put("telemetryAgeMs", root.optLong("ageMs", -1L))
            .put("interpolation", interpolation)
        // Mesmo envelope que presentJson() devolveria, sem reler o que acabou de ser publicado.
        val revision = uiSnapshots.publishPresent(root)
        return JSONObject().put("ok", true).put("revision", revision).put("data", root)
    }

    /**
     * ScienceSnapshot nunca reconstrói ciência na thread da WebView. A chamada
     * compara somente metadados de arquivos e, se necessário, agenda um prewarm
     * no executor que já pertence à bridge. Até o prewarm terminar, devolve o
     * último snapshot válido em RAM.
     */
    @JavascriptInterface
    fun getScienceSnapshotSince(lastRevision: Long): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        val signature = scienceSignature(service)
        if (signature != publishedScienceSignature && scienceRefreshBusy.compareAndSet(false, true)) {
            mapReadExecutor.execute {
                try {
                    val learning = try { JSONObject(getLearningMaps()) } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Learning indisponível")
                    }
                    val calibration = try { JSONObject(service.v7CalibrationStateJson()) } catch (error: Exception) {
                        JSONObject().put("ok", false).put("error", error.message ?: "Estado V7 indisponível")
                    }
                    val science = JSONObject()
                        .put("learning", learning)
                        .put("calibrationState", calibration)
                        .put("predictor", calibration.optJSONObject("predictor") ?: JSONObject())
                        .put("generatedAt", System.currentTimeMillis())
                        .put("signature", signature)
                    uiSnapshots.publishScience(science, signature)
                    publishedScienceSignature = signature
                } catch (_: Exception) {
                    // Mantém a última ciência válida; a próxima chamada tentará novamente.
                } finally {
                    scienceRefreshBusy.set(false)
                }
            }
        }
        return uiSnapshots.scienceJsonSince(lastRevision)
            .put("refreshing", scienceRefreshBusy.get())
            .put("pendingSignature", signature != publishedScienceSignature)
            .toString()
    }

    private fun scienceSignature(service: TelemetryForegroundService): String {
        val root = service.paths.runtimeRoot
        val files = listOf(
            File(root, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE),
            File(root, "learning_v6_evidence.json"),
            File(root, "k_map_cache.json"),
            File(root, "k_factor_cache.json"),
            File(root, "v7_sessions"),
        )
        return files.joinToString("|") { file ->
            if (file.exists()) {
                "${file.name}:${file.lastModified()}:${if (file.isFile) file.length() else 0L}"
            } else {
                "${file.name}:missing"
            }
        }
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
            .put("engineVersion", status.engineVersion)
            .put("usbConnected", status.usbConnected)
            .put("usbDevice", status.usbDevice)
            .put("usbPermissionPending", status.usbPermissionPending)
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
            .put("learningMinimumWaterC", learningTemperature.minimumWaterC())
            .put("learningTolerancePolicy", LearningToleranceSettings.current.toJson())
            .put("learningScaleMigration", LearningTelemetrySchemaMigration.status(service.paths.runtimeRoot))
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("release", releaseIdentity())
            .toString()
    } ?: JSONObject()
        .put("serviceRunning", false)
        .put("learningMinimumWaterC", learningTemperature.minimumWaterC())
        .put("learningTolerancePolicy", LearningToleranceSettings.current.toJson())
        .put("learningScaleMigration", LearningTelemetrySchemaMigration.status(AppPaths(appContext).runtimeRoot))
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("release", releaseIdentity())
        .toString()

    @JavascriptInterface
    fun getLearningCheckpointStatus(): String = activity?.serviceOrNull()?.learningCheckpointStatusJson() ?: "{}"

    @JavascriptInterface
    fun getLearningMaps(): String {
        val status = activity?.serviceOrNull()?.status()
        val file = File(AppPaths(appContext).runtimeRoot, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
        val root = try {
            val raw = if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
            LearningUiSnapshotAssembler.assemble(raw)
        } catch (error: Exception) {
            return JSONObject()
                .put("ok", false)
                .put("error", "Não foi possível reconciliar a memória: ${error.message}")
                .toString()
        }
        val source = root.optJSONArray("regions") ?: JSONArray()
        val petrol = JSONArray()
        val cngCurrent = JSONArray()
        val cngPrevious = JSONArray()
        val epoch = root.optInt("epoch", 1)
        repeat(source.length()) { index ->
            val region = LearningGridProjection.enrichRegion(source.optJSONObject(index) ?: return@repeat)
            when (region.optString("fuel").uppercase()) {
                "PETROL", "GASOLINA" -> petrol.put(region)
                "CNG", "GNV" -> if (region.optInt("epoch", epoch) == epoch) {
                    cngCurrent.put(region)
                } else {
                    cngPrevious.put(region)
                }
            }
        }
        val projectedCells = root.optJSONArray("cells") ?: LearningGridProjection.project(source, epoch)
        val comparisons = root.optJSONArray("comparisons") ?: JSONArray()
        val integrity = root.optJSONObject("integrity")
            ?: LearningGridProjection.integrity(
                regions = source,
                cells = projectedCells,
                comparisons = comparisons,
                epoch = epoch,
                mapHash = root.optString("mapHash", root.optString("map_hash", "")),
            )
        val currentCell = LearningGridProjection.cellFor(
            rpm = (status?.rpm ?: 0).toDouble(),
            petrolMs = status?.petrolMs ?: 0.0,
        )
        return JSONObject()
            .put("ok", true)
            .put("source", LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            .put("format", SignalLearningStore.FORMAT)
            .put("internalFormat", root.optString("format", ""))
            .put("telemetryScaleSchema", BuildConfig.OMEGAS_TELEMETRY_SCHEMA)
            .put("scaleMigration", LearningTelemetrySchemaMigration.status(AppPaths(appContext).runtimeRoot))
            .put("epoch", epoch)
            .put("grid", root.optJSONObject("grid") ?: LearningGridProjection.gridJson())
            .put("cells", projectedCells)
            .put("integrity", integrity)
            .put("mapHash", root.optString("mapHash", root.optString("map_hash", "")))
            .put("petrol", petrol)
            .put("cng", cngCurrent)
            .put("cngPreviousEpochs", cngPrevious)
            .put("comparisons", comparisons)
            .put("comparisonCount", comparisons.length())
            .put("assistedCalibration", root.optJSONObject("assistedCalibration") ?: JSONObject())
            .put("assisted_calibration", root.optJSONObject("assisted_calibration") ?: JSONObject())
            .put("reconciliation", root.optJSONObject("reconciliation") ?: JSONObject())
            .put("summary", root.optJSONObject("summary") ?: JSONObject())
            .put("uiPipeline", root.optString("uiPipeline", "PERSISTED_REGIONS_RECONCILED_ADVISOR"))
            .put("revalidation", root.optJSONObject("revalidation") ?: JSONObject())
            .put("current", JSONObject()
                .put("fuel", status?.fuelState ?: "--")
                .put("rpm", status?.rpm ?: 0)
                .put("petrolMs", status?.petrolMs ?: 0.0)
                .put("mapBar", status?.mapBar ?: 0.0)
                .put("cell", currentCell))
            .toString()
    }

    @JavascriptInterface
    fun getLearningTemperatureSettings(): String = learningTemperature.toJson().toString()

    @JavascriptInterface
    fun setLearningMinimumWaterC(value: Int): String {
        val applied = learningTemperature.setMinimumWaterC(value)
        activity?.serviceOrNull()?.let { service ->
            service.log.add("INFO", "LEARNING-NATIVE", "Temperatura mínima Landi ajustada para $applied °C")
            service.sessionRecorder.record(
                "settings_changed",
                "learning",
                JSONObject().put("minimumLandiWaterC", applied).put("source", "LANDI_ECU"),
                force = true,
            )
        }
        return learningTemperature.toJson().put("applied", applied).toString()
    }

    @JavascriptInterface
    fun getLearningToleranceSettings(): String = learningTolerances.toJson().toString()

    /**
     * As tolerâncias de estabilidade são fixas pelo sistema (LearningToleranceSettings ignora o payload): a ponte diz
     * isso em vez de registrar "atualizadas" no log e na sessão sem ter mudado nada. A água mínima tem ajuste próprio.
     */
    @JavascriptInterface
    fun setLearningToleranceSettings(payload: String): String = learningTolerances.toJson()
        .put("ok", false).put("fixed", true).put("error", "Tolerâncias fixas pelo sistema").toString()

    @JavascriptInterface
    fun resetLearningToleranceSettings(): String = learningTolerances.toJson()
        .put("ok", false).put("fixed", true).put("error", "Tolerâncias fixas pelo sistema").toString()

    @JavascriptInterface fun restartEngine(): Boolean = activity?.serviceOrNull()?.restartEngine() ?: false
    @JavascriptInterface fun connectUsb(deviceName: String): Boolean =
        activity?.serviceOrNull()?.connectUsb(deviceName.ifBlank { null }, userInitiated = true) ?: false
    @JavascriptInterface fun disconnectUsb() = activity?.serviceOrNull()?.disconnectUsb()
    @JavascriptInterface fun listUsbDevices(): String = activity?.serviceOrNull()?.usbDevicesJson() ?: "[]"

    @JavascriptInterface fun getFullEngineSnapshot(): String = activity?.serviceOrNull()?.fullEngineSnapshotJson() ?: "{}"

    @JavascriptInterface
    fun getLiveTelemetry(): String = activity?.serviceOrNull()?.let { service ->
        val root = service.telemetryStore.liveObject()
        val live = root.optJSONObject("live") ?: JSONObject()
        val interpolation = LearningGridProjection.liveInterpolationJson(
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

    @JavascriptInterface fun getEngineMetrics(): String = activity?.serviceOrNull()?.engineMetricsJson() ?: unavailable()
    @JavascriptInterface fun runEngineSelfTests(): String = activity?.serviceOrNull()?.engineSelfTestJson() ?: unavailable()
    @JavascriptInterface fun runProtocolLab(): String = activity?.serviceOrNull()?.protocolLabJson() ?: unavailable()

    // Fora da ponte JS (auditoria de fluxo, 2026-10-11): nenhuma tela chama; leitura serial síncrona travaria a tela.
    // O Mapa K lê pela ponte V7 (startMapRead), que roda fora da thread da tela.
    fun readKCell(row: Int, column: Int): String = activity?.serviceOrNull()?.readKCell(row, column) ?: unavailable()
    fun readKLine(row: Int): String = activity?.serviceOrNull()?.readKLine(row) ?: unavailable()
    fun readKMap(): String = activity?.serviceOrNull()?.readKMap() ?: unavailable()

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
                JSONObject().put("ok", false).put("error", error.message ?: "Falha ao ler mapa K")
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

    @JavascriptInterface fun previewKMapCell(row: Int, column: Int, targetValue: Int): String =
        activity?.serviceOrNull()?.previewKMapCell(row, column, targetValue) ?: unavailable()

    // Gravadores diretos fora da ponte JS (auditoria de fluxo, 2026-10-11): nenhuma tela chama e eles contornam o
    // coordenador V7. A gravação manual continua pela ponte V7 (Mapa K/Curva K), com readback. Bytes não mudam.
    fun startKWrite(row: Int, column: Int, current: Int, target: Int): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        CalibrationWriteSafetyPolicy.unsafeReason(service.status())?.let { return safetyBlocked(it) }
        if (target < 100) {
            return JSONObject()
                .put("ok", false)
                .put("error", "O valor mínimo de segurança do mapa K é 100")
                .toString()
        }
        return service.startKWrite(row, column, current, target)
    }

    fun startKBatchWrite(cellsJson: String, reason: String): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        CalibrationWriteSafetyPolicy.unsafeReason(service.status())?.let { return safetyBlocked(it) }
        val cells = try { JSONArray(cellsJson) } catch (_: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote de células inválido").toString()
        }
        if (cells.length() !in 1..KWriteManager.MAX_BATCH_CELLS) {
            return JSONObject().put("ok", false)
                .put("error", "Selecione entre 1 e ${KWriteManager.MAX_BATCH_CELLS} células")
                .toString()
        }
        repeat(cells.length()) { index ->
            val cell = cells.optJSONObject(index)
                ?: return JSONObject().put("ok", false).put("error", "Célula inválida no lote").toString()
            if (cell.optInt("target", -1) < 100) {
                return JSONObject()
                    .put("ok", false)
                    .put("error", "O valor mínimo de segurança do mapa K é 100")
                    .toString()
            }
        }
        return service.startKBatchWrite(cells.toString(), reason)
    }

    @JavascriptInterface fun getKWriteStatus(): String = activity?.serviceOrNull()?.kWriteStatusJson() ?: "{}"
    @JavascriptInterface fun getKWriteHistory(): String = activity?.serviceOrNull()?.kWriteHistoryJson() ?: "[]"
    @JavascriptInterface fun recoverKInsertionState(): String =
        activity?.serviceOrNull()?.recoverKInsertionState() ?: unavailable()

    // Sem @JavascriptInterface: espera a leitura automática (bloquearia a thread JavaBridge) e nenhuma tela chama;
    // a tela lê a curva por startCurveRead (executor da V7JavascriptBridge).
    fun readKFactorCurve(): String = activity?.serviceOrNull()?.readKFactorCurve() ?: unavailable()
    @JavascriptInterface fun previewKFactorPoint(index: Int, targetFactor: Double): String =
        KFactorManualPlanner.preview(AppPaths(appContext).runtimeRoot, index, targetFactor).toString()

    fun startKFactorWrite(pointsJson: String, reason: String): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        CalibrationWriteSafetyPolicy.unsafeReason(service.status())?.let { return safetyBlocked(it) }
        val minimumRaw = KFactorProtocol.rawFromFactor(0.60)
        val points = try { JSONArray(pointsJson) } catch (error: Exception) {
            return JSONObject().put("ok", false).put("error", "Lote K factor inválido").toString()
        }
        repeat(points.length()) { index ->
            val targetRaw = points.optJSONObject(index)?.optInt("targetRaw", -1) ?: -1
            if (targetRaw < minimumRaw) {
                return JSONObject()
                    .put("ok", false)
                    .put("error", "O fator K mínimo de segurança é 0,60 (Q14 $minimumRaw)")
                    .toString()
            }
        }
        return service.startKFactorWrite(points.toString(), reason)
    }

    @JavascriptInterface fun getKFactorStatus(): String = activity?.serviceOrNull()?.kFactorStatusJson() ?: "{}"
    @JavascriptInterface fun getKFactorHistory(): String = activity?.serviceOrNull()?.kFactorHistoryJson() ?: "[]"

    @JavascriptInterface fun getLearningSyncStatus(): String = activity?.serviceOrNull()?.learningSyncStatusJson() ?: "{}"

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
    /** Contagens de uso da interface (abas e toques) para o log da sessão; só contagens, nada de rede (core/usage-meter.js). */
    @JavascriptInterface fun recordUiUsage(json: String): String {
        return try {
            val service = activity?.serviceOrNull() ?: return unavailable()
            service.sessionRecorder.record("ui_usage", "webview", JSONObject(json), force = true)
            JSONObject().put("ok", true).toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false).toString()
        }
    }

    @JavascriptInterface fun setGpsEnabled(enabled: Boolean) = activity?.setGpsEnabled(enabled)
    @JavascriptInterface fun openAppSettings() = activity?.openAppSettings()
    @JavascriptInterface
    fun registerRefuel(addedM3: Double, distanceKm: Double): String {
        val service = activity?.serviceOrNull() ?: return unavailable()
        val capacity = AppSettings(appContext).gnvCylinderCapacityM3
        return service.consumptionTracker.registerRefuel(addedM3, distanceKm, capacity).toString()
    }

    @JavascriptInterface
    fun setGnvSettings(capacityM3: Float): String {
        AppSettings(appContext).gnvCylinderCapacityM3 = capacityM3
        return JSONObject().put("ok", true).toString()
    }
}