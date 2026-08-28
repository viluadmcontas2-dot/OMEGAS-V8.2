package com.omegas.prohub.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class UsbSerialManager(
    private val context: Context,
    private val settings: AppSettings,
    private val log: RingLog,
    private val onStateChanged: () -> Unit,
    private val onRawIo: (String, ByteArray) -> Unit = { _, _ -> },
) : SerialInputOutputManager.Listener {

    private val actionUsbPermission = "${context.packageName}.USB_PERMISSION"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val rxQueue = ConcurrentLinkedQueue<Byte>()
    private val rxSize = AtomicInteger(0)
    private val sessionCounter = AtomicLong(0L)
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor()
    private var reconnectTask: ScheduledFuture<*>? = null
    private var recoveryTask: ScheduledFuture<*>? = null
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var manualDisconnect = false
    private val transactionLock = ReentrantLock(true)
    private val rxLock = ReentrantLock()
    private val rxCondition = rxLock.newCondition()
    @Volatile var transactionActive = false
        private set

    @Volatile var connected = false
        private set
    @Volatile var recovering = false
        private set
    @Volatile var recoveryAttempts = 0
        private set
    @Volatile var lastRecoveryReason = ""
        private set
    @Volatile var permissionPending = false
        private set
    @Volatile var deviceLabel = "Nenhum"
        private set
    @Volatile var activeDeviceName = ""
        private set
    @Volatile var connectionSessionId = 0L
        private set

    private val permissionIntent = PendingIntent.getBroadcast(
        context,
        4301,
        Intent(actionUsbPermission).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                actionUsbPermission -> {
                    permissionPending = false
                    val device = intent.usbDevice()
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null && isOmegasDevice(device)) {
                        open(device)
                    } else if (device != null && !isOmegasDevice(device)) {
                        log.add("INFO", "USB", "Permissão ignorada para USB fora da identidade OMEGAS")
                    } else {
                        log.add("WARN", "USB", "Permissão USB negada")
                    }
                    onStateChanged()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached = intent.usbDevice()
                    if (detached != null && detached.deviceName == activeDeviceName) {
                        log.add("WARN", "USB", "Interface OMEGAS removida: ${detached.deviceName}")
                        hardDisconnect(scheduleReconnect = false, reason = "USB_DEVICE_DETACHED")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val attached = intent.usbDevice()
                    if (attached != null && isOmegasDevice(attached)) {
                        log.add("INFO", "USB", "Interface OMEGAS detectada: ${attached.deviceName}")
                        if (settings.autoConnectUsb) connect(attached.deviceName)
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(actionUsbPermission)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun hasCompatibleDevice(): Boolean = usbManager.deviceList.values.any(::isOmegasDevice)

    private fun isOmegasDevice(device: UsbDevice): Boolean =
        OmegasUsbIdentity.matches(device.vendorId, device.productId) &&
            UsbSerialProber.getDefaultProber().probeDevice(device) != null

    private fun activeOmegasDevice(): UsbDevice? = usbManager.deviceList.values.firstOrNull {
        it.deviceName == activeDeviceName && isOmegasDevice(it)
    }

    fun devicesJson(): String {
        val arr = JSONArray()
        usbManager.deviceList.values.sortedBy { it.deviceId }.forEach { device ->
            val omegas = isOmegasDevice(device)
            val driver = if (omegas) UsbSerialProber.getDefaultProber().probeDevice(device) else null
            arr.put(
                JSONObject()
                    .put("deviceName", device.deviceName)
                    .put("vendorId", device.vendorId)
                    .put("productId", device.productId)
                    .put("permission", if (omegas) usbManager.hasPermission(device) else false)
                    .put("omegasInterface", omegas)
                    .put("compatible", omegas && driver != null)
                    .put("ports", driver?.ports?.size ?: 0)
                    .put("driver", driver?.javaClass?.simpleName ?: "Ignorado")
                    .put("selected", omegas && device.deviceName == activeDeviceName),
            )
        }
        return arr.toString()
    }

    @Synchronized
    fun connect(requestedDeviceName: String? = null): Boolean {
        manualDisconnect = false
        recoveryTask?.cancel(false)
        recoveryTask = null
        recovering = false
        recoveryAttempts = 0
        reconnectTask?.cancel(false)
        if (connected && (requestedDeviceName.isNullOrBlank() || requestedDeviceName == activeDeviceName)) return true
        val candidates = usbManager.deviceList.values.filter(::isOmegasDevice).sortedBy { it.deviceId }
        val preferred = requestedDeviceName?.takeIf { it.isNotBlank() }
            ?: settings.preferredDeviceName.takeIf { it.isNotBlank() }
        val device = candidates.firstOrNull { it.deviceName == preferred }
            ?: candidates.firstOrNull()
            ?: run {
                log.add("INFO", "USB", "Aguardando interface OMEGAS 10C4:EA60")
                onStateChanged()
                return false
            }
        settings.preferredDeviceName = device.deviceName
        if (!usbManager.hasPermission(device)) {
            permissionPending = true
            activeDeviceName = device.deviceName
            usbManager.requestPermission(device, permissionIntent)
            log.add("INFO", "USB", "Solicitando permissão somente para interface OMEGAS ${device.deviceName}")
            onStateChanged()
            return false
        }
        return open(device)
    }

    @Synchronized
    private fun open(device: UsbDevice): Boolean {
        if (!isOmegasDevice(device)) {
            log.add("WARN", "USB", "Dispositivo ignorado: identidade diferente de OMEGAS 10C4:EA60")
            return false
        }
        disconnectInternal(scheduleReconnect = false, notify = false)
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: return false.also { log.add("ERROR", "USB", "Driver serial OMEGAS não localizado") }
        val connection = usbManager.openDevice(device)
            ?: return false.also { log.add("ERROR", "USB", "Falha ao abrir interface OMEGAS") }
        return try {
            val selected = driver.ports.firstOrNull() ?: error("Driver sem porta serial")
            configurePort(selected, connection)
            port = selected
            purge("nova conexão USB OMEGAS", selected)
            ioManager = SerialInputOutputManager(selected, this).also { ioExecutor.submit(it) }
            activeDeviceName = device.deviceName
            deviceLabel = "${device.vendorId}:${device.productId} • ${driver.javaClass.simpleName}"
            connected = true
            recovering = false
            recoveryAttempts = 0
            lastRecoveryReason = ""
            connectionSessionId = sessionCounter.incrementAndGet()
            permissionPending = false
            clearReceiveBuffer()
            log.add(
                "INFO",
                "USB",
                "Conectado ${settings.baudRate} ${settings.dataBits}${settings.parity.firstOrNull() ?: 'N'}${settings.stopBits}: $deviceLabel",
            )
            onStateChanged()
            true
        } catch (e: Exception) {
            log.add("ERROR", "USB", "Falha ao configurar porta OMEGAS: ${e.message}")
            try { connection.close() } catch (_: Exception) {}
            disconnectInternal(scheduleReconnect = true)
            false
        }
    }

    private fun configurePort(selected: UsbSerialPort, connection: android.hardware.usb.UsbDeviceConnection) {
        selected.open(connection)
        selected.setParameters(
            settings.baudRate,
            settings.dataBits,
            if (settings.stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1,
            parityConstant(settings.parity),
        )
        try { selected.dtr = settings.dtr } catch (_: Exception) {}
        try { selected.rts = settings.rts } catch (_: Exception) {}
    }

    @Synchronized
    fun disconnect() {
        manualDisconnect = true
        reconnectTask?.cancel(false)
        recoveryTask?.cancel(false)
        hardDisconnect(scheduleReconnect = false, reason = "MANUAL")
        log.add("INFO", "USB", "Desconexão manual")
    }

    @Synchronized
    fun reconfigure(): Boolean {
        val selected = activeDeviceName.takeIf { it.isNotBlank() }
        disconnectInternal(scheduleReconnect = false)
        return connect(selected)
    }

    @Synchronized
    private fun hardDisconnect(scheduleReconnect: Boolean, reason: String) {
        recoveryTask?.cancel(false)
        recoveryTask = null
        recovering = false
        recoveryAttempts = 0
        lastRecoveryReason = reason
        disconnectInternal(scheduleReconnect = scheduleReconnect)
    }

    @Synchronized
    private fun disconnectInternal(scheduleReconnect: Boolean, notify: Boolean = true) {
        closePortOnly()
        connected = false
        recovering = false
        permissionPending = false
        deviceLabel = "Nenhum"
        clearReceiveBuffer()
        if (notify) onStateChanged()
        if (scheduleReconnect) scheduleReconnect()
    }

    private fun closePortOnly() {
        try { ioManager?.stop() } catch (_: Exception) {}
        ioManager = null
        try { port?.close() } catch (_: Exception) {}
        port = null
        clearReceiveBuffer()
    }

    @Synchronized
    fun purge(reason: String = "sincronização serial"): Boolean = purge(reason, port)

    private fun purge(reason: String, target: UsbSerialPort?): Boolean {
        clearReceiveBuffer()
        if (target == null) return false
        return try {
            target.purgeHwBuffers(true, true)
            clearReceiveBuffer()
            log.add("INFO", "USB", "Purge RX/TX concluído • $reason")
            true
        } catch (e: Exception) {
            clearReceiveBuffer()
            log.add("WARN", "USB", "Purge de software aplicado; driver sem purge físico: ${e.message}")
            false
        }
    }

    @Synchronized
    fun send(data: ByteArray) {
        if (recovering) return
        val current = port ?: return
        try {
            current.write(data, 1200)
            onRawIo("TX", data.copyOf())
        } catch (e: IOException) {
            log.add("ERROR", "USB", "Falha TX: ${e.message}")
            beginTransientRecovery("TX: ${e.message ?: "IOException"}")
        }
    }

    fun receive(maxBytes: Int = 8192): ByteArray {
        val available = rxSize.get().coerceAtMost(maxBytes)
        if (available <= 0) return byteArrayOf()
        val out = ByteArray(available)
        var written = 0
        while (written < available) {
            val value = rxQueue.poll() ?: break
            out[written++] = value
            rxSize.decrementAndGet()
        }
        return if (written == out.size) out else out.copyOf(written)
    }

    /** Executa uma transação exclusiva com eco, frame 53/LEN/PAYLOAD/CK e checksum. */
    fun protocolTransaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int = 1800,
        purgeBefore: Boolean = true,
        expectedSessionId: Long = 0L,
    ): UsbProtocolReply = transactionLock.withLock {
        val started = android.os.SystemClock.elapsedRealtime()
        if (recovering) {
            return@withLock UsbProtocolReply(false, error = "USB em recuperação transitória", request = request)
        }
        if (expectedSessionId > 0L && connectionSessionId != expectedSessionId) {
            return@withLock UsbProtocolReply(
                false,
                error = "Sessão USB mudou antes de $reason",
                request = request,
            )
        }
        val current = port
            ?: return@withLock UsbProtocolReply(false, error = "USB desconectado", request = request)
        if (!connected) return@withLock UsbProtocolReply(false, error = "USB desconectado", request = request)
        transactionActive = true
        try {
            if (purgeBefore) purge("$reason • pré-transação")
            current.write(request, timeoutMs)
            onRawIo("TX", request.copyOf())
            val echo = readExact(request.size, minOf(timeoutMs, 900))
            if (!echo.contentEquals(request)) {
                return@withLock UsbProtocolReply(false, request = request, echo = echo, error = "Eco divergente ou incompleto", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            }
            val statusBytes = readExact(1, timeoutMs)
            if (statusBytes.size != 1) return@withLock UsbProtocolReply(false, request = request, echo = echo, error = "Timeout aguardando status", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            val status = statusBytes[0].toInt() and 0xFF
            val lenBytes = readExact(1, timeoutMs)
            if (lenBytes.size != 1) return@withLock UsbProtocolReply(false, status = status, request = request, echo = echo, rawResponse = statusBytes, error = "Resposta sem campo de tamanho", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            val length = lenBytes[0].toInt() and 0xFF
            if (length > 192) return@withLock UsbProtocolReply(false, status = status, request = request, echo = echo, rawResponse = statusBytes + lenBytes, error = "Tamanho de resposta inválido: $length", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            val tail = readExact(length + 1, timeoutMs)
            val raw = statusBytes + lenBytes + tail
            if (tail.size != length + 1) return@withLock UsbProtocolReply(false, status = status, request = request, echo = echo, rawResponse = raw, error = "Resposta incompleta: ${tail.size}/${length + 1} bytes", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            val expected = raw.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
            val received = raw.last().toInt() and 0xFF
            if (expected != received) return@withLock UsbProtocolReply(false, status = status, request = request, echo = echo, rawResponse = raw, error = "Checksum RX inválido: esperado %02X, recebido %02X".format(expected, received), elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            val payload = if (length == 0) byteArrayOf() else tail.copyOfRange(0, length)
            if (expectedSessionId > 0L && (!connected || connectionSessionId != expectedSessionId)) {
                UsbProtocolReply(
                    false,
                    status,
                    payload,
                    request,
                    echo,
                    raw,
                    "Sessão USB mudou durante $reason",
                    android.os.SystemClock.elapsedRealtime() - started,
                )
            } else {
                UsbProtocolReply(status == 0x53, status, payload, request, echo, raw, if (status == 0x53) "" else "ECU retornou status 0x%02X".format(status), elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
            }
        } catch (e: Exception) {
            log.add("ERROR", "USB", "Transação serial falhou ($reason): ${e.message}")
            UsbProtocolReply(false, request = request, error = e.message ?: "Falha serial", elapsedMs = android.os.SystemClock.elapsedRealtime() - started)
        } finally {
            transactionActive = false
        }
    }

    private fun readExact(count: Int, timeoutMs: Int): ByteArray {
        if (count <= 0) return byteArrayOf()
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        val out = java.io.ByteArrayOutputStream(count)
        while (out.size() < count) {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            val chunk = receive(count - out.size())
            if (chunk.isNotEmpty()) {
                out.write(chunk)
            } else {
                rxLock.withLock {
                    rxCondition.await(remaining.coerceAtMost(5L), TimeUnit.MILLISECONDS)
                }
            }
        }
        return out.toByteArray()
    }

    override fun onNewData(data: ByteArray) {
        onRawIo("RX", data.copyOf())
        data.forEach {
            rxQueue.offer(it)
            val size = rxSize.incrementAndGet()
            if (size > 131_072) {
                if (rxQueue.poll() != null) rxSize.decrementAndGet()
            }
        }
        rxLock.withLock {
            rxCondition.signalAll()
        }
    }

    override fun onRunError(e: Exception) {
        log.add("ERROR", "USB", "Thread serial interrompida: ${e.message}")
        beginTransientRecovery("IO: ${e.message ?: e.javaClass.simpleName}")
    }

    @Synchronized
    private fun beginTransientRecovery(reason: String) {
        if (recovering || manualDisconnect) return
        val devicePresent = activeOmegasDevice() != null
        val decision = UsbRecoveryPolicy.decide(
            devicePresent = devicePresent,
            autoReconnect = settings.autoReconnectUsb,
            manualDisconnect = manualDisconnect,
            attempt = 0,
        )
        if (decision.action == UsbRecoveryAction.HARD_DISCONNECT) {
            hardDisconnect(scheduleReconnect = devicePresent && settings.autoReconnectUsb, reason = reason)
            return
        }
        recovering = true
        recoveryAttempts = 0
        lastRecoveryReason = reason
        closePortOnly()
        log.add("WARN", "USB", "Falha transitória; preservando sessão lógica e tentando recuperar em ${decision.delayMs} ms • $reason")
        onStateChanged()
        scheduleRecoveryAttempt(decision.delayMs)
    }

    private fun scheduleRecoveryAttempt(delayMs: Long) {
        recoveryTask?.cancel(false)
        recoveryTask = reconnectExecutor.schedule({ performRecoveryAttempt() }, delayMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun performRecoveryAttempt() {
        if (!recovering || manualDisconnect) return
        val device = activeOmegasDevice()
        if (device == null) {
            hardDisconnect(scheduleReconnect = false, reason = "OMEGAS_REMOVIDO_DURANTE_RECUPERACAO")
            return
        }
        if (openRecoveredPort(device)) {
            recovering = false
            recoveryAttempts = 0
            val reason = lastRecoveryReason
            lastRecoveryReason = ""
            log.add("INFO", "USB", "Porta OMEGAS recuperada sem derrubar sessão lógica • $reason")
            onStateChanged()
            return
        }
        recoveryAttempts += 1
        val next = UsbRecoveryPolicy.decide(
            devicePresent = activeOmegasDevice() != null,
            autoReconnect = settings.autoReconnectUsb,
            manualDisconnect = manualDisconnect,
            attempt = recoveryAttempts,
        )
        if (next.action == UsbRecoveryAction.RETRY_TRANSPORT) {
            log.add("WARN", "USB", "Recuperação OMEGAS tentativa ${recoveryAttempts + 1}; novo retry em ${next.delayMs} ms")
            scheduleRecoveryAttempt(next.delayMs)
        } else {
            hardDisconnect(scheduleReconnect = settings.autoReconnectUsb && hasCompatibleDevice(), reason = "RECOVERY_EXHAUSTED")
        }
    }

    @Synchronized
    private fun openRecoveredPort(device: UsbDevice): Boolean {
        if (!isOmegasDevice(device) || !usbManager.hasPermission(device)) return false
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return false
        val connection = usbManager.openDevice(device) ?: return false
        return try {
            val selected = driver.ports.firstOrNull() ?: error("Driver sem porta serial")
            configurePort(selected, connection)
            port = selected
            purge("recuperação transitória OMEGAS", selected)
            ioManager = SerialInputOutputManager(selected, this).also { ioExecutor.submit(it) }
            deviceLabel = "${device.vendorId}:${device.productId} • ${driver.javaClass.simpleName}"
            permissionPending = false
            clearReceiveBuffer()
            true
        } catch (e: Exception) {
            try { connection.close() } catch (_: Exception) {}
            closePortOnly()
            log.add("WARN", "USB", "Tentativa de recuperar porta OMEGAS falhou: ${e.message}")
            false
        }
    }

    fun close() {
        manualDisconnect = true
        recoveryTask?.cancel(true)
        reconnectTask?.cancel(true)
        disconnectInternal(scheduleReconnect = false)
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        ioExecutor.shutdownNow()
        reconnectExecutor.shutdownNow()
    }

    private fun scheduleReconnect() {
        if (manualDisconnect || !settings.autoReconnectUsb || reconnectExecutor.isShutdown || !hasCompatibleDevice()) return
        reconnectTask?.cancel(false)
        reconnectTask = reconnectExecutor.schedule({
            try { connect() } catch (e: Exception) { log.add("WARN", "USB", "Reconexão falhou: ${e.message}") }
        }, 3, TimeUnit.SECONDS)
    }

    private fun clearReceiveBuffer() {
        rxQueue.clear()
        rxSize.set(0)
    }

    private fun parityConstant(value: String): Int = when (value.uppercase()) {
        "ODD" -> UsbSerialPort.PARITY_ODD
        "EVEN" -> UsbSerialPort.PARITY_EVEN
        "MARK" -> UsbSerialPort.PARITY_MARK
        "SPACE" -> UsbSerialPort.PARITY_SPACE
        else -> UsbSerialPort.PARITY_NONE
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
