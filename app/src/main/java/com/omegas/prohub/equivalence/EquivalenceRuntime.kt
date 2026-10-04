package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EquivalencePhases
import org.json.JSONObject
import java.io.File

/**
 * Junta Referência, medidores, livro e fases: é a única coisa que o serviço chama. Observa; não escreve na
 * ECU. `evaluate` roda no executor do serviço (tique de 3 s), nunca na thread da interface; a ponte só lê o
 * último resultado pronto.
 */
class EquivalenceRuntime(root: File?, private val clock: () -> Long = System::currentTimeMillis) {
    val references = ReferenceStore(root?.let { File(it, "equivalence_reference.json") })
    val experience = ExperienceMeter(root?.let { File(it, "experience_meter.json") }, clock)
    val usage = UsageMeter(root?.let { File(it, "usage_meter.json") }, clock)

    private val lock = Any()
    @Volatile private var last: EquivalenceResult? = null
    private var lastKey: String? = null

    fun onFrame(t: Long, fuel: String, rpm: Double, map: Double, petrolMs: Double, sessionId: Long) {
        experience.accept(t, fuel, rpm, map, petrolMs)
        usage.accept(t, fuel, rpm, map, sessionId)
    }

    fun onStall(event: JSONObject) = experience.onStall(event)

    /**
     * A curva ou o mapa mudou e o GNV medido antes saiu do livro: a experiência do GNV sai também. As provas
     * abertas só sobrevivem quando a mudança é a gravação que elas estão provando (`CURVA_K_GRAVADA`).
     */
    fun onGasReset(reason: String, phases: EquivalencePhases) {
        experience.resetGas(reason)
        if (reason != "CURVA_K_GRAVADA") phases.interruptProofs(reason)
        synchronized(lock) { lastKey = null }
    }

    /**
     * Alinha o livro à Curva K lida da ECU (impressão digital) e, se ela mudou por fora do app ou era desconhecida
     * com GNV guardado, o GNV sai do livro E a experiência/provas do cérebro saem junto. Idempotente: a ponte (tela)
     * e o tique do serviço chamam a mesma coisa; o alinhamento não depende de a tela estar aberta.
     */
    fun alignCurve(ledger: EquivalenceLedger, phases: EquivalencePhases, mulActRaw: IntArray): Boolean {
        val reset = ledger.alignCurve(EquivalenceLedger.fingerprint(mulActRaw))
        if (reset) onGasReset("CURVA_K_MUDOU_FORA_DO_APP", phases)
        return reset
    }

    /** O dono gravou a Curva K: cada ponto que mudou entra em prova, com o estado de antes como base. */
    fun onCurveWritten(beforeRaw: IntArray, afterRaw: IntArray, phases: EquivalencePhases) {
        val changed = (0 until minOf(beforeRaw.size, afterRaw.size)).filter { beforeRaw[it] != afterRaw[it] }
        if (changed.isNotEmpty()) phases.beginProof(changed, last?.points ?: emptyList())
        synchronized(lock) { lastKey = null }
    }

    /** Congela a gasolina madura da ECU como Referência (toque do dono). Imatura: lança `AQUISICAO_IMATURA`. */
    fun freeze(acquisition: JSONObject?, phases: EquivalencePhases): Reference {
        val fresh = references.freeze(acquisition ?: throw IllegalStateException("AQUISICAO_IMATURA"), clock())
        phases.restartProofs("REFERENCIA_CONGELADA")
        synchronized(lock) { lastKey = null }
        return fresh
    }

    /** Desfazer do congelamento: volta à Referência anterior da sessão. */
    fun restorePreviousReference(phases: EquivalencePhases): Reference? {
        val back = references.restorePrevious() ?: return null
        phases.restartProofs("REFERENCIA_RESTAURADA")
        synchronized(lock) { lastKey = null }
        return back
    }

    /**
     * Reavalia. Nulo sem Curva K lida. Com prova aberta sempre reavalia (o relógio dela anda); senão devolve o
     * último resultado enquanto livro, Referência, curva, uso e experiência não mudarem.
     */
    fun evaluate(
        ledger: EquivalenceLedger,
        phases: EquivalencePhases,
        snapshot: JSONObject?,
        acquisition: JSONObject?,
        ecuOnline: Boolean,
        gainScale: (List<Double>) -> DoubleArray?,
    ): EquivalenceResult? {
        val curve = EquivalenceEngine.curveFromSnapshot(snapshot) ?: return null
        val (axisRaw, mulActRaw) = curve
        // O alinhamento do livro à curva da ECU roda aqui, no tique do serviço, sem depender de a tela estar aberta
        // (antes só a ponte alinhava: com a tela fechada o GNV de uma curva antiga seguia valendo). Sem cabo não há curva viva.
        if (ecuOnline) alignCurve(ledger, phases, mulActRaw)
        val reference = references.current()
        val provisional = if (reference == null) references.provisional(acquisition) else null
        val scale = gainScale(axisRaw.map { it / com.omegas.prohub.autocal.AutoMatchRefinedEngine.AXIS_COUNTS_PER_MS })
        val key = listOf(
            ledger.revision(), reference?.id, provisional?.ecuAcquisitionFingerprint, EquivalenceLedger.fingerprint(mulActRaw),
            EquivalenceLedger.fingerprint(axisRaw), usage.revision(), experience.revision(),
            scale?.joinToString(",") { "%.3f".format(it) },
        ).joinToString("|")
        synchronized(lock) {
            val cached = last
            if (cached != null && key == lastKey && phases.openProofCount() == 0) return cached
        }
        val result = EquivalenceEngine.evaluate(
            EquivalenceInput(
                axisRaw, mulActRaw, reference, provisional, ledger.petrolObservations(), ledger.gasObservations(),
                experience.reading(), usage.reading(), null, scale,
                com.omegas.prohub.autocal.AutoMatchRefinedEngine.HOLD_MIN_STEP_LOG,
            ),
        ) { points -> phases.judgePoints(points, ecuOnline) }
        synchronized(lock) {
            last = result
            lastKey = key
        }
        return result
    }

    fun last(): EquivalenceResult? = last

    /** JSON do último resultado (a ponte só lê; o cálculo já aconteceu no tique do serviço). */
    fun json(acquisition: JSONObject?): JSONObject = EquivalenceJson.result(
        last, references.current(), references.ecuDrift(acquisition), references.previous(),
        ReferenceStore.pointsFrom(acquisition).isNotEmpty(), clock(),
    )

    fun flush() {
        experience.flush()
        usage.flush()
    }
}
