import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MIRROR_PATH = ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/DocumentsSessionMirror.kt"
RECORDER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"


class AutoCalDocumentsPersistenceContractTest(unittest.TestCase):
    def test_documents_mirror_exists_and_uses_exact_public_root(self):
        self.assertTrue(MIRROR_PATH.is_file(), "DocumentsSessionMirror.kt ainda não existe")
        mirror = MIRROR_PATH.read_text(encoding="utf-8")
        self.assertIn('PUBLIC_ROOT = "Download/Omegas"', mirror)
        self.assertIn("MediaStore.Files.getContentUri", mirror)
        self.assertIn("MediaStore.MediaColumns.RELATIVE_PATH", mirror)
        self.assertIn("Environment.getExternalStoragePublicDirectory", mirror)

    def test_android_26_28_has_legacy_write_permission_only_until_28(self):
        root = ET.parse(MANIFEST_PATH).getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        matches = [
            node for node in root.findall("uses-permission")
            if node.attrib.get(android + "name") == "android.permission.WRITE_EXTERNAL_STORAGE"
        ]
        self.assertEqual(1, len(matches))
        self.assertEqual("28", matches[0].attrib.get(android + "maxSdkVersion"))

    def test_service_injects_documents_mirror_into_the_only_session_recorder(self):
        self.assertIn("DocumentsSessionMirror(this)", SERVICE)
        self.assertIn("SessionRecorder(paths, settings, documentsMirror)", SERVICE)
        self.assertEqual(1, SERVICE.count("SessionRecorder("))

    def test_recorder_mirrors_active_autocal_without_creating_second_capture_loop(self):
        self.assertIn("documentsMirror", RECORDER)
        self.assertIn("syncDocumentsMirror", RECORDER)
        for event in (
            "autocal_native_snapshot",
            "autocal_manual_snapshot",
            "autocal_native_action",
            "autocal_native_calibration_epoch",
        ):
            self.assertIn(event, RECORDER)
        self.assertIn("DOCUMENTS_MIRROR_INTERVAL_MS", RECORDER)
        mirror = MIRROR_PATH.read_text(encoding="utf-8")
        self.assertNotIn("ScheduledExecutor", mirror)
        self.assertNotIn("ThreadPoolExecutor", mirror)

    def test_stopped_session_is_forced_to_documents_and_public_archive_is_not_pruned(self):
        stop = RECORDER[RECORDER.index("fun stop("):RECORDER.index("fun record(", RECORDER.index("fun stop("))]
        self.assertIn("syncDocumentsMirror(force = true)", stop)
        prune = RECORDER[RECORDER.index("private fun pruneOldSessions"):RECORDER.index("private fun awaitPendingWrites")]
        self.assertIn("paths.sessionLogsRoot", prune)
        self.assertIn("documentsMirrorMarker", prune)
        self.assertNotIn("PUBLIC_ROOT", prune)
        self.assertNotIn("MediaStore", prune)
        self.assertNotIn("Environment.getExternalStoragePublicDirectory", prune)
        self.assertRegex(prune, r"documentsMirrorMarker\([^)]*\)\.isFile")

    def test_status_tells_hmi_where_the_durable_copy_lives(self):
        self.assertIn('"documentsMirror"', RECORDER)
        self.assertIn('"Download/Omegas"', MIRROR_PATH.read_text(encoding="utf-8"))

    def test_session_is_published_as_immutable_zip_parts(self):
        # Contrato de texto (apoio); a prova de comportamento é SessionPartPlannerTest (JVM).
        mirror = MIRROR_PATH.read_text(encoding="utf-8")
        publish = mirror[mirror.index("fun publishPart("):mirror.index("fun publishRootFile(")]
        self.assertIn('"application/zip"', publish)
        self.assertIn("MediaStore.MATCH_INCLUDE", publish)
        self.assertIn("IS_PENDING", publish)
        recorder_sync = RECORDER[RECORDER.index("private fun syncDocumentsMirror"):RECORDER.index("private fun documentsMirrorMarker")]
        self.assertIn("fd?.sync()", recorder_sync)
        self.assertIn("SessionPartPlanner.commit", recorder_sync)
        recover = RECORDER[RECORDER.index("fun recoverDocumentsMirrorAsync"):RECORDER.index("fun close()")]
        self.assertIn("final = true", recover)

    def test_repeated_active_exports_do_not_repackage_prior_event_segments(self):
        active = RECORDER[
            RECORDER.index("private fun createActiveExportSnapshot"):
            RECORDER.index("private fun createStoppedExportSnapshot")
        ]
        self.assertIn("exportStartSegment = lastExportedSegment + 1", active)
        self.assertIn("exportStartSequenceExclusive = lastExportBoundarySequence", active)
        self.assertIn("segment in exportStartSegment..boundarySegment", active)
        self.assertIn('.put("exportMode", "INCREMENTAL_ACTIVE")', active)

        export = RECORDER[RECORDER.index("fun exportSession("):RECORDER.index("fun recoverDocumentsMirrorAsync")]
        self.assertIn("lastExportedSegment = maxOf", export)
        self.assertIn("lastExportBoundarySequence = maxOf", export)
        self.assertIn('"INCREMENTAL_ACTIVE"', export)
        self.assertIn('"FULL_STOPPED"', export)

    def test_stopped_export_remains_complete(self):
        stopped = RECORDER[
            RECORDER.index("private fun createStoppedExportSnapshot"):
            RECORDER.index("private fun eventSegmentNumber")
        ]
        self.assertIn("dir.walkTopDown()", stopped)
        self.assertIn(".filter { it.isFile }", stopped)
        self.assertIn("incrementalEvents = false", stopped)


if __name__ == "__main__":
    unittest.main()