package com.omegas.prohub.link

import com.omegas.prohub.obd.ObdAssistManager
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sincronização local entre dois aparelhos, sem internet e sem usar Bluetooth.
 * Descoberta por UDP e troca autenticada por código sobre TCP.
 */
class OmegasLinkManager(
    private val settings: AppSettings,
    private val log: RingLog,
    private val usbConnected: () -> Boolean,
    private val coreTelemetry: () -> JSONObject,
    private val exportLearning: () -> JSONObject,
    private val mergeLearning: (JSONObject) -> JSONObject,
    private val exportHistory: () -> JSONObject,
    private val mergeHistory: (JSONObject) -> JSONObject,
    private val obd: ObdAssistManager?,
    private val onStateChanged: () -> Unit,
    private val exportAutoCalContext: () -> JSONObject = { JSONObject() },
    private val mergeAutoCalContext: (JSONObject) -> JSONObject = { JSONObject().put("ok", true).put("accepted", false).put("reason", "context-only") },
    private val exportNativeReceipts: () -> JSONObject = { JSONObject().put("receipts", org.json.JSONArray()) },
) {
    data class Peer(
        val deviceId: String,
        var name: String,
        var address: InetAddress,
        var port: Int,
        var usb: Boolean,
        var obdConnected: Boolean,
        var role: String,
        var controlEpoch: Long,
        var lastSeenAt: Long,
    )

    private val running = AtomicBoolean(false)
    private val executor = Executors.newScheduledThreadPool(5) { r -> Thread(r, "omegas-link").apply { isDaemon = true } }
    private val peers = ConcurrentHashMap<String, Peer>()
    private val peerLearningRevisions = ConcurrentHashMap<String, Long>()
    private val peerAckedLocalLearningRevisions = ConcurrentHashMap<String, Long>()
    private var server: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var beaconTask: ScheduledFuture<*>? = null
    private var liveTask: ScheduledFuture<*>? = null
    private var syncTask: ScheduledFuture<*>? = null
    @Volatile private var lastError = ""
    @Volatile private var lastSyncAt = 0L
    @Volatile private var lastLiveAt = 0L
    @Volatile private var pendingChanges = false
    @Volatile private var controlOwnerId = ""
    @Volatile private var controlEpoch = settings.linkControlEpoch
    @Volatile private var activeRole = "OFF"
    @Volatile private var bytesSent = 0L
    @Volatile private var bytesReceived = 0L
    @Volatile private var lastLocalUsb = false
    @Volatile private var lastPeerId = ""
    @Volatile private var lastBeaconAt = 0L

    fun start(): Boolean {
        if (running.get()) return true
        if (!settings.linkEnabled) return false
        return try {
            running.set(true)
            startServer()
            startDiscovery()
            beaconTask = executor.scheduleWithFixedDelay(::sendBeacon, 0, 5, TimeUnit.SECONDS)
            scheduleNextLiveFrame(1_000L)
            syncTask = executor.scheduleWithFixedDelay(::syncBestPeer, 3, 10, TimeUnit.SECONDS)
            lastLocalUsb = usbConnected()
            resolveRole()
            log.add("INFO", "OMEGAS LINK", "Ativo na rede local • aparelho ${settings.deviceName}")
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao iniciar OMEGAS Link"
            running.set(false)
            log.add("WARN", "OMEGAS LINK", lastError)
            false
        }
    }

    fun stop() {
        running.set(false)
        beaconTask?.cancel(true); liveTask?.cancel(true); syncTask?.cancel(true)
        try { server?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        server = null; discoverySocket = null; peers.clear(); activeRole = "OFF"; lastPeerId = ""
        onStateChanged()
    }

    fun close() { stop(); executor.shutdownNow() }

    fun applySettings(): JSONObject {
        stop()
        return if (settings.linkEnabled && start()) JSONObject().put("ok", true).put("enabled", true)
        else JSONObject().put("ok", true).put("enabled", false).put("error", lastError)
    }

    fun statusJson(): String {
        prunePeers(); resolveRole()
        val peer = bestPeer()
        return JSONObject()
            .put("enabled", settings.linkEnabled)
            .put("running", running.get())
            .put("deviceId", settings.deviceId)
            .put("deviceName", settings.deviceName)
            .put("pairCode", settings.linkPairCode)
            .put("rolePreference", settings.linkRolePreference)
            .put("activeRole", activeRole)
            .put("controlOwnerId", controlOwnerId)
            .put("controlEpoch", controlEpoch)
            .put("canWrite", canWriteLocally())
            .put("canAssume", usbConnected() && controlOwnerId != settings.deviceId)
            .put("peerConnected", peer != null)
            .put("peer", peer?.let { peerJson(it) } ?: JSONObject.NULL)
            .put("peerCount", peers.size)
            .put("lastSyncAt", lastSyncAt)
            .put("lastLiveAt", lastLiveAt)
            .put("pendingChanges", pendingChanges)
            .put("bytesSent", bytesSent)
            .put("bytesReceived", bytesReceived)
            .put("lastError", lastError)
            .put("smartMode", true)
            .put("automaticHandoff", true)
            .put("idleTrafficReduced", true)
            .put("incrementalSync", true)
            .put("peerLearningRevision", peer?.let { peerLearningRevisions[it.deviceId] } ?: 0L)
            .toString()
    }

    fun hasConnectedPeer(): Boolean = running.get() && bestPeer() != null

    /** Chamado quando MP48/OBD muda de estado. O papel acompanha o hardware físico. */
    fun onLocalCapabilitiesChanged() {
        val nowUsb = usbConnected()
        if (nowUsb != lastLocalUsb) {
            lastLocalUsb = nowUsb
            pendingChanges = true
            reconcileControl("mudança física do MP48")
            bestPeer()?.let { peer -> executor.execute { syncWith(peer) } }
        } else {
            reconcileControl("atualização de capacidade")
        }
        onStateChanged()
    }

    fun markDataChanged(reason: String = "novos dados") {
        pendingChanges = true
        log.add("INFO", "OMEGAS LINK", "Sincronização pendente • $reason")
    }

    fun claimMain(): JSONObject {
        if (!usbConnected()) return JSONObject().put("ok", false).put("error", "Conecte o MP48 neste aparelho antes de assumir")
        controlEpoch = maxOf(System.currentTimeMillis(), controlEpoch + 1)
        controlOwnerId = settings.deviceId
        settings.linkControlEpoch = controlEpoch
        activeRole = "PRINCIPAL"
        bestPeer()?.let { sendMessage(it, JSONObject().put("type", "claim").put("epoch", controlEpoch).put("owner", settings.deviceId)) }
        log.add("INFO", "OMEGAS LINK", "Controle principal assumido neste aparelho")
        onStateChanged()
        return JSONObject().put("ok", true).put("activeRole", activeRole).put("epoch", controlEpoch)
    }

    fun releaseMain(): JSONObject {
        if (controlOwnerId == settings.deviceId) {
            controlEpoch = maxOf(System.currentTimeMillis(), controlEpoch + 1)
            controlOwnerId = ""
            settings.linkControlEpoch = controlEpoch
            bestPeer()?.let { sendMessage(it, JSONObject().put("type", "release").put("epoch", controlEpoch).put("owner", settings.deviceId)) }
        }
        resolveRole(); onStateChanged()
        return JSONObject().put("ok", true).put("activeRole", activeRole)
    }

    fun canWriteLocally(): Boolean {
        if (!settings.linkEnabled || !running.get() || bestPeer() == null) return usbConnected()
        return usbConnected() && (controlOwnerId.isBlank() || controlOwnerId == settings.deviceId) && activeRole == "PRINCIPAL"
    }

    fun syncNow(): JSONObject {
        val peer = bestPeer() ?: return JSONObject().put("ok", false).put("error", "Nenhum aparelho pareado encontrado na rede")
        pendingChanges = true
        executor.execute { syncWith(peer) }
        return JSONObject().put("ok", true).put("state", "SINCRONIZANDO").put("peer", peer.name)
    }

    private fun startServer() {
        val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(settings.linkDataPort)); soTimeout = 5_000 }
        server = s
        executor.execute {
            while (running.get()) {
                try {
                    val client = s.accept().apply { soTimeout = 8_000 }
                    executor.execute { handleClient(client) }
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    if (running.get()) lastError = e.message ?: "Servidor Link interrompido"
                }
            }
        }
    }

    private fun startDiscovery() {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = 5_000
            bind(InetSocketAddress(settings.linkDiscoveryPort))
        }
        discoverySocket = socket
        executor.execute {
            val buffer = ByteArray(8192)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    receiveBeacon(text, packet.address)
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    if (running.get()) lastError = e.message ?: "Falha de descoberta"
                }
            }
        }
    }

    private fun sendBeacon() {
        if (!running.get()) return
        val now = System.currentTimeMillis()
        val localObd = try { JSONObject((obd?.statusJson() ?: "{}")).optBoolean("connected") } catch (_: Exception) { false }
        val busy = usbConnected() || localObd || bestPeer() != null
        val minimumInterval = if (busy) 5_000L else 15_000L
        if (now - lastBeaconAt < minimumInterval) return
        lastBeaconAt = now
        val payload = JSONObject()
            .put("protocol", "OMEGAS_LINK_V1")
            .put("pairHash", pairHash())
            .put("deviceId", settings.deviceId)
            .put("name", settings.deviceName)
            .put("port", settings.linkDataPort)
            .put("usb", usbConnected())
            .put("obd", JSONObject((obd?.statusJson() ?: "{}")).optBoolean("connected"))
            .put("role", activeRole)
            .put("controlEpoch", controlEpoch)
            .put("controlOwner", controlOwnerId)
        val bytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val socket = discoverySocket ?: return
        broadcastAddresses().forEach { address ->
            try { socket.send(DatagramPacket(bytes, bytes.size, address, settings.linkDiscoveryPort)); bytesSent += bytes.size }
            catch (_: Exception) {}
        }
        prunePeers(); resolveRole()
    }

    private fun receiveBeacon(text: String, address: InetAddress) {
        val json = try { JSONObject(text) } catch (_: Exception) { return }
        if (json.optString("protocol") != "OMEGAS_LINK_V1" || json.optString("pairHash") != pairHash()) return
        val id = json.optString("deviceId")
        if (id.isBlank() || id == settings.deviceId) return
        val trusted = settings.linkTrustedPeerId
        if (trusted.isNotBlank() && trusted != id) return
        val isNewPeer = !peers.containsKey(id)
        val peer = peers.compute(id) { _, existing ->
            (existing ?: Peer(id, json.optString("name", "Outro aparelho"), address, json.optInt("port", settings.linkDataPort), false, false, "COMPANHEIRO", 0L, 0L)).apply {
                name = json.optString("name", name); this.address = address; port = json.optInt("port", port)
                usb = json.optBoolean("usb"); obdConnected = json.optBoolean("obd"); role = json.optString("role", role)
                controlEpoch = json.optLong("controlEpoch", controlEpoch); lastSeenAt = System.currentTimeMillis()
            }
        } ?: return
        val remoteEpoch = json.optLong("controlEpoch", 0L)
        val remoteOwner = json.optString("controlOwner")
        adoptControl(remoteEpoch, remoteOwner)
        if (settings.linkTrustedPeerId.isBlank()) settings.linkTrustedPeerId = peer.deviceId
        if (isNewPeer || lastPeerId != peer.deviceId) {
            lastPeerId = peer.deviceId
            pendingChanges = true
            log.add("INFO", "OMEGAS LINK", "${peer.name} encontrado; preparando fusão automática")
            executor.execute { syncWith(peer) }
        }
        reconcileControl("estado recebido do companheiro")
        onStateChanged()
    }

    private fun scheduleNextLiveFrame(delayMs: Long) {
        if (!running.get() || executor.isShutdown) return
        liveTask?.cancel(false)
        liveTask = executor.schedule({
            try { sendLiveFrame() } finally {
                if (running.get()) scheduleNextLiveFrame(liveIntervalMs())
            }
        }, delayMs.coerceAtLeast(500L), TimeUnit.MILLISECONDS)
    }

    private fun liveIntervalMs(): Long {
        val peer = bestPeer() ?: return 10_000L
        val localObd = try { JSONObject((obd?.statusJson() ?: "{}")).optBoolean("connected") } catch (_: Exception) { false }
        return if (usbConnected() || localObd || peer.usb || peer.obdConnected) 1_000L else 10_000L
    }

    private fun sendLiveFrame() {
        val peer = bestPeer() ?: return
        val obdConnected = try { JSONObject((obd?.statusJson() ?: "{}")).optBoolean("connected") } catch (_: Exception) { false }
        if (!usbConnected() && !obdConnected && !peer.usb && !peer.obdConnected) return
        val payload = JSONObject()
            .put("type", "live")
            .put("from", settings.deviceId)
            .put("epoch", controlEpoch)
            .put("owner", controlOwnerId)
            .put("usb", usbConnected())
            .put("role", activeRole)
            .put("core", coreTelemetry())
            .put("obd", JSONObject((obd?.statusJson() ?: "{}")))
        val response = sendMessage(peer, payload) ?: return
        applyIncoming(response, peer)
        lastLiveAt = System.currentTimeMillis()
    }

    private fun syncBestPeer() {
        val peer = bestPeer() ?: return
        val activeSession = usbConnected() || peer.usb || peer.obdConnected ||
            try { JSONObject((obd?.statusJson() ?: "{}")).optBoolean("connected") } catch (_: Exception) { false }
        val periodicDue = activeSession && System.currentTimeMillis() - lastSyncAt >= 60_000L
        if (pendingChanges || lastSyncAt == 0L || periodicDue) syncWith(peer)
    }

    private fun syncWith(peer: Peer) {
        try {
            val hadPendingChanges = pendingChanges
            pendingChanges = true
            val learning = exportLearning()
            val localLearningRevision = learning.optLong("componentRevision", 0L)
            val peerKnownRevision = peerLearningRevisions[peer.deviceId] ?: -1L
            val peerAckedLocalRevision = peerAckedLocalLearningRevisions[peer.deviceId] ?: -1L
            val kHistory = exportHistory()
            val request = JSONObject()
                .put("type", "sync")
                .put("from", settings.deviceId)
                .put("name", settings.deviceName)
                .put("epoch", controlEpoch)
                .put("owner", controlOwnerId)
                .put("syncManifest", syncManifest(learning, kHistory, peerKnownRevision))
                .put("obdComponent", (obd?.exportLocalState(settings.deviceId) ?: org.json.JSONObject()))
                .put("kHistory", kHistory)
                .put("autoCalContext", exportAutoCalContext())
                .put("nativeReceipts", exportNativeReceipts())
                .put("core", coreTelemetry())
                .put("obd", JSONObject((obd?.statusJson() ?: "{}")));
            if (hadPendingChanges || peerAckedLocalRevision < localLearningRevision) request.put("learning", learning)
            val response = sendMessage(peer, request) ?: error("Sem resposta do companheiro")
            applyIncoming(response, peer)
            lastSyncAt = System.currentTimeMillis(); pendingChanges = false; lastError = ""
            log.add("INFO", "OMEGAS LINK", "Sincronizado com ${peer.name}")
            onStateChanged()
        } catch (e: Exception) {
            lastError = e.message ?: "Falha de sincronização"
            log.add("WARN", "OMEGAS LINK", lastError)
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val request = readFrame(client) ?: return
                if (request.optString("pairHash") != pairHash()) {
                    writeFrame(client, JSONObject().put("ok", false).put("error", "Código de pareamento inválido")); return
                }
                val id = request.optString("from")
                if (id.isBlank() || id == settings.deviceId) return
                if (settings.linkTrustedPeerId.isBlank()) settings.linkTrustedPeerId = id
                if (settings.linkTrustedPeerId != id) {
                    writeFrame(client, JSONObject().put("ok", false).put("error", "Aparelho não autorizado")); return
                }
                val peer = peers[id]
                val response = when (request.optString("type")) {
                    "claim" -> { adoptControl(request.optLong("epoch"), request.optString("owner")); baseResponse("claim-ack") }
                    "release" -> { adoptControl(request.optLong("epoch"), ""); baseResponse("release-ack") }
                    "live" -> { applyIncoming(request, peer); baseResponse("live-ack") }
                    "sync" -> {
                        applyIncoming(request, peer)
                        val requesterRevision = request.optJSONObject("syncManifest")?.optLong("learningRevision", -1L) ?: -1L
                        val requesterKnowsLocalRevision = request.optJSONObject("syncManifest")?.optLong("knownPeerLearningRevision", -1L) ?: -1L
                        val localLearning = exportLearning()
                        val response = baseResponse("sync-ack")
                            .put("syncManifest", syncManifest(localLearning, exportHistory(), requesterRevision).put("acknowledgedRequesterRevision", requesterRevision))
                            .put("obdComponent", (obd?.exportLocalState(settings.deviceId) ?: org.json.JSONObject()))
                            .put("kHistory", exportHistory())
                            .put("autoCalContext", exportAutoCalContext())
                            .put("nativeReceipts", exportNativeReceipts())
                            .put("core", coreTelemetry())
                            .put("obd", JSONObject((obd?.statusJson() ?: "{}")));
                        if (requesterKnowsLocalRevision < localLearning.optLong("componentRevision", 0L)) response.put("learning", localLearning)
                        response
                    }
                    else -> {
                        val err = JSONObject()
                        err.put("ok", false)
                        err.put("error", "Mensagem desconhecida")
                        err
                    }
                }
                writeFrame(client, response)
            } catch (e: Exception) {
                lastError = e.message ?: "Falha no cliente Link"
            }
        }
    }

    private fun applyIncoming(root: JSONObject, peer: Peer?) {
        if (!root.optBoolean("ok", true)) return
        adoptControl(root.optLong("epoch", 0L), root.optString("owner"))
        if (peer != null) root.optJSONObject("syncManifest")?.let { manifest ->
            peerLearningRevisions[peer.deviceId] = maxOf(peerLearningRevisions[peer.deviceId] ?: -1L, manifest.optLong("learningRevision", -1L))
            peerAckedLocalLearningRevisions[peer.deviceId] = maxOf(peerAckedLocalLearningRevisions[peer.deviceId] ?: -1L, manifest.optLong("acknowledgedRequesterRevision", -1L))
        }
        root.optJSONObject("core")?.let { obd?.updateRemoteCoreTelemetry(it) }
        root.optJSONObject("obd")?.let { obd?.acceptRemoteLive(it) }
        root.optJSONObject("learning")?.let {
            val result = mergeLearning(it)
            if (!result.optBoolean("ok")) log.add("WARN", "OMEGAS LINK", "Fusão recusada: ${result.optString("error")}")
            else if (peer != null) peerLearningRevisions[peer.deviceId] = it.optLong("componentRevision", 0L)
        }
        root.optJSONObject("obdComponent")?.let { obd?.mergeRemoteState(it) }
        root.optJSONObject("kHistory")?.let { mergeHistory(it) }
        root.optJSONObject("autoCalContext")?.let { context ->
            val result = mergeAutoCalContext(context)
            if (!result.optBoolean("ok", true)) log.add("WARN", "OMEGAS LINK", "Contexto AutoCal recusado: ${result.optString("error")}")
        }
        root.optJSONObject("nativeReceipts")?.let { receipts ->
            log.add("INFO", "OMEGAS LINK", "Recibos nativos recebidos: ${receipts.optJSONArray("receipts")?.length() ?: 0}")
        }
        if (peer != null) peer.lastSeenAt = System.currentTimeMillis()
        onStateChanged()
    }

    private fun baseResponse(type: String): JSONObject = JSONObject()
        .put("ok", true).put("type", type).put("from", settings.deviceId).put("name", settings.deviceName)
        .put("epoch", controlEpoch).put("owner", controlOwnerId).put("role", activeRole).put("usb", usbConnected())

    private fun syncManifest(learning: JSONObject, history: JSONObject, knownPeerRevision: Long): JSONObject = JSONObject()
        .put("schema", "omegas-link-v6")
        .put("learningSchema", learning.optString("format", "omegas-learning-v6-mp48-v4"))
        .put("learningRevision", learning.optLong("componentRevision", 0L))
        .put("knownPeerLearningRevision", knownPeerRevision)
        .put("learningEpoch", learning.optInt("epoch", 0))
        .put("mapHash", history.optString("mapHash", history.optString("map_hash", "")))
        .put("curveHash", history.optString("curveHash", history.optString("curve_hash", "")))
        .put("controlEpoch", controlEpoch)
        .put("updatedAt", System.currentTimeMillis())

    private fun sendMessage(peer: Peer, payload: JSONObject): JSONObject? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(peer.address, peer.port), 1800); socket.soTimeout = 8_000
            payload.put("protocol", "OMEGAS_LINK_V1").put("pairHash", pairHash()).put("from", settings.deviceId)
            writeFrame(socket, payload)
            readFrame(socket)
        }
    } catch (e: Exception) {
        lastError = "${peer.name}: ${e.message}"; null
    }

    private fun writeFrame(socket: Socket, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 16 * 1024 * 1024) { "Pacote Link excede 16 MB" }
        val out = DataOutputStream(socket.getOutputStream())
        out.writeInt(bytes.size); out.write(bytes); out.flush()
        bytesSent += bytes.size + 4
    }

    private fun readFrame(socket: Socket): JSONObject? {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        if (size <= 0 || size > 16 * 1024 * 1024) error("Tamanho de pacote inválido")
        val bytes = ByteArray(size); input.readFully(bytes); bytesReceived += size + 4
        return JSONObject(String(bytes, StandardCharsets.UTF_8))
    }

    private fun adoptControl(epoch: Long, owner: String) {
        if (epoch < controlEpoch) return
        if (epoch == controlEpoch && owner.isNotBlank() && controlOwnerId.isNotBlank() && owner > controlOwnerId) return
        controlEpoch = epoch; controlOwnerId = owner; settings.linkControlEpoch = epoch; resolveRole()
    }

    private fun reconcileControl(reason: String) {
        if (!settings.linkEnabled || !running.get()) return
        val peer = bestPeer()
        val localUsb = usbConnected()
        when {
            localUsb && (peer == null || !peer.usb) && controlOwnerId != settings.deviceId -> {
                controlEpoch = maxOf(System.currentTimeMillis(), controlEpoch + 1)
                controlOwnerId = settings.deviceId
                settings.linkControlEpoch = controlEpoch
                activeRole = "PRINCIPAL"
                peer?.let { target ->
                    executor.execute {
                        sendMessage(target, JSONObject().put("type", "claim").put("epoch", controlEpoch).put("owner", settings.deviceId))
                    }
                }
                log.add("INFO", "OMEGAS LINK", "Controle assumido automaticamente • $reason")
            }
            !localUsb && peer?.usb == true && controlOwnerId == settings.deviceId -> {
                controlEpoch = maxOf(System.currentTimeMillis(), controlEpoch + 1)
                controlOwnerId = ""
                settings.linkControlEpoch = controlEpoch
                executor.execute {
                    sendMessage(peer, JSONObject().put("type", "release").put("epoch", controlEpoch).put("owner", settings.deviceId))
                }
                log.add("INFO", "OMEGAS LINK", "Controle liberado automaticamente para o aparelho com MP48")
            }
        }
        resolveRole()
    }

    private fun resolveRole() {
        if (!settings.linkEnabled || !running.get()) { activeRole = "OFF"; return }
        val peer = bestPeer()
        if (controlOwnerId == settings.deviceId) activeRole = "PRINCIPAL"
        else if (controlOwnerId.isNotBlank()) activeRole = "COMPANHEIRO"
        else if (peer == null) activeRole = if (usbConnected()) "PRINCIPAL" else "COMPANHEIRO"
        else activeRole = when (settings.linkRolePreference) {
            "main" -> if (usbConnected()) "PRINCIPAL" else "COMPANHEIRO"
            "companion" -> "COMPANHEIRO"
            else -> if (usbConnected() && !peer.usb) "PRINCIPAL" else "COMPANHEIRO"
        }
        if (activeRole == "PRINCIPAL" && controlOwnerId.isBlank()) controlOwnerId = settings.deviceId
    }

    private fun bestPeer(): Peer? = peers.values.filter { System.currentTimeMillis() - it.lastSeenAt < 8_000L }
        .sortedWith(compareByDescending<Peer> { it.usb }.thenByDescending { it.lastSeenAt }).firstOrNull()

    private fun prunePeers() {
        val now = System.currentTimeMillis(); peers.entries.removeIf { now - it.value.lastSeenAt > 15_000L }
    }

    private fun pairHash(): String = MessageDigest.getInstance("SHA-256")
        .digest(("OMEGAS_LINK:" + settings.linkPairCode).toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun peerJson(peer: Peer): JSONObject = JSONObject()
        .put("deviceId", peer.deviceId).put("name", peer.name).put("address", peer.address.hostAddress)
        .put("port", peer.port).put("usb", peer.usb).put("obdConnected", peer.obdConnected)
        .put("role", peer.role).put("controlEpoch", peer.controlEpoch).put("lastSeenAt", peer.lastSeenAt)

    private fun broadcastAddresses(): Set<InetAddress> {
        val out = linkedSetOf<InetAddress>()
        out += InetAddress.getByName("255.255.255.255")
        try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.forEach { iface ->
                iface.interfaceAddresses.mapNotNullTo(out) { it.broadcast }
                iface.inetAddresses.toList().filterIsInstance<Inet4Address>().forEach { address ->
                    val raw = address.address.copyOf(); raw[3] = 0xFF.toByte(); out += InetAddress.getByAddress(raw)
                }
            }
        } catch (_: Exception) {}
        return out
    }
}
