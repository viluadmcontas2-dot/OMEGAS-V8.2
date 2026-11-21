package com.omegas.dyno.lab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PassiveObdBackgroundService : Service() {
    private val stopped = CountDownLatch(1)
    private val activeSocket = AtomicReference<BluetoothSocket>()
    private var started = false

    // Accessed only by the capture worker.
    private var bytes = 0L
    private var frames = 0L
    private var linked = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val validIntent = intent != null && hasNoExtras(intent)
        if (started) return START_NOT_STICKY
        val error = when {
            !validIntent -> "Início inválido"
            !hasBluetoothPermission() -> "Permissão Bluetooth ausente"
            else -> {
                try {
                    promoteToForeground()
                    null
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "Falha ao iniciar serviço em primeiro plano", exception)
                    "Serviço em primeiro plano indisponível"
                }
            }
        }
        if (error != null) {
            captureWorker.execute { publishStatus(error) }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        started = true
        captureWorker.execute {
            try {
                capture()
            } finally {
                mainHandler.post { stopSelf() }
            }
        }
        return START_NOT_STICKY
    }

    private val mainHandler by lazy { Handler(mainLooper) }

    override fun onDestroy() {
        stopped.countDown()
        activeSocket.get()?.let { socket -> watchdog.execute { closeSocket(socket) } }
        super.onDestroy()
    }

    private fun hasNoExtras(intent: Intent): Boolean =
        try {
            intent.extras?.isEmpty != false
        } catch (_: RuntimeException) {
            false
        }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground() {
        val notifications = checkNotNull(getSystemService(NotificationManager::class.java))
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Dinamometro - captacao passiva", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dyno)
            .setContentTitle("Captação passiva do dinamômetro")
            .setOngoing(true)
            .build())
    }

    private fun capture() {
        var error: String? = null
        publishStatus(null)
        try {
            VaultStore(applicationContext).use { vault ->
                vault.recoverInterrupted(passive = true)
                val session = vault.start(receiptStamp(), passive = true)
                try {
                    val buffer = ByteArray(4096)
                    while (stopped.count != 0L) {
                        error = try {
                            receive(vault, session, buffer)
                            null
                        } catch (exception: IOException) {
                            exception.message
                        } catch (_: SecurityException) {
                            "Permissão Bluetooth ausente"
                        }
                        if (stopped.count == 0L) break
                        publishStatus(error)
                        stopped.await(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                    }
                } finally {
                    vault.stop(session, receiptStamp(), passive = true)
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Falha na captação passiva", exception)
            error = "Falha na captação ou no armazenamento"
        } finally {
            linked = false
            publishStatus(error)
        }
    }

    @SuppressLint("MissingPermission") // Checked here and again by Android on each Bluetooth call.
    private fun receive(vault: VaultStore, session: Long, buffer: ByteArray) {
        if (!hasBluetoothPermission()) throw SecurityException()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("Bluetooth indisponível")
        if (!adapter.isEnabled) throw IOException("Bluetooth desligado")
        val devices = adapter.bondedDevices.filter { it.name?.startsWith("OBDII") == true }
        if (devices.size != 1) throw IOException(
            if (devices.isEmpty()) "Nenhum OBDII emparelhado" else "Mais de um OBDII emparelhado"
        )

        val socket = devices.single().createRfcommSocketToServiceRecord(SPP_UUID)
        activeSocket.set(socket)
        try {
            if (stopped.count == 0L) return
            withDeadline(socket, CONNECT_TIMEOUT_MS, "Tempo de conexão esgotado") { socket.connect() }
            val input = socket.inputStream
            linked = true
            publishStatus(null)
            var lastStatusNs = 0L
            while (stopped.count != 0L) {
                val startedNs = SystemClock.elapsedRealtimeNanos()
                val size = withDeadline(socket, READ_TIMEOUT_MS, "Sem dados no prazo de leitura") {
                    input.read(buffer)
                }
                val stamp = receiptStamp()
                if (size < 0) throw IOException("Conexão encerrada pelo adaptador")
                if (size == 0) continue
                bytes += size
                val hex = buildString(size * 2) {
                    repeat(size) { index ->
                        val value = buffer[index].toInt() and 0xff
                        append(HEX[value ushr 4])
                        append(HEX[value and 0x0f])
                    }
                }
                // Read age measures the wait for this chunk. RFCOMM has no packet boundaries.
                vault.observePassiveObd(session, stamp, hex, size, stamp.monoNs - startedNs)
                frames++
                if (frames == 1L || stamp.monoNs - lastStatusNs >= STATUS_INTERVAL_NS) {
                    publishStatus(null)
                    lastStatusNs = stamp.monoNs
                }
            }
        } catch (exception: IOException) {
            // Keep driver-specific messages out of the status file.
            if (stopped.count != 0L) {
                if (exception is SocketTimeoutException) throw exception
                throw IOException("Conexão Bluetooth indisponível", exception)
            }
        } finally {
            linked = false
            activeSocket.compareAndSet(socket, null)
            closeSocket(socket)
        }
    }

    // BluetoothSocket exposes no public read timeout. Closing it unblocks the pending operation.
    private fun <T> withDeadline(
        socket: BluetoothSocket, timeoutMs: Long, message: String, operation: () -> T,
    ): T {
        val finished = AtomicBoolean(false)
        val deadline = watchdog.schedule(Runnable {
            if (finished.compareAndSet(false, true)) closeSocket(socket)
        }, timeoutMs, TimeUnit.MILLISECONDS)
        try {
            val result = operation()
            finished.compareAndSet(false, true)
            return result
        } catch (exception: IOException) {
            if (finished.compareAndSet(false, true)) throw exception
            throw SocketTimeoutException(message).apply { initCause(exception) }
        } finally {
            finished.set(true)
            deadline.cancel(false)
        }
    }

    private fun closeSocket(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (exception: IOException) {
            Log.w(TAG, "Falha ao fechar conexão Bluetooth", exception)
        }
    }

    private fun publishStatus(error: String?) {
        val file = AtomicFile(File(filesDir, "passive-obd-state.json"))
        val data = JSONObject()
            .put("bytes", bytes)
            .put("frames", frames)
            .put("error", error ?: JSONObject.NULL)
            .put("linked", linked)
            .put("ts", Instant.now().toString())
        try {
            val stream = file.startWrite()
            try {
                stream.write(data.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (exception: IOException) {
                file.failWrite(stream)
                throw exception
            }
        } catch (exception: IOException) {
            Log.e(TAG, "Falha ao atualizar estado da captação", exception)
        }
    }

    private companion object {
        const val TAG = "PassiveObd"
        const val CHANNEL = "dyno_passive_obd"
        const val NOTIFICATION_ID = 3001
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val READ_TIMEOUT_MS = 3_000L
        const val RETRY_DELAY_MS = 25_000L
        const val STATUS_INTERVAL_NS = 1_000_000_000L
        const val HEX = "0123456789ABCDEF"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        // Serialize rollover of the vault session and state file across service instances.
        val captureWorker = Executors.newSingleThreadExecutor()
        val watchdog = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    }
}
