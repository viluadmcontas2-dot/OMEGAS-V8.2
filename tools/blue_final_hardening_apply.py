#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"required source seam missing: {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Distance legibility: no embedded CSS text below 10px; Map K gets a
# dedicated, larger 1280x720 contract for axis names, pins and cell values.
# ---------------------------------------------------------------------------
ui = ROOT / "app/src/main/assets/ui"
for css_path in ui.glob("*.css"):
    css = css_path.read_text(encoding="utf-8")
    css = re.sub(
        r"font-size\s*:\s*(7|8|9)(?:\.0+)?px",
        lambda m: "font-size:10px",
        css,
    )
    css_path.write_text(css, encoding="utf-8")

styles = read("app/src/main/assets/ui/styles.css")
if "/* OBD */" in styles and "/* SUGESTÕES E FERRAMENTAS */" in styles:
    a = styles.index("/* OBD */")
    b = styles.index("/* SUGESTÕES E FERRAMENTAS */", a)
    styles = styles[:a] + "/* OBD witness usa exclusivamente styles-witness-multimedia.css. */\n" + styles[b:]
write("app/src/main/assets/ui/styles.css", styles)

calibration_css = read("app/src/main/assets/ui/styles-calibration-obd.css")
calibration_css = calibration_css.replace(
    "/* Curva K e OBD — composição 1280×720 sem animação contínua e sem scroll principal. */",
    "/* Curva K — composição 1280×720 sem animação contínua e sem scroll principal. OBD witness possui stylesheet próprio. */",
)
if "/* OBD */" in calibration_css and "/* Não usar movimento contínuo nestas superfícies. */" in calibration_css:
    a = calibration_css.index("/* OBD */")
    b = calibration_css.index("/* Não usar movimento contínuo nestas superfícies. */", a)
    calibration_css = calibration_css[:a] + "/* OBD scanner legado removido. */\n\n" + calibration_css[b:]
calibration_css = re.sub(
    r"@media \(max-height:650px\)\{[^}]*\.curve-screen \.curve-chart\{min-height:260px\}[^}]*\}",
    "@media (max-height:650px){.curve-screen .curve-chart{min-height:260px}}",
    calibration_css,
)
write("app/src/main/assets/ui/styles-calibration-obd.css", calibration_css)

multimedia = read("app/src/main/assets/ui/styles-witness-multimedia.css")
marker = "/* DISTANCE_LEGIBILITY_1280X720 */"
if marker not in multimedia:
    multimedia += r'''

/* DISTANCE_LEGIBILITY_1280X720
 * A multimídia é vista à distância. 10px é piso absoluto para texto auxiliar;
 * eixos/pins do Mapa K são maiores porque orientam calibração em uso real.
 */
.map-screen .map-k-grid-with-axes{
  grid-template-columns:82px repeat(12,minmax(0,1fr));
  grid-template-rows:48px repeat(12,minmax(0,1fr));
}
.map-screen .map-axis-corner small{font-size:11px!important}
.map-screen .map-axis-header small{font-size:11px!important}
.map-screen .map-axis-corner b{font-size:12px!important}
.map-screen .map-axis-header b{font-size:12px!important}
.map-screen .map-rpm-header b{font-size:13px!important}
.map-screen .map-ms-header b{font-size:13px!important}
.map-screen .map-k-cell b{font-size:15px!important;line-height:1!important}
.map-screen .map-k-cell span{font-size:10px!important;line-height:1.05!important}
.map-screen .technical-row-note,
.map-screen .surface-toolbar,
.learning-screen .axis-top,
.learning-screen .axis-side,
.learning-screen .coverage-copy,
.learning-screen .trace-note,
.curve-screen .curve-axis,
.curve-screen .curve-legend{font-size:11px!important}
.learning-screen .cell-subvalue{font-size:10px!important}
@media (max-width:1100px){
  .map-screen .map-k-grid-with-axes{grid-template-columns:72px repeat(12,minmax(0,1fr));grid-template-rows:46px repeat(12,minmax(0,1fr))}
  .map-screen .map-rpm-header b,.map-screen .map-ms-header b{font-size:12px!important}
  .map-screen .map-k-cell b{font-size:14px!important}
}
'''
write("app/src/main/assets/ui/styles-witness-multimedia.css", multimedia)

# ---------------------------------------------------------------------------
# 2) Static HTML is only a shell for dashboard/OBD. The prior duplicate RPM,
# LTFT/scanner and OBD map must not remain shipped beneath runtime-owned views.
# ---------------------------------------------------------------------------
index = read("app/src/main/assets/ui/index.html")
if 'href="styles-witness-multimedia.css"' not in index:
    index = replace_required(
        index,
        '  <link rel="stylesheet" href="styles-calibration-obd.css">\n',
        '  <link rel="stylesheet" href="styles-calibration-obd.css">\n  <link rel="stylesheet" href="styles-witness-multimedia.css" data-witness-multimedia="true">\n',
        "static witness stylesheet",
    )
start = index.index('        <section class="screen active" data-screen="dashboard"')
end = index.index('        <section class="screen" data-screen="learning"', start)
index = index[:start] + '        <section class="screen active" data-screen="dashboard" aria-label="Agora"></section>\n\n' + index[end:]
start = index.index('        <section class="screen obd-screen" data-screen="obd"')
end = index.index('        <section id="suggestionDrawer"', start)
index = index[:start] + '        <section class="screen obd-screen" data-screen="obd" aria-label="OBD witness"></section>\n\n' + index[end:]
write("app/src/main/assets/ui/index.html", index)

# ---------------------------------------------------------------------------
# 3) Lean STFT-only OBD transport. Handshake/support discovery + PID 0106 are
# retained; old scanner context, maps, evidence ledger and second science leave
# the production runtime completely.
# ---------------------------------------------------------------------------
manager = r'''package com.omegas.prohub.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sidecar OBD estritamente observacional.
 *
 * O único sinal científico LIVE é STFT Bank 1 (Mode 01 PID 0106). Comandos AT
 * e 0100 existem apenas para transporte, handshake e descoberta de suporte.
 * Pareamento RPM/MAP/Petrol Inj., combustível e CalibrationStateID pertencem
 * ao serviço Blue/MP48; este manager não possui mapa, alvo K ou writer.
 */
class ObdAssistManager(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val paths: AppPaths,
    private val settings: AppSettings,
    private val log: RingLog,
    @Suppress("UNUSED_PARAMETER") private val localCoreProvider: () -> JSONObject,
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

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-obd").apply { isDaemon = true }
    }
    private val stateLock = Any()
    private var socket: BluetoothSocket? = null
    private var remoteLiveAt = 0L
    private var live = JSONObject()
        .put("connected", false)
        .put("mode", settings.obdMode)
        .put("state", "DESATIVADO")
        .put("connectionStage", ElmStage.IDLE.name)
        .put("connectionErrorCode", JSONObject.NULL)
        .put("connectionDetail", "OBD local inativo")
        .put("retryable", false)
        .put("stft", JSONObject.NULL)
        .put("quality", "SEM DADOS")
        .put("reason", "Ative uma fonte OBD")
    private val pidDiagnostics = linkedMapOf<String, PidDiagnostic>()
    private val supportedStandardPids = linkedSetOf<Int>()
    private var lastCommand = ""
    private var lastCommandAt = 0L
    private var lastError = ""
    private var lastCycleMs = 0L
    private var pollWindowStartedAt = 0L
    private var pollWindowCycles = 0
    private var currentSessionStartedAt = 0L
    private var lastSessionEndedAt = 0L
    private val connectionState = ElmConnectionState(
        connectTimeoutMs = 12_000L,
        handshakeTimeoutMs = 12_000L,
    )

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
                devices.put(
                    JSONObject()
                        .put("name", device.name ?: "ELM327")
                        .put("address", device.address)
                        .put("bonded", true)
                        .put("selected", device.address == settings.obdDeviceAddress)
                        .put("connected", device.address == connectedAddress),
                )
            }
            JSONObject()
                .put("permissionRequired", false)
                .put("enabled", adapter?.isEnabled == true)
                .put("devices", devices)
                .toString()
        } catch (error: SecurityException) {
            JSONObject().put("permissionRequired", true).put("error", error.message).put("devices", devices).toString()
        }
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
            when (normalized) {
                "off" -> live.put("state", "DESATIVADO").put("connected", false)
                "remote" -> live.put("state", "AGUARDANDO OMEGAS LINK").put("connected", false)
            }
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
            return JSONObject().put("ok", true).put("state", "já conectado").put("connectionStage", status.stage.name)
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
        return JSONObject().put("ok", true).put("state", "CONECTANDO").put("connectionStage", status.stage.name)
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
        onStateChanged()
    }

    fun close() {
        disconnect()
        worker.shutdownNow()
    }

    /** MP48 remoto é transportado pelo Link; o pareamento científico ocorre no serviço Blue. */
    fun updateRemoteCoreTelemetry(@Suppress("UNUSED_PARAMETER") payload: JSONObject) = Unit

    /** Aceita somente STFT remoto; campos de scanner de peers antigos são descartados. */
    fun acceptRemoteLive(payload: JSONObject) {
        val now = System.currentTimeMillis()
        val stft = payload.optDouble("stft", Double.NaN).takeIf { it.isFinite() }
        val observedAtMs = payload.optLong("observedAtMs", payload.optLong("updatedAt", now)).takeIf { it > 0L } ?: now
        val requestedAtMs = payload.optLong("requestedAtMs", observedAtMs).takeIf { it > 0L } ?: observedAtMs
        synchronized(stateLock) {
            remoteLiveAt = now
            if (settings.obdMode == "remote") {
                live = JSONObject()
                    .put("connected", stft != null)
                    .put("mode", "remote")
                    .put("state", if (stft != null) "REMOTO AO VIVO" else "AGUARDANDO STFT REMOTO")
                    .put("connectionStage", if (stft != null) ElmStage.LIVE.name else ElmStage.STFT_READY.name)
                    .put("stft", stft ?: JSONObject.NULL)
                    .put("quality", if (stft != null) "STFT_ONLY" else "SEM DADOS")
                    .put("fuelSource", "PENDING_MP48_PAIR")
                    .put("requestedAtMs", requestedAtMs)
                    .put("observedAtMs", observedAtMs)
                    .put("updatedAt", now)
            }
        }
        if (stft != null) {
            try {
                onLiveSample(
                    JSONObject()
                        .put("kind", "STFT_OBSERVATION")
                        .put("stft", stft)
                        .put("requestedAtMs", requestedAtMs)
                        .put("observedAtMs", observedAtMs)
                        .put("mode", "remote"),
                )
            } catch (_: Exception) {}
        }
        onStateChanged()
    }

    fun statusJson(): String = synchronized(stateLock) {
        val now = System.currentTimeMillis()
        if (settings.obdMode == "remote" && now - remoteLiveAt > 5_000L) {
            live.put("connected", false)
                .put("state", "AGUARDANDO OMEGAS LINK")
                .put("reason", "STFT remoto expirou")
        }
        JSONObject(live.toString())
            .put("mode", settings.obdMode)
            .put("deviceAddress", settings.obdDeviceAddress)
            .put("permissionRequired", !hasBluetoothPermission())
            .put("diagnostic", diagnosticJsonLocked())
            .toString()
    }

    private fun discoverStandardPids(sock: BluetoothSocket) {
        val bitmap = readPid(sock, "0100", 0x00)?.take(4).orEmpty()
        val discovered = linkedSetOf<Int>()
        if (bitmap.size == 4) {
            for (offset in 1..0x20) {
                val byteIndex = (offset - 1) / 8
                val bitIndex = 7 - ((offset - 1) % 8)
                if ((bitmap[byteIndex] and (1 shl bitIndex)) != 0) discovered += offset
            }
        }
        synchronized(stateLock) {
            supportedStandardPids.clear()
            supportedStandardPids.addAll(discovered)
        }
        log.add("INFO", "OBD", "Suporte Mode 01 descoberto por 0100; STFT=${discovered.contains(0x06)}")
    }

    private fun supportsStandardPid(pid: Int): Boolean = synchronized(stateLock) {
        supportedStandardPids.isEmpty() || supportedStandardPids.contains(pid)
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
            "NO DATA", "UNABLE TO CONNECT", "STOPPED", "BUS ERROR", "CAN ERROR", "ERROR",
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
                if (timed.stage == ElmStage.ERROR && timed.errorCode == "RFCOMM_TIMEOUT" && running.get() && socket === connectingSocket) {
                    publishConnectionStatus(timed, device = deviceLabel)
                    try { connectingSocket.close() } catch (_: Exception) {}
                    log.add("WARN", "OBD", "RFCOMM timeout: ${timed.detail}")
                    onStateChanged()
                }
            }, "omegas-obd-rfcomm-watchdog").apply { isDaemon = true; start() }

            adapter.cancelDiscovery()
            current.connect()
            connectionState.snapshot().takeIf { it.stage == ElmStage.ERROR }?.let { timed ->
                error(timed.detail.ifBlank { timed.errorCode })
            }

            val init = connectionState.enter(ElmStage.ELM_INIT, System.currentTimeMillis(), "Inicializando adaptador ELM")
            publishConnectionStatus(init, device = deviceLabel)
            onStateChanged()
            initializeElm(current)

            val liveStatus = connectionState.enter(ElmStage.LIVE, System.currentTimeMillis(), "STFT OBD ao vivo")
            synchronized(stateLock) {
                currentSessionStartedAt = System.currentTimeMillis()
                live.put("sessionLive", true).put("sessionStartedAt", currentSessionStartedAt).put("device", deviceLabel)
            }
            publishConnectionStatus(liveStatus, connected = true, device = deviceLabel)
            log.add("INFO", "OBD", "ELM327 conectado para STFT: $deviceLabel")
            onStateChanged()
            while (running.get() && current.isConnected) pollCycle(current)
            if (running.get() && connectionState.snapshot().stage == ElmStage.LIVE) {
                val lost = connectionState.fail(
                    "LIVE_LINK_LOST",
                    "Conexão ELM foi encerrada durante aquisição STFT",
                    System.currentTimeMillis(),
                    retryable = true,
                )
                publishConnectionStatus(lost, device = deviceLabel)
                log.add("WARN", "OBD", "Conexão ELM perdida após entrar em LIVE")
                onStateChanged()
            }
        } catch (error: SecurityException) {
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
                publishConnectionStatus(connectionState.enter(ElmStage.IDLE, System.currentTimeMillis(), "OBD local desconectado"), device = deviceLabel)
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
                live.put("connected", false).put("sessionLive", false).put("lastSessionEndedAt", lastSessionEndedAt)
            }
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
        val adaptiveTiming = elmCommand(sock, "ATAT1", 1200L)
        if (elmResponseFailed(adaptiveTiming)) log.add("WARN", "OBD", "ELM não aceitou ATAT1; seguindo sem adaptive timing")
        val identity = elmCommand(sock, "ATI", 1200L)
        if (elmResponseFailed(identity) || identity.trim().equals("ATI", ignoreCase = true)) error("ELM não respondeu ao ATI")

        val protocol = connectionState.enter(ElmStage.PROTOCOL, System.currentTimeMillis(), "Negociando protocolo OBD automático")
        publishConnectionStatus(protocol)
        onStateChanged()
        val protocolResponse = elmCommand(sock, "ATSP0", 1500L)
        if (elmResponseFailed(protocolResponse)) error("ELM não encontrou protocolo OBD")
        val protocolClock = connectionState.onClock(System.currentTimeMillis())
        if (protocolClock.stage == ElmStage.ERROR) error(protocolClock.detail)

        discoverStandardPids(sock)
        if (!supportsStandardPid(0x06)) error("ECU não anuncia PID 0106/STFT")
        val stftProbe = readPidTimed(sock, "0106", 0x06)
        if (stftProbe.bytes == null) error("PID 0106/STFT não respondeu")
        val ready = connectionState.enter(ElmStage.STFT_READY, System.currentTimeMillis(), "PID 0106/STFT disponível")
        publishConnectionStatus(ready)
        onStateChanged()
    }

    private fun pollCycle(sock: BluetoothSocket) {
        val cycleStartedAt = System.currentTimeMillis()
        val stftRead = readPidTimed(sock, "0106", 0x06)
        val rawStft = stftRead.bytes?.firstOrNull()
        val stft = rawStft?.let { ObdStftCodec.percent(it) }
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

        val payload = JSONObject()
            .put("connected", true)
            .put("mode", "local")
            .put("state", "CONECTADO")
            .put("connectionStage", ElmStage.LIVE.name)
            .put("stft", stft ?: JSONObject.NULL)
            .put("fuelSource", "PENDING_MP48_PAIR")
            .put("quality", if (stft != null) "STFT_ONLY" else "SEM DADOS")
            .put("reason", if (stft != null) "STFT adquirido; aguardando pareamento MP48" else "PID 0106/STFT sem resposta")
            .put("learningState", if (stft != null) "STFT_OBSERVED" else "PAUSADO")
            .put("conditionState", "AGUARDANDO_PAREAMENTO_MP48")
            .put("pollCycleMs", cycleMs)
            .put("requestedAtMs", stftRead.startedAtMs)
            .put("observedAtMs", stftRead.observedAtMs)
            .put("pidObservedAt", JSONObject().put("stft", stftRead.observedAtMs))
            .put("pidReadStartedAt", JSONObject().put("stft", stftRead.startedAtMs))
            .put("pidAgeMs", JSONObject().put("stft", pidAgeMs(stftRead, now)))
            .put("sessionLive", true)
            .put("sessionStartedAt", currentSessionStartedAt)
            .put("updatedAt", now)

        synchronized(stateLock) { live = payload }
        if (stft != null) {
            try {
                onLiveSample(
                    JSONObject()
                        .put("kind", "STFT_OBSERVATION")
                        .put("stft", stft)
                        .put("rawByte", rawStft)
                        .put("requestedAtMs", stftRead.startedAtMs)
                        .put("observedAtMs", stftRead.observedAtMs)
                        .put("pollCycleMs", cycleMs)
                        .put("mode", "local"),
                )
            } catch (_: Exception) {}
        }
        onStateChanged()
        try {
            Thread.sleep(settings.obdPollIntervalMs.coerceIn(150L, 3000L))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readPid(sock: BluetoothSocket, command: String, pid: Int): List<Int>? = readPidTimed(sock, command, pid).bytes

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
        if (error.isNotBlank()) lastError = error
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
            .put("scientificPolling", "0106/STFT somente")
            .put("supportDiscovery", "0100 somente durante handshake")
            .put("sessionState", if (live.optBoolean("connected", false)) "LIVE" else "LAST_SESSION")
            .put("sessionStartedAt", currentSessionStartedAt)
            .put("lastSessionEndedAt", lastSessionEndedAt)
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
                Thread.sleep(8L)
            } catch (error: InterruptedException) {
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
}
'''
write("app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt", manager)

# ---------------------------------------------------------------------------
# 4) CalibrationStateID comes from canonical Blue coordinator revisions.
# ---------------------------------------------------------------------------
blue_access = read("app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt")
if "fun TelemetryForegroundService.blueCalibrationStateId()" not in blue_access:
    seam = "fun TelemetryForegroundService.blueCalibrationStateJson(): String = try {\n"
    helper = r'''fun TelemetryForegroundService.blueCalibrationStateId(): String = try {
    val state = BlueCalibrationRegistry.get(this).stateJson()
    if (!state.optBoolean("ready", false)) "" else {
        val revision = state.optJSONObject("revision") ?: JSONObject()
        "map-${revision.optInt("mapK", 0)}:curve-${revision.optInt("curveK", 0)}"
    }
} catch (_: Exception) {
    ""
}

'''
    blue_access = replace_required(blue_access, seam, helper + seam, "Blue calibration state id helper")
write("app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt", blue_access)

service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
service = service.replace('                obd?.recordConfirmedAdjustment("MAP_K", payload)\n', '')
service = service.replace('                obd?.recordConfirmedAdjustment("K_FACTOR", payload)\n', '')
service = replace_required(
    service,
    "        learningArchive = LearningArchiveManager(paths, settings, runtime, obd, kWriter, log)\n",
    "        learningArchive = LearningArchiveManager(paths, settings, runtime, kWriter, log)\n",
    "LearningArchive constructor",
)
service = service.replace('    fun obdMapsJson(): String = obd?.mapsJson() ?: "{}"\n', '')
service = service.replace('    fun setObdManualFuel(fuel: String): String = obd?.setManualFuel(fuel)?.toString() ?: "{}"\n', '')
old_epoch = '''        val epoch = try {
            JSONObject(obd?.mapsJson() ?: "{}").optJSONObject("epoch")
        } catch (_: Exception) {
            null
        } ?: return
        val mapEpochId = epoch.optString("mapEpochId").trim()
        val curveEpochId = epoch.optString("curveEpochId").trim()
        if (mapEpochId.isBlank() || curveEpochId.isBlank()) return
        val calibrationState = "$mapEpochId:$curveEpochId"
'''
new_epoch = '''        val calibrationState = blueCalibrationStateId()
        if (calibrationState.isBlank()) return
'''
service = replace_required(service, old_epoch, new_epoch, "witness calibration state source")
write("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt", service)

# ---------------------------------------------------------------------------
# 5) Portable learning + Link keep Blue evidence/history and live STFT only.
# No OBD map component survives import/export/sync.
# ---------------------------------------------------------------------------
archive = read("app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt")
archive = archive.replace("import com.omegas.prohub.obd.ObdAssistManager\n", "")
archive = archive.replace("    private val obd: ObdAssistManager?,\n", "")
archive = archive.replace('            .put("obd", (obd?.exportLocalState(settings.deviceId) ?: org.json.JSONObject()))\n', '')
old_obd_import = '''        val obdResult = root.optJSONObject("obd")?.let { (obd?.importPortableState(it, settings.deviceId) ?: org.json.JSONObject().put("ok", true)) }
            ?: JSONObject().put("ok", true).put("ignored", true)
'''
archive = archive.replace(old_obd_import, '')
archive = archive.replace('        val ok = learningResult.optBoolean("ok") &&\n            obdResult.optBoolean("ok", true) && historyResult.optBoolean("ok", true)\n',
                          '        val ok = learningResult.optBoolean("ok") && historyResult.optBoolean("ok", true)\n')
archive = archive.replace('            "Importação .omegas: aprendizado=${learningResult.optBoolean("ok")} " +\n                "OBD=${obdResult.optBoolean("ok", true)} histórico=${historyResult.optBoolean("ok", true)}",\n',
                          '            "Importação .omegas: aprendizado=${learningResult.optBoolean("ok")} histórico=${historyResult.optBoolean("ok", true)}",\n')
archive = archive.replace('            .put("obd", obdResult)\n', '')
write("app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt", archive)

link = read("app/src/main/java/com/omegas/prohub/link/OmegasLinkManager.kt")
link = re.sub(r'\n\s*\.put\("obdComponent", \(obd\?\.exportLocalState\(settings\.deviceId\) \?: org\.json\.JSONObject\(\)\)\)', '', link)
link = link.replace('        root.optJSONObject("obdComponent")?.let { obd?.mergeRemoteState(it) }\n', '')
write("app/src/main/java/com/omegas/prohub/link/OmegasLinkManager.kt", link)

bridge = read("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt")
bridge = bridge.replace('    @JavascriptInterface fun getObdMaps(): String = activity?.serviceOrNull()?.obdMapsJson() ?: "{}"\n', '')
bridge = bridge.replace('    @JavascriptInterface fun setObdManualFuel(fuel: String): String = activity?.serviceOrNull()?.setObdManualFuel(fuel) ?: unavailable()\n', '')
write("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt", bridge)

api = read("app/src/main/assets/ui/core/native-api.js")
if "  function demoObdMaps() {" in api:
    a = api.index("  function demoObdMaps() {")
    b = api.index("  function demoToleranceSettings()", a)
    api = api[:a] + api[b:]
api = re.sub(r'^\s*obdMaps\(\) \{[^\n]*\}\n', '', api, flags=re.MULTILINE)
api = re.sub(r'^\s*setObdManualFuel\(fuel\) \{[^\n]*\}\n', '', api, flags=re.MULTILINE)
write("app/src/main/assets/ui/core/native-api.js", api)

# ---------------------------------------------------------------------------
# 6) Remove settings that only existed for scanner/map learning. Keep transport
# mode/device/autoconnect/poll interval only.
# ---------------------------------------------------------------------------
settings = read("app/src/main/java/com/omegas/prohub/settings/AppSettings.kt")
settings = re.sub(
    r'\n\s*/\*\* Declaração do operador[^\n]*\n\s*var obdManualFuel: String\n\s*get\(\) = [^\n]*\n\s*set\(value\) = [^\n]*\n',
    '\n',
    settings,
)
start_token = "    var obdMinimumCoolantC: Double\n"
end_token = "    var gnvCylinderCapacityM3: Float\n"
if start_token in settings:
    a = settings.index(start_token)
    b = settings.index(end_token, a)
    settings = settings[:a] + settings[b:]
for key in [
    "obdManualFuel", "obdMinimumCoolantC", "obdMaxRpmDifference", "obdMaxPairSkewMs",
    "obdMaxContextAgeMs", "obdMinimumSamplesPerCell", "obdNeutralBandPct",
    "obdDivergenceBandPct", "obdGasFlowCoefficient", "obdCylinderCount",
]:
    settings = re.sub(rf'\n\s*\.put\("{key}", {key}\)', '', settings)
write("app/src/main/java/com/omegas/prohub/settings/AppSettings.kt", settings)

# ---------------------------------------------------------------------------
# 7) Retire source/tests/assets whose only purpose was the old OBD second map.
# ---------------------------------------------------------------------------
for rel in [
    "app/src/main/java/com/omegas/prohub/obd/ObdConditionEngine.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdEvidenceLedger.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdIndependentEvidenceMap.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdLearningGate.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdConditionEngineTest.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdEvidenceLedgerTest.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdIndependentEvidenceMapTest.kt",
    "app/src/main/assets/ui/components/floating-telemetry.js",
    "app/src/main/assets/ui/styles-floating-telemetry.css",
    "tests/ui/floating-telemetry.test.cjs",
    "tests/ui/obd-independent-map.test.cjs",
]:
    path = ROOT / rel
    if path.exists():
        path.unlink()

# ---------------------------------------------------------------------------
# 8) Replace stale contracts that still demanded the scanner/old epoch source.
# ---------------------------------------------------------------------------
write("tests/test_blue_obd_auxiliary_contract.py", r'''from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

for rel in [
    "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt",
    "app/src/main/assets/ui/screens/obd.js",
]:
    assert (ROOT / rel).exists(), f"required optional OBD witness missing: {rel}"

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in ["android.hardware.bluetooth", "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"]:
    assert token in manifest, f"OBD Bluetooth surface missing: {token}"

index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert 'data-route="obd"' in index and 'data-screen="obd"' in index

bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")
for token in ["getObdStatus", "listObdDevices", "connectObd", "disconnectObd"]:
    assert token in bridge, f"OBD bridge capability missing: {token}"
for forbidden in ["getObdMaps", "setObdManualFuel"]:
    assert forbidden not in bridge

obd = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt").read_text(encoding="utf-8")
assert "STFT Bank 1" in obd and '"0106"' in obd
for forbidden in ["KWriteManager", "startKWrite(", "startKFactorWrite(", "ObdLearningGate", "ObdEvidenceLedger"]:
    assert forbidden not in obd

for rel in ["app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt", "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt"]:
    text = (ROOT / rel).read_text(encoding="utf-8").lower()
    assert "obd" not in text, f"Blue core gained an OBD dependency: {rel}"

print("BLUE_OBD_AUXILIARY_CONTRACT=PASS")
''')

write("tests/test_blue_obd_stft_only_contract.py", r'''from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"


def section(text: str, start: str, end: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]


def main() -> None:
    text = MANAGER.read_text(encoding="utf-8")
    poll = section(text, "    private fun pollCycle(sock: BluetoothSocket) {", "    private fun readPid(sock: BluetoothSocket")
    required = [
        'readPidTimed(sock, "0106", 0x06)', "ObdStftCodec.percent", "onLiveSample",
        '"STFT_OBSERVATION"', '"PENDING_MP48_PAIR"', 'put("requestedAtMs"', 'put("observedAtMs"',
    ]
    missing = [token for token in required if token not in poll]
    assert not missing, f"STFT witness handoff metadata missing: {missing}"
    for forbidden in ['"0103"', '"010C"', "qualification(", "collectQualified(", "independentMap.observe("]:
        assert forbidden not in poll, f"live OBD acquisition is not STFT-only: {forbidden}"
    assert 'readPid(sock, "0100", 0x00)' in text
    print("BLUE_OBD_STFT_ONLY_CONTRACT=PASS")


if __name__ == "__main__":
    main()
''')

write("tests/test_blue_obd_witness_runtime_pairing_contract.py", r'''from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
ACCESS = ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"


def main() -> None:
    text = SERVICE.read_text(encoding="utf-8")
    access = ACCESS.read_text(encoding="utf-8")
    required = [
        "ObdWitnessEngine", "pairObdStftWitness(sample)", "telemetryStore.nearestFrame(observedAtMs, 250L)",
        "ObdWitnessSample(", "obdWitnessEngine.observe", 'optDouble("rpm"', 'optDouble("map_bar"',
        'optDouble("petrol_ms"', 'optString("fuel"', 'optLong("skew_ms"', "blueCalibrationStateId()",
    ]
    missing = [token for token in required if token not in text]
    assert not missing, f"OBD witness runtime pairing seam missing: {missing}"
    assert '"map-${revision.optInt("mapK", 0)}:curve-${revision.optInt("curveK", 0)}"' in access

    start = text.index("    private fun pairObdStftWitness(sample: JSONObject)")
    end = text.index("\n    private fun ", start + 10)
    pairing = text[start:end]
    for forbidden in ["coolant", "water", "load", "throttle", "maf", "speed", "iat", "ltft", "mapsJson", "mapEpochId", "curveEpochId"]:
        assert forbidden.lower() not in pairing.lower(), f"forbidden variable/legacy state leaked into witness pairing: {forbidden}"
    print("BLUE_OBD_WITNESS_RUNTIME_PAIRING_CONTRACT=PASS")


if __name__ == "__main__":
    main()
''')

# Strengthen the hard-cut contract with stale settings/API names too.
hardcut = read("tests/test_blue_obd_legacy_cut_contract.py")
if "obdManualFuel" not in hardcut:
    hardcut += r'''

settings_text = read("app/src/main/java/com/omegas/prohub/settings/AppSettings.kt")
for forbidden in [
    "obdManualFuel", "obdMinimumCoolantC", "obdMaxRpmDifference", "obdMaxPairSkewMs",
    "obdMaxContextAgeMs", "obdMinimumSamplesPerCell", "obdNeutralBandPct",
    "obdDivergenceBandPct", "obdGasFlowCoefficient", "obdCylinderCount",
]:
    assert forbidden not in settings_text, f"configuração OBD legada ainda embarcada: {forbidden}"
'''
write("tests/test_blue_obd_legacy_cut_contract.py", hardcut)

print("BLUE_FINAL_HARDENING_APPLY=OK")
