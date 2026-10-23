package com.omegas.prohub.diagnostics

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * Sessão órfã: 91 de 105 sessões reais terminaram sem `session_stopped` (o carro corta a energia ao desligar a chave).
 * Na próxima abertura o manifesto que ficou `recording:true` é fechado com o motivo "sem stop" e a hora da ÚLTIMA prova
 * de vida (batimento da camada bruta, senão o último evento inteiro; sem nenhuma, null e não 0). Idempotente. Puro.
 */
object OrphanSessions {
    const val REASON = "sem stop (energia cortada ou app encerrado)"
    private val RECORDED = Regex("\"recordedAtMs\"\\s*:\\s*(\\d+)")

    /** True se fechou um órfão agora. */
    @JvmStatic
    fun closeIfOrphan(dir: File): Boolean {
        val file = File(dir, "manifest.json")
        val m = try { JSONObject(file.readText(Charsets.UTF_8)) } catch (_: Exception) { return false }
        if (!m.optBoolean("recording", false)) return false
        val beat = lastHeartbeatWall(dir)
        val event = if (beat == null) lastEventWall(dir) else null
        val stopped = beat ?: event
        m.put("recording", false)
            .put("stoppedAtMs", stopped ?: JSONObject.NULL)
            .put("stopReason", REASON)
            .put("orphan", true)
            .put("lastLifeSign", if (beat != null) "batimento" else if (event != null) "evento" else "nenhuma")
        SessionSummaryFiles.atomicWrite(file, m.toString(2).toByteArray(Charsets.UTF_8))
        return true
    }

    private fun lastHeartbeatWall(dir: File): Long? {
        val files = File(dir, "raw").listFiles { f -> f.name.startsWith("heartbeat.") && f.name.endsWith(".tsv") }?.sortedBy { it.name } ?: return null
        for (f in files.asReversed()) {
            for (line in completeTailLines(f).asReversed()) {
                if (line.startsWith("#")) continue
                line.split('\t').getOrNull(1)?.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun lastEventWall(dir: File): Long? {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("events_") && f.name.endsWith(".jsonl") }?.sortedBy { it.name } ?: return null
        for (f in files.asReversed()) {
            for (line in completeTailLines(f).asReversed()) {
                RECORDED.find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    /** Últimas linhas COMPLETAS (terminadas em \n) do final do arquivo; a linha cortada pela queda de energia fica de fora. */
    private fun completeTailLines(f: File, window: Int = 64 * 1024): List<String> {
        if (!f.isFile || f.length() == 0L) return emptyList()
        RandomAccessFile(f, "r").use { raf ->
            val start = maxOf(0L, raf.length() - window)
            raf.seek(start)
            val buf = ByteArray((raf.length() - start).toInt())
            raf.readFully(buf)
            val text = String(buf, Charsets.UTF_8)
            val end = text.lastIndexOf('\n')
            if (end < 0) return emptyList()
            val lines = text.substring(0, end).split('\n')
            return if (start > 0L) lines.drop(1) else lines
        }
    }
}
