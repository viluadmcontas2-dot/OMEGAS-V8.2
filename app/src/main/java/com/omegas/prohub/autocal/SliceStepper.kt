package com.omegas.prohub.autocal

/**
 * Decisão pura de "o que fazer neste tick" da aquisição fatiada (Lote D).
 *
 * Sem I/O, sem thread, sem relógio próprio: junta o [NativeAutoCalRefreshPlanner] (QUAL grupo, em que
 * ordem) com o [SlotArbiter] (QUANDO pode ocupar a porta). O monitor executa a decisão; os testes JVM
 * usam o MESMO objeto sobre um barramento simulado, então o que é provado é o que roda em produção.
 *
 * Ordem de precedência por tick:
 * 1. grupo lido no slot anterior espera o probe de época ([Step.CONFIRM_PROBE]);
 * 2. se NÃO há snapshot completo devido e há grupo vencido: com probe fresco e slot livre,
 *    [Step.RUN_GROUP] (no máximo um por slot); slot ocupado → [Step.IDLE];
 *    probe velho → [Step.PROBE] (o grupo sai no tick seguinte);
 * 3. sem nada vencido, o status leve segue ~1x/s ([Step.PROBE]) e, entre um e outro, [Step.IDLE].
 */
class SliceStepper(
    private val planner: NativeAutoCalRefreshPlanner,
    private val arbiter: SlotArbiter,
    private val duty: AcquisitionDuty? = null,
) {
    enum class Step { CONFIRM_PROBE, RUN_GROUP, PROBE, IDLE }

    data class Decision(
        val step: Step,
        val group: NativeAutoCalRefreshPlanner.Group? = null,
    )

    private var roundOpenedAtMs = -1L

    /** @param probeAgeMs idade do último probe (status leve); negativo = ainda não houve. */
    fun decide(
        nowMs: Long,
        hasPending: Boolean,
        snapshotWanted: Boolean,
        acquisitionEnabled: Boolean,
        probeAgeMs: Long,
    ): Decision {
        if (hasPending) return Decision(Step.CONFIRM_PROBE)
        if (!snapshotWanted) {
            val roundWasOpen = planner.roundInProgress()
            val group = planner.nextGroup(nowMs, acquisitionEnabled)
            if (group != null) {
                if (!roundWasOpen) roundOpenedAtMs = nowMs
                if (probeAgeMs in 0..PROBE_FRESH_MS) {
                    if (!arbiter.tryBegin()) return Decision(Step.IDLE)
                    if (roundOpenedAtMs >= 0L) {
                        duty?.roundStarted(roundOpenedAtMs)
                        roundOpenedAtMs = -1L
                    }
                    return Decision(Step.RUN_GROUP, group)
                }
                return Decision(Step.PROBE)
            }
            if (probeAgeMs in 0 until PROBE_IDLE_MS) return Decision(Step.IDLE)
        }
        return Decision(Step.PROBE)
    }

    companion object {
        /** Probe "antes" de um grupo precisa ser desta idade ou mais novo; senão o tick refaz o probe primeiro. */
        const val PROBE_FRESH_MS = 1_500L

        /** Sem nada vencido, o status leve (contador AutoMatch) segue sendo lido ~1x/s como sempre. */
        const val PROBE_IDLE_MS = 1_000L
    }
}
