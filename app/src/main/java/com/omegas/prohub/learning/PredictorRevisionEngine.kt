package com.omegas.prohub.learning

data class PredictorScientificRevisionKey(
    val evidenceRevision: Long,
    val referenceRevision: Long,
    val calibrationRevision: Long,
    val geometryRevision: Long,
    val physicsRevision: Long,
    val modelCalibrationRevision: Long,
    val sensitivityRevision: Long,
) {
    init {
        require(evidenceRevision > 0L)
        require(referenceRevision > 0L)
        require(calibrationRevision > 0L)
        require(geometryRevision > 0L)
        require(physicsRevision > 0L)
        require(modelCalibrationRevision > 0L)
        require(sensitivityRevision > 0L)
    }

    /** Primitive stable token; no JSON/string scan enters the numerical path. */
    fun token(): Long {
        var hash = -3750763034362895579L
        fun mix(value: Long) {
            hash = hash xor value
            hash *= 1099511628211L
        }
        mix(evidenceRevision)
        mix(referenceRevision)
        mix(calibrationRevision)
        mix(geometryRevision)
        mix(physicsRevision)
        mix(modelCalibrationRevision)
        mix(sensitivityRevision)
        return hash
    }
}

data class PredictorCellRef(
    val row: Int,
    val column: Int,
)

enum class PredictorRecomputeKind {
    NOOP,
    PATCH,
    FULL,
}

data class PredictorRecomputePlan(
    val kind: PredictorRecomputeKind,
    val revisionToken: Long,
    val key: PredictorScientificRevisionKey,
    val cells: Set<PredictorCellRef>,
)

data class PredictorRevisionMetrics(
    val requestedPlans: Long,
    val computedPlans: Long,
)

/**
 * Revision-only planner for Predictor science. Visual telemetry, route, selection,
 * inspector and render state are intentionally absent from the input contract.
 */
class PredictorRevisionEngine(
    private val rows: Int,
    private val columns: Int,
) {
    init {
        require(rows > 0)
        require(columns > 0)
    }

    private var acceptedKey: PredictorScientificRevisionKey? = null
    private var requestedPlans = 0L
    private var computedPlans = 0L

    @Synchronized
    fun plan(
        key: PredictorScientificRevisionKey,
        affectedCells: Set<PredictorCellRef> = emptySet(),
    ): PredictorRecomputePlan {
        requestedPlans = saturatingIncrement(requestedPlans)
        validateCells(affectedCells)
        val previous = acceptedKey
        val token = key.token()
        if (previous == key) {
            return PredictorRecomputePlan(
                kind = PredictorRecomputeKind.NOOP,
                revisionToken = token,
                key = key,
                cells = emptySet(),
            )
        }

        val global = previous == null || globalDependencyChanged(previous, key)
        val cells = when {
            global -> fullGrid()
            affectedCells.isNotEmpty() -> affectedCells.toSet()
            else -> fullGrid()
        }
        return PredictorRecomputePlan(
            kind = if (cells.size == rows * columns) PredictorRecomputeKind.FULL else PredictorRecomputeKind.PATCH,
            revisionToken = token,
            key = key,
            cells = cells,
        )
    }

    @Synchronized
    fun accept(plan: PredictorRecomputePlan) {
        if (plan.kind == PredictorRecomputeKind.NOOP) return
        require(plan.revisionToken == plan.key.token()) { "revision token/key mismatch" }
        validateCells(plan.cells)
        acceptedKey = plan.key
        computedPlans = saturatingIncrement(computedPlans)
    }

    @Synchronized
    fun metrics(): PredictorRevisionMetrics = PredictorRevisionMetrics(
        requestedPlans = requestedPlans,
        computedPlans = computedPlans,
    )

    private fun globalDependencyChanged(
        previous: PredictorScientificRevisionKey,
        next: PredictorScientificRevisionKey,
    ): Boolean =
        previous.calibrationRevision != next.calibrationRevision ||
            previous.geometryRevision != next.geometryRevision ||
            previous.physicsRevision != next.physicsRevision ||
            previous.modelCalibrationRevision != next.modelCalibrationRevision

    private fun fullGrid(): Set<PredictorCellRef> = buildSet(rows * columns) {
        repeat(rows) { row ->
            repeat(columns) { column -> add(PredictorCellRef(row, column)) }
        }
    }

    private fun validateCells(cells: Set<PredictorCellRef>) {
        cells.forEach { cell ->
            require(cell.row in 0 until rows) { "row outside Predictor geometry" }
            require(cell.column in 0 until columns) { "column outside Predictor geometry" }
        }
    }

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}
