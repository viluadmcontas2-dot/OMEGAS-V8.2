from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
SIGNAL = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"
WRITER = ROOT / "app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt"
RECORDER = ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt"
MODELS = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningEvidenceModels.kt"


class PersistenceAfterValueContract(unittest.TestCase):
    def test_main_learning_memory_does_not_persist_rejected_sample(self):
        source = MEMORY.read_text("utf-8")
        ingest = source.index("fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision)")
        reject = source.index("if (sample == null || !decision.learningEligible)", ingest)
        first_persist = source.index("persist()", reject)
        accepted_update = source.index("val region = updateRegion(sample, visit)", reject)
        self.assertLess(reject, accepted_update)
        self.assertGreater(first_persist, accepted_update)

    def test_novelty_is_decided_before_delegate_and_sidecar_request(self):
        source = SIGNAL.read_text("utf-8")
        ingest = source.index("fun ingest(telemetry: Mp48Telemetry, decision: SampleDecision)")
        novelty = source.index("ContinuousWindowNovelty.calculate(", ingest)
        duplicate = source.index("novelty.duplicate ->", novelty)
        duplicate_null = source.index("sample = null", duplicate)
        prior_scope = source.index("NativePetrolPriorScope.withAnchors(nativePetrolPriors)", duplicate_null)
        delegate = source.index("delegate.ingest(telemetry, prepared)", prior_scope)
        persist = source.index("persistEvidenceState()", delegate)
        self.assertLess(novelty, duplicate)
        self.assertLess(duplicate_null, prior_scope)
        self.assertLess(prior_scope, delegate)
        self.assertLess(delegate, persist)

    def test_sidecar_contains_bounded_rejection_provenance_not_raw_telemetry_frame(self):
        source = SIGNAL.read_text("utf-8")
        start = source.index("private fun buildEvidencePayload")
        end = source.index("private fun loadEvidenceState", start)
        body = source[start:end]
        for expected in (
            '"evidenceProvenance"',
            '"performanceMetrics"',
            '"visitAccumulators"',
            '"nativeEcuEvidence"',
        ):
            self.assertIn(expected, body)
        for forbidden in (
            "Mp48Telemetry",
            "telemetry.toJson",
            '"live"',
            '"petrol_ms"',
            '"rpm"',
        ):
            self.assertNotIn(forbidden, body)

        provenance = MODELS.read_text("utf-8")
        start = provenance.index("data class EvidenceProvenance")
        end = provenance.index("data class VisitComparisonAccumulator", start)
        body = provenance[start:end]
        self.assertIn("newFrameCount", body)
        self.assertIn("reusedFrameCount", body)
        self.assertIn("noveltyRatio", body)
        self.assertNotIn("rpm", body)
        self.assertNotIn("petrolMs", body)
        self.assertNotIn("mapBar", body)

    def test_snapshot_writer_builds_payload_and_writes_on_dedicated_executor(self):
        source = WRITER.read_text("utf-8")
        request = source.index("fun request(payloadProvider: () -> String)")
        schedule = source.index("scheduleDrain()", request)
        executor = source.index("executor.execute { drain() }")
        provider = source.index("latestPayloadProvider?.invoke()", executor)
        disk = source.index("writeAtomically(payload)", provider)
        self.assertLess(request, schedule)
        self.assertLess(executor, provider)
        self.assertLess(provider, disk)

    def test_session_recorder_file_write_is_worker_side_and_bounded(self):
        source = RECORDER.read_text("utf-8")
        self.assertIn("private const val QUEUE_CAPACITY = 8192", source)
        self.assertIn("ArrayBlockingQueue(QUEUE_CAPACITY)", source)

        record_start = source.index("fun record(type: String, source: String, data: JSONObject")
        record_end = source.index("fun recordRawUsb(", record_start)
        record = source[record_start:record_end]
        enqueue = record.index("enqueuePayload(")
        encoded_write = record.index("recordEncodedNow(type, source, dataJson, summary)", enqueue)
        self.assertLess(enqueue, encoded_write)
        self.assertNotIn("worker.execute", record)

        enqueue_start = source.index("private fun enqueuePayload(")
        enqueue_end = source.index("private fun consumerBudgetJson", enqueue_start)
        enqueue_body = source[enqueue_start:enqueue_end]
        self.assertIn("worker.execute(RecorderTask(", enqueue_body)
        self.assertIn('type == "engine_event" && data.optString("event") == "telemetry"', source)
        self.assertIn("sessionTelemetryEveryMs", source)
        self.assertIn("droppedEvents.incrementAndGet()", source)


if __name__ == "__main__":
    unittest.main()
