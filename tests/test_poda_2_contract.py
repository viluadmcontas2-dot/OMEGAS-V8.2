#!/usr/bin/env python3
"""Fatia 3 — Poda 2: cérebro 2, sync LAN de aprendizado e órfãos saem; o núcleo da ECU fica."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src"
MAIN = APP / "main/java/com/omegas/prohub"
UNIT = APP / "test/java/com/omegas/prohub"
UI = APP / "main/assets/ui"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def kotlin_users(symbol: str) -> list:
    pattern = re.compile(rf"\b{re.escape(symbol)}\b")
    return sorted(str(p.relative_to(ROOT)) for p in APP.rglob("*.kt") if pattern.search(read(p)))


JS_INTERFACE = re.compile(r"@JavascriptInterface\s+(?:@\w+(?:\([^)]*\))?\s+)*fun\s+(\w+)\s*\(")
JS_INVOKE = re.compile(r"invoke\((?:this\.\w+,\s*)?'(\w+)'")


def ui_corpus() -> str:
    files = [p for p in UI.rglob("*.js") if not p.name.startswith("portmon-")]
    return "\n".join(read(p) for p in files) + read(UI / "index.html")


def bridge_methods() -> dict:
    return {p: JS_INTERFACE.findall(read(p)) for p in MAIN.rglob("*.kt") if "@JavascriptInterface" in read(p)}


def bridge_orphans() -> list:
    corpus = ui_corpus()
    return [f"{p.name}:{m}" for p, ms in bridge_methods().items() for m in ms
            if not re.search(rf"\b{m}\b", corpus)]


def js_calls_without_kotlin() -> list:
    kotlin = {m for ms in bridge_methods().values() for m in ms}
    calls = {c for p in UI.rglob("*.js") if not p.name.startswith("portmon-") for c in JS_INVOKE.findall(read(p))}
    return sorted(calls - kotlin)


class Poda2Contract(unittest.TestCase):
    def test_3_1_orfaos_kotlin_sem_arquivo_nem_consumidor(self):
        for name in ("AebProtocolFramesV7", "ArchitectureContracts", "AutoMatchDraftReviewValidator",
                     "AutoMatchKFactorDraft", "AutoMatchResidualPlanner", "CurveKComparison",
                     "NativeAutoCalEvidenceSummary", "OmegasMigrationService", "OrderedBackgroundPipeline",
                     "StartupLifecyclePolicy", "TelemetryVisualLifecyclePolicy", "WeightedStat",
                     "CalibrationCausalTransitionV7", "CausalTransitionStatusV7", "CalibrationTransitionV7"):
            with self.subTest(name=name):
                self.assertEqual([], kotlin_users(name))
        self.assertFalse((MAIN / "migration").exists())
        self.assertFalse((MAIN / "stats").exists())

    def test_3_2_ferramentas_sem_aprendizado(self):
        html = read(UI / "index.html")
        drawers = read(UI / "components/drawers.js")
        for gone in ("toolExportLearning", "toolImportLearning", "Exportar aprendizado", "Importar aprendizado",
                     "learning-portability-card", "arquivo .omegas", "regiões gasolina", "petrolCount"):
            self.assertNotIn(gone, html + drawers, gone)
        self.assertIn('id="toolExportData"', html)
        self.assertIn("Tudo do app: calibrações salvas e sessões", html)
        self.assertIn("data-tool-export-logs", drawers)
        self.assertNotIn("getElementById('toolExportLogs')", drawers)

    def test_3_3_js_sem_bomba_de_ciencia(self):
        api = read(UI / "core/native-api.js")
        app = read(UI / "app.js")
        for gone in ("getScienceSnapshotSince", "scienceSnapshotSince", "scienceRevision", "getLearningMaps",
                     "getLearningSyncStatus", "getLearningToleranceSettings", "setLearningToleranceSettings",
                     "resetLearningToleranceSettings", "exportLearningArchive", "importLearningArchive",
                     "demoLearning", "demoToleranceSettings"):
            self.assertNotIn(gone, api + app, gone)
        self.assertIn("presentSnapshot(", api)
        self.assertIn("api.presentSnapshot(", app)

    REMOVED_BRIDGE = (
        "getScienceSnapshotSince", "getLearningCheckpointStatus", "getLearningMaps", "getLearningTemperatureSettings",
        "setLearningMinimumWaterC", "getLearningToleranceSettings", "setLearningToleranceSettings",
        "resetLearningToleranceSettings", "getLearningSyncStatus", "importLearningArchive", "exportLearningArchive",
        "restartEngine", "listUsbDevices", "getEngineMetrics", "runProtocolLab", "readKCell", "readKLine", "readKMap",
        "previewKMapCell", "startKWrite", "startKBatchWrite", "getKWriteStatus", "getKWriteHistory",
        "recoverKInsertionState", "readKFactorCurve", "startKFactorWrite", "getKFactorStatus", "getKFactorHistory",
        "requestBluetoothPermission", "getLinkStatus", "configureOmegasLink", "claimLinkMain", "releaseLinkMain",
        "syncLinkNow", "setGpsEnabled", "setLanEnabled", "openAppSettings", "registerRefuel", "setGnvSettings")

    def test_3_4_ponte_sem_aprendizado_e_sem_metodo_orfao(self):
        hub = read(MAIN / "web/HubJavascriptBridge.kt")
        self.assertEqual(39, len(self.REMOVED_BRIDGE))
        for name in self.REMOVED_BRIDGE:
            self.assertNotRegex(hub, rf"\bfun {name}\(", name)
        for gone in ("Learning", "science", "Science"):
            self.assertNotIn(gone, hub.split("fun getReleaseIdentity", 1)[1], gone)
        self.assertNotIn("science", read(MAIN / "runtime/RuntimeSnapshotBus.kt").lower())
        activity = read(MAIN / "MainActivity.kt")
        for gone in ("importLearningLauncher", "exportLearningLauncher", "vnd.omegas.learning", "fun importLearningArchive"):
            self.assertNotIn(gone, activity, gone)
        self.assertEqual([], bridge_orphans())
        self.assertEqual([], js_calls_without_kotlin())

    def test_3_5_link_v2_sem_aprendizado(self):
        link = read(MAIN / "link/OmegasLinkManager.kt")
        proto = read(MAIN / "link/LinkProtocol.kt")
        self.assertNotIn("earning", link)
        self.assertNotIn("OMEGAS_LINK_V1", link + proto)
        self.assertIn('const val CURRENT = "OMEGAS_LINK_V2"', proto)
        self.assertGreaterEqual(link.count("LinkProtocol.CURRENT"), 3)
        self.assertIn("LinkProtocol.isOldPeer(", link)
        self.assertIn("LinkProtocol.oldPeerResponse()", link)
        self.assertIn("LinkProtocol.SYNC_SCHEMA", link)
        service = read(MAIN / "service/TelemetryForegroundService.kt")
        for gone in ("exportLearning =", "mergeLearning =", "nativeEcuEvidence"):
            self.assertNotIn(gone, service, gone)

    def test_3_6_runtime_e_servico_sem_aprendizado(self):
        runtime = read(MAIN / "ecu/NativeRuntimeManager.kt")
        consume = runtime.split("    private fun consumeTelemetry(", 1)[1].split("\n    private fun consumeState", 1)[0]
        for gone in ("LiveOnlyLearningStore", "RealtimeLearningBuffer", "learningPipeline", "learningSessionLock",
                     "publishLearningState", "exportLearning", "mergeLearning", "importNativeAutoCalSnapshot",
                     "notifyCalibrationAdjustment", "previewKWrite", '"learning_state"', '.put("learning",',
                     '"surface_cell"', '"current_cell_confidence"', '"learning_pipeline"'):
            self.assertNotIn(gone, runtime, gone)
        for kept in ('.put("sample_state", decision.state)', '.put("sample_reason", decision.reason)',
                     '.put("sample_frame_count", decision.frameCount)', '.put("sample_minimum_frames", decision.minimumFrames)',
                     '.put("sample_desired_frames", decision.desiredFrames)', '.put("sample_duration_ms", decision.durationMs)',
                     '.put("sample", decision.toTelemetryJson())', '.put("learning_quality", decision.sample?.quality ?: 0.0)',
                     '.put("stable_ms", decision.durationMs)', "telemetryDeliveryPipeline.submit(sequence)"):
            self.assertIn(kept, consume, kept)
        self.assertIn("fun beginUsbSession(sessionId: Long) {", runtime)
        self.assertIn("fun endUsbSession(reason: String) {", runtime)
        service = read(MAIN / "service/TelemetryForegroundService.kt")
        for gone in ("LearningArchiveManager", "learningArchive", "saveInternalCheckpoint", "notifyCalibrationAdjustment",
                     "importNativeAutoCalSnapshot", "learningSyncStatusJson", "learningCheckpointStatusJson",
                     "importLearningArchive", "exportLearningArchive", "previewKMapCell", '"learningResult"',
                     "runtime.learningStatus()"):
            self.assertNotIn(gone, service, gone)
        self.assertNotIn("importNativeAutoCalSnapshot", read(MAIN / "autocal/AutoCalJavascriptBridge.kt"))
        for gone in (MAIN / "learning/LearningArchiveManager.kt", MAIN / "util/RealtimeLearningBuffer.kt",
                     UNIT / "util/RealtimeLearningBufferTest.kt",
                     ROOT / "tests/test_multimedia_telemetry_backpressure_contract.py",
                     ROOT / "tests/test_checkpoint_hot_path_contract.py",
                     ROOT / "tests/test_startup_learning_restore_contract.py"):
            self.assertFalse(gone.exists(), gone)

    ECU_CORE = ("LearningToleranceSettings", "LearningControlModel", "AdaptiveSampleWindow",
                "LearningTemperatureSettings", "NativeAnchorTelemetryWindow", "NativeAutoCalAnchorCorrelator")
    CORE_TESTS = ("LearningControlModelTest", "AdaptiveSampleWindowTest", "NativeAnchorTelemetryWindowTest",
                  "NativeAutoCalAnchorCorrelatorTest", "LearningToleranceMigrationTest", "LearningTolerancePolicyTest")
    BRAIN_2 = ("MotorLearningMemory", "SignalLearningStore", "LiveOnlyLearningStore", "DeferredLiveOnlyLearningStore",
               "AdaptivePetrolReference", "PetrolReferenceSelector", "AssistedCalibrationAdvisor", "LearningGridProjection",
               "LearningSnapshotReconciler", "LearningUiSnapshotAssembler", "LearningArchiveManager", "AdvisorRevisionGate",
               "CoalescedSnapshotWriter", "ContinuousWindowNovelty", "LearningEvidenceBudget", "LearningMemoryBudget",
               "SciencePublicationGate", "InternalLearningNamespace", "LearningEvidenceDimensions", "VisitConfidence",
               "LearningTelemetrySchemaMigration", "NativeLearningAnchor", "NativeLearningAnchorRegistry",
               "NativeEcuEvidence", "EvidenceProvenance", "VisitComparisonAccumulator", "LearningPerformanceMetrics")

    def test_3_7_cerebro_2_apagado(self):
        for name in self.BRAIN_2:
            with self.subTest(name=name):
                self.assertEqual([], kotlin_users(name))
        remaining = sorted(p.stem for p in (MAIN / "learning").glob("*.kt")) if (MAIN / "learning").exists() else []
        if remaining:  # antes da 3.8; depois dela o diretório não existe
            self.assertEqual(sorted(self.ECU_CORE + ("ContinuousLearningMath",)), remaining)
        for name in ("E2ELearningFlowTest.kt", "LearningLatencyContractTest.kt"):
            self.assertFalse((UNIT / name).exists(), name)
        for name in ("test_red_learning_confidence_contract.py", "test_red_science_publication_contract.py",
                     "test_learning_evidence_budget_contract.py", "test_learning_memory_budget_contract.py",
                     "test_advisor_revision_budget_contract.py", "test_verde_scientific_runtime_contract.py",
                     "test_learning_consolidation_contract.py", "test_v82_integral_regression_contract.py",
                     "test_curve_kotlin_math_authority_contract.py", "test_predictor_map_residual_contract.py"):
            self.assertFalse((ROOT / "tests" / name).exists(), name)
        for name in ("test_native_autocal_contract.py", "test_native_anchor_science_contract.py"):
            text = read(ROOT / "tests" / name)
            for gone in ("SignalLearningStore", "LiveOnlyLearningStore", "NativeLearningAnchor.kt"):
                self.assertNotIn(gone, text, f"{name}: {gone}")

    def test_3_8_nucleo_mora_em_ecu_e_learning_nao_existe(self):
        for name in self.ECU_CORE:
            with self.subTest(name=name):
                self.assertTrue(read(MAIN / "ecu" / f"{name}.kt").startswith("package com.omegas.prohub.ecu\n"))
        for name in self.CORE_TESTS:
            self.assertTrue(read(UNIT / "ecu" / f"{name}.kt").startswith("package com.omegas.prohub.ecu\n"), name)
        self.assertTrue(read(MAIN / "calibration/ContinuousLearningMath.kt").startswith("package com.omegas.prohub.calibration\n"))
        self.assertTrue((UNIT / "calibration/ContinuousLearningMathTest.kt").is_file())
        self.assertFalse((MAIN / "learning").exists())
        self.assertFalse((UNIT / "learning").exists())
        self.assertIn('getSharedPreferences("omegas_learning_v5"', read(MAIN / "ecu/LearningToleranceSettings.kt"))
        self.assertIn('getSharedPreferences("omegas_learning_native"', read(MAIN / "ecu/LearningTemperatureSettings.kt"))
        for p in APP.rglob("*.kt"):
            self.assertNotIn("com.omegas.prohub.learning", read(p), p)
        me = Path(__file__).name
        for p in list((ROOT / "tests").glob("*.py")) + list((ROOT / "tests/ui").glob("*.cjs")):
            if p.name != me:
                self.assertNotIn("prohub/learning/", read(p), p)
                self.assertNotIn("com.omegas.prohub.learning", read(p), p)

    def test_3_9_leia_me_da_sessao_sem_cerebro_2(self):
        recorder = read(MAIN / "diagnostics/SessionRecorder.kt")
        self.assertNotIn("learning.session_summary", recorder)
        self.assertIn("A validade de cada amostra está em live.sample.state e live.sample.reason", recorder)

    def test_3_10_ui_sem_arquivo_orfao_e_portmon_em_tools(self):
        for rel in ("components/physical-grid.js", "components/floating-telemetry.js", "core/learning-model.js",
                    "suggestion-model.js", "styles-expansion.css", "styles-expansion-panels.css",
                    "styles-floating-telemetry.css"):
            self.assertFalse((UI / rel).exists(), rel)
        for name in ("portmon-browser-simulator.js", "portmon-frame-decoder.js", "portmon-replay-adapter.js"):
            self.assertFalse((UI / name).exists(), name)
            self.assertTrue((ROOT / "tools/portmon" / name).is_file(), name)
        for test in (ROOT / "tests/ui").glob("portmon-*.test.cjs"):
            self.assertNotIn("assets/ui/portmon-", read(test), test.name)
        html = read(UI / "index.html")
        loaded = set(re.findall(r'(?:src|href)="([^"]+\.(?:js|css))"', html))
        for p in UI.rglob("*.js"):
            text = read(p)
            loaded |= set(re.findall(r"loadOptionalScript\('([^']+)'", text))
            loaded |= set(re.findall(r"\.href\s*=\s*['\"]([^'\"]+\.css)['\"]", text))
        files = {str(p.relative_to(UI)) for p in UI.rglob("*") if p.suffix in (".js", ".css")}
        self.assertEqual(set(), files - loaded)
        self.assertNotIn("renderSuggestions(", read(UI / "components/drawers.js"))
        self.assertIn("OmegasMapEditor", read(UI / "screens/map.js"))

    def test_3_11_limpeza_unica_no_servico(self):
        service = read(MAIN / "service/TelemetryForegroundService.kt")
        self.assertRegex(service, r"scheduler\.execute \{\s*legacySweepRemoved = "
                                  r"LegacyDataSweeper\.sweep\(paths\.runtimeRoot, paths\.runtimeBackupsRoot\)")
        self.assertGreaterEqual(service.count('.put("legacySweep", JSONArray(legacySweepRemoved))'), 2)
        sweeper = read(MAIN / "diagnostics/LegacyDataSweeper.kt")
        for never in ("refinement_autopilot", "equivalence_ledger", "getSharedPreferences"):
            self.assertNotIn(never, sweeper, never)
        self.assertIn("LegacyDataUpgradeTest", read(ROOT / ".github/workflows/verde-android-render-evidence.yml"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
