package com.omegas.prohub.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.omegas.prohub.calibration.KMapPhysicalAxes
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.stats.WeightedStat
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Ambiente OBD totalmente isolado do writer MP48.
 *
 * - Lê somente PIDs OBD-II por ELM327 Bluetooth clássico.
 * - STFT é o sinal principal; LTFT é contexto.
 * - Nunca chama UsbSerialManager, KWriteManager ou funções de escrita.
 * - Pode consumir telemetria MP48 local ou recebida pelo OMEGAS Link.
 * - Mantém uma segunda prova OBD independente em RPM x carga OBD.
 */
class ObdAssistManager(
    private val context: Context,
    private val paths: AppPaths,
    private val settings: AppSettings,
    private val log: RingLog,
    private val localCoreProvider: () -> JSONObject,
    private val onStateChanged: () -> Unit,
    private val onLiveSample: (JSONObject) -> Unit = {},
) {
    private data class PidDiagnostic(
        val command: String,
        var responded: Boolean = false,
        var latencyMs: Long = 0L,
        var observedAt: Long = 0L,
        var error: String = "",
    )

    private data class PidRead(
        val bytes: List<Int>?,
        val startedAtMs: Long,
        val observedAtMs: Long,
    )

    private data class ContextReadings(
        val ltftRead: PidRead = PidRead(null, 0L, 0L),
        val speedRead: PidRead = PidRead(null, 0L, 0L),
        val coolantRead: PidRead = PidRead(null, 0L, 0L),
        val loadRead: PidRead = PidRead(null, 0L, 0L),
        val throttleRead: PidRead = PidRead(null, 0L, 0L),
        val mapRead: PidRead = PidRead(null, 0L, 0L),
        val intakeAirRead: PidRead = PidRead(null, 0L, 0L),
        val mafRead: PidRead = PidRead(null, 0L, 0L),
        val fuelLevelRead: PidRead = PidRead(null, 0L, 0L),
        val moduleVoltageRead: PidRead = PidRead(null, 0L, 0L),
    ) {
        val ltft: Double? get() = ltftRead.bytes?.firstOrNull()?.let { (it - 128.0) * 100.0 / 128.0 }
        val speed: Double? get() = speedRead.bytes?.firstOrNull()?.toDouble()
        val coolant: Double? get() = coolantRead.bytes?.firstOrNull()?.let { it - 40.0 }
        val load: Double? get() = loadRead.bytes?.firstOrNull()?.let { it * 100.0 / 255.0 }
        val throttle: Double? get() = throttleRead.bytes?.firstOrNull()?.let { it * 100.0 / 255.0 }
        val mapKpa: Double? get() = mapRead.bytes?.firstOrNull()?.toDouble()
        val intakeAirC: Double? get() = intakeAirRead.bytes?.firstOrNull()?.let { it - 40.0 }
        val mafGps: Double? get() = mafRead.bytes?.takeIf { it.size >= 2 }?.let { (it[0] * 256.0 + it[1]) / 100.0 }
        val fuelLevelPct: Double? get() = fuelLevelRead.bytes?.firstOrNull()?.let { it * 100.0 / 255.0 }
        val moduleVoltageV: Double? get() = moduleVoltageRead.bytes?.takeIf { it.size >= 2 }?.let { (it[0] * 256.0 + it[1]) / 1000.0 }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val RPM_BINS = KMapPhysicalAxes.rpmBins().map { it.toDouble() }.toDoubleArray()
        private val PETROL_MS_BINS = KMapPhysicalAxes.petrolBins()
    }

    data class CellStats(
        val stft: WeightedStat = WeightedStat(),
        val ltft: WeightedStat = WeightedStat(),
        val speed: WeightedStat = WeightedStat(),
        val coolant: WeightedStat = WeightedStat(),
        var qualified: Long = 0,
        var rejected: Long = 0,
        var updatedAt: Long = 0L,
    ) {
        fun merge(other: CellStats): CellStats {
            stft.merge(other.stft)
            ltft.merge(other.ltft)
            speed.merge(other.speed)
            coolant.merge(other.coolant)
            qualified += other.qualified
            rejected += other.rejected
            updatedAt = maxOf(updatedAt, other.updatedAt)
            return this
        }

        fun toJson(): JSONObject = JSONObject()
            .put("stft", stft.toJson())
            .put("ltft", ltft.toJson())
            .put("speed", speed.toJson())
            .put("coolant", coolant.toJson())
            .put("qualified", qualified)
            .put("rejected", rejected)
            .put("updatedAt", updatedAt)

        companion object {
            fun fromJson(json: JSONObject?): CellStats = CellStats(
                stft = WeightedStat.fromJson(json?.optJSONObject("stft")),
                ltft = WeightedStat.fromJson(json?.optJSONObject("ltft")),
                speed = WeightedStat.fromJson(json?.optJSONObject("speed")),
                coolant = WeightedStat.fromJson(json?.optJSONObject("coolant")),
                qualified = json?.optLong("qualified", 0L) ?: 0L,
                rejected = json?.optLong("rejected", 0L) ?: 0L,
                updatedAt = json?.optLong("updatedAt", 0L) ?: 0L,
            )
        }
    }

    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-obd").apply { isDaemon = true }
    }
    private val stateLock = Any()
    private var socket: BluetoothSocket? = null
    private var remoteCore = JSONObject()
    private var remoteCoreAt = 0L
    private var remoteLive = JSONObject()
    private var remoteLiveAt = 0L
    private val localMaps: MutableMap<String, MutableMap<String, CellStats>> = mutableMapOf(
        "GASOLINA" to linkedMapOf(),
        "GNV" to linkedMapOf(),
    )
    private val independentMap = ObdIndependentEvidenceMap { settings.obdMinimumSamplesPerCell }
    private val remoteComponents = linkedMapOf<String, MutableMap<String, MutableMap<String, CellStats>>>()
    private var live = JSONObject()
        .put("connected", false)
        .put("mode", settings.obdMode)
        .put("state", "DESATIVADO")
        .put("connectionStage", ElmStage.IDLE.name)
        .put("connectionErrorCode", JSONObject.NULL)
        .put("connectionDetail", "OBD local inativo")
        .put("retryable", false)
        .put("stft", JSONObject.NULL)
        .put("ltft", JSONObject.NULL)
        .put("rpm", JSONObject.NULL)
        .put("speed", JSONObject.NULL)
        .put("coolant", JSONObject.NULL)
        .put("load", JSONObject.NULL)
        .put("mapKpa", JSONObject.NULL)
        .put("intakeAirC", JSONObject.NULL)
        .put("mafGps", JSONObject.NULL)
        .put("fuelLevelPct", JSONObject.NULL)
        .put("moduleVoltageV", JSONObject.NULL)
        .put("closedLoop", false)
        .put("quality", "SEM DADOS")
        .put("reason", "Ative uma fonte OBD")
    private var tripStartedAt = 0L
    private var tripLastAt = 0L
    private var tripDistanceKm = 0.0
    private var tripEstimatedGasLiters = 0.0
    private val pidDiagnostics = linkedMapOf<String, PidDiagnostic>()
    private var lastCommand = ""
    private var lastCommandAt = 0L
    private var lastError = ""
    private var lastCycleMs = 0L
    private var pollWindowStartedAt = 0L
    private var pollWindowCycles = 0
    private var pollSequence = 0L
    private var cachedContext = ContextReadings()
    private var currentSessionStartedAt = 0L
    private var lastSessionEndedAt = 0L
    /** PIDs Mode 01 anunciados pela ECU; não presumimos que todo carro responda igual. */
    private val supportedStandardPids = linkedSetOf<Int>()
    private val conditionEngine = ObdConditionEngine()
    private val evidenceLedger = ObdEvidenceLedger()
    private val connectionState = ElmConnectionState(
        connectTimeoutMs = 12_000L,
        handshakeTimeoutMs = 12_000L,
    )
    /** Mapas fechados ficam preservados por época; o ativo nunca mistura antes/depois. */
    private val closedEpochMaps = linkedMapOf<String, JSONObject>()
    private val storageFile = File(paths.runtimeRoot, "obd_assist_v1.json")

    init {
        load()
    }

    fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevicesJson(): String {
        val devices = JSONArray()
        if (!hasBluetoothPermission()) {
            return JSONObject().put("permissionRequired", true).put("devices", devices).toString()
        }
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val connectedAddress = synchronized(stateLock) {
                settings.obdDeviceAddress.takeIf { running.get() && socket?.isConnected == true }
            }
            adapter?.bondedDevices?.sortedBy { it.name ?: it.address }?.forEach { device ->
                val isConnected = device.address == connectedAddress
                devices.put(
                    JSONObject()
                        .put("name", device.name ?: "ELM327")
                        .put("address", device.address)
                        .put("bonded", true)
                        .put("selected", device.address == settings.obdDeviceAddress)
                        .put("connected", isConnected),
                )
            }
            JSONObject()
                .put("permissionRequired", false)
                .put("enabled", adapter?.isEnabled == true)
                .put("devices", devices)
                .toString()
        } catch (error: SecurityException) {
            JSONObject()
                .put("permissionRequired", true)
                .put("error", error.message)
                .put("devices", devices)
                .toString()
        }
    }

    private fun discoverStandardPids(sock: BluetoothSocket) {
        val discovered = linkedSetOf<Int>()
        var page = 0
        while (page <= 0xE0) {
            val bitmap = readPid(sock, "01%02X".format(page), page)?.take(4) ?: break
            for (offset in 1..0x20) {
                val byteIndex = (offset - 1) / 8
                val bitIndex = 7 - ((offset - 1) % 8)
                if ((bitmap[byteIndex] and (1 shl bitIndex)) != 0) discovered += page + offset
            }
            if (!discovered.contains(page + 0x20)) break
            page += 0x20
        }
        synchronized(stateLock) {
            supportedStandardPids.clear()
            supportedStandardPids.addAll(discovered)
        }
        log.add("INFO", "OBD", "PIDs padrão anunciados: ${discovered.joinToString(",") { "01%02X".format(it) }}")
    }

    @Synchronized
    fun setMode(mode: String): JSONObject {
        val normalized = when (mode.lowercase()) {
            "local" -> "local"
            "remote" -> "remote"
            else -> "off"
        }
        settings.obdMode = normalized
        if (normalized != "local") disconnect()
        synchronized(stateLock) {
            live.put("mode", normalized)
            if (normalized == "off") live.put("state", "DESATIVADO").put("connected", false)
            if (normalized == "remote") live.put("state", "AGUARDANDO OMEGAS LINK").put("connected", false)
        }
        onStateChanged()
        return JSONObject().put("ok", true).put("mode", normalized)
    }

    @Synchronized
    fun connect(address: String): JSONObject {
        val now = System.currentTimeMillis()
        if (!hasBluetoothPermission()) {
            val status = connectionState.enter(ElmStage.PERMISSION, now, "Permissão Bluetooth necessária")
            publishConnectionStatus(status, permissionRequired = true)
            onStateChanged()
            return JSONObject()
                .put("ok", false)
                .put("permissionRequired", true)
                .put("connectionStage", status.stage.name)
                .put("error", "Permissão Bluetooth necessária")
        }
        if (running.get()) {
            val status = connectionState.snapshot()
            return JSONObject()
                .put("ok", true)
                .put("state", "já conectado")
                .put("connectionStage", status.stage.name)
        }
        val clean = address.trim().ifBlank { settings.obdDeviceAddress }
        if (clean.isBlank()) return JSONObject().put("ok", false).put("error", "Selecione um adaptador OBD pareado")
        settings.obdMode = "local"
        settings.obdDeviceAddress = clean
        running.set(true)
        val status = connectionState.enter(ElmStage.RFCOMM, now, "Abrindo Bluetooth RFCOMM")
        publishConnectionStatus(status, device = clean)
        worker.execute { connectionLoop(clean) }
        onStateChanged()
        return JSONObject()
            .put("ok", true)
            .put("state", "CONECTANDO")
            .put("connectionStage", status.stage.name)
    }

    @Synchronized
    fun disconnect() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        val now = System.currentTimeMillis()
        val status = connectionState.enter(ElmStage.IDLE, now, "OBD local desconectado")
        synchronized(stateLock) {
            if (currentSessionStartedAt > 0L) lastSessionEndedAt = now
            currentSessionStartedAt = 0L
            live.put("connected", false)
                .put("sessionLive", false)
                .put("lastSessionEndedAt", lastSessionEndedAt)
                .put("state", if (settings.obdMode == "remote") "AGUARDANDO OMEGAS LINK" else "DESCONECTADO")
        }
        if (settings.obdMode != "remote") publishConnectionStatus(status)
        conditionEngine.reset()
        onStateChanged()
    }

    @Synchronized
    fun setManualFuel(fuel: String): JSONObject {
        settings.obdManualFuel = when (fuel.trim().uppercase()) {
            "GNV", "CNG", "GAS" -> "GNV"
            "GASOLINA", "PETROL" -> "GASOLINA"
            else -> ""
        }
        onStateChanged()
        return JSONObject()
            .put("ok", true)
            .put("manualFuel", settings.obdManualFuel.ifBlank { JSONObject.NULL })
            .put("manualFuelSource", if (settings.obdManualFuel.isBlank()) "UNKNOWN" else "MANUAL_OPERATOR")
    }

    fun close() {
        disconnect()
        worker.shutdownNow()
        save()
    }

    fun updateRemoteCoreTelemetry(payload: JSONObject) {
        synchronized(stateLock) {
            remoteCore = JSONObject(payload.toString())
            remoteCoreAt = System.currentTimeMillis()
        }
    }

    fun acceptRemoteLive(payload: JSONObject) {
        synchronized(stateLock) {
            remoteLive = JSONObject(payload.toString())
            remoteLiveAt = System.currentTimeMillis()
            if (settings.obdMode == "remote") {
                live = JSONObject(payload.toString())
                    .put("mode", "remote")
                    .put("connected", true)
                    .put("state", "REMOTO AO VIVO")
            }
        }
        try { onLiveSample(JSONObject(payload.toString()).put("mode", "remote")) } catch (_: Exception) {}
        onStateChanged()
    }

    fun statusJson(): String = synchronized(stateLock) {
        val now = System.currentTimeMillis()
        if (settings.obdMode == "remote" && now - remoteLiveAt > 5_000L) {
            live.put("connected", false)
                .put("state", "AGUARDANDO OMEGAS LINK")
                .put("reason", "Dados remotos expiraram")
        }
        JSONObject(live.toString())
            .put("mode", settings.obdMode)
            .put("deviceAddress", settings.obdDeviceAddress)
            .put("permissionRequired", !hasBluetoothPermission())
            .put("trip", tripJson())
            .put("diagnostic", diagnosticJsonLocked())
            .toString()
    }

    fun mapsJson(): String = synchronized(stateLock) {
        val petrol = fusedMap("GASOLINA")
        val gnv = fusedMap("GNV")
        val metrics = evidenceLedger.metricsJson()
            .put("accepted", petrol.values.sumOf { it.qualified } + gnv.values.sumOf { it.qualified })
        JSONObject()
            .put("rpmBins", JSONArray(RPM_BINS.toList()))
            .put("petrolMsBins", JSONArray(PETROL_MS_BINS.toList()))
            .put("gasoline", mapToJson(petrol))
            .put("gnv", mapToJson(gnv))
            .put("validation", validationJson(petrol, gnv))
            .put("independent", independentMap.toJson())
            .put("epoch", epochJson(evidenceLedger.current()))
            .put("history", JSONArray().also { history ->
                closedEpochMaps.forEach { (epochId, maps) ->
                    history.put(JSONObject().put("epochId", epochId).put("maps", JSONObject(maps.toString())))
                }
            })
            .put("rejectionMetrics", metrics)
            .put("remoteComponentsStandby", true)
            .put("updatedAt", maxOf(
                petrol.values.maxOfOrNull { it.updatedAt } ?: 0L,
                gnv.values.maxOfOrNull { it.updatedAt } ?: 0L,
                independentMap.toJson().optLong("updatedAt", 0L),
            ))
            .toString()
    }

    fun exportLocalState(deviceId: String): JSONObject = synchronized(stateLock) {
        val maps = JSONObject()
        localMaps.forEach { (fuel, values) -> maps.put(fuel, mapToJson(values)) }
        JSONObject()
            .put("format", "omegas-obd-component-v1")
            .put("deviceId", deviceId)
            .put("revision", localRevision())
            .put("maps", maps)
            .put("independent", independentMap.persistenceJson())
            .put("trip", tripJson())
    }

    fun importPortableState(payload: JSONObject, localDeviceId: String): JSONObject = synchronized(stateLock) {
        if (payload.optString("format") != "omegas-obd-component-v1") {
            return JSONObject().put("ok", false).put("error", "Formato OBD incompatível")
        }
        val source = payload.optString("deviceId").trim()
        if (source == localDeviceId) {
            val mapsJson = payload.optJSONObject("maps") ?: JSONObject()
            for (fuel in listOf("GASOLINA", "GNV")) {
                localMaps.getValue(fuel).clear()
                localMaps.getValue(fuel).putAll(jsonToMap(mapsJson.optJSONObject(fuel)))
            }
            independentMap.load(payload.optJSONObject("independent"))
            save()
            return JSONObject().put("ok", true).put("restoredLocal", true).put("cells", localMaps.values.sumOf { it.size })
        }
        mergeRemoteState(payload)
    }

    fun mergeRemoteState(payload: JSONObject): JSONObject = synchronized(stateLock) {
        if (payload.optString("format") != "omegas-obd-component-v1") {
            return JSONObject().put("ok", false).put("error", "Formato OBD incompatível")
        }
        val deviceId = payload.optString("deviceId").trim()
        if (deviceId.isBlank() || deviceId == settings.deviceId) {
            return JSONObject().put("ok", false).put("error", "Origem OBD inválida")
        }
        val mapsJson = payload.optJSONObject("maps") ?: JSONObject()
        val component = linkedMapOf<String, MutableMap<String, CellStats>>()
        for (fuel in listOf("GASOLINA", "GNV")) component[fuel] = jsonToMap(mapsJson.optJSONObject(fuel))
        remoteComponents[deviceId] = component
        save()
        return JSONObject().put("ok", true).put("deviceId", deviceId).put("cells", component.values.sumOf { it.size })
    }

    fun resetTrip(): JSONObject = synchronized(stateLock) {
        tripStartedAt = System.currentTimeMillis()
        tripLastAt = 0L
        tripDistanceKm = 0.0
        tripEstimatedGasLiters = 0.0
        save()
        JSONObject().put("ok", true)
    }

    fun recordConfirmedAdjustment(kind: String, receipt: JSONObject): JSONObject = synchronized(stateLock) {
        val previous = evidenceLedger.current()
        val result = evidenceLedger.openAfterConfirmedReadback(
            kind = kind,
            humanConfirmed = receipt.optBoolean("humanConfirmed", false),
            readbackValid = receipt.optBoolean("readbackValid", false),
            readbackHash = receipt.optString("newHash").takeIf { it.isNotBlank() },
            nowMs = receipt.optLong("confirmedAt", System.currentTimeMillis()),
        )
        if (!result.opened) return@synchronized JSONObject().put("ok", false).put("reason", result.reason)
        closedEpochMaps["${previous.mapEpochId}:${previous.curveEpochId}"] = snapshotLocalMaps()
        localMaps.values.forEach { it.clear() }
        independentMap.clear()
        conditionEngine.reset()
        save()
        onStateChanged()
        JSONObject().put("ok", true).put("reason", result.reason).put("epoch", epochJson(result.epoch!!))
    }

    fun calibrateConsumption(actualLiters: Double): JSONObject = synchronized(stateLock) {
        if (actualLiters <= 0.0 || tripEstimatedGasLiters <= 0.0) {
            return JSONObject().put("ok", false).put("error", "Informe litros reais após uma viagem com estimativa")
        }
        val ratio = actualLiters / tripEstimatedGasLiters
        settings.obdGasFlowCoefficient = (settings.obdGasFlowCoefficient * ratio).coerceIn(0.00000001, 0.01)
        JSONObject().put("ok", true).put("coefficient", settings.obdGasFlowCoefficient).put("ratio", ratio)
    }

    private fun publishConnectionStatus(
        status: ElmConnectionStatus,
        connected: Boolean = status.stage == ElmStage.LIVE,
        permissionRequired: Boolean = false,
        device: String? = null,
    ) = synchronized(stateLock) {
        live.put("connectionStage", status.stage.name)
            .put("connectionErrorCode", status.errorCode.ifBlank { JSONObject.NULL })
            .put("connectionDetail", status.detail.ifBlank { JSONObject.NULL })
            .put("retryable", status.retryable)
            .put("connected", connected)
            .put("permissionRequired", permissionRequired)
            .put("state", connectionStageLabel(status.stage))
        if (status.detail.isNotBlank()) live.put("reason", status.detail) else live.remove("reason")
        if (!device.isNullOrBlank()) live.put("device", device)
    }

    private fun connectionStageLabel(stage: ElmStage): String = when (stage) {
        ElmStage.IDLE -> "DESCONECTADO"
        ElmStage.PERMISSION -> "PERMISSÃO NECESSÁRIA"
        ElmStage.RFCOMM -> "CONECTANDO BLUETOOTH"
        ElmStage.ELM_INIT -> "INICIALIZANDO ELM"
        ElmStage.PROTOCOL -> "NEGOCIANDO OBD"
        ElmStage.STFT_READY -> "STFT DISPONÍVEL"
        ElmStage.LIVE -> "CONECTADO"
        ElmStage.ERROR -> "ERRO"
    }

    private fun connectionFailureCode(stage: ElmStage): String = when (stage) {
        ElmStage.PERMISSION -> "BLUETOOTH_PERMISSION_REQUIRED"
        ElmStage.RFCOMM -> "RFCOMM_FAILED"
        ElmStage.ELM_INIT -> "ELM_INIT_FAILED"
        ElmStage.PROTOCOL -> "PROTOCOL_FAILED"
        ElmStage.STFT_READY -> "STFT_PROBE_FAILED"
        ElmStage.LIVE -> "LIVE_LINK_LOST"
        else -> "OBD_CONNECTION_FAILED"
    }

    private fun elmResponseFailed(response: String): Boolean {
        val upper = response.uppercase().trim()
        return upper.isBlank() || upper == "?" || listOf(
            "NO DATA",
            "UNABLE TO CONNECT",
            "STOPPED",
            "BUS ERROR",
            "CAN ERROR",
            "ERROR",
        ).any { upper.contains(it) }
    }

    @SuppressLint("MissingPermission")
    private fun connectionLoop(address: String) {
        var current: BluetoothSocket? = null
        var deviceLabel = address
        try {
            if (!hasBluetoothPermission()) throw SecurityException("Permissão Bluetooth necessária")
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth indisponível")
            if (!adapter.isEnabled) error("Ative o Bluetooth")
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            deviceLabel = device.name ?: address
            val rfcomm = connectionState.enter(ElmStage.RFCOMM, System.currentTimeMillis(), "Abrindo Bluetooth RFCOMM")
            publishConnectionStatus(rfcomm, device = deviceLabel)
            onStateChanged()

            current = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket = current
            val connectingSocket = current
            Thread({
                try {
                    Thread.sleep(12_100L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
                val timed = connectionState.onClock(System.currentTimeMillis())
                if (
                    timed.stage == ElmStage.ERROR &&
                    timed.errorCode == "RFCOMM_TIMEOUT" &&
                    running.get() &&
                    socket === connectingSocket
                ) {
                    publishConnectionStatus(timed, device = deviceLabel)
                    try { connectingSocket.close() } catch (_: Exception) {}
                    log.add("WARN", "OBD", "RFCOMM timeout: ${timed.detail}")
                    onStateChanged()
                }
            }, "omegas-obd-rfcomm-watchdog").apply {
                isDaemon = true
                start()
            }

            adapter.cancelDiscovery()
            current.connect()
            connectionState.snapshot().takeIf { it.stage == ElmStage.ERROR }?.let { timed ->
                error(timed.detail.ifBlank { timed.errorCode })
            }

            pollSequence = 0L
            cachedContext = ContextReadings()
            val init = connectionState.enter(ElmStage.ELM_INIT, System.currentTimeMillis(), "Inicializando adaptador ELM")
            publishConnectionStatus(init, device = deviceLabel)
            onStateChanged()
            initializeElm(current)

            val liveStatus = connectionState.enter(ElmStage.LIVE, System.currentTimeMillis(), "STFT OBD ao vivo")
            synchronized(stateLock) {
                currentSessionStartedAt = System.currentTimeMillis()
                live.put("sessionLive", true)
                    .put("sessionStartedAt", currentSessionStartedAt)
                    .put("device", deviceLabel)
            }
            publishConnectionStatus(liveStatus, connected = true, device = deviceLabel)
            log.add("INFO", "OBD", "ELM327 conectado: $deviceLabel")
            onStateChanged()
            while (running.get() && current.isConnected) pollCycle(current)
        } catch (error: SecurityException) {
            log.add("WARN", "OBD", "Permissão Bluetooth indisponível durante a conexão")
            val failed = connectionState.fail(
                "BLUETOOTH_PERMISSION_REQUIRED",
                error.message ?: "Permissão Bluetooth necessária",
                System.currentTimeMillis(),
                retryable = false,
            )
            publishConnectionStatus(failed, permissionRequired = true, device = deviceLabel)
        } catch (error: Exception) {
            val prior = connectionState.snapshot()
            if (!running.get() && prior.stage != ElmStage.ERROR) {
                publishConnectionStatus(
                    connectionState.enter(ElmStage.IDLE, System.currentTimeMillis(), "OBD local desconectado"),
                    device = deviceLabel,
                )
            } else {
                val failed = if (prior.stage == ElmStage.ERROR) prior else connectionState.fail(
                    connectionFailureCode(prior.stage),
                    error.message ?: "Falha OBD",
                    System.currentTimeMillis(),
                    retryable = true,
                )
                log.add("WARN", "OBD", "Conexão encerrada [${failed.errorCode}]: ${failed.detail}")
                publishConnectionStatus(failed, device = deviceLabel)
            }
        } finally {
            running.set(false)
            try { current?.close() } catch (_: Exception) {}
            if (socket === current) socket = null
            synchronized(stateLock) {
                if (currentSessionStartedAt > 0L) lastSessionEndedAt = System.currentTimeMillis()
                currentSessionStartedAt = 0L
                live.put("connected", false)
                    .put("sessionLive", false)
                    .put("lastSessionEndedAt", lastSessionEndedAt)
            }
            conditionEngine.reset()
            onStateChanged()
        }
    }

    private fun initializeElm(sock: BluetoothSocket) {
        val reset = elmCommand(sock, "ATZ", 2500L)
        if (elmResponseFailed(reset)) error("ELM não respondeu ao reset ATZ")

        listOf("ATE0", "ATL0", "ATS0", "ATH0").forEach { command ->
            val response = elmCommand(sock, command, 1200L)
            if (elmResponseFailed(response)) error("ELM falhou em $command")
        }

        // Adaptive timing melhora clones lentos; é best-effort porque alguns
        // adaptadores antigos respondem '?' e ainda funcionam normalmente.
        val adaptiveTiming = elmCommand(sock, "ATAT1", 1200L)
        if (elmResponseFailed(adaptiveTiming)) {
            log.add("WARN", "OBD", "ELM não aceitou ATAT1; seguindo sem adaptive timing")
        }

        val identity = elmCommand(sock, "ATI", 1200L)
        if (elmResponseFailed(identity) || identity.trim().equals("ATI", ignoreCase = true)) {
            error("ELM não respondeu ao ATI")
        }

        val protocol = connectionState.enter(ElmStage.PROTOCOL, System.currentTimeMillis(), "Negociando protocolo OBD automático")
        publishConnectionStatus(protocol)
        onStateChanged()
        val protocolResponse = elmCommand(sock, "ATSP0", 1500L)
        if (elmResponseFailed(protocolResponse)) error("ELM não encontrou protocolo OBD")
        val protocolClock = connectionState.onClock(System.currentTimeMillis())
        if (protocolClock.stage == ElmStage.ERROR) error(protocolClock.detail)

        discoverStandardPids(sock)
        val stftProbe = readPidTimed(sock, "0106", 0x06)
        if (stftProbe.bytes == null) error("PID 0106/STFT não respondeu")
        val ready = connectionState.enter(ElmStage.STFT_READY, System.currentTimeMillis(), "PID 0106/STFT disponível")
        publishConnectionStatus(ready)
        onStateChanged()
    }

    private fun pollCycle(sock: BluetoothSocket) {
        val cycleStartedAt = System.currentTimeMillis()
        val fuelStatusRead = readPidTimed(sock, "0103", 0x03)
        val fuelStatus = fuelStatusRead.bytes?.firstOrNull() ?: 0
        val closedLoop = fuelStatus == 2 || fuelStatus == 16
        val stftRead = readPidTimed(sock, "0106", 0x06)
        val stft = stftRead.bytes?.firstOrNull()?.let { (it - 128.0) * 100.0 / 128.0 }
        val rpmRead = readPidTimed(sock, "010C", 0x0C)
        val rpmBytes = rpmRead.bytes
        val rpm = rpmBytes?.takeIf { it.size >= 2 }?.let { (it[0] * 256.0 + it[1]) / 4.0 }
        val core = currentCore(rpmRead.observedAtMs)
        val context = readContext(sock)
        val now = System.currentTimeMillis()
        val cycleMs = (now - cycleStartedAt).coerceAtLeast(0L)
        synchronized(stateLock) {
            lastCycleMs = cycleMs
            if (pollWindowStartedAt == 0L || now - pollWindowStartedAt > 10_000L) {
                pollWindowStartedAt = now
                pollWindowCycles = 0
            }
            pollWindowCycles += 1
        }
        val gate = qualification(
            core = core,
            stft = stft,
            rpm = rpm,
            coolant = context.coolant,
            closedLoop = closedLoop,
            stftObservedAtMs = stftRead.observedAtMs,
            rpmObservedAtMs = rpmRead.observedAtMs,
            closedLoopObservedAtMs = fuelStatusRead.observedAtMs,
            stftStartedAtMs = stftRead.startedAtMs,
            rpmStartedAtMs = rpmRead.startedAtMs,
            closedLoopStartedAtMs = fuelStatusRead.startedAtMs,
            coolantObservedAtMs = context.coolantRead.observedAtMs,
        )
        val fuelState = gate.fuelState
        val conditionState = if (gate.accepted && stft != null) {
            collectQualified(
                core,
                fuelState.fuel!!,
                stft,
                context.ltft,
                context.speed,
                context.coolant,
                rpm!!,
                rpmRead.observedAtMs,
            )
        } else {
            conditionEngine.reset()
            evidenceLedger.recordRejection(gate.reason)
            "PAUSADO"
        }

        val independentFuel = observationalFuel(core)
        val independentObservation = independentMap.observe(
            fuel = independentFuel.first,
            rpm = rpm,
            loadPct = context.load,
            stftPct = stft,
            ltftPct = context.ltft,
            speedKmh = context.speed,
            coolantC = context.coolant,
            mapKpa = context.mapKpa,
            mafGps = context.mafGps,
            throttlePct = context.throttle,
            closedLoop = closedLoop,
            minimumCoolantC = settings.obdMinimumCoolantC,
            nowMs = now,
        )

        val payload = JSONObject()
            .put("connected", true)
            .put("mode", "local")
            .put("state", "CONECTADO")
            .put("stft", stft ?: JSONObject.NULL)
            .put("ltft", context.ltft ?: JSONObject.NULL)
            .put("rpm", rpm ?: JSONObject.NULL)
            .put("speed", context.speed ?: JSONObject.NULL)
            .put("coolant", context.coolant ?: JSONObject.NULL)
            .put("load", context.load ?: JSONObject.NULL)
            .put("throttle", context.throttle ?: JSONObject.NULL)
            .put("mapKpa", context.mapKpa ?: JSONObject.NULL)
            .put("intakeAirC", context.intakeAirC ?: JSONObject.NULL)
            .put("mafGps", context.mafGps ?: JSONObject.NULL)
            .put("fuelLevelPct", context.fuelLevelPct ?: JSONObject.NULL)
            .put("fuelLevelSupported", supportsStandardPid(0x2F))
            .put("moduleVoltageV", context.moduleVoltageV ?: JSONObject.NULL)
            .put("closedLoop", closedLoop)
            .put("quality", if (gate.accepted) "BOA" else "OBSERVACIONAL")
            .put("reason", gate.reason)
            .put("fuel", fuelState.fuel ?: JSONObject.NULL)
            .put("fuelSource", fuelState.source.name)
            .put("manualFuel", settings.obdManualFuel.ifBlank { JSONObject.NULL })
            .put("learningState", if (gate.accepted) "QUALIFICADO" else "PAUSADO")
            .put("physicalCellAvailable", fuelState.canQualifyMap && gate.accepted)
            .put("conditionState", conditionState)
            .put("independentEvidence", JSONObject()
                .put("accepted", independentObservation.accepted)
                .put("reason", independentObservation.reason)
                .put("cellKey", independentObservation.key ?: JSONObject.NULL)
                .put("fuel", independentFuel.first ?: JSONObject.NULL)
                .put("fuelSource", independentFuel.second)
                .put("axes", "OBD_RPM_X_LOAD"))
            .put("pollCycleMs", cycleMs)
            .put("pidObservedAt", JSONObject()
                .put("stft", stftRead.observedAtMs)
                .put("rpm", rpmRead.observedAtMs)
                .put("closedLoop", fuelStatusRead.observedAtMs)
                .put("ltft", context.ltftRead.observedAtMs)
                .put("speed", context.speedRead.observedAtMs)
                .put("coolant", context.coolantRead.observedAtMs)
                .put("load", context.loadRead.observedAtMs)
                .put("throttle", context.throttleRead.observedAtMs)
                .put("map", context.mapRead.observedAtMs)
                .put("intakeAir", context.intakeAirRead.observedAtMs)
                .put("maf", context.mafRead.observedAtMs)
                .put("fuelLevel", context.fuelLevelRead.observedAtMs)
                .put("moduleVoltage", context.moduleVoltageRead.observedAtMs))
            .put("pidReadStartedAt", JSONObject()
                .put("stft", stftRead.startedAtMs)
                .put("rpm", rpmRead.startedAtMs)
                .put("closedLoop", fuelStatusRead.startedAtMs)
                .put("ltft", context.ltftRead.startedAtMs)
                .put("speed", context.speedRead.startedAtMs)
                .put("coolant", context.coolantRead.startedAtMs)
                .put("load", context.loadRead.startedAtMs)
                .put("throttle", context.throttleRead.startedAtMs)
                .put("map", context.mapRead.startedAtMs)
                .put("intakeAir", context.intakeAirRead.startedAtMs)
                .put("maf", context.mafRead.startedAtMs)
                .put("fuelLevel", context.fuelLevelRead.startedAtMs)
                .put("moduleVoltage", context.moduleVoltageRead.startedAtMs))
            .put("pidAgeMs", JSONObject()
                .put("stft", pidAgeMs(stftRead, now))
                .put("rpm", pidAgeMs(rpmRead, now))
                .put("closedLoop", pidAgeMs(fuelStatusRead, now))
                .put("ltft", pidAgeMs(context.ltftRead, now))
                .put("speed", pidAgeMs(context.speedRead, now))
                .put("coolant", pidAgeMs(context.coolantRead, now))
                .put("load", pidAgeMs(context.loadRead, now))
                .put("throttle", pidAgeMs(context.throttleRead, now))
                .put("map", pidAgeMs(context.mapRead, now))
                .put("intakeAir", pidAgeMs(context.intakeAirRead, now))
                .put("maf", pidAgeMs(context.mafRead, now))
                .put("fuelLevel", pidAgeMs(context.fuelLevelRead, now))
                .put("moduleVoltage", pidAgeMs(context.moduleVoltageRead, now)))
            .put("sessionLive", true)
            .put("sessionStartedAt", currentSessionStartedAt)
            .put("updatedAt", now)
            .put("coreSource", core.optString("source", "local"))
            .put("mp48Available", core.optBoolean("engineReady", false) && core.optString("source") != "indisponível")
        synchronized(stateLock) { live = payload }
        try { onLiveSample(JSONObject(payload.toString())) } catch (_: Exception) {}
        updateTrip(core, context.speed ?: 0.0, now)
        if (independentObservation.accepted && pollSequence % 20L == 0L) save()
        onStateChanged()
        try {
            Thread.sleep(settings.obdPollIntervalMs.coerceIn(150L, 3000L))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readContext(sock: BluetoothSocket): ContextReadings {
        pollSequence += 1L
        val refreshMedium = pollSequence == 1L || pollSequence % 2L == 0L
        val refreshFastContext = pollSequence == 1L || pollSequence % 4L == 0L
        val refreshSlow = pollSequence == 1L || pollSequence % 8L == 0L
        val refreshVerySlow = pollSequence == 1L || pollSequence % 20L == 0L
        cachedContext = ContextReadings(
            ltftRead = if (refreshMedium) readSupportedPidTimed(sock, "0107", 0x07) else cachedContext.ltftRead,
            speedRead = if (refreshMedium) readSupportedPidTimed(sock, "010D", 0x0D) else cachedContext.speedRead,
            coolantRead = if (refreshSlow) readSupportedPidTimed(sock, "0105", 0x05) else cachedContext.coolantRead,
            loadRead = if (refreshMedium) readSupportedPidTimed(sock, "0104", 0x04) else cachedContext.loadRead,
            throttleRead = if (refreshSlow) readSupportedPidTimed(sock, "0111", 0x11) else cachedContext.throttleRead,
            mapRead = if (refreshFastContext) readSupportedPidTimed(sock, "010B", 0x0B) else cachedContext.mapRead,
            intakeAirRead = if (refreshSlow) readSupportedPidTimed(sock, "010F", 0x0F) else cachedContext.intakeAirRead,
            mafRead = if (refreshFastContext) readSupportedPidTimed(sock, "0110", 0x10) else cachedContext.mafRead,
            fuelLevelRead = if (refreshVerySlow) readSupportedPidTimed(sock, "012F", 0x2F) else cachedContext.fuelLevelRead,
            moduleVoltageRead = if (refreshSlow) readSupportedPidTimed(sock, "0142", 0x42) else cachedContext.moduleVoltageRead,
        )
        return cachedContext
    }

    private fun observationalFuel(core: JSONObject): Pair<String?, String> {
        val raw = core.optString("fuelState", core.optString("fuel", core.optString("state", ""))).uppercase()
        val fromMp48 = when {
            raw.contains("GNV") || raw == "CNG" || raw == "GAS" -> "GNV"
            raw.contains("GASOLINA") || raw == "PETROL" -> "GASOLINA"
            else -> null
        }
        if (fromMp48 != null && core.optString("source") != "indisponível") return fromMp48 to "MP48_LABEL"
        val manual = settings.obdManualFuel.takeIf { it == "GNV" || it == "GASOLINA" }
        return manual to if (manual == null) "UNKNOWN" else "MANUAL_OPERATOR_LABEL"
    }

    private fun currentCore(now: Long): JSONObject {
        val local = try { localCoreProvider() } catch (_: Exception) { JSONObject() }
        val localAt = local.optLong("updatedAt", local.optLong("native_updated_at", 0L))
        return synchronized(stateLock) {
            when {
                local.optBoolean("engineReady", false) || now - localAt < 3_000L -> JSONObject(local.toString()).put("source", "local")
                now - remoteCoreAt < 3_000L -> JSONObject(remoteCore.toString()).put("source", "remote-link")
                else -> JSONObject(local.toString()).put("source", "indisponível")
            }
        }
    }

    private fun qualification(
        core: JSONObject,
        stft: Double?,
        rpm: Double?,
        coolant: Double?,
        closedLoop: Boolean,
        stftObservedAtMs: Long,
        rpmObservedAtMs: Long,
        closedLoopObservedAtMs: Long,
        stftStartedAtMs: Long,
        rpmStartedAtMs: Long,
        closedLoopStartedAtMs: Long,
        coolantObservedAtMs: Long,
    ): ObdLearningGate.Decision {
        val coreRpm = core.optDouble("rpm", 0.0)
        val petrol = core.optDouble("petrolMs", core.optDouble("petrol_ms", 0.0))
        val fuel = core.optString("fuelState", core.optString("fuel", core.optString("state", "")))
        val mp48Present = core.optBoolean("engineReady", false) && core.optString("source") != "indisponível"
        return ObdLearningGate.evaluate(
            ObdLearningGate.Input(
                mp48Present = mp48Present,
                mp48Fuel = fuel,
                manualFuel = settings.obdManualFuel,
                mp48Rpm = coreRpm,
                petrolInjectionMs = petrol,
                mp48ObservedAtMs = core.optLong("updatedAt", core.optLong("native_updated_at", 0L)),
                obdRpm = rpm,
                obdObservedAtMs = stftObservedAtMs,
                stftObservedAtMs = stftObservedAtMs,
                obdRpmObservedAtMs = rpmObservedAtMs,
                closedLoopObservedAtMs = closedLoopObservedAtMs,
                stftStartedAtMs = stftStartedAtMs,
                obdRpmStartedAtMs = rpmStartedAtMs,
                closedLoopStartedAtMs = closedLoopStartedAtMs,
                coolantObservedAtMs = coolantObservedAtMs,
                stft = stft,
                coolantC = coolant,
                closedLoop = closedLoop,
                maxRpmDifference = settings.obdMaxRpmDifference,
                maxTimeSkewMs = settings.obdMaxPairSkewMs,
                maxContextAgeMs = settings.obdMaxContextAgeMs,
                fuelTransition = fuel.uppercase().contains("TRANS"),
            ),
            settings.obdMinimumCoolantC,
        )
    }

    private fun collectQualified(
        core: JSONObject,
        fuel: String,
        stft: Double,
        ltft: Double?,
        speed: Double?,
        coolant: Double?,
        obdRpm: Double,
        now: Long,
    ): String {
        val rpm = core.optDouble("rpm", 0.0)
        val petrol = core.optDouble("petrolMs", core.optDouble("petrol_ms", 0.0))
        val key = "${nearest(rpm, RPM_BINS)}:${nearest(petrol, PETROL_MS_BINS)}"
        val result = conditionEngine.accept(
            ObdConditionEngine.Frame(
                observedAtMs = now,
                fuel = fuel,
                cellKey = key,
                mp48Rpm = rpm,
                petrolInjectionMs = petrol,
                obdRpm = obdRpm,
                stft = stft,
                ltft = ltft,
                speedKmh = speed,
                coolantC = coolant,
            ),
        )
        if (result !is ObdConditionEngine.Result.Accepted) {
            return when (result) {
                is ObdConditionEngine.Result.Forming -> "FORMANDO ${result.frameCount}/${result.requiredFrames}"
                is ObdConditionEngine.Result.Discarded -> {
                    evidenceLedger.recordRejection(result.reason)
                    "DESCARTADA: ${result.reason}"
                }
                else -> "PAUSADO"
            }
        }
        val condition = result.condition
        synchronized(stateLock) {
            val cell = localMaps.getValue(fuel).getOrPut(key) { CellStats() }
            cell.stft.update(condition.stft)
            if (condition.ltft != null) cell.ltft.update(condition.ltft)
            if (condition.speedKmh != null) cell.speed.update(condition.speedKmh)
            if (condition.coolantC != null) cell.coolant.update(condition.coolantC)
            cell.qualified += 1
            cell.updatedAt = now
        }
        if (now % 17L == 0L || localRevision() % 20L == 0L) save()
        return "CONDIÇÃO ACEITA (${condition.frameCount} frames)"
    }

    private fun updateTrip(core: JSONObject, speedKmh: Double, now: Long) {
        synchronized(stateLock) {
            if (tripStartedAt == 0L) tripStartedAt = now
            if (tripLastAt > 0L) {
                val dtHours = (now - tripLastAt).coerceIn(0L, 5_000L) / 3_600_000.0
                tripDistanceKm += speedKmh.coerceAtLeast(0.0) * dtHours
                val fuel = core.optString("fuelState", core.optString("fuel", "")).uppercase()
                if (fuel.contains("GNV") || fuel.contains("GAS")) {
                    val gasMs = core.optDouble("gasMs", core.optDouble("gas_ms_diagnostic", 0.0)).coerceAtLeast(0.0)
                    val rpm = core.optDouble("rpm", 0.0).coerceAtLeast(0.0)
                    val injections = rpm / 120.0 * settings.obdCylinderCount
                    tripEstimatedGasLiters += gasMs * injections * (dtHours * 3600.0) * settings.obdGasFlowCoefficient / 1000.0
                }
            }
            tripLastAt = now
        }
    }

    private fun tripJson(): JSONObject = JSONObject()
        .put("startedAt", tripStartedAt)
        .put("distanceKm", tripDistanceKm)
        .put("estimatedGasLiters", tripEstimatedGasLiters)
        .put("kmPerEstimatedLiter", if (tripEstimatedGasLiters > 0.0001) tripDistanceKm / tripEstimatedGasLiters else JSONObject.NULL)
        .put("estimateOnly", true)
        .put("coefficient", settings.obdGasFlowCoefficient)

    private fun readPid(sock: BluetoothSocket, command: String, pid: Int): List<Int>? =
        readPidTimed(sock, command, pid).bytes

    private fun supportsStandardPid(pid: Int): Boolean = synchronized(stateLock) {
        supportedStandardPids.isEmpty() || supportedStandardPids.contains(pid)
    }

    private fun readSupportedPidTimed(sock: BluetoothSocket, command: String, pid: Int): PidRead {
        if (!supportsStandardPid(pid)) {
            recordPidDiagnostic(command, false, 0L, 0L, "PID não anunciado pela ECU")
            return PidRead(null, 0L, 0L)
        }
        return readPidTimed(sock, command, pid)
    }

    private fun readPidTimed(sock: BluetoothSocket, command: String, pid: Int): PidRead {
        val startedAt = System.currentTimeMillis()
        return try {
            val response = elmCommand(sock, command, 1200L)
            val parsed = ElmResponseParser.mode01(response, pid)
            val observedAt = System.currentTimeMillis()
            recordPidDiagnostic(command, parsed != null, startedAt, observedAt, "")
            PidRead(parsed, startedAt, observedAt)
        } catch (error: Exception) {
            recordPidDiagnostic(command, false, startedAt, System.currentTimeMillis(), error.message ?: "Falha de leitura")
            throw error
        }
    }

    private fun pidAgeMs(read: PidRead, now: Long): Any =
        if (read.observedAtMs > 0L) (now - read.observedAtMs).coerceAtLeast(0L) else JSONObject.NULL

    private fun recordPidDiagnostic(command: String, responded: Boolean, startedAt: Long, observedAt: Long, error: String) = synchronized(stateLock) {
        val diagnostic = pidDiagnostics.getOrPut(command) { PidDiagnostic(command) }
        diagnostic.responded = responded
        diagnostic.latencyMs = if (startedAt > 0L && observedAt > 0L) (observedAt - startedAt).coerceAtLeast(0L) else 0L
        diagnostic.observedAt = observedAt
        diagnostic.error = error
        if (observedAt > 0L) {
            lastCommand = command
            lastCommandAt = observedAt
        }
        if (error.isNotBlank() && error != "PID não anunciado pela ECU") lastError = error
    }

    private fun diagnosticJsonLocked(): JSONObject {
        val now = System.currentTimeMillis()
        val pids = JSONArray()
        pidDiagnostics.values.forEach { diagnostic ->
            pids.put(
                JSONObject()
                    .put("command", diagnostic.command)
                    .put("responded", diagnostic.responded)
                    .put("latencyMs", diagnostic.latencyMs)
                    .put("observedAt", diagnostic.observedAt)
                    .put("error", diagnostic.error.ifBlank { JSONObject.NULL }),
            )
        }
        val elapsed = (now - pollWindowStartedAt).coerceAtLeast(1L)
        return JSONObject()
            .put("protocolMode", "ELM automático (ATSP0)")
            .put("sessionState", if (live.optBoolean("connected", false)) "LIVE" else "LAST_SESSION")
            .put("sessionStartedAt", currentSessionStartedAt)
            .put("lastSessionEndedAt", lastSessionEndedAt)
            .put("pollGroups", JSONObject()
                .put("critical", "0103,0106,010C a cada ciclo")
                .put("medium", "0107,010D,0104 a cada 2 ciclos")
                .put("fastContext", "010B,0110 a cada 4 ciclos")
                .put("slow", "0105,010F,0111,0142 a cada 8 ciclos")
                .put("verySlow", "012F a cada 20 ciclos"))
            .put("lastCommand", lastCommand.ifBlank { JSONObject.NULL })
            .put("lastCommandAt", lastCommandAt)
            .put("lastError", lastError.ifBlank { JSONObject.NULL })
            .put("lastCycleMs", lastCycleMs)
            .put("pollRateHz", pollWindowCycles * 1000.0 / elapsed)
            .put("supportedStandardPids", JSONArray(supportedStandardPids.map { "01%02X".format(it) }))
            .put("pids", pids)
    }

    private fun elmCommand(sock: BluetoothSocket, command: String, timeoutMs: Long): String {
        val output = sock.outputStream
        val input = sock.inputStream
        output.write((command.trim() + "\r").toByteArray(StandardCharsets.US_ASCII))
        output.flush()
        val buffer = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && running.get()) {
            while (input.available() > 0) {
                val char = input.read()
                if (char < 0) break
                if (char.toChar() == '>') return normalizeElm(buffer.toString())
                buffer.append(char.toChar())
            }
            try {
                Thread.sleep(8)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return normalizeElm(buffer.toString())
    }

    private fun normalizeElm(value: String): String = value.uppercase()
        .replace("SEARCHING...", "")
        .replace("\r", " ")
        .replace("\n", " ")
        .replace(">", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun fusedMap(fuel: String): MutableMap<String, CellStats> {
        val output = linkedMapOf<String, CellStats>()
        localMaps[fuel]?.forEach { (key, value) -> output[key] = CellStats.fromJson(value.toJson()) }
        return output
    }

    private fun validationJson(petrol: Map<String, CellStats>, gnv: Map<String, CellStats>): JSONObject {
        val output = JSONObject()
        (petrol.keys + gnv.keys).forEach { key ->
            val petrolCell = petrol[key]
            val gnvCell = gnv[key]
            val petrolSamples = petrolCell?.stft?.physicalSamples ?: 0L
            val gnvSamples = gnvCell?.stft?.physicalSamples ?: 0L
            val gnvStft = gnvCell?.stft?.mean
            val gasolineStft = petrolCell?.stft?.mean
            val gnvReady = gnvSamples >= settings.obdMinimumSamplesPerCell
            val gasolineReady = petrolSamples >= settings.obdMinimumSamplesPerCell
            val comparisonReady = gasolineReady && gnvReady
            val gasolineAdvisory = if (petrolSamples >= settings.obdMinimumSamplesPerCell) {
                ObdLearningGate.gasolineAdvisory(gasolineStft, settings.obdNeutralBandPct)
            } else null
            val status = when {
                !gnvReady -> "GNV_INSUFICIENTE"
                else -> ObdLearningGate.directGnvSignal(gnvStft!!, settings.obdNeutralBandPct)
            }
            output.put(
                key,
                JSONObject()
                    .put("gasoline", gasolineStft ?: JSONObject.NULL)
                    .put("gnv", gnvStft ?: JSONObject.NULL)
                    .put("gasolineAdvisory", gasolineAdvisory ?: JSONObject.NULL)
                    .put("gasolineSamples", petrolSamples)
                    .put("gnvSamples", gnvSamples)
                    .put("sameCell", true)
                    .put("sameEpoch", true)
                    .put("comparisonReady", comparisonReady)
                    .put("comparisonReason", when {
                        comparisonReady -> "MESMA_CELULA_E_EPOCA"
                        !gasolineReady -> "GASOLINA_INSUFICIENTE"
                        else -> "GNV_INSUFICIENTE"
                    })
                    .put("status", status),
            )
        }
        return output
    }

    private fun mapToJson(map: Map<String, CellStats>): JSONObject = JSONObject().also { output ->
        map.forEach { (key, value) -> output.put(key, value.toJson()) }
    }

    private fun snapshotLocalMaps(): JSONObject = JSONObject().also { snapshot ->
        localMaps.forEach { (fuel, map) -> snapshot.put(fuel, mapToJson(map)) }
        snapshot.put("independent", independentMap.persistenceJson())
    }

    private fun epochJson(epoch: ObdEvidenceLedger.Epoch): JSONObject = JSONObject()
        .put("mapEpochId", epoch.mapEpochId)
        .put("curveEpochId", epoch.curveEpochId)
        .put("mapReadbackHash", epoch.mapReadbackHash)
        .put("curveReadbackHash", epoch.curveReadbackHash)
        .put("startedAtMs", epoch.startedAtMs)

    private fun jsonToMap(json: JSONObject?): MutableMap<String, CellStats> = linkedMapOf<String, CellStats>().also { output ->
        json?.keys()?.forEach { key -> output[key] = CellStats.fromJson(json.optJSONObject(key)) }
    }

    private fun nearest(value: Double, bins: DoubleArray): Int = bins.indices.minByOrNull { abs(bins[it] - value) } ?: 0

    private fun localRevision(): Long = localMaps.values
        .flatMap { it.values }
        .sumOf { it.qualified + it.rejected + it.stft.updates }

    private fun save() = synchronized(stateLock) {
        try {
            storageFile.parentFile?.mkdirs()
            val local = JSONObject()
            localMaps.forEach { (fuel, map) -> local.put(fuel, mapToJson(map)) }
            val remote = JSONObject()
            remoteComponents.forEach { (device, maps) ->
                val item = JSONObject()
                maps.forEach { (fuel, map) -> item.put(fuel, mapToJson(map)) }
                remote.put(device, item)
            }
            val root = JSONObject()
                .put("format", "omegas-obd-store-v1")
                .put("local", local)
                .put("independent", independentMap.persistenceJson())
                .put("remote", remote)
                .put("evidenceLedger", evidenceLedger.toJson())
                .put("closedEpochMaps", JSONObject().also { closed ->
                    closedEpochMaps.forEach { (key, maps) -> closed.put(key, maps) }
                })
                .put("trip", tripJson())
            val temporary = File(storageFile.parentFile, storageFile.name + ".tmp")
            temporary.writeText(root.toString(), Charsets.UTF_8)
            if (!temporary.renameTo(storageFile)) {
                storageFile.writeText(root.toString(), Charsets.UTF_8)
                temporary.delete()
            }
        } catch (error: Exception) {
            log.add("WARN", "OBD", "Falha ao salvar mapas OBD: ${error.message}")
        }
    }

    private fun load() {
        synchronized(stateLock) {
            try {
                if (!storageFile.isFile) return@synchronized
                val root = JSONObject(storageFile.readText(Charsets.UTF_8))
                val local = root.optJSONObject("local") ?: JSONObject()
                for (fuel in listOf("GASOLINA", "GNV")) {
                    localMaps[fuel]?.clear()
                    localMaps[fuel]?.putAll(jsonToMap(local.optJSONObject(fuel)))
                }
                independentMap.load(root.optJSONObject("independent"))
                val remote = root.optJSONObject("remote") ?: JSONObject()
                remote.keys().forEach { device ->
                    val item = remote.optJSONObject(device) ?: JSONObject()
                    val maps = linkedMapOf<String, MutableMap<String, CellStats>>()
                    for (fuel in listOf("GASOLINA", "GNV")) maps[fuel] = jsonToMap(item.optJSONObject(fuel))
                    remoteComponents[device] = maps
                }
                evidenceLedger.load(root.optJSONObject("evidenceLedger"))
                closedEpochMaps.clear()
                root.optJSONObject("closedEpochMaps")?.keys()?.forEach { key ->
                    root.optJSONObject("closedEpochMaps")?.optJSONObject(key)?.let { closedEpochMaps[key] = it }
                }
                root.optJSONObject("trip")?.let { trip ->
                    tripStartedAt = trip.optLong("startedAt", 0L)
                    tripDistanceKm = trip.optDouble("distanceKm", 0.0)
                    tripEstimatedGasLiters = trip.optDouble("estimatedGasLiters", 0.0)
                }
            } catch (error: Exception) {
                log.add("WARN", "OBD", "Arquivo OBD ignorado: ${error.message}")
            }
        }
    }
}
