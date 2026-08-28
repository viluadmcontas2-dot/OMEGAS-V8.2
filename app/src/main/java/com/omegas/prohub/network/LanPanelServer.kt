package com.omegas.prohub.network

import android.content.Context
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only LAN panel. It never touches USB and never accepts write routes. */
class LanPanelServer(
    private val context: Context,
    private val log: RingLog,
    private val stateProvider: () -> String,
    private val statusProvider: () -> JSONObject,
) {
    private val runningFlag = AtomicBoolean(false)
    private val acceptExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "omegas-lan-accept").apply { isDaemon = true } }
    private val clientExecutor = Executors.newFixedThreadPool(3) { r -> Thread(r, "omegas-lan-client").apply { isDaemon = true } }
    private var serverSocket: ServerSocket? = null

    @Volatile var port: Int = 8088
        private set
    @Volatile var token: String = ""
        private set
    @Volatile var lastError: String = ""
        private set
    @Volatile var requests: Long = 0
        private set

    val running: Boolean get() = runningFlag.get()

    @Synchronized
    fun start(requestedPort: Int, accessToken: String): Boolean {
        if (running) return true
        port = requestedPort.coerceIn(1024, 65535)
        token = accessToken.trim()
        if (token.length < 4) {
            lastError = "Código de acesso inválido"
            return false
        }
        return try {
            val socket = ServerSocket(port).apply { soTimeout = 1000; reuseAddress = true }
            serverSocket = socket
            runningFlag.set(true)
            lastError = ""
            acceptExecutor.execute { acceptLoop(socket) }
            log.add("INFO", "LAN", "Painel Wi-Fi somente leitura iniciado em ${address()}")
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao abrir servidor Wi-Fi"
            log.add("WARN", "LAN", lastError)
            false
        }
    }

    @Synchronized
    fun stop() {
        runningFlag.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        log.add("INFO", "LAN", "Painel Wi-Fi encerrado")
    }

    fun close() {
        stop()
        acceptExecutor.shutdownNow()
        clientExecutor.shutdownNow()
    }

    fun statusJson(): JSONObject = JSONObject()
        .put("enabled", running)
        .put("port", port)
        .put("address", address())
        .put("token", token)
        .put("requests", requests)
        .put("lastError", lastError)
        .put("readOnly", true)

    fun address(): String {
        val ip = localIpv4() ?: "127.0.0.1"
        return "http://$ip:$port/?token=$token"
    }

    private fun acceptLoop(server: ServerSocket) {
        while (runningFlag.get()) {
            try {
                val client = server.accept()
                client.soTimeout = 4000
                clientExecutor.execute { handle(client) }
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                if (runningFlag.get()) {
                    lastError = e.message ?: "Falha no servidor"
                    log.add("WARN", "LAN", lastError)
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine()?.take(4096) ?: return
                var lineCount = 0
                while (lineCount++ < 80) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] != "GET") {
                    respond(client, 405, "application/json", JSONObject().put("error", "Somente GET").toString())
                    return
                }
                requests += 1
                val target = parts[1]
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                if (path.startsWith("/api/") && query["token"] != token) {
                    respond(client, 401, "application/json", JSONObject().put("error", "Código de acesso inválido").toString())
                    return
                }
                when (path) {
                    "/" -> {
                        if (query["token"] != token) {
                            respond(client, 401, "text/html; charset=utf-8", "<h1>OMEGAS</h1><p>Informe ?token=...</p>")
                        } else respondAsset(client, "remote/index.html", "text/html; charset=utf-8")
                    }
                    "/app.js" -> respondAsset(client, "remote/app.js", "application/javascript; charset=utf-8")
                    "/styles.css" -> respondAsset(client, "remote/styles.css", "text/css; charset=utf-8")
                    "/api/health" -> respond(client, 200, "application/json", statusProvider().put("lan", statusJson()).toString())
                    "/api/state" -> respond(client, 200, "application/json", stateProvider())
                    else -> respond(client, 404, "application/json", JSONObject().put("error", "Rota não encontrada").toString())
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Falha de cliente"
            }
        }
    }

    private fun respondAsset(socket: Socket, asset: String, type: String) {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        respond(socket, 200, type, bytes)
    }

    private fun respond(socket: Socket, status: Int, type: String, body: String) = respond(socket, status, type, body.toByteArray(StandardCharsets.UTF_8))

    private fun respond(socket: Socket, status: Int, type: String, body: ByteArray) {
        val text = when (status) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val header = buildString {
            append("HTTP/1.1 $status $text\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Content-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().use { out -> out.write(header); out.write(body); out.flush() }
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val pieces = pair.split('=', limit = 2)
        val key = URLDecoder.decode(pieces[0], "UTF-8")
        val value = URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8")
        key to value
    }.toMap()

    private fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (_: Exception) { null }
}

