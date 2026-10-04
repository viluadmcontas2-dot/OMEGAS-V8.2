"""Platina fix-life: contrato de texto do ciclo de vida, segurança de escrita e robustez (classe 1).

O comportamento fino está nos testes Kotlin do CI (RateCapTest, MirrorRetentionTest, FailureKindTest,
AnalysisRobustnessTest, AutoCalRecoveryPolicyTest). Aqui trava-se a ESTRUTURA que impede a volta dos defeitos.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / "app/src/main/java/com/omegas/prohub"
UI = ROOT / "app/src/main/assets/ui"


def read(path):
    return path.read_text(encoding="utf-8")


KWRITE = read(K / "calibration/KWriteManager.kt")
KFACTOR = read(K / "calibration/KFactorManager.kt")
NATIVE = read(K / "autocal/AutoCalNativeActionManager.kt")
BRIDGE = read(K / "web/CalibrationOperationsBridge.kt")
SERVICE = read(K / "service/TelemetryForegroundService.kt")
MAIN = read(K / "MainActivity.kt")
RUNTIME = read(K / "ecu/NativeRuntimeManager.kt")
ENGINE = read(K / "ecu/ResponseDrivenEcuEngine.kt")
RECORDER = read(K / "diagnostics/SessionRecorder.kt")
MANIFEST = read(ROOT / "app/src/main/AndroidManifest.xml")


def body(text, start, end):
    a = text.index(start)
    return text[a:text.index(end, a)]


class MapKRelease(unittest.TestCase):
    """S1: o Mapa K travado se libera com UM toque do dono, pela fila normal, só após o ACK da ECU."""

    def test_bridge_exposes_one_touch_release_through_the_normal_queue(self):
        self.assertIn("fun startInsertionRecovery()", BRIDGE)
        self.assertIn('startOperation("INSERTION_RECOVERING")', BRIDGE)
        self.assertIn("service.recoverKInsertionState()", BRIDGE)

    def test_native_api_and_map_button(self):
        self.assertIn("startInsertionRecovery", read(UI / "core/native-api.js"))
        map_js = read(UI / "screens/map.js")
        self.assertIn("mapReleaseButton", map_js)
        self.assertIn("Liberar Mapa K", map_js)
        # "liberado" só depois de `recovered` (ACK da ECU)
        self.assertRegex(map_js, r"operation\.recovered === true")
        self.assertNotIn("setTimeout", map_js)

    def test_release_is_gated_like_any_write_and_unlocks_only_after_ack(self):
        service = body(SERVICE, "fun recoverKInsertionState", "fun kWriteStatusJson")
        for guard in ("usb.connected", "kFactor.isBusy()", "writerConflict(SerialWriteGuard.OWNER_K_MAP)", "canWriteLocally"):
            self.assertIn(guard, service)
        recover = body(KWRITE, "fun recoverInsertionState", "fun startWrite")
        self.assertLess(recover.index("requireAck("), recover.index("insertionStateUnknown.set(false)"))
        self.assertIn('"Mapa K liberado"', recover)

    def test_corrupt_lock_file_stays_locked(self):
        self.assertRegex(KWRITE, re.compile(r"loadInsertionSafetyLock\(file: File\): Boolean = try \{.*?catch \(_: Exception\) \{ true \}", re.S))


class AtomicFiles(unittest.TestCase):
    """S4: nada de apagar o destino antes do rename; fsync do temporário."""

    def test_kwrite_atomic_write_matches_kfactor(self):
        fn = body(KWRITE, "private fun atomicWrite", "\n}\n")
        self.assertIn("fd.sync()", fn)
        self.assertIn("ATOMIC_MOVE", fn)
        self.assertNotIn("file.delete()", fn)

    def test_json_files_fsync_and_stallwatch_alignment(self):
        json_files = read(K / "equivalence/JsonFiles.kt")
        self.assertIn("fd.sync()", body(json_files, "fun writeAtomic", "fun readJsonWithBak"))
        stall = read(K / "autocal/StallWatch.kt")
        self.assertIn("saveLock", stall)
        self.assertIn("JsonFiles.writeAtomic", stall)
        self.assertIn("JsonFiles.readJsonWithBak", stall)
        self.assertNotIn('File(target.parentFile, target.name + ".tmp")', stall)


class WriteLockNeverLeaks(unittest.TestCase):
    """S8: tudo entre adquirir busy/trava e entregar ao executor fica sob try/finally."""

    def test_kwrite_start_batch_write(self):
        fn = body(KWRITE, "fun startBatchWrite", "fun listMapBackups")
        self.assertIn("if (!submitted)", fn)
        self.assertIn("guard.release(SerialWriteGuard.OWNER_K_MAP)", fn)
        # nenhum `busy.set(false)` solto antes do finally
        before_finally = fn[: fn.index("finally")]
        self.assertNotIn("busy.set(false)", before_finally)

    def test_kwrite_execute_batch_releases_even_if_safety_exit_throws(self):
        fn = body(KWRITE, "        } finally {\n            try {\n                if (insertionEnabled)", "    private fun <T> runSynchronous")
        self.assertIn("} finally {\n                guard.release(SerialWriteGuard.OWNER_K_MAP)", fn)

    def test_kfactor_and_autocal_paths(self):
        for name, end in (("fun startResetToNeutral", "private fun releaseWriter"), ("fun startBatchWrite", "fun close()")):
            fn = body(KFACTOR, name, end)
            self.assertIn("if (!submitted) releaseWriter()", fn)
        execute = body(NATIVE, "    fun execute(preparationId: String)", "    fun clearPreparation")
        self.assertIn("if (!submitted)", execute)
        self.assertIn('update("QUEUED"', execute[execute.index("var submitted"):])


class RuntimeLifecycle(unittest.TestCase):
    def test_stuck_clears_when_engine_really_stops(self):
        self.assertIn("stuck = false", body(RUNTIME, 'if (state == "STOPPED")', "onStateChanged()"))
        self.assertIn("if (stuck && !engine.isRunning()) stuck = false", RUNTIME)

    def test_handshake_backoff_is_sliced_and_checks_stop(self):
        fn = body(ENGINE, "private fun performHandshakeOrResume", "hadOnlineSession || recoveringExistingSession")
        self.assertIn("stopRequested.get()", fn)
        self.assertIn("minOf(left, 50L)", fn)
        self.assertNotIn("SystemClock.sleep(wait)", fn)

    def test_usb_state_changed_is_guarded_and_marks_transition_after_success(self):
        fn = body(SERVICE, "private fun usbStateChanged", "private fun handleUsbTransition")
        self.assertIn("catch (error: Throwable)", fn)
        handle = body(SERVICE, "private fun handleUsbTransition", "private fun startEngine")
        self.assertLess(handle.index("guarded(\"USB\", \"Telemetria\")"), handle.index("lastUsbConnected = connected"))
        self.assertEqual(handle.count("lastUsbConnected = connected"), 1)

    def test_ticks_catch_throwable(self):
        self.assertIn("catch (error: Throwable)", body(SERVICE, "private fun healthTick", "private fun resumeSessionRecordingIfStopped"))
        self.assertIn("catch (error: Throwable)", body(SERVICE, "private fun autoCalTick", "@Volatile private var lastDriveRpm"))

    def test_foreground_first_and_teardown_off_the_main_thread(self):
        on_create = body(SERVICE, "override fun onCreate()", "paths = AppPaths(this)")
        self.assertIn("startForegroundBootstrap()", on_create)
        on_destroy = body(SERVICE, "override fun onDestroy()", "private fun teardownBlocking")
        self.assertLess(on_destroy.index("stopForeground("), on_destroy.index("teardownBlocking"))
        self.assertIn("teardown.join(4_000L)", on_destroy)

    def test_stop_button_really_stops_with_a_bound_activity(self):
        self.assertIn("ACTION_STOP_SERVICE -> scheduler.execute { stopFromUser() }", SERVICE)
        self.assertIn("setStopListener", MAIN)
        self.assertIn("finishAndRemoveTask()", MAIN)


class ActivityRecreation(unittest.TestCase):
    """S5/S6: a gravação não morre com a Activity; o renderizador não derruba o app."""

    def test_bridge_state_is_process_wide_and_destroy_does_not_shut_down(self):
        destroy = body(BRIDGE, "    fun destroy()", "@JavascriptInterface")
        self.assertNotIn("shutdownNow", destroy)
        self.assertIn("private val sharedExecutor", BRIDGE)
        self.assertIn("sharedLastOperation", BRIDGE)

    def test_manifest_config_changes(self):
        for token in ("screenLayout", "smallestScreenSize", "density", "fontScale", "layoutDirection"):
            self.assertIn(token, MANIFEST)

    def test_render_process_gone_returns_true_and_rebuilds_webview(self):
        fn = body(MAIN, "override fun onRenderProcessGone", "override fun shouldOverrideUrlLoading")
        self.assertIn("return true", fn)
        self.assertIn("rebuildWebView()", fn)
        self.assertIn("RenderProcessGoneDetail", MAIN)

    def test_crash_handler_installed_once_and_dialog_is_dismissible(self):
        self.assertIn("crashRecorderInstalled", MAIN)
        self.assertEqual(MAIN.count("Thread.setDefaultUncaughtExceptionHandler"), 1)
        self.assertNotIn("setCancelable(false)", MAIN)
        self.assertNotIn(".setMessage(lastCrash)", MAIN)


class SessionRecording(unittest.TestCase):
    def test_monitor_resumes_recording_with_backoff(self):
        fn = body(SERVICE, "private fun resumeSessionRecordingIfStopped", "/** Roda na faixa de análise")
        self.assertIn("sessionStoppedByOwner", fn)
        self.assertIn("sessionRestartNotBefore", fn)
        self.assertIn("resumeSessionRecordingIfStopped()", body(SERVICE, "private fun healthTick", "private fun resumeSessionRecordingIfStopped"))

    def test_mirror_recovery_uses_the_publisher_not_the_event_worker(self):
        fn = body(RECORDER, "fun recoverDocumentsMirrorAsync", "fun close()")
        self.assertIn("publisher.execute", fn)
        self.assertNotIn("worker.execute", fn)

    def test_app_log_and_console_have_a_rate_cap(self):
        self.assertIn("appLogCap.allow", SERVICE)
        self.assertIn("consoleChatterCap", MAIN)
        self.assertIn("consoleSeriousCap", MAIN)


class FailureClassification(unittest.TestCase):
    def test_transport_is_never_an_ecu_refusal(self):
        kind = read(K / "calibration/FailureKind.kt")
        self.assertIn("TimeoutException -> TRANSPORT", kind)
        policy = read(K / "autocal/AutoCalRecoveryPolicy.kt")
        self.assertLess(policy.index('reasonCode = "TRANSPORT_FAILURE"'), policy.index('reasonCode = "ECU_ACK_MISSING"'))
        self.assertIn("FailureKind.TRANSPORT", KWRITE)

    def test_autocal_actions_honour_the_link_gate(self):
        bridge = read(K / "autocal/AutoCalJavascriptBridge.kt")
        self.assertGreaterEqual(bridge.count("noLocalControlFailure()"), 2)

    def test_usb_permission_denied_is_exposed_read_only(self):
        self.assertIn("val usbPermissionDenied: Boolean", read(K / "model/HubStatus.kt"))
        self.assertIn("usbPermissionDenied", read(K / "web/HubJavascriptBridge.kt"))
        self.assertIn("usbPermissionDenied = usb.permissionDenied", SERVICE)


class CurveK(unittest.TestCase):
    def test_photos_rotate_and_floor_exception_needs_a_saved_photo(self):
        self.assertIn('if (preWrite) "PREWRITE" else "MANUAL"', KFACTOR)
        self.assertIn("pruneBackups()", body(KFACTOR, "private fun executeBatch", "private fun readRawPoints(request"))
        self.assertIn("photoHadRaw", KFACTOR)
        self.assertIn("A restauração não confere com a foto escolhida", KFACTOR)
        self.assertIn("O eixo Petrol Inj. atual difere da foto", KFACTOR)

    def test_read_curve_refuses_while_a_writer_holds_the_serial(self):
        fn = body(KFACTOR, "    fun readCurve()", "    fun saveCurrentBackup")
        self.assertIn("guard.holder()", fn)


if __name__ == "__main__":
    unittest.main()
