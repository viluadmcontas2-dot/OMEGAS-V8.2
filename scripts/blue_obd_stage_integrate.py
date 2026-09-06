#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
text = TARGET.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "    private val conditionEngine = ObdConditionEngine()\n    private val evidenceLedger = ObdEvidenceLedger()\n",
    "    private val conditionEngine = ObdConditionEngine()\n"
    "    private val evidenceLedger = ObdEvidenceLedger()\n"
    "    private val connectionState = ElmConnectionState(\n"
    "        connectTimeoutMs = 12_000L,\n"
    "        handshakeTimeoutMs = 12_000L,\n"
    "    )\n",
    "connection state field",
)

replace_once(
    "        .put(\"state\", \"DESATIVADO\")\n        .put(\"stft\", JSONObject.NULL)\n",
    "        .put(\"state\", \"DESATIVADO\")\n"
    "        .put(\"connectionStage\", ElmStage.IDLE.name)\n"
    "        .put(\"connectionErrorCode\", JSONObject.NULL)\n"
    "        .put(\"connectionDetail\", \"OBD local inativo\")\n"
    "        .put(\"retryable\", false)\n"
    "        .put(\"stft\", JSONObject.NULL)\n",
    "initial connection diagnostics",
)

old_connect = '''    @Synchronized
    fun connect(address: String): JSONObject {
        if (!hasBluetoothPermission()) {
            return JSONObject()
                .put("ok", false)
                .put("permissionRequired", true)
                .put("error", "Permissão Bluetooth necessária")
        }
        if (running.get()) return JSONObject().put("ok", true).put("state", "já conectado")
        val clean = address.trim().ifBlank { settings.obdDeviceAddress }
        if (clean.isBlank()) return JSONObject().put("ok", false).put("error", "Selecione um adaptador OBD pareado")
        settings.obdMode = "local"
        settings.obdDeviceAddress = clean
        running.set(true)
        synchronized(stateLock) {
            live.put("mode", "local")
                .put("state", "CONECTANDO")
                .put("connected", false)
                .put("device", clean)
                .put("permissionRequired", false)
        }
        worker.execute { connectionLoop(clean) }
        onStateChanged()
        return JSONObject().put("ok", true).put("state", "CONECTANDO")
    }
'''
new_connect = '''    @Synchronized
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
'''
replace_once(old_connect, new_connect, "connect function")

old_disconnect = '''    @Synchronized
    fun disconnect() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        synchronized(stateLock) {
            val now = System.currentTimeMillis()
            if (currentSessionStartedAt > 0L) lastSessionEndedAt = now
            currentSessionStartedAt = 0L
            live.put("connected", false)
                .put("sessionLive", false)
                .put("lastSessionEndedAt", lastSessionEndedAt)
                .put("state", if (settings.obdMode == "remote") "AGUARDANDO OMEGAS LINK" else "DESCONECTADO")
        }
        conditionEngine.reset()
        onStateChanged()
    }
'''
new_disconnect = '''    @Synchronized
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
'''
replace_once(old_disconnect, new_disconnect, "disconnect function")

marker = '''    @SuppressLint("MissingPermission")
    private fun connectionLoop(address: String) {
'''
helpers = '''    private fun publishConnectionStatus(
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

'''
if marker not in text:
    raise SystemExit("connectionLoop marker not found")
text = text.replace(marker, helpers + marker, 1)

old_loop = '''    @SuppressLint("MissingPermission")
    private fun connectionLoop(address: String) {
        var current: BluetoothSocket? = null
        try {
            if (!hasBluetoothPermission()) throw SecurityException("Permissão Bluetooth necessária")
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Bluetooth indisponível")
            if (!adapter.isEnabled) error("Ative o Bluetooth")
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            current = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket = current
            adapter.cancelDiscovery()
            current.connect()
            pollSequence = 0L
            cachedContext = ContextReadings()
            initializeElm(current)
            synchronized(stateLock) {
                currentSessionStartedAt = System.currentTimeMillis()
                live.put("connected", true)
                    .put("sessionLive", true)
                    .put("sessionStartedAt", currentSessionStartedAt)
                    .put("state", "CONECTADO")
                    .put("device", device.name ?: address)
                    .put("permissionRequired", false)
            }
            log.add("INFO", "OBD", "ELM327 conectado: ${device.name ?: address}")
            onStateChanged()
            while (running.get() && current.isConnected) pollCycle(current)
        } catch (error: SecurityException) {
            log.add("WARN", "OBD", "Permissão Bluetooth indisponível durante a conexão")
            synchronized(stateLock) {
                live.put("connected", false)
                    .put("state", "PERMISSÃO NECESSÁRIA")
                    .put("permissionRequired", true)
                    .put("reason", error.message ?: "Permissão Bluetooth necessária")
            }
        } catch (error: Exception) {
            log.add("WARN", "OBD", "Conexão encerrada: ${error.message}")
            synchronized(stateLock) {
                live.put("connected", false)
                    .put("state", "ERRO")
                    .put("reason", error.message ?: "Falha OBD")
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
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0").forEach { command ->
            val response = elmCommand(sock, command, if (command == "ATZ") 2500L else 1200L)
            if (response.contains("UNABLE TO CONNECT") && command == "ATSP0") error("ELM não encontrou protocolo OBD")
        }
        discoverStandardPids(sock)
    }
'''
new_loop = '''    @SuppressLint("MissingPermission")
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
'''
replace_once(old_loop, new_loop, "connection loop and handshake")

old_parser = '''            val parsed = if (response.contains("NO DATA") || response.contains("UNABLE TO CONNECT") || response.contains("STOPPED")) {
                null
            } else {
                val bytes = response.replace(Regex("[^0-9A-F]"), "").chunked(2).mapNotNull { it.toIntOrNull(16) }
                (0 until bytes.size - 2).firstOrNull { index -> bytes[index] == 0x41 && bytes[index + 1] == pid }
                    ?.let { index -> bytes.drop(index + 2) }
            }
'''
new_parser = '''            val parsed = ElmResponseParser.mode01(response, pid)
'''
replace_once(old_parser, new_parser, "ELM PID parser integration")

TARGET.write_text(text, encoding="utf-8")
print("BLUE_OBD_STAGE_INTEGRATION_PATCH=APPLIED")
