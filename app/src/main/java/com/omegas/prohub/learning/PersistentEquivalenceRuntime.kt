package com.omegas.prohub.learning

import org.json.JSONObject
import java.io.File

/**
 * Persistence shell around [EquivalenceRuntime]. Heavy sparse snapshot/JSON work runs
 * only inside [CoalescedSnapshotWriter]'s dedicated thread. The hot path performs
 * bounded arithmetic plus atomic gate/request bookkeeping.
 */
internal class PersistentEquivalenceRuntime(
    stateFile: File,
    private val runtime: EquivalenceRuntime = EquivalenceRuntime(),
    private val snapshotEncoder: (EquivalenceSurface.Snapshot) -> String = EquivalenceSurfaceCodec::encode,
) : AutoCloseable {
    private val lock = Any()
    private val target = File(
        stateFile.parentFile ?: stateFile.absoluteFile.parentFile,
        "learning_equivalence_v1.json",
    )
    private val writer = CoalescedSnapshotWriter(
        target = target,
        threadName = "omegas-learning-equivalence-persist",
    )
    private val persistenceGate = MaterialPersistenceGate()

    init {
        load()
    }

    fun observe(
        lane: FuelLane,
        rpm: Double,
        mapBar: Double,
        petrolTinjMs: Double,
        stability: Double,
        novelty: Double,
        materialRevision: Long,
    ): EquivalenceRuntime.ObserveOutcome {
        val result = synchronized(lock) {
            runtime.observe(
                lane = lane,
                rpm = rpm,
                mapBar = mapBar,
                petrolTinjMs = petrolTinjMs,
                stability = stability,
                novelty = novelty,
                materialRevision = materialRevision,
            )
        }
        if (result.scientificWeight > 0.0) {
            persistenceGate.markMaterialChange()
            persist()
        }
        return result
    }

    fun query(rpm: Double, mapBar: Double): EquivalenceSurface.QueryResult =
        synchronized(lock) { runtime.query(rpm, mapBar) }

    fun estimate(rpm: Double, mapBar: Double): EquivalenceRuntime.EquivalenceEstimate? =
        synchronized(lock) { runtime.estimate(rpm, mapBar) }

    fun totalWeight(lane: FuelLane): Double = synchronized(lock) { runtime.totalWeight(lane) }

    fun allocatedScalarCount(): Int = synchronized(lock) { runtime.allocatedScalarCount() }

    /** A gas-calibration epoch changed: gasoline ruler survives; old CNG residual does not. */
    fun clearCngForCalibrationAdjustment() {
        synchronized(lock) {
            runtime.surface.clearLane(FuelLane.CNG_PETROL_OBSERVED)
        }
        persistenceGate.markMaterialChange()
        persist(forceBoundary = true)
    }

    fun flush(timeoutMs: Long = 5_000L): Boolean {
        persist(forceBoundary = true)
        return writer.flush(timeoutMs)
    }

    fun metricsJson(): JSONObject = writer.metricsJson()
        .put("representation", EquivalenceSurfaceCodec.REPRESENTATION)
        .put("materialGate", persistenceGate.metricsJson())

    override fun close() {
        persist(forceBoundary = true)
        writer.flush(10_000L)
        writer.close()
    }

    private fun persist(forceBoundary: Boolean = false) {
        if (!persistenceGate.shouldRequest(forceBoundary)) return
        writer.request {
            val snapshot = synchronized(lock) { runtime.surface.snapshot() }
            snapshotEncoder(snapshot)
        }
    }

    private fun load() {
        if (!target.isFile) return
        try {
            val decoded = EquivalenceSurfaceCodec.decode(target.readText(Charsets.UTF_8))
            synchronized(lock) {
                runtime.surface.restore(decoded)
            }
        } catch (_: Exception) {
            val quarantine = File(target.parentFile, "${target.name}.invalid")
            if (quarantine.exists()) quarantine.delete()
            target.renameTo(quarantine)
        }
    }
}
