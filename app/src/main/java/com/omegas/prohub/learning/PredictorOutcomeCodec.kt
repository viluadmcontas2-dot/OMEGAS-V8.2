package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned ledger/diagnostic serialization only; never a per-frame Predictor transport. */
object PredictorOutcomeCodec {
    const val TARGET_SCHEMA: String = "omegas-predictor-target-v155"
    const val OUTCOME_SCHEMA: String = "omegas-predictor-outcome-v155"

    fun encodeTarget(target: IdealTargetCandidate): String = encode(TARGET_SCHEMA) { out ->
        writeCell(out, target.cell)
        out.writeInt(target.targetK)
        out.writeDouble(target.kStarObserved)
        out.writeInt(target.currentKObserved)
        out.writeDouble(target.uncertaintyPercent)
        out.writeDouble(target.support)
        out.writeUTF(target.provenance)
        writeRevisions(out, target.sourceRevisions)
        out.writeDouble(target.estimateK)
        writeRange(out, target.range)
        out.writeUTF(target.authority.name)
        writeStrings(out, target.assumptions)
        writeStrings(out, target.evidenceRefs)
        writeModel(out, target.model)
        writeStats(out, target.predictionErrorStats)
    }

    fun decodeTarget(encoded: String): IdealTargetCandidate = decode(encoded, TARGET_SCHEMA) { input ->
        IdealTargetCandidate(
            cell = readCell(input),
            targetK = input.readInt(),
            kStarObserved = input.readDouble(),
            currentKObserved = input.readInt(),
            uncertaintyPercent = input.readDouble(),
            support = input.readDouble(),
            provenance = input.readUTF(),
            sourceRevisions = readRevisions(input),
            estimateK = input.readDouble(),
            range = readRange(input),
            authority = MagnitudeAuthority.valueOf(input.readUTF()),
            assumptions = readStrings(input),
            evidenceRefs = readStrings(input),
            model = readModel(input),
            predictionErrorStats = readStats(input),
        )
    }

    fun encodeOutcome(outcome: PredictionOutcome): String = encode(OUTCOME_SCHEMA) { out ->
        out.writeUTF(outcome.predictionId)
        out.writeUTF(outcome.predictionRevisionToken)
        writeCell(out, outcome.cell)
        out.writeDouble(outcome.predictedEstimateK)
        out.writeDouble(outcome.lowerK)
        out.writeDouble(outcome.upperK)
        writeNullableDouble(out, outcome.pImprove)
        writeOperatingPoint(out, outcome.context)
        writeNullableDouble(out, outcome.appliedTargetK)
        writeNullableDouble(out, outcome.actualKStar)
        out.writeUTF(outcome.authority.name)
        writeModel(out, outcome.model)
        writeStrings(out, outcome.evidenceRefs)
    }

    fun decodeOutcome(encoded: String): PredictionOutcome = decode(encoded, OUTCOME_SCHEMA) { input ->
        PredictionOutcome(
            predictionId = input.readUTF(),
            predictionRevisionToken = input.readUTF(),
            cell = readCell(input),
            predictedEstimateK = input.readDouble(),
            lowerK = input.readDouble(),
            upperK = input.readDouble(),
            pImprove = readNullableDouble(input),
            context = readOperatingPoint(input),
            appliedTargetK = readNullableDouble(input),
            actualKStar = readNullableDouble(input),
            authority = MagnitudeAuthority.valueOf(input.readUTF()),
            model = readModel(input),
            evidenceRefs = readStrings(input),
        )
    }

    private fun encode(schema: String, writer: (DataOutputStream) -> Unit): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use(writer)
            buffer.toByteArray()
        }
        return "$schema:${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }

    private fun <T> decode(
        encoded: String,
        expectedSchema: String,
        reader: (DataInputStream) -> T,
    ): T {
        require(encoded.startsWith("$expectedSchema:")) { "Unexpected Predictor ledger schema" }
        val payload = encoded.substring(expectedSchema.length + 1)
        require(payload.isNotBlank()) { "Empty Predictor ledger payload" }
        return try {
            val bytes = Base64.getUrlDecoder().decode(payload)
            ByteArrayInputStream(bytes).use { buffer ->
                DataInputStream(buffer).use { input ->
                    val value = reader(input)
                    require(input.available() == 0) { "Trailing Predictor ledger bytes" }
                    value
                }
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed Predictor ledger payload", error)
        }
    }

    private fun writeCell(out: DataOutputStream, cell: PredictorCell) {
        out.writeInt(cell.row)
        out.writeInt(cell.column)
    }

    private fun readCell(input: DataInputStream): PredictorCell = PredictorCell(
        row = input.readInt(),
        column = input.readInt(),
    )

    private fun writeRevisions(out: DataOutputStream, revisions: PredictorSourceRevisions) {
        out.writeLong(revisions.mapRevision)
        out.writeLong(revisions.curveRevision)
        out.writeLong(revisions.evidenceRevision)
        out.writeLong(revisions.referenceRevision)
        out.writeLong(revisions.physicsRevision)
    }

    private fun readRevisions(input: DataInputStream): PredictorSourceRevisions = PredictorSourceRevisions(
        mapRevision = input.readLong(),
        curveRevision = input.readLong(),
        evidenceRevision = input.readLong(),
        referenceRevision = input.readLong(),
        physicsRevision = input.readLong(),
    )

    private fun writeRange(out: DataOutputStream, range: PredictorTargetRange) {
        out.writeDouble(range.lowerK)
        out.writeDouble(range.upperK)
        out.writeUTF(range.basis)
    }

    private fun readRange(input: DataInputStream): PredictorTargetRange = PredictorTargetRange(
        lowerK = input.readDouble(),
        upperK = input.readDouble(),
        basis = input.readUTF(),
    )

    private fun writeModel(out: DataOutputStream, model: PredictorModelDescriptor) {
        out.writeUTF(model.modelFamily)
        out.writeUTF(model.modelVersion)
        out.writeUTF(model.confidenceCalibrationVersion)
    }

    private fun readModel(input: DataInputStream): PredictorModelDescriptor = PredictorModelDescriptor(
        modelFamily = input.readUTF(),
        modelVersion = input.readUTF(),
        confidenceCalibrationVersion = input.readUTF(),
    )

    private fun writeStats(out: DataOutputStream, stats: PredictorPredictionErrorStats) {
        out.writeInt(stats.sampleCount)
        out.writeInt(stats.intervalHitCount)
        out.writeInt(stats.intervalMissCount)
        out.writeDouble(stats.meanAbsoluteLogError)
        out.writeDouble(stats.calibrationError)
    }

    private fun readStats(input: DataInputStream): PredictorPredictionErrorStats = PredictorPredictionErrorStats(
        sampleCount = input.readInt(),
        intervalHitCount = input.readInt(),
        intervalMissCount = input.readInt(),
        meanAbsoluteLogError = input.readDouble(),
        calibrationError = input.readDouble(),
    )

    private fun writeStrings(out: DataOutputStream, values: List<String>) {
        out.writeInt(values.size)
        values.forEach(out::writeUTF)
    }

    private fun readStrings(input: DataInputStream): List<String> {
        val size = input.readInt()
        require(size in 0..10_000) { "Invalid Predictor ledger list size" }
        return List(size) { input.readUTF() }
    }

    private fun writeNullableDouble(out: DataOutputStream, value: Double?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeDouble(value)
    }

    private fun readNullableDouble(input: DataInputStream): Double? =
        if (input.readBoolean()) input.readDouble() else null

    private fun writeOperatingPoint(out: DataOutputStream, point: PredictorOperatingPoint) {
        out.writeDouble(point.rpm)
        out.writeDouble(point.petrolInjectionMs)
        writeNullableDouble(out, point.mapBar)
        writeNullableDouble(out, point.deltaPressureBar)
        writeNullableDouble(out, point.petrolReferenceTemperatureC)
        writeNullableDouble(out, point.waterTemperatureC)
        writeNullableDouble(out, point.gasTemperatureC)
        writeNullableDouble(out, point.effectiveMass)
        writeNullableDouble(out, point.effectiveCapacity)
    }

    private fun readOperatingPoint(input: DataInputStream): PredictorOperatingPoint = PredictorOperatingPoint(
        rpm = input.readDouble(),
        petrolInjectionMs = input.readDouble(),
        mapBar = readNullableDouble(input),
        deltaPressureBar = readNullableDouble(input),
        petrolReferenceTemperatureC = readNullableDouble(input),
        waterTemperatureC = readNullableDouble(input),
        gasTemperatureC = readNullableDouble(input),
        effectiveMass = readNullableDouble(input),
        effectiveCapacity = readNullableDouble(input),
    )
}
