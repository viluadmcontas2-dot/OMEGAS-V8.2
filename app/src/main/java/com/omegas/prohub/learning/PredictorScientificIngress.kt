package com.omegas.prohub.learning

enum class PredictorScientificSourceType {
    DIRECT_OBSERVATION,
    POST_WRITE_OUTCOME,
    HISTORICAL_PRIOR,
    PREDICTION,
    SUGGESTION,
    DRAFT,
    UI,
    PREDICTED_CACHE,
}

data class PredictorScientificIngressClassification(
    val acceptedAsEvidence: Boolean,
    val acceptedAsPrior: Boolean,
    val countsAsAnchor: Boolean,
    val countsAsCurrentVisit: Boolean,
    val countsAsSupport: Boolean,
)

object PredictorScientificIngress {
    fun classify(source: PredictorScientificSourceType): PredictorScientificIngressClassification = when (source) {
        PredictorScientificSourceType.DIRECT_OBSERVATION -> PredictorScientificIngressClassification(
            acceptedAsEvidence = true,
            acceptedAsPrior = false,
            countsAsAnchor = true,
            countsAsCurrentVisit = true,
            countsAsSupport = true,
        )
        PredictorScientificSourceType.POST_WRITE_OUTCOME -> PredictorScientificIngressClassification(
            acceptedAsEvidence = true,
            acceptedAsPrior = false,
            countsAsAnchor = false,
            countsAsCurrentVisit = true,
            countsAsSupport = true,
        )
        PredictorScientificSourceType.HISTORICAL_PRIOR -> PredictorScientificIngressClassification(
            acceptedAsEvidence = false,
            acceptedAsPrior = true,
            countsAsAnchor = false,
            countsAsCurrentVisit = false,
            countsAsSupport = false,
        )
        PredictorScientificSourceType.PREDICTION,
        PredictorScientificSourceType.SUGGESTION,
        PredictorScientificSourceType.DRAFT,
        PredictorScientificSourceType.UI,
        PredictorScientificSourceType.PREDICTED_CACHE,
        -> PredictorScientificIngressClassification(
            acceptedAsEvidence = false,
            acceptedAsPrior = false,
            countsAsAnchor = false,
            countsAsCurrentVisit = false,
            countsAsSupport = false,
        )
    }
}

data class PredictorScientificIngressRecord(
    val provenanceId: String,
    val sourceType: PredictorScientificSourceType,
) {
    init {
        require(provenanceId.isNotBlank())
    }
}

data class PredictorScientificIngressResult(
    val sourceType: PredictorScientificSourceType,
    val acceptedAsEvidence: Boolean,
    val acceptedAsPrior: Boolean,
    val duplicate: Boolean,
)

data class PredictorScientificSupportSnapshot(
    val evidenceCount: Int,
    val anchorCount: Int,
    val currentVisitCount: Int,
    val supportCount: Int,
    val historicalPriorCount: Int,
) {
    companion object {
        val ZERO = PredictorScientificSupportSnapshot(0, 0, 0, 0, 0)
    }
}

/**
 * Provenance-deduplicated scientific support ledger.
 *
 * Prediction/display/persistence/recompute activity has no mutation path here.
 * Restore uses the same typed source firewall as live ingress, so cached predicted
 * cells can never become observations simply because they were serialized.
 */
class PredictorScientificSupportLedger {
    private val evidenceProvenance = linkedSetOf<String>()
    private val priorProvenance = linkedSetOf<String>()
    private var anchors = 0
    private var currentVisits = 0
    private var support = 0

    @Synchronized
    fun ingest(record: PredictorScientificIngressRecord): PredictorScientificIngressResult {
        val classification = PredictorScientificIngress.classify(record.sourceType)
        if (classification.acceptedAsEvidence) {
            val novel = evidenceProvenance.add(record.provenanceId)
            if (novel) {
                if (classification.countsAsAnchor) anchors = increment(anchors)
                if (classification.countsAsCurrentVisit) currentVisits = increment(currentVisits)
                if (classification.countsAsSupport) support = increment(support)
            }
            return PredictorScientificIngressResult(
                sourceType = record.sourceType,
                acceptedAsEvidence = novel,
                acceptedAsPrior = false,
                duplicate = !novel,
            )
        }
        if (classification.acceptedAsPrior) {
            val novel = priorProvenance.add(record.provenanceId)
            return PredictorScientificIngressResult(
                sourceType = record.sourceType,
                acceptedAsEvidence = false,
                acceptedAsPrior = novel,
                duplicate = !novel,
            )
        }
        return PredictorScientificIngressResult(
            sourceType = record.sourceType,
            acceptedAsEvidence = false,
            acceptedAsPrior = false,
            duplicate = false,
        )
    }

    @Synchronized
    fun restore(records: List<PredictorScientificIngressRecord>) {
        records.forEach(::ingest)
    }

    /** Recompute is deliberately not an evidence event. */
    fun onRecompute() = Unit

    @Synchronized
    fun snapshot(): PredictorScientificSupportSnapshot = PredictorScientificSupportSnapshot(
        evidenceCount = evidenceProvenance.size,
        anchorCount = anchors,
        currentVisitCount = currentVisits,
        supportCount = support,
        historicalPriorCount = priorProvenance.size,
    )

    private fun increment(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1
}
