from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
BUFFER = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"


class LearningGenerationBoundaryContract(unittest.TestCase):
    def test_new_session_generation_is_switched_inside_same_lock_used_by_ingest(self):
        source = RUNTIME.read_text("utf-8")
        begin = source.index("fun beginUsbSession(sessionId: Long)")
        block = source.index("val learningState = synchronized(learningSessionLock)", begin)
        session = source.index("currentUsbSessionId = sessionId", block)
        generation = source.index("learningPipeline.beginGeneration(sessionId)", block)
        start = source.index("learning.startSession()", block)
        block_end = source.index("latestCanonicalEvidence.beginGeneration(sessionId)", start)
        self.assertLess(block, session)
        self.assertLess(session, generation)
        self.assertLess(generation, start)
        self.assertLess(start, block_end)

    def test_old_task_checks_generation_before_and_inside_learning_lock(self):
        source = RUNTIME.read_text("utf-8")
        submit = source.index("val accepted = learningPipeline.submit(")
        before = source.index("if (generation != currentUsbSessionId) return@submit", submit)
        lock = source.index("synchronized(learningSessionLock)", before)
        inside = source.index("if (generation != currentUsbSessionId) return@synchronized", lock)
        ingest = source.index("learning.ingest(telemetry, decision)", inside)
        publish = source.index("if (generation == currentUsbSessionId) publishLearningState", ingest)
        self.assertLess(before, lock)
        self.assertLess(lock, inside)
        self.assertLess(inside, ingest)
        self.assertLess(ingest, publish)

    def test_buffer_purges_queued_old_generation_and_rejects_stale_submit(self):
        source = BUFFER.read_text("utf-8")
        self.assertIn("currentGeneration = generation", source)
        self.assertIn("purgeQueuedLocked()", source)
        self.assertIn("generation != currentGeneration", source)
        self.assertIn("rejectedStale.incrementAndGet()", source)


if __name__ == "__main__":
    unittest.main()
