from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"
LATEST = ROOT / "app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt"
SCIENCE = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"
WORK = ROOT / "app/src/main/java/com/omegas/prohub/util/EvidenceWorkClass.kt"
APP = ROOT / "app/src/main/assets/ui/app.js"
PREDICTOR = ROOT / "app/src/main/assets/ui/core/predictor-model.js"


def between(source: str, start: str, end: str) -> str:
    a = source.index(start)
    b = source.index(end, a)
    return source[a:b]


class G2RuntimeArbiterGate(unittest.TestCase):
    def test_single_runtime_owners(self):
        runtime = RUNTIME.read_text("utf-8")
        app = APP.read_text("utf-8")
        self.assertEqual(1, runtime.count("ResponseDrivenEcuEngine("))
        self.assertEqual(1, runtime.count("Mp48BackpressureScheduler(engine)"))
        self.assertEqual(1, runtime.count("LatestOnlyState<CanonicalEvidence>"))
        # Dois consumidores latest-only são permitidos: projeção visual e shadow
        # Adaptive. Ambos recebem o mesmo CanonicalEvidence e nenhum adquire MP48.
        self.assertEqual(2, runtime.count("LatestOnlyBackgroundPipeline("))
        self.assertIn('consumerName = "UI_PROJECTION"', runtime)
        self.assertIn('consumerName = "ADAPTIVE_SHADOW"', runtime)
        self.assertEqual(1, runtime.count("RealtimeLearningBuffer("))
        self.assertEqual(1, app.count("const store = new ui.Store("))
        self.assertEqual(1, app.count("const router = new ui.Router(store)"))
        self.assertEqual(1, app.count("new ui.Scheduler("))

    def test_ecu_callback_is_typed_and_json_free(self):
        runtime = RUNTIME.read_text("utf-8")
        hot = between(runtime, "    private fun consumeTelemetry(", "    /**\n     * Projeção visual")
        self.assertIn("CanonicalEvidence.from(", hot)
        self.assertIn("latestCanonicalEvidence.publish(evidence)", hot)
        self.assertIn("telemetryDeliveryPipeline.submit", hot)
        self.assertIn("adaptiveShadowPipeline.submit", hot)
        self.assertIn("learningPipeline.submit", hot)
        for forbidden in ("JSONObject", ".toJson()", "metricsJson()", ".toString()"):
            self.assertNotIn(forbidden, hot)

    def test_legacy_projection_is_worker_side_and_generation_guarded(self):
        runtime = RUNTIME.read_text("utf-8")
        projection = between(runtime, "    private fun projectTelemetryCompatibility(", "    private fun publishLearningState")
        self.assertIn("val telemetry = evidence.rawTelemetry", projection)
        self.assertIn("telemetry.toJson()", projection)
        self.assertIn("metrics.toJson()", projection)
        self.assertIn("if (generation != currentUsbSessionId || evidence.usbSessionId != generation) return", projection)
        self.assertIn("if (generation != currentUsbSessionId) return@synchronized null", projection)
        hot = between(runtime, "    private fun consumeTelemetry(", "    /**\n     * Projeção visual")
        self.assertLess(hot.index("latestCanonicalEvidence.publish(evidence)"), hot.index("telemetryDeliveryPipeline.submit"))
        self.assertLess(hot.index("latestCanonicalEvidence.publish(evidence)"), hot.index("adaptiveShadowPipeline.submit"))
        self.assertLess(hot.index("latestCanonicalEvidence.publish(evidence)"), hot.index("learningPipeline.submit"))

    def test_visual_and_science_backlogs_are_hard_bounded(self):
        latest = LATEST.read_text("utf-8")
        science = SCIENCE.read_text("utf-8")
        work = WORK.read_text("utf-8")
        compact_work = "".join(work.split())
        self.assertIn("pending = task", latest)
        self.assertIn("coalesced.incrementAndGet()", latest)
        self.assertIn("MAX_HOT_EVIDENCE = 3", science)
        self.assertIn("importantCapacity.coerceIn(1, MAX_HOT_EVIDENCE)", science)
        self.assertIn("SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING", science)
        self.assertIn(
            "POST_WRITE_REVALIDATION(100,MarginalInformationClass.CAUSAL_POST_INTERVENTION)",
            compact_work,
        )
        self.assertIn(
            "DIAGNOSTIC_ONLY(10,MarginalInformationClass.DIAGNOSTIC_PRESENT_STATE,diagnosticOnly=true,)",
            compact_work,
        )

    def test_engine_scheduler_recovers_to_telemetry_after_units(self):
        engine = ENGINE.read_text("utf-8")
        self.assertIn("PriorityBlockingQueue<QueuedSerialWork>", engine)
        self.assertIn("plannedWorkSinceLastTelemetry = true", engine)
        queued_block = between(engine, "                val queued = queue.poll()", "        } catch (interrupted: InterruptedException)")
        self.assertIn("if (queued.telemetryAfter && sessionReady && usb.connected && !stopRequested.get())", queued_block)
        self.assertIn("continue\n                }\n\n                pollTelemetry()", queued_block)

    def test_navigation_and_predictor_are_not_scientific_clocks(self):
        app = APP.read_text("utf-8")
        predictor = PREDICTOR.read_text("utf-8")
        route = between(app, "  function activateRoute(route, context)", "  router.onNavigate")
        for forbidden in ("startMapRead", "startCurveRead", "connectUsb", "restartEngine"):
            self.assertNotIn(forbidden, route)
        for forbidden in ("setInterval", "requestAnimationFrame", "Scheduler", "OmegasNative"):
            self.assertNotIn(forbidden, predictor)

    def test_publish_path_has_constant_structural_work_before_queue_handoff(self):
        runtime = RUNTIME.read_text("utf-8")
        hot = between(runtime, "    private fun consumeTelemetry(", "    /**\n     * Projeção visual")
        # Um envelope tipado e três handoffs bounded; sem loops, disco, parse ou JSON.
        self.assertEqual(1, hot.count("CanonicalEvidence.from("))
        self.assertNotIn("for (", hot)
        self.assertNotIn("repeat(", hot)
        self.assertNotIn("File(", hot)
        self.assertNotIn("readText", hot)
        self.assertNotIn("writeText", hot)


if __name__ == "__main__":
    unittest.main()
