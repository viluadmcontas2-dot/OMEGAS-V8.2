package com.omegas.prohub.obd

import kotlin.math.abs

/**
 * Fronteira pura entre leitura OBD ao vivo e evidência utilizável no mapa OBD.
 *
 * A escolha manual de combustível existe para tornar a leitura compreensível
 * quando a MP48 cai, mas nunca cria uma célula física nem reativa aprendizado.
 */
object ObdLearningGate {
    enum class FuelSource { MP48_CONFIRMED, MANUAL_OPERATOR, UNKNOWN }

    enum class ReasonCode {
        ACCEPTED,
        MP48_UNAVAILABLE,
        FUEL_NOT_CONFIRMED,
        PID_TIMESTAMP_INVALID,
        STFT_READ_TOO_SLOW,
        RPM_READ_TOO_SLOW,
        STFT_MP48_SKEW,
        RPM_MP48_SKEW,
        CLOSED_LOOP_CONTEXT_STALE,
        COOLANT_CONTEXT_STALE,
        FUEL_TRANSITION,
        STFT_UNAVAILABLE,
        OPEN_LOOP,
        ENGINE_COLD,
        OBD_RPM_INVALID,
        MP48_CELL_INVALID,
        RPM_DIVERGENCE,
    }

    data class FuelState(
        val fuel: String?,
        val source: FuelSource,
        val canQualifyMap: Boolean,
    )

    data class Input(
        val mp48Present: Boolean,
        val mp48Fuel: String?,
        val manualFuel: String?,
        /** RPM da amostra MP48 capturada mais perto do RPM OBD. */
        val mp48Rpm: Double,
        /** Pulso gasolina da amostra MP48 capturada mais perto do STFT. */
        val petrolInjectionMs: Double,
        /** Compatibilidade: horário MP48 associado ao STFT. */
        val mp48ObservedAtMs: Long,
        val obdRpm: Double?,
        val obdObservedAtMs: Long,
        val stftObservedAtMs: Long = obdObservedAtMs,
        val obdRpmObservedAtMs: Long = obdObservedAtMs,
        val closedLoopObservedAtMs: Long = obdObservedAtMs,
        val stftStartedAtMs: Long = stftObservedAtMs,
        val obdRpmStartedAtMs: Long = obdRpmObservedAtMs,
        val closedLoopStartedAtMs: Long = closedLoopObservedAtMs,
        val coolantObservedAtMs: Long = obdObservedAtMs,
        val stftMp48ObservedAtMs: Long = mp48ObservedAtMs,
        val rpmMp48ObservedAtMs: Long = mp48ObservedAtMs,
        val stft: Double?,
        val coolantC: Double?,
        val closedLoop: Boolean,
        val maxRpmDifference: Double,
        val maxTimeSkewMs: Long,
        val maxContextAgeMs: Long = 15_000L,
        val fuelTransition: Boolean = false,
    )

    data class Metrics(
        val stftReadMs: Long?,
        val rpmReadMs: Long?,
        val stftMp48SkewMs: Long?,
        val rpmMp48SkewMs: Long?,
        val closedLoopAgeMs: Long?,
        val coolantAgeMs: Long?,
        val rpmDifference: Double?,
        val configuredPairLimitMs: Long,
        val configuredContextLimitMs: Long,
    )

    data class Decision(
        val accepted: Boolean,
        val reason: String,
        val fuelState: FuelState,
        val reasonCode: ReasonCode,
        /** Rejeição temporal isolada não precisa apagar toda a janela estável. */
        val resetCondition: Boolean,
        val metrics: Metrics,
    )

    fun fuelState(mp48Present: Boolean, mp48Fuel: String?, manualFuel: String?): FuelState {
        val confirmed = normalizeFuel(mp48Fuel)
        if (mp48Present && confirmed != null) return FuelState(confirmed, FuelSource.MP48_CONFIRMED, true)
        val declared = normalizeFuel(manualFuel)
        if (!mp48Present && declared != null) return FuelState(declared, FuelSource.MANUAL_OPERATOR, false)
        return FuelState(null, FuelSource.UNKNOWN, false)
    }

    fun evaluate(input: Input, minimumCoolantC: Double): Decision {
        val fuel = fuelState(input.mp48Present, input.mp48Fuel, input.manualFuel)
        val stftReadMs = duration(input.stftStartedAtMs, input.stftObservedAtMs)
        val rpmReadMs = duration(input.obdRpmStartedAtMs, input.obdRpmObservedAtMs)
        val stftSkew = skew(input.stftObservedAtMs, input.stftMp48ObservedAtMs)
        val rpmSkew = skew(input.obdRpmObservedAtMs, input.rpmMp48ObservedAtMs)
        val closedLoopAge = age(input.stftObservedAtMs, input.closedLoopObservedAtMs)
        val coolantAge = age(input.stftObservedAtMs, input.coolantObservedAtMs)
        val rpmDifference = input.obdRpm?.takeIf { it.isFinite() }?.let { abs(it - input.mp48Rpm) }
        val metrics = Metrics(
            stftReadMs = stftReadMs,
            rpmReadMs = rpmReadMs,
            stftMp48SkewMs = stftSkew,
            rpmMp48SkewMs = rpmSkew,
            closedLoopAgeMs = closedLoopAge,
            coolantAgeMs = coolantAge,
            rpmDifference = rpmDifference,
            configuredPairLimitMs = input.maxTimeSkewMs,
            configuredContextLimitMs = input.maxContextAgeMs,
        )

        fun reject(code: ReasonCode, message: String, reset: Boolean) =
            Decision(false, message, fuel, code, reset, metrics)

        return when {
            !input.mp48Present ->
                reject(ReasonCode.MP48_UNAVAILABLE, "MP48 indisponível: aprendizado principal pausado", true)
            fuel.source != FuelSource.MP48_CONFIRMED ->
                reject(ReasonCode.FUEL_NOT_CONFIRMED, "combustível MP48 não confirmado", true)
            listOf(
                input.stftStartedAtMs to input.stftObservedAtMs,
                input.obdRpmStartedAtMs to input.obdRpmObservedAtMs,
            ).any { (start, end) -> start <= 0L || end < start } ->
                reject(ReasonCode.PID_TIMESTAMP_INVALID, "horário individual de PID indisponível", false)
            stftReadMs == null || stftReadMs > input.maxTimeSkewMs ->
                reject(ReasonCode.STFT_READ_TOO_SLOW, "leitura STFT excedeu ${input.maxTimeSkewMs} ms", false)
            rpmReadMs == null || rpmReadMs > input.maxTimeSkewMs ->
                reject(ReasonCode.RPM_READ_TOO_SLOW, "leitura RPM OBD excedeu ${input.maxTimeSkewMs} ms", false)
            stftSkew == null || stftSkew > input.maxTimeSkewMs ->
                reject(ReasonCode.STFT_MP48_SKEW, "STFT e MP48 fora de sincronia temporal", false)
            rpmSkew == null || rpmSkew > input.maxTimeSkewMs ->
                reject(ReasonCode.RPM_MP48_SKEW, "RPM OBD e MP48 fora de sincronia temporal", false)
            closedLoopAge == null || closedLoopAge > input.maxContextAgeMs ->
                reject(ReasonCode.CLOSED_LOOP_CONTEXT_STALE, "estado de malha OBD desatualizado", false)
            coolantAge == null || coolantAge > input.maxContextAgeMs ->
                reject(ReasonCode.COOLANT_CONTEXT_STALE, "temperatura OBD desatualizada", false)
            input.fuelTransition ->
                reject(ReasonCode.FUEL_TRANSITION, "transição de combustível", true)
            input.stft == null || !input.stft.isFinite() ->
                reject(ReasonCode.STFT_UNAVAILABLE, "STFT indisponível", false)
            !input.closedLoop ->
                reject(ReasonCode.OPEN_LOOP, "malha aberta", true)
            (input.coolantC ?: -100.0) < minimumCoolantC ->
                reject(ReasonCode.ENGINE_COLD, "motor ainda frio", true)
            (input.obdRpm ?: 0.0) < 500.0 ->
                reject(ReasonCode.OBD_RPM_INVALID, "RPM OBD inválida", true)
            input.mp48Rpm < 500.0 || input.petrolInjectionMs <= 0.0 ->
                reject(ReasonCode.MP48_CELL_INVALID, "sem célula MP48 válida", true)
            (rpmDifference ?: Double.POSITIVE_INFINITY) > input.maxRpmDifference ->
                reject(ReasonCode.RPM_DIVERGENCE, "RPM OBD e MP48 divergentes", true)
            else -> Decision(
                accepted = true,
                reason = "Amostra qualificada: MP48 confirmada, motor aquecido e malha fechada",
                fuelState = fuel,
                reasonCode = ReasonCode.ACCEPTED,
                resetCondition = false,
                metrics = metrics,
            )
        }
    }

    fun directGnvSignal(stft: Double, neutralBandPct: Double): String = when {
        stft > neutralBandPct -> "TENDÊNCIA POBRE · aumentar combustível gradualmente"
        stft < -neutralBandPct -> "TENDÊNCIA RICA · reduzir combustível gradualmente"
        else -> "PRÓXIMO DO ALVO · confirmar"
    }

    fun gasolineAdvisory(stft: Double?, neutralBandPct: Double): String? =
        stft?.takeIf { abs(it) > neutralBandPct }?.let { "GASOLINA_FORA_DO_NEUTRO" }

    private fun duration(start: Long, end: Long): Long? =
        if (start > 0L && end >= start) end - start else null

    private fun skew(first: Long, second: Long): Long? =
        if (first > 0L && second > 0L) abs(first - second) else null

    private fun age(reference: Long, observed: Long): Long? =
        if (reference > 0L && observed > 0L) abs(reference - observed) else null

    private fun normalizeFuel(value: String?): String? = when (value?.trim()?.uppercase()) {
        "GNV", "CNG", "GAS" -> "GNV"
        "GASOLINA", "PETROL", "BENZINA" -> "GASOLINA"
        else -> null
    }
}
