"""Fatia H-escrita: contrato de texto dos consertos de segurança de escrita, Desfazer honesto, ciclo de vida e consumo.

Classe de prova 1 (contrato). O comportamento está nos testes Kotlin (CI) e em tests/ui/h-writes.test.cjs.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / "app/src/main/java/com/omegas/prohub"


def read(rel):
    return (K / rel).read_text(encoding="utf-8")


KFACTOR = read("calibration/KFactorManager.kt")
KWRITE = read("calibration/KWriteManager.kt")
BRIDGE = read("web/CalibrationOperationsBridge.kt")
NATIVE = read("autocal/AutoCalNativeActionManager.kt")
SERVICE = read("service/TelemetryForegroundService.kt")
RECORDER = read("diagnostics/SessionRecorder.kt")
RINGLOG = read("util/RingLog.kt")
NOTIFICATION = read("service/NotificationController.kt")


def body(text, start, end):
    a = text.index(start)
    return text[a:text.index(end, a)]


class WriteSafety(unittest.TestCase):
    def test_batch_writer_saves_validated_photo_before_first_ack(self):
        execute = body(KFACTOR, "private fun executeBatch", "private fun readRawPoints(request")
        photo = execute.index("writeManualPhoto(cachedAxis, ecuBefore")
        first_ack = execute.index("KFactorProtocol.writeFactor(index, targetRaw)")
        self.assertLess(photo, first_ack, "a foto sai ANTES do primeiro ACK")
        # foto = disco da curva já lida (nenhum comando novo) e validada por hash
        helper = body(KFACTOR, "private fun writeManualPhoto", "fun listBackups")
        self.assertIn('"MANUAL-$createdAt-', helper)
        self.assertIn("loadBackup(fileName)", helper)
        self.assertIn("Hash do backup salvo divergiu", helper)
        self.assertNotIn("transaction(", helper)
        # o nome da foto volta em lastOperation (sucesso e falha)
        self.assertIn('.put("photoFile", photoFile)', execute)
        self.assertGreaterEqual(execute.count('.put("photoFile", photoFile)'), 2)
        self.assertIn('for (key in arrayOf("photoFile"', BRIDGE)

    def test_restore_floor_applies_to_new_targets_only(self):
        self.assertIn("allowBelowFloor", KFACTOR)
        self.assertIn("if (allowBelowFloor) 0 else KFactorProtocol.rawFromFactor(MIN_SAFE_FACTOR)", KFACTOR)
        prepare = body(KFACTOR, "fun prepareRestore", "private fun normalizePoints")
        self.assertNotIn("MIN_SAFE_FACTOR", prepare)
        self.assertIn("A restauração não confere com a foto escolhida", KFACTOR)
        self.assertIn("allowBelowFloor", KWRITE)
        self.assertIn("val floor = if (allowBelowFloor) 0 else MIN_SAFE_K", KWRITE)
        self.assertIn("fun startCurveRestoreWrite", BRIDGE)
        self.assertIn("fun startMapRestoreWrite", BRIDGE)

    def test_shared_serial_guard_is_honoured_by_every_mutating_path(self):
        self.assertIn("class SerialWriteGuard", read("calibration/SerialWriteGuard.kt"))
        for text, owner in ((KFACTOR, "OWNER_K_FACTOR"), (KWRITE, "OWNER_K_MAP"), (NATIVE, "OWNER_AUTOCAL")):
            self.assertIn("guard.tryAcquire(SerialWriteGuard." + owner + ")", text)
            self.assertIn("guard.release(SerialWriteGuard." + owner + ")", text)
        self.assertIn("SerialWriteGuard.shared.holder()", SERVICE)

    def test_autocal_close_never_shuts_down_mid_action_and_checks_safety_once(self):
        close = body(NATIVE, "    fun close()", "    private fun executePrepared")
        self.assertIn("if (busy.get()) executor.shutdown() else executor.shutdownNow()", close)
        ensure = body(NATIVE, "    private fun ensureSession", "    private fun update(")
        self.assertIn("safetyCheckedPreparationId != prepared.id", ensure)

    def test_autocal_reset_text_is_honest(self):
        self.assertIn('"A ECU respondeu (ACK) e o estado foi relido"', NATIVE)
        self.assertIn("prepared.action.expectedEnableReadback != null || prepared.action == Action.RESET_K_FACTOR", NATIVE)


class BusyNeverSticks(unittest.TestCase):
    def test_bridge_runs_every_operation_through_launch(self):
        launch = body(BRIDGE, "    private fun launch(", "    private fun refreshUi")
        self.assertIn("catch (error: Throwable)", launch)
        self.assertIn("finally {\n                busy.set(false)", launch)
        self.assertIn("RejectedExecutionException", launch)
        self.assertEqual(BRIDGE.count("executor.execute"), 1, "um só ponto de execução na fila")
        self.assertGreaterEqual(BRIDGE.count("launch("), 4)

    def test_writer_terminal_status_comes_before_the_stale_cache_write(self):
        execute = body(KFACTOR, "private fun executeBatch", "private fun readRawPoints(request")
        catch = execute[execute.index("catch (error: Throwable)"):]
        self.assertLess(catch.index('"BATCH_FAILED"'), catch.index("atomicWrite(cacheFile, stale"))
        self.assertIn("try {\n                val stale", catch)

    def test_reset_hands_over_to_the_writer_without_releasing_busy(self):
        reset = body(KFACTOR, "        val resetId = ", "    /** Solta `busy`")
        self.assertNotIn("busy.set(false)", reset)
        self.assertIn("executeBatch(resetId, normalized, reason, expectedSessionId)", reset)
        self.assertNotIn("startBatchWrite(points, reason)", reset)


class TransportIsNotEcu(unittest.TestCase):
    def test_failure_kind_is_classified_where_reply_error_is_read(self):
        kind = read("calibration/FailureKind.kt")
        for word in ('"TRANSPORTE"', '"ECU"', '"APP"'):
            self.assertIn(word, kind)
        self.assertIn("FailureKind.ofReply(reply)", KFACTOR)
        self.assertIn("FailureKind.ofReply(reply)", KWRITE)
        self.assertIn('.put("failureKind"', KFACTOR)
        self.assertIn('.put("failureKind"', KWRITE)
        self.assertIn('.put("failureKind"', BRIDGE)


class MapPartialFailure(unittest.TestCase):
    def test_partial_count_uses_confirmed_events(self):
        self.assertIn('details.optJSONArray("confirmedEvents")', BRIDGE)
        self.assertIn(".put(\"ecuPartiallyChanged\", ecuPartiallyChanged)", BRIDGE)
        self.assertIn('.put("mutationMayHaveStarted", writeStarted)', KWRITE)
        self.assertIn("fun prepareRestore(adjustmentId: String)", KWRITE)
        self.assertIn("fun listMapBackups()", KWRITE)


class Lifecycle(unittest.TestCase):
    def test_session_recorder_close_drains_before_taking_the_monitor(self):
        close = body(RECORDER, "    fun close()", "    private fun createActiveExportSnapshot")
        self.assertNotIn("@Synchronized", RECORDER[RECORDER.index("    fun close()") - 40:RECORDER.index("    fun close()")])
        self.assertLess(close.index("awaitPendingWrites"), close.index('stop("serviço encerrado")'))

    def test_session_id_has_milliseconds_and_sequence(self):
        self.assertIn("yyyy-MM-dd_HH-mm-ss-SSS", RECORDER)
        self.assertIn("sessionCounter.incrementAndGet()", RECORDER)
        self.assertIn("SessionIdFormat.build(", RECORDER)

    def test_total_bytes_cap_prunes_closed_sessions_without_a_mirror_marker(self):
        prune = body(RECORDER, "    private fun pruneOldSessions", "    private fun awaitPendingWrites")
        self.assertIn("SessionByteCap.select(remaining, SessionByteCap.TOTAL_BYTES_CAP)", prune)
        self.assertIn("1_536L * 1024L * 1024L", read("diagnostics/SessionByteCap.kt"))

    def test_size_cap_rolls_over_instead_of_staying_stopped(self):
        self.assertIn("rollOverAfterCap(", RECORDER)
        helper = body(RECORDER, "    private fun rollOverAfterCap", "    private fun openNextSegment")
        self.assertIn("start(", helper)
        self.assertIn("rollingOver", helper)

    def test_full_snapshot_does_not_embed_the_recorder_status(self):
        self.assertNotIn('"session_recorder"', SERVICE)
        self.assertNotIn('statusObject().optBoolean("recording")', SERVICE)

    def test_notification_and_overlay_do_only_necessary_work(self):
        update = body(SERVICE, "    private fun updateNotification()", "    private fun updateWakeLock")
        self.assertIn("if (content == lastNotificationContent) return", update)
        self.assertEqual(NOTIFICATION.count("PendingIntent.getService("), 3)
        self.assertEqual(NOTIFICATION.count("by lazy"), 4, "quatro PendingIntents criados uma vez")
        overlay = body(SERVICE, "    private fun updateOverlay()", "    private fun startForegroundCompat")
        self.assertLess(overlay.index("overlay.wantsUpdate()"), overlay.index("status()"))
        self.assertIn("visible() && SystemClock.elapsedRealtime() - lastDrawAt >= 250L", read("service/TelemetryOverlayController.kt"))

    def test_ring_log_writes_the_file_outside_the_add_lock(self):
        add = body(RINGLOG, "    fun add(", "    private fun scheduleDrain")
        self.assertNotIn("appendText", add)
        self.assertNotIn("copyTo", add)
        self.assertIn("fileWriter.execute", RINGLOG)
        self.assertIn("renameTo(old)", RINGLOG)


class Wiring(unittest.TestCase):
    def test_session_summary_emits_blackouts_the_screen_reads(self):
        self.assertIn('.put("blackouts", blackouts)', read("diagnostics/SessionSemanticLedger.kt"))
        sessions = (ROOT / "app/src/main/assets/ui/screens/sessions.js").read_text(encoding="utf-8")
        self.assertIn("summary.blackouts", sessions)

    def test_autocal_bridge_is_registered_before_load_url(self):
        main = read("MainActivity.kt")
        self.assertLess(main.index("AutoCalBridgeProvider.attachBeforeLoad(this)"), main.index('webView.loadUrl("file:///android_asset/ui/index.html")'))

    def test_present_snapshot_cheap_path_and_interpolation_cache(self):
        hub = read("web/HubJavascriptBridge.kt")
        self.assertIn("fun getPresentSnapshotIfChanged(lastSequence: Long): String", hub)
        self.assertIn('\\"changed\\":false', hub)
        self.assertIn("interpolationCache", hub)
        self.assertIn("fun sequenceNow(): Long", read("telemetry/TelemetryStateStore.kt"))

    def test_ecu_command_bytes_are_untouched(self):
        # Nenhum construtor de comando foi tocado: só conferência, trava e foto em disco.
        for name in ("KFactorProtocol.writeFactor(index, targetRaw)", "KFactorProtocol.readFactors()", "KFactorProtocol.readPetrolAxis()"):
            self.assertIn(name, KFACTOR)
        self.assertIn("Mp48Protocol.writeKCell(row, column, target)", KWRITE)
        self.assertIn("Mp48Protocol.kInsertionMode(true)", KWRITE)
        self.assertIsNone(re.search(r"byteArrayOf\(0x", KFACTOR))


if __name__ == "__main__":
    unittest.main()
