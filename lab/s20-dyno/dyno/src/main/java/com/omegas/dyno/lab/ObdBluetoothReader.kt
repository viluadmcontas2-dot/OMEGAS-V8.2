package com.omegas.dyno.lab

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun receiptStamp() = Stamp(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())

/** Single socket, bounded reconnect backoff, read-only OBD-II Mode 01 polling. */
@SuppressLint("MissingPermission")
internal class ObdBluetoothReader(
    private val context: Context,
    private val state: (String, String?) -> Unit,
    private val sample: (Observation) -> Unit,
) {
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private var worker: Thread? = null
    private var socket: BluetoothSocket? = null
    @Volatile private var closed = false
    @Volatile private var desiredId: String? = null

    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    fun devices(): List<BluetoothDevice> {
        if (needsPermission()) return emptyList()
        return context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }.sortedBy { it.address }
    }

    @Synchronized fun start(id: String): Boolean {
        if (closed) return false
        // A foreground service and an Activity can request the same MAC; never create two sockets.
        if (worker != null) {
            if (desiredId != id) {
                desiredId = id
                runCatching { socket?.close() }
            }
            return true
        }
        desiredId = id
        state("conectando", null)
        worker = Thread(::keepConnected, "dyno-obd-persistent").also { it.start() }
        return true
    }

    private fun keepConnected() {
        var retries = 0
        try {
            while (!closed && desiredId != null) {
                val id = desiredId ?: break
                var error: String? = null
                try {
                    if (needsPermission()) throw SecurityException("Permissão Bluetooth ausente")
                    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                        ?: throw IOException("Bluetooth indisponível")
                    if (!adapter.isEnabled) throw IOException("Bluetooth desligado")
                    val device = devices().firstOrNull { it.address == id }
                        ?: throw IOException("Adaptador OBD pareado indisponível")
                    val link = device.createRfcommSocketToServiceRecord(SPP)
                    synchronized(this) {
                        if (closed) {
                            link.close()
                            return
                        }
                        socket = link
                    }
                    link.use {
                        timed(link) { link.connect() }
                        if (closed || desiredId != id) return@use
                        state("conectando", null)
                        poll(link) { retries = 0 }
                    }
                } catch (exception: Exception) {
                    error = when (exception) {
                        is SecurityException -> "Permissão Bluetooth ausente"
                        is IOException -> exception.message ?: "Conexão Bluetooth interrompida"
                        else -> "Falha na comunicação OBD"
                    }
                } finally {
                    synchronized(this) {
                        socket = null
                    }
                }
                if (closed || desiredId == null) break
                retries = (retries + 1).coerceAtMost(RETRY_SECONDS.size)
                val seconds = RETRY_SECONDS[(retries - 1).coerceAtLeast(0)]
                val reason = error ?: "OBD interrompido"
                state("reconectando em $seconds s", "$reason; reconexão automática")
                // Wakes regularly to respect service stop without accumulating retry threads.
                repeat(seconds * 4) {
                    if (closed || desiredId == null) return@repeat
                    try {
                        Thread.sleep(250)
                    } catch (_: InterruptedException) {
                        return@repeat
                    }
                }
                if (!closed && desiredId != null) state("conectando", null)
            }
        } finally {
            synchronized(this) {
                worker = null
                if (!closed) state(TelemetryHub.OFFLINE, null)
            }
        }
    }

    fun close() {
        val link = synchronized(this) {
            closed = true
            desiredId = null
            worker?.interrupt()
            socket
        }
        runCatching { link?.close() }
        timer.shutdownNow()
    }

    private fun <T> timed(link: BluetoothSocket, block: () -> T): T {
        val timeout = timer.schedule({ runCatching { link.close() } }, 6, TimeUnit.SECONDS)
        try {
            return block()
        } finally {
            timeout.cancel(false)
        }
    }

    private fun poll(link: BluetoothSocket, connected: () -> Unit) {
        val input = link.inputStream
        val output = link.outputStream
        fun exchange(command: String): String = timed(link) {
            output.write((command + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()
            val response = StringBuilder()
            while (response.length < 8192 && !Thread.currentThread().isInterrupted && !closed) {
                val byte = input.read()
                if (byte < 0) throw IOException("Adaptador OBD desconectado")
                if (byte == '>'.code) break
                response.append(byte.toChar())
            }
            if (response.length >= 8192 || Thread.currentThread().isInterrupted || closed) {
                throw IOException("Resposta OBD interrompida ou longa demais")
            }
            response.toString()
        }

        // Adapter setup is local to ELM327; ECU queries remain in read-only Mode 01 allowlist.
        for (command in ObdProtocol.adapterSetup) {
            if (exchange(command).lineSequence().none { it.trim() == "OK" }) {
                throw IOException("Adaptador OBD não confirmou a configuração")
            }
        }
        val bitmaps = mutableMapOf<Int, Long>()
        for ((base, command) in ObdProtocol.discoveryCommands) {
            // Each 32-PID bitmap advertises availability of the next 32 PIDs.
            if (base != 0 && !ObdProtocol.supports(bitmaps[base - 0x20] ?: 0L, 32)) break
            val bitmap = ObdProtocol.bitmap(base, exchange(command))
            if (bitmap != null) bitmaps[base] = bitmap
            else if (base == 0) throw IOException("OBD não informou os sensores disponíveis")
        }
        val supported = ObdPid.entries.filter { pid ->
            val base = (pid.code - 1) / 32 * 32
            ObdProtocol.supports(bitmaps[base] ?: 0L, pid.code - base)
        }
        if (supported.isEmpty()) throw IOException("Nenhum sensor solicitado disponível no OBD")
        connected()
        state(TelemetryHub.ONLINE, null)
        var misses = 0
        while (!Thread.currentThread().isInterrupted && !closed) {
            for (pid in supported) {
                if (Thread.currentThread().isInterrupted || closed) return
                val response = exchange(pid.command)
                val stamp = receiptStamp()
                val value = ObdProtocol.decode(pid, response)
                if (value != null) {
                    misses = 0
                    sample(Observation(Source.OBD, stamp, mapOf(
                        pid.field to value, "quality" to "válido",
                        "pid" to pid.command,
                        "rawHex" to response.toByteArray(Charsets.US_ASCII)
                            .joinToString("") { "%02X".format(it.toInt() and 0xff) },
                    )))
                } else if (++misses >= supported.size * 3) {
                    throw IOException("OBD parou de retornar leituras válidas")
                }
            }
        }
    }

    companion object {
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val RETRY_SECONDS = intArrayOf(2, 5, 10, 20, 30)
    }
}
