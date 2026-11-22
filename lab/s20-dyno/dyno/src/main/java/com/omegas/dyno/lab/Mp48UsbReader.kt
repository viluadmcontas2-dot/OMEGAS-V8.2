package com.omegas.dyno.lab

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

internal class Mp48UsbReader(
    private val manager: UsbManager?,
    private val state: (String, String?) -> Unit,
    private val sample: (Observation) -> Unit,
) {
    private var worker: Thread? = null
    private var closed = false

    fun devices(): List<UsbDevice> = manager?.deviceList?.values.orEmpty()
        .filter { it.vendorId == 0x10C4 && it.productId == 0xEA60 }

    @Synchronized fun start(device: UsbDevice): Boolean {
        if (closed || worker != null) return false
        state("conectando", null)
        worker = Thread({
            var error: String? = null
            try {
                read(device)
            } catch (exception: Exception) {
                error = exception.message ?: "Conexão MP48 interrompida"
            } finally {
                synchronized(this) {
                    worker = null
                    if (!closed) state(TelemetryHub.OFFLINE, error)
                }
            }
        }, "dyno-mp48").also { it.start() }
        return true
    }

    @Synchronized fun close() {
        closed = true
        worker?.interrupt()
    }

    private fun read(device: UsbDevice) {
        val usb = manager ?: throw IOException("USB indisponível")
        if (devices().none { it == device }) throw IOException("Adaptador MP48 indisponível")
        val port = Cp21xxSerialDriver(device).ports.first()
        val connection = usb.openDevice(device) ?: throw IOException("MP48 sem acesso USB")
        try {
            port.open(connection)
            // Fixed AppSettings values from c3f15fad.
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = false
            port.rts = false
            port.purgeHwBuffers(true, true)
            val wire = Wire(port)
            val init = wire.exchange(Mp48Command.INIT_1)
            val existingSession = Mp48Protocol.payload(Mp48Command.INIT_1, init, 0xCA)
                ?.contentEquals(byteArrayOf(0x08)) == true
            if (!existingSession) {
                if (Mp48Protocol.payload(Mp48Command.INIT_1, init) == null ||
                    Mp48Protocol.payload(Mp48Command.INIT_2, wire.exchange(Mp48Command.INIT_2)) == null
                ) throw IOException("MP48 recusou a abertura da sessão de leitura")
            }

            var online = false
            while (!Thread.currentThread().isInterrupted) {
                val frame = wire.exchange(Mp48Command.TELEMETRY)
                val stamp = receiptStamp()
                val payload = Mp48Protocol.payload(Mp48Command.TELEMETRY, frame)
                val reading = payload?.let(Mp48Protocol::decode)
                    ?: throw IOException("Resposta de telemetria MP48 inválida")
                if (!online) {
                    state(TelemetryHub.ONLINE, null)
                    online = true
                }
                sample(Observation(Source.MP48, stamp, mapOf(
                    "rpm" to reading.rpm, "mapBar" to reading.mapBar,
                    "gasPressureBar" to reading.gasPressureBar, "petrolMs" to reading.petrolMs,
                    "gasMs" to (reading.gasMs ?: UNKNOWN), "fuel" to reading.fuel.label,
                    "fuelByte" to reading.fuelByte,
                    // Raw MP48 telemetry payload, retained in the private vault for later decoding.
                    "rawHex" to payload.joinToString("") { "%02X".format(it.toInt() and 0xff) },
                    "quality" to if (reading.fuel.verified) "válido" else "combustível sem confirmação",
                ), reading.fuel))
            }
        } finally {
            if (port.isOpen) runCatching { port.close() } else connection.close()
        }
    }

    private class Wire(private val port: UsbSerialPort) {
        private val buffer = ByteArray(256)
        private var cursor = 0
        private var end = 0

        fun exchange(command: Mp48Command): ByteArray {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val request = command.bytes()
            port.write(request, 350)
            val deadline = SystemClock.elapsedRealtime() + if (command == Mp48Command.TELEMETRY) 750 else 1200
            val prefix = exact(request.size + 2, deadline)
            if (!prefix.copyOf(request.size).contentEquals(request)) throw IOException("Eco MP48 divergente")
            val length = Mp48Protocol.replyLength(prefix[request.size].toInt() and 255, prefix.last().toInt() and 255)
            if (length > Mp48Protocol.MAX_PAYLOAD) throw IOException("Tamanho da resposta MP48 inválido")
            return prefix + exact(length + 1, deadline)
        }

        private fun exact(size: Int, deadline: Long): ByteArray {
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                if (cursor == end) {
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0) throw IOException("Tempo limite da resposta MP48")
                    end = port.read(buffer, minOf(remaining, 100L).toInt())
                    cursor = 0
                }
                val count = minOf(size - offset, end - cursor)
                buffer.copyInto(bytes, offset, cursor, cursor + count)
                cursor += count
                offset += count
            }
            return bytes
        }
    }
}
