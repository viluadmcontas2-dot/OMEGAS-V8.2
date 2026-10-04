package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * O que a aba Refino diz sobre o estado, em português simples, em uma só leitura (`refinoState`).
 *
 *  - `phase`: a fase humana ("Lendo a ECU", "Coletando entre as faixas da ECU: 14 de 17 intervalos", "Pronto para gravar 5 pontos",
 *    "Verificando", "Estável", ...);
 *  - `whatNow`: uma frase do que está acontecendo agora;
 *  - `nextAction`: a frase do botão (o que vai fazer ao tocar) e `canAct` (há botão de gravar?);
 *  - `counts`: intervalos coletados/faltando, AutoMatch da ECU lidos, zonas lidas, pontos a gravar;
 *  - `whyNoProposal`: o motivo humano quando não propõe (nulo quando propõe ou quando não há o que propor);
 *  - `technical`: códigos e regras internas (fase, motivo, tipo da ação). NUNCA no texto do dono.
 *
 * Nenhum texto de `phase`/`whatNow`/`nextAction`/`whyNoProposal` cita regra interna (minutos, visitas, intervalos de confiança,
 * episódios, contagem mínima): só a consequência humana. Só observa; nada grava.
 */
object RefinoState {
    private fun num(o: JSONObject?, key: String): Int? = if (o == null || !o.has(key) || o.isNull(key)) null else o.optInt(key)

    fun build(autopilot: JSONObject, equivalence: JSONObject?, between: JSONArray, stalls: JSONObject?): JSONObject {
        val code = autopilot.optString("phase", "SEM_ECU")
        val truth = autopilot.optJSONObject("ecuTruth")
        val gaps = (0 until between.length()).mapNotNull { between.optJSONObject(it) }.filter { it.optString("kind") == "gap" }
        val total = gaps.size
        val collected = gaps.count { it.optString("state") == "coletado" }
        val missing = total - collected
        val available = equivalence?.optBoolean("available", false) == true
        val action = equivalence?.optJSONObject("nextAction")
        val kind = action?.optString("kind").orEmpty()
        val pointsToWrite = if (kind == "APPLY") action?.optJSONArray("pointIndexes")?.length() ?: 0 else 0
        val local = action?.optBoolean("local", false) == true
        val index = if (equivalence != null && !equivalence.isNull("index")) equivalence.optDouble("index") else null

        var phase: String
        var whatNow: String
        var next: String
        var canAct = false
        var why: String? = null
        when {
            code == "SEM_ECU" -> { phase = "Sem ECU"; whatNow = "Conecte o cabo e ligue o motor."; next = "Aguardar a ECU" }
            code == "LENDO_ECU" || !available -> {
                phase = "Lendo a ECU"
                whatNow = "Estou lendo o AutoMatch e as curvas que a ECU guarda."
                next = "Aguardar a leitura"
            }
            code == "RESTAURAR_TRECHO" || kind == "CONTESTED" -> {
                phase = "Piorou em um trecho"; whatNow = "Um trecho ficou pior com a curva nova."
                next = "Restaurar o trecho"; canAct = true
            }
            code == "VERIFICANDO" || kind == "PROVING" -> {
                phase = "Verificando"; whatNow = "A curva nova foi gravada; confiro se o GNV chegou na gasolina."; next = "Seguir dirigindo"
            }
            kind == "APPLY" && pointsToWrite > 0 -> {
                phase = if (local) "Pronto para corrigir um engasgo" else "Pronto para gravar $pointsToWrite ${if (pointsToWrite == 1) "ponto" else "pontos"}"
                whatNow = action?.optString("text").orEmpty()
                next = if (local) "Corrigir ${if (pointsToWrite == 1) "o ponto" else "os $pointsToWrite pontos"} da Curva K" else "Gravar $pointsToWrite ${if (pointsToWrite == 1) "ponto" else "pontos"} na Curva K"
                canAct = true
            }
            kind == "FREEZE_REFERENCE" -> {
                phase = "Pronto para salvar a referência"; whatNow = "A ECU já entregou a curva de gasolina dela."
                next = "Salvar a gasolina da ECU como referência"; canAct = true
            }
            code == "ECU_TRABALHANDO" -> {
                phase = "A ECU está no automático"
                whatNow = truth?.optString("summary").orEmpty().ifBlank { "A ECU está calibrando; só observo." }
                next = "Aguardar a ECU"
            }
            code == "ESTAVEL" || (kind == "NOTHING" && index != null) -> {
                phase = "Estável"; whatNow = "O GNV está igual à gasolina."; next = "Nada a fazer"
            }
            else -> {
                phase = if (total > 0) "Coletando entre as faixas da ECU: $collected de $total intervalos" else "Coletando"
                whatNow = "Estou aprendendo seu motor entre as faixas da ECU."
                next = "Seguir dirigindo no GNV"
                why = when {
                    missing > 0 -> "Ainda sem leituras em $missing ${if (missing == 1) "intervalo" else "intervalos"}."
                    index == null -> "Ainda aprendendo seu motor para afirmar a diferença."
                    else -> null
                }
            }
        }
        // Há engasgo repetido sem solução? Diz o porquê de não propor (direção desconhecida etc.).
        val stallReason = stalls?.optJSONArray("regions")?.let { regions ->
            (0 until regions.length()).mapNotNull { regions.optJSONObject(it) }
                .firstOrNull { it.optInt("count") >= StallLocalFix.MIN_REPEATS && !it.has("proposal") }
                ?.optJSONObject("diagnosis")?.optString("text")
        }
        if (why == null && stallReason != null && !canAct) why = stallReason

        val counts = JSONObject()
            .put("intervalsTotal", total).put("intervalsCollected", collected).put("intervalsMissing", missing)
            .put("ecuAutoMatchCount", autopilot.opt("autoMatchCount") ?: JSONObject.NULL)
            .put("ecuAutoMatchMax", autopilot.opt("maxAutomatch") ?: JSONObject.NULL)
            .put("ecuZonesPetrol", autopilot.opt("petrolZones") ?: JSONObject.NULL)
            .put("ecuZonesGas", autopilot.opt("gasZones") ?: JSONObject.NULL)
            .put("pointsToWrite", pointsToWrite)
        return JSONObject()
            .put("phase", phase).put("whatNow", whatNow).put("nextAction", next).put("canAct", canAct)
            .put("counts", counts)
            .put("whyNoProposal", why ?: JSONObject.NULL)
            .put("reason", why ?: JSONObject.NULL)
            .put("ecuSummary", truth?.optString("summary") ?: JSONObject.NULL)
            .put("technical", JSONObject()
                .put("phase", code).put("reasonCode", autopilot.opt("reasonCode") ?: JSONObject.NULL)
                .put("failureDomain", autopilot.opt("failureDomain") ?: JSONObject.NULL)
                .put("nextActionKind", if (kind.isEmpty()) JSONObject.NULL else kind)
                .put("local", local)
                .put("index", index ?: JSONObject.NULL)
                .put("judgedUsage", equivalence?.opt("judgedUsage") ?: JSONObject.NULL))
    }

}
