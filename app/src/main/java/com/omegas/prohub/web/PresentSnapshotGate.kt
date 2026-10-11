package com.omegas.prohub.web

import com.omegas.prohub.learning.LearningGridProjection
import com.omegas.prohub.telemetry.TelemetryStateStore
import org.json.JSONObject

/**
 * Portão do quadro ao vivo da WebView. A UI pergunta a cada 50–200 ms; a telemetria muda bem menos que isso.
 * Se a UI já recebeu a sequência atual desta mesma fonte, a ponte devolve só `{changed:false}` com a idade,
 * sem montar nem serializar o quadro (cada quadro completo custa cópias de JSON na thread da ponte, que é
 * síncrona e segura a thread JavaScript da WebView). Lógica pura: testável na JVM sem Activity.
 */
class PresentSnapshotGate {
    private var servedSource: Any? = null
    private var servedSequence = -1L
    private var interpolationKey: InterpolationKey? = null
    private var interpolation: JSONObject? = null

    private data class InterpolationKey(val rpm: Double, val petrolMs: Double, val mapBar: Double, val valid: Boolean)

    /**
     * Resposta para a UI que já recebeu a sequência [lastSequence] de [store]: leve se nada mudou; senão o quadro
     * completo de [full] (`{ok, revision, data}`) com `changed:true`, e a sequência que foi em `data` passa a ser
     * a entregue. Serviço recriado (outro store) ou sequência que este portão não entregou: quadro completo.
     */
    fun respond(store: TelemetryStateStore, lastSequence: Long, full: () -> JSONObject): String {
        val sequence = store.sequenceNow()
        if (alreadyServed(store, sequence, lastSequence)) {
            val age = store.ageMs().let { if (it == Long.MAX_VALUE) -1L else it }
            return lightJson(sequence, age)
        }
        val envelope = full()
        val data = envelope.optJSONObject("data")
        markServed(store, if (data != null && data.has("sequence")) data.optLong("sequence", -1L) else -1L)
        return envelope.put("changed", true).toString()
    }

    @Synchronized
    private fun alreadyServed(source: Any, currentSequence: Long, lastSequence: Long): Boolean =
        lastSequence >= 0L && lastSequence == currentSequence &&
            servedSource === source && servedSequence == currentSequence

    @Synchronized
    private fun markServed(source: Any, sequence: Long) {
        servedSource = source
        servedSequence = sequence
    }

    /** Resposta leve: a UI reaproveita o último quadro e só anda a idade. */
    private fun lightJson(sequence: Long, ageMs: Long): String =
        "{\"ok\":true,\"changed\":false,\"revision\":$sequence,\"sequence\":$sequence,\"telemetryAgeMs\":$ageMs}"

    /**
     * Interpolação da célula atual: só é recalculada quando RPM, injeção, MAP ou validade mudam. A chave usa os
     * valores como chegam da ECU (já quantizados): arredondar mais poderia trocar a célula de quem está colado numa
     * borda do eixo. Devolve uma cópia rasa com a sequência e o instante deste quadro; o cache não é alterado.
     */
    @Synchronized
    fun interpolation(rpm: Double, petrolMs: Double, mapBar: Double, valid: Boolean, sequence: Long, updatedAt: Long): JSONObject {
        val key = InterpolationKey(rpm, petrolMs, mapBar, valid)
        val cached = interpolation?.takeIf { key == interpolationKey }
            ?: LearningGridProjection.liveInterpolationJson(
                rpm = rpm,
                petrolMs = petrolMs,
                mapBar = mapBar,
                sequence = sequence,
                updatedAt = updatedAt,
                telemetryValid = valid,
            ).also {
                interpolationKey = key
                interpolation = it
            }
        val result = JSONObject()
        cached.keys().forEach { name -> result.put(name, cached.opt(name)) }
        return result.put("sequence", sequence).put("updatedAt", updatedAt)
    }
}
