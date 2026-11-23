package com.omegas.dyno.lab

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A bounded, local, append-only telemetry feed consumed by the Termux investigator.
 * Never sends data to the network. Raw payloads never leave the S20 automatically.
 * The worker owns all I/O; no disk access on USB/OBD acquisition threads.
 */
internal class TelemetryStream(private val context: Context) {
    private val queue = LinkedBlockingQueue<String>(8192)
    private var lastMp48AtNs = 0L
    private var lastFlag: Int? = null
    private val dropped = AtomicLong()
    private val writer = Thread(::writeLoop, "omegas-telemetry-stream").apply {
        isDaemon = true
        start()
    }
    @Synchronized fun record(observation: Observation) {
        if (observation.source == Source.MP48) {
            val now = observation.stamp.monoNs
            val flag = observation.values["fuelByte"] as? Int
            if (lastFlag == flag && now - lastMp48AtNs < 110_000_000L) return
            lastFlag = flag
            lastMp48AtNs = now
        }
        val item = JSONObject()
            .put("v", 1)
            .put("source", observation.source.key)
            .put("monoNs", observation.stamp.monoNs)
            .put("t", observation.stamp.wallMs)
            .put("values", JSONObject(observation.values))
            .toString() + "\n"
        if (!queue.offer(item)) dropped.incrementAndGet()
    }

    private fun createOutput(): OutputStream {
        if (Build.VERSION.SDK_INT < 29) {
            throw IllegalStateException("Saída automática de telemetria requer Android 10 ou superior")
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "stream-${System.currentTimeMillis()}.ndjson")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-ndjson")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Omegas/Inteligencia/")
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Não foi possível criar a transmissão local")
        return BufferedOutputStream(
            context.contentResolver.openOutputStream(uri, "wa")
                ?: throw IllegalStateException("Não foi possível abrir a transmissão local"), 32768
        )
    }

    private fun writeLoop() {
        var output: OutputStream? = null
        var bufferCount = 0
        while (!Thread.currentThread().isInterrupted) {
            try {
                val line = queue.poll(800, TimeUnit.MILLISECONDS)
                if (line == null) {
                    if (bufferCount > 0) output?.flush()
                    bufferCount = 0
                    continue
                }
                if (output == null) output = createOutput()
                output.write(line.toByteArray(Charsets.UTF_8))
                bufferCount++
                if (bufferCount >= 12) {
                    output.flush()
                    bufferCount = 0
                }
            } catch (_: InterruptedException) {
                break
            } catch (exception: Exception) {
                android.util.Log.e("OmegasTelemetry", "Falha no arquivo da telemetria", exception)
                runCatching { output?.close() }
                output = null
                bufferCount = 0
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }
        runCatching { output?.flush(); output?.close() }
    }
}
