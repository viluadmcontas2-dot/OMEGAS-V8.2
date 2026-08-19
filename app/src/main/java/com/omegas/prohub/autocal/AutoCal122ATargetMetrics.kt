package com.omegas.prohub.autocal

import android.os.Debug
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Receipt observacional do gate 122A para o hardware alvo.
 *
 * Não cria timer, thread, I/O ECU ou decisão científica. O caller apenas oferece
 * oportunidades de amostragem no health tick já existente. A leitura de /proc é
 * limitada por [MIN_SAMPLE_INTERVAL_MS] para não transformar a medição em carga.
 */
object AutoCal122ATargetMetrics {
    private const val TAG = "OMEGAS-122A"
    private const val MIN_SAMPLE_INTERVAL_MS = 10_000L

    data class Snapshot(
        val sessionId: Long,
        val startedAtElapsedMs: Long,
        val lastSampleAtElapsedMs: Long,
        val observationSpanMs: Long,
        val samples: Long,
        val cpuStartMs: Long?,
        val cpuLastMs: Long?,
        val cpuDeltaMs: Long?,
        val cpuShareOfOneCore: Double?,
        val pssKb: Long?,
        val maxPssKb: Long?,
        val rssKb: Long?,
        val maxRssKb: Long?,
        val firstAnchorAtElapsedMs: Long?,
        val timeToFirstAnchorMs: Long?,
    )

    private var sessionId = 0L
    private var startedAtElapsedMs = 0L
    private var lastSampleAtElapsedMs = 0L
    private var samples = 0L
    private var cpuStartMs: Long? = null
    private var cpuLastMs: Long? = null
    private var pssKb: Long? = null
    private var maxPssKb: Long? = null
    private var rssKb: Long? = null
    private var maxRssKb: Long? = null
    private var firstAnchorAtElapsedMs: Long? = null

    @Synchronized
    fun reset() {
        sessionId = 0L
        startedAtElapsedMs = 0L
        lastSampleAtElapsedMs = 0L
        samples = 0L
        cpuStartMs = null
        cpuLastMs = null
        pssKb = null
        maxPssKb = null
        rssKb = null
        maxRssKb = null
        firstAnchorAtElapsedMs = null
    }

    @Synchronized
    fun ensureSession(currentSessionId: Long, nowElapsedMs: Long) {
        if (currentSessionId <= 0L) return
        if (sessionId == currentSessionId && startedAtElapsedMs > 0L) return
        reset()
        sessionId = currentSessionId
        startedAtElapsedMs = nowElapsedMs
        sampleProcessLocked(nowElapsedMs, force = true)
        emitLocked("SESSION_START")
    }

    @Synchronized
    fun sampleProcess(nowElapsedMs: Long) {
        if (sessionId <= 0L || startedAtElapsedMs <= 0L) return
        if (lastSampleAtElapsedMs > 0L && nowElapsedMs - lastSampleAtElapsedMs < MIN_SAMPLE_INTERVAL_MS) return
        sampleProcessLocked(nowElapsedMs, force = false)
        emitLocked("SAMPLE")
    }

    @Synchronized
    fun markFirstAnchor(currentSessionId: Long, observedAtElapsedMs: Long) {
        if (sessionId != currentSessionId || startedAtElapsedMs <= 0L || firstAnchorAtElapsedMs != null) return
        firstAnchorAtElapsedMs = observedAtElapsedMs.coerceAtLeast(startedAtElapsedMs)
        emitLocked("FIRST_ANCHOR")
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val cpuStart = cpuStartMs
        val cpuLast = cpuLastMs
        val anchor = firstAnchorAtElapsedMs
        val span = if (startedAtElapsedMs > 0L && lastSampleAtElapsedMs >= startedAtElapsedMs) {
            lastSampleAtElapsedMs - startedAtElapsedMs
        } else 0L
        val cpuDelta = if (cpuStart != null && cpuLast != null) (cpuLast - cpuStart).coerceAtLeast(0L) else null
        return Snapshot(
            sessionId = sessionId,
            startedAtElapsedMs = startedAtElapsedMs,
            lastSampleAtElapsedMs = lastSampleAtElapsedMs,
            observationSpanMs = span,
            samples = samples,
            cpuStartMs = cpuStart,
            cpuLastMs = cpuLast,
            cpuDeltaMs = cpuDelta,
            cpuShareOfOneCore = if (cpuDelta != null && span > 0L) cpuDelta.toDouble() / span.toDouble() else null,
            pssKb = pssKb,
            maxPssKb = maxPssKb,
            rssKb = rssKb,
            maxRssKb = maxRssKb,
            firstAnchorAtElapsedMs = anchor,
            timeToFirstAnchorMs = anchor?.let { (it - startedAtElapsedMs).coerceAtLeast(0L) },
        )
    }

    @Synchronized
    fun snapshotJson(): JSONObject = snapshot().toJson()

    private fun sampleProcessLocked(nowElapsedMs: Long, force: Boolean) {
        if (!force && lastSampleAtElapsedMs > 0L && nowElapsedMs - lastSampleAtElapsedMs < MIN_SAMPLE_INTERVAL_MS) return
        val cpu = Process.getElapsedCpuTime().coerceAtLeast(0L)
        val pss = try { Debug.getPss().toLong().takeIf { it >= 0L } } catch (_: Throwable) { null }
        val rss = readRssKb()
        if (cpuStartMs == null) cpuStartMs = cpu
        cpuLastMs = cpu
        if (pss != null) {
            pssKb = pss
            maxPssKb = maxOf(maxPssKb ?: pss, pss)
        }
        if (rss != null) {
            rssKb = rss
            maxRssKb = maxOf(maxRssKb ?: rss, rss)
        }
        samples += 1L
        lastSampleAtElapsedMs = nowElapsedMs
    }

    private fun readRssKb(): Long? = try {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLongOrNull()
        }
    } catch (_: Throwable) {
        null
    }

    private fun emitLocked(event: String) {
        try {
            Log.i(TAG, JSONObject().put("event", event).put("metrics", snapshot().toJson()).toString())
        } catch (_: Throwable) {
            // Métrica nunca pode afetar o runtime AutoCal.
        }
    }

    private fun Snapshot.toJson(): JSONObject = JSONObject()
        .put("schema", "autocal-target-performance-v1")
        .put("sessionId", sessionId)
        .put("startedAtElapsedMs", startedAtElapsedMs)
        .put("lastSampleAtElapsedMs", lastSampleAtElapsedMs)
        .put("observationSpanMs", observationSpanMs)
        .put("samples", samples)
        .put("cpuStartMs", cpuStartMs ?: JSONObject.NULL)
        .put("cpuLastMs", cpuLastMs ?: JSONObject.NULL)
        .put("cpuDeltaMs", cpuDeltaMs ?: JSONObject.NULL)
        .put("cpuShareOfOneCore", cpuShareOfOneCore ?: JSONObject.NULL)
        .put("pssKb", pssKb ?: JSONObject.NULL)
        .put("maxPssKb", maxPssKb ?: JSONObject.NULL)
        .put("rssKb", rssKb ?: JSONObject.NULL)
        .put("maxRssKb", maxRssKb ?: JSONObject.NULL)
        .put("firstAnchorAtElapsedMs", firstAnchorAtElapsedMs ?: JSONObject.NULL)
        .put("timeToFirstAnchorMs", timeToFirstAnchorMs ?: JSONObject.NULL)
        .put("sampleIntervalFloorMs", MIN_SAMPLE_INTERVAL_MS)
        .put("appAutomaticWrite", false)
}
