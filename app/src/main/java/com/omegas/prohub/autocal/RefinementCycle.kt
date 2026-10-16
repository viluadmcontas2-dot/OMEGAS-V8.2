package com.omegas.prohub.autocal

import org.json.JSONObject

/**
 * Cola do refino, sem Android (o TelemetryForegroundService só a chama): gravação confirmada ou parcial →
 * experimento, referência de gasolina da ECU, proposta do motor sobre a leitura da ECU, avaliação e piloto.
 * Tudo sob um lock só: a gravação nunca se intercala com o cálculo da proposta. Nunca grava na ECU.
 */
class RefinementCycle(
    val equivalence: EquivalenceLedger,
    val journal: RefinementJournal,
    val autopilot: RefinementAutopilot,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
    /** Motor de correção (inalterado: AutoMatchSnapshotAnalysis.analyzeRefined); os testes do ciclo trocam por um sintético. */
    private val analyze: (JSONObject, List<Pair<Double, Double>>, DoubleArray?) -> JSONObject =
        { snapshot, pairs, scale -> AutoMatchSnapshotAnalysis.analyzeRefined(snapshot, pairs, scale, equivalence.pairs().map { it.weight }) },
) {
    companion object {
        /** O motor refinado roda de novo por leituras novas no máximo a cada isso (o resto do memo é por conteúdo). */
        const val ANALYSIS_MIN_INTERVAL_MS = 15_000L

        /**
         * O que conta para o prazo da verificação: motor funcionando no GNV em qualquer rotação, lenta incluída
         * (dono, 2026-10-11: a curva aprende tudo; rodar na gasolina não mede o GNV) e injeção acima de 1 ms.
         */
        fun driving(rpm: Double, petrolMs: Double, fuel: String?): Boolean =
            fuel?.trim()?.uppercase() == "GNV" && rpm > 0.0 && petrolMs >= EquivalenceLedger.MIN_PETROL_MS

        private fun field(snapshot: JSONObject?, key: String): JSONObject? {
            val fields = snapshot?.optJSONArray("fields") ?: return null
            for (i in 0 until fields.length()) {
                val f = fields.optJSONObject(i) ?: continue
                if (f.optString("key") != key) continue
                val raw = f.optJSONArray("rawValues") ?: return null
                return f.takeIf { it.optString("status") == "VALID" && raw.length() == 30 }
            }
            return null
        }

        private fun raw30(field: JSONObject?): IntArray? =
            field?.optJSONArray("rawValues")?.takeIf { it.length() == 30 }?.let { a -> IntArray(30) { a.optInt(it) } }
    }

    private val lock = Any()
    private var memo: Pair<String, JSONObject>? = null
    private var memoRevision = -1L
    private var memoAt = Long.MIN_VALUE / 2
    /** Lotes da Curva K já registrados (o status do KFactorManager fica parado até a próxima operação). */
    private val recorded = LinkedHashSet<String>()

    // ------------------------------------------------------------ gravações

    /** Gravação de Curva K confirmada (BATCH_CONFIRMED + readback) → experimento com o índice da curva antiga. */
    fun recordConfirmedCurve(payload: JSONObject) = synchronized(lock) {
        if (!payload.optBoolean("readbackValid", false)) return@synchronized
        var axisRaw: IntArray? = null
        var beforeRaw: IntArray? = null
        var afterRaw: IntArray? = null
        try {
            payload.optString("adjustmentId").takeIf { it.isNotBlank() }?.let(::remember)
            val curve = payload.optJSONObject("curve") ?: return@synchronized
            val axis = curve.optJSONArray("axisRaw") ?: return@synchronized
            val after = curve.optJSONArray("factorsRaw") ?: return@synchronized
            if (axis.length() != 30 || after.length() != 30) return@synchronized
            val afterValues = IntArray(30) { after.optInt(it) }
            val beforeValues = afterValues.copyOf()
            val points = payload.optJSONArray("points")
            if (points != null) for (i in 0 until points.length()) {
                val point = points.optJSONObject(i) ?: continue
                val index = point.optInt("index", -1)
                if (index in 0 until 30) beforeValues[index] = point.optInt("currentRaw", beforeValues[index])
            }
            val axisValues = IntArray(30) { axis.optInt(it) }
            axisRaw = axisValues; beforeRaw = beforeValues; afterRaw = afterValues
            journal.recordCurveWrite(beforeValues, afterValues, axisValues, equivalence.index(),
                payload.optString("adjustmentId", "K_FACTOR"),
                // Proposta, restaurar ou desfazer do Refino (pelo motivo que a tela passou); reset/backup; senão manual.
                RefinementJournal.kindFromReason(payload.optJSONArray("confirmedEvents")?.optJSONObject(0)?.optString("reason")))
        } finally {
            // Curva nova só nos pontos alterados: sai só o GNV medido onde eles agem; o resto e a gasolina ficam.
            val axis = axisRaw
            val before = beforeRaw
            val after = afterRaw
            if (axis != null && before != null && after != null) {
                equivalence.rescaleCurve(axis, before, after, "CURVA_K_GRAVADA")
            } else {
                equivalence.suspendGas("CURVA_K_SEM_BASE_PARA_REESCALA")
            }
            payload.optJSONObject("curve")?.optJSONArray("factorsRaw")?.takeIf { it.length() == 30 }?.let { raw ->
                equivalence.adoptCurve(EquivalenceLedger.fingerprint(IntArray(30) { raw.optInt(it) }))
            }
        }
    }

    /**
     * Status do KFactorManager: BATCH_FAILED parcial (pontos com ACK antes da falha, em confirmedEvents) também
     * mudou a ECU e é gravação do app, não "mudou por fora". Só lê o status; não mexe em bytes nem no escritor.
     * Retorna true quando registrou um experimento novo.
     */
    fun noteCurveOperation(status: JSONObject?, snapshot: JSONObject?): Boolean = synchronized(lock) {
        if (status?.optString("state") != "BATCH_FAILED") return@synchronized false
        val details = status.optJSONObject("details") ?: return@synchronized false
        val id = details.optString("adjustmentId").takeIf { it.isNotBlank() } ?: return@synchronized false
        val events = details.optJSONArray("confirmedEvents") ?: return@synchronized false
        if (id in recorded) return@synchronized false
        remember(id)
        if (events.length() == 0) return@synchronized false
        val base = journal.expectedCurve() ?: raw30(field(snapshot, "MUL_ACT")) ?: return@synchronized false
        val axis = raw30(field(snapshot, "PETR_INJ_TBP")) ?: journal.latestAxis() ?: return@synchronized false
        val before = base.copyOf()
        val after = base.copyOf()
        var reason: String? = null
        for (k in 0 until events.length()) {
            val e = events.optJSONObject(k) ?: continue
            val i = e.optInt("index", -1)
            if (i !in 0 until 30 || !e.has("afterRaw")) continue
            before[i] = e.optInt("beforeRaw", before[i])
            after[i] = e.optInt("afterRaw")
            if (reason == null) reason = e.optString("reason")
        }
        if (before.contentEquals(after)) return@synchronized false
        journal.recordCurveWrite(before, after, axis, equivalence.index(), id, RefinementJournal.kindFromReason(reason))
        equivalence.rescaleCurve(axis, before, after, "CURVA_K_GRAVADA")
        equivalence.adoptCurve(EquivalenceLedger.fingerprint(after))
        true
    }

    private fun remember(id: String) {
        recorded += id
        while (recorded.size > 32) recorded.remove(recorded.first())
    }

    /** Mapa K gravado: o gás mudou; GNV antigo sai, verificações param e a oferta de restaurar perde a base. */
    fun mapKWritten() = synchronized(lock) {
        equivalence.suspendGas(RefinementJournal.MAP_K_WRITTEN)
        journal.interrupt(RefinementJournal.MAP_K_WRITTEN)
    }

    /** A ECU trocou a Curva K sozinha (AutoMatch nativo). */
    fun nativeCurveChanged(payload: JSONObject = JSONObject(), snapshot: JSONObject? = null) = synchronized(lock) {
        if (!payload.optBoolean("readbackValid", false)) return@synchronized
        val axis = raw30(field(snapshot, "PETR_INJ_TBP")) ?: journal.latestAxis()
        val before = payload.optJSONArray("beforeRaw")?.let { a -> IntArray(a.length()) { a.optInt(it) } }
        val after = payload.optJSONArray("afterRaw")?.let { a -> IntArray(a.length()) { a.optInt(it) } }
        if (axis == null || before == null || after == null ||
            !equivalence.rescaleCurve(axis, before, after, RefinementJournal.OUTSIDE_AUTOMATCH))
            equivalence.suspendGas("AUTOMATCH_SEM_BASE_PARA_REESCALA")
        journal.interrupt(RefinementJournal.OUTSIDE_AUTOMATCH)
    }

    // ------------------------------------------------------------ leitura da ECU

    /**
     * Referência de gasolina da ECU e aquisição de gasolina. Leitura desconhecida (sem cabo, reconexão, campo
     * indisponível) mantém a última referência. Gasolina readquirida descongela a própria e anula verificações e
     * ofertas medidas com a referência antiga.
     */
    fun observeEcu(progress: JSONObject?, usbConnected: Boolean) = synchronized(lock) {
        if (!usbConnected) return@synchronized
        val acquisition = progress?.optJSONObject("acquisition")
        equivalence.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(acquisition))
        var restarted = EcuPetrolReference.petrolActivity(acquisition)?.let(equivalence::notePetrolAcquisition) ?: false
        if (progress != null && progress.has("petrolGeneration") && !progress.isNull("petrolGeneration")) {
            restarted = equivalence.notePetrolGeneration(progress.optLong("usbSessionId", 0L), progress.optInt("petrolGeneration", -1)) || restarted
        }
        if (restarted) journal.interrupt(RefinementJournal.PETROL_REACQUIRED)
    }

    /**
     * Proposta do refino, calculada aqui (a tela nunca calcula "pronto"): alinha a curva lida (MUL_ACT) antes de
     * usar o GNV, depois o motor de correção inalterado (AutoMatchSnapshotAnalysis.analyzeRefined) e o diário tira
     * os pontos em medição ou bloqueados. Memo por conteúdo (leitura, ganho, época do GNV, referência, gravação);
     * leituras novas recalculam no máximo a cada [ANALYSIS_MIN_INTERVAL_MS].
     */
    fun proposal(snapshot: JSONObject?, index: JSONObject): JSONObject = synchronized(lock) {
        val fields = snapshot?.optJSONArray("fields")
        if (snapshot == null || fields == null || fields.length() == 0) return@synchronized JSONObject().put("known", false).put("reason", "SEM_LEITURA_DA_ECU")
        val axisMs = raw30(field(snapshot, "PETR_INJ_TBP"))?.map { it / AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS }
        val mulActField = field(snapshot, "MUL_ACT")
        val mulAct = raw30(mulActField)
        val mulActAt = mulActField?.optLong("capturedAtMs", 0L)?.takeIf { it > 0L } ?: snapshot.optLong("capturedAtMs", 0L)
        if (mulAct != null) {
            // Curva mudou por fora (ProgBase, reset, gravação que falhou no meio): o GNV medido com a outra não vale.
            val fingerprint = EquivalenceLedger.fingerprint(mulAct)
            val axis = raw30(field(snapshot, "PETR_INJ_TBP"))
            journal.noteEcuCurve(mulAct, mulActAt)
            if (axis != null) equivalence.observeCurve(axis, mulAct)
        }
        val scale = axisMs?.let { journal.pointGainScale(it) }
        val key = listOf(
            snapshot.optString("snapshotHash"), mulActAt, scale?.joinToString(",") { "%.3f".format(it) },
            equivalence.gasEpochToken(), equivalence.referenceToken(), journal.latestId(),
        ).joinToString("|")
        val revision = equivalence.revision()
        val now = monotonicMs()
        val cached = memo?.takeIf { it.first == key && (revision == memoRevision || now - memoAt < ANALYSIS_MIN_INTERVAL_MS) }?.second
        val analysis = cached ?: analyze(snapshot, equivalence.pairs().map { it.petrolRefMs to it.gasPetrolMs }, scale)
            .also { memo = key to it; memoRevision = revision; memoAt = now }
        journal.proposalFrom(analysis, index) { since -> equivalence.index(since) }
    }

    /** Avalia as gravações abertas. [driving] = ECU online e motor funcionando no GNV (só então o prazo anda). */
    fun evaluate(driving: Boolean): Boolean = synchronized(lock) {
        journal.evaluate(equivalence.index(), driving) { since -> equivalence.index(since) }
    }

    /** Um tick do piloto: referência da ECU, proposta da curva, ajuste local candidato (Mapa K) e fase. */
    fun observe(ecuOnline: Boolean, usbConnected: Boolean, progress: JSONObject?, snapshot: JSONObject?): JSONObject = synchronized(lock) {
        observeEcu(progress, usbConnected)
        val proposal = try { proposal(snapshot, equivalence.index()) } catch (error: Exception) {
            JSONObject().put("known", false).put("reason", "ERRO_NO_MOTOR").put("error", error.message ?: "erro")
        }
        autopilot.observe(
            ecuOnline = ecuOnline,
            monitor = progress,
            acquisition = progress?.optJSONObject("acquisition"),
            index = equivalence.index(),
            journal = journal.json(),
            restoreCount = journal.restorePoints().length(),
            proposal = proposal,
            local = LocalMapPlanner.plan(equivalence.attribution()),
        )
    }

    /** Última análise do motor (a ponte devolve esta, sem recalcular). */
    fun lastAnalysis(): JSONObject = synchronized(lock) {
        memo?.second?.let { JSONObject(it.toString()) } ?: JSONObject().put("ok", false).put("available", false).put("reason", "MOTOR_AINDA_NAO_RODOU")
    }

}
