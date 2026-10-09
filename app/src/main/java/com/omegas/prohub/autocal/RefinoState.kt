package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * O que a aba Refino diz sobre o estado, em português simples, em uma só leitura (`refinoState`).
 *
 *  - `phase`: a fase humana ("Lendo a ECU", "Coletando entre as faixas da ECU: 14 de 17 intervalos", "Pronto para gravar 5 pontos",
 *    "Verificando", "Estável", ...);
 *  - `label`: a mesma fase em duas ou três palavras, para o chip e para a aba Agora ("Medindo o GNV", "Medindo a gasolina",
 *    "Curva pronta", "ECU no automático"); diz o combustível que o motor está queimando AGORA, nunca o que ele "deveria" estar;
 *  - `whatNow`: uma frase do que está acontecendo agora;
 *  - `nextAction`: a frase do botão (o que vai fazer ao tocar) e `canAct` (há botão de gravar?);
 *  - `counts`: intervalos coletados/faltando, AutoMatch da ECU lidos, zonas lidas, pontos a gravar;
 *  - `whyNoProposal`: o motivo humano quando não propõe (nulo quando propõe ou quando não há o que propor);
 *  - `technical`: códigos e regras internas (fase, motivo, tipo da ação, combustível vivo). NUNCA no texto do dono.
 *
 * `canAct` é a autoridade única do botão: a UI nunca mostra "Gravar" quando aqui é false, e aqui nunca é true numa fase em que a
 * UI não grava (ECU no automático, lendo, sem ECU, verificando). Nenhum texto de `phase`/`label`/`whatNow`/`nextAction`/
 * `whyNoProposal` cita regra interna (minutos, visitas, intervalos de confiança, episódios, contagem mínima): só a consequência
 * humana. Só observa; nada grava.
 */
object RefinoState {
    /** Combustível vivo como a ECU informa (Mp48Fuel.wireName). */
    const val FUEL_PETROL = "GASOLINA"
    const val FUEL_GAS = "GNV"

    private fun points(n: Int) = "$n ${if (n == 1) "ponto" else "pontos"}"

    /**
     * [fuel] = combustível do último quadro recente da telemetria (GASOLINA, GNV, TRANSICAO, DESLIGADO, CUTOFF) ou nulo sem
     * quadro recente. Só muda as palavras: a fase, a ação e o `canAct` não dependem dele.
     */
    fun build(autopilot: JSONObject, equivalence: JSONObject?, between: JSONArray, stalls: JSONObject?, fuel: String? = null): JSONObject {
        val code = autopilot.optString("phase", "SEM_ECU")
        val truth = autopilot.optJSONObject("ecuTruth")
        // Apenas regiões próprias com pares reais. Não existe obrigação de cobrir 17 faixas da ECU.
        val regions = (0 until between.length()).mapNotNull { between.optJSONObject(it) }
        val total = regions.size
        val collected = regions.count { it.optString("state") == "coletado" }
        val missing = total - collected
        val nextMeasuredRegion = regions.filter { it.optString("state") != "coletado" }
            .maxByOrNull { it.optInt("samples") }
        val nextRange = nextMeasuredRegion?.let {
            "%.2f–%.2f ms".format(java.util.Locale.forLanguageTag("pt-BR"), it.optDouble("fromMs"), it.optDouble("toMs"))
        }
        val perPointReason = nextRange?.let { "Estou medindo em $it; falta confiança somente nesse trecho." }
            ?: "Estou medindo cada região onde houver leituras; nenhuma outra faixa é obrigatória."
        val available = equivalence?.optBoolean("available", false) == true
        val action = equivalence?.optJSONObject("nextAction")
        val kind = action?.optString("kind").orEmpty()
        val actionPoints = action?.optJSONArray("pointIndexes")?.length() ?: 0
        val pointsToWrite = if (kind == "APPLY") actionPoints else 0
        val local = action?.optBoolean("local", false) == true
        val index = if (equivalence != null && !equivalence.isNull("index")) equivalence.optDouble("index") else null
        val onPetrol = fuel == FUEL_PETROL
        val onGas = fuel == FUEL_GAS
        val keepDriving = if (onGas) "Seguir dirigindo no GNV" else "Seguir dirigindo"
        // Fases em que a UI não grava a curva inteira (o ajuste local de engasgo segue a própria regra): o botão e a frase
        // obedecem à mesma tabela, então "Pronto para gravar" nunca aparece sem botão.
        val expiredFrom = autopilot.optString("expiredFrom")
        val pausedNoWrite = code == "TENTATIVA_ENCERRADA" && !local && expiredFrom != "PROPOSTA_PRONTA" && expiredFrom != "ECU_TRABALHANDO"
        val stableNoWrite = code == "ESTAVEL" && !local
        val autoCalReady = autopilot.optBoolean("ecuDone", false) &&
            autopilot.optInt("autoCalEnabled", -1) == 1
        val canWriteProposal = autoCalReady && code !in setOf("SEM_ECU", "LENDO_ECU", "ECU_TRABALHANDO", "VERIFICANDO")

        var phase: String
        var label: String
        var whatNow: String
        var next: String
        var canAct = false
        var why: String? = null
        when {
            code == "SEM_ECU" -> {
                phase = "Sem ECU"; label = phase
                whatNow = "Conecte o cabo e ligue o motor."; next = "Aguardar a ECU"
            }
            code == "LENDO_ECU" || !available -> {
                val failed = !autopilot.isNull("readFailure") && autopilot.optString("readFailure").isNotBlank()
                phase = if (failed) "Leitura da ECU falhou" else "Lendo a ECU"; label = if (failed) "Leitura falhou" else "Lendo a ECU"
                whatNow = if (failed) "A ECU não respondeu à leitura do AutoCal. Tento de novo sozinho; nada muda na ECU."
                else "Estou lendo o AutoMatch e as curvas que a ECU guarda."
                next = "Aguardar a leitura"
            }
            code == "RESTAURAR_TRECHO" || kind == "CONTESTED" -> {
                phase = "Piorou em um trecho"; label = phase
                whatNow = "Um trecho ficou pior com a curva nova."
                next = "Restaurar o trecho"; canAct = true
            }
            code == "VERIFICANDO" || kind == "PROVING" -> {
                phase = "Verificando"; label = phase
                whatNow = if (onPetrol) "A curva nova foi gravada. O carro está na gasolina; confiro o GNV quando ele voltar."
                else "A curva nova foi gravada; confiro se o GNV chegou na gasolina."
                next = keepDriving
            }
            // Proposta pronta, mas a ECU ainda está no automático e pode sobrescrever a curva: a UI não grava, então aqui
            // não há botão. A frase diz a verdade (há proposta; espero a ECU) em vez de prometer "Gravar" sem botão.
            kind == "APPLY" && pointsToWrite > 0 && !autoCalReady -> {
                phase = "A ECU está no automático"; label = "ECU no automático"
                whatNow = "Já tenho ${points(pointsToWrite)} para gravar, mas a ECU ainda está no automático e pode sobrescrever a curva. Espero ela terminar."
                next = "Aguardar a ECU"
                why = truth?.optString("summary")?.takeIf { it.isNotBlank() }
            }
            kind == "APPLY" && pointsToWrite > 0 && stableNoWrite -> {
                phase = "Estável"; label = phase
                whatNow = "Nas faixas medidas o GNV está igual à gasolina. Guardo um ajuste fino de ${points(pointsToWrite)}; só proponho gravar se alguma faixa sair do lugar."
                next = "Nada a fazer"
            }
            kind == "APPLY" && pointsToWrite > 0 && pausedNoWrite -> {
                phase = "Pausado"; label = phase
                whatNow = "Já tenho ${points(pointsToWrite)} para gravar. O acompanhamento pausou sem leitura nova; a próxima leitura libera a gravação."
                next = "Aguardar a próxima leitura"
            }
            kind == "APPLY" && pointsToWrite > 0 -> {
                phase = if (local) "Pronto para corrigir um engasgo" else "Pronto para gravar ${points(pointsToWrite)}"
                label = if (local) "Ajuste pronto" else "Curva pronta"
                whatNow = action?.optString("text").orEmpty()
                next = if (local) "Corrigir ${if (pointsToWrite == 1) "o ponto" else "os $pointsToWrite pontos"} da Curva K" else "Gravar ${points(pointsToWrite)} na Curva K"
                canAct = canWriteProposal
            }
            kind == "FREEZE_REFERENCE" -> {
                phase = "Pronto para salvar a referência"; label = "Referência pronta"
                whatNow = "A ECU já entregou a curva de gasolina dela."
                next = "Salvar a gasolina da ECU como referência"; canAct = true
            }
            code == "ECU_TRABALHANDO" -> {
                phase = "A ECU está no automático"; label = "ECU no automático"
                whatNow = "O Refino continua coletando pontos próprios enquanto a ECU completa o AutoCal."
                next = "Seguir medindo"
                why = "Aguardando apenas a conclusão do AutoCal nativo antes de autorizar gravação."
            }
            code == "ESTAVEL" || (kind == "NOTHING" && index != null && actionPoints == 0) -> {
                phase = "Estável"; label = phase
                whatNow = "O GNV está igual à gasolina."; next = "Nada a fazer"
            }
            // Prova que fechou sem convergir e esgotou as tentativas: não é "estável" nem "coletando"; o cérebro diz onde.
            kind == "NOTHING" && actionPoints > 0 -> {
                phase = "Sem nova proposta nessa faixa"; label = "Sem proposta"
                whatNow = action?.optString("text").orEmpty().ifBlank { "O ajuste nessa faixa não fechou; não proponho de novo ali." }
                next = "Revisar a Curva K nessa faixa"
            }
            code == "TENTATIVA_ENCERRADA" -> {
                phase = "Pausado"; label = phase
                whatNow = autopilot.optString("headline").ifBlank { "O acompanhamento pausou sem leitura nova. Nada foi gravado." }
                next = autopilot.optString("next").ifBlank { "Aguardar a próxima leitura" }
            }
            onPetrol -> {
                // Na gasolina o app mede a referência, não o GNV: dizer "Medindo o GNV" aqui era mentira.
                phase = "Na gasolina: medindo a referência"; label = "Medindo a gasolina"
                whatNow = "O carro está na gasolina. Estou medindo a referência da gasolina; comparo com o GNV quando o carro trocar."
                next = keepDriving
                why = when {
                    missing > 0 -> perPointReason
                    index == null -> "Ainda aprendendo seu motor para afirmar a diferença."
                    else -> null
                }
            }
            else -> {
                phase = if (total > 0) "Medindo $total regiões próprias" else "Coletando"
                label = if (onGas) "Medindo o GNV" else "Medindo"
                whatNow = "Cada região confiável conta sozinha, sem esperar outras faixas da ECU."
                next = keepDriving
                why = when {
                    missing > 0 -> perPointReason
                    index == null -> "Ainda aprendendo seu motor para afirmar a diferença."
                    else -> "Sem ajuste a sugerir agora: a curva está dentro da margem nas faixas medidas."
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
            .put("regionsObserved", total).put("regionsConfirmed", collected).put("regionsLearning", missing)
            .put("ecuAutoMatchCount", autopilot.opt("autoMatchCount") ?: JSONObject.NULL)
            .put("ecuAutoMatchMax", autopilot.opt("maxAutomatch") ?: JSONObject.NULL)
            .put("ecuZonesPetrol", autopilot.opt("petrolZones") ?: JSONObject.NULL)
            .put("ecuZonesGas", autopilot.opt("gasZones") ?: JSONObject.NULL)
            .put("pointsToWrite", pointsToWrite)
        return JSONObject()
            .put("phase", phase).put("label", label).put("whatNow", whatNow).put("nextAction", next).put("canAct", canAct)
            .put("counts", counts)
            .put("whyNoProposal", why ?: JSONObject.NULL)
            .put("reason", why ?: JSONObject.NULL)
            .put("ecuSummary", truth?.optString("summary") ?: JSONObject.NULL)
            .put("technical", JSONObject()
                .put("phase", code).put("reasonCode", autopilot.opt("reasonCode") ?: JSONObject.NULL)
                .put("failureDomain", autopilot.opt("failureDomain") ?: JSONObject.NULL)
                .put("nextActionKind", if (kind.isEmpty()) JSONObject.NULL else kind)
                .put("local", local)
                .put("fuel", fuel ?: JSONObject.NULL)
                .put("index", index ?: JSONObject.NULL)
                .put("judgedUsage", equivalence?.opt("judgedUsage") ?: JSONObject.NULL))
    }

}
