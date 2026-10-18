package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Último param_snapshot da sessão USB, para a aba Ferramentas > Avançado. Só guarda o que a ECU respondeu: de outra
 * sessão, ou nunca lido, devolve "não lido" (a tela mostra "—", nunca 0). Quem lê é o EcuParameterReader (uma vez por
 * sessão, por evento) ou o toque do dono na prévia da escrita (leitura ao vivo de FLAG_CONF3).
 */
class AdvancedParamsStore {
    private val lock = Any()
    private var sessionId = 0L
    private var trigger = ""
    private var params = JSONObject()

    fun update(session: Long, snapshot: JSONObject) = synchronized(lock) {
        sessionId = session
        trigger = snapshot.optString("trigger")
        params = JSONObject(snapshot.optJSONObject("params")?.toString() ?: "{}")
    }

    /** Troca só um vetor de 16 bits (leitura ao vivo na prévia ou readback confirmado). */
    fun replaceVector(session: Long, name: String, address: Int, words: IntArray, newTrigger: String) = synchronized(lock) {
        if (sessionId != session) {
            sessionId = session
            params = JSONObject()
        }
        trigger = newTrigger
        params.put(
            name,
            JSONObject().put("address", address).put("state", "OK").put("values", JSONArray(words.toList())).put("detail", ""),
        )
    }

    /**
     * A leitura de volta falhou: o que a ECU tem agora é desconhecido. Troca a entrada por UNKNOWN (sem valores) para a tela
     * não exibir o valor velho como "LIDO DA ECU". Só vale para a sessão corrente do cofre.
     */
    fun markUnknown(session: Long, name: String, address: Int, newTrigger: String) = synchronized(lock) {
        if (sessionId != session) return@synchronized
        trigger = newTrigger
        params.put(
            name,
            JSONObject().put("address", address).put("state", "UNKNOWN").put("values", JSONObject.NULL).put("detail", "leitura de volta falhou"),
        )
    }

    fun json(currentSession: Long): JSONObject = synchronized(lock) {
        val valid = currentSession > 0L && currentSession == sessionId && params.length() > 0
        JSONObject()
            .put("ok", true)
            .put("read", valid)
            .put("trigger", if (valid) trigger else JSONObject.NULL)
            .put("params", if (valid) JSONObject(params.toString()) else JSONObject())
    }
}
