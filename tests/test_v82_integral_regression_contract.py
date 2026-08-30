import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text('utf-8')


class V82IntegralRegressionContract(unittest.TestCase):
    def test_large_startup_remains_deferred_and_bounded(self):
        deferred = read('app/src/main/java/com/omegas/prohub/learning/DeferredLiveOnlyLearningStore.kt')
        budget = read('app/src/main/java/com/omegas/prohub/learning/LearningEvidenceBudget.kt')
        store = read('app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt')
        self.assertIn('Thread(', deferred)
        self.assertIn('LEARNING_RESTORING', deferred)
        self.assertIn('MAX_PERSISTED_BYTES', budget)
        self.assertIn('MAX_NATIVE_ANCHORS', budget)
        self.assertIn('CoalescedSnapshotWriter', store)
        self.assertIn('EVIDENCE_STATE_SCHEMA', store)

    def test_slow_ui_cannot_create_visual_history_queue(self):
        pipeline = read('app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt')
        scheduler = read('app/src/main/assets/ui/core/scheduler.js')
        tracing = read('app/src/main/assets/ui/components/predictor-current-cell.js')
        self.assertIn('latest', pipeline.lower())
        self.assertIn('pending', pipeline.lower())
        self.assertIn('coalesced', pipeline.lower())
        self.assertIn('pending = task', pipeline)
        self.assertEqual(scheduler.count('setInterval'), 1)
        self.assertNotIn("scheduler.addHook('fast'", tracing)
        self.assertIn('this.store.subscribe', tracing)
        self.assertNotIn('setInterval', tracing)
        self.assertNotIn('history', tracing)
        self.assertNotIn('setTrace', tracing)

    def test_concurrent_serial_clients_share_one_scheduler(self):
        engine = read('app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt')
        contract = read('app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt')
        map_writer = read('app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt')
        curve_writer = read('app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt')
        monitor = read('app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt')
        self.assertIn('class ResponseDrivenEcuEngine', engine)
        self.assertIn('SAFETY', contract)
        self.assertIn('MANUAL_WRITE', contract)
        self.assertIn('READ_ONLY', contract)
        self.assertIn('Mp48SerialScheduler', map_writer)
        self.assertIn('Mp48SerialScheduler', curve_writer)
        self.assertIn('Mp48SerialScheduler', monitor)
        self.assertNotIn('UsbSerialManager', map_writer)
        self.assertNotIn('UsbSerialManager', curve_writer)
        self.assertNotIn('UsbSerialManager', monitor)

    def test_long_learning_has_explicit_hot_state_limits(self):
        memory = read('app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt')
        budget = read('app/src/main/java/com/omegas/prohub/learning/LearningEvidenceBudget.kt')
        window = read('app/src/main/java/com/omegas/prohub/learning/NativeAnchorTelemetryWindow.kt')
        anchors = read('app/src/main/java/com/omegas/prohub/learning/NativeLearningAnchor.kt')
        self.assertIn('MAX_COMPARISONS', memory)
        self.assertIn('MAX_SESSIONS', memory)
        self.assertIn('MAX_REGIONS', memory)
        self.assertIn('MAX_PERSISTED_BYTES = 256 * 1024', budget)
        self.assertIn('MAX_NATIVE_ANCHORS', budget)
        self.assertIn('maxFrames: Int = 256', window)
        self.assertIn('maxAgeMs', window)
        self.assertIn('while (anchors.size > maxEntries', anchors)

    def test_autocal_pause_resume_is_observational_and_readback_guarded(self):
        monitor = read('app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt')
        actions = read('app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt')
        cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js')
        self.assertIn('enabled == 0', monitor)
        self.assertIn('"PAUSED"', monitor)
        self.assertIn('workClass = Mp48WorkClass.READ_ONLY', monitor)
        self.assertIn('readbackValid', actions)
        self.assertIn('ENABLE_AUTO_CAL', actions)
        self.assertIn('DISABLE_AUTO_CAL', actions)
        self.assertNotIn('NATIVE_AUTOMATCH', actions)
        self.assertNotIn('NATIVE_AUTOMATCH', cockpit)
        self.assertIn('Abrir confirmação final', cockpit)
        self.assertIn('Nada foi enviado.', cockpit)

    def test_predictor_never_bootstraps_confidence_from_predictions(self):
        surface = read('app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt')
        spatial = read('app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt')
        interpolator = read('app/src/main/java/com/omegas/prohub/learning/PredictorInterpolator.kt')
        consumer = read('app/src/main/assets/ui/core/predictor-model.js')
        self.assertIn('VALIDADO', surface)
        self.assertIn('OBSERVADO', surface)
        self.assertIn('PREVISTO', surface)
        self.assertIn('DESCONHECIDO', surface)
        self.assertIn('EXTRAPOLATION_OUTSIDE_SUPPORT_HULL', spatial)
        self.assertIn('supportFrozenBeforePrediction', interpolator)
        self.assertIn('predictionsFeedConfidence", false', interpolator)
        self.assertNotIn('KWriteManager', interpolator)
        self.assertNotIn('OmegasNative', consumer)
        self.assertIn("router.navigate('map'", consumer)
        self.assertIn("intent: 'review-only'", consumer)

    def test_ui_authorities_remain_single_and_extensions_have_no_own_timers(self):
        router = read('app/src/main/assets/ui/core/router.js')
        store = read('app/src/main/assets/ui/core/store.js')
        scheduler = read('app/src/main/assets/ui/core/scheduler.js')
        split = read('app/src/main/assets/ui/components/split-layout.js')
        floating = read('app/src/main/assets/ui/components/floating-telemetry.js')
        predictor = read('app/src/main/assets/ui/screens/predictor.js')
        self.assertIn('class Router', router)
        self.assertIn('class Store', store)
        self.assertIn('class Scheduler', scheduler)
        self.assertEqual(scheduler.count('setInterval'), 1)
        for source in (split, floating, predictor):
            self.assertNotIn('setInterval', source)
            self.assertNotIn('new Store', source)
            self.assertNotIn('new Router', source)

    def test_live_interpolation_is_kotlin_owned_and_visual_only(self):
        projection = read('app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt')
        bridge = read('app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt')
        tracing = read('app/src/main/assets/ui/components/predictor-current-cell.js')
        self.assertIn('fun liveInterpolationJson(', projection)
        self.assertIn('ContinuousLearningMath.bilinearWeights', projection)
        self.assertIn('.put("continuousWeights"', projection)
        self.assertIn('.put("affectsLearning", false)', projection)
        self.assertIn('.put("affectsCalibration", false)', projection)
        self.assertIn('LearningGridProjection.liveInterpolationJson(', bridge)
        self.assertIn('cell.continuousWeights', tracing)
        self.assertNotIn('bilinear', tracing)
        self.assertIn('liveTracingEnabled', tracing)
        self.assertNotIn('writeMap', tracing)


if __name__ == '__main__':
    unittest.main()
