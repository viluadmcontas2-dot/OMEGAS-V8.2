from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
BUFFER = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"


class LearningGenerationBoundaryContract(unittest.TestCase):
    def test_new_session_switches_all_generation_owners_before_publication(self):
        source = RUNTIME.read_text("utf-8")
        begin = source.index("fun beginUsbSession(sessionId: Long)")
        session = source.index("currentUsbSessionId = sessionId", begin)
        learning_generation = source.index("learningPipeline.beginGeneration(sessionId)", session)
        learning_start = source.index("learning.startSession()", learning_generation)
        canonical_generation = source.index("latestCanonicalEvidence.beginGeneration(sessionId)", learning_start)
        publish = source.index("publishLearningState(0L, learningState)", canonical_generation)
        self.assertLess(session, learning_generation)
        self.assertLess(learning_generation, learning_start)
        self.assertLess(learning_start, canonical_generation)
        self.assertLess(canonical_generation, publish)

    def test_old_learning_task_is_rejected_on_both_sides_of_lock(self):
        source = RUNTIME.read_text("utf-8")
        submit = source.index("val accepted = learningPipeline.submit(")
        task = source[submit : source.index("if (accepted && workClass", submit)]
        self.assertIn("if (generation != currentUsbSessionId) return@submit", task)
        self.assertIn("synchronized(learningSessionLock)", task)
        self.assertIn("if (generation != currentUsbSessionId) return@synchronized", task)
        self.assertIn("learning.ingest(evidence.rawTelemetry, evidence.sampleDecision)", task)
        self.assertIn("if (generation == currentUsbSessionId) publishLearningState", task)

    def test_buffer_purges_old_generation_and_rejects_stale_submit(self):
        source = BUFFER.read_text("utf-8")
        for marker in (
            "currentGeneration = generation",
            "purgeQueuedLocked()",
            "generation != currentGeneration",
            "rejectedStale.incrementAndGet()",
        ):
            self.assertIn(marker, source)


if __name__ == "__main__":
    unittest.main()
