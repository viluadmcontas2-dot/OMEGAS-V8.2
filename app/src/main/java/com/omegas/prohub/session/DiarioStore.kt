package com.omegas.prohub.session

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Diário de eventos em arquivo (uma linha JSON por evento), sem relógio e sem rede.
 * Escrita atômica (arquivo temporário + troca) e poda nos últimos [MAX_EVENTS] eventos.
 * Leitura tolerante: uma linha corrompida é descartada, o resto continua valendo.
 */
class DiarioStore(
    private val file: File,
) {
    companion object {
        const val MAX_EVENTS = 2000
        const val FORMAT = "omegas-diario-v1"
    }

    /** Eventos válidos, na ordem do arquivo. Arquivo ausente ou ilegível = lista vazia. */
    fun load(): List<JSONObject> {
        if (!file.isFile) return emptyList()
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return emptyList()
        }
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                try {
                    JSONObject(line).takeIf { it.has("seq") && it.has("leituras") }
                } catch (_: Exception) {
                    null
                }
            }
            .toList()
            .takeLast(MAX_EVENTS)
    }

    /** Substitui o diário pelos últimos [MAX_EVENTS] eventos. Entradas que não são objetos são ignoradas. */
    fun save(events: List<JSONObject>) {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        val body = events.takeLast(MAX_EVENTS).joinToString("\n") { it.toString() }
        temporary.writeText(if (body.isEmpty()) "" else body + "\n", Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Formato trocado com a interface: {"format":"omegas-diario-v1","eventos":[...]}. */
    fun loadJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("eventos", JSONArray(load()))
        .toString()

    /** Recebe o mesmo formato. Formato desconhecido ou JSON ruim não apaga o diário existente. */
    fun saveJson(snapshotJson: String): Boolean {
        val root = try {
            JSONObject(snapshotJson)
        } catch (_: Exception) {
            return false
        }
        if (root.optString("format") != FORMAT) return false
        val array = root.optJSONArray("eventos") ?: return false
        val events = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        return try {
            save(events)
            true
        } catch (_: Exception) {
            false
        }
    }
}
