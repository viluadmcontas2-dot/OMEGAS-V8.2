package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Documentos que tornam o pacote AUTOEXPLICADO (para humano e agente): LEIA-ME.md, PERGUNTAS-E-RESPOSTAS.md,
 * MAPA-DO-CARRO.json/.md, COMPARATIVO.json/.md e LINHA-DO-TEMPO.md. Gerados pelo app a partir do resumo da PRÓPRIA sessão
 * (autocontida; ligações por ID, tolerantes a ausência). O catálogo de arquivos e de campos mora aqui e é conferido por
 * teste contra um pacote real: arquivo ou campo novo sem descrição faz o teste falhar (o LEIA-ME não envelhece).
 * Desconhecido é "—"/null, nunca 0. Puro: sem Android.
 */
object SessionGuide {
    const val README = "LEIA-ME.md"
    const val QA = "PERGUNTAS-E-RESPOSTAS.md"
    const val TIMELINE = "LINHA-DO-TEMPO.md"
    const val MAP_JSON = "MAPA-DO-CARRO.json"
    const val MAP_MD = "MAPA-DO-CARRO.md"
    const val CMP_JSON = "COMPARATIVO.json"
    const val CMP_MD = "COMPARATIVO.md"
    val DOCS = listOf(README, QA, TIMELINE, MAP_JSON, MAP_MD, CMP_JSON, CMP_MD)
    const val MAP_SCHEMA = "omegas-car-map-v1"

    class FileDoc(val name: String, val pattern: Regex, val what: String)

    /** Todo arquivo que pode aparecer num pacote (completo, partes ou resumo). `name` é o texto citado no LEIA-ME. */
    val FILES: List<FileDoc> = listOf(
        FileDoc(README, Regex("""(^|/)LEIA-ME\.md$"""), "este guia: o que é cada arquivo, escalas, relógios, glossário e o que é conhecido ou desconhecido."),
        FileDoc(QA, Regex("""(^|/)PERGUNTAS-E-RESPOSTAS\.md$"""), "as perguntas de sempre (qual tanque rendeu mais? o mapa mudou? melhorou ou piorou?) já respondidas com os números desta sessão e o jeito de conferir."),
        FileDoc(TIMELINE, Regex("""(^|/)LINHA-DO-TEMPO\.md$"""), "os marcos da sessão em ordem (cabo, central reconhecida, calibração do nível, abastecimento, gravações, quedas, fim)."),
        FileDoc(MAP_JSON, Regex("""(^|/)MAPA-DO-CARRO\.json$"""), "o Mapa K e a Curva K ATIVOS (valores completos) e o HISTÓRICO de cada mudança de ponto (antes, depois, quando, quem causou, em qual tanque)."),
        FileDoc(MAP_MD, Regex("""(^|/)MAPA-DO-CARRO\.md$"""), "a mesma coisa em tabelas legíveis."),
        FileDoc(CMP_JSON, Regex("""(^|/)COMPARATIVO\.json$"""), "este tanque contra o anterior e contra o melhor conhecido: rendimento, tempo parado × andando, melhorou ou piorou e a causa provável. Só quando a cobertura é comparável; senão INCONCLUSIVO."),
        FileDoc(CMP_MD, Regex("""(^|/)COMPARATIVO\.md$"""), "o comparativo em poucas linhas."),
        FileDoc("resumo/session_summary.json", Regex("""(^|/)(resumo/)?session_summary\.json$"""), "o resumo em números (identidade, relógio, tempos, km por fonte, nível, tanques, mapa/curva, multimídia, lacunas, qualidade). Comece por ele para análises."),
        FileDoc("resumo/series_1hz.csv", Regex("""(^|/)(resumo/)?series_1hz\.csv$"""), "uma linha por segundo com dado (rpm, MAP, velocidade e fonte, nível, tempos de injeção, combustível, multimídia). Célula vazia = desconhecido."),
        FileDoc("resumo/manifest.json", Regex("""(^|/)(resumo/)?manifest\.json$"""), "índice com bytes e SHA-256 dos arquivos do resumo e dos brutos, e as colunas de cada fluxo bruto."),
        FileDoc("manifest.json", Regex("""(^|/)manifest\.json$"""), "manifesto do log v1 (omegas-session-log-v1): identidade, início/fim, contagens e motivo de parada."),
        FileDoc("session_summary.json", Regex("""(^|/)session_summary\.json$"""), "resumo semântico ANTIGO da sessão (omegas-session-semantic-v1); o resumo novo está em resumo/."),
        FileDoc("events_NNNN.jsonl", Regex("""(^|/)events_\d{4}(\.from_\d+)?\.jsonl$"""), "log v1: um evento por linha (type, source, data). Em partes, o sufixo .from_<byte> diz de onde o trecho começa."),
        FileDoc("raw/frames_48_01.NNNN.tsv", Regex("""(^|/)raw/frames_48_01\.\d{4}(\.from_\d+)?\.tsv$"""), "BRUTO: os 34 bytes de CADA quadro 48 01 da central (e_ms, hex)."),
        FileDoc("raw/tx.NNNN.tsv", Regex("""(^|/)raw/tx\.\d{4}(\.from_\d+)?\.tsv$"""), "BRUTO: toda conversa serial que não é telemetria (leituras e escritas manuais), pedido e resposta inteiros."),
        FileDoc("raw/mm.NNNN.tsv", Regex("""(^|/)raw/mm\.\d{4}(\.from_\d+)?\.tsv$"""), "BRUTO: cada mudança do painel da multimídia e das 17 transações do Car Info."),
        FileDoc("raw/heartbeat.NNNN.tsv", Regex("""(^|/)raw/heartbeat\.\d{4}(\.from_\d+)?\.tsv$"""), "BRUTO: batimento a cada 75 quadros (acha salto de relógio, corte de energia e linhas descartadas)."),
        FileDoc("README_PARA_IA.txt", Regex("""(^|/)README_PARA_IA\.txt$"""), "descrição técnica de cada tipo de evento do log v1."),
        FileDoc("RESUMO-DA-VIAGEM.html", Regex("""(^|/)RESUMO-DA-VIAGEM\.html$"""), "resumo visual da viagem para abrir no navegador."),
        FileDoc("anti-apagao.json", Regex("""(^|/)anti-apagao\.json$"""), "análise do anti-apagão desta sessão (onde a central apagou e por quê)."),
        FileDoc("tank_ledger.json", Regex("""(^|/)tank_ledger\.json$"""), "livro-caixa dos tanques entre sessões (ciclos, rendimento PROXY e cobertura)."),
        FileDoc("parte.json", Regex("""(^|/)parte\.json$"""), "só em pacotes em PARTES: quais bytes cada parte traz, SHA-256 e como juntar."),
        FileDoc("export_summary.json", Regex("""(^|/)export_summary\.json$"""), "só no 'Exportar tudo': bytes e SHA-256 exatos de cada arquivo incluído."),
    )

    /** Com a subpasta `resumo/` o arquivo é do resumo novo; sem ela, na pasta cheia é o antigo (raiz) e no pacote leve é o novo (ambos descritos). */
    fun describeFile(path: String): FileDoc? {
        val inResumo = path.contains("resumo/")
        return FILES.firstOrNull { it.pattern.containsMatchIn(path) && (it.name.startsWith("resumo/") == inResumo) }
            ?: FILES.firstOrNull { it.pattern.containsMatchIn(path) }
    }

    /** Campos dos manifestos e documentos. Chave = arquivo; valor = campo → o que é. */
    val FIELDS: Map<String, Map<String, String>> = mapOf(
        "resumo/manifest.json" to linkedMapOf(
            "schema" to "versão do manifesto (omegas-session-summary-manifest-v1).",
            "sessionId" to "nome da sessão (data da parede + aparelho; frágil, use sessionUuid/sessionKey).",
            "sessionUuid" to "identificador estável da sessão (UUID); duas cópias da mesma sessão têm o mesmo.",
            "startMs" to "início em ms de parede (pode estar errado se clockSuspect).",
            "sessionKey" to "chave estável aparelho + boot + início monotônico; — nas sessões antigas.",
            "bootId" to "identificador do boot do aparelho; — se o Android não deixou ler.",
            "final" to "true = resumo fechado; false = parcial (sessão ainda gravando ou cortada).",
            "closedCleanly" to "true = a sessão registrou 'parar'; false = terminou sem parar (energia cortada); — se ainda não fechou.",
            "summarySchema" to "versão do resumo (omegas-session-summary-v1).",
            "appVersion" to "versão do app.", "commit" to "commit REAL do app que gravou (a versão repete entre builds).",
            "clockSuspect" to "true = o relógio de parede é suspeito (use só o tempo monotônico).",
            "files" to "arquivos do resumo com name, bytes e sha256.",
            "rawFiles" to "arquivos events_*.jsonl com name, bytes e sha256 (sha256 — se ainda não calculado).",
            "rawFormat" to "formato dos fluxos brutos (omegas-raw-v2).",
            "streams" to "por fluxo bruto: columns (nomes das colunas, constantes) e files (name, bytes, sha256).",
            "packageSha256" to "SHA-256 sobre a lista de arquivos do resumo (confere o pacote leve).",
            "guide" to "os documentos explicativos gerados (LEIA-ME etc.) com bytes e sha256; comece por guide.readFirst.",
        ),
        "manifest.json" to linkedMapOf(
            "format" to "omegas-session-log-v1.", "schemaVersion" to "versão do esquema do manifesto.",
            "sessionId" to "nome da sessão.", "sessionUuid" to "UUID estável da sessão.", "sessionKey" to "chave estável da sessão; — nas antigas.",
            "bootId" to "boot do aparelho.", "startElapsedMs" to "elapsedRealtime (monotônico) no início.",
            "createdAtMs" to "criação em ms de parede.", "createdAtUtc" to "criação em UTC legível.", "reason" to "por que a sessão começou.",
            "deviceId" to "identificador do aparelho.", "deviceName" to "nome do aparelho.",
            "capture" to "como foi capturado (telemetria a cada N ms, snapshot completo, USB cru, teto de MB).",
            "metadata" to "versão/commit do app, perfil do carro, hashes do mapa/curva no início (— quando desconhecidos).",
            "startedAtMs" to "início em ms de parede.", "stoppedAtMs" to "fim em ms de parede (0 = não parou).", "stoppedAtUtc" to "fim em UTC; — se não parou.",
            "recording" to "true = ainda gravando.", "events" to "quantos eventos foram gravados.", "droppedEvents" to "eventos descartados para proteger a comunicação (> 0 = lacuna).",
            "bytes" to "bytes gravados no log.", "segments" to "quantos events_NNNN.jsonl existem.", "stopReason" to "por que parou; vazio = não parou.",
            "lastError" to "último erro de gravação.", "petrolTicks" to "contagem de ticks em gasolina.", "cngTicks" to "contagem de ticks em GNV.",
            "orphan" to "true = sessão fechada na abertura seguinte (energia cortada).", "lastLifeSign" to "hora da última prova de vida de uma sessão órfã.",
            "exportMode" to "INCREMENTAL_ACTIVE ou FULL_STOPPED.", "exportEventSegmentStart" to "primeiro segmento no export ativo.", "exportEventSegmentEnd" to "último segmento no export ativo.",
            "exportStartSequenceExclusive" to "o export traz eventos depois desta sequência.", "exportBoundarySequence" to "sequência do corte imutável.", "exportBoundaryAtMs" to "hora do corte.",
        ),
        "parte.json" to linkedMapOf(
            "format" to "omegas-session-part-v1.", "sessionId" to "nome da sessão.", "part" to "número da parte.", "final" to "true = última parte.",
            "createdAtMs" to "quando a parte foi feita.", "sessionUuid" to "UUID da sessão.", "startMs" to "início da sessão.", "sessionKey" to "chave estável.",
            "partIndex" to "índice da parte.", "isLast" to "última parte.", "totalParts" to "total (— até a última sair).", "rawBytes" to "bytes brutos desta parte.",
            "contentSha256" to "SHA-256 do conteúdo da parte.", "single" to "true = sessão inteira num ZIP só.", "howToJoin" to "como juntar as partes.", "entries" to "cada arquivo/trecho com bytes e SHA-256.",
        ),
        "export_summary.json" to linkedMapOf(
            "exportedAtMs" to "hora do export.", "exportedAtUtc" to "hora do export em UTC.", "session" to "estado da sessão no export.", "appSettings" to "configuração do app no export.",
            "files" to "path, bytes e sha256 de cada arquivo.", "immutableBoundary" to "true = corte imutável usado.", "exportMode" to "INCREMENTAL_ACTIVE ou FULL_STOPPED.",
            "exportStartSequenceExclusive" to "export traz eventos depois desta sequência.", "exportEndSequenceInclusive" to "até esta sequência.", "exportedEventCount" to "quantos eventos vieram.", "integrity" to "como a integridade foi calculada.",
        ),
        MAP_JSON to linkedMapOf(
            "schema" to "omegas-car-map-v1.", "read" to "como ler este arquivo.", "identity" to "carro/perfil, central, firmware, app/commit e sessão.",
            "active" to "Mapa K (map) e Curva K (curve) ATIVOS: known, hashes, valores completos, valores no início.", "history" to "mudanças: changes (por célula/ponto), meta, net (início → fim) e note.",
            "changes" to "cada lote: tMs, tankIndex, tank (ciclo), kind, cause, causeText, reason, adjustmentId, oldHash, newHash, cellsChanged e changes (antes/depois).",
        ),
        CMP_JSON to linkedMapOf(
            "schema" to "omegas-comparison-v1.", "sessionId" to "sessão de referência.", "rule" to "regra de comparação.", "verdict" to "MELHOROU, PIOROU, IGUAL ou INCONCLUSIVO.",
            "current" to "tanque desta sessão.", "previous" to "tanque anterior.", "best" to "melhor tanque fechado comparável.", "currentIsBest" to "este tanque é o melhor? (— se não há comparável).",
            "reasons" to "por que é inconclusivo.", "comparisons" to "cada comparação: against, comparable, primaryMetric, metrics, verdict, reasons, probableCause (HYPOTHESIS).",
        ),
    )

    // ---------------------------------------------------------------- formatação

    private fun pt(v: Double, digits: Int = 1) = String.format(Locale("pt", "BR"), "%.${digits}f", v)
    private fun dur(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
    private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
    private fun str(v: Any?): String = if (v == null || v == JSONObject.NULL) "—" else v.toString()

    // ---------------------------------------------------------------- MAPA-DO-CARRO

    fun carMapJson(summary: JSONObject, ledger: JSONObject?): JSONObject {
        val id = summary.optJSONObject("identity") ?: JSONObject()
        val sid = id.optString("sessionId")
        val cycles = ledger?.optJSONArray("cycles")
        fun cycleOf(tank: Int): Any {
            if (cycles == null) return JSONObject.NULL
            for (i in cycles.length() - 1 downTo 0) {
                val refs = cycles.getJSONObject(i).optJSONArray("segmentRefs") ?: continue
                for (k in 0 until refs.length()) { val r = refs.getJSONObject(k); if (r.optString("sessionId") == sid && r.optInt("segmentIndex") == tank) return cycles.getJSONObject(i).optInt("index") }
            }
            return JSONObject.NULL
        }
        val log = summary.optJSONArray("changeLog") ?: JSONArray()
        val changes = JSONArray()
        for (i in 0 until log.length()) {
            val b = JSONObject(log.getJSONObject(i).toString())
            b.put("sessionId", sid)
            b.put("tank", JSONObject().put("sessionSegment", b.opt("tankIndex")).put("cycleIndex", cycleOf(b.optInt("tankIndex", -1))))
            changes.put(b)
        }
        val car = summary.optJSONObject("carMap") ?: JSONObject()
        val build = summary.optJSONObject("build") ?: JSONObject()
        val ctx = summary.optJSONObject("context") ?: JSONObject()
        val ecu = summary.optJSONObject("ecu") ?: JSONObject()
        return JSONObject().put("schema", MAP_SCHEMA)
            .put("read", "active = mapa/curva em uso (known:false = não foi lido nesta sessão, nunca 0); history.changes = cada mudança por célula/ponto; history.net = o que ficou diferente do início ao fim.")
            .put("identity", JSONObject()
                .put("car", JSONObject().put("profile", ctx.opt("carProfile") ?: JSONObject.NULL).put("vin", ctx.opt("vin") ?: JSONObject.NULL))
                .put("central", JSONObject().put("moduleVersion", ecu.opt("moduleVersion") ?: JSONObject.NULL).put("serial", JSONObject.NULL).put("firmware", JSONObject.NULL)
                    .put("note", "serial e firmware ainda não são lidos pelo app (— = desconhecido)"))
                .put("app", JSONObject().put("version", build.opt("appVersion") ?: JSONObject.NULL).put("commit", build.opt("commit") ?: JSONObject.NULL).put("buildType", build.opt("buildType") ?: JSONObject.NULL))
                .put("session", JSONObject().put("sessionId", sid).put("sessionUuid", id.opt("sessionUuid") ?: JSONObject.NULL).put("sessionKey", id.opt("sessionKey") ?: JSONObject.NULL)
                    .put("startUtc", id.opt("startUtc") ?: JSONObject.NULL).put("final", summary.optBoolean("final"))))
            .put("active", JSONObject().put("map", car.optJSONObject("map") ?: JSONObject()).put("curve", car.optJSONObject("curve") ?: JSONObject()))
            .put("history", JSONObject().put("changes", changes).put("meta", summary.optJSONObject("changeLogMeta") ?: JSONObject())
                .put("net", summary.optJSONObject("netChange") ?: JSONObject())
                .put("note", "Cada lote liga à sessão (sessionId) e ao tanque (tank.sessionSegment dentro da sessão; tank.cycleIndex no livro-caixa, null se ainda não costurado)."))
    }

    fun carMapMd(j: JSONObject): String {
        val sb = StringBuilder("# Mapa do carro\n\n")
        val ident = j.getJSONObject("identity")
        val app = ident.getJSONObject("app"); val s = ident.getJSONObject("session"); val c = ident.getJSONObject("central")
        sb.append("- Sessão: ${str(s.opt("sessionId"))} (${if (s.optBoolean("final")) "fechada" else "parcial"})\n")
        sb.append("- Carro/perfil: ${str(ident.getJSONObject("car").opt("profile"))} · Central: módulo versão ${str(c.opt("moduleVersion"))}, firmware —\n")
        sb.append("- App: ${str(app.opt("version"))} · commit ${str(app.opt("commit"))}\n\n")
        val map = j.getJSONObject("active").optJSONObject("map") ?: JSONObject()
        sb.append("## Mapa K ativo\n\n")
        val rows = map.optJSONArray("rows")
        if (rows == null) sb.append("Não foi lido nesta sessão (— = desconhecido).\n\n") else {
            val axes = map.getJSONObject("axes")
            val rpm = axes.getJSONArray("rpmBins"); val ms = axes.getJSONArray("petrolMsBins")
            sb.append("Linhas = ms de gasolina, colunas = rpm. Hash: ${str(map.opt("hashLast"))}\n\n| ms \\ rpm | ${(0 until rpm.length()).joinToString(" | ") { rpm.getInt(it).toString() }} |\n|---|${"---|".repeat(rpm.length())}\n")
            for (r in 0 until rows.length()) sb.append("| ${pt(ms.getDouble(r), 1)} | ${(0 until rows.getJSONArray(r).length()).joinToString(" | ") { rows.getJSONArray(r).getInt(it).toString() }} |\n")
            sb.append("\n")
        }
        val curve = j.getJSONObject("active").optJSONObject("curve") ?: JSONObject()
        sb.append("## Curva K ativa\n\n")
        val pts = curve.optJSONArray("points")
        if (pts == null) sb.append("Não foi lida nesta sessão (— = desconhecido).\n\n") else {
            sb.append("Hash: ${str(curve.opt("hashLast"))}\n\n| ponto | ms gasolina | fator |\n|---|---|---|\n")
            for (i in 0 until pts.length()) { val p = pts.getJSONObject(i); sb.append("| ${i + 1} | ${p.dbl("petrolMs")?.let { pt(it, 2) } ?: "—"} | ${p.dbl("factor")?.let { pt(it, 3) } ?: "—"} |\n") }
            sb.append("\n")
        }
        sb.append("## O que mudou\n\n")
        val ch = j.getJSONObject("history").getJSONArray("changes")
        if (ch.length() == 0) sb.append("Nenhuma mudança registrada nesta sessão.\n") else {
            for (i in 0 until minOf(ch.length(), 40)) {
                val b = ch.getJSONObject(i)
                sb.append("- ${dur(b.optLong("tMs"))} · ${if (b.optString("kind") == "MAP_K") "Mapa K" else "Curva K"} · ${b.optInt("cellsChanged")} · ${b.optString("causeText")} · tanque ${str(b.optJSONObject("tank")?.opt("cycleIndex"))}\n")
                val cells = b.optJSONArray("changes") ?: JSONArray()
                for (k in 0 until minOf(cells.length(), 3)) {
                    val x = cells.getJSONObject(k)
                    sb.append(if (b.optString("kind") == "MAP_K") "  - linha ${x.optInt("row") + 1} (${str(x.opt("petrolMs"))} ms) × coluna ${x.optInt("column") + 1} (${str(x.opt("rpm"))} rpm): ${str(x.opt("before"))} → ${str(x.opt("after"))}\n"
                    else "  - ponto ${x.optInt("index") + 1} (${str(x.opt("petrolMs"))} ms): ${str(x.opt("beforeFactor"))} → ${str(x.opt("afterFactor"))}\n")
                }
            }
            if (ch.length() > 40) sb.append("- … e mais ${ch.length() - 40} (veja $MAP_JSON)\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- COMPARATIVO

    fun comparisonMd(j: JSONObject): String {
        val sb = StringBuilder("# Comparativo\n\n**Veredito: ${j.optString("verdict")}**\n\n")
        val reasons = j.optJSONArray("reasons")
        if (reasons != null) for (i in 0 until reasons.length()) sb.append("- ${reasons.getString(i)}\n")
        val cs = j.optJSONArray("comparisons") ?: JSONArray()
        for (i in 0 until cs.length()) {
            val c = cs.getJSONObject(i)
            sb.append("\n## Contra o tanque ${if (c.optString("against") == "previous") "anterior" else "de melhor rendimento"}: ${c.optString("verdict")}\n")
            val rs = c.optJSONArray("reasons")
            if (rs != null) for (k in 0 until rs.length()) sb.append("- ${rs.getString(k)}\n")
            val ms = c.optJSONArray("metrics") ?: JSONArray()
            for (k in 0 until ms.length()) {
                val m = ms.getJSONObject(k)
                sb.append("- ${m.optString("name")}: agora ${m.dbl("current")?.let { pt(it, 3) } ?: "—"}, antes ${m.dbl("other")?.let { pt(it, 3) } ?: "—"}${m.dbl("deltaPct")?.let { " (" + pt(it, 1) + "%)" } ?: ""} → ${m.optString("verdict")}\n")
            }
            c.optJSONObject("probableCause")?.let { sb.append("- Causa provável (hipótese): ${it.optString("text")}\n") }
        }
        sb.append("\nRendimento é PROXY: o sensor de nível é uma escada; mais é melhor; diferença menor que 10% = igual.\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- LINHA-DO-TEMPO

    fun timelineMd(summary: JSONObject): String {
        val sb = StringBuilder("# Linha do tempo da sessão\n\n")
        val id = summary.optJSONObject("identity") ?: JSONObject()
        sb.append("Sessão ${str(id.opt("sessionId"))} · início ${str(id.opt("startUtc"))} · duração ${id.lng("elapsedMs")?.let { dur(it) } ?: "—"} · ${if (summary.optBoolean("final")) "fechada" else "parcial (ainda gravando ou cortada)"}\n\n")
        val all = summary.optJSONArray("timeline") ?: JSONArray()
        val items = ArrayList<JSONObject>()
        for (i in 0 until all.length()) items.add(all.getJSONObject(i))
        val gaps = summary.optJSONObject("gaps")?.optJSONArray("items")
        val extra = ArrayList<JSONObject>()
        if (gaps != null) for (i in 0 until gaps.length()) {
            val g = gaps.optJSONArray(i) ?: continue
            if (g.length() >= 2 && g.optLong(1) - g.optLong(0) >= 10_000L) extra.add(JSONObject().put("tMs", g.optLong(0)).put("kind", "gap").put("text", "Sem dados da central por ${dur(g.optLong(1) - g.optLong(0))} (queda do cabo, motor desligado ou energia).").put("important", true))
        }
        val merged = (items + extra.take(6)).sortedBy { it.optLong("tMs") }.toMutableList()
        if (summary.optBoolean("final") && summary.opt("closedCleanly") == false) merged.add(JSONObject().put("tMs", id.optLong("elapsedMs")).put("kind", "no_stop").put("text", "A sessão terminou sem o 'parar' (energia cortada ou app fechado); o fim vem do último dado.").put("important", true))
        var shown = merged
        if (shown.size > 30) {
            val keep = shown.filter { it.optBoolean("important") }.toMutableList()
            val low = shown.filter { !it.optBoolean("important") }
            val room = (30 - keep.size).coerceAtLeast(0)
            keep.addAll(low.take(room))
            shown = keep.sortedBy { it.optLong("tMs") }.take(30).toMutableList()
        }
        if (shown.isEmpty()) sb.append("Nenhum marco registrado ainda (sessão curta ou sem eventos): isto não é erro.\n")
        shown.forEach { sb.append("- ${if (it.optLong("tMs") < 0) "antes" else dur(it.optLong("tMs"))} — ${it.optString("text")}\n") }
        if (merged.size > shown.size) sb.append("\n(+ ${merged.size - shown.size} marcos menores no resumo/session_summary.json → timeline)\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- PERGUNTAS E RESPOSTAS

    fun qaMd(summary: JSONObject, comparison: JSONObject, carMap: JSONObject): String {
        val sb = StringBuilder("# Perguntas e respostas (com os números desta sessão)\n\n")
        val v = comparison.optString("verdict")
        sb.append("## Qual tanque rendeu mais?\n")
        val best = comparison.optJSONObject("best"); val cur = comparison.optJSONObject("current")
        sb.append(when {
            cur == null -> "Não sei: esta sessão não tem nível do cilindro lido (— = desconhecido).\n"
            best == null -> "Ainda não há outro tanque fechado e comparável. Deste tanque (ciclo ${cur.optInt("cycleIndex")}): ${cur.optJSONObject("kmPerLevel")?.dbl("value")?.let { "${pt(it, 3)} km por unidade de nível" } ?: cur.dbl("gnvMinutesPer100Level")?.let { "${pt(it, 1)} min em GNV por 100 de nível" } ?: "rendimento ainda inconclusivo"}.\n"
            comparison.opt("currentIsBest") == true -> "Este (ciclo ${cur.optInt("cycleIndex")}) é o melhor tanque comparável conhecido.\n"
            else -> "O melhor tanque comparável conhecido é o ciclo ${best.optInt("cycleIndex")}.\n"
        })
        sb.append("Como conferir: $CMP_JSON → best, current; todos os ciclos em tank_ledger.json → cycles (kmPerRawUnit, gnvMinutesPer100Level, coverage). Mais é melhor; PROXY.\n\n")
        sb.append("## O mapa mudou?\n")
        val net = summary.optJSONObject("netChange")
        val log = summary.optJSONArray("changeLog") ?: JSONArray()
        val map = carMap.getJSONObject("active").optJSONObject("map")
        sb.append(when {
            log.length() > 0 -> "Sim: ${log.length()} lote(s) de mudança registrados nesta sessão; do início ao fim ficaram diferentes ${net?.optJSONArray("map")?.length() ?: 0} célula(s) do Mapa K e ${net?.optJSONArray("curve")?.length() ?: 0} ponto(s) da Curva K.\n"
            map?.optBoolean("known") == true -> "Não: o mapa foi lido e nenhuma mudança foi registrada.\n"
            else -> "Não sei: o mapa e a curva não foram lidos nesta sessão (— = desconhecido, não é 'igual').\n"
        })
        sb.append("Como conferir: $MAP_JSON → history.changes (antes/depois, causa, tanque) e history.net.\n\n")
        sb.append("## Melhorou ou piorou?\n")
        sb.append(when (v) {
            "MELHOROU" -> "Melhorou (rendimento PROXY acima do tanque comparado em mais de 10%).\n"
            "PIOROU" -> "Piorou (rendimento PROXY abaixo do tanque comparado em mais de 10%).\n"
            "IGUAL" -> "Igual: a diferença está dentro do ruído do sensor (menos de 10%).\n"
            else -> "Inconclusivo: a cobertura não é comparável (veja os motivos no $CMP_MD). Não inventei número.\n"
        })
        val pc = comparison.optJSONArray("comparisons")?.optJSONObject(0)?.optJSONObject("probableCause")
        if (pc != null) sb.append("Causa provável (hipótese): ${pc.optString("text")}\n")
        sb.append("\n## Quando abasteci e quanto andei?\n")
        val refuels = (summary.optJSONArray("timeline") ?: JSONArray()).let { t -> (0 until t.length()).map { t.getJSONObject(it) }.filter { it.optString("kind") == "refuel" } }
        sb.append(if (refuels.isEmpty()) "Nenhum abastecimento detectado nesta sessão (pode ter ocorrido entre sessões: veja tank_ledger.json).\n" else refuels.joinToString("") { "- ${dur(it.optLong("tMs"))}: ${it.optString("text")}\n" })
        val prim = summary.optJSONObject("movement")?.optJSONObject("primary")
        sb.append(if (prim?.dbl("km") != null) "Andou ${pt(prim.dbl("km")!!, 1)} km (fonte ${prim.optString("source")}).\n" else "Distância: — (sem velocidade da multimídia nem do GPS; o rpm só mede tempo andando, não km).\n")
        sb.append("\n## Como responder outras perguntas\n- Números da sessão: resumo/session_summary.json.\n- Segundo a segundo: resumo/series_1hz.csv (junte pelo t_s).\n- O que aconteceu e quando: $TIMELINE.\n- Mapa/curva: $MAP_JSON.\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- LEIA-ME

    fun readmeMd(summary: JSONObject, generatedAtMs: Long?): String {
        val b = summary.optJSONObject("build") ?: JSONObject()
        val id = summary.optJSONObject("identity") ?: JSONObject()
        val sb = StringBuilder("# LEIA-ME — pacote de sessão do OMEGAS\n\n")
        sb.append("Gerado pelo app (versão ${str(b.opt("appVersion"))}, commit ${str(b.opt("commit"))}) para a sessão ${str(id.opt("sessionId"))}. Serve para um humano e para um agente que nunca viu o app. Tudo aqui vale para esta sessão sozinha: ela é um intervalo de conexão (cabo ligado → desligado) e não precisa de abastecimento, parada ou ignição para ser válida.\n\n")
        sb.append("## 1. Comece por aqui\n\nFormato: `PERGUNTA | ARQUIVO | ONDE`\n\n")
        listOf(
            "Qual é o mapa K e a curva K em uso? | $MAP_JSON | active.map.rows, active.curve.points",
            "O que mudou no mapa/curva, quando e por quê? | $MAP_JSON | history.changes, history.net",
            "Melhorou ou piorou? Qual tanque rendeu mais? | $CMP_JSON | verdict, best, comparisons",
            "O que aconteceu e em que ordem? | $TIMELINE | (arquivo inteiro)",
            "Respostas prontas às perguntas de sempre | $QA | (arquivo inteiro)",
            "Números da sessão (tempos, km, nível, GNV) | resumo/session_summary.json | durations, movement, gnvLevel, tankSegments",
            "Um valor por segundo | resumo/series_1hz.csv | colunas t_s, rpm, map_bar, speed_kmh...",
            "Dados brutos capturados | raw/frames_48_01.NNNN.tsv, raw/tx.NNNN.tsv, raw/mm.NNNN.tsv, raw/heartbeat.NNNN.tsv, events_NNNN.jsonl | descartes indicados em heartbeat/dropped_lines",
            "Integridade (SHA-256) | resumo/manifest.json | files, streams, packageSha256",
        ).forEach { sb.append("- $it\n") }
        sb.append("\n## 2. Cada arquivo\n\nNem todo pacote traz todos: partes trazem parte.json, o 'Exportar tudo' traz export_summary.json, o resumo traz tank_ledger.json. No pacote leve (`_resumo.zip`) os três arquivos de `resumo/` ficam direto na pasta da sessão (sem a subpasta `resumo/`).\n\n")
        FILES.forEach { sb.append("- `${it.name}` — ${it.what}\n") }
        sb.append("""

## 3. Escalas e unidades
- **Nível do cilindro (`level_raw`, byte 13 do quadro 48 01)**: 255 = VAZIO; recém abastecido ≈ 31–78. É uma ESCADA de degraus ≈ 75 / 125 / 177 / 253 (a central filtra: o degrau chega com atraso de ~7 min). 19–22 = sensor sem leitura (central sem motor) e ≤ 25 é inválido: nunca é nível. Zonas = entre as 4 referências lidas da central (evento `level_calibration`; ex.: 39/93/143/219).
- **rpm**: rotações por minuto. **MAP/`map_bar`**: pressão no coletor em bar. **`petrol_ms` / `gas_ms`**: milissegundos de abertura do injetor (gasolina / GNV). Temperaturas em °C (a da água vem CRUA em `waterRaw`; a conversão é offline).
- **Minutos de injetor GNV**: soma de `gas_ms × rpm/120 × 4 cilindros × dt` ÷ 60000, só em intervalos de até 2 s.
- **Mapa K**: 12 linhas (ms de gasolina) × 12 colunas (rpm); valor CRU 0–255 gravado na central; piso de segurança do app = 100. **Curva K**: 30 pontos, fator Q14 (16384 = 1,0); teto de escrita do app = 1,8; eixo em ms de gasolina (`axisRaw`/512).
- **Rendimento do tanque** (PROXY): minutos de injetor ou de GNV por 100 de nível e km por unidade de nível. Só com ≥ 20 unidades de nível consumidas. Mais é melhor. Diferença < 10% = igual.
- **Desconhecido**: `null`, `—` ou célula vazia = desconhecido. 0 é zero de verdade. Nada foi preenchido com 0.

## 4. Como ler os relógios
- `t_s` (série e linha do tempo `tMs`): tempo MONOTÔNICO desde o início da sessão (não sofre com relógio errado). Use este para juntar fluxos.
- `e_ms` (arquivos raw): `elapsedRealtime` do aparelho relativo ao início do fluxo (`base_e` no cabeçalho). `wall_ms`/`recordedAtMs`: relógio de PAREDE (pode estar errado: `clock.clockSuspect`, `driftMs`; a hora do GPS corrige por `gpsReferenceOffsetMs`).
- Ordem entre sessões: pelo início (`startMs`); se `clockSuspect`, a ordem é incerta e o ciclo fica marcado `orderUncertain`.
- Fontes de velocidade (`speed_src`): `MM` (multimídia) > `GPS` > `RPM_PROXY` (rpm > 1100 = só 'andando', sem km). Nunca somadas nem misturadas; cada km diz de qual fonte veio.

## 5. Como juntar fluxos
1. Pela série: `resumo/series_1hz.csv` já traz tudo por segundo (`t_s`).
2. Pelo bruto: `raw/frames_48_01` (e_ms) + `raw/tx` (e_ms) + `raw/mm` (e_ms/wall_ms) + `events_NNNN.jsonl` (`elapsedMs` e `capturedAtMs`). Some o `base_e` do cabeçalho ao `e_ms` para alinhar.
3. Em partes: concatene os `events_NNNN.from_<byte>.jsonl` em ordem de byte; `parte.json` diz como. Duplicatas e ordem trocada não importam: a ingestão offline (`tools/sessoes/export_v2.py ingerir`) é idempotente por (sessão, arquivo, byte inicial, SHA-256).
4. Entre sessões: pelo `sessionUuid`/`sessionKey` e o livro-caixa `tank_ledger.json`. Tanque/ciclo/abastecimento é camada DERIVADA costurada entre sessões; sem evento de abastecimento o ciclo continua aberto.

## 6. Glossário
- **Central**: a caixa do GNV (MP48) ligada por cabo USB. **Mapa K**: tabela 12×12 que corrige o gás por ms de gasolina e rpm. **Curva K**: 30 fatores que corrigem o gás só pelo ms de gasolina.
- **AutoCal**: a central aprende sozinha (18 pontos por combustível). **AutoMatch**: a central aplica o aprendido na curva; o app só manda aplicar à mão. **Anti-apagão**: ajuste que segura o motor para não apagar ao pisar na embreagem. **Refino**: o app sugere correções no Mapa K a partir dos dados; só grava com o seu toque.
- **Tanque / ciclo**: o gasto entre dois abastecimentos (derivado, entre sessões). **PROXY**: estimativa indireta, não medida direta. **Hipótese**: causa provável, não certeza.
- O app NUNCA escreve na central sozinho: toda gravação é de um toque seu, com leitura de volta.

## 7. Conhecido × desconhecido
""")
        val ecu = summary.optJSONObject("ecu") ?: JSONObject()
        val car = summary.optJSONObject("carMap") ?: JSONObject()
        sb.append("- Nesta sessão: Mapa K ${if (car.optJSONObject("map")?.optBoolean("known") == true) "lido" else "NÃO lido (—)"}; Curva K ${if (car.optJSONObject("curve")?.optBoolean("known") == true) "lida" else "NÃO lida (—)"}; nível do cilindro ${if (summary.optJSONObject("gnvLevel")?.isNull("startRaw") == false) "lido" else "sem leitura (—)"}; versão do módulo da central ${str(ecu.opt("moduleVersion"))}.\n")
        sb.append("- Sempre desconhecidos hoje: serial e firmware da central; significado do tipo de sensor 0x81; escala do byte 19 (tensão ÷16 V, provisória); unidade de velocidade/odômetro da multimídia (assumida km/h e km); peso real de cada degrau do nível.\n")
        sb.append("- Um valor ausente significa que o app não pôde ler, não que seja zero.\n\n")
        sb.append("## 8. Campos dos manifestos e documentos\n")
        FIELDS.forEach { (file, fields) ->
            sb.append("\n### `$file`\n")
            fields.forEach { (k, d) -> sb.append("- `$k` — $d\n") }
        }
        sb.append("\nResumo (`resumo/session_summary.json`), campos de topo: `schema`, `final`, `closedCleanly`, `identity`, `clock`, `build`, `ecu`, `context`, `durations`, `movement`, `engine`, `gnvLevel`, `tankSegments`, `mapCurve`, `carMap`, `changeLog`, `changeLogMeta`, `netChange`, `timeline`, `multimedia`, `gaps`, `quality`, `series`, `safety`.\n")
        if (generatedAtMs != null) sb.append("\nGerado em ms de parede: $generatedAtMs (o relógio pode estar errado).\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- montagem

    /** Todos os documentos (nome → bytes). [lookup] devolve o resumo de OUTRA sessão pelo id (null = indisponível). */
    fun build(summary: JSONObject, ledger: JSONObject?, lookup: (String) -> JSONObject?, generatedAtMs: Long? = null): Map<String, ByteArray> {
        val carMap = carMapJson(summary, ledger)
        val cmp = SessionComparison.build(summary, ledger, lookup)
        val out = LinkedHashMap<String, ByteArray>()
        fun put(n: String, t: String) { out[n] = t.toByteArray(Charsets.UTF_8) }
        put(README, readmeMd(summary, generatedAtMs))
        put(QA, qaMd(summary, cmp, carMap))
        put(TIMELINE, timelineMd(summary))
        put(MAP_JSON, carMap.toString(2))
        put(MAP_MD, carMapMd(carMap))
        put(CMP_JSON, cmp.toString(2))
        put(CMP_MD, comparisonMd(cmp))
        return out
    }
}
