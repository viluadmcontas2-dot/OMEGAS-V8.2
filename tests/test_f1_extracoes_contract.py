import pathlib, re, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
K = ROOT / "app/src/main/java/com/omegas/prohub"
def read(p): return pathlib.Path(p).read_text(encoding="utf-8")


class T11LiveCellProjection(unittest.TestCase):
    def test_live_cell_lives_in_calibration(self):
        src = read(K / "calibration/LiveCellProjection.kt")
        for must in ("object LiveCellProjection", "fun liveInterpolationJson(", "fun cellFor(",
                     "KMapPhysicalAxes.rpmBins()", "KMapPhysicalAxes.petrolBins()",
                     "ContinuousLearningMath.bilinearWeights", '.put("affectsCalibration", false)'):
            self.assertIn(must, src)
        hub = read(K / "web/HubJavascriptBridge.kt")
        self.assertEqual(hub.count("LiveCellProjection.liveInterpolationJson("), 2)
        self.assertNotIn("LearningGridProjection.liveInterpolationJson(", hub)
        # F3: ContinuousLearningMath mora em calibration/ e a projeção antiga do aprendizado saiu.
        clm = read(K / "calibration/ContinuousLearningMath.kt")
        self.assertNotIn("LearningGridProjection", clm)
        self.assertIn("KMapPhysicalAxes", clm)
        self.assertFalse((K / "learning/LearningGridProjection.kt").exists())


class T12SampleAnalyzerInEcu(unittest.TestCase):
    def test_analyzer_moved_to_ecu(self):
        self.assertFalse((K / "learning/MotorSampleAnalyzer.kt").exists())
        src = read(K / "ecu/MotorSampleAnalyzer.kt")
        self.assertTrue(src.startswith("package com.omegas.prohub.ecu"))
        for must in ("class MotorSampleAnalyzer(", "data class SampleDecision(", "LiveCellProjection.cellFor("):
            self.assertIn(must, src)
        self.assertNotIn("LearningGridProjection", src)
        # O pacote de aprendizado inteiro saiu na F3 (tests/test_poda_2_contract.py).
        self.assertFalse((K / "learning").exists())
        runtime = read(K / "ecu/NativeRuntimeManager.kt")
        self.assertIn('.put("sample_state", decision.state)', runtime)
        self.assertIn('.put("sample", decision.toTelemetryJson())', runtime)
        self.assertIn('"com/omegas/prohub/ecu/SampleStubs.kt"', read(ROOT / "tests/test_mp48_serial_scheduler_behavior.py"))


class T13EquivalencePhases(unittest.TestCase):
    def test_renamed_with_same_disk_format(self):
        self.assertFalse((K / "autocal/RefinementAutopilot.kt").exists())
        src = read(K / "autocal/EquivalencePhases.kt")
        self.assertIn("class EquivalencePhases(", src)
        self.assertIn('const val FORMAT = "omegas-refinement-autopilot-v1"', src)
        svc = read(K / "service/TelemetryForegroundService.kt")
        # O construtor real também recebe durationClock (relógio monotônico); o arquivo em disco é o mesmo.
        self.assertIn('EquivalencePhases(File(paths.runtimeRoot, "refinement_autopilot.json")', svc)
        self.assertIn("lateinit var equivalencePhases: EquivalencePhases", svc)
        self.assertIn('.put("autopilot", service.equivalencePhases.json())', read(K / "autocal/AutoCalJavascriptBridge.kt"))
        self.assertIn('.put("autopilot", autopilot)', read(K / "autocal/EquivalenceView.kt"))
        for path in (ROOT / "app/src").rglob("*.kt"):
            self.assertNotIn("RefinementAutopilot", read(path), path)
            self.assertNotIn("refinementAutopilot", read(path), path)


NINE = {
    "startCurveRead": "fun startCurveRead(): String",
    "startCurveBackup": "fun startCurveBackup(label: String): String",
    "listCurveBackups": "fun listCurveBackups(): String",
    "startCurveRestorePrepare": "fun startCurveRestorePrepare(fileName: String): String",
    "startCurveReset": "fun startCurveReset(): String",
    "startCurveBatchWrite": "fun startCurveBatchWrite(pointsJson: String, reason: String): String",
    "startMapBatchWrite": "fun startMapBatchWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String): String",
    "previewMapAdjustment": "fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String",
    "getLastOperation": "fun getLastOperation(): String",
}


class T14CalibrationBridge(unittest.TestCase):
    def test_bridge_owns_the_nine_endpoints_without_v7(self):
        src = read(K / "web/CalibrationOperationsBridge.kt")
        self.assertIn('const val JS_NAME = "OmegasCalibration"', src)
        for name, sig in NINE.items():
            self.assertRegex(src, r"@JavascriptInterface\s+" + re.escape(sig), name)
        for gone in ("v7Reconcile", "suggestionReconciliation", "com.omegas.prohub.service.v7"):
            self.assertNotIn(gone, src)
        self.assertIn("internal fun startOperation(", src)
        self.assertEqual(src.count("Executors.newSingleThreadExecutor"), 1)
        self.assertEqual(src.count("AtomicBoolean(false)"), 1)
        for kept in ('.put("state", "BATCH_CONFIRMED")', '.put("readbackValid", true)', '.put("humanConfirmed", true)',
                     '"Outra operação V8 está em andamento"', "MapBatchPlan.build(cells)"):
            self.assertIn(kept, src)
        main = read(K / "MainActivity.kt")
        self.assertIn("addJavascriptInterface(calibrationBridge!!, CalibrationOperationsBridge.JS_NAME)", main)
        self.assertIn("removeJavascriptInterface(CalibrationOperationsBridge.JS_NAME)", main)
        self.assertIn("calibrationBridge?.destroy()", main)
        self.assertIn("calibrationBridge = CalibrationOperationsBridge(this)", main)

    def test_no_ui_reads_suggestion_reconciliation(self):
        for path in (ROOT / "app/src/main/assets/ui").rglob("*.js"):
            self.assertNotIn("suggestionReconciliation", read(path), path)


class T15OnlyCalibrationWrites(unittest.TestCase):
    def test_only_calibration_bridge_writes_and_ui_calls_it(self):
        # A ponte de compatibilidade antiga foi apagada na F2 (tests/test_poda_1_contract.py).
        api = read(ROOT / "app/src/main/assets/ui/core/native-api.js")
        self.assertIn("root.OmegasCalibration", api)
        self.assertNotIn("this.v7", api)
        for name in NINE:
            self.assertIn("invoke(this.calibration, '%s'" % name, api)
        self.assertFalse((ROOT / "tests/test_suggestion_readback_lifecycle_contract.py").exists())
        android = read(ROOT / "app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt")
        self.assertIn('"calibrationBridge"', android)


if __name__ == "__main__":
    unittest.main()
