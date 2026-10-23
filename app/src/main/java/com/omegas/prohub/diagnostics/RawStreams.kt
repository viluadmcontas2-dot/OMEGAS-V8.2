package com.omegas.prohub.diagnostics

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Camada BRUTA sem perda do export v2 (`omegas-raw-v2`): texto colunar, append-only, UM arquivo por fluxo e segmento
 * (`<fluxo>.NNNN.tsv`), cada um com cabeçalho `#omegas-raw-v2 ...` que o identifica sozinho. Fluxos:
 *  - `frames_48_01`: todo quadro válido da telemetria, os 34 bytes crus em hex (nada decodificado, nada arredondado);
 *  - `tx`: toda transação serial que NÃO é telemetria (pedido, resposta inteira, status, tempo, erro);
 *  - `mm`: cada mudança do painel da multimídia, com os dois relógios;
 *  - `heartbeat`: a cada [HEARTBEAT_FRAMES] quadros (≈ 5 s a 15 Hz, por contagem e não por relógio): parede, monotônico e
 *    contadores do núcleo, para achar salto de relógio, corte de energia e perdas.
 * Tempo: `e_ms` = elapsedRealtime relativo ao início da sessão (monotônico); a parede entra no batimento e no `mm`.
 * Quem chama (thread da ECU) nunca espera: fila limitada; cheia = descarta e CONTA ([dropped], no batimento).
 * Durabilidade: fsync a cada batimento e a cada [SYNC_EVERY_LINES] linhas (perda máxima ≈ 5 s). Puro: sem Android.
 */
class RawStreams(
    private val dir: File,
    private val sessionKey: String,
    private val bootId: String,
    private val segmentLimitBytes: Long = DEFAULT_SEGMENT_BYTES,
    queueCapacity: Int = 16_384,
    startElapsedMs: Long? = null,
    private val beforeDrain: (() -> Unit)? = null,
) {
    companion object {
        const val FORMAT = "omegas-raw-v2"
        /** Teto bruto de um segmento de texto; o ZIP comprime ≥ 4x mesmo no pior caso (quadros ≈ 11 B comprimidos). */
        const val DEFAULT_SEGMENT_BYTES = 16L * 1024 * 1024
        const val HEARTBEAT_FRAMES = 75
        const val SYNC_EVERY_LINES = 200
        private const val MAX_HEX = 8192

        val COLUMNS = mapOf(
            "frames_48_01" to "e_ms,hex",
            "tx" to "e_ms,class,status,ok,ms,req_hex,resp_hex,reason,error",
            "mm" to "e_ms,wall_ms,kind,json",
            "heartbeat" to "e_ms,wall_ms,engine_frames,engine_failures,recorded_frames,dropped_lines",
        )
    }

    private class Item(val stream: String, val line: String, val syncNow: Boolean = false)
    private class Out(val file: File, val stream: String, val out: FileOutputStream, val writer: BufferedWriter, var bytes: Long)

    private val queue = ArrayBlockingQueue<Item>(queueCapacity)
    private val dropped = AtomicLong(0L)
    private val syncs = AtomicInteger(0)
    private val frames = AtomicLong(0L)
    private val poison = Item("", "")
    @Volatile private var base: Long? = startElapsedMs
    private val outs = HashMap<String, Out>()
    private val segment = HashMap<String, Int>()
    private var linesSinceSync = 0
    @Volatile private var closed = false
    private val thread = Thread({ drain() }, "omegas-raw-streams").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }

    init {
        dir.mkdirs()
        thread.start()
    }

    fun dropped(): Long = dropped.get()
    fun syncCount(): Int = syncs.get()

    private fun rel(elapsedMs: Long): Long {
        val b = base ?: synchronized(this) { base ?: elapsedMs.also { base = it } }
        return elapsedMs - b
    }

    private fun offer(stream: String, line: String, syncNow: Boolean = false) {
        if (closed) return
        if (!queue.offer(Item(stream, line, syncNow))) dropped.incrementAndGet()
    }

    /** Quadro `48 01` válido: 34 bytes crus. Rápido e sem bloqueio (copia o texto, enfileira). */
    fun frame(elapsedMs: Long, payload: ByteArray, wallMs: Long, engineFrames: Long, engineFailures: Long) {
        val r = rel(elapsedMs)
        offer("frames_48_01", "$r\t${hex(payload)}")
        val n = frames.incrementAndGet()
        if (n % HEARTBEAT_FRAMES == 0L) {
            offer("heartbeat", "$r\t$wallMs\t$engineFrames\t$engineFailures\t$n\t${dropped.get()}", syncNow = true)
        }
    }

    /** Transação serial que não é telemetria (leituras de AutoCal, parâmetros, nível, mapa, escritas manuais). */
    fun tx(
        elapsedMs: Long, workClass: String, reason: String, request: ByteArray, ok: Boolean, status: Int,
        payload: ByteArray, ms: Long, error: String,
    ) {
        offer(
            "tx",
            "${rel(elapsedMs)}\t$workClass\t$status\t${if (ok) 1 else 0}\t$ms\t${hex(request)}\t${hex(payload)}\t${clean(reason)}\t${clean(error)}",
        )
    }

    /** Mudança do painel da multimídia ([kind] = car_info, tx17, ...), JSON compacto. */
    fun mm(elapsedMs: Long, wallMs: Long, kind: String, json: String) {
        offer("mm", "${rel(elapsedMs)}\t$wallMs\t${clean(kind)}\t${clean(json)}")
    }

    /** Fecha: esvazia a fila, grava e fsync. Idempotente. */
    fun close() {
        if (closed) return
        closed = true
        queue.put(poison)
        thread.join(10_000L)
    }

    // ---------------------------------------------------------------- escritor

    private fun drain() {
        beforeDrain?.invoke()
        try {
            while (true) {
                val item = queue.take()
                if (item === poison) break
                write(item)
                if (queue.isEmpty()) flushAll()
            }
        } finally {
            flushAll()
            syncAll()
            outs.values.forEach { try { it.writer.close() } catch (_: Exception) { /* fechando/sincronizando: o que importava já foi gravado */ } }
            outs.clear()
        }
    }

    private fun write(item: Item) {
        try {
            var o = outs[item.stream]
            val bytes = item.line.length + 1L
            if (o != null && o.bytes + bytes > segmentLimitBytes) {
                try { o.writer.close() } catch (_: Exception) { /* fechando/sincronizando: o que importava já foi gravado */ }
                outs.remove(item.stream)
                o = null
            }
            if (o == null) o = open(item.stream)
            o.writer.write(item.line)
            o.writer.write("\n")
            o.bytes += bytes
            linesSinceSync += 1
            if (item.syncNow || linesSinceSync >= SYNC_EVERY_LINES) { flushAll(); syncAll() }
        } catch (_: Exception) {
            dropped.incrementAndGet()
        }
    }

    private fun open(stream: String): Out {
        val n = (segment[stream] ?: 0) + 1
        segment[stream] = n
        val file = File(dir, "$stream.${n.toString().padStart(4, '0')}.tsv")
        val fos = FileOutputStream(file, true)
        val w = BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 32 * 1024)
        val header = "#$FORMAT stream=$stream cols=${COLUMNS[stream] ?: "?"} session=$sessionKey boot=$bootId segment=$n base_e=${base ?: 0}\n"
        w.write(header)
        val o = Out(file, stream, fos, w, header.length.toLong())
        outs[stream] = o
        return o
    }

    private fun flushAll() = outs.values.forEach { try { it.writer.flush() } catch (_: Exception) { /* fechando/sincronizando: o que importava já foi gravado */ } }

    private fun syncAll() {
        outs.values.forEach { try { it.out.fd.sync() } catch (_: Exception) { /* fechando/sincronizando: o que importava já foi gravado */ } }
        linesSinceSync = 0
        syncs.incrementAndGet()
    }

    private fun hex(b: ByteArray): String {
        val n = minOf(b.size, MAX_HEX)
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) { val v = b[i].toInt() and 0xFF; sb.append(HEX[v ushr 4]).append(HEX[v and 15]) }
        return sb.toString()
    }

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private val HEX = "0123456789abcdef".toCharArray()
}
