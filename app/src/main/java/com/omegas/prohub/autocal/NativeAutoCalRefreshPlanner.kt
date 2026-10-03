package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import java.util.ArrayDeque

/**
 * Cadência pura do espelho operacional AutoCal.
 *
 * Não possui thread, timer, I/O ou autoridade serial. O serviço decide quando
 * chamar o monitor; este planner apenas informa quais famílias estão vencidas
 * e em que ordem os GRUPOS de leitura (<= 3 leituras cada) de um round são lidos.
 * Um snapshot completo ancora ambas as famílias. Leitura de grupo que falha recua
 * de forma exponencial (com teto): uma ECU/cabo ruim não é reperguntada a cada tick.
 *
 * Lote D (D2): rodada operacional ~2 s; referência (RV/MUL_ACT) a cada 2ª rodada (~4 s) ou JÁ quando
 * o dono/ECU muda algo ([requestReferenceNow]). Um grupo que falha é descartado e só volta na
 * próxima rodada, depois do recuo; grupos vencidos nunca se acumulam.
 */
class NativeAutoCalRefreshPlanner {
    data class Due(
        val acquisition: Boolean,
        val reference: Boolean,
    )

    enum class Family { ACQUISITION, REFERENCE }

    /**
     * Mesmos campos da antiga unidade única (aquisição: 10 leituras, mesma ordem; referência: 5 leituras),
     * agora em unidades de no máximo 3 leituras e ~160 ms. Nenhum comando novo: só agrupamento e ordem.
     * G6 mantém as DUAS leituras de zonas (gasolina, GNV) para os bytes por rodada ficarem idênticos.
     */
    enum class Group(
        val family: Family,
        val fields: List<AutoCalProtocol.Field>,
    ) {
        G2_PETROL_BUFFERS(
            Family.ACQUISITION,
            listOf(AutoCalProtocol.PETR_INJ_TBUF, AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalProtocol.NUM_BUF_UPD_PETR),
        ),
        G3_GAS_PREV(
            Family.ACQUISITION,
            listOf(AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV, AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV),
        ),
        G4_GAS(
            Family.ACQUISITION,
            listOf(AutoCalProtocol.PETR_INJ_TBUF_GAS, AutoCalProtocol.MNFLD_PRESS_BUF_GAS, AutoCalProtocol.NUM_BUF_UPD_GAS),
        ),
        G6_ZONES(
            Family.ACQUISITION,
            listOf(AutoCalProtocol.ACQUIRED_ZONES_PETROL, AutoCalProtocol.ACQUIRED_ZONES_GAS),
        ),
        // Referência: o tempo de cada grupo cabe em ~160 ms (MUL_ACT/TBP/RV30 têm resposta de 30 elementos).
        G5_MUL_ACT(Family.REFERENCE, listOf(AutoCalProtocol.MUL_ACT, AutoCalProtocol.MNFLD_PRESS_THD)),
        G7_PETROL_RV(Family.REFERENCE, listOf(AutoCalProtocol.PETR_INJ_TBP, AutoCalProtocol.PETR_MNFLD_PRESS_RV)),
        G8_GAS_RV(Family.REFERENCE, listOf(AutoCalProtocol.GAS_MNFLD_PRESS_RV)),
        ;

        init {
            require(fields.size in 1..NativeAutoCalRefreshPlanner.MAX_READS_PER_GROUP)
        }

        /** Rótulo curto estável (G2…G8) para JSON/telemetria interna. */
        val label: String get() = name.substringBefore('_')
    }

    private var lastAcquisitionAtElapsedMs = 0L
    private var lastReferenceAtElapsedMs = 0L
    private var acquisitionFailures = 0
    private var referenceFailures = 0
    private var referenceNow = false
    private val roundQueue = ArrayDeque<Group>()

    @Synchronized
    fun reset() {
        lastAcquisitionAtElapsedMs = 0L
        lastReferenceAtElapsedMs = 0L
        acquisitionFailures = 0
        referenceFailures = 0
        referenceNow = false
        roundQueue.clear()
    }

    @Synchronized
    fun markFullSnapshot(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
        lastReferenceAtElapsedMs = observedAtElapsedMs
        acquisitionFailures = 0
        referenceFailures = 0
        referenceNow = false
        roundQueue.clear() // o snapshot completo (também fatiado) já cobre tudo
    }

    /** Referência JÁ (AutoMatch/Finish/reset/ação do dono); ignorada até existir uma âncora de snapshot. */
    @Synchronized
    fun requestReferenceNow() {
        referenceNow = true
    }

    /**
     * Próximo grupo a ler (sem consumir): enquanto o round corrente não acabou devolve sempre o mesmo
     * até [groupDone]/[groupFailed]/[abandonRound]. Com a fila vazia, abre um round se algo venceu:
     * grupos de aquisição (se habilitada) seguidos dos de referência. O início do round âncora o
     * relógio (rodada ≈ 2 s de início a início), sem apagar o recuo de falhas.
     */
    @Synchronized
    fun nextGroup(nowElapsedMs: Long, acquisitionEnabled: Boolean): Group? {
        if (roundQueue.isEmpty()) {
            val due = due(nowElapsedMs)
            val acquisition = acquisitionEnabled && due.acquisition
            val reference = due.reference
            if (!acquisition && !reference) return null
            if (acquisition) {
                lastAcquisitionAtElapsedMs = nowElapsedMs
                Group.values().filter { it.family == Family.ACQUISITION }.forEach(roundQueue::addLast)
            }
            if (reference) {
                lastReferenceAtElapsedMs = nowElapsedMs
                referenceNow = false
                Group.values().filter { it.family == Family.REFERENCE }.forEach(roundQueue::addLast)
            }
        }
        return roundQueue.peekFirst()
    }

    /** Grupo lido E confirmado. Ao fechar a última do tipo, zera o recuo dele. */
    @Synchronized
    fun groupDone(group: Group) {
        if (roundQueue.peekFirst() == group) roundQueue.pollFirst()
        if (roundQueue.none { it.family == group.family }) {
            if (group.family == Family.ACQUISITION) acquisitionFailures = 0 else referenceFailures = 0
        }
    }

    /**
     * Grupo falhou (transporte/época/validação): recuo exponencial do tipo e descarte do resto do
     * tipo neste round. Nada fica "atrasado": a próxima tentativa é a próxima rodada vencida.
     */
    @Synchronized
    fun groupFailed(group: Group, attemptedAtElapsedMs: Long) {
        roundQueue.removeAll { it.family == group.family }
        if (group.family == Family.ACQUISITION) markAcquisitionFailure(attemptedAtElapsedMs)
        else markReferenceFailure(attemptedAtElapsedMs)
    }

    /** Larga o round sem contar falha (maturidade/snapshot pedido): o dado em voo não vale mais. */
    @Synchronized
    fun abandonRound() {
        roundQueue.clear()
    }

    @Synchronized
    fun roundInProgress(): Boolean = roundQueue.isNotEmpty()

    @Synchronized
    fun roundRemaining(): List<Group> = roundQueue.toList()

    @Synchronized
    fun markAcquisition(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = observedAtElapsedMs
        acquisitionFailures = 0
    }

    @Synchronized
    fun markReference(observedAtElapsedMs: Long) {
        require(observedAtElapsedMs > 0L)
        lastReferenceAtElapsedMs = observedAtElapsedMs
        referenceFailures = 0
    }

    /** A leitura agrupada de aquisição falhou: próxima tentativa só depois do recuo (2 s, 4 s, 8 s, … até o teto de 30 s). */
    @Synchronized
    fun markAcquisitionFailure(attemptedAtElapsedMs: Long) {
        require(attemptedAtElapsedMs > 0L)
        lastAcquisitionAtElapsedMs = attemptedAtElapsedMs
        acquisitionFailures = (acquisitionFailures + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
    }

    /** A leitura agrupada de referência falhou: próxima tentativa só depois do recuo (8 s, 16 s, … até o teto de 30 s). */
    @Synchronized
    fun markReferenceFailure(attemptedAtElapsedMs: Long) {
        require(attemptedAtElapsedMs > 0L)
        lastReferenceAtElapsedMs = attemptedAtElapsedMs
        referenceFailures = (referenceFailures + 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
    }

    @Synchronized
    fun due(nowElapsedMs: Long): Due {
        if (nowElapsedMs <= 0L || lastAcquisitionAtElapsedMs <= 0L || lastReferenceAtElapsedMs <= 0L) {
            return Due(acquisition = false, reference = false)
        }
        return Due(
            acquisition = nowElapsedMs - lastAcquisitionAtElapsedMs >= interval(ACQUISITION_INTERVAL_MS, acquisitionFailures),
            reference = (referenceNow && referenceFailures == 0) ||
                nowElapsedMs - lastReferenceAtElapsedMs >= interval(REFERENCE_INTERVAL_MS, referenceFailures),
        )
    }

    private fun interval(base: Long, failures: Int): Long =
        if (failures <= 0) base else (base shl failures).coerceAtMost(BACKOFF_CAP_MS)

    companion object {
        /** Rodada operacional ≈ 2 s (D2: só o vivo é tempo real; tabelas só quando mudam). */
        const val ACQUISITION_INTERVAL_MS = 2_000L
        /** Referência = a cada 2ª rodada (~4 s). */
        const val REFERENCE_INTERVAL_MS = 4_000L
        const val BACKOFF_CAP_MS = 30_000L
        const val MAX_READS_PER_GROUP = 3
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
