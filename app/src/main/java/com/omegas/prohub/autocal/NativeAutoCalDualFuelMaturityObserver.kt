package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.learning.NativeAutoCalEventCorrelator

/**
 * Observa maturidade das duas famílias de aquisição no MESMO scheduler/health tick.
 * Não possui timer, thread ou writer. Cada leitura é READ_ONLY e devolve a porta
 * à telemetria pelo scheduler normal.
 *
 * O bootstrap é mínimo e acontece no máximo uma vez por sessão: lê apenas enable,
 * thresholds necessários e os dois vetores de contadores para estabelecer baseline.
 * Não executa full snapshot e não depende de UI/rota.
 */
class NativeAutoCalDualFuelMaturityObserver(
    private val serial: Mp48SerialScheduler,
    private val elapsedRealtime: () -> Long,
) {
    data class Event(
        val sourceFuel: NativeAutoCalEventCorrelator.SourceFuel,
        val transition: NativeAutoCalMaturityTracker.Transition,
        val counterPayloadHex: String,
    )

    data class Observation(
        val events: List<Event>,
        val petrolRead: Boolean,
        val cngRead: Boolean,
    )

    data class Readiness(
        val petrol: Boolean,
        val cng: Boolean,
    )

    data class BootstrapState(
        val attempted: Boolean,
        val complete: Boolean,
    )

    private val petrolTracker = NativeAutoCalMaturityTracker()
    private val cngTracker = NativeAutoCalMaturityTracker()

    private var enabled = false
    private var petrolLowThreshold: Int? = null
    private var petrolNormalThreshold: Int? = null
    private var cngLowThreshold: Int? = null
    private var cngNormalThreshold: Int? = null
    private var bootstrapAttempted = false
    private var bootstrapComplete = false

    @Synchronized
    fun reset() {
        enabled = false
        petrolLowThreshold = null
        petrolNormalThreshold = null
        cngLowThreshold = null
        cngNormalThreshold = null
        bootstrapAttempted = false
        bootstrapComplete = false
        petrolTracker.reset()
        cngTracker.reset()
    }

    @Synchronized
    fun configure(
        enabled: Boolean,
        petrolLowThreshold: Int?,
        petrolNormalThreshold: Int?,
        cngLowThreshold: Int?,
        cngNormalThreshold: Int?,
    ) {
        this.enabled = enabled
        this.petrolLowThreshold = petrolLowThreshold
        this.petrolNormalThreshold = petrolNormalThreshold
        this.cngLowThreshold = cngLowThreshold
        this.cngNormalThreshold = cngNormalThreshold
    }

    @Synchronized
    fun readiness(): Readiness = Readiness(
        petrol = enabled && thresholdKnown(petrolLowThreshold) && thresholdKnown(petrolNormalThreshold),
        cng = enabled && thresholdKnown(cngLowThreshold) && thresholdKnown(cngNormalThreshold),
    )

    @Synchronized
    fun bootstrapState(): BootstrapState = BootstrapState(bootstrapAttempted, bootstrapComplete)

    /**
     * Bootstrap read-only mínimo. Uma tentativa por sessão; falha permanece explícita
     * até um snapshot material posterior fornecer configuração/baseline ou a sessão reiniciar.
     */
    fun ensureBootstrap(expectedSessionId: Long): Boolean {
        synchronized(this) {
            if (bootstrapAttempted) return bootstrapComplete
            bootstrapAttempted = true
        }

        val enable = readDecoded(expectedSessionId, AutoCalProtocol.AUTO_CAL_ENABLE, "AutoCal bootstrap enable")
            ?.rawValues?.singleOrNull()
            ?: return false
        val petrolLow = readDecoded(expectedSessionId, AutoCalProtocol.VECT_AUTOCAL_U8_1, "AutoCal bootstrap threshold gasolina")
            ?.rawValues?.singleOrNull()
            ?: return false
        val calibration = readDecoded(expectedSessionId, AutoCalProtocol.CALIBRATION_VAL_1, "AutoCal bootstrap thresholds")
            ?.rawValues
            ?: return false
        val petrolCounters = readDecoded(expectedSessionId, AutoCalProtocol.NUM_BUF_UPD_PETR, "AutoCal bootstrap contadores gasolina")
            ?.rawValues?.takeIf { it.size == NativeAutoCalProgression.ACQUISITION_BANDS }
            ?: return false
        val cngCounters = readDecoded(expectedSessionId, AutoCalProtocol.NUM_BUF_UPD_GAS, "AutoCal bootstrap contadores GNV")
            ?.rawValues?.takeIf { it.size == NativeAutoCalProgression.ACQUISITION_BANDS }
            ?: return false

        val petrolNormal = calibration.getOrNull(2) ?: return false
        val cngLow = calibration.getOrNull(5) ?: return false
        val cngNormal = calibration.getOrNull(8) ?: return false
        val observedAt = elapsedRealtime()
        configure(
            enabled = enable == 1,
            petrolLowThreshold = petrolLow,
            petrolNormalThreshold = petrolNormal,
            cngLowThreshold = cngLow,
            cngNormalThreshold = cngNormal,
        )
        baseline(petrolCounters, cngCounters, observedAt)
        synchronized(this) { bootstrapComplete = true }
        return true
    }

    fun observe(expectedSessionId: Long): Observation {
        val ready = readiness()
        val petrol = if (ready.petrol) {
            probe(
                expectedSessionId = expectedSessionId,
                field = AutoCalProtocol.NUM_BUF_UPD_PETR,
                sourceFuel = NativeAutoCalEventCorrelator.SourceFuel.PETROL,
                tracker = petrolTracker,
                lowThreshold = synchronized(this) { petrolLowThreshold },
                normalThreshold = synchronized(this) { petrolNormalThreshold },
                reason = "AutoCal maturidade gasolina",
            )
        } else null
        val cng = if (ready.cng) {
            probe(
                expectedSessionId = expectedSessionId,
                field = AutoCalProtocol.NUM_BUF_UPD_GAS,
                sourceFuel = NativeAutoCalEventCorrelator.SourceFuel.CNG,
                tracker = cngTracker,
                lowThreshold = synchronized(this) { cngLowThreshold },
                normalThreshold = synchronized(this) { cngNormalThreshold },
                reason = "AutoCal maturidade GNV",
            )
        } else null
        return Observation(
            events = petrol.orEmpty() + cng.orEmpty(),
            petrolRead = petrol != null,
            cngRead = cng != null,
        )
    }

    fun baseline(
        petrolCounters: IntArray?,
        cngCounters: IntArray?,
        observedAtElapsedMs: Long = elapsedRealtime(),
    ) {
        petrolCounters?.let { petrolTracker.baseline(it, observedAtElapsedMs) }
        cngCounters?.let { cngTracker.baseline(it, observedAtElapsedMs) }
        if (petrolCounters?.size == NativeAutoCalProgression.ACQUISITION_BANDS &&
            cngCounters?.size == NativeAutoCalProgression.ACQUISITION_BANDS
        ) {
            synchronized(this) { bootstrapComplete = true }
        }
    }

    private fun probe(
        expectedSessionId: Long,
        field: AutoCalProtocol.Field,
        sourceFuel: NativeAutoCalEventCorrelator.SourceFuel,
        tracker: NativeAutoCalMaturityTracker,
        lowThreshold: Int?,
        normalThreshold: Int?,
        reason: String,
    ): List<Event>? {
        val reply = serial.transaction(
            request = AutoCalProtocol.read(field),
            reason = reason,
            timeoutMs = 900,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (!reply.ok) return null
        val decoded = try {
            AutoCalProtocol.decode(field, reply.status, reply.payload)
        } catch (_: Exception) {
            return null
        }
        val observedAt = elapsedRealtime()
        return tracker.observe(
            counters = decoded.rawValues,
            gasLowThreshold = lowThreshold,
            gasNormalThreshold = normalThreshold,
            enabled = true,
            observedAtElapsedMs = observedAt,
        ).map { transition ->
            Event(
                sourceFuel = sourceFuel,
                transition = transition,
                counterPayloadHex = reply.payload.toHex(),
            )
        }
    }

    private fun readDecoded(
        expectedSessionId: Long,
        field: AutoCalProtocol.Field,
        reason: String,
    ): AutoCalProtocol.Decoded? {
        val reply = serial.transaction(
            request = AutoCalProtocol.read(field),
            reason = reason,
            timeoutMs = 900,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (!reply.ok) return null
        return try {
            AutoCalProtocol.decode(field, reply.status, reply.payload)
        } catch (_: Exception) {
            null
        }
    }

    private fun thresholdKnown(value: Int?): Boolean = value != null && value > 0

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
