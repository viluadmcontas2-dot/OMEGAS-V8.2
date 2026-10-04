package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RealSessionReplaySupport
import org.json.JSONObject
import java.io.File

/** Ajudantes de teste do cérebro sobre as sessões reais (fixtures/autocal/real). */
object EquivalenceReplaySupport {
    fun frames(name: String): List<RealSessionReplaySupport.Telemetry> =
        RealSessionReplaySupport.telemetry(RealSessionReplaySupport.fixture(name))

    /** Livro alimentado com os quadros da sessão que passam em [keep], na ordem gravada. */
    fun ledger(
        name: String, keep: (RealSessionReplaySupport.Telemetry) -> Boolean = { true }, curveSeq: Int? = null,
    ): EquivalenceLedger {
        val ledger = EquivalenceLedger(null)
        // Com [curveSeq] o livro já conhece a impressão digital da Curva K desse snapshot (como no app em uso): o GNV
        // guardado vale para ela. Sem ela, o primeiro alinhamento descarta o GNV (curva desconhecida = falha fechada).
        curveSeq?.let { ledger.alignCurve(EquivalenceLedger.fingerprint(curve(name, it).second)) }
        frames(name).filter(keep).forEach { ledger.accept(RealSessionReplaySupport.ledgerFrame(it)) }
        return ledger
    }

    fun snapshot(name: String, seq: Int): JSONObject =
        RealSessionReplaySupport.snapshot(RealSessionReplaySupport.fixture(name), seq)

    fun acquisition(name: String, seq: Int): JSONObject =
        RealSessionReplaySupport.acquisition(snapshot(name, seq))

    /** Referência congelada da aquisição da ECU naquele instante da sessão. */
    fun reference(name: String, seq: Int): Reference = ReferenceStore(null).freeze(acquisition(name, seq), 0L)

    /** (PETR_INJ_TBP, MUL_ACT) brutos do snapshot. */
    fun curve(name: String, seq: Int): Pair<IntArray, IntArray> {
        val snap = snapshot(name, seq)
        val axis = RealSessionReplaySupport.rawValues(snap, "PETR_INJ_TBP") ?: error("eixo ausente no snapshot $seq")
        val k = RealSessionReplaySupport.rawValues(snap, "MUL_ACT") ?: error("MUL_ACT ausente no snapshot $seq")
        return axis to k
    }

    /** Instante (ms UTC) da primeira escrita de Curva K da sessão. */
    fun writeAtMs(name: String): Long {
        val writes = RealSessionReplaySupport.fixture(name).getJSONArray("kFactorWrites")
        return java.time.Instant.parse(writes.getJSONObject(0).getString("recordedAtUtc")).toEpochMilli()
    }

    fun snapshotAtMs(name: String, seq: Int): Long = RealSessionReplaySupport.recordedAtMs(snapshot(name, seq))

    /** Quem é a pasta do repositório (para chamar o oráculo Python). */
    fun repoRoot(): File = listOf("..", ".").map(::File).first { File(it, "tools/equivalence_oracle").isDirectory }
}
